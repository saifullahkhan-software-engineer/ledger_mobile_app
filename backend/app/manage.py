"""Explicit schema and image-data migration commands; never exposed over HTTP.

Commands
- init-db:        create the current schema on an EMPTY database only.
- upgrade-db:     repeatable additive schema upgrade for existing databases
                  (creates image_assets/app_icons, adds missing icon columns).
- migrate-images: repeatable data migration importing legacy /uploads/ files
                  referenced by app_icons.image_url and businesses.icon_url
                  into image_assets and pointing those rows at the new
                  /api/v1/images/{id} serving URLs.
- seed:           create the owner account and sample businesses.
"""

import argparse
import asyncio
import getpass
import hashlib
import json
import os
from collections import defaultdict

from sqlalchemy import inspect, select, text

from .db import (
    Base,
    AsyncSessionLocal,
    Session,
    engine,
    sync_engine,
    ensure_business_icon_column_on_connection,
)
from .images import (
    UPLOAD_DIR,
    asset_url,
    clean_filename,
    ensure_image_asset_schema_on_connection,
    local_upload_relative_path,
    match_asset_id,
    validate_image,
    MAX_IMAGE_BYTES,
)
from .models import AppIcon, Business, ImageAsset, User, WriteLock
from .schemas import Register
from .security import passwords


async def init():
    # Use sync engine for metadata creation (doesn't support async)
    Base.metadata.create_all(sync_engine)
    
    # Use async session for data operations
    async with AsyncSessionLocal() as db:
        async with db.begin():
            result = await db.execute(select(WriteLock).where(WriteLock.id == 1))
            if not result.scalar_one_or_none():
                db.add(WriteLock(id=1))
    
    if sync_engine.dialect.name == "postgresql":
        async with engine.begin() as conn:
            await conn.execute(
                text("""CREATE OR REPLACE FUNCTION reject_ledger_mutation() RETURNS trigger AS $$
            BEGIN RAISE EXCEPTION 'Financial history is append-only'; END; $$ LANGUAGE plpgsql""")
            )
            for table in ("journal", "postings", "share_ledger", "settlements"):
                await conn.execute(
                    text(f"DROP TRIGGER IF EXISTS immutable_history ON {table}")
                )
                await conn.execute(
                    text(
                        f"CREATE TRIGGER immutable_history BEFORE UPDATE OR DELETE ON {table} FOR EACH ROW EXECUTE FUNCTION reject_ledger_mutation()"
                    )
                )
    print(
        "Schema initialized. Use migrations for future schema changes; init is not an upgrade tool."
    )


async def _apply_upgrade() -> None:
    """Apply the additive image/icon schema upgrade to an initialized database."""
    async with engine.begin() as conn:
        initialized = await conn.run_sync(
            lambda connection: inspect(connection).has_table("businesses")
        )
        if not initialized:
            raise RuntimeError(
                "Database is not initialized. Run python -m app.manage init-db first."
            )
        await conn.run_sync(ensure_business_icon_column_on_connection)
        await conn.run_sync(ensure_image_asset_schema_on_connection)


async def upgrade():
    """Repeatable additive schema upgrade; no data is altered or deleted."""
    await _apply_upgrade()
    print(
        "Upgraded legacy database schema for database-backed image storage "
        "(image_assets table, icon asset references). Existing users, "
        "businesses, financial history and icon settings are preserved. "
        "Run 'python -m app.manage migrate-images' next to import any legacy "
        "upload files into the database."
    )


def import_legacy_uploads(uploads_root: str | None = None) -> dict:
    """Import legacy files referenced by icon rows into the image_assets table.

    Repeatable: rows already pointing at /api/v1/images/{id} are skipped, and
    content is deduplicated by SHA-256 so an interrupted run never creates
    duplicate assets — each row update commits together with its asset insert,
    so rerunning simply re-processes the not-yet-updated rows. Local upload
    references (relative '/uploads/…' paths and absolute URLs pointing at this
    server's /uploads/ directory) are read from disk WITHOUT any HTTP fetch;
    other external URLs are preserved as legacy values, untouched.
    """
    uploads_root = uploads_root or UPLOAD_DIR
    root_real = os.path.realpath(uploads_root)
    report: dict[str, list] = defaultdict(list)

    with Session() as db:
        targets = [
            ("app_icon", row.id, row.image_url)
            for row in db.execute(select(AppIcon)).scalars()
            if row.image_url
        ] + [
            ("business", row.id, row.icon_url)
            for row in db.execute(select(Business)).scalars()
            if row.icon_url
        ]

    for kind, row_id, url in targets:
        # Commit each reference with its asset in one small transaction: a
        # crash leaves either the old state (rerendered on rerun) or the new.
        with Session.begin() as db:
            relative = local_upload_relative_path(url)
            if relative is None:
                bucket = "already_migrated" if match_asset_id(url) else "external"
                report[bucket].append({"kind": kind, "id": row_id, "url": url})
                continue
            if relative is False:
                report["unsafe"].append({"kind": kind, "id": row_id, "url": url})
                continue
            full_path = os.path.realpath(os.path.join(root_real, relative))
            if not full_path.startswith(root_real + os.sep):
                report["unsafe"].append({"kind": kind, "id": row_id, "url": url})
                continue
            if not os.path.isfile(full_path):
                # Missing files are reported; the existing reference is kept.
                report["missing"].append(
                    {"kind": kind, "id": row_id, "url": url, "file": relative}
                )
                continue
            with open(full_path, "rb") as handle:
                raw = handle.read(MAX_IMAGE_BYTES + 1)
            try:
                content_type = validate_image(raw)
            except Exception as exc:  # malformed/oversized/unsupported legacy file
                report["unsupported"].append(
                    {
                        "kind": kind,
                        "id": row_id,
                        "url": url,
                        "file": relative,
                        "reason": getattr(exc, "detail", str(exc)),
                    }
                )
                continue

            digest = hashlib.sha256(raw).hexdigest()
            asset = db.execute(
                select(ImageAsset).where(
                    ImageAsset.sha256 == digest, ImageAsset.size == len(raw)
                )
            ).scalars().first()
            if asset is None:
                asset = ImageAsset(
                    data=raw,
                    content_type=content_type,
                    filename=clean_filename(os.path.basename(full_path)),
                    size=len(raw),
                    sha256=digest,
                )
                db.add(asset)
                db.flush()
                outcome = "imported"
            else:
                outcome = "reused"
            row = db.get(AppIcon, row_id) if kind == "app_icon" else db.get(Business, row_id)
            if kind == "app_icon":
                row.asset_id = asset.id
                row.image_url = asset_url(asset.id)
            else:
                row.icon_asset_id = asset.id
                row.icon_url = asset_url(asset.id)
            report[outcome].append(
                {
                    "kind": kind,
                    "id": row_id,
                    "file": relative,
                    "asset_id": asset.id,
                    "image_url": row.image_url if kind == "app_icon" else row.icon_url,
                }
            )
    return report


async def migrate_images():
    """Import legacy local upload files into image_assets; safe to rerun."""
    await _apply_upgrade()
    report = import_legacy_uploads()
    print(json.dumps(report, indent=2))
    imported = len(report["imported"]) + len(report["reused"])
    skipped = sum(len(report[key]) for key in report) - imported
    print(
        f"Legacy image migration: {imported} references imported, "
        f"{len(report['already_migrated'])} already migrated (skipped: {skipped} total), "
        f"{len(report['external'])} external URLs left unchanged, "
        f"{len(report['missing'])} missing files (references kept), "
        f"{len(report['unsupported'])} unsupported files (references kept), "
        f"{len(report['unsafe'])} unsafe paths skipped. Old files under "
        f"uploads/ were NOT deleted; remove them manually after verifying "
        f"icons render correctly."
    )


async def seed():
    p = Register(
        phone=os.getenv("ADMIN_PHONE") or input("Owner phone (+923...): "),
        name="Ahsan Khan",
        password=os.getenv("ADMIN_PASSWORD")
        or getpass.getpass("Owner password: "),
    )
    async with AsyncSessionLocal() as db:
        async with db.begin():
            result = await db.execute(select(User).where(User.phone == p.phone))
            if not result.scalar_one_or_none():
                db.add(
                    User(
                        phone=p.phone,
                        name=p.name,
                        password_hash=passwords.hash(p.password),
                        role="SUPERADMIN",
                    )
                )
            result = await db.execute(select(Business.id).limit(1))
            if not result.scalar_one_or_none():
                for kind, name in [
                    ("CHICKEN", "Ahsan Chicken Shop"),
                    ("LPG", "Ahsan LPG Business"),
                    ("BROILER", "Ahsan Broiler Farming"),
                ]:
                    db.add(
                        Business(name=name, type=kind, total_shares=1000, share_price=10000)
                    )
    print(
        "Owner and sample businesses ready; existing credentials are not overwritten."
    )


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "command", choices=["init-db", "upgrade-db", "migrate-images", "seed"]
    )
    args = parser.parse_args()
    commands = {
        "init-db": init,
        "upgrade-db": upgrade,
        "migrate-images": migrate_images,
        "seed": seed,
    }
    asyncio.run(commands[args.command]())
