import hashlib
import json
import os
from datetime import datetime, timedelta
from decimal import ROUND_HALF_UP, Decimal
from zoneinfo import ZoneInfo

from fastapi import HTTPException
from fastapi.encoders import jsonable_encoder
from sqlalchemy import func, select

from .models import (
    Assignment,
    Batch,
    Business,
    Day,
    Idempotency,
    Journal,
    Operation,
    Ownership,
    Posting,
    Settlement,
    Supplier,
    uid,
)


def today():
    return datetime.now(ZoneInfo("Asia/Karachi")).date()


# Business days close themselves once their date has ended: by default at
# midnight (00:00) Pakistan time. Set DAY_CLOSE_HOUR=12 to close each day at
# noon the next day instead (a grace period for late-night entry).
DAY_CLOSE_HOUR = int(os.getenv("DAY_CLOSE_HOUR", "0"))
if not 0 <= DAY_CLOSE_HOUR <= 23:
    raise RuntimeError("DAY_CLOSE_HOUR must be an hour of the day (0-23)")


def cutoff_date(moment=None):
    """Latest business date whose automatic close time has already passed."""
    moment = moment or datetime.now(ZoneInfo("Asia/Karachi"))
    return (moment - timedelta(days=1, hours=DAY_CLOSE_HOUR)).date()


def fail(message, status=409):
    raise HTTPException(status, message)


async def get(db, model, ident):
    result = await db.execute(select(model).where(model.id == ident))
    obj = result.scalar_one_or_none()
    if not obj:
        fail(f"{model.__name__} not found", 404)
    return obj


def data(obj):
    return jsonable_encoder(
        {c.name: getattr(obj, c.name) for c in obj.__table__.columns}
    )


async def allowed(db, user, business_id):
    business = await get(db, Business, business_id)
    if user.role != "SUPERADMIN":
        result = await db.execute(select(Assignment).where(Assignment.user_id == user.id, Assignment.business_id == business_id))
        if not result.scalar_one_or_none():
            fail("Business access denied", 403)
    return business


async def business_ids(db, user):
    if user.role == "SUPERADMIN":
        result = await db.execute(select(Business.id))
        return list(result.scalars())
    result = await db.execute(select(Assignment.business_id).where(Assignment.user_id == user.id))
    return list(result.scalars())


async def wallet(db, user_id):
    result = await db.execute(
        select(func.coalesce(func.sum(Posting.amount), 0)).where(
            Posting.account == f"wallet:{user_id}"
        )
    )
    return result.scalar()


async def journal(db, reference, kind, lines):
    if sum(lines.values()) != 0:
        raise ValueError("Unbalanced journal")
    entry = Journal(reference=reference, kind=kind)
    db.add(entry)
    await db.flush()
    for account, amount in lines.items():
        if amount:
            db.add(Posting(journal_id=entry.id, account=account, amount=amount))
    await db.flush()
    return entry


async def once(db, user, scope, key, payload, action):
    # Recheck permissions even on a cached response after an assignment revocation.
    if user.role == "ADMIN":
        if "business_id" in payload:
            await allowed(db, user, payload["business_id"])
        elif "day_id" in payload:
            day = await get(db, Day, payload["day_id"])
            await allowed(db, user, day.business_id)
        elif scope.startswith(("batch-start:", "batch-update:", "harvest:")):
            batch = await get(db, Batch, scope.split(":", 1)[1])
            await allowed(db, user, batch.business_id)
    digest = hashlib.sha256(
        json.dumps(jsonable_encoder(payload), sort_keys=True).encode()
    ).hexdigest()
    scope = f"{user.id}:{scope}"
    result = await db.execute(select(Idempotency).where(Idempotency.scope == scope, Idempotency.key == key))
    old = result.scalar_one_or_none()
    if old:
        if old.digest != digest:
            fail("Idempotency key reused with different input")
        return old.response
    result = jsonable_encoder(await action())
    db.add(Idempotency(scope=scope, key=key, digest=digest, response=result))
    await db.flush()
    return result


async def owned(db, business_id, batch_id=None):
    result = await db.execute(
        select(Ownership).where(
            Ownership.business_id == business_id, Ownership.batch_id == batch_id
        )
    )
    rows = result.scalars().all()
    result_dict = {}
    for row in rows:
        result_dict[row.user_id] = result_dict.get(row.user_id, 0) + row.shares
    return result_dict


async def settle(db, business, source, profit, total_shares, holders, principal=0):
    # Unsold equity belongs economically to the operator. Floor to paisa;
    # all undistributed/rounding amounts are explicitly retained, never lost.
    pool = max(0, principal + profit)
    payouts = {
        u: pool * shares // total_shares for u, shares in sorted(holders.items())
    }
    distributed = sum(payouts.values())
    result = Settlement(
        source=source,
        business_id=business.id,
        net_profit=profit,
        distributed=distributed,
        retained=pool - distributed,
        snapshot={
            "shares": holders,
            "total_shares": total_shares,
            "payouts": payouts,
            "principal": principal,
        },
    )
    db.add(result)
    await db.flush()
    lines = {f"wallet:{u}": amount for u, amount in payouts.items()}
    lines[f"business:{business.id}"] = -distributed
    await journal(db, source, "BATCH_SETTLEMENT" if principal else "DIVIDEND", lines)
    return data(result)


async def close_day_record(db, business, day):
    """Finalize one day: fix its summary and distribute eligible investor profit."""
    if day.status != "OPEN":
        fail("Day already settled")
    day.net_profit = day.revenue - day.cost - day.expenses
    day.status = "CLOSED"
    settlement = await settle(
        db,
        business,
        f"day:{day.id}",
        day.net_profit,
        business.total_shares,
        await owned(db, business.id),
    )
    return {"day": data(day), "settlement": settlement}


async def auto_close_due_days(db, business_id=None):
    """Close every open day whose Pakistan business date has ended.

    Reporting endpoints sweep all businesses so a status is never stale; write
    paths may pass a ``business_id`` to settle just the business they touch.
    Running here also means the midnight rule does not depend on a scheduler.
    ``manage.py auto-close-days`` performs the same sweep from cron for
    installations that want the close to happen on time even when nobody opens
    the app. A manual ``/admin/ledger/close`` still closes today early.
    """
    query = (
        select(Day)
        .where(Day.status == "OPEN", Day.date <= cutoff_date())
        .order_by(Day.date)
    )
    if business_id:
        query = query.where(Day.business_id == business_id)
    result = await db.execute(query)
    closed = []
    for day in result.scalars().all():
        business = await get(db, Business, day.business_id)
        await close_day_record(db, business, day)
        closed.append(day)
    return closed


async def day_payloads(db, days):
    """Day summaries plus what each day actually settled for.

    An owner correction after the close rebuilds the summary but never rewrites
    the payout, so both figures are returned and their difference is explicit.
    """
    if not days:
        return []
    result = await db.execute(
        select(Settlement).where(
            Settlement.source.in_([f"day:{day.id}" for day in days])
        )
    )
    settled = {row.source: row.net_profit for row in result.scalars()}
    payloads = []
    for day in days:
        original = settled.get(f"day:{day.id}")
        payload = data(day)
        payload["settled_net_profit"] = original
        payload["variance"] = None if original is None else day.net_profit - original
        payloads.append(payload)
    return payloads


async def day_payload(db, day):
    return (await day_payloads(db, [day]))[0]


async def recompute_day_totals(db, day):
    """Rebuild a day's summary from its operations after an owner correction."""
    result = await db.execute(select(Operation).where(Operation.day_id == day.id))
    rows = result.scalars().all()
    day.revenue = sum(r.amount for r in rows if r.kind in ("SALE", "BYPRODUCT"))
    day.cost = sum(r.cost for r in rows if r.kind == "SALE")
    day.expenses = sum(r.amount for r in rows if r.kind == "EXPENSE")
    if day.status == "CLOSED":
        # The day is settled, so its summary profit follows the corrected records.
        day.net_profit = day.revenue - day.cost - day.expenses
    await db.flush()
    return day


async def operation(db, user, p):
    b = await allowed(db, user, p.business_id)
    if b.type == "BROILER":
        fail("Use batch operations for broiler")
    if p.date != today():
        fail("Daily records must use the current Asia/Karachi business date", 422)
    # Midnight rule: days past their business date settle themselves before the
    # first record of the new date is written.
    await auto_close_due_days(db, b.id)
    result = await db.execute(
        select(Day).where(
            Day.business_id == b.id, Day.status == "OPEN", Day.date < p.date
        )
    )
    pending = result.scalar_one_or_none()
    if pending:
        fail("Close the previous open day first")
    result = await db.execute(select(Day).where(Day.business_id == b.id, Day.date == p.date))
    day = result.scalar_one_or_none()
    if day and day.status != "OPEN":
        fail("Day is already closed")
    if not day:
        day = Day(business_id=b.id, date=p.date)
        db.add(day)
        await db.flush()
    if p.supplier_id:
        supplier = await get(db, Supplier, p.supplier_id)
        if supplier.business_id != b.id:
            fail("Supplier belongs to another business", 422)
    if p.kind in ("PURCHASE", "SALE") and p.quantity <= 0:
        fail("Positive quantity required", 422)
    if b.type == "LPG" and p.quantity != p.quantity.to_integral_value():
        fail("Cylinder quantity must be an integer", 422)
    if b.type == "LPG" and p.kind == "BYPRODUCT":
        fail("Byproduct sales are chicken-only", 422)
    if b.type == "LPG" and p.kind == "SALE" and not p.channel:
        fail("LPG sales require RETAIL or COMMERCIAL channel", 422)
    if p.kind == "EXPENSE" and p.quantity:
        fail("Expenses cannot change stock", 422)
    if p.count is not None:
        if b.type != "CHICKEN":
            fail("Bird count is only supported for chicken businesses", 422)
        if p.kind not in ("PURCHASE", "SALE"):
            fail("Bird count is only supported for purchases and sales", 422)
    if p.category is not None and p.kind != "EXPENSE":
        fail("Category is only supported for expenses", 422)
    cost = 0
    if p.kind == "PURCHASE":
        b.stock += p.quantity
        b.stock_cost += p.amount
        b.stock_count += p.count or 0
    elif p.kind == "SALE":
        if p.quantity > b.stock:
            fail("Insufficient stock")
        if (p.count or 0) > b.stock_count:
            fail("Insufficient bird count in stock", 422)
        cost = (
            b.stock_cost
            if p.quantity == b.stock
            else int(
                (Decimal(b.stock_cost) * p.quantity / b.stock).quantize(
                    Decimal(1), rounding=ROUND_HALF_UP
                )
            )
        )
        b.stock -= p.quantity
        b.stock_cost -= cost
        b.stock_count -= p.count or 0
        day.cost += cost
        day.revenue += p.amount
    elif p.kind == "BYPRODUCT":
        day.revenue += p.amount
    else:
        day.expenses += p.amount
    row = Operation(
        day_id=day.id, **p.model_dump(exclude={"business_id", "date"}), cost=cost
    )
    db.add(row)
    await db.flush()
    cash = p.amount if p.kind in ("SALE", "BYPRODUCT") else -p.amount
    await journal(
        db,
        f"operation:{row.id}",
        p.kind,
        {f"business:{b.id}": cash, "external:operations": -cash},
    )
    return {"operation": data(row), "day": data(day), "stock": data(b)}


def _apply_operation_effect(b, day, kind, quantity, amount, cost, count, sign):
    # sign=-1 reverses a recorded effect, sign=+1 applies it. Money always
    # moves as the recorded total price; weight/count only move stock.
    if kind == "PURCHASE":
        b.stock += sign * quantity
        b.stock_cost += sign * amount
        b.stock_count += sign * (count or 0)
    elif kind == "SALE":
        b.stock -= sign * quantity
        b.stock_cost -= sign * cost
        b.stock_count -= sign * (count or 0)
        day.cost += sign * cost
        day.revenue += sign * amount
    elif kind == "BYPRODUCT":
        day.revenue += sign * amount
    else:
        day.expenses += sign * amount


async def edit_operation(db, user, operation_id, p):
    op = await get(db, Operation, operation_id)
    day = await get(db, Day, op.day_id)
    b = await get(db, Business, day.business_id)
    # Owner-only route (Depends(root)). A manager can only ever touch today's
    # open day; the super admin may also correct a previous, already settled
    # date. Such a correction rebuilds the date summary but never rewrites the
    # payout that was already distributed — the gap is reported as `variance`.
    if day.status != "OPEN" and user.role != "SUPERADMIN":
        fail("Only the super admin can correct a closed day")
    provided = p.model_fields_set
    quantity = p.quantity if "quantity" in provided else op.quantity
    count = p.count if "count" in provided else op.count
    amount = p.amount if "amount" in provided else op.amount
    category = p.category if "category" in provided else op.category
    note = p.note if "note" in provided else op.note
    kind = op.kind
    if kind in ("PURCHASE", "SALE") and quantity <= 0:
        fail("Positive quantity required", 422)
    if b.type == "LPG" and quantity != quantity.to_integral_value():
        fail("Cylinder quantity must be an integer", 422)
    if kind == "EXPENSE" and quantity:
        fail("Expenses cannot change stock", 422)
    if count is not None and (b.type != "CHICKEN" or kind not in ("PURCHASE", "SALE")):
        fail("Bird count is only supported for chicken purchases and sales", 422)
    if category is not None and kind != "EXPENSE":
        fail("Category is only supported for expenses", 422)
    _apply_operation_effect(b, day, kind, op.quantity, op.amount, op.cost, op.count, -1)
    new_cost = 0
    if kind == "SALE":
        if quantity > b.stock:
            fail("Insufficient stock", 422)
        if (count or 0) > b.stock_count:
            fail("Insufficient bird count in stock", 422)
        new_cost = (
            b.stock_cost
            if quantity == b.stock
            else int(
                (Decimal(b.stock_cost) * quantity / b.stock).quantize(
                    Decimal(1), rounding=ROUND_HALF_UP
                )
            )
        )
    _apply_operation_effect(b, day, kind, quantity, amount, new_cost, count, +1)
    if b.stock < 0 or b.stock_cost < 0 or b.stock_count < 0:
        fail("Correction would make stock negative", 422)
    old_cash = op.amount if kind in ("SALE", "BYPRODUCT") else -op.amount
    new_cash = amount if kind in ("SALE", "BYPRODUCT") else -amount
    delta = new_cash - old_cash
    if delta:
        await journal(
            db,
            f"operation-adjust:{op.id}:{uid()}",
            kind,
            {f"business:{b.id}": delta, "external:operations": -delta},
        )
    op.quantity = quantity
    op.count = count
    op.amount = amount
    op.cost = new_cost
    op.category = category
    op.note = note
    await db.flush()
    await recompute_day_totals(db, day)
    return {
        "operation": data(op),
        "day": await day_payload(db, day),
        "business": data(b),
        "stock": data(b),
    }


async def buy(db, user, p):
    b = await get(db, Business, p.business_id)
    if b.type == "BROILER":
        if not p.batch_id:
            fail("Broiler purchases require batch_id", 422)
        batch = await get(db, Batch, p.batch_id)
        if batch.business_id != b.id or batch.status != "FUNDING":
            fail("Batch is not open for investment")
        total, price = batch.total_shares, batch.share_price
    else:
        if p.batch_id:
            fail("Running business cannot have batch_id", 422)
        # Midnight rule: settle a finished day before checking the cutoff, so a
        # day that closed automatically does not block purchases on the new date.
        await auto_close_due_days(db, b.id)
        # No buying after today's operations start: no last-minute capture of known profit.
        result = await db.execute(
            select(Day.id)
            .where(
                Day.business_id == b.id,
                ((Day.date == today()) | (Day.status == "OPEN")),
            )
            .limit(1)
        )
        if result.scalar_one_or_none():
            fail(
                "Daily ownership cutoff reached; buy before the first operation of the next day"
            )
        total, price = b.total_shares, b.share_price
    holders = await owned(db, b.id, p.batch_id)
    if sum(holders.values()) + p.shares > total:
        fail("Not enough shares available")
    amount = p.shares * price
    balance = await wallet(db, user.id)
    if balance < amount:
        fail("Insufficient wallet funds")
    row = Ownership(
        user_id=user.id,
        business_id=b.id,
        batch_id=p.batch_id,
        shares=p.shares,
        paid=amount,
    )
    db.add(row)
    await db.flush()
    await journal(
        db,
        f"buy:{row.id}",
        "SHARE_PURCHASE",
        {f"wallet:{user.id}": -amount, f"business:{b.id}": amount},
    )
    balance = await wallet(db, user.id)
    return {"investment": data(row), "wallet_balance": balance}
