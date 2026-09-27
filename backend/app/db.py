import os

from dotenv import load_dotenv
from sqlalchemy import create_engine, event, inspect, select, text
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.orm import DeclarativeBase, sessionmaker


def ensure_business_icon_column_on_connection(conn):
    """Add the nullable icon column without changing existing business records."""
    columns = inspect(conn).get_columns("businesses")
    if not any(column["name"] == "icon_url" for column in columns):
        clause = "IF NOT EXISTS " if conn.dialect.name == "postgresql" else ""
        conn.execute(text(f"ALTER TABLE businesses ADD COLUMN {clause}icon_url VARCHAR(500)"))


def ensure_column_on_connection(conn, table, column, ddl):
    """Add one missing column to an existing table; repeatable and additive."""
    if not inspect(conn).has_table(table):
        return
    if any(c["name"] == column for c in inspect(conn).get_columns(table)):
        return
    clause = "IF NOT EXISTS " if conn.dialect.name == "postgresql" else ""
    try:
        conn.execute(text(f"ALTER TABLE {table} ADD COLUMN {clause}{ddl}"))
    except Exception as exc:
        if "duplicate column" not in str(exc).lower() and "already exists" not in str(exc).lower():
            raise


def ensure_additive_columns_on_connection(conn):
    """Self-heal legacy databases: icon column, bird count, expense category,
    and counted stock. Safe to run on every connection; never touches data."""
    if not inspect(conn).has_table("businesses"):
        return
    ensure_business_icon_column_on_connection(conn)
    ensure_column_on_connection(conn, "operations", "count", "count INTEGER")
    ensure_column_on_connection(conn, "operations", "category", "category VARCHAR(50)")
    ensure_column_on_connection(
        conn, "businesses", "stock_count", "stock_count INTEGER NOT NULL DEFAULT 0"
    )


async def ensure_business_icon_column():
    """Backfill legacy Postgres/SQLite databases that predate the business icon column."""
    async with engine.begin() as conn:
        await conn.run_sync(ensure_business_icon_column_on_connection)


async def ensure_additive_columns():
    """Backfill legacy databases that predate bird counts / expense categories."""
    async with engine.begin() as conn:
        await conn.run_sync(ensure_additive_columns_on_connection)


load_dotenv()
URL = os.getenv("DATABASE_URL", "sqlite+aiosqlite:///./ahsan.db")

# Convert synchronous URL to async URL if needed
if URL.startswith("postgresql://"):
    sync_url = URL.replace("postgresql://", "postgresql+psycopg://", 1)
    URL = URL.replace("postgresql://", "postgresql+asyncpg://", 1)
elif URL.startswith("postgresql+psycopg://"):
    sync_url = URL
    URL = URL.replace("postgresql+psycopg://", "postgresql+asyncpg://", 1)
elif URL.startswith("postgresql+asyncpg://"):
    sync_url = URL.replace("postgresql+asyncpg://", "postgresql+psycopg://", 1)
elif URL.startswith("sqlite:///"):
    sync_url = URL
    URL = URL.replace("sqlite:///", "sqlite+aiosqlite:///", 1)
elif URL.startswith("sqlite+aiosqlite:///"):
    sync_url = URL.replace("sqlite+aiosqlite:///", "sqlite:///", 1)
else:
    sync_url = URL

engine = create_async_engine(
    URL,
    **(
        {"connect_args": {"check_same_thread": False}}
        if URL.startswith("sqlite")
        else {"pool_pre_ping": True}
    ),
)

# Keep sync engine for migrations that don't support async yet
sync_engine = create_engine(
    sync_url,
    **(
        {"connect_args": {"check_same_thread": False}}
        if sync_url.startswith("sqlite")
        else {"pool_pre_ping": True}
    ),
)

if sync_url.startswith("sqlite"):

    @event.listens_for(sync_engine, "connect")
    def sqlite_config(connection, _):
        connection.execute("PRAGMA foreign_keys=ON")
        connection.execute("PRAGMA busy_timeout=10000")


AsyncSessionLocal = async_sessionmaker(engine, expire_on_commit=False)
Session = sessionmaker(sync_engine, expire_on_commit=False)


class Base(DeclarativeBase):
    pass


async def get_db():
    from .models import WriteLock

    await ensure_additive_columns()

    async with AsyncSessionLocal() as db:
        async with db.begin():
            # MVP correctness over throughput: serialize transactions. PostgreSQL
            # releases this row lock on commit/rollback, including across workers.
            if engine.dialect.name == "sqlite":
                await db.execute(text("BEGIN IMMEDIATE"))
            else:
                result = await db.execute(
                    select(WriteLock).where(WriteLock.id == 1).with_for_update()
                )
                result.scalar_one()
            yield db
