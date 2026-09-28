"""Day summary for any date: past (closed), today, gaps and future dates.

The day stepper in the Admin app browses dates, so the summary endpoint must
answer for any date — including ones that have no `daily_ledgers` row — and say
which kind of date it is without the client trusting the device clock.
"""

from datetime import timedelta

from sqlalchemy import func, select

from app.db import Session
from app.models import Day
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


def summary(c, h, on, business="chicken"):
    r = c.get(f"{P}/admin/businesses/{business}/summary?on={on}", headers=h)
    assert r.status_code == 200, r.text
    return r.json()


def shift_day_by(days, business="chicken"):
    with Session.begin() as db:
        day = db.scalar(select(Day).where(Day.business_id == business))
        day.date = today() + timedelta(days=days)
        return day.id


def test_summary_classifies_past_today_and_future_without_creating_rows(
    client, admin_headers, investor_headers
):
    post(
        client,
        "/investor/transaction/buy",
        investor_headers,
        {"business_id": "chicken", "shares": 1},
    )
    daily(client, admin_headers, "BYPRODUCT", 101, 1)
    day_id = shift_day_by(-1)

    past = summary(client, admin_headers, today() - timedelta(days=1))
    assert past["relation"] == "PAST"
    assert past["day"]["status"] == "CLOSED"
    assert past["day"]["date"] == str(today() - timedelta(days=1))
    # Auto-close already settled this date, so the stepper can label it.
    assert past["settled_net_profit"] == 101
    assert past["variance"] == 0
    assert past["bounds"]["first_date"] == str(today() - timedelta(days=1))
    assert past["bounds"]["last_date"] == str(today())

    now = summary(client, admin_headers, today())
    assert now["relation"] == "TODAY"
    assert now["day"] is None
    assert now["settled_net_profit"] is None and now["variance"] is None

    with Session() as db:
        before = db.scalar(select(func.count(Day.id)))
    future = summary(client, admin_headers, today() + timedelta(days=3))
    assert future["relation"] == "FUTURE"
    assert future["day"] is None
    assert future["sold_quantity"] == 0
    assert future["settled_net_profit"] is None
    with Session() as db:
        # Reading a future date must never create a ledger row.
        assert db.scalar(select(func.count(Day.id))) == before
        assert db.get(Day, day_id).date == today() - timedelta(days=1)


def test_summary_reports_a_gap_date_as_past_with_no_records(client, admin_headers):
    daily(client, admin_headers, "EXPENSE", 500, key="exp-0001")
    gap = summary(client, admin_headers, today() - timedelta(days=5))
    assert gap["relation"] == "PAST"
    assert gap["day"] is None
    assert gap["sold_quantity"] == 0 and gap["purchased_quantity"] == 0
    # Bounds keep pointing at real records, so the stepper knows where to stop.
    assert gap["bounds"]["first_date"] == str(today())


def test_summary_bounds_are_null_and_today_only_for_a_new_business(
    client, admin_headers
):
    fresh = summary(client, admin_headers, today(), business="lpg")
    assert fresh["relation"] == "TODAY"
    assert fresh["day"] is None
    assert fresh["bounds"] == {"first_date": None, "last_date": str(today())}


def test_summary_shows_the_rebuilt_total_and_the_variance_after_a_correction(
    client, admin_headers
):
    purchase = daily(client, admin_headers, "PURCHASE", 100000, 10)
    sale = daily(client, admin_headers, "SALE", 100000, 5, "sale-0001")
    day_id = sale["day"]["id"]
    post(client, "/admin/ledger/close", admin_headers, {"day_id": day_id})

    before = summary(client, admin_headers, today())
    assert before["relation"] == "TODAY"  # closed early by hand, still today's date
    assert before["day"]["status"] == "CLOSED"
    assert before["settled_net_profit"] == 50000 and before["variance"] == 0

    r = client.patch(
        P + f"/admin/operations/{sale['operation']['id']}",
        json={"amount": 150000},
        headers={**admin_headers, "Idempotency-Key": "owner-fix"},
    )
    assert r.status_code == 200, r.text

    after = summary(client, admin_headers, today())
    assert after["day"]["net_profit"] == 100000
    assert after["settled_net_profit"] == 50000
    assert after["variance"] == 50000
    assert purchase["operation"]["kind"] == "PURCHASE"
