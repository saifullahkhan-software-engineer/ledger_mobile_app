"""migrate-images: legacy upload files → image_assets table (repeatable)."""
import hashlib
import io
import subprocess
import sys
from pathlib import Path

from PIL import Image
from sqlalchemy import func, select, text

from app.db import Session, sync_engine
from app.images import UPLOAD_DIR, local_upload_relative_path
from app.manage import import_legacy_uploads
from app.models import AppIcon, Business, ImageAsset, User
from test_upgrade import simulate_legacy_schema

P = "/api/v1"

APP_ICON_COLUMNS = [
    "id", "key", "label", "screen", "image_url", "fallback_icon", "updated_at",
]
BUSINESS_COLUMNS = [
    "id", "name", "type", "total_shares", "share_price", "stock", "stock_cost",
    "icon_url",
]


def encode(fmt="PNG", w=4, h=4, color=(200, 30, 30)):
    buf = io.BytesIO()
    Image.new("RGB", (w, h), color).save(buf, format=fmt)
    return buf.getvalue()


LEGACY_PNG = encode()
LEGACY_JPEG = encode("JPEG")
SHARED_PNG = encode(color=(10, 120, 220))
SVG = b'<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>'


def make_uploads(tmp_path):
    root = tmp_path / "uploads"
    icons = root / "icons"
    icons.mkdir(parents=True)
    (icons / "legacy.png").write_bytes(LEGACY_PNG)
    (icons / "legacy.jpg").write_bytes(LEGACY_JPEG)
    (icons / "shared.png").write_bytes(SHARED_PNG)
    (icons / "logo.svg").write_bytes(SVG)
    (tmp_path / "secret.txt").write_text("not for the web")
    return root


def seed_legacy_rows(external_business="lpg"):
    with Session.begin() as db:
        db.add(AppIcon(key="legacy_one", label="One", screen="dashboard",
                       image_url="/uploads/icons/legacy.png"))
        db.add(AppIcon(key="legacy_two", label="Two", screen="dashboard",
                       image_url="http://192.168.1.20:8000/uploads/icons/legacy.jpg"))
        db.add(AppIcon(key="shared_a", label="Shared A", screen="dashboard",
                       image_url="/uploads/icons/shared.png"))
        db.add(AppIcon(key="shared_b", label="Shared B", screen="dashboard",
                       image_url="uploads/icons/shared.png"))
        db.add(AppIcon(key="external", label="External", screen="dashboard",
                       image_url="https://cdn.example.com/logo.png"))
        db.add(AppIcon(key="missing", label="Missing", screen="dashboard",
                       image_url="/uploads/icons/gone.png"))
        db.add(AppIcon(key="traversal", label="Traversal", screen="dashboard",
                       image_url="/uploads/../secret.txt"))
        db.add(AppIcon(key="old_svg", label="Old SVG", screen="dashboard",
                       image_url="/uploads/icons/logo.svg"))
        db.get(Business, "chicken").icon_url = "/uploads/icons/legacy.png"
        db.get(Business, external_business).icon_url = "https://images.example.net/lpg.png"


def icon(key):
    with Session() as db:
        row = db.execute(select(AppIcon).where(AppIcon.key == key)).scalar_one()
        return row.id, row.image_url, row.asset_id


def asset_sha(raw):
    digest = hashlib.sha256(raw).hexdigest()
    with Session() as db:
        return db.execute(
            select(ImageAsset).where(ImageAsset.sha256 == digest)
        ).scalars().one_or_none()


def asset_count():
    with Session() as db:
        return db.execute(select(func.count(ImageAsset.id))).scalar()


def test_local_upload_path_classification():
    assert local_upload_relative_path("/uploads/icons/a.png") == "icons/a.png"
    assert local_upload_relative_path("uploads/icons/a.png") == "icons/a.png"
    assert local_upload_relative_path("http://192.168.1.20:8000/uploads/icons/a.png") == "icons/a.png"
    assert local_upload_relative_path("http://localhost:8000/uploads/icons/a.png") == "icons/a.png"
    assert local_upload_relative_path("https://cdn.example.com/logo.png") is None
    assert local_upload_relative_path("/api/v1/images/123e4567-e89b-12d3-a456-426614174000") is None
    assert local_upload_relative_path("") is None
    assert local_upload_relative_path(None) is None
    for attack in ("/uploads/../secret.txt", "/uploads/icons/../../x",
                   "http://h/uploads/../../etc/passwd", "/uploads//..//x",
                   "/uploads/icons\\..\\secret.txt", "/uploads/"):
        assert local_upload_relative_path(attack) is False, attack


def test_import_legacy_uploads_flow(client, tmp_path):
    seed_legacy_rows()
    root = make_uploads(tmp_path)

    report = import_legacy_uploads(uploads_root=str(root))
    expected_ids = {icon(key)[0] for key in ("legacy_one", "legacy_two", "shared_a", "shared_b")} | {"chicken"}
    assert {entry["id"] for entry in report["imported"] + report["reused"]} == expected_ids

    _, one_url, one_asset = icon("legacy_one")
    _, two_url, two_asset = icon("legacy_two")
    _, _, shared_a_asset = icon("shared_a")
    _, _, shared_b_asset = icon("shared_b")
    assert one_url == f"/api/v1/images/{one_asset}"
    assert two_url == f"/api/v1/images/{two_asset}"
    assert shared_a_asset == shared_b_asset  # one file → one shared asset

    with Session() as db:
        chicken = db.get(Business, "chicken")
        assert chicken.icon_asset_id == one_asset  # same file → same asset
        assert chicken.icon_url == f"/api/v1/images/{one_asset}"

    png_asset = asset_sha(LEGACY_PNG)
    assert png_asset is not None
    assert (png_asset.content_type, png_asset.filename, png_asset.size) == (
        "image/png", "legacy.png", len(LEGACY_PNG))
    assert png_asset.created_at is not None
    with Session() as db:
        (data,) = db.execute(
            select(ImageAsset.data).where(ImageAsset.id == png_asset.id)
        ).one()
    assert bytes(data) == LEGACY_PNG  # exact bytes, decoded binary (no base64)
    assert asset_sha(LEGACY_JPEG).content_type == "image/jpeg"
    assert asset_sha(SVG) is None

    # References that must NOT change: external URLs, missing files, unsafe
    # paths, and formats that cannot be served safely.
    assert icon("external")[1:] == ("https://cdn.example.com/logo.png", None)
    assert icon("missing")[1:] == ("/uploads/icons/gone.png", None)
    assert icon("traversal")[1:] == ("/uploads/../secret.txt", None)
    assert icon("old_svg")[1:] == ("/uploads/icons/logo.svg", None)
    with Session() as db:
        lpg = db.get(Business, "lpg")
        assert lpg.icon_url == "https://images.example.net/lpg.png"
        assert lpg.icon_asset_id is None

    assert {e["id"] for e in report["missing"]} == {icon("missing")[0]}
    assert {e["id"] for e in report["unsafe"]} == {icon("traversal")[0]}
    assert {e["id"] for e in report["unsupported"]} == {icon("old_svg")[0]}
    assert {e["id"] for e in report["external"]} == {icon("external")[0], "lpg"}

    # Existing records beyond icon fields are preserved.
    with Session() as db:
        chicken = db.get(Business, "chicken")
        assert (chicken.name, chicken.total_shares, chicken.share_price) == ("Chicken", 10, 10000)
        assert db.get(User, "admin").phone == "+923001234567"

    # Serving migrated assets returns the exact original bytes.
    response = client.get(one_url)
    assert response.status_code == 200 and response.content == LEGACY_PNG

    before = asset_count()
    rerun = import_legacy_uploads(uploads_root=str(root))
    assert asset_count() == before  # repeatable: nothing imported twice
    assert rerun["imported"] == [] and rerun["reused"] == []
    assert {e["id"] for e in rerun["already_migrated"]} == expected_ids


def test_interrupted_migration_rerun_reuses_assets(client, tmp_path):
    seed_legacy_rows()
    root = make_uploads(tmp_path)

    # Full first pass, then rewind ONE row to its legacy URL — equivalent to a
    # run interrupted after sibling references had committed.
    import_legacy_uploads(uploads_root=str(root))
    first_pass_assets = asset_count()
    with Session.begin() as db:
        row = db.execute(select(AppIcon).where(AppIcon.key == "legacy_two")).scalar_one()
        row.image_url = "http://192.168.1.20:8000/uploads/icons/legacy.jpg"
        row.asset_id = None

    report = import_legacy_uploads(uploads_root=str(root))
    assert asset_count() == first_pass_assets
    assert report["imported"] == []  # identical content deduplicated by hash
    assert {e["id"] for e in report["reused"]} == {icon("legacy_two")[0]}
    assert icon("legacy_two")[2] == asset_sha(LEGACY_JPEG).id


def test_migrate_images_cli_upgrades_schema_and_imports(client):
    """The operator command: schema upgrade + import, fully repeatable."""
    target_dir = Path(UPLOAD_DIR) / "icons"
    target_dir.mkdir(parents=True, exist_ok=True)
    legacy_file = target_dir / "cli-legacy-test-image.png"
    legacy_file.write_bytes(LEGACY_PNG)
    try:
        with Session.begin() as db:
            db.add(AppIcon(key="cli_icon", label="CLI", screen="dashboard",
                           image_url="/uploads/icons/cli-legacy-test-image.png"))

        # Simulate a database created before image storage: rebuild the tables
        # in their old shapes and drop the image-asset table.
        with sync_engine.begin() as conn:
            simulate_legacy_schema(conn, "app_icons", APP_ICON_COLUMNS, ["asset_id"])
            simulate_legacy_schema(conn, "businesses", BUSINESS_COLUMNS, ["icon_asset_id"])
            conn.execute(text("DROP TABLE image_assets"))

        result = subprocess.run(
            [sys.executable, "-m", "app.manage", "migrate-images"],
            cwd=Path(__file__).resolve().parents[1],
            capture_output=True, text=True, timeout=60,
        )
        assert result.returncode == 0, result.stdout + result.stderr
        assert "1 references imported" in result.stdout

        _, url, asset_id = icon("cli_icon")
        assert asset_id is not None
        assert url == f"/api/v1/images/{asset_id}"
        with Session() as db:
            (data,) = db.execute(
                select(ImageAsset.data).where(ImageAsset.id == asset_id)
            ).one()
        assert bytes(data) == LEGACY_PNG
        served = client.get(url)
        assert served.status_code == 200 and served.content == LEGACY_PNG

        # Second run: repeatability from the CLI entry point too.
        result = subprocess.run(
            [sys.executable, "-m", "app.manage", "migrate-images"],
            cwd=Path(__file__).resolve().parents[1],
            capture_output=True, text=True, timeout=60,
        )
        assert result.returncode == 0, result.stdout + result.stderr
        assert "0 references imported" in result.stdout
        assert "1 already migrated" in result.stdout

        # Migration never deletes the legacy file automatically.
        assert legacy_file.exists()
    finally:
        legacy_file.unlink(missing_ok=True)
