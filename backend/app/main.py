import base64
import os
from datetime import date, timedelta
from decimal import ROUND_HALF_UP, Decimal
from typing import Annotated
from uuid import uuid4

from fastapi import Depends, FastAPI, File, Header, Query, UploadFile
from fastapi.responses import JSONResponse, RedirectResponse
from starlette.staticfiles import StaticFiles
from sqlalchemy import func, select, text
from sqlalchemy.exc import IntegrityError

from .db import get_db
from .models import (
    AppIcon,
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
    AppIconOut,
    AppIconUpdate,
    Base64IconUpload,
    BatchCreate,
    BatchUpdate,
    BusinessCreate,
    BusinessIconUpdate,
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
    UserOut,
    UserRoleUpdate,
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

UPLOAD_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "uploads"
)
os.makedirs(os.path.join(UPLOAD_DIR, "icons"), exist_ok=True)
app.mount("/uploads", StaticFiles(directory=UPLOAD_DIR), name="uploads")
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
async def health(db: DB):
    await db.execute(text("SELECT 1"))
    return {"status": "ok", "environment": MODE, "mock_payments": MOCK}


@app.post("/api/v1/auth/register", tags=["Authentication"], status_code=201)
async def register(p: Register, db: DB):
    result = await db.execute(select(User).where(User.phone == p.phone))
    if result.scalar_one_or_none():
        fail("Phone already registered")
    u = User(phone=p.phone, name=p.name, password_hash=passwords.hash(p.password))
    db.add(u)
    await db.flush()
    return {
        "id": u.id,
        "kyc_status": u.kyc_status,
        "access_token": token(u),
        "token_type": "bearer",
    }


@app.post("/api/v1/auth/login", tags=["Authentication"])
async def login(p: Login, db: DB):
    result = await db.execute(select(User).where(User.phone == p.phone))
    u = result.scalar_one_or_none()
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
async def profile(p: Profile, db: DB, u=Depends(current_user)):
    u.name, u.language = p.name, p.language
    await db.flush()
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
async def create_business(p: BusinessCreate, db: DB, u=Depends(root)):
    b = Business(**p.model_dump())
    db.add(b)
    await db.flush()
    return data(b)


@app.post("/api/v1/admin/managers", tags=["Administration"], status_code=201)
async def create_manager(p: Register, db: DB, u=Depends(root)):
    manager = User(
        phone=p.phone,
        name=p.name,
        password_hash=passwords.hash(p.password),
        role="ADMIN",
    )
    db.add(manager)
    await db.flush()
    return {"id": manager.id, "name": manager.name}


@app.put(
    "/api/v1/admin/businesses/{business_id}/managers/{manager_id}",
    tags=["Administration"],
)
async def assign(business_id: str, manager_id: str, db: DB, u=Depends(root)):
    await get(db, Business, business_id)
    manager = await get(db, User, manager_id)
    if manager.role != "ADMIN":
        fail("User must be a manager", 422)
    result = await db.execute(select(Assignment).where(Assignment.user_id == manager_id, Assignment.business_id == business_id))
    if not result.scalar_one_or_none():
        db.add(Assignment(user_id=manager_id, business_id=business_id))
    return {"assigned": True}


@app.delete(
    "/api/v1/admin/businesses/{business_id}/managers/{manager_id}",
    tags=["Administration"],
)
async def unassign(business_id: str, manager_id: str, db: DB, u=Depends(root)):
    result = await db.execute(select(Assignment).where(Assignment.user_id == manager_id, Assignment.business_id == business_id))
    row = result.scalar_one_or_none()
    if row:
        await db.delete(row)
    return {"assigned": False}


@app.get("/api/v1/admin/businesses", tags=["Administration"])
async def admin_businesses(db: DB, u=Depends(admin)):
    ids = await business_ids(db, u)
    result = await db.execute(select(Business).where(Business.id.in_(ids)))
    return [
        data(b)
        for b in result.scalars()
    ]


@app.get("/api/v1/admin/users", tags=["Administration"], response_model=list[UserOut])
async def list_users(
    db: DB,
    u=Depends(root),
    role: str | None = None,
    search: str | None = None,
    limit: int = 100,
    offset: int = 0,
):
    stmt = select(User)
    if role:
        stmt = stmt.where(User.role == role.upper())
    if search:
        term = f"%{search.strip()}%"
        stmt = stmt.where((User.name.ilike(term)) | (User.phone.ilike(term)))
    stmt = stmt.order_by(User.name).offset(offset).limit(limit)
    res = await db.execute(stmt)
    users = res.scalars().all()
    user_ids = [usr.id for usr in users]
    assign_map: dict[str, list[str]] = {}
    if user_ids:
        assignments_stmt = select(Assignment).where(Assignment.user_id.in_(user_ids))
        assign_res = await db.execute(assignments_stmt)
        for a in assign_res.scalars().all():
            assign_map.setdefault(a.user_id, []).append(a.business_id)

    return [
        UserOut(
            id=usr.id,
            phone=usr.phone,
            name=usr.name,
            role=usr.role,
            language=usr.language,
            kyc_status=usr.kyc_status,
            assigned_businesses=assign_map.get(usr.id, []),
        )
        for usr in users
    ]


@app.get("/api/v1/admin/users/{user_id}", tags=["Administration"], response_model=UserOut)
async def get_user_detail(user_id: str, db: DB, u=Depends(root)):
    target = await get(db, User, user_id)
    if not target:
        fail("User not found", 404)
    assign_res = await db.execute(
        select(Assignment.business_id).where(Assignment.user_id == user_id)
    )
    businesses = list(assign_res.scalars().all())
    return UserOut(
        id=target.id,
        phone=target.phone,
        name=target.name,
        role=target.role,
        language=target.language,
        kyc_status=target.kyc_status,
        assigned_businesses=businesses,
    )


@app.put(
    "/api/v1/admin/users/{user_id}/role",
    tags=["Administration"],
    response_model=UserOut,
)
async def update_user_role(
    user_id: str, payload: UserRoleUpdate, db: DB, u=Depends(root)
):
    target = await get(db, User, user_id)
    if not target:
        fail("User not found", 404)
    if target.id == u.id and payload.role != "SUPERADMIN":
        fail("Cannot demote current superadmin", 400)
    target.role = payload.role
    target.token_version += 1
    assign_res = await db.execute(
        select(Assignment.business_id).where(Assignment.user_id == user_id)
    )
    businesses = list(assign_res.scalars().all())
    return UserOut(
        id=target.id,
        phone=target.phone,
        name=target.name,
        role=target.role,
        language=target.language,
        kyc_status=target.kyc_status,
        assigned_businesses=businesses,
    )


@app.post(
    "/api/v1/admin/users/{user_id}/verify-kyc",
    tags=["Administration"],
    response_model=UserOut,
)
async def superadmin_verify_kyc(user_id: str, db: DB, u=Depends(root)):
    target = await get(db, User, user_id)
    if not target:
        fail("User not found", 404)
    target.kyc_status = "VERIFIED"
    assign_res = await db.execute(
        select(Assignment.business_id).where(Assignment.user_id == user_id)
    )
    businesses = list(assign_res.scalars().all())
    return UserOut(
        id=target.id,
        phone=target.phone,
        name=target.name,
        role=target.role,
        language=target.language,
        kyc_status=target.kyc_status,
        assigned_businesses=businesses,
    )


@app.get("/api/v1/mobile/icons", tags=["Mobile App"])
async def get_mobile_icons(db: DB):
    res = await db.execute(select(AppIcon))
    icons = res.scalars().all()
    b_res = await db.execute(select(Business.id, Business.type, Business.icon_url))
    biz_icons = {row[0]: row[2] for row in b_res.all() if row[2]}
    return {
        "icons": {
            i.key: {
                "label": i.label,
                "screen": i.screen,
                "image_url": i.image_url,
                "fallback": i.fallback_icon,
            }
            for i in icons
        },
        "business_icons": biz_icons,
    }


@app.get("/api/v1/admin/icons", tags=["Administration"], response_model=list[AppIconOut])
async def list_admin_icons(db: DB, u=Depends(root)):
    res = await db.execute(select(AppIcon).order_by(AppIcon.screen, AppIcon.key))
    return res.scalars().all()


@app.put(
    "/api/v1/admin/icons/{key}",
    tags=["Administration"],
    response_model=AppIconOut,
)
async def set_app_icon(key: str, payload: AppIconUpdate, db: DB, u=Depends(root)):
    res = await db.execute(select(AppIcon).where(AppIcon.key == key))
    icon = res.scalar_one_or_none()
    if not icon:
        icon = AppIcon(
            key=key,
            label=payload.label or key.replace("_", " ").title(),
            screen=payload.screen,
            image_url=payload.image_url,
            fallback_icon=payload.fallback_icon,
        )
        db.add(icon)
    else:
        if payload.label:
            icon.label = payload.label
        icon.screen = payload.screen
        icon.image_url = payload.image_url
        if payload.fallback_icon:
            icon.fallback_icon = payload.fallback_icon
    await db.flush()
    return icon


@app.put("/api/v1/admin/businesses/{business_id}/icon", tags=["Administration"])
async def update_business_icon(
    business_id: str, payload: BusinessIconUpdate, db: DB, u=Depends(root)
):
    biz = await get(db, Business, business_id)
    if not biz:
        fail("Business not found", 404)
    biz.icon_url = payload.icon_url
    return {"id": biz.id, "name": biz.name, "icon_url": biz.icon_url}


@app.post("/api/v1/admin/icons/upload", tags=["Administration"])
async def upload_icon_file(
    file: UploadFile = File(...), u=Depends(root)
):
    if not file.content_type or not (
        file.content_type.startswith("image/")
        or file.content_type in ("image/svg+xml", "application/octet-stream")
    ):
        fail("Only image files are supported", 400)

    ext = os.path.splitext(file.filename or "")[1].lower()
    if ext not in (".png", ".jpg", ".jpeg", ".webp", ".svg", ".ico"):
        ext = ".png"

    filename = f"icon_{uuid4().hex}{ext}"
    target_path = os.path.join(UPLOAD_DIR, "icons", filename)

    contents = await file.read()
    if len(contents) > 2 * 1024 * 1024:
        fail("Image size exceeds 2MB limit", 400)

    with open(target_path, "wb") as f:
        f.write(contents)

    image_url = f"/uploads/icons/{filename}"
    return {"filename": filename, "image_url": image_url}


@app.post("/api/v1/admin/icons/upload-base64", tags=["Administration"])
async def upload_icon_base64(payload: Base64IconUpload, u=Depends(root)):
    raw_data = payload.data
    if "," in raw_data:
        raw_data = raw_data.split(",", 1)[1]
    try:
        binary_data = base64.b64decode(raw_data)
    except Exception:
        fail("Invalid base64 image data", 400)
    if len(binary_data) > 2 * 1024 * 1024:
        fail("Image size exceeds 2MB limit", 400)

    ext = os.path.splitext(payload.filename or "")[1].lower()
    if ext not in (".png", ".jpg", ".jpeg", ".webp", ".svg", ".ico"):
        ext = ".png"
    filename = f"icon_{uuid4().hex}{ext}"
    target_path = os.path.join(UPLOAD_DIR, "icons", filename)
    with open(target_path, "wb") as f:
        f.write(binary_data)
    image_url = f"/uploads/icons/{filename}"
    return {"filename": filename, "image_url": image_url}


@app.post("/api/v1/admin/suppliers", tags=["Suppliers"], status_code=201)
async def supplier_create(p: SupplierCreate, db: DB, u=Depends(admin)):
    await allowed(db, u, p.business_id)
    s = Supplier(**p.model_dump())
    db.add(s)
    await db.flush()
    return data(s)


@app.get("/api/v1/admin/suppliers", tags=["Suppliers"])
async def suppliers(business_id: str, db: DB, u=Depends(admin)):
    await allowed(db, u, business_id)
    result = await db.execute(select(Supplier).where(Supplier.business_id == business_id))
    return [
        data(s)
        for s in result.scalars()
    ]


@app.get("/api/v1/admin/suppliers/{supplier_id}/bills", tags=["Suppliers"])
async def supplier_bills(
    supplier_id: str,
    db: DB,
    u=Depends(admin),
    offset: int = Query(0, ge=0),
    limit: int = Query(50, ge=1, le=200),
):
    s = await get(db, Supplier, supplier_id)
    await allowed(db, u, s.business_id)
    result = await db.execute(
        select(Operation)
        .where(Operation.supplier_id == s.id, Operation.kind == "PURCHASE")
        .order_by(Operation.created_at.desc(), Operation.id)
        .offset(offset)
        .limit(limit)
    )
    return [
        data(r)
        for r in result.scalars()
    ]


@app.get("/api/v1/admin/stock", tags=["Operations"])
async def stock(business_id: str, db: DB, u=Depends(admin)):
    b = await allowed(db, u, business_id)
    result = await db.execute(
        select(Batch).where(Batch.business_id == b.id, Batch.status != "HARVESTED")
    )
    batches = result.scalars().all()
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
async def daily(p: DailyInput, db: DB, key: Key, u=Depends(admin)):
    async def action():
        return await operation(db, u, p)
    return await once(db, u, "daily", key, p.model_dump(), action)


@app.get("/api/v1/admin/ledger/daily", tags=["Operations"])
async def daily_list(
    business_id: str,
    db: DB,
    u=Depends(admin),
    offset: int = Query(0, ge=0),
    limit: int = Query(50, ge=1, le=200),
):
    await allowed(db, u, business_id)
    result = await db.execute(
        select(Day)
        .where(Day.business_id == business_id)
        .order_by(Day.date.desc())
        .offset(offset)
        .limit(limit)
    )
    return [
        data(d)
        for d in result.scalars()
    ]


@app.get("/api/v1/admin/ledger/{day_id}/operations", tags=["Operations"])
async def operations(day_id: str, db: DB, u=Depends(admin)):
    d = await get(db, Day, day_id)
    await allowed(db, u, d.business_id)
    result = await db.execute(
        select(Operation)
        .where(Operation.day_id == day_id)
        .order_by(Operation.created_at, Operation.id)
    )
    return [
        data(r)
        for r in result.scalars()
    ]


@app.post("/api/v1/admin/ledger/close", tags=["Settlement"])
async def close_day(p: CloseDay, db: DB, key: Key, u=Depends(admin)):
    async def action():
        d = await get(db, Day, p.day_id)
        b = await allowed(db, u, d.business_id)
        if d.status != "OPEN":
            fail("Day already settled")
        d.net_profit = d.revenue - d.cost - d.expenses
        d.status = "CLOSED"
        result = await settle(
            db, b, f"day:{d.id}", d.net_profit, b.total_shares, await owned(db, b.id)
        )
        return {"day": data(d), "settlement": result}

    return await once(db, u, "close", key, p.model_dump(), action)


@app.post("/api/v1/admin/batch/create", tags=["Broiler"])
async def create_batch(p: BatchCreate, db: DB, key: Key, u=Depends(admin)):
    async def action():
        b = await allowed(db, u, p.business_id)
        if b.type != "BROILER":
            fail("Batch requires broiler business", 422)
        batch = Batch(**p.model_dump(exclude={"initial_cost"}), expenses=p.initial_cost)
        db.add(batch)
        await db.flush()
        await journal(
            db,
            f"batch-initial:{batch.id}",
            "BATCH_COST",
            {
                f"business:{b.id}": -p.initial_cost,
                "external:operations": p.initial_cost,
            },
        )
        return data(batch)

    return await once(db, u, "batch-create", key, p.model_dump(), action)


@app.post("/api/v1/admin/batch/{batch_id}/start", tags=["Broiler"])
async def start_batch(batch_id: str, db: DB, key: Key, u=Depends(admin)):
    async def action():
        b = await get(db, Batch, batch_id)
        await allowed(db, u, b.business_id)
        if b.status != "FUNDING":
            fail("Batch already started")
        b.status = "ACTIVE"
        b.started_on = today()
        await db.flush()
        return data(b)

    return await once(db, u, f"batch-start:{batch_id}", key, {}, action)


@app.put("/api/v1/admin/batch/{batch_id}/update", tags=["Broiler"])
async def update_batch(batch_id: str, p: BatchUpdate, db: DB, key: Key, u=Depends(admin)):
    async def action():
        b = await get(db, Batch, batch_id)
        await allowed(db, u, b.business_id)
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
        await db.flush()
        await journal(
            db,
            f"batch-log:{row.id}",
            "BATCH_COST",
            {f"business:{b.business_id}": -p.expense, "external:operations": p.expense},
        )
        return {"batch": data(b), "log": data(row)}

    return await once(db, u, f"batch-update:{batch_id}", key, p.model_dump(), action)


@app.get("/api/v1/admin/batch/{batch_id}/logs", tags=["Broiler"])
async def batch_logs(batch_id: str, db: DB, u=Depends(admin)):
    b = await get(db, Batch, batch_id)
    await allowed(db, u, b.business_id)
    result = await db.execute(
        select(BatchLog)
        .where(BatchLog.batch_id == b.id)
        .order_by(BatchLog.date)
    )
    return {
        "batch": data(b),
        "logs": [
            data(r)
            for r in result.scalars()
        ],
    }


@app.post("/api/v1/admin/batch/{batch_id}/harvest", tags=["Settlement"])
async def harvest(batch_id: str, p: Harvest, db: DB, key: Key, u=Depends(admin)):
    async def action():
        batch = await get(db, Batch, batch_id)
        b = await allowed(db, u, batch.business_id)
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
        await journal(
            db,
            f"harvest-cash:{batch.id}",
            "BATCH_REVENUE",
            {f"business:{b.id}": cash, "external:operations": -cash},
        )
        result = await settle(
            db,
            b,
            f"batch:{batch.id}",
            batch.net_profit,
            batch.total_shares,
            await owned(db, b.id, batch.id),
            batch.total_shares * batch.share_price,
        )
        return {"batch": data(batch), "settlement": result}

    return await once(db, u, f"harvest:{batch_id}", key, p.model_dump(), action)


@app.get("/api/v1/admin/reports", tags=["Reports"])
async def reports(
    db: DB, start: date, end: date, u=Depends(admin), business_id: str | None = None
):
    if end < start or (end - start).days > 366:
        fail("Report range must be 0–366 days", 422)
    ids = await business_ids(db, u)
    if business_id:
        await allowed(db, u, business_id)
        ids = [business_id]
    rows = []
    result = await db.execute(select(Business).where(Business.id.in_(ids)))
    for b in result.scalars():
        days_result = await db.execute(
            select(Day).where(Day.business_id == b.id, Day.date.between(start, end))
        )
        days = days_result.scalars().all()
        batches_result = await db.execute(
            select(Batch).where(
                Batch.business_id == b.id, Batch.closed_on.between(start, end)
            )
        )
        batches = batches_result.scalars().all()
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
async def dashboard(db: DB, u=Depends(admin)):
    result = await reports(db, today(), today(), u)
    previous = await reports(db, today() - timedelta(days=1), today() - timedelta(days=1), u)
    result["yesterday"] = {
        "total_sales": previous["total_sales"],
        "total_profit": previous["total_profit"],
    }
    return result


@app.get("/api/v1/admin/settlements", tags=["Settlement"])
async def settlements(
    business_id: str,
    db: DB,
    u=Depends(admin),
    offset: int = Query(0, ge=0),
    limit: int = Query(50, ge=1, le=200),
):
    await allowed(db, u, business_id)
    result = await db.execute(
        select(Settlement)
        .where(Settlement.business_id == business_id)
        .order_by(Settlement.created_at.desc(), Settlement.id)
        .offset(offset)
        .limit(limit)
    )
    return [
        data(r)
        for r in result.scalars()
    ]


@app.get("/api/v1/investor/marketplace", tags=["Investments"])
async def marketplace(db: DB, u=Depends(investor)):
    items = []
    result = await db.execute(select(Business).order_by(Business.name))
    for b in result.scalars():
        item = data(b)
        item["available_shares"] = (
            b.total_shares - sum((await owned(db, b.id)).values())
            if b.type != "BROILER"
            else None
        )
        item["market_cap"] = (
            b.total_shares * b.share_price if b.type != "BROILER" else None
        )
        batch_result = await db.execute(select(Batch).where(Batch.business_id == b.id))
        item["batches"] = [
            {
                **data(batch),
                "available_shares": batch.total_shares
                - sum((await owned(db, b.id, batch.id)).values()),
            }
            for batch in batch_result.scalars()
        ]
        history_result = await db.execute(
            select(Settlement)
            .where(Settlement.business_id == b.id)
            .order_by(Settlement.created_at.desc())
            .limit(30)
        )
        history = history_result.scalars().all()
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
async def purchase(p: Buy, db: DB, key: Key, u=Depends(verified)):
    async def action():
        return await buy(db, u, p)
    return await once(db, u, "buy", key, p.model_dump(), action)


@app.get("/api/v1/investor/portfolio", tags=["Investments"])
async def portfolio(db: DB, u=Depends(investor)):
    result = await db.execute(select(Ownership).where(Ownership.user_id == u.id))
    holdings = result.scalars().all()
    active = []
    for h in holdings:
        if not h.batch_id:
            active.append(h)
        else:
            batch = await get(db, Batch, h.batch_id)
            if batch.status != "HARVESTED":
                active.append(h)
    
    dividend_result = await db.execute(
        select(func.coalesce(func.sum(Posting.amount), 0))
        .join(Journal, Posting.journal_id == Journal.id)
        .where(Posting.account == f"wallet:{u.id}", Journal.kind == "DIVIDEND")
    )
    dividend = dividend_result.scalar()
    
    batch_profit = 0
    settlement_result = await db.execute(select(Settlement).where(Settlement.source.like("batch:%")))
    for s in settlement_result.scalars():
        shares = s.snapshot["shares"].get(u.id, 0)
        batch_profit += (
            s.snapshot["payouts"].get(u.id, 0)
            - s.snapshot["principal"] * shares // s.snapshot["total_shares"]
        )
    
    balance = await wallet(db, u.id)
    
    holdings_data = []
    for h in holdings:
        business = await get(db, Business, h.business_id)
        holdings_data.append({
            **data(h),
            "business_type": business.type,
            "active": h in active,
        })
    
    return {
        "wallet_balance": balance,
        "total_invested": sum(h.paid for h in holdings),
        "active_invested": sum(h.paid for h in active),
        "total_profit_earned": dividend + batch_profit,
        "valuation_method": "Acquisition cost; no secondary market price feed",
        "holdings": holdings_data,
    }


@app.get("/api/v1/investor/wallet/transactions", tags=["Wallet"])
async def transactions(
    db: DB,
    u=Depends(investor),
    offset: int = Query(0, ge=0),
    limit: int = Query(50, ge=1, le=200),
):
    result = await db.execute(
        select(Posting, Journal)
        .join(Journal)
        .where(Posting.account == f"wallet:{u.id}")
        .order_by(Journal.created_at.desc(), Journal.id)
        .offset(offset)
        .limit(limit)
    )
    rows = result.all()
    balance = await wallet(db, u.id)
    return {
        "balance": balance,
        "transactions": [{**data(j), "amount": p.amount} for p, j in rows],
    }


@app.post("/api/v1/investor/wallet/withdraw", tags=["Wallet"])
async def withdraw(p: Withdraw, db: DB, key: Key, u=Depends(verified)):
    if not MOCK:
        fail("Live withdrawal provider is not configured", 503)

    async def action():
        balance = await wallet(db, u.id)
        if balance < p.amount:
            fail("Insufficient wallet funds")
        w = Withdrawal(user_id=u.id, **p.model_dump())
        db.add(w)
        await db.flush()
        await journal(
            db,
            f"withdraw:{w.id}",
            "WITHDRAWAL_HOLD",
            {f"wallet:{u.id}": -p.amount, f"withdrawal:{w.id}": p.amount},
        )
        return data(w)

    return await once(db, u, "withdraw", key, p.model_dump(), action)


@app.get("/api/v1/investor/wallet/withdrawals", tags=["Wallet"])
async def withdrawal_list(
    db: DB,
    u=Depends(investor),
    offset: int = Query(0, ge=0),
    limit: int = Query(50, ge=1, le=200),
):
    result = await db.execute(
        select(Withdrawal)
        .where(Withdrawal.user_id == u.id)
        .order_by(Withdrawal.created_at.desc())
        .offset(offset)
        .limit(limit)
    )
    return [
        data(w)
        for w in result.scalars()
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
    async def mock_kyc(db: DB, u=Depends(investor)):
        u.kyc_status = "MOCK_VERIFIED"
        return {"kyc_status": "MOCK_VERIFIED", "mock": True}

    @app.post("/api/v1/dev/wallet/deposit", tags=["Development ONLY"])
    async def mock_deposit(p: Deposit, db: DB, key: Key, u=Depends(verified)):
        async def action():
            await journal(
                db,
                f"deposit:{u.id}:{key}",
                "MOCK_DEPOSIT",
                {f"wallet:{u.id}": p.amount, "external:mock": -p.amount},
            )
            balance = await wallet(db, u.id)
            return {"wallet_balance": balance, "mock": True}

        return await once(db, u, "deposit", key, p.model_dump(), action)

    @app.post(
        "/api/v1/dev/withdrawals/{withdrawal_id}/resolve", tags=["Development ONLY"]
    )
    async def mock_resolve(
        withdrawal_id: str, p: ResolveWithdrawal, db: DB, key: Key, u=Depends(root)
    ):
        async def action():
            w = await get(db, Withdrawal, withdrawal_id)
            if w.status != "PENDING":
                fail("Withdrawal already resolved")
            w.status = p.status
            target = f"wallet:{w.user_id}" if p.status == "FAILED" else "external:mock"
            await journal(
                db,
                f"resolve:{w.id}",
                "WITHDRAWAL_" + p.status,
                {f"withdrawal:{w.id}": -w.amount, target: w.amount},
            )
            return data(w)

        return await once(db, u, f"resolve:{withdrawal_id}", key, p.model_dump(), action)


@app.get("/api/v1/admin/businesses/{business_id}/summary", tags=["Reports"])
async def business_summary(
    business_id: str, db: DB, u=Depends(admin), on: date | None = None
):
    b = await allowed(db, u, business_id)
    on = on or today()
    day_result = await db.execute(select(Day).where(Day.business_id == b.id, Day.date == on))
    day = day_result.scalar_one_or_none()
    ops = []
    if day:
        ops_result = await db.execute(select(Operation).where(Operation.day_id == day.id))
        ops = ops_result.scalars().all()
    
    batches_result = await db.execute(
        select(Batch).where(Batch.business_id == b.id, Batch.status != "HARVESTED")
    )
    batches = batches_result.scalars().all()
    
    logs_result = await db.execute(
        select(BatchLog)
        .join(Batch)
        .where(Batch.business_id == b.id, BatchLog.date == on)
    )
    logs = logs_result.scalars().all()
    
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
async def expense_list(
    business_id: str,
    db: DB,
    start: date,
    end: date,
    u=Depends(admin),
    offset: int = Query(0, ge=0),
    limit: int = Query(50, ge=1, le=200),
):
    await allowed(db, u, business_id)
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
    result = await db.execute(query.offset(offset).limit(limit))
    return [data(r) for r in result.scalars()]


@app.get("/api/v1/admin/batches", tags=["Broiler"])
async def admin_batch_list(
    business_id: str,
    db: DB,
    u=Depends(admin),
    offset: int = Query(0, ge=0),
    limit: int = Query(50, ge=1, le=200),
):
    """Include harvested batches so administrators can revisit closed records."""
    await allowed(db, u, business_id)
    result = await db.execute(
        select(Batch)
        .where(Batch.business_id == business_id)
        .order_by(Batch.id)
        .offset(offset)
        .limit(limit)
    )
    return [
        data(row)
        for row in result.scalars()
    ]


@app.get("/api/v1/admin/ledger/{day_id}", tags=["Operations"])
async def daily_detail(day_id: str, db: DB, u=Depends(admin)):
    """Refresh a historical day without relying on a paginated list snapshot."""
    row = await get(db, Day, day_id)
    await allowed(db, u, row.business_id)
    return data(row)
