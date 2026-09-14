from datetime import date, timedelta
from decimal import ROUND_HALF_UP, Decimal
from typing import Annotated

from fastapi import Depends, FastAPI, Header, Query
from fastapi.responses import JSONResponse, RedirectResponse
from sqlalchemy import func, select, text
from sqlalchemy.exc import IntegrityError

from .db import get_db
from .models import (
    Assignment,
    Batch,
    BatchLog,
    Business,
    Day,
    Journal,
    Operation,
    Ownership,
    Posting,
    Settlement,
    Supplier,
    User,
    Withdrawal,
)
from .schemas import (
    BatchCreate,
    BatchUpdate,
    BusinessCreate,
    Buy,
    ChangePassword,
    CloseDay,
    DailyInput,
    Deposit,
    Harvest,
    Login,
    Profile,
    Register,
    ResolveWithdrawal,
    SupplierCreate,
    Withdraw,
)
from .security import (
    DUMMY_HASH,
    MOCK,
    MODE,
    admin,
    current_user,
    investor,
    passwords,
    root,
    token,
    verified,
)
from .services import (
    allowed,
    business_ids,
    buy,
    data,
    fail,
    get,
    journal,
    once,
    operation,
    owned,
    settle,
    today,
    wallet,
)

app = FastAPI(
    title="Ahsan Traders API",
    version="1.0.0",
    description="Admin operations and fractional ownership MVP. All money uses integer paisa. See README for settlement rules and integration boundaries.",
)
Key = Annotated[str, Header(alias="Idempotency-Key", min_length=8, max_length=100)]
DB = Annotated[object, Depends(get_db, scope="function")]


@app.exception_handler(IntegrityError)
async def conflict(_, exc):
    return JSONResponse(
        status_code=409,
        content={"detail": "Duplicate record or database constraint violation"},
    )


@app.get("/", include_in_schema=False)
def index():
    return RedirectResponse("/docs")


@app.get("/health", tags=["System"])
def health(db: DB):
    db.execute(text("SELECT 1"))
    return {"status": "ok", "environment": MODE, "mock_payments": MOCK}


@app.post("/api/v1/auth/register", tags=["Authentication"], status_code=201)
def register(p: Register, db: DB):
    if db.scalar(select(User).where(User.phone == p.phone)):
        fail("Phone already registered")
    u = User(phone=p.phone, name=p.name, password_hash=passwords.hash(p.password))
    db.add(u)
    db.flush()
    return {
        "id": u.id,
        "kyc_status": u.kyc_status,
        "access_token": token(u),
        "token_type": "bearer",
    }


@app.post("/api/v1/auth/login", tags=["Authentication"])
def login(p: Login, db: DB):
    u = db.scalar(select(User).where(User.phone == p.phone))
    valid = passwords.verify(p.password, u.password_hash if u else DUMMY_HASH)
    if not valid or not u:
        fail("Invalid credentials", 401)
    return {
        "access_token": token(u),
        "token_type": "bearer",
        "expires_in": 3600,
        "role": u.role,
    }


@app.get("/api/v1/me", tags=["Profile"])
def me(u=Depends(current_user)):
    return {
        k: v for k, v in data(u).items() if k not in ("password_hash", "token_version")
    }


@app.patch("/api/v1/me", tags=["Profile"])
def profile(p: Profile, db: DB, u=Depends(current_user)):
    u.name, u.language = p.name, p.language
    db.flush()
    return me(u)


@app.post("/api/v1/auth/change-password", tags=["Authentication"])
def change_password(p: ChangePassword, db: DB, u=Depends(current_user)):
    if not passwords.verify(p.old_password, u.password_hash):
        fail("Incorrect password", 401)
    u.password_hash = passwords.hash(p.new_password)
    u.token_version += 1
    return {"message": "Password changed; log in again on all devices"}


@app.post("/api/v1/auth/logout", tags=["Authentication"])
def logout(db: DB, u=Depends(current_user)):
    u.token_version += 1
    return {"message": "All existing tokens revoked"}


@app.post("/api/v1/admin/businesses", tags=["Administration"], status_code=201)
def create_business(p: BusinessCreate, db: DB, u=Depends(root)):
    b = Business(**p.model_dump())
    db.add(b)
    db.flush()
    return data(b)


@app.post("/api/v1/admin/managers", tags=["Administration"], status_code=201)
def create_manager(p: Register, db: DB, u=Depends(root)):
    manager = User(
        phone=p.phone,
        name=p.name,
        password_hash=passwords.hash(p.password),
        role="ADMIN",
    )
    db.add(manager)
    db.flush()
    return {"id": manager.id, "name": manager.name}


@app.put(
    "/api/v1/admin/businesses/{business_id}/managers/{manager_id}",
    tags=["Administration"],
)
def assign(business_id: str, manager_id: str, db: DB, u=Depends(root)):
    get(db, Business, business_id)
    manager = get(db, User, manager_id)
    if manager.role != "ADMIN":
        fail("User must be a manager", 422)
    if not db.get(Assignment, (manager_id, business_id)):
        db.add(Assignment(user_id=manager_id, business_id=business_id))
    return {"assigned": True}


@app.delete(
    "/api/v1/admin/businesses/{business_id}/managers/{manager_id}",
    tags=["Administration"],
)
def unassign(business_id: str, manager_id: str, db: DB, u=Depends(root)):
    row = db.get(Assignment, (manager_id, business_id))
    if row:
        db.delete(row)
    return {"assigned": False}


@app.get("/api/v1/admin/businesses", tags=["Administration"])
def admin_businesses(db: DB, u=Depends(admin)):
    return [
        data(b)
        for b in db.scalars(
            select(Business).where(Business.id.in_(business_ids(db, u)))
        )
    ]


@app.post("/api/v1/admin/suppliers", tags=["Suppliers"], status_code=201)
def supplier_create(p: SupplierCreate, db: DB, u=Depends(admin)):
    allowed(db, u, p.business_id)
    s = Supplier(**p.model_dump())
    db.add(s)
    db.flush()
    return data(s)


@app.get("/api/v1/admin/suppliers", tags=["Suppliers"])
def suppliers(business_id: str, db: DB, u=Depends(admin)):
    allowed(db, u, business_id)
    return [
        data(s)
        for s in db.scalars(select(Supplier).where(Supplier.business_id == business_id))
    ]


@app.get("/api/v1/admin/suppliers/{supplier_id}/bills", tags=["Suppliers"])
def supplier_bills(
    supplier_id: str,
    db: DB,
    u=Depends(admin),
    offset: int = Query(0, ge=0),
    limit: int = Query(50, ge=1, le=200),
):
    s = get(db, Supplier, supplier_id)
    allowed(db, u, s.business_id)
    return [
        data(r)
        for r in db.scalars(
            select(Operation)
            .where(Operation.supplier_id == s.id, Operation.kind == "PURCHASE")
            .order_by(Operation.created_at.desc(), Operation.id)
            .offset(offset)
            .limit(limit)
        )
    ]


@app.get("/api/v1/admin/stock", tags=["Operations"])
def stock(business_id: str, db: DB, u=Depends(admin)):
    b = allowed(db, u, business_id)
    batches = db.scalars(
        select(Batch).where(Batch.business_id == b.id, Batch.status != "HARVESTED")
    ).all()
    return {
        "business_id": b.id,
        "quantity": b.stock,
        "unit": "kg"
        if b.type == "CHICKEN"
        else "cylinders"
        if b.type == "LPG"
        else "birds",
        "inventory_cost": b.stock_cost,
        "live_birds": sum(x.chicks - x.deaths for x in batches),
    }


@app.post("/api/v1/admin/ledger/daily", tags=["Operations"])
def daily(p: DailyInput, db: DB, key: Key, u=Depends(admin)):
    return once(db, u, "daily", key, p.model_dump(), lambda: operation(db, u, p))


@app.get("/api/v1/admin/ledger/daily", tags=["Operations"])
def daily_list(
    business_id: str,
    db: DB,
    u=Depends(admin),
    offset: int = Query(0, ge=0),
    limit: int = Query(50, ge=1, le=200),
):
    allowed(db, u, business_id)
    return [
        data(d)
        for d in db.scalars(
            select(Day)
            .where(Day.business_id == business_id)
            .order_by(Day.date.desc())
            .offset(offset)
            .limit(limit)
        )
    ]


@app.get("/api/v1/admin/ledger/{day_id}/operations", tags=["Operations"])
def operations(day_id: str, db: DB, u=Depends(admin)):
    d = get(db, Day, day_id)
    allowed(db, u, d.business_id)
    return [
        data(r)
        for r in db.scalars(
            select(Operation)
            .where(Operation.day_id == day_id)
            .order_by(Operation.created_at, Operation.id)
        )
    ]


@app.post("/api/v1/admin/ledger/close", tags=["Settlement"])
def close_day(p: CloseDay, db: DB, key: Key, u=Depends(admin)):
    def action():
        d = get(db, Day, p.day_id)
        b = allowed(db, u, d.business_id)
        if d.status != "OPEN":
            fail("Day already settled")
        d.net_profit = d.revenue - d.cost - d.expenses
        d.status = "CLOSED"
        result = settle(
            db, b, f"day:{d.id}", d.net_profit, b.total_shares, owned(db, b.id)
        )
        return {"day": data(d), "settlement": result}

    return once(db, u, "close", key, p.model_dump(), action)


@app.post("/api/v1/admin/batch/create", tags=["Broiler"])
def create_batch(p: BatchCreate, db: DB, key: Key, u=Depends(admin)):
    def action():
        b = allowed(db, u, p.business_id)
        if b.type != "BROILER":
            fail("Batch requires broiler business", 422)
        batch = Batch(**p.model_dump(exclude={"initial_cost"}), expenses=p.initial_cost)
        db.add(batch)
        db.flush()
        journal(
            db,
            f"batch-initial:{batch.id}",
            "BATCH_COST",
            {
                f"business:{b.id}": -p.initial_cost,
                "external:operations": p.initial_cost,
            },
        )
        return data(batch)

    return once(db, u, "batch-create", key, p.model_dump(), action)


@app.post("/api/v1/admin/batch/{batch_id}/start", tags=["Broiler"])
def start_batch(batch_id: str, db: DB, key: Key, u=Depends(admin)):
    def action():
        b = get(db, Batch, batch_id)
        allowed(db, u, b.business_id)
        if b.status != "FUNDING":
            fail("Batch already started")
        b.status = "ACTIVE"
        b.started_on = today()
        db.flush()
        return data(b)

    return once(db, u, f"batch-start:{batch_id}", key, {}, action)


@app.put("/api/v1/admin/batch/{batch_id}/update", tags=["Broiler"])
def update_batch(batch_id: str, p: BatchUpdate, db: DB, key: Key, u=Depends(admin)):
    def action():
        b = get(db, Batch, batch_id)
        allowed(db, u, b.business_id)
        if b.status != "ACTIVE":
            fail("Batch is not active")
        if not b.started_on <= p.date <= today():
            fail("Log date must be between batch start and today", 422)
        if b.deaths + p.deaths > b.chicks:
            fail("Mortality exceeds remaining birds", 422)
        b.deaths += p.deaths
        b.expenses += p.expense
        row = BatchLog(batch_id=b.id, **p.model_dump())
        db.add(row)
        db.flush()
        journal(
            db,
            f"batch-log:{row.id}",
            "BATCH_COST",
            {f"business:{b.business_id}": -p.expense, "external:operations": p.expense},
        )
        return {"batch": data(b), "log": data(row)}

    return once(db, u, f"batch-update:{batch_id}", key, p.model_dump(), action)


@app.get("/api/v1/admin/batch/{batch_id}/logs", tags=["Broiler"])
def batch_logs(batch_id: str, db: DB, u=Depends(admin)):
    b = get(db, Batch, batch_id)
    allowed(db, u, b.business_id)
    return {
        "batch": data(b),
        "logs": [
            data(r)
            for r in db.scalars(
                select(BatchLog)
                .where(BatchLog.batch_id == b.id)
                .order_by(BatchLog.date)
            )
        ],
    }


@app.post("/api/v1/admin/batch/{batch_id}/harvest", tags=["Settlement"])
def harvest(batch_id: str, p: Harvest, db: DB, key: Key, u=Depends(admin)):
    def action():
        batch = get(db, Batch, batch_id)
        b = allowed(db, u, batch.business_id)
        if batch.status != "ACTIVE":
            fail("Batch is not active")
        batch.yield_kg = p.yield_kg
        batch.revenue = int(
            (p.yield_kg * p.price_per_kg).quantize(Decimal(1), rounding=ROUND_HALF_UP)
        )
        batch.expenses += p.additional_expense
        batch.net_profit = batch.revenue - batch.expenses
        batch.status = "HARVESTED"
        batch.closed_on = today()
        cash = batch.revenue - p.additional_expense
        journal(
            db,
            f"harvest-cash:{batch.id}",
            "BATCH_REVENUE",
            {f"business:{b.id}": cash, "external:operations": -cash},
        )
        result = settle(
            db,
            b,
            f"batch:{batch.id}",
            batch.net_profit,
            batch.total_shares,
            owned(db, b.id, batch.id),
            batch.total_shares * batch.share_price,
        )
        return {"batch": data(batch), "settlement": result}

    return once(db, u, f"harvest:{batch_id}", key, p.model_dump(), action)


@app.get("/api/v1/admin/reports", tags=["Reports"])
def reports(
    db: DB, start: date, end: date, u=Depends(admin), business_id: str | None = None
):
    if end < start or (end - start).days > 366:
        fail("Report range must be 0–366 days", 422)
    ids = business_ids(db, u)
    if business_id:
        allowed(db, u, business_id)
        ids = [business_id]
    rows = []
    for b in db.scalars(select(Business).where(Business.id.in_(ids))):
        days = db.scalars(
            select(Day).where(Day.business_id == b.id, Day.date.between(start, end))
        ).all()
        batches = db.scalars(
            select(Batch).where(
                Batch.business_id == b.id, Batch.closed_on.between(start, end)
            )
        ).all()
        revenue = sum(d.revenue for d in days) + sum(x.revenue for x in batches)
        costs = sum(d.cost + d.expenses for d in days) + sum(
            x.expenses for x in batches
        )
        rows.append(
            {
                "business_id": b.id,
                "name": b.name,
                "type": b.type,
                "revenue": revenue,
                "cost_and_expenses": costs,
                "net_profit": revenue - costs,
                "open_days": sum(d.status == "OPEN" for d in days),
            }
        )
    return {
        "start": start,
        "end": end,
        "businesses": rows,
        "total_sales": sum(r["revenue"] for r in rows),
        "total_cost_and_expenses": sum(r["cost_and_expenses"] for r in rows),
        "total_profit": sum(r["net_profit"] for r in rows),
    }


@app.get("/api/v1/admin/dashboard", tags=["Reports"])
def dashboard(db: DB, u=Depends(admin)):
    result = reports(db, today(), today(), u)
    previous = reports(db, today() - timedelta(days=1), today() - timedelta(days=1), u)
    result["yesterday"] = {
        "total_sales": previous["total_sales"],
        "total_profit": previous["total_profit"],
    }
    return result


@app.get("/api/v1/admin/settlements", tags=["Settlement"])
def settlements(
    business_id: str,
    db: DB,
    u=Depends(admin),
    offset: int = Query(0, ge=0),
    limit: int = Query(50, ge=1, le=200),
):
    allowed(db, u, business_id)
    return [
        data(r)
        for r in db.scalars(
            select(Settlement)
            .where(Settlement.business_id == business_id)
            .order_by(Settlement.created_at.desc(), Settlement.id)
            .offset(offset)
            .limit(limit)
        )
    ]


@app.get("/api/v1/investor/marketplace", tags=["Investments"])
def marketplace(db: DB, u=Depends(investor)):
    items = []
    for b in db.scalars(select(Business).order_by(Business.name)):
        item = data(b)
        item["available_shares"] = (
            b.total_shares - sum(owned(db, b.id).values())
            if b.type != "BROILER"
            else None
        )
        item["market_cap"] = (
            b.total_shares * b.share_price if b.type != "BROILER" else None
        )
        item["batches"] = [
            {
                **data(batch),
                "available_shares": batch.total_shares
                - sum(owned(db, b.id, batch.id).values()),
            }
            for batch in db.scalars(select(Batch).where(Batch.business_id == b.id))
        ]
        history = db.scalars(
            select(Settlement)
            .where(Settlement.business_id == b.id)
            .order_by(Settlement.created_at.desc())
            .limit(30)
        ).all()
        item["history"] = [
            {
                "date": r.created_at,
                "net_profit": r.net_profit,
                "distributed": r.distributed,
                "roi_percent": str(
                    Decimal(r.net_profit)
                    * 100
                    / (r.snapshot["principal"] or b.total_shares * b.share_price)
                ),
            }
            for r in history
        ]
        items.append(item)
    return items


@app.post("/api/v1/investor/transaction/buy", tags=["Investments"])
def purchase(p: Buy, db: DB, key: Key, u=Depends(verified)):
    return once(db, u, "buy", key, p.model_dump(), lambda: buy(db, u, p))


@app.get("/api/v1/investor/portfolio", tags=["Investments"])
def portfolio(db: DB, u=Depends(investor)):
    holdings = db.scalars(select(Ownership).where(Ownership.user_id == u.id)).all()
    active = [
        h
        for h in holdings
        if not h.batch_id or get(db, Batch, h.batch_id).status != "HARVESTED"
    ]
    dividend = db.scalar(
        select(func.coalesce(func.sum(Posting.amount), 0))
        .join(Journal, Posting.journal_id == Journal.id)
        .where(Posting.account == f"wallet:{u.id}", Journal.kind == "DIVIDEND")
    )
    batch_profit = 0
    for s in db.scalars(select(Settlement).where(Settlement.source.like("batch:%"))):
        shares = s.snapshot["shares"].get(u.id, 0)
        batch_profit += (
            s.snapshot["payouts"].get(u.id, 0)
            - s.snapshot["principal"] * shares // s.snapshot["total_shares"]
        )
    return {
        "wallet_balance": wallet(db, u.id),
        "total_invested": sum(h.paid for h in holdings),
        "active_invested": sum(h.paid for h in active),
        "total_profit_earned": dividend + batch_profit,
        "valuation_method": "Acquisition cost; no secondary market price feed",
        "holdings": [
            {
                **data(h),
                "business_type": get(db, Business, h.business_id).type,
                "active": h in active,
            }
            for h in holdings
        ],
    }


@app.get("/api/v1/investor/wallet/transactions", tags=["Wallet"])
def transactions(
    db: DB,
    u=Depends(investor),
    offset: int = Query(0, ge=0),
    limit: int = Query(50, ge=1, le=200),
):
    rows = db.execute(
        select(Posting, Journal)
        .join(Journal)
        .where(Posting.account == f"wallet:{u.id}")
        .order_by(Journal.created_at.desc(), Journal.id)
        .offset(offset)
        .limit(limit)
    ).all()
    return {
        "balance": wallet(db, u.id),
        "transactions": [{**data(j), "amount": p.amount} for p, j in rows],
    }


@app.post("/api/v1/investor/wallet/withdraw", tags=["Wallet"])
def withdraw(p: Withdraw, db: DB, key: Key, u=Depends(verified)):
    if not MOCK:
        fail("Live withdrawal provider is not configured", 503)

    def action():
        if wallet(db, u.id) < p.amount:
            fail("Insufficient wallet funds")
        w = Withdrawal(user_id=u.id, **p.model_dump())
        db.add(w)
        db.flush()
        journal(
            db,
            f"withdraw:{w.id}",
            "WITHDRAWAL_HOLD",
            {f"wallet:{u.id}": -p.amount, f"withdrawal:{w.id}": p.amount},
        )
        return data(w)

    return once(db, u, "withdraw", key, p.model_dump(), action)


@app.get("/api/v1/investor/wallet/withdrawals", tags=["Wallet"])
def withdrawal_list(
    db: DB,
    u=Depends(investor),
    offset: int = Query(0, ge=0),
    limit: int = Query(50, ge=1, le=200),
):
    return [
        data(w)
        for w in db.scalars(
            select(Withdrawal)
            .where(Withdrawal.user_id == u.id)
            .order_by(Withdrawal.created_at.desc())
            .offset(offset)
            .limit(limit)
        )
    ]


@app.post("/api/v1/investor/wallet/deposit", tags=["Wallet"])
def live_deposit(p: Deposit, u=Depends(verified)):
    fail(
        "Live payment gateway is not configured; development testing uses /dev/wallet/deposit",
        503,
    )


# Explicitly isolated development routes; never registered in production.
if MODE == "development" and MOCK:

    @app.post("/api/v1/dev/verify-kyc", tags=["Development ONLY"])
    def mock_kyc(db: DB, u=Depends(investor)):
        u.kyc_status = "MOCK_VERIFIED"
        return {"kyc_status": "MOCK_VERIFIED", "mock": True}

    @app.post("/api/v1/dev/wallet/deposit", tags=["Development ONLY"])
    def mock_deposit(p: Deposit, db: DB, key: Key, u=Depends(verified)):
        def action():
            journal(
                db,
                f"deposit:{u.id}:{key}",
                "MOCK_DEPOSIT",
                {f"wallet:{u.id}": p.amount, "external:mock": -p.amount},
            )
            return {"wallet_balance": wallet(db, u.id), "mock": True}

        return once(db, u, "deposit", key, p.model_dump(), action)

    @app.post(
        "/api/v1/dev/withdrawals/{withdrawal_id}/resolve", tags=["Development ONLY"]
    )
    def mock_resolve(
        withdrawal_id: str, p: ResolveWithdrawal, db: DB, key: Key, u=Depends(root)
    ):
        def action():
            w = get(db, Withdrawal, withdrawal_id)
            if w.status != "PENDING":
                fail("Withdrawal already resolved")
            w.status = p.status
            target = f"wallet:{w.user_id}" if p.status == "FAILED" else "external:mock"
            journal(
                db,
                f"resolve:{w.id}",
                "WITHDRAWAL_" + p.status,
                {f"withdrawal:{w.id}": -w.amount, target: w.amount},
            )
            return data(w)

        return once(db, u, f"resolve:{withdrawal_id}", key, p.model_dump(), action)


@app.get("/api/v1/admin/businesses/{business_id}/summary", tags=["Reports"])
def business_summary(
    business_id: str, db: DB, u=Depends(admin), on: date | None = None
):
    b = allowed(db, u, business_id)
    on = on or today()
    day = db.scalar(select(Day).where(Day.business_id == b.id, Day.date == on))
    ops = (
        db.scalars(select(Operation).where(Operation.day_id == day.id)).all()
        if day
        else []
    )
    batches = db.scalars(
        select(Batch).where(Batch.business_id == b.id, Batch.status != "HARVESTED")
    ).all()
    logs = db.scalars(
        select(BatchLog)
        .join(Batch)
        .where(Batch.business_id == b.id, BatchLog.date == on)
    ).all()
    return {
        "business": data(b),
        "date": on,
        "day": data(day) if day else None,
        "purchased_quantity": sum(r.quantity for r in ops if r.kind == "PURCHASE"),
        "sold_quantity": sum(r.quantity for r in ops if r.kind == "SALE"),
        "byproduct_quantity": sum(r.quantity for r in ops if r.kind == "BYPRODUCT"),
        "byproduct_revenue": sum(r.amount for r in ops if r.kind == "BYPRODUCT"),
        "retail_sold": sum(
            r.quantity for r in ops if r.kind == "SALE" and r.channel == "RETAIL"
        ),
        "commercial_sold": sum(
            r.quantity for r in ops if r.kind == "SALE" and r.channel == "COMMERCIAL"
        ),
        "live_birds": sum(r.chicks - r.deaths for r in batches),
        "feed_kg": sum(r.feed_kg for r in logs),
        "mortality": sum(r.deaths for r in logs),
        "batches": [data(r) for r in batches],
    }


@app.get("/api/v1/admin/expenses", tags=["Operations"])
def expense_list(
    business_id: str,
    db: DB,
    start: date,
    end: date,
    u=Depends(admin),
    offset: int = Query(0, ge=0),
    limit: int = Query(50, ge=1, le=200),
):
    allowed(db, u, business_id)
    if end < start or (end - start).days > 366:
        fail("Invalid date range", 422)
    query = (
        select(Operation)
        .join(Day)
        .where(
            Day.business_id == business_id,
            Operation.kind == "EXPENSE",
            Day.date.between(start, end),
        )
        .order_by(Operation.created_at.desc(), Operation.id)
    )
    return [data(r) for r in db.scalars(query.offset(offset).limit(limit))]


@app.get("/api/v1/admin/batches", tags=["Broiler"])
def admin_batch_list(
    business_id: str,
    db: DB,
    u=Depends(admin),
    offset: int = Query(0, ge=0),
    limit: int = Query(50, ge=1, le=200),
):
    """Include harvested batches so administrators can revisit closed records."""
    allowed(db, u, business_id)
    return [
        data(row)
        for row in db.scalars(
            select(Batch)
            .where(Batch.business_id == business_id)
            .order_by(Batch.id)
            .offset(offset)
            .limit(limit)
        )
    ]


@app.get("/api/v1/admin/ledger/{day_id}", tags=["Operations"])
def daily_detail(day_id: str, db: DB, u=Depends(admin)):
    """Refresh a historical day without relying on a paginated list snapshot."""
    row = get(db, Day, day_id)
    allowed(db, u, row.business_id)
    return data(row)
