"""Business-wide transaction feed: last-N tables and the transaction history.

`GET /admin/operations` lists individual transactions of one business (newest
business date first) with an optional kind and date range, so the app does not
have to walk the per-day endpoints to show a flat history.
"""

import asyncio
from datetime import timedelta

from sqlalchemy import inspect, select, text

from app.db import Session, ensure_additive_columns, sync_engine
from app.models import Day, User
from app.services import today

P = "/api/v1"


def post(c, path, h, payload, key="request-0001", status=200):
    r = c.post(P + path, json=payload, headers={**h, "Idempotency-Key": key})
    assert r.status_code == status, r.text
    return r.json()


def daily(c, h, kind, amount, quantity=0, key="daily-0001", business="chicken", **extra):
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


def feed(c, h, business="chicken", **params):
    query = "&".join(f"{k}={v}" for k, v in {"business_id": business, **params}.items() if v is not None)
    r = c.get(f"{P}/admin/operations?{query}", headers=h)
    assert r.status_code == 200, r.text
    return r.json()


def shift_day_to_yesterday(business="chicken"):
    with Session.begin() as db:
        day = db.scalar(select(Day).where(Day.business_id == business))
        day.date = today() - timedelta(days=1)
        return day.id


def test_feed_returns_individual_transactions_newest_first(
    client, admin_headers, investor_headers
):
    daily(client, admin_headers, "PURCHASE", 100000, 10)
    sale = daily(client, admin_headers, "SALE", 100000, 5, "sale-0001")
    daily(client, admin_headers, "EXPENSE", 5000, key="expense-0001", category="Transport")
    daily(client, admin_headers, "BYPRODUCT", 2500, 1, key="other-0001")

    rows = feed(client, admin_headers)
    assert len(rows) == 4
    # Newest recorded first, and every row says which business date it belongs to.
    assert [row["kind"] for row in rows] == ["BYPRODUCT", "EXPENSE", "SALE", "PURCHASE"]
    assert {row["date"] for row in rows} == {str(today())}
    assert rows[1]["category"] == "Transport"
    assert all(row["day_id"] == sale["day"]["id"] for row in rows)

    # "Last 5 sales" / "Recent expenses": one kind, capped at the requested count.
    assert [row["kind"] for row in feed(client, admin_headers, kind="SALE")] == ["SALE"]
    assert len(feed(client, admin_headers, kind="SALE", limit=5)) == 1
    assert feed(client, admin_headers, kind="SALE")[0]["amount"] == 100000
    assert [row["kind"] for row in feed(client, admin_headers, kind="EXPENSE")] == ["EXPENSE"]

    assert client.get(f"{P}/admin/operations?business_id=chicken", headers=investor_headers).status_code == 403


def test_feed_orders_by_business_date_and_filters_ranges(client, admin_headers):
    daily(client, admin_headers, "PURCHASE", 100000, 10)
    shift_day_to_yesterday()
    # Recording today's first operation auto-closes yesterday, which is why the
    # feed can mix dates safely.
    daily(client, admin_headers, "EXPENSE", 500, key="today-expense")

    rows = feed(client, admin_headers)
    assert [row["date"] for row in rows] == [str(today()), str(today() - timedelta(days=1))]

    only_yesterday = feed(
        client, admin_headers, start=today() - timedelta(days=1), end=today() - timedelta(days=1)
    )
    assert [row["kind"] for row in only_yesterday] == ["PURCHASE"]
    only_today = feed(client, admin_headers, start=today(), end=today())
    assert [row["kind"] for row in only_today] == ["EXPENSE"]
    # A reversed range is a client error.
    r = client.get(
        f"{P}/admin/operations?business_id=chicken&start={today()}&end={today() - timedelta(days=2)}",
        headers=admin_headers,
    )
    assert r.status_code == 422, r.text

    # Pagination walks the flat list without repeating rows.
    first = feed(client, admin_headers, limit=1)
    second = feed(client, admin_headers, limit=1, offset=1)
    assert len(first) == 1 and len(second) == 1
    assert first[0]["id"] != second[0]["id"]
    assert len(feed(client, admin_headers, offset=2)) == 0


def test_expenses_and_supplier_bills_carry_the_business_date(client, admin_headers):
    supplier = post(
        client,
        "/admin/suppliers",
        admin_headers,
        {"business_id": "chicken", "name": "Supplier A", "phone": "+923001111111"},
        status=201,
    )
    daily(client, admin_headers, "EXPENSE", 5000, key="exp-0001", category="Transport")
    daily(client, admin_headers, "PURCHASE", 10000, 1, key="buy-0001", supplier_id=supplier["id"])

    expenses = client.get(
        f"{P}/admin/expenses?business_id=chicken&start={today()}&end={today()}",
        headers=admin_headers,
    ).json()
    assert [row["date"] for row in expenses] == [str(today())]

    bills = client.get(
        f"{P}/admin/suppliers/{supplier['id']}/bills", headers=admin_headers
    ).json()
    assert [row["date"] for row in bills] == [str(today())]
    assert bills[0]["amount"] == 10000


def test_feed_requires_access_to_the_business(client, admin_headers):
    with Session() as db:
        manager_id = db.scalar(select(User.id).where(User.phone == "+923001234568"))
    login = client.post(
        P + "/auth/login",
        json={"phone": "+923001234568", "password": "AdminTest123!"},
    )
    headers = {"Authorization": "Bearer " + login.json()["access_token"]}
    # Unassigned business: no feed. After the owner assigns it: the same feed.
    assert client.get(f"{P}/admin/operations?business_id=chicken", headers=headers).status_code == 403
    assert (
        client.put(
            P + "/admin/businesses/chicken/managers/" + manager_id, headers=admin_headers
        ).status_code
        == 200
    )
    assert client.get(f"{P}/admin/operations?business_id=chicken", headers=headers).status_code == 200


def test_operations_history_index_self_heals(client):
    def indexes():
        return {index["name"] for index in inspect(sync_engine).get_indexes("operations")}

    assert "ix_operations_day_id" in indexes()
    with sync_engine.begin() as conn:
        conn.execute(text("DROP INDEX ix_operations_day_id"))
    assert "ix_operations_day_id" not in indexes()
    asyncio.run(ensure_additive_columns())
    assert "ix_operations_day_id" in indexes()
