"""Exercise the operator command against a database created before icon support."""
import subprocess
import sys
from pathlib import Path

import pytest
from sqlalchemy import inspect, text

from app.db import Session, sync_engine
from app.models import AppIcon, Business, User


def run_upgrade():
    result = subprocess.run(
        [sys.executable, "-m", "app.manage", "upgrade-db"],
        cwd=Path(__file__).resolve().parents[1],
        capture_output=True,
        text=True,
        timeout=30,
    )
    assert result.returncode == 0, result.stdout + result.stderr


def legacy_without(conn, table, columns):
    """Rebuild a table without newer columns (SQLite cannot DROP FK columns)."""
    cols = ", ".join(columns)
    conn.execute(text(f"CREATE TABLE {table}__legacy AS SELECT {cols} FROM {table}"))
    conn.execute(text(f"DROP TABLE {table}"))
    conn.execute(text(f"ALTER TABLE {table}__legacy RENAME TO {table}"))


def simulate_legacy_schema(conn, table, keep_columns, drop_columns):
    """Give a table its previous-release shape on either supported engine."""
    if conn.dialect.name == "postgresql":
        for column in drop_columns:
            conn.execute(text(f"ALTER TABLE {table} DROP COLUMN {column}"))
    else:  # SQLite rebuild paths: businesses has dependents and FK columns
        legacy_without(conn, table, keep_columns)


BUSINESS_COLUMNS = [
    "id", "name", "type", "total_shares", "share_price", "stock", "stock_cost",
]
APP_ICON_COLUMNS = [
    "id", "key", "label", "screen", "image_url", "fallback_icon", "updated_at",
]


@pytest.mark.parametrize("missing_business_icon", [True, False])
def test_upgrade_legacy_icons_preserves_data(client, admin_headers, missing_business_icon):
    with sync_engine.begin() as conn:
        AppIcon.__table__.drop(conn)
        keep = BUSINESS_COLUMNS + ([] if missing_business_icon else ["icon_url"])
        drop = ["icon_asset_id"] + (["icon_url"] if missing_business_icon else [])
        simulate_legacy_schema(conn, "businesses", keep, drop)
        conn.execute(text("DROP TABLE image_assets"))
        if not missing_business_icon:
            conn.execute(text("UPDATE businesses SET icon_url = '/uploads/existing.png'"))

    run_upgrade()
    columns = {col["name"] for col in inspect(sync_engine).get_columns("businesses")}
    assert "icon_url" in columns
    assert "icon_asset_id" in columns
    assert "image_assets" in inspect(sync_engine).get_table_names()
    asset_columns = {
        col["name"] for col in inspect(sync_engine).get_columns("image_assets")
    }
    assert {"id", "data", "content_type", "filename", "size", "created_at"} <= asset_columns
    response = client.get("/api/v1/admin/icons", headers=admin_headers)
    assert response.status_code == 200, response.text
    assert response.json() == []

    payload = {
        "label": "App logo", "screen": "dashboard",
        "image_url": "/uploads/icons/logo.png", "fallback_icon": "store",
    }
    response = client.put("/api/v1/admin/icons/app_logo", headers=admin_headers, json=payload)
    assert response.status_code == 200, response.text
    saved_icon = client.get("/api/v1/admin/icons", headers=admin_headers).json()[0]

    # A second upgrade must not reset uploaded icons, accounts or businesses.
    run_upgrade()
    assert client.get("/api/v1/admin/icons", headers=admin_headers).json() == [saved_icon]
    response = client.get("/api/v1/mobile/icons")
    assert response.status_code == 200, response.text
    assert response.json()["icons"]["app_logo"]["image_url"] == payload["image_url"]
    with Session() as db:
        assert db.get(User, "admin").phone == "+923001234567"
        business = db.get(Business, "chicken")
        assert business.name == "Chicken"
        assert business.total_shares == 10
        assert business.icon_url == (None if missing_business_icon else "/uploads/existing.png")


def test_upgrade_adds_asset_columns_to_intermediate_schema(client, admin_headers):
    """A database that already had screen icons but not image storage."""
    with Session.begin() as db:
        db.add(
            AppIcon(
                id="keep-me",
                key="legacy_logo",
                label="Legacy logo",
                screen="dashboard",
                image_url="/uploads/icons/old.png",
                fallback_icon="store",
            )
        )
    with sync_engine.begin() as conn:
        # Recreate the previous release's table shapes (no asset columns) and
        # drop the image-asset table (children first for PostgreSQL).
        simulate_legacy_schema(conn, "app_icons", APP_ICON_COLUMNS, ["asset_id"])
        simulate_legacy_schema(conn, "businesses", BUSINESS_COLUMNS + ["icon_url"], ["icon_asset_id"])
        conn.execute(text("DROP TABLE image_assets"))

    run_upgrade()
    assert "asset_id" in {
        col["name"] for col in inspect(sync_engine).get_columns("app_icons")
    }
    assert "icon_asset_id" in {
        col["name"] for col in inspect(sync_engine).get_columns("businesses")
    }
    assert "image_assets" in inspect(sync_engine).get_table_names()
    # Previously configured icons survive the upgrade untouched.
    icons = client.get("/api/v1/admin/icons", headers=admin_headers)
    assert icons.status_code == 200, icons.text
    kept = [i for i in icons.json() if i["key"] == "legacy_logo"]
    assert kept and kept[0]["image_url"] == "/uploads/icons/old.png"

    # The full upload → assign → serve flow works on the upgraded schema.
    import base64

    sample = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
    uploaded = client.post(
        "/api/v1/admin/icons/upload-base64",
        headers=admin_headers,
        json={"filename": "logo.png", "data": sample},
    )
    assert uploaded.status_code == 200, uploaded.text
    url = uploaded.json()["image_url"]
    saved = client.put(
        "/api/v1/admin/icons/legacy_logo",
        headers=admin_headers,
        json={"label": "Legacy logo", "screen": "dashboard", "image_url": url},
    )
    assert saved.status_code == 200 and saved.json()["asset_id"] == uploaded.json()["id"]
    served = client.get(url)
    assert served.status_code == 200
    assert base64.b64encode(served.content).decode() == sample
