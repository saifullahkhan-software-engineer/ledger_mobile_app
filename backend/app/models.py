from datetime import date, datetime, timezone
from uuid import uuid4

from sqlalchemy import (
    JSON,
    BigInteger,
    CheckConstraint,
    Date,
    DateTime,
    ForeignKey,
    Integer,
    LargeBinary,
    Numeric,
    String,
    UniqueConstraint,
)
from sqlalchemy.orm import Mapped, mapped_column

from .db import Base


def uid():
    return str(uuid4())


def now():
    return datetime.now(timezone.utc)


class WriteLock(Base):
    __tablename__ = "write_lock"
    id: Mapped[int] = mapped_column(primary_key=True)


class User(Base):
    __tablename__ = "users"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=uid)
    phone: Mapped[str] = mapped_column(String(20), unique=True)
    name: Mapped[str] = mapped_column(String(120))
    password_hash: Mapped[str] = mapped_column(String(255))
    role: Mapped[str] = mapped_column(String(20), default="INVESTOR")
    language: Mapped[str] = mapped_column(String(2), default="en")
    kyc_status: Mapped[str] = mapped_column(String(20), default="UNVERIFIED")
    token_version: Mapped[int] = mapped_column(default=0)


class Business(Base):
    __tablename__ = "businesses"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=uid)
    name: Mapped[str] = mapped_column(String(120))
    type: Mapped[str] = mapped_column(String(20))
    total_shares: Mapped[int] = mapped_column(Integer)
    share_price: Mapped[int] = mapped_column(BigInteger)
    stock: Mapped[float] = mapped_column(Numeric(18, 3), default=0)
    stock_cost: Mapped[int] = mapped_column(BigInteger, default=0)
    icon_url: Mapped[str | None] = mapped_column(String(500), nullable=True, default=None)
    # Nullable FK to the uploaded image bytes; plain string icon_url values are
    # preserved for legacy file paths and explicitly approved external URLs.
    icon_asset_id: Mapped[str | None] = mapped_column(
        ForeignKey("image_assets.id"), nullable=True, default=None
    )
    __table_args__ = (
        CheckConstraint(
            "total_shares > 0 AND share_price > 0 AND stock >= 0 AND stock_cost >= 0"
        ),
    )


class AppIcon(Base):
    __tablename__ = "app_icons"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=uid)
    key: Mapped[str] = mapped_column(String(50), unique=True, index=True)
    label: Mapped[str] = mapped_column(String(120))
    screen: Mapped[str] = mapped_column(String(50), default="dashboard")
    image_url: Mapped[str] = mapped_column(String(500))
    fallback_icon: Mapped[str | None] = mapped_column(String(50), nullable=True, default=None)
    asset_id: Mapped[str | None] = mapped_column(
        ForeignKey("image_assets.id"), nullable=True, default=None
    )
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now, onupdate=now)


class ImageAsset(Base):
    """Uploaded image bytes stored in the database (BYTEA / BLOB).

    No ORM relationships point here and ``data`` is deferred, so ordinary
    icon/business list queries never load image bytes.
    """

    __tablename__ = "image_assets"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=uid)
    data: Mapped[bytes] = mapped_column(LargeBinary, deferred=True)
    content_type: Mapped[str] = mapped_column(String(100))
    filename: Mapped[str] = mapped_column(String(255), default="")
    size: Mapped[int] = mapped_column(BigInteger)
    sha256: Mapped[str] = mapped_column(String(64), index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)


class Assignment(Base):
    __tablename__ = "admin_assignments"
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id"), primary_key=True)
    business_id: Mapped[str] = mapped_column(
        ForeignKey("businesses.id"), primary_key=True
    )


class Supplier(Base):
    __tablename__ = "suppliers"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=uid)
    business_id: Mapped[str] = mapped_column(ForeignKey("businesses.id"))
    name: Mapped[str] = mapped_column(String(120))
    phone: Mapped[str | None] = mapped_column(String(30))


class Day(Base):
    __tablename__ = "daily_ledgers"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=uid)
    business_id: Mapped[str] = mapped_column(ForeignKey("businesses.id"))
    date: Mapped[date] = mapped_column(Date)
    status: Mapped[str] = mapped_column(default="OPEN")
    revenue: Mapped[int] = mapped_column(BigInteger, default=0)
    cost: Mapped[int] = mapped_column(BigInteger, default=0)
    expenses: Mapped[int] = mapped_column(BigInteger, default=0)
    net_profit: Mapped[int] = mapped_column(BigInteger, default=0)
    __table_args__ = (UniqueConstraint("business_id", "date"),)


class Operation(Base):
    __tablename__ = "operations"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=uid)
    day_id: Mapped[str] = mapped_column(ForeignKey("daily_ledgers.id"))
    kind: Mapped[str] = mapped_column(String(20))
    quantity: Mapped[float] = mapped_column(Numeric(18, 3), default=0)
    amount: Mapped[int] = mapped_column(BigInteger)
    cost: Mapped[int] = mapped_column(BigInteger, default=0)
    channel: Mapped[str | None] = mapped_column(String(20))
    note: Mapped[str] = mapped_column(String(1000), default="")
    supplier_id: Mapped[str | None] = mapped_column(ForeignKey("suppliers.id"))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)


class Batch(Base):
    __tablename__ = "batches"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=uid)
    business_id: Mapped[str] = mapped_column(ForeignKey("businesses.id"))
    name: Mapped[str] = mapped_column(String(120))
    chicks: Mapped[int] = mapped_column(Integer)
    deaths: Mapped[int] = mapped_column(Integer, default=0)
    total_shares: Mapped[int] = mapped_column(Integer)
    share_price: Mapped[int] = mapped_column(BigInteger)
    expenses: Mapped[int] = mapped_column(BigInteger)
    revenue: Mapped[int] = mapped_column(BigInteger, default=0)
    net_profit: Mapped[int] = mapped_column(BigInteger, default=0)
    yield_kg: Mapped[float] = mapped_column(Numeric(18, 3), default=0)
    status: Mapped[str] = mapped_column(default="FUNDING")
    started_on: Mapped[date | None] = mapped_column(Date)
    closed_on: Mapped[date | None] = mapped_column(Date)


class BatchLog(Base):
    __tablename__ = "batch_logs"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=uid)
    batch_id: Mapped[str] = mapped_column(ForeignKey("batches.id"))
    date: Mapped[date] = mapped_column(Date)
    feed_kg: Mapped[float] = mapped_column(Numeric(18, 3))
    deaths: Mapped[int] = mapped_column(Integer)
    expense: Mapped[int] = mapped_column(BigInteger)
    note: Mapped[str] = mapped_column(String(1000), default="")
    __table_args__ = (UniqueConstraint("batch_id", "date"),)


class Ownership(Base):
    __tablename__ = "share_ledger"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=uid)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id"))
    business_id: Mapped[str] = mapped_column(ForeignKey("businesses.id"))
    batch_id: Mapped[str | None] = mapped_column(ForeignKey("batches.id"))
    shares: Mapped[int] = mapped_column(Integer)
    paid: Mapped[int] = mapped_column(BigInteger)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)
    __table_args__ = (CheckConstraint("shares > 0 AND paid > 0"),)


class Journal(Base):
    __tablename__ = "journal"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=uid)
    reference: Mapped[str] = mapped_column(String(150), unique=True)
    kind: Mapped[str] = mapped_column(String(30))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)


class Posting(Base):
    __tablename__ = "postings"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=uid)
    journal_id: Mapped[str] = mapped_column(ForeignKey("journal.id"), index=True)
    account: Mapped[str] = mapped_column(String(100), index=True)
    amount: Mapped[int] = mapped_column(BigInteger)


class Settlement(Base):
    __tablename__ = "settlements"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=uid)
    source: Mapped[str] = mapped_column(String(60), unique=True)
    business_id: Mapped[str] = mapped_column(ForeignKey("businesses.id"))
    net_profit: Mapped[int] = mapped_column(BigInteger)
    distributed: Mapped[int] = mapped_column(BigInteger)
    retained: Mapped[int] = mapped_column(BigInteger)
    snapshot: Mapped[dict] = mapped_column(JSON)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)


class Withdrawal(Base):
    __tablename__ = "withdrawals"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=uid)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id"))
    amount: Mapped[int] = mapped_column(BigInteger)
    provider: Mapped[str] = mapped_column(String(20))
    destination: Mapped[str] = mapped_column(String(120))
    status: Mapped[str] = mapped_column(default="PENDING")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)


class Idempotency(Base):
    __tablename__ = "idempotency"
    scope: Mapped[str] = mapped_column(String(250), primary_key=True)
    key: Mapped[str] = mapped_column(String(100), primary_key=True)
    digest: Mapped[str] = mapped_column(String(64))
    response: Mapped[dict] = mapped_column(JSON)
