import os

from dotenv import load_dotenv
from sqlalchemy import create_engine, event, select
from sqlalchemy.orm import DeclarativeBase, sessionmaker

load_dotenv()
URL = os.getenv("DATABASE_URL", "sqlite:///./ahsan.db")
engine = create_engine(
    URL,
    **(
        {"connect_args": {"check_same_thread": False}}
        if URL.startswith("sqlite")
        else {"pool_pre_ping": True}
    ),
)
if URL.startswith("sqlite"):

    @event.listens_for(engine, "connect")
    def sqlite_config(connection, _):
        connection.execute("PRAGMA foreign_keys=ON")
        connection.execute("PRAGMA busy_timeout=10000")


Session = sessionmaker(engine, expire_on_commit=False)


class Base(DeclarativeBase):
    pass


def get_db():
    from .models import WriteLock

    with Session() as db:
        with db.begin():
            # MVP correctness over throughput: serialize transactions. PostgreSQL
            # releases this row lock on commit/rollback, including across workers.
            if engine.dialect.name == "sqlite":
                db.connection().exec_driver_sql("BEGIN IMMEDIATE")
            else:
                db.execute(
                    select(WriteLock).where(WriteLock.id == 1).with_for_update()
                ).scalar_one()
            yield db
