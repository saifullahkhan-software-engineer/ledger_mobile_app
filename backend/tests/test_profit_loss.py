"""Business profit and loss: live-weight purchases, frozen cost of sales, P&L lines.

Net profit = revenue - cost of sales - wastage loss - expenses. Everything here is
about the business itself; shares, wallets and settlements are not involved.
"""

from datetime import timedelta
from decimal import Decimal

from app.services import today

P = "/api/v1"


def post(c, path, h, payload, key="request-0001", status=200):
    r = c.post(P + path, json=payload, headers={**h, "Idempotency-Key": key})
    assert r.status_code == status, r.text
    return r.json()


def daily(
    c, h, kind, amount, quantity=0, key="daily-0001", business="chicken", status=200, **extra
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
        status,
    )


def patch(c, h, operation_id, payload, key="patch-0001", status=200):
    r = c.patch(
        P + f"/admin/operations/{operation_id}",
        json=payload,
        headers={**h, "Idempotency-Key": key},
    )
    assert r.status_code == status, r.text
    return r.json()


def stock(c, h, business="chicken"):
    body = c.get(P + f"/admin/stock?business_id={business}", headers=h).json()
    return Decimal(str(body["quantity"])), body["inventory_cost"]


def profit(day):
    return day["revenue"] - day["cost"] - day["expenses"]


def kg(value):
    return Decimal(str(value))


# --------------------------------------------------------------------------
# Live weight -> dressed meat (35% minimum processing loss, 65% maximum yield)
# --------------------------------------------------------------------------


def test_live_weight_purchase_stocks_65_percent_and_costs_the_dressed_kilo(
    client, admin_headers
):
    # Rs 15,000 buys 100 kg of live birds; at most 65 kg can be sold as meat.
    buy = daily(client, admin_headers, "PURCHASE", 1500000, 0, "live-buy-01", live_weight="100")
    assert kg(buy["operation"]["quantity"]) == 65
    assert kg(buy["operation"]["live_weight"]) == 100
    assert buy["yield_capped"] is False
    assert buy["stock"]["stock_cost"] == 1500000
    assert stock(client, admin_headers) == (Decimal("65"), 1500000)

    # 40 kg sold for Rs 12,000 costs 40/65 of Rs 15,000 = Rs 9,230.77 (Rs 230.77/kg),
    # not the Rs 150/kg of live weight.
    sale = daily(client, admin_headers, "SALE", 1200000, 40, "live-sale-01")
    assert sale["operation"]["cost"] == 923077
    assert profit(sale["day"]) == 276923


def test_dressed_weight_above_the_cap_is_clamped_and_reported(client, admin_headers):
    over = daily(
        client, admin_headers, "PURCHASE", 1500000, 70, "cap-over-001", live_weight="100"
    )
    assert kg(over["operation"]["quantity"]) == 65
    assert over["yield_capped"] is True

    # A lower real yield is kept as entered: the cap is a ceiling, not a target.
    under = daily(
        client, admin_headers, "PURCHASE", 1000000, 60, "cap-under-01", live_weight="100"
    )
    assert kg(under["operation"]["quantity"]) == 60
    assert under["yield_capped"] is False
    assert stock(client, admin_headers) == (Decimal("125"), 2500000)


def test_cap_rounds_down_to_the_gram(client, admin_headers):
    buy = daily(
        client, admin_headers, "PURCHASE", 100000, 0, "cap-round-01", live_weight="10.001"
    )
    assert kg(buy["operation"]["quantity"]) == Decimal("6.5")  # 6.50065 kg -> 6.500
    tiny = daily(
        client, admin_headers, "PURCHASE", 100000, 0, "cap-tiny-001", live_weight="0.001",
        status=422,
    )
    # 65% of one gram is under a gram: nothing sellable, refused by the quantity rule.
    assert tiny["detail"] == "Positive quantity required"


def test_live_weight_is_only_for_chicken_purchases(client, admin_headers):
    daily(client, admin_headers, "PURCHASE", 100000, 10, "lw-seed-0001")
    refused = [
        daily(client, admin_headers, "SALE", 50000, 1, "lw-sale-0001", live_weight="2", status=422),
        daily(client, admin_headers, "EXPENSE", 1000, 0, "lw-exp-00001", live_weight="2", status=422),
        daily(client, admin_headers, "BYPRODUCT", 1000, 0, "lw-byp-00001", live_weight="1", status=422),
        daily(
            client, admin_headers, "PURCHASE", 100000, 5, "lw-lpg-00001", business="lpg",
            live_weight="5", status=422,
        ),
    ]
    for body in refused:
        # Our own rule answered, not a schema rejection of an unknown field.
        assert body["detail"] == "Live weight is only supported on chicken purchases"
    zero = daily(
        client, admin_headers, "PURCHASE", 100000, 5, "lw-zero-0001", live_weight="0",
        status=422,
    )
    assert zero["detail"] == "Positive live weight required"


def test_purchase_without_live_weight_is_unchanged(client, admin_headers):
    buy = daily(client, admin_headers, "PURCHASE", 100000, 10, "plain-buy-001")
    assert buy["operation"]["live_weight"] is None
    assert buy["yield_capped"] is False
    assert kg(buy["operation"]["quantity"]) == 10


def test_correcting_a_live_purchase_keeps_dressed_weight_within_the_cap(
    client, admin_headers
):
    buy = daily(client, admin_headers, "PURCHASE", 1500000, 0, "edit-buy-001", live_weight="100")
    op_id = buy["operation"]["id"]

    r = patch(client, admin_headers, op_id, {"quantity": "70"}, "edit-cap-0001")
    assert kg(r["operation"]["quantity"]) == 65 and r["yield_capped"] is True

    r = patch(client, admin_headers, op_id, {"quantity": "60"}, "edit-cap-0002")
    assert kg(r["operation"]["quantity"]) == 60 and r["yield_capped"] is False
    assert stock(client, admin_headers) == (Decimal("60"), 1500000)

    # A smaller live weight pulls the dressed weight down to its new 65%.
    r = patch(client, admin_headers, op_id, {"live_weight": "80"}, "edit-cap-0003")
    assert kg(r["operation"]["quantity"]) == 52 and r["yield_capped"] is True
    assert stock(client, admin_headers) == (Decimal("52"), 1500000)


# --------------------------------------------------------------------------
# Cost of sales is frozen when the sale is booked
# --------------------------------------------------------------------------


def booked_sale(client, admin_headers, tag):
    """10 kg for Rs 1,000, 5 kg sold for Rs 1,000 (cost Rs 500), then a dearer purchase."""
    daily(client, admin_headers, "PURCHASE", 100000, 10, f"{tag}-buy-0001")
    sale = daily(client, admin_headers, "SALE", 100000, 5, f"{tag}-sale-001")
    daily(client, admin_headers, "PURCHASE", 300000, 10, f"{tag}-buy-0002")
    return sale["operation"]["id"], sale["day"]["id"]


def test_correcting_a_sale_amount_does_not_reprice_its_cost(client, admin_headers):
    op_id, _ = booked_sale(client, admin_headers, "amt")
    before = stock(client, admin_headers)
    assert before == (Decimal("15"), 350000)

    fixed = patch(client, admin_headers, op_id, {"amount": 90000}, "amt-fix-0001")
    assert fixed["operation"]["cost"] == 50000
    assert (fixed["day"]["revenue"], fixed["day"]["cost"]) == (90000, 50000)
    assert profit(fixed["day"]) == 40000
    assert stock(client, admin_headers) == before


def test_a_note_only_correction_leaves_profit_untouched(client, admin_headers):
    op_id, _ = booked_sale(client, admin_headers, "note")
    fixed = patch(client, admin_headers, op_id, {"note": "typo fix"}, "note-fix-001")
    assert fixed["operation"]["cost"] == 50000
    assert profit(fixed["day"]) == 50000


def test_a_weight_correction_scales_the_booked_unit_cost(client, admin_headers):
    op_id, _ = booked_sale(client, admin_headers, "qty")
    fixed = patch(client, admin_headers, op_id, {"quantity": "6"}, "qty-fix-0001")
    # Rs 100/kg when it was sold, not today's Rs 200/kg average.
    assert fixed["operation"]["cost"] == 60000
    assert fixed["day"]["cost"] == 60000
    assert stock(client, admin_headers) == (Decimal("14"), 340000)


def test_wastage_corrections_keep_their_booked_unit_cost(client, admin_headers):
    daily(client, admin_headers, "PURCHASE", 100000, 10, "ws-buy-00001")
    waste = daily(client, admin_headers, "WASTAGE", 0, 2, "ws-waste-001")
    assert waste["operation"]["cost"] == 20000
    daily(client, admin_headers, "PURCHASE", 300000, 10, "ws-buy-00002")
    op_id = waste["operation"]["id"]

    same = patch(client, admin_headers, op_id, {"note": "ice melted"}, "ws-note-0001")
    assert same["operation"]["cost"] == 20000
    more = patch(client, admin_headers, op_id, {"quantity": "3"}, "ws-qty-00001")
    assert more["operation"]["cost"] == 30000


def test_a_sale_with_wastage_corrects_against_its_own_unit_cost(client, admin_headers):
    daily(client, admin_headers, "PURCHASE", 100000, 10, "sw-buy-00001")
    sale = daily(client, admin_headers, "SALE", 80000, 4, "sw-sale-0001", wastage="0.5")
    assert sale["operation"]["cost"] == 45000
    daily(client, admin_headers, "PURCHASE", 300000, 10, "sw-buy-00002")
    op_id = sale["operation"]["id"]

    same = patch(client, admin_headers, op_id, {"amount": 70000}, "sw-amt-00001")
    assert same["operation"]["cost"] == 45000
    more = patch(client, admin_headers, op_id, {"wastage": "1"}, "sw-wst-00001")
    assert more["operation"]["cost"] == 50000  # 5 kg left at the booked Rs 100/kg


def test_a_correction_that_sells_the_last_kilos_takes_the_remaining_cost(
    client, admin_headers
):
    daily(client, admin_headers, "PURCHASE", 100000, 10, "last-buy-0001")
    sale = daily(client, admin_headers, "SALE", 50000, 4, "last-sale-001")
    op_id = sale["operation"]["id"]

    # More than the shop holds is still refused, and nothing is left half-applied.
    patch(client, admin_headers, op_id, {"quantity": "11"}, "last-over-001", status=409)
    assert stock(client, admin_headers) == (Decimal("6"), 60000)

    fixed = patch(client, admin_headers, op_id, {"quantity": "10"}, "last-fix-0001")
    assert fixed["operation"]["cost"] == 100000
    assert stock(client, admin_headers) == (Decimal("0"), 0)


def test_owner_correction_after_close_moves_profit_only_by_the_edit(client, admin_headers):
    # A later, dearer purchase must not leak into the profit of a corrected sale.
    op_id, day_id = booked_sale(client, admin_headers, "cls")
    closed = post(client, "/admin/ledger/close", admin_headers, {"day_id": day_id}, "cls-close-001")
    assert closed["day"]["net_profit"] == 50000

    fixed = patch(client, admin_headers, op_id, {"amount": 90000}, "cls-fix-0001")
    assert fixed["day"]["net_profit"] == 40000
    assert fixed["day"]["settled_net_profit"] == 50000
    assert fixed["day"]["variance"] == -10000


# --------------------------------------------------------------------------
# Profit and loss lines: revenue - cost of sales - wastage loss - expenses
# --------------------------------------------------------------------------


def pnl_day(client, admin_headers):
    daily(client, admin_headers, "PURCHASE", 100000, 10, "pl-buy-00001")
    # Cost 45,000 for 4.5 kg out: 40,000 for the 4 kg sold + 5,000 for the 0.5 kg wasted.
    daily(client, admin_headers, "SALE", 80000, 4, "pl-sale-0001", wastage="0.5")
    daily(client, admin_headers, "BYPRODUCT", 10000, 0, "pl-byp-00001")
    # 1 kg of the 5.5 kg left (carrying cost 55,000) is a 10,000 loss with no revenue.
    daily(client, admin_headers, "WASTAGE", 0, 1, "pl-waste-001")
    last = daily(client, admin_headers, "EXPENSE", 5000, 0, "pl-exp-00001", category="Electricity")
    return last["day"]


def test_day_exposes_cost_of_sales_and_wastage_loss_separately(client, admin_headers):
    day = pnl_day(client, admin_headers)
    assert (day["revenue"], day["cost"], day["expenses"]) == (90000, 55000, 5000)
    assert (day["cogs"], day["wastage_cost"]) == (40000, 15000)
    assert day["cogs"] + day["wastage_cost"] == day["cost"]
    assert day["revenue"] - day["cogs"] - day["wastage_cost"] - day["expenses"] == 30000

    # The same lines come back from the summary, the day list and the day detail.
    summary = client.get(
        P + f"/admin/businesses/chicken/summary?on={today()}", headers=admin_headers
    ).json()
    assert (summary["cogs"], summary["wastage_cost"]) == (40000, 15000)
    listed = client.get(
        P + "/admin/ledger/daily?business_id=chicken", headers=admin_headers
    ).json()[0]
    assert (listed["cogs"], listed["wastage_cost"]) == (40000, 15000)
    detail = client.get(P + f"/admin/ledger/{day['id']}", headers=admin_headers).json()
    assert (detail["cogs"], detail["wastage_cost"]) == (40000, 15000)

    # A date with no records has no lines at all.
    empty = client.get(
        P + f"/admin/businesses/chicken/summary?on={today() - timedelta(days=1)}",
        headers=admin_headers,
    ).json()
    assert empty["cogs"] is None and empty["wastage_cost"] is None


def test_report_lines_add_up_to_net_profit(client, admin_headers):
    pnl_day(client, admin_headers)
    report = client.get(
        P + f"/admin/reports?start={today()}&end={today()}&business_id=chicken",
        headers=admin_headers,
    ).json()
    row = report["businesses"][0]
    assert (row["revenue"], row["cogs"], row["wastage_cost"], row["expenses"]) == (
        90000, 40000, 15000, 5000,
    )
    assert row["cost_and_expenses"] == 60000 and row["net_profit"] == 30000
    assert (report["total_cogs"], report["total_wastage_cost"], report["total_expenses"]) == (
        40000, 15000, 5000,
    )
    assert (
        report["total_sales"]
        - report["total_cogs"]
        - report["total_wastage_cost"]
        - report["total_expenses"]
        == report["total_profit"]
    )
    dashboard = client.get(P + "/admin/dashboard", headers=admin_headers).json()
    assert dashboard["total_cogs"] == 40000 and dashboard["total_wastage_cost"] == 15000


def test_broiler_report_counts_batch_costs_as_expenses(client, admin_headers):
    batch = post(
        client,
        "/admin/batch/create",
        admin_headers,
        {
            "business_id": "broiler",
            "name": "Batch A",
            "chicks": 100,
            "total_shares": 10,
            "share_price": 10000,
            "initial_cost": 100000,
        },
        "bro-create-001",
    )
    post(client, f"/admin/batch/{batch['id']}/start", admin_headers, {}, "bro-start-0001")
    post(
        client,
        f"/admin/batch/{batch['id']}/harvest",
        admin_headers,
        {"yield_kg": "100", "price_per_kg": 1500},
        "bro-harvest-01",
    )
    report = client.get(
        P + f"/admin/reports?start={today()}&end={today()}&business_id=broiler",
        headers=admin_headers,
    ).json()
    row = report["businesses"][0]
    assert (row["revenue"], row["cogs"], row["wastage_cost"], row["expenses"]) == (
        150000, 0, 0, 100000,
    )
    assert row["net_profit"] == 50000
