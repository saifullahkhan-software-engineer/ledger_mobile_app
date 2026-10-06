"""Explicit schema migration commands; never exposed over HTTP.

Commands
- init-db:         create the current schema on an EMPTY database only.
- upgrade-db:      repeatable additive schema upgrade for existing databases.
- drop-icon-schema: one-time removal of the retired database-backed icon
                   storage (app_icons, image_assets, businesses.icon_url and
                   the asset foreign keys). Destructive; dry run by default.
- seed:           create the owner account and sample businesses.
- auto-close-days: close (and settle) every day whose business date has ended;
                  safe to run from cron every few minutes. The API also performs
                  this sweep itself before it reports or changes day status.
- clear-data:      delete every record (ledger, days, batches, shares,
                   settlements, withdrawals, investors) while KEEPING the
                   SUPERADMIN/ADMIN accounts. Dry run unless --yes is passed.
"""

import argparse
import asyncio
import getpass
import os
import sys
from contextlib import asynccontextmanager

from sqlalchemy import inspect, select, text

from .db import (
    Base,
    AsyncSessionLocal,
    engine,
    sync_engine,
    ICON_COLUMNS,
    ICON_TABLES,
    drop_icon_schema_on_connection,
    ensure_additive_columns,
    ensure_additive_columns_on_connection,
    write_session,
)
from .models import Business, User, WriteLock
from .schemas import Register
from .security import passwords


async def init():
    # Use sync engine for metadata creation (doesn't support async)
    Base.metadata.create_all(sync_engine)
    
    # Use async session for data operations
    async with AsyncSessionLocal() as db:
        async with db.begin():
            result = await db.execute(select(WriteLock).where(WriteLock.id == 1))
            if not result.scalar_one_or_none():
                db.add(WriteLock(id=1))
    
    if sync_engine.dialect.name == "postgresql":
        async with engine.begin() as conn:
            await conn.execute(
                text("""CREATE OR REPLACE FUNCTION reject_ledger_mutation() RETURNS trigger AS $$
            BEGIN RAISE EXCEPTION 'Financial history is append-only'; END; $$ LANGUAGE plpgsql""")
            )
            for table in ("journal", "postings", "share_ledger", "settlements"):
                await conn.execute(
                    text(f"DROP TRIGGER IF EXISTS immutable_history ON {table}")
                )
                await conn.execute(
                    text(
                        f"CREATE TRIGGER immutable_history BEFORE UPDATE OR DELETE ON {table} FOR EACH ROW EXECUTE FUNCTION reject_ledger_mutation()"
                    )
                )
    print(
        "Schema initialized. Use migrations for future schema changes; init is not an upgrade tool."
    )


async def _apply_upgrade() -> None:
    """Apply the additive schema upgrade to an initialized database."""
    async with engine.begin() as conn:
        initialized = await conn.run_sync(
            lambda connection: inspect(connection).has_table("businesses")
        )
        if not initialized:
            raise RuntimeError(
                "Database is not initialized. Run python -m app.manage init-db first."
            )
        await conn.run_sync(ensure_additive_columns_on_connection)


async def upgrade():
    """Repeatable additive schema upgrade; no data is altered or deleted."""
    await _apply_upgrade()
    print(
        "Upgraded legacy database schema (missing columns and indexes added, including operations.wastage and operations.live_weight). "
        "Existing users, businesses and financial history are preserved. "
        "Databases created before the custom-icon feature was removed may "
        "still hold the retired icon tables; run 'python -m app.manage "
        "drop-icon-schema' to remove them."
    )


async def drop_icon_schema(*, execute: bool = False) -> dict:
    """Remove the retired database-backed icon schema. Dry run by default.

    Drops ``app_icons``, ``image_assets``, ``businesses.icon_url`` and the two
    asset foreign keys. Uploaded icon images are permanently lost, which is the
    point: the apps now resolve every icon from their bundled drawables.
    """
    if not _database_is_initialized():
        raise RuntimeError(
            "Database is not initialized. Run python -m app.manage init-db first."
        )
    report: dict = {"mode": "executed" if execute else "dry-run", "database": None}

    def collect(conn):
        inspector = inspect(conn)
        found_columns = [
            f"{table}.{column}"
            for table, column in ICON_COLUMNS
            if inspector.has_table(table)
            and any(c["name"] == column for c in inspector.get_columns(table))
        ]
        found_tables = [t for t in ICON_TABLES if inspector.has_table(t)]
        if execute:
            result = drop_icon_schema_on_connection(conn)
        else:
            result = {"dropped_tables": [], "dropped_columns": [], "skipped": []}
        return found_columns, found_tables, result

    if execute:
        async with engine.begin() as conn:
            found_columns, found_tables, result = await conn.run_sync(collect)
    else:
        async with engine.connect() as conn:
            found_columns, found_tables, result = await conn.run_sync(collect)

    report["database"] = engine.url.render_as_string(hide_password=True)
    report["icon_columns"] = result["dropped_columns"] or found_columns
    report["icon_tables"] = result["dropped_tables"] or found_tables
    report["skipped"] = result["skipped"]
    return report


def _print_drop_icon_schema(report: dict) -> None:
    executed = report["mode"] == "executed"
    verb = "Dropped" if executed else "Would drop"
    print(f"drop-icon-schema — {'EXECUTED' if executed else 'DRY RUN (nothing was changed)'}")
    print(f"Database: {report['database']}")
    print()
    if report["icon_columns"] or report["icon_tables"]:
        for column in report["icon_columns"]:
            print(f"  {verb} column  {column}")
        for table in report["icon_tables"]:
            print(f"  {verb} table   {table}")
    else:
        print("  Nothing to do — this database has no icon schema left.")
    for skip in report["skipped"]:
        print(f"  SKIPPED {skip['target']}: {skip['reason']}")
    if not executed and (report["icon_columns"] or report["icon_tables"]):
        print(
            "\nDry run only. Re-run with --yes to drop these permanently "
            "(add --force to skip the typed confirmation)."
        )


async def drop_icon_schema_command(args) -> None:
    """Dry run first; drop only after --yes plus an explicit typed DROP."""
    plan = await drop_icon_schema(execute=False)
    _print_drop_icon_schema(plan)
    if not plan["icon_columns"] and not plan["icon_tables"]:
        return
    if not args.yes:
        return
    if not args.force:
        try:
            answer = input("\nType DROP to permanently remove the icon schema: ")
        except (EOFError, KeyboardInterrupt):
            print("\nAborted; nothing was dropped.")
            return
        if answer.strip() != "DROP":
            print("Aborted; nothing was dropped.")
            return
    report = await drop_icon_schema(execute=True)
    print()
    _print_drop_icon_schema(report)
    print(
        f"\nDone. {len(report['icon_tables'])} table(s) and "
        f"{len(report['icon_columns'])} column(s) removed."
    )


async def seed():
    p = Register(
        phone=os.getenv("ADMIN_PHONE") or input("Owner phone (+923...): "),
        name="Ahsan Khan",
        password=os.getenv("ADMIN_PASSWORD")
        or getpass.getpass("Owner password: "),
    )
    async with AsyncSessionLocal() as db:
        async with db.begin():
            result = await db.execute(select(User).where(User.phone == p.phone))
            if not result.scalar_one_or_none():
                db.add(
                    User(
                        phone=p.phone,
                        name=p.name,
                        password_hash=passwords.hash(p.password),
                        role="SUPERADMIN",
                    )
                )
            result = await db.execute(select(Business.id).limit(1))
            if not result.scalar_one_or_none():
                for kind, name in [
                    ("CHICKEN", "Ahsan Chicken Shop"),
                    ("LPG", "Ahsan LPG Business"),
                    ("BROILER", "Ahsan Broiler Farming"),
                ]:
                    db.add(
                        Business(name=name, type=kind, total_shares=1000, share_price=10000)
                    )
    print(
        "Owner and sample businesses ready; existing credentials are not overwritten."
    )


async def auto_close_days():
    """Close and settle every day whose Asia/Karachi business date has ended.

    Uses the same serialized write transaction as the API, so it is safe to run
    from cron while the server is serving requests.
    """
    from .services import auto_close_due_days

    async with write_session() as db:
        closed = await auto_close_due_days(db)
        summary = ", ".join(
            f"{day.date} ({day.business_id})" for day in closed
        )
    if not closed:
        print("No days past their business date; nothing to close.")
        return
    print(f"Auto-closed {len(closed)} day(s): {summary}")


# ---------------------------------------------------------------------------
# clear-data: wipe every record but keep the owner and manager accounts.
# ---------------------------------------------------------------------------

KNOWN_ROLES = ("SUPERADMIN", "ADMIN", "INVESTOR")
KEPT_ROLES_DEFAULT = "SUPERADMIN,ADMIN"

# Tables whose rows PostgreSQL protects with the append-only `immutable_history`
# trigger installed by init-db. The reset lifts that guard for its own
# transaction only (see _disable_history_triggers).
IMMUTABLE_TABLES = ("journal", "postings", "share_ledger", "settlements")

# Every table holding business records, in foreign-key-safe delete order, so the
# same plan works whether or not the engine enforces foreign keys. write_lock is
# deliberately absent: it is the API's serialization row, not data.
PURGED_TABLES = {
    "idempotency": "cached API responses (idempotency keys)",
    "withdrawals": "investor withdrawal requests",
    "share_ledger": "sold shares / investor ownership",
    "postings": "double-entry postings (wallet balances, payouts)",
    "journal": "journal entries",
    "settlements": "daily payout settlements",
    "batch_logs": "broiler batch daily logs",
    "batches": "broiler batches",
    "operations": "sales, purchases and expenses (the ledger records)",
    "daily_ledgers": "per-business day summaries",
    "suppliers": "suppliers",
}


def _step(table, sql, count_sql, params=None, describe=""):
    return {
        "table": table,
        "sql": sql,
        "count": count_sql,
        "params": params or {},
        "describe": describe or PURGED_TABLES.get(table, ""),
        "action": "delete" if sql.lstrip().upper().startswith("DELETE") else "update",
    }


def clear_data_steps(
    *, keep_in, keep_params, delete_businesses, force_relogin
):
    """Build the ordered reset plan: one entry per DELETE/UPDATE to run."""
    steps = [
        _step(table, f"DELETE FROM {table}", f"SELECT count(*) FROM {table}")
        for table in PURGED_TABLES
    ]
    orphaned_assignments = (
        "DELETE FROM admin_assignments WHERE user_id NOT IN "
        f"(SELECT id FROM users WHERE role IN ({keep_in}))"
    )
    if delete_businesses:
        steps.append(
            _step(
                "admin_assignments",
                "DELETE FROM admin_assignments",
                "SELECT count(*) FROM admin_assignments",
                describe="manager -> business assignments",
            )
        )
        steps.append(
            _step(
                "businesses",
                "DELETE FROM businesses",
                "SELECT count(*) FROM businesses",
                describe="businesses (Chicken, LPG, Broiler, ...)",
            )
        )
    else:
        steps.append(
            _step(
                "admin_assignments",
                orphaned_assignments,
                orphaned_assignments.replace("DELETE FROM", "SELECT count(*) FROM", 1),
                keep_params,
                describe="assignments of the accounts being deleted",
            )
        )
    steps.append(
        _step(
            "users",
            f"DELETE FROM users WHERE role IS NULL OR role NOT IN ({keep_in})",
            f"SELECT count(*) FROM users WHERE role IS NULL OR role NOT IN ({keep_in})",
            keep_params,
            describe="every account that is not SUPERADMIN/ADMIN",
        )
    )
    if not delete_businesses:
        steps.append(
            _step(
                "businesses",
                "UPDATE businesses SET stock = 0, stock_cost = 0, stock_count = 0 "
                "WHERE stock <> 0 OR stock_cost <> 0 OR stock_count <> 0",
                "SELECT count(*) FROM businesses "
                "WHERE stock <> 0 OR stock_cost <> 0 OR stock_count <> 0",
                describe="business stock reset to zero",
            )
        )
    if force_relogin:
        steps.append(
            _step(
                "users",
                f"UPDATE users SET token_version = token_version + 1 "
                f"WHERE role IN ({keep_in})",
                f"SELECT count(*) FROM users WHERE role IN ({keep_in})",
                keep_params,
                describe="sign the kept accounts out of every device",
            )
        )
    return steps


def _database_is_initialized() -> bool:
    return inspect(sync_engine).has_table("users")


@asynccontextmanager
async def _reset_transaction(execute: bool):
    """Serialized transaction that only commits when ``execute`` is true.

    Same lock discipline as :func:`app.db.write_session`, so a running API (or
    the ``auto-close-days`` cron) cannot interleave a write with the reset. A
    dry run performs SELECT counts only and rolls its transaction back.
    """
    await ensure_additive_columns()
    db = AsyncSessionLocal()
    transaction = await db.begin()
    committed = False
    try:
        if engine.dialect.name == "sqlite":
            await db.execute(text("BEGIN IMMEDIATE"))
        else:
            result = await db.execute(
                select(WriteLock).where(WriteLock.id == 1).with_for_update()
            )
            result.scalar_one()
        yield db
        if execute:
            await transaction.commit()
            committed = True
        else:
            await transaction.rollback()
    finally:
        if not committed:
            try:
                await transaction.rollback()
            except Exception:
                pass  # already rolled back / connection lost
        await db.close()


async def _disable_history_triggers(db, tables):
    """Lift the append-only guard for this transaction only.

    ``DISABLE TRIGGER USER`` switches off user-defined triggers while leaving
    PostgreSQL's internally generated foreign-key constraint triggers active, so
    referential integrity is still enforced. The change is transactional: if the
    reset rolls back, the triggers are enabled again by the rollback itself.
    """
    for table in tables:
        try:
            await db.execute(text(f"ALTER TABLE {table} DISABLE TRIGGER USER"))
        except Exception as exc:
            raise RuntimeError(
                f"Cannot lift the append-only guard on '{table}': {exc}. Run this "
                "command as the database role that owns the tables (the user in "
                "DATABASE_URL), or drop the immutable_history triggers first."
            ) from exc
    return list(tables)


async def _enable_history_triggers(db, tables):
    """Best-effort restore; a rollback restores them anyway (DDL is transactional)."""
    for table in tables:
        try:
            await db.execute(text(f"ALTER TABLE {table} ENABLE TRIGGER USER"))
        except Exception:
            return


async def clear_data(
    *,
    kept_roles=KEPT_ROLES_DEFAULT,
    delete_businesses: bool = False,
    force_relogin: bool = False,
    execute: bool = False,
) -> dict:
    """Delete every record except the accounts of the roles listed in kept_roles.

    Dry run by default (``execute=False``): counts exactly what would be deleted
    and changes nothing. Returns the report that the CLI prints.
    """
    if isinstance(kept_roles, str):
        kept_roles = kept_roles.split(",")
    roles = tuple(str(role).strip().upper() for role in kept_roles if str(role).strip())
    if not roles:
        raise RuntimeError("clear-data needs at least one role to keep")
    unknown = sorted(set(roles) - set(KNOWN_ROLES))
    if unknown:
        raise RuntimeError(
            f"Unknown role(s) {', '.join(unknown)}; this deployment uses "
            f"{', '.join(KNOWN_ROLES)}"
        )
    if not _database_is_initialized():
        raise RuntimeError(
            "Database is not initialized. Run python -m app.manage init-db first."
        )
    keep_in = ", ".join(f":keep{i}" for i in range(len(roles)))
    keep_params = {f"keep{i}": role for i, role in enumerate(roles)}
    steps = clear_data_steps(
        keep_in=keep_in,
        keep_params=keep_params,
        delete_businesses=delete_businesses,
        force_relogin=force_relogin,
    )

    async with _reset_transaction(execute) as db:
        kept_users = [
            {"id": row.id, "phone": row.phone, "name": row.name, "role": row.role}
            for row in (
                await db.execute(
                    select(User).where(User.role.in_(roles)).order_by(User.role, User.phone)
                )
            ).scalars()
        ]
        if not kept_users:
            raise RuntimeError(
                f"No account has role {', '.join(roles)}, so this would delete "
                "every user. Run python -m app.manage seed to create the owner "
                "first, or pass --keep-roles with a role that exists."
            )
        removed = (
            await db.execute(
                text(
                    "SELECT coalesce(role, '(no role)') AS role, count(*) AS n "
                    "FROM users WHERE role IS NULL OR role NOT IN "
                    f"({keep_in}) GROUP BY role ORDER BY role"
                ),
                keep_params,
            )
        ).all()
        counts = {}
        totals = {"delete": 0, "update": 0}
        disabled = []
        try:
            if execute and engine.dialect.name == "postgresql":
                disabled = await _disable_history_triggers(db, IMMUTABLE_TABLES)
            for step in steps:
                if execute:
                    result = await db.execute(text(step["sql"]), step["params"])
                    rows = max(int(result.rowcount or 0), 0)
                else:
                    rows = int(await db.scalar(text(step["count"]), step["params"]) or 0)
                counts[step["table"]] = counts.get(step["table"], 0) + rows
                totals[step["action"]] = totals.get(step["action"], 0) + rows
                step["rows"] = rows
        finally:
            if disabled:
                await _enable_history_triggers(db, disabled)

    return {
        "mode": "executed" if execute else "dry-run",
        "database": engine.url.render_as_string(hide_password=True),
        "kept_roles": list(roles),
        "kept_users": kept_users,
        "deleted_users_by_role": {row[0]: int(row[1]) for row in removed},
        "steps": steps,
        "rows": counts,
        "totals": totals,
        "businesses": "deleted" if delete_businesses else "kept (stock reset to 0)",
        "kept_tables": ["write_lock"],
    }


def _print_clear_data(report: dict) -> None:
    executed = report["mode"] == "executed"
    verb = "Deleted" if executed else "Would delete"
    print(f"clear-data — {'EXECUTED' if executed else 'DRY RUN (nothing was changed)'}")
    print(f"Database: {report['database']}")
    print()
    print(f"Keeping {len(report['kept_users'])} account(s): {', '.join(report['kept_roles'])}")
    for user in report["kept_users"]:
        print(f"  {user['role']:<10} {user['phone']:<16} {user['name']}")
    removed = report["deleted_users_by_role"]
    if removed:
        detail = ", ".join(f"{role}: {n}" for role, n in sorted(removed.items()))
        print(f"{verb} {sum(removed.values())} other account(s) ({detail})")
    else:
        print("No other accounts to delete")
    print()

    def show(action, heading, empty=""):
        steps = [step for step in report["steps"] if step["action"] == action]
        print(heading)
        if not any(step["rows"] for step in steps):
            print(f"  {empty}")
            return
        for step in steps:
            if not step["rows"]:
                continue
            label = step["describe"] or step["table"]
            print(f"  {step['rows']:>8}  {step['table']:<20} {label}")

    show("delete", f"{verb}:", "nothing — the database already holds no records")
    if any(step["rows"] for step in report["steps"] if step["action"] == "update"):
        print()
        show("update", "Updating:" if executed else "Would also update:")
    print()
    print(f"Businesses: {report['businesses']}")
    print("write_lock row kept (API serialization lock, not data)")


async def clear_data_command(args) -> None:
    """Dry run first; delete only after --yes plus an explicit typed DELETE."""
    options = dict(
        kept_roles=args.keep_roles,
        delete_businesses=args.delete_businesses,
        force_relogin=args.force_relogin,
    )
    print(
        "Stop the API (uvicorn) and any auto-close-days cron before clearing data.\n"
    )
    plan = await clear_data(execute=False, **options)
    _print_clear_data(plan)
    if not args.yes:
        print(
            "\nDry run only. Re-run with --yes to delete these rows "
            "(add --force to skip the typed confirmation)."
        )
        return
    if not args.force:
        try:
            answer = input("\nType DELETE to permanently remove these records: ")
        except (EOFError, KeyboardInterrupt):
            print("\nAborted; nothing was deleted.")
            return
        if answer.strip() != "DELETE":
            print("Aborted; nothing was deleted.")
            return
    report = await clear_data(execute=True, **options)
    print()
    _print_clear_data(report)
    deleted = report["totals"]["delete"]
    updated = report["totals"]["update"]
    print(
        f"\nDone. {deleted} row(s) deleted"
        + (f" and {updated} row(s) updated" if updated else "")
        + f" in one transaction; {len(report['kept_users'])} account(s) kept and "
        "still able to sign in."
    )


if __name__ == "__main__":
    parser = argparse.ArgumentParser(prog="python -m app.manage")
    commands = {
        "init-db": init,
        "upgrade-db": upgrade,
        "seed": seed,
        "auto-close-days": auto_close_days,
    }
    subparsers = parser.add_subparsers(dest="command", required=True)
    help_text = {
        "init-db": "create the schema on an empty database",
        "upgrade-db": "repeatable additive schema upgrade (no data changes)",
        "seed": "create the owner account and sample businesses",
        "auto-close-days": "close and settle every day whose business date ended",
    }
    for name in commands:
        subparsers.add_parser(name, help=help_text[name])
    clear = subparsers.add_parser(
        "clear-data",
        help="delete every record but keep the SUPERADMIN/ADMIN accounts",
        description=(
            "Delete all business records (operations, days, batches, shares, "
            "settlements, withdrawals, investors) while keeping the SUPERADMIN "
            "and ADMIN accounts. Prints a dry run unless --yes is passed."
        ),
    )
    clear.add_argument(
        "--yes",
        action="store_true",
        help="actually delete; without it the command only reports what it would delete",
    )
    clear.add_argument(
        "--force",
        action="store_true",
        help="skip the typed DELETE confirmation (with --yes, for scripts)",
    )
    clear.add_argument(
        "--keep-roles",
        default=KEPT_ROLES_DEFAULT,
        help=f"comma-separated roles to keep (default: {KEPT_ROLES_DEFAULT})",
    )
    clear.add_argument(
        "--delete-businesses",
        action="store_true",
        help="also delete the businesses and their manager assignments "
        "(default: keep them with stock reset to 0)",
    )
    clear.add_argument(
        "--force-relogin",
        action="store_true",
        help="bump token_version on the kept accounts so signed-in devices must log in again",
    )
    drop = subparsers.add_parser(
        "drop-icon-schema",
        help="remove the retired database-backed icon tables and columns",
        description=(
            "Drop app_icons, image_assets, businesses.icon_url and the asset "
            "foreign keys left behind by the removed custom-icon feature. "
            "Prints a dry run unless --yes is passed."
        ),
    )
    drop.add_argument(
        "--yes",
        action="store_true",
        help="actually drop; without it the command only reports what it would drop",
    )
    drop.add_argument(
        "--force",
        action="store_true",
        help="skip the typed DROP confirmation (with --yes, for scripts)",
    )
    args = parser.parse_args()
    if args.command != "init-db":
        with sync_engine.begin() as conn:
            ensure_additive_columns_on_connection(conn)
    if args.command == "clear-data":
        try:
            asyncio.run(clear_data_command(args))
        except RuntimeError as exc:
            print(f"clear-data aborted: {exc}", file=sys.stderr)
            raise SystemExit(1)
    elif args.command == "drop-icon-schema":
        try:
            asyncio.run(drop_icon_schema_command(args))
        except RuntimeError as exc:
            print(f"drop-icon-schema aborted: {exc}", file=sys.stderr)
            raise SystemExit(1)
    else:
        asyncio.run(commands[args.command]())
