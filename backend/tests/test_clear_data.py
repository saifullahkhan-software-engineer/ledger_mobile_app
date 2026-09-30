"""clear-data: wipe every record but keep the SUPERADMIN and ADMIN accounts."""
import asyncio
import subprocess
import sys
from datetime import date
from pathlib import Path

import pytest
from sqlalchemy import func, select, text

from app.db import Session, engine, sync_engine
from app.manage import clear_data
from app.services import today
from app.models import (
    Assignment,
    Batch,
    BatchLog,
    Business,
    Day,
    Idempotency,
    Journal,
    Operation,
    Ownership,
    Posting,
    Settlement,
    Supplier,
    User,
    Withdrawal,
)

P = "/api/v1"
BACKEND = Path(__file__).resolve().parents[1]

RECORD_TABLES = [
    "idempotency",
    "withdrawals",
    "share_ledger",
    "postings",
    "journal",
    "settlements",
    "batch_logs",
    "batches",
    "operations",
    "daily_ledgers",
    "suppliers",
]


def run_clear(*args, answer=None):
    return subprocess.run(
        [sys.executable, "-m", "app.manage", "clear-data", *args],
        cwd=BACKEND,
        capture_output=True,
        text=True,
        timeout=60,
        input=answer,
    )


def counts(*tables):
    with sync_engine.connect() as conn:
        return {
            table: conn.execute(text(f"SELECT count(*) FROM {table}")).scalar()
            for table in tables
        }


def users_by_role():
    with Session() as db:
        rows = db.execute(select(User.role, func.count(User.id)).group_by(User.role)).all()
    return {role: total for role, total in rows}


def add_records():
    """One row in every table that holds business records."""
    with Session.begin() as db:
        db.add(
            User(
                id="investor-extra",
                phone="+923009876500",
                name="Investor Extra",
                password_hash="not-a-login-hash",
                role="INVESTOR",
                kyc_status="VERIFIED",
            )
        )
        db.flush()
        db.add_all(
            [
                Supplier(id="sup-1", business_id="chicken", name="Feed Supplier"),
                Day(
                    id="day-1",
                    business_id="chicken",
                    date=date(2026, 1, 1),
                    status="CLOSED",
                    revenue=5000,
                    cost=2000,
                    expenses=500,
                    net_profit=2500,
                ),
                Batch(
                    id="batch-1",
                    business_id="broiler",
                    name="Batch 1",
                    chicks=500,
                    total_shares=100,
                    share_price=1000,
                    expenses=50000,
                ),
                Ownership(
                    id="own-1",
                    user_id="investor-extra",
                    business_id="chicken",
                    shares=2,
                    paid=20000,
                ),
                Journal(id="jr-1", reference="settle:chicken:2026-01-01", kind="SETTLEMENT"),
                Settlement(
                    id="st-1",
                    source="day:chicken:2026-01-01",
                    business_id="chicken",
                    net_profit=2500,
                    distributed=80,
                    retained=2420,
                    snapshot={"profit": 2500},
                ),
                Withdrawal(
                    id="wd-1",
                    user_id="investor-extra",
                    amount=80,
                    provider="RAAST",
                    destination="PK00BANK",
                ),
                Idempotency(scope="admin:daily", key="request-0001", digest="x", response={}),
                Assignment(user_id="manager", business_id="chicken"),
                # Stale assignment for an account the reset deletes.
                Assignment(user_id="investor-extra", business_id="lpg"),
            ]
        )
        db.flush()
        db.add_all(
            [
                Operation(
                    id="op-1",
                    day_id="day-1",
                    kind="SALE",
                    quantity=10,
                    amount=5000,
                    supplier_id="sup-1",
                ),
                BatchLog(
                    id="blog-1",
                    batch_id="batch-1",
                    date=date(2026, 1, 1),
                    feed_kg=50,
                    deaths=2,
                    expense=3000,
                ),
                Posting(id="pg-1", journal_id="jr-1", account="wallet:investor-extra", amount=80),
                Posting(id="pg-2", journal_id="jr-1", account="profit:chicken", amount=-80),
            ]
        )
        business = db.get(Business, "chicken")
        business.stock = 12.5
        business.stock_cost = 4300
        business.stock_count = 7


def test_clear_data_dry_run_reports_without_deleting(client):
    add_records()
    before = counts(*RECORD_TABLES, "users", "businesses", "admin_assignments")

    result = run_clear()

    assert result.returncode == 0, result.stdout + result.stderr
    assert "DRY RUN" in result.stdout
    assert "Would delete" in result.stdout
    assert "--yes" in result.stdout
    assert counts(*RECORD_TABLES, "users", "businesses", "admin_assignments") == before

    # The in-process report carries the same plan the CLI printed.
    report = asyncio.run(clear_data(execute=False))
    assert report["mode"] == "dry-run"
    assert report["kept_roles"] == ["SUPERADMIN", "ADMIN"]
    assert report["deleted_users_by_role"] == {"INVESTOR": 1}
    assert report["rows"]["operations"] == 1
    assert report["rows"]["users"] == 1
    assert report["totals"] == {"delete": 14, "update": 1}
    assert {user["role"] for user in report["kept_users"]} == {"SUPERADMIN", "ADMIN"}
    assert counts(*RECORD_TABLES, "users", "businesses", "admin_assignments") == before


def test_clear_data_keeps_owner_managers_and_businesses(client, admin_headers):
    add_records()

    result = run_clear("--yes", "--force")

    assert result.returncode == 0, result.stdout + result.stderr
    assert "EXECUTED" in result.stdout
    remaining = counts(*RECORD_TABLES, "users", "businesses", "admin_assignments",
                       "write_lock")
    assert {table: remaining[table] for table in RECORD_TABLES} == {
        table: 0 for table in RECORD_TABLES
    }
    assert remaining["users"] == 2  # SUPERADMIN + ADMIN only
    assert users_by_role() == {"SUPERADMIN": 1, "ADMIN": 1}
    assert remaining["businesses"] == 3
    assert remaining["admin_assignments"] == 1  # the deleted investor's row is gone
    assert remaining["write_lock"] == 1
    with Session() as db:
        chicken = db.get(Business, "chicken")
        assert (chicken.stock, chicken.stock_cost, chicken.stock_count) == (0, 0, 0)

    # The kept accounts still sign in and can record a fresh transaction.
    login = client.post(
        P + "/auth/login", json={"phone": "+923001234567", "password": "AdminTest123!"}
    )
    assert login.status_code == 200, login.text
    assert (
        client.post(
            P + "/auth/login", json={"phone": "+923009876500", "password": "Investor123!"}
        ).status_code
        == 401
    )
    purchase = client.post(
        P + "/admin/ledger/daily",
        headers={**admin_headers, "Idempotency-Key": "after-reset-1"},
        json={
            "business_id": "chicken",
            "date": str(today()),
            "kind": "PURCHASE",
            "amount": 3000,
            "quantity": "20",
        },
    )
    assert purchase.status_code == 200, purchase.text
    with Session() as db:
        assert db.scalar(select(func.count(Operation.id))) == 1


def test_clear_data_deletes_businesses_when_asked(client):
    add_records()

    result = run_clear("--yes", "--force", "--delete-businesses")

    assert result.returncode == 0, result.stdout + result.stderr
    remaining = counts(
        *RECORD_TABLES, "users", "businesses", "admin_assignments", "write_lock",
    )
    assert remaining["businesses"] == 0
    assert remaining["admin_assignments"] == 0
    assert remaining["users"] == 2
    assert remaining["write_lock"] == 1


def test_clear_data_without_yes_never_deletes(client):
    add_records()
    before = counts(*RECORD_TABLES, "users")

    for answer in ("no\n", "delete\n", None):  # None closes stdin: EOF
        result = run_clear("--yes", answer=answer)
        assert result.returncode == 0, result.stdout + result.stderr
        assert "Aborted; nothing was deleted." in result.stdout

    assert counts(*RECORD_TABLES, "users") == before


def test_clear_data_refuses_to_delete_every_account(client):
    add_records()
    assert run_clear("--yes", "--force").returncode == 0  # no investor is left
    before = counts(*RECORD_TABLES, "users")

    result = run_clear("--keep-roles", "INVESTOR", "--yes", "--force")

    assert result.returncode == 1
    assert "would delete every user" in result.stdout + result.stderr
    assert counts(*RECORD_TABLES, "users") == before

    bogus = run_clear("--keep-roles", "BOSS", "--yes", "--force")
    assert bogus.returncode == 1
    assert "Unknown role(s) BOSS" in bogus.stdout + bogus.stderr
    assert counts(*RECORD_TABLES, "users") == before


def test_clear_data_force_relogin_invalidates_existing_tokens(client, admin_headers):
    add_records()
    stale = client.get(P + "/admin/businesses", headers=admin_headers)
    assert stale.status_code == 200

    assert run_clear("--yes", "--force", "--force-relogin").returncode == 0

    assert client.get(P + "/admin/businesses", headers=admin_headers).status_code == 401
    fresh = client.post(
        P + "/auth/login", json={"phone": "+923001234567", "password": "AdminTest123!"}
    )
    assert fresh.status_code == 200


@pytest.mark.skipif(
    engine.dialect.name != "postgresql", reason="PostgreSQL append-only triggers"
)
def test_clear_data_restores_append_only_triggers(client):
    add_records()
    assert run_clear("--yes", "--force").returncode == 0

    # The reset lifts the guard for its own transaction only: the catalog must
    # show every append-only trigger enabled again ('O' = origin mode).
    with sync_engine.connect() as conn:
        enabled = dict(
            conn.execute(
                text(
                    "SELECT c.relname, t.tgenabled FROM pg_trigger t "
                    "JOIN pg_class c ON c.oid = t.tgrelid "
                    "WHERE t.tgname = 'immutable_history'"
                )
            ).all()
        )
    assert enabled == {
        table: "O" for table in ("journal", "postings", "settlements", "share_ledger")
    }

    # The trigger is row-level, so it only fires for a row that exists: the
    # reset left these tables empty, so add one and check the guard rejects it.
    with Session.begin() as db:
        db.add(Journal(id="jr-2", reference="after-reset", kind="SETTLEMENT"))
        db.flush()
        db.add(Posting(id="pg-3", journal_id="jr-2", account="wallet:x", amount=10))

    with pytest.raises(Exception, match="append-only"):
        with sync_engine.begin() as conn:
            conn.execute(text("DELETE FROM postings"))
