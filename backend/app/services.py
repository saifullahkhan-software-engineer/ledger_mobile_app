import hashlib
import json
from datetime import datetime
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
)


def today():
    return datetime.now(ZoneInfo("Asia/Karachi")).date()


def fail(message, status=409):
    raise HTTPException(status, message)


def get(db, model, ident):
    obj = db.get(model, ident)
    if not obj:
        fail(f"{model.__name__} not found", 404)
    return obj


def data(obj):
    return jsonable_encoder(
        {c.name: getattr(obj, c.name) for c in obj.__table__.columns}
    )


def allowed(db, user, business_id):
    business = get(db, Business, business_id)
    if user.role != "SUPERADMIN" and not db.get(Assignment, (user.id, business_id)):
        fail("Business access denied", 403)
    return business


def business_ids(db, user):
    if user.role == "SUPERADMIN":
        return list(db.scalars(select(Business.id)))
    return list(
        db.scalars(select(Assignment.business_id).where(Assignment.user_id == user.id))
    )


def wallet(db, user_id):
    return db.scalar(
        select(func.coalesce(func.sum(Posting.amount), 0)).where(
            Posting.account == f"wallet:{user_id}"
        )
    )


def journal(db, reference, kind, lines):
    if sum(lines.values()) != 0:
        raise ValueError("Unbalanced journal")
    entry = Journal(reference=reference, kind=kind)
    db.add(entry)
    db.flush()
    for account, amount in lines.items():
        if amount:
            db.add(Posting(journal_id=entry.id, account=account, amount=amount))
    db.flush()
    return entry


def once(db, user, scope, key, payload, action):
    # Recheck permissions even on a cached response after an assignment revocation.
    if user.role == "ADMIN":
        if "business_id" in payload:
            allowed(db, user, payload["business_id"])
        elif "day_id" in payload:
            allowed(db, user, get(db, Day, payload["day_id"]).business_id)
        elif scope.startswith(("batch-start:", "batch-update:", "harvest:")):
            allowed(db, user, get(db, Batch, scope.split(":", 1)[1]).business_id)
    digest = hashlib.sha256(
        json.dumps(jsonable_encoder(payload), sort_keys=True).encode()
    ).hexdigest()
    scope = f"{user.id}:{scope}"
    old = db.get(Idempotency, (scope, key))
    if old:
        if old.digest != digest:
            fail("Idempotency key reused with different input")
        return old.response
    result = jsonable_encoder(action())
    db.add(Idempotency(scope=scope, key=key, digest=digest, response=result))
    db.flush()
    return result


def owned(db, business_id, batch_id=None):
    rows = db.scalars(
        select(Ownership).where(
            Ownership.business_id == business_id, Ownership.batch_id == batch_id
        )
    ).all()
    result = {}
    for row in rows:
        result[row.user_id] = result.get(row.user_id, 0) + row.shares
    return result


def settle(db, business, source, profit, total_shares, holders, principal=0):
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
    db.flush()
    lines = {f"wallet:{u}": amount for u, amount in payouts.items()}
    lines[f"business:{business.id}"] = -distributed
    journal(db, source, "BATCH_SETTLEMENT" if principal else "DIVIDEND", lines)
    return data(result)


def operation(db, user, p):
    b = allowed(db, user, p.business_id)
    if b.type == "BROILER":
        fail("Use batch operations for broiler")
    if p.date != today():
        fail("Daily records must use the current Asia/Karachi business date", 422)
    pending = db.scalar(
        select(Day).where(
            Day.business_id == b.id, Day.status == "OPEN", Day.date < p.date
        )
    )
    if pending:
        fail("Close the previous open day first")
    day = db.scalar(select(Day).where(Day.business_id == b.id, Day.date == p.date))
    if day and day.status != "OPEN":
        fail("Day is already closed")
    if not day:
        day = Day(business_id=b.id, date=p.date)
        db.add(day)
        db.flush()
    if p.supplier_id:
        supplier = get(db, Supplier, p.supplier_id)
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
    cost = 0
    if p.kind == "PURCHASE":
        b.stock += p.quantity
        b.stock_cost += p.amount
    elif p.kind == "SALE":
        if p.quantity > b.stock:
            fail("Insufficient stock")
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
    db.flush()
    cash = p.amount if p.kind in ("SALE", "BYPRODUCT") else -p.amount
    journal(
        db,
        f"operation:{row.id}",
        p.kind,
        {f"business:{b.id}": cash, "external:operations": -cash},
    )
    return {"operation": data(row), "day": data(day), "stock": data(b)}


def buy(db, user, p):
    b = get(db, Business, p.business_id)
    if b.type == "BROILER":
        if not p.batch_id:
            fail("Broiler purchases require batch_id", 422)
        batch = get(db, Batch, p.batch_id)
        if batch.business_id != b.id or batch.status != "FUNDING":
            fail("Batch is not open for investment")
        total, price = batch.total_shares, batch.share_price
    else:
        if p.batch_id:
            fail("Running business cannot have batch_id", 422)
        # No buying after today's operations start: no last-minute capture of known profit.
        if db.scalar(
            select(Day.id).where(
                Day.business_id == b.id,
                ((Day.date == today()) | (Day.status == "OPEN")),
            )
        ):
            fail(
                "Daily ownership cutoff reached; buy before the first operation of the next day"
            )
        total, price = b.total_shares, b.share_price
    holders = owned(db, b.id, p.batch_id)
    if sum(holders.values()) + p.shares > total:
        fail("Not enough shares available")
    amount = p.shares * price
    if wallet(db, user.id) < amount:
        fail("Insufficient wallet funds")
    row = Ownership(
        user_id=user.id,
        business_id=b.id,
        batch_id=p.batch_id,
        shares=p.shares,
        paid=amount,
    )
    db.add(row)
    db.flush()
    journal(
        db,
        f"buy:{row.id}",
        "SHARE_PURCHASE",
        {f"wallet:{user.id}": -amount, f"business:{b.id}": amount},
    )
    return {"investment": data(row), "wallet_balance": wallet(db, user.id)}
