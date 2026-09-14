"""Explicit initialization and owner provisioning; never expose these over HTTP."""

import argparse
import getpass
import os

from sqlalchemy import select, text

from .db import Base, Session, engine
from .models import Business, User, WriteLock
from .schemas import Register
from .security import passwords


def init():
    Base.metadata.create_all(engine)
    with Session.begin() as db:
        if not db.get(WriteLock, 1):
            db.add(WriteLock(id=1))
    if engine.dialect.name == "postgresql":
        with engine.begin() as conn:
            conn.execute(
                text("""CREATE OR REPLACE FUNCTION reject_ledger_mutation() RETURNS trigger AS $$
            BEGIN RAISE EXCEPTION 'Financial history is append-only'; END; $$ LANGUAGE plpgsql""")
            )
            for table in ("journal", "postings", "share_ledger", "settlements"):
                conn.execute(
                    text(f"DROP TRIGGER IF EXISTS immutable_history ON {table}")
                )
                conn.execute(
                    text(
                        f"CREATE TRIGGER immutable_history BEFORE UPDATE OR DELETE ON {table} FOR EACH ROW EXECUTE FUNCTION reject_ledger_mutation()"
                    )
                )
    print(
        "Schema initialized. Use migrations for future schema changes; init is not an upgrade tool."
    )


def seed():
    p = Register(
        phone=os.getenv("ADMIN_PHONE") or input("Owner phone (+923...): "),
        name="Ahsan Khan",
        password=os.getenv("ADMIN_PASSWORD")
        or getpass.getpass("Owner password (10+ characters): "),
    )
    with Session.begin() as db:
        if not db.scalar(select(User).where(User.phone == p.phone)):
            db.add(
                User(
                    phone=p.phone,
                    name=p.name,
                    password_hash=passwords.hash(p.password),
                    role="SUPERADMIN",
                )
            )
        if not db.scalar(select(Business.id).limit(1)):
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


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["init-db", "seed"])
    args = parser.parse_args()
    init() if args.command == "init-db" else seed()
