"""Chicken-shop operations: fixed amounts, wastage, editable stock, past dates."""

from datetime import timedelta
from decimal import Decimal

from app.db import Session
from app.models import Business
from app.services import today

P = "/api/v1"


def post(c, path, h, payload, key="request-0001", status=200):
    r = c.post(P + path, json=payload, headers={**h, "Idempotency-Key": key})
    assert r.status_code == status, r.text
    return r.json()


def put(c, path, h, payload, key="request-0001", status=200):
    r = c.put(P + path, json=payload, headers={**h, "Idempotency-Key": key})
    assert r.status_code == status, r.text
    return r.json()


def daily(c, h, kind, amount, quantity=0, key="daily-0001", business="chicken", **extra):
    body = {
        "business_id": business,
        "date": str(today()),
        "kind": kind,
        "amount": amount,
        "quantity": str(quantity),
        **extra,
    }
    return post(c, "/admin/ledger/daily", h, body, key)


def manager_headers(client):
    r = client.post(
        P + "/auth/login",
        json={"phone": "+923001234568", "password": "AdminTest123!"},
    )
    assert r.status_code == 200, r.text
    return {"Authorization": "Bearer " + r.json()["access_token"]}


def test_sale_amount_is_the_fixed_total_not_weight_times_rate(client, admin_headers):
    daily(client, admin_headers, "PURCHASE", 100000, 10)
    # 2.5 kg sold for a fixed Rs 1,500.00 — not 2.5 × any per-kg rate.
    sale = daily(client, admin_headers, "SALE", 150000, "2.5", "sale-fixed")
    assert sale["operation"]["amount"] == 150000
    assert Decimal(str(sale["operation"]["quantity"])) == Decimal("2.5")
    assert sale["day"]["revenue"] == 150000
    stock = client.get(P + "/admin/stock?business_id=chicken", headers=admin_headers).json()
    assert Decimal(str(stock["quantity"])) == Decimal("7.5")


def test_chicken_sale_wastage_reduces_stock_without_adding_revenue(client, admin_headers):
    daily(client, admin_headers, "PURCHASE", 100000, 10, count=10)
    sale = daily(
        client,
        admin_headers,
        "SALE",
        80000,
        4,
        "sale-waste",
        wastage="0.5",
        count=4,
    )
    assert Decimal(str(sale["operation"]["wastage"])) == Decimal("0.5")
    assert sale["day"]["revenue"] == 80000
    # 4 kg sold + 0.5 kg wasted leave 5.5 kg; carrying cost follows the 4.5 kg that left.
    stock = client.get(P + "/admin/stock?business_id=chicken", headers=admin_headers).json()
    assert Decimal(str(stock["quantity"])) == Decimal("5.500") or Decimal(
        str(stock["quantity"])
    ) == Decimal("5.5")
    assert stock["count"] == 6
    assert sale["operation"]["cost"] == 45000
    summary = client.get(
        P + f"/admin/businesses/chicken/summary?on={today()}", headers=admin_headers
    ).json()
    assert Decimal(str(summary["sold_quantity"])) == Decimal("4")
    assert Decimal(str(summary["wasted_quantity"])) == Decimal("0.5")


def test_standalone_wastage_balances_the_day_end_weight(client, admin_headers):
    daily(client, admin_headers, "PURCHASE", 100000, 10, count=5)
    daily(client, admin_headers, "SALE", 50000, 6, "sale-0001", count=3)
    waste = post(
        client,
        "/admin/ledger/daily",
        admin_headers,
        {
            "business_id": "chicken",
            "date": str(today()),
            "kind": "WASTAGE",
            "quantity": "4",
            "amount": 0,
            "count": 2,
        },
        "waste-end",
    )
    assert waste["operation"]["kind"] == "WASTAGE"
    assert waste["operation"]["amount"] == 0
    assert waste["day"]["revenue"] == 50000
    stock = client.get(P + "/admin/stock?business_id=chicken", headers=admin_headers).json()
    assert Decimal(str(stock["quantity"])) == Decimal("0")
    assert stock["count"] == 0
    assert stock["inventory_cost"] == 0
    summary = client.get(
        P + f"/admin/businesses/chicken/summary?on={today()}", headers=admin_headers
    ).json()
    assert Decimal(str(summary["wasted_quantity"])) == Decimal("4")


def test_wastage_is_chicken_only_and_cannot_overdraw_stock(client, admin_headers):
    daily(client, admin_headers, "PURCHASE", 50000, 5, business="lpg")
    r = client.post(
        P + "/admin/ledger/daily",
        json={
            "business_id": "lpg",
            "date": str(today()),
            "kind": "SALE",
            "quantity": "1",
            "amount": 10000,
            "wastage": "0.2",
            "channel": "RETAIL",
        },
        headers={**admin_headers, "Idempotency-Key": "lpg-waste"},
    )
    assert r.status_code == 422, r.text
    r = client.post(
        P + "/admin/ledger/daily",
        json={
            "business_id": "lpg",
            "date": str(today()),
            "kind": "WASTAGE",
            "quantity": "1",
            "amount": 0,
        },
        headers={**admin_headers, "Idempotency-Key": "lpg-waste-kind"},
    )
    assert r.status_code == 422, r.text
    daily(client, admin_headers, "PURCHASE", 100000, 2, key="ch-buy-01")
    r = client.post(
        P + "/admin/ledger/daily",
        json={
            "business_id": "chicken",
            "date": str(today()),
            "kind": "SALE",
            "quantity": "1.5",
            "amount": 10000,
            "wastage": "1",
        },
        headers={**admin_headers, "Idempotency-Key": "over-waste"},
    )
    assert r.status_code == 409, r.text


def test_stock_is_editable_weight_count_and_price(client, admin_headers):
    daily(client, admin_headers, "PURCHASE", 100000, 10, count=8)
    updated = put(
        client,
        "/admin/stock",
        admin_headers,
        {
            "business_id": "chicken",
            "quantity": "12.250",
            "count": 9,
            "inventory_cost": 150000,
        },
        "set-stock",
    )
    assert Decimal(str(updated["quantity"])) == Decimal("12.250")
    assert updated["count"] == 9
    assert updated["inventory_cost"] == 150000
    stock = client.get(P + "/admin/stock?business_id=chicken", headers=admin_headers).json()
    assert stock["count"] == 9
    assert stock["inventory_cost"] == 150000
    with Session() as db:
        row = db.get(Business, "chicken")
        assert Decimal(str(row.stock)) == Decimal("12.250")
        assert row.stock_count == 9
        assert row.stock_cost == 150000
    r = client.put(
        P + "/admin/stock",
        json={"business_id": "broiler", "quantity": "1", "count": 1, "inventory_cost": 1},
        headers={**admin_headers, "Idempotency-Key": "broiler-stock"},
    )
    assert r.status_code == 422, r.text


def test_super_admin_adds_sale_with_wastage_to_a_closed_date(
    client, admin_headers, investor_headers
):
    post(
        client,
        "/investor/transaction/buy",
        investor_headers,
        {"business_id": "chicken", "shares": 1},
    )
    daily(client, admin_headers, "PURCHASE", 100000, 10, count=10)
    sale = daily(client, admin_headers, "SALE", 50000, 2, "sale-0001", count=2)
    day_id = sale["day"]["id"]
    post(client, "/admin/ledger/close", admin_headers, {"day_id": day_id})

    # Managers still cannot add to a closed date, even today's.
    assert (
        client.put(
            P + "/admin/businesses/chicken/managers/manager", headers=admin_headers
        ).status_code
        == 200
    )
    forbidden = client.post(
        P + "/admin/ledger/daily",
        json={
            "business_id": "chicken",
            "date": str(today()),
            "kind": "WASTAGE",
            "quantity": "0.5",
            "amount": 0,
        },
        headers={**manager_headers(client), "Idempotency-Key": "manager-closed"},
    )
    assert forbidden.status_code == 409, forbidden.text

    added = client.post(
        P + "/admin/ledger/daily",
        json={
            "business_id": "chicken",
            "date": str(today()),
            "kind": "WASTAGE",
            "quantity": "0.5",
            "amount": 0,
        },
        headers={**admin_headers, "Idempotency-Key": "owner-closed-waste"},
    )
    assert added.status_code == 200, added.text
    body = added.json()
    assert body["day"]["status"] == "CLOSED"
    assert body["day"]["id"] == day_id
    # Original sale COGS 20,000 + wastage COGS 5,000; payout is unchanged.
    assert body["day"]["cost"] == 25000
    assert body["day"]["revenue"] == 50000
    assert body["day"]["settled_net_profit"] == 30000
    assert body["day"]["variance"] == -5000
    stock = client.get(P + "/admin/stock?business_id=chicken", headers=admin_headers).json()
    assert Decimal(str(stock["quantity"])) == Decimal("7.5")


def test_correcting_a_sale_keeps_wastage_in_stock(client, admin_headers):
    daily(client, admin_headers, "PURCHASE", 100000, 10)
    sale = daily(
        client, admin_headers, "SALE", 40000, 3, "sale-w-01", wastage="1"
    )
    sale_id = sale["operation"]["id"]
    fixed = client.patch(
        P + f"/admin/operations/{sale_id}",
        json={"quantity": "2", "wastage": "0.5", "amount": 30000},
        headers={**admin_headers, "Idempotency-Key": "fix-waste"},
    )
    assert fixed.status_code == 200, fixed.text
    body = fixed.json()
    assert Decimal(str(body["operation"]["quantity"])) == Decimal("2")
    assert Decimal(str(body["operation"]["wastage"])) == Decimal("0.5")
    assert body["operation"]["amount"] == 30000
    stock = client.get(P + "/admin/stock?business_id=chicken", headers=admin_headers).json()
    # 10 − (2 + 0.5) = 7.5
    assert Decimal(str(stock["quantity"])) == Decimal("7.5")


def test_zero_amount_rejected_for_sales_and_purchases(client, admin_headers):
    r = client.post(
        P + "/admin/ledger/daily",
        json={
            "business_id": "chicken",
            "date": str(today()),
            "kind": "SALE",
            "quantity": "1",
            "amount": 0,
        },
        headers={**admin_headers, "Idempotency-Key": "zero-sale"},
    )
    assert r.status_code == 422, r.text
    r = client.post(
        P + "/admin/ledger/daily",
        json={
            "business_id": "chicken",
            "date": str(today()),
            "kind": "PURCHASE",
            "quantity": "1",
            "amount": 0,
        },
        headers={**admin_headers, "Idempotency-Key": "zero-buy"},
    )
    assert r.status_code == 422, r.text
