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


@pytest.mark.parametrize("missing_business_icon", [True, False])
def test_upgrade_legacy_icons_preserves_data(client, admin_headers, missing_business_icon):
    with sync_engine.begin() as conn:
        AppIcon.__table__.drop(conn)
        if missing_business_icon:
            conn.execute(text("ALTER TABLE businesses DROP COLUMN icon_url"))
        else:
            conn.execute(text("UPDATE businesses SET icon_url = '/uploads/existing.png'"))

    run_upgrade()
    columns = {col["name"] for col in inspect(sync_engine).get_columns("businesses")}
    assert "icon_url" in columns
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
