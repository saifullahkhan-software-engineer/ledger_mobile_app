"""Automatic midnight close of business days and owner corrections of past dates."""

import asyncio
from datetime import date, datetime, timedelta
from zoneinfo import ZoneInfo

from sqlalchemy import func, select

from app import manage, services
from app.db import Session
from app.models import Day, Posting, Settlement
from app.services import cutoff_date, today

PKT = ZoneInfo("Asia/Karachi")

P = "/api/v1"


def post(c, path, h, payload, key="request-0001", status=200):
    r = c.post(P + path, json=payload, headers={**h, "Idempotency-Key": key})
    assert r.status_code == status, r.text
    return r.json()


def daily(
    c, h, kind, amount, quantity=0, key="daily-0001", business="chicken", **extra
):
    return post(
        c,
        "/admin/ledger/daily",
        h,
        {
            "business_id": business,
            "date": str(today()),
            "kind": kind,
            "amount": amount,
            "quantity": str(quantity),
            **extra,
        },
        key,
    )


def yesterday(business="chicken"):
    """Move the business's open day into the past, as midnight would."""
    with Session.begin() as db:
        day = db.scalar(select(Day).where(Day.business_id == business))
        assert day is not None and day.status == "OPEN"
        day.date = today() - timedelta(days=1)
        return day.id


def manager_headers(client):
    r = client.post(
        P + "/auth/login",
        json={"phone": "+923001234568", "password": "AdminTest123!"},
    )
    assert r.status_code == 200, r.text
    return {"Authorization": "Bearer " + r.json()["access_token"]}


def test_cutoff_date_ends_each_date_at_midnight(monkeypatch):
    assert cutoff_date() == today() - timedelta(days=1)
    # DAY_CLOSE_HOUR moves the moment: 12 closes each date at noon the next day,
    # so the previous date is still open in the morning.
    monkeypatch.setattr(services, "DAY_CLOSE_HOUR", 12)
    assert cutoff_date(datetime(2026, 9, 28, 9, 0, tzinfo=PKT)) == date(2026, 9, 26)
    assert cutoff_date(datetime(2026, 9, 28, 13, 0, tzinfo=PKT)) == date(2026, 9, 27)


def test_finished_day_closes_and_settles_without_a_button(
    client, admin_headers, investor_headers
):
    post(
        client,
        "/investor/transaction/buy",
        investor_headers,
        {"business_id": "chicken", "shares": 1},
    )
    daily(client, admin_headers, "BYPRODUCT", 101, 1)
    day_id = yesterday()

    # A plain history read reports the finished day as already closed and settled.
    rows = client.get(
        P + "/admin/ledger/daily?business_id=chicken", headers=admin_headers
    ).json()
    assert [row["id"] for row in rows] == [day_id]
    assert rows[0]["status"] == "CLOSED"
    assert rows[0]["settled_net_profit"] == 101
    assert rows[0]["variance"] == 0

    with Session() as db:
        settlement = db.scalar(
            select(Settlement).where(Settlement.source == f"day:{day_id}")
        )
        assert settlement.net_profit == 101 and settlement.distributed == 10
        assert db.scalar(select(func.sum(Posting.amount))) == 0

    portfolio = client.get(P + "/investor/portfolio", headers=investor_headers).json()
    # 1,000,000 deposit − 10,000 for one share + 10 profit share.
    assert portfolio["wallet_balance"] == 990010
    assert portfolio["total_profit_earned"] == 10

    # The new business date is immediately writable: no manual close is needed.
    fresh = daily(client, admin_headers, "EXPENSE", 500, key="next-date")
    assert fresh["day"]["status"] == "OPEN"
    assert fresh["day"]["date"] == str(today())
    assert fresh["day"]["id"] != day_id


def test_scheduled_sweep_matches_the_api_and_is_repeatable(
    client, admin_headers, capsys
):
    daily(client, admin_headers, "PURCHASE", 10000, 1)
    day_id = yesterday()

    asyncio.run(manage.auto_close_days())
    assert "Auto-closed 1 day(s)" in capsys.readouterr().out
    with Session() as db:
        assert db.get(Day, day_id).status == "CLOSED"
        assert db.scalar(select(func.count(Settlement.id))) == 1

    # Running the cron command again settles nothing twice.
    asyncio.run(manage.auto_close_days())
    assert "nothing to close" in capsys.readouterr().out
    with Session() as db:
        assert db.scalar(select(func.count(Settlement.id))) == 1


def test_managers_cannot_add_records_to_a_previous_date(client, admin_headers):
    assert (
        client.put(
            P + "/admin/businesses/chicken/managers/manager", headers=admin_headers
        ).status_code
        == 200
    )
    r = client.post(
        P + "/admin/ledger/daily",
        json={
            "business_id": "chicken",
            "date": str(today() - timedelta(days=1)),
            "kind": "EXPENSE",
            "amount": 100,
        },
        headers={**manager_headers(client), "Idempotency-Key": "manager-backdated"},
    )
    assert r.status_code == 422, r.text


def test_nobody_can_add_records_to_a_future_date(client, admin_headers):
    r = client.post(
        P + "/admin/ledger/daily",
        json={
            "business_id": "chicken",
            "date": str(today() + timedelta(days=1)),
            "kind": "EXPENSE",
            "amount": 100,
        },
        headers={**admin_headers, "Idempotency-Key": "future-entry"},
    )
    assert r.status_code == 422, r.text


def test_super_admin_can_add_a_record_to_a_previous_date(
    client, admin_headers, investor_headers
):
    post(
        client,
        "/investor/transaction/buy",
        investor_headers,
        {"business_id": "chicken", "shares": 1},
    )
    past = str(today() - timedelta(days=1))
    added = client.post(
        P + "/admin/ledger/daily",
        json={
            "business_id": "chicken",
            "date": past,
            "kind": "EXPENSE",
            "amount": 500,
        },
        headers={**admin_headers, "Idempotency-Key": "owner-backdated"},
    )
    assert added.status_code == 200, added.text
    body = added.json()
    assert body["day"]["date"] == past
    assert body["day"]["status"] == "CLOSED"
    assert body["day"]["expenses"] == 500
    assert body["operation"]["kind"] == "EXPENSE"
    # A gap date is settled the same way midnight would have.
    assert body["day"]["settled_net_profit"] == -500
    assert body["day"]["variance"] == 0


def test_super_admin_corrects_a_settled_date_without_touching_the_payout(
    client, admin_headers, investor_headers
):
    post(
        client,
        "/investor/transaction/buy",
        investor_headers,
        {"business_id": "chicken", "shares": 2},
    )
    daily(client, admin_headers, "PURCHASE", 100000, 10)
    sale = daily(client, admin_headers, "SALE", 100000, 5, "sale-0001")
    sale_id = sale["operation"]["id"]
    day_id = sale["day"]["id"]
    closed = post(client, "/admin/ledger/close", admin_headers, {"day_id": day_id})
    assert closed["day"]["net_profit"] == 50000
    assert closed["settlement"]["distributed"] == 10000

    # Managers can never correct records, open day or not.
    forbidden = client.patch(
        P + f"/admin/operations/{sale_id}",
        json={"amount": 150000},
        headers={**manager_headers(client), "Idempotency-Key": "manager-fix"},
    )
    assert forbidden.status_code == 403

    fixed = client.patch(
        P + f"/admin/operations/{sale_id}",
        json={"amount": 150000},
        headers={**admin_headers, "Idempotency-Key": "owner-fix"},
    )
    assert fixed.status_code == 200, fixed.text
    body = fixed.json()
    # The date summary is rebuilt from the corrected records.
    assert body["day"]["revenue"] == 150000
    assert body["day"]["cost"] == 50000
    assert body["day"]["net_profit"] == 100000
    assert body["day"]["status"] == "CLOSED"
    # The payout that already went out is unchanged; the gap is explicit.
    assert body["day"]["settled_net_profit"] == 50000
    assert body["day"]["variance"] == 50000
    # The correction response carries everything a client needs to redraw.
    assert body["business"]["id"] == "chicken"
    assert body["operation"]["amount"] == 150000

    detail = client.get(P + f"/admin/ledger/{day_id}", headers=admin_headers).json()
    assert detail["net_profit"] == 100000
    assert detail["settled_net_profit"] == 50000
    assert detail["variance"] == 50000

    with Session() as db:
        settlement = db.scalar(
            select(Settlement).where(Settlement.source == f"day:{day_id}")
        )
        assert settlement.net_profit == 50000 and settlement.distributed == 10000
        assert db.scalar(select(func.sum(Posting.amount))) == 0

    portfolio = client.get(P + "/investor/portfolio", headers=investor_headers).json()
    assert portfolio["total_profit_earned"] == 10000
