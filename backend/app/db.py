import os
from contextlib import asynccontextmanager

from dotenv import load_dotenv
from sqlalchemy import create_engine, event, inspect, select, text
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.orm import DeclarativeBase, sessionmaker


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


def ensure_index_on_connection(conn, table, name, columns):
    """Create one missing index on an existing table; repeatable and additive."""
    if not inspect(conn).has_table(table):
        return
    if any(index["name"] == name for index in inspect(conn).get_indexes(table)):
        return
    conn.execute(
        text(f"CREATE INDEX IF NOT EXISTS {name} ON {table} ({', '.join(columns)})")
    )


def ensure_additive_columns_on_connection(conn):
    """Self-heal legacy databases: bird count, expense category, counted stock,
    chicken-sale wastage and the operation history index. Safe to run on every
    connection; never touches data."""
    if not inspect(conn).has_table("businesses"):
        return
    ensure_column_on_connection(conn, "operations", "count", "count INTEGER")
    ensure_column_on_connection(conn, "operations", "category", "category VARCHAR(50)")
    ensure_column_on_connection(
        conn, "operations", "wastage", "wastage NUMERIC(18, 3) DEFAULT 0 NOT NULL"
    )
    ensure_column_on_connection(
        conn, "businesses", "stock_count", "stock_count INTEGER NOT NULL DEFAULT 0"
    )
    ensure_index_on_connection(conn, "operations", "ix_operations_day_id", ["day_id"])


# Tables and columns that belonged to the removed database-backed icon storage.
# They are no longer part of the ORM metadata, so `init-db` never recreates
# them; this is the one-time removal path for databases that still have them.
ICON_TABLES = ("app_icons", "image_assets")
ICON_COLUMNS = (
    ("app_icons", "asset_id"),
    ("businesses", "icon_asset_id"),
    ("businesses", "icon_url"),
)


def drop_icon_schema_on_connection(conn) -> dict:
    """Remove every trace of the database-backed icon schema. Repeatable.

    Columns are dropped before their tables so the foreign keys pointing at
    ``image_assets`` disappear with them. Old SQLite builds without
    ``ALTER TABLE DROP COLUMN`` are reported as skipped instead of failing, so
    the operator can see what still needs manual attention.
    """
    report: dict[str, list] = {"dropped_tables": [], "dropped_columns": [], "skipped": []}
    inspector = inspect(conn)
    for table, column in ICON_COLUMNS:
        if not inspector.has_table(table):
            continue
        if not any(c["name"] == column for c in inspector.get_columns(table)):
            continue
        try:
            conn.execute(text(f"ALTER TABLE {table} DROP COLUMN {column}"))
            report["dropped_columns"].append(f"{table}.{column}")
        except Exception as exc:
            report["skipped"].append(
                {"target": f"{table}.{column}", "reason": str(exc).splitlines()[0][:200]}
            )
    for table in ICON_TABLES:
        if not inspector.has_table(table):
            continue
        conn.execute(text(f"DROP TABLE {table}"))
        report["dropped_tables"].append(table)
    return report


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
    async with write_session() as db:
        yield db


@asynccontextmanager
async def write_session():
    """Open a serialized write transaction.

    Used by request handling and by the scheduled `auto-close-days` command so
    both obey the same lock: MVP correctness over throughput. PostgreSQL
    releases this row lock on commit/rollback, including across workers.
    """
    from .models import WriteLock

    await ensure_additive_columns()

    async with AsyncSessionLocal() as db:
        async with db.begin():
            if engine.dialect.name == "sqlite":
                await db.execute(text("BEGIN IMMEDIATE"))
            else:
                result = await db.execute(
                    select(WriteLock).where(WriteLock.id == 1).with_for_update()
                )
                result.scalar_one()
            yield db
