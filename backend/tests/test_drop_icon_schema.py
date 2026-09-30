"""drop-icon-schema: one-time removal of the retired database-backed icon storage."""
import os
import subprocess
import sys
from pathlib import Path

import pytest
from sqlalchemy import inspect, text

from app.db import sync_engine
from app.models import Business, User

BACKEND = Path(__file__).resolve().parents[1]

LEGACY_IMAGE_ASSETS_DDL = """
CREATE TABLE image_assets (
    id VARCHAR(36) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    size BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL,
    data BLOB NOT NULL,
    PRIMARY KEY (id)
)
"""

LEGACY_APP_ICONS_DDL = """
CREATE TABLE app_icons (
    id VARCHAR(36) NOT NULL,
    key VARCHAR(50) NOT NULL,
    label VARCHAR(120) NOT NULL,
    screen VARCHAR(50) NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    fallback_icon VARCHAR(50),
    updated_at DATETIME NOT NULL,
    asset_id VARCHAR(36) REFERENCES image_assets(id),
    PRIMARY KEY (id)
)
"""


def clear_legacy_icon_schema():
    """Undo a previous call: these tables are no longer in the ORM metadata,
    so the fixture's ``Base.metadata.drop_all`` cannot clean them up."""
    with sync_engine.begin() as conn:
        if inspect(conn).has_table("businesses"):
            for column in ("icon_asset_id", "icon_url"):
                if any(c["name"] == column for c in inspect(conn).get_columns("businesses")):
                    conn.execute(text(f"ALTER TABLE businesses DROP COLUMN {column}"))
        for table in ("app_icons", "image_assets"):
            conn.execute(text(f"DROP TABLE IF EXISTS {table}"))


def add_legacy_icon_schema():
    """Recreate the pre-removal icon tables and columns with raw SQL.

    The ORM no longer knows about them, which is exactly the situation this
    command exists for: a database created before the custom-icon feature was
    removed.
    """
    clear_legacy_icon_schema()
    with sync_engine.begin() as conn:
        conn.execute(text(LEGACY_IMAGE_ASSETS_DDL))
        conn.execute(text(LEGACY_APP_ICONS_DDL))
        conn.execute(
            text("INSERT INTO image_assets VALUES "
                 "('asset-1', 'image/png', 'icon.png', 8, '" + "0" * 64 + "', "
                 "CURRENT_TIMESTAMP, X'89504E470D0A1A0A')")
        )
        conn.execute(
            text("INSERT INTO app_icons VALUES "
                 "('icon-1', 'app_logo', 'Logo', 'dashboard', "
                 "'/api/v1/images/asset-1', 'store', CURRENT_TIMESTAMP, 'asset-1')")
        )
        conn.execute(
            text("ALTER TABLE businesses ADD COLUMN icon_url VARCHAR(500)")
        )
        conn.execute(
            text("ALTER TABLE businesses ADD COLUMN icon_asset_id VARCHAR(36) "
                 "REFERENCES image_assets(id)")
        )
        conn.execute(
            text("UPDATE businesses SET icon_url = '/api/v1/images/asset-1', "
                 "icon_asset_id = 'asset-1' WHERE id = 'chicken'")
        )


@pytest.fixture(autouse=True)
def clean_icon_schema():
    """Never leak the raw-SQL icon tables into another test module."""
    yield
    clear_legacy_icon_schema()


def run_drop(*args, database_url=None):
    env = {**os.environ}
    if database_url:
        env["DATABASE_URL"] = database_url
    return subprocess.run(
        [sys.executable, "-m", "app.manage", "drop-icon-schema", *args],
        cwd=BACKEND,
        capture_output=True,
        text=True,
        timeout=60,
        env=env,
    )


def icon_tables():
    return [
        table
        for table in ("app_icons", "image_assets")
        if inspect(sync_engine).has_table(table)
    ]


def business_columns():
    return {col["name"] for col in inspect(sync_engine).get_columns("businesses")}


def test_dry_run_reports_without_dropping(client):
    add_legacy_icon_schema()

    result = run_drop()

    assert result.returncode == 0, result.stdout + result.stderr
    assert "DRY RUN" in result.stdout
    assert "Would drop table   image_assets" in result.stdout
    assert "Would drop table   app_icons" in result.stdout
    assert "Would drop column  businesses.icon_url" in result.stdout
    assert "--yes" in result.stdout
    # Nothing was actually removed.
    assert set(icon_tables()) == {"app_icons", "image_assets"}
    assert {"icon_url", "icon_asset_id"} <= business_columns()


def test_yes_force_drops_everything(client):
    add_legacy_icon_schema()

    result = run_drop("--yes", "--force")

    assert result.returncode == 0, result.stdout + result.stderr
    assert "EXECUTED" in result.stdout
    assert icon_tables() == []
    assert {"icon_url", "icon_asset_id"} & business_columns() == set()
    assert "Done." in result.stdout
    # The business rows themselves are untouched.
    with sync_engine.connect() as conn:
        assert conn.execute(text("SELECT count(*) FROM businesses")).scalar() == 3


def test_requiring_typed_confirmation(client):
    add_legacy_icon_schema()

    env = {**os.environ}
    # Without an answer on stdin the prompt aborts rather than dropping.
    wrong = subprocess.run(
        [sys.executable, "-m", "app.manage", "drop-icon-schema", "--yes"],
        cwd=BACKEND, capture_output=True, text=True, timeout=60, env=env,
        input="no\n",
    )
    assert "Aborted; nothing was dropped." in wrong.stdout
    assert set(icon_tables()) == {"app_icons", "image_assets"}

    # The right answer proceeds.
    confirmed = subprocess.run(
        [sys.executable, "-m", "app.manage", "drop-icon-schema", "--yes"],
        cwd=BACKEND, capture_output=True, text=True, timeout=60, env=env,
        input="DROP\n",
    )
    assert confirmed.returncode == 0, confirmed.stdout + confirmed.stderr
    assert icon_tables() == []


def test_repeatable_and_reports_nothing_to_do(client):
    add_legacy_icon_schema()
    assert run_drop("--yes", "--force").returncode == 0

    again = run_drop()
    assert again.returncode == 0, again.stdout + again.stderr
    assert "Nothing to do" in again.stdout


def test_upgrade_and_init_never_recreate_icon_schema(client):
    add_legacy_icon_schema()
    assert run_drop("--yes", "--force").returncode == 0

    upgraded = subprocess.run(
        [sys.executable, "-m", "app.manage", "upgrade-db"],
        cwd=BACKEND, capture_output=True, text=True, timeout=60,
    )
    assert upgraded.returncode == 0, upgraded.stdout + upgraded.stderr
    assert icon_tables() == []
    assert {"icon_url", "icon_asset_id"} & business_columns() == set()

    with sync_engine.begin() as conn:
        from app.db import Base
        Base.metadata.create_all(conn)
    assert icon_tables() == []
    # Ordinary data is still readable through the ORM afterwards.
    with sync_engine.connect() as conn:
        assert conn.execute(text("SELECT count(*) FROM businesses")).scalar() == 3
    assert inspect(sync_engine).has_table("users")
    assert User.__tablename__ in inspect(sync_engine).get_table_names()
    assert Business.__tablename__ in inspect(sync_engine).get_table_names()
