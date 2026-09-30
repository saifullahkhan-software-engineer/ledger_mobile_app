"""Exercise the operator schema commands against an older database shape."""
import os
import subprocess
import sys
from pathlib import Path

from sqlalchemy import inspect, text

from app.db import Session, sync_engine
from app.models import Business, User


def run_manage(*args):
    result = subprocess.run(
        [sys.executable, "-m", "app.manage", *args],
        cwd=Path(__file__).resolve().parents[1],
        capture_output=True,
        text=True,
        timeout=60,
    )
    return result


def legacy_without(conn, table, columns):
    """Rebuild a table without newer columns (SQLite cannot always DROP them)."""
    cols = ", ".join(columns)
    conn.execute(text(f"CREATE TABLE {table}__legacy AS SELECT {cols} FROM {table}"))
    conn.execute(text(f"DROP TABLE {table}"))
    conn.execute(text(f"ALTER TABLE {table}__legacy RENAME TO {table}"))


def simulate_legacy_schema(conn, table, keep_columns, drop_columns):
    """Give a table its previous-release shape on either supported engine."""
    if conn.dialect.name == "postgresql":
        for column in drop_columns:
            conn.execute(text(f"ALTER TABLE {table} DROP COLUMN {column}"))
    else:  # SQLite rebuild paths
        legacy_without(conn, table, keep_columns)


BUSINESS_COLUMNS = [
    "id", "name", "type", "total_shares", "share_price", "stock", "stock_cost",
]
OPERATION_COLUMNS = [
    "id", "day_id", "kind", "quantity", "amount", "cost", "channel", "note",
    "supplier_id", "created_at",
]


def business_columns():
    return {col["name"] for col in inspect(sync_engine).get_columns("businesses")}


def operation_columns():
    return {col["name"] for col in inspect(sync_engine).get_columns("operations")}


def test_upgrade_adds_missing_columns_and_preserves_data(client, admin_headers):
    with Session.begin() as db:
        business = db.get(Business, "chicken")
        business.stock_count = 7

    with sync_engine.begin() as conn:
        simulate_legacy_schema(conn, "businesses", BUSINESS_COLUMNS, ["stock_count"])
        simulate_legacy_schema(conn, "operations", OPERATION_COLUMNS, ["count", "category"])
        conn.execute(text("DROP INDEX IF EXISTS ix_operations_day_id"))

    assert "stock_count" not in business_columns()
    assert {"count", "category"} & operation_columns() == set()

    result = run_manage("upgrade-db")
    assert result.returncode == 0, result.stdout + result.stderr

    assert "stock_count" in business_columns()
    assert {"count", "category"} <= operation_columns()
    assert "ix_operations_day_id" in {
        index["name"] for index in inspect(sync_engine).get_indexes("operations")
    }

    # Accounts, businesses and their data survive the upgrade untouched.
    with Session() as db:
        assert db.get(User, "admin").phone == "+923001234567"
        assert db.get(Business, "chicken").name == "Chicken"
    assert (
        client.get("/api/v1/admin/businesses", headers=admin_headers).status_code == 200
    )


def test_upgrade_is_repeatable(client):
    first = run_manage("upgrade-db")
    second = run_manage("upgrade-db")
    assert first.returncode == 0, first.stdout + first.stderr
    assert second.returncode == 0, second.stdout + second.stderr
    assert business_columns() == business_columns()


def test_upgrade_refuses_an_uninitialized_database(tmp_path):
    """upgrade-db must not create a schema on a database that was never init'd."""
    result = subprocess.run(
        [sys.executable, "-m", "app.manage", "upgrade-db"],
        cwd=Path(__file__).resolve().parents[1],
        capture_output=True,
        text=True,
        timeout=60,
        env={
            **os.environ,
            "DATABASE_URL": f"sqlite:///{tmp_path / 'empty.db'}",
            "JWT_SECRET": "test-secret-at-least-thirty-two-characters-long",
        },
    )
    assert result.returncode != 0
    assert "not initialized" in result.stdout + result.stderr
