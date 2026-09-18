import os

os.environ.setdefault("JWT_SECRET", "test-secret-at-least-thirty-two-characters-long")
os.environ["APP_ENV"] = "development"
os.environ["ENABLE_MOCK_PAYMENTS"] = "true"
os.environ.setdefault("DATABASE_URL", "sqlite:///./test.db")
import pytest
import asyncio
from fastapi.testclient import TestClient
from app.db import Base, Session, sync_engine as engine

if "test" not in (engine.url.database or "").lower():
    raise RuntimeError(
        "Tests destroy tables: DATABASE_URL must name a dedicated test database."
    )
from app.models import User, Business
from app.security import passwords
from app.main import app


@pytest.fixture()
def client():
    # Dedicated test database ONLY. Do not point DATABASE_URL at real data.
    Base.metadata.drop_all(engine)
    from app.manage import init

    asyncio.run(init())
    with Session.begin() as db:
        db.add_all(
            [
                User(
                    id="admin",
                    phone="+923001234567",
                    name="Admin",
                    password_hash=passwords.hash("AdminTest123!"),
                    role="SUPERADMIN",
                ),
                User(
                    id="manager",
                    phone="+923001234568",
                    name="Manager",
                    password_hash=passwords.hash("AdminTest123!"),
                    role="ADMIN",
                ),
                Business(
                    id="chicken",
                    name="Chicken",
                    type="CHICKEN",
                    total_shares=10,
                    share_price=10000,
                ),
                Business(
                    id="lpg", name="LPG", type="LPG", total_shares=10, share_price=10000
                ),
                Business(
                    id="broiler",
                    name="Broiler",
                    type="BROILER",
                    total_shares=10,
                    share_price=10000,
                ),
            ]
        )
    with TestClient(app) as c:
        yield c
    Base.metadata.drop_all(engine)


@pytest.fixture()
def admin_headers(client):
    r = client.post(
        "/api/v1/auth/login",
        json={"phone": "+923001234567", "password": "AdminTest123!"},
    )
    assert r.status_code == 200, r.text
    return {"Authorization": "Bearer " + r.json()["access_token"]}


@pytest.fixture()
def investor_headers(client):
    r = client.post(
        "/api/v1/auth/register",
        json={
            "phone": "+923009876543",
            "name": "  احسن Khan  ",
            "password": "Investor123!",
        },
    )
    assert r.status_code == 201, r.text
    h = {"Authorization": "Bearer " + r.json()["access_token"]}
    assert client.post("/api/v1/dev/verify-kyc", headers=h).status_code == 200
    assert (
        client.post(
            "/api/v1/dev/wallet/deposit",
            headers={**h, "Idempotency-Key": "deposit-001"},
            json={"amount": 1000000},
        ).status_code
        == 200
    )
    return h
