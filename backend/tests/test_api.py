import asyncio
from concurrent.futures import ThreadPoolExecutor
from sqlalchemy import inspect, select, func, text
import pytest
from app.db import Session, engine, sync_engine, ensure_business_icon_column
from app.models import Posting, Ownership, Settlement
from app.services import today

P = "/api/v1"


def post(c, path, h, payload, key="request-0001", status=200):
    r = c.post(P + path, json=payload, headers={**h, "Idempotency-Key": key})
    assert r.status_code == status, r.text
    return r.json()


def daily(
    c, h, kind, amount, quantity=0, key="daily-0001", business="chicken", **extra
):
    return post(
        c,
        "/admin/ledger/daily",
        h,
        {
            "business_id": business,
            "date": str(today()),
            "kind": kind,
            "amount": amount,
            "quantity": str(quantity),
            **extra,
        },
        key,
    )


def test_legacy_business_schema_adds_missing_icon_column(client):
    with sync_engine.begin() as conn:
        conn.execute(text("ALTER TABLE businesses DROP COLUMN icon_url"))
    asyncio.run(ensure_business_icon_column())
    asyncio.run(ensure_business_icon_column())
    assert "icon_url" in {col["name"] for col in inspect(sync_engine).get_columns("businesses")}


def test_daily_settlement_and_idempotency(client, admin_headers, investor_headers):
    post(
        client,
        "/investor/transaction/buy",
        investor_headers,
        {"business_id": "chicken", "shares": 2},
    )
    daily(client, admin_headers, "PURCHASE", 100000, 10)
    sale = daily(client, admin_headers, "SALE", 100000, 5, "sale-0001")
    assert sale["stock"]["stock_cost"] == 50000
    daily(client, admin_headers, "EXPENSE", 10000, key="expense-0001")
    payload = {"day_id": sale["day"]["id"]}
    first = post(client, "/admin/ledger/close", admin_headers, payload)
    assert first["day"]["net_profit"] == 40000
    assert first["settlement"]["distributed"] == 8000
    assert first == post(client, "/admin/ledger/close", admin_headers, payload)
    post(client, "/admin/ledger/close", admin_headers, payload, "other-key", 409)
    r = client.get(P + "/investor/portfolio", headers=investor_headers).json()
    assert r["wallet_balance"] == 988000
    assert r["total_profit_earned"] == 8000
    with Session() as db:
        assert db.scalar(select(func.sum(Posting.amount))) == 0
        assert db.scalar(select(func.count(Settlement.id))) == 1
    post(
        client,
        "/admin/ledger/daily",
        admin_headers,
        {
            "business_id": "chicken",
            "date": str(today()),
            "kind": "EXPENSE",
            "amount": 1,
        },
        "closed-write",
        409,
    )


def test_auth_and_permissions(client, admin_headers, investor_headers):
    assert client.get(P + "/admin/businesses").status_code in (401, 403)
    assert (
        client.get(P + "/admin/businesses", headers=investor_headers).status_code == 403
    )
    h = {
        "Authorization": "Bearer "
        + post(
            client,
            "/auth/login",
            {},
            {"phone": "+923001234568", "password": "AdminTest123!"},
        )["access_token"]
    }
    assert (
        client.get(P + "/admin/stock?business_id=chicken", headers=h).status_code == 403
    )
    assert (
        client.put(
            P + "/admin/businesses/chicken/managers/manager", headers=admin_headers
        ).status_code
        == 200
    )
    assert (
        client.get(P + "/admin/stock?business_id=chicken", headers=h).status_code == 200
    )
    assert client.get(P + "/admin/stock?business_id=lpg", headers=h).status_code == 403
    assert (
        client.get(P + "/me", headers=investor_headers).json()["name"]
        == "  احسن Khan  "
    )
    post(client, "/auth/logout", investor_headers, {})
    assert client.get(P + "/me", headers=investor_headers).status_code == 401


def test_buy_guards(client, admin_headers, investor_headers):
    body = {"business_id": "chicken", "shares": 2}
    first = post(client, "/investor/transaction/buy", investor_headers, body)
    assert first == post(client, "/investor/transaction/buy", investor_headers, body)
    post(
        client,
        "/investor/transaction/buy",
        investor_headers,
        {**body, "shares": 3},
        status=409,
    )
    post(
        client,
        "/investor/transaction/buy",
        investor_headers,
        {**body, "shares": 9},
        "oversell-key",
        409,
    )
    post(
        client,
        "/investor/transaction/buy",
        investor_headers,
        {**body, "shares": -1},
        "negative-key",
        422,
    )
    daily(client, admin_headers, "PURCHASE", 100, 1)
    post(client, "/investor/transaction/buy", investor_headers, body, "cutoff-key", 409)
    post(
        client,
        "/admin/ledger/daily",
        admin_headers,
        {
            "business_id": "chicken",
            "date": str(today()),
            "kind": "SALE",
            "quantity": "2",
            "amount": 100,
        },
        "oversale-key",
        409,
    )


def test_withdrawals_and_refunds(client, admin_headers, investor_headers):
    w = post(
        client,
        "/investor/wallet/withdraw",
        investor_headers,
        {"amount": 100000, "provider": "RAAST", "destination": "PK00-test-account"},
    )
    assert (
        client.get(P + "/investor/portfolio", headers=investor_headers).json()[
            "wallet_balance"
        ]
        == 900000
    )
    post(
        client,
        "/dev/withdrawals/" + w["id"] + "/resolve",
        admin_headers,
        {"status": "FAILED"},
    )
    assert (
        client.get(P + "/investor/portfolio", headers=investor_headers).json()[
            "wallet_balance"
        ]
        == 1000000
    )
    post(
        client,
        "/dev/withdrawals/" + w["id"] + "/resolve",
        admin_headers,
        {"status": "PAID"},
        "second-resolve",
        409,
    )
    post(
        client,
        "/investor/wallet/withdraw",
        investor_headers,
        {"amount": 1000001, "provider": "UBL", "destination": "account-1"},
        "overdraw-key",
        409,
    )


@pytest.mark.parametrize(
    "revenue,expected,profit",
    [(120000, 24000, 4000), (50000, 10000, -10000), (0, 0, -20000)],
)
def test_batch_profit_loss(
    client, admin_headers, investor_headers, revenue, expected, profit
):
    b = post(
        client,
        "/admin/batch/create",
        admin_headers,
        {
            "business_id": "broiler",
            "name": "Batch A",
            "chicks": 100,
            "total_shares": 10,
            "share_price": 10000,
            "initial_cost": 100000,
        },
    )
    post(
        client,
        "/investor/transaction/buy",
        investor_headers,
        {"business_id": "broiler", "batch_id": b["id"], "shares": 2},
    )
    post(client, "/admin/batch/" + b["id"] + "/start", admin_headers, {})
    post(
        client,
        "/investor/transaction/buy",
        investor_headers,
        {"business_id": "broiler", "batch_id": b["id"], "shares": 1},
        "locked-buy",
        409,
    )
    r = client.put(
        P + "/admin/batch/" + b["id"] + "/update",
        json={"date": str(today()), "feed_kg": "10", "deaths": 101},
        headers={**admin_headers, "Idempotency-Key": "mortality-1"},
    )
    assert r.status_code == 422
    result = post(
        client,
        "/admin/batch/" + b["id"] + "/harvest",
        admin_headers,
        {"yield_kg": "100", "price_per_kg": revenue // 100},
    )
    assert result["settlement"]["distributed"] == expected
    portfolio = client.get(P + "/investor/portfolio", headers=investor_headers).json()
    assert portfolio["wallet_balance"] == 980000 + expected
    assert portfolio["active_invested"] == 0
    assert portfolio["total_profit_earned"] == profit
    post(
        client,
        "/admin/batch/" + b["id"] + "/harvest",
        admin_headers,
        {"yield_kg": "100", "price_per_kg": revenue // 100},
        "repeat-harvest",
        409,
    )


def test_lpg_and_reports(client, admin_headers):
    daily(client, admin_headers, "PURCHASE", 50000, 5, business="lpg")
    daily(
        client,
        admin_headers,
        "SALE",
        30000,
        2,
        "sale-retail",
        business="lpg",
        channel="RETAIL",
    )
    daily(
        client,
        admin_headers,
        "SALE",
        12000,
        1,
        "sale-hotel",
        business="lpg",
        channel="COMMERCIAL",
    )
    stock = client.get(P + "/admin/stock?business_id=lpg", headers=admin_headers).json()
    assert str(stock["quantity"]) in ("2", "2.0", "2.000")
    result = client.get(P + "/admin/dashboard", headers=admin_headers)
    assert result.status_code == 200, result.text
    assert result.json()["total_profit"] == 12000


def test_concurrent_share_purchases(client, investor_headers):
    def purchase(i):
        return client.post(
            P + "/investor/transaction/buy",
            json={"business_id": "lpg", "shares": 6},
            headers={**investor_headers, "Idempotency-Key": f"concurrent-{i}"},
        ).status_code

    with ThreadPoolExecutor(max_workers=2) as pool:
        assert sorted(pool.map(purchase, range(2))) == [200, 409]
    with Session() as db:
        assert db.scalar(select(func.sum(Ownership.shares))) == 6


@pytest.mark.skipif(
    engine.dialect.name != "postgresql", reason="PostgreSQL append-only triggers"
)
def test_database_history_immutable(client, investor_headers):
    with pytest.raises(Exception, match="append-only"):
        with engine.begin() as conn:
            conn.execute(text("DELETE FROM postings"))


def test_concurrent_identical_settlement(client, admin_headers, investor_headers):
    post(
        client,
        "/investor/transaction/buy",
        investor_headers,
        {"business_id": "chicken", "shares": 1},
    )
    day = daily(client, admin_headers, "BYPRODUCT", 101, 1)

    def close(_):
        return post(
            client,
            "/admin/ledger/close",
            admin_headers,
            {"day_id": day["day"]["id"]},
            "concurrent-close",
        )

    with ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(close, range(2)))
    assert results[0] == results[1]
    assert results[0]["settlement"]["distributed"] == 10
    assert results[0]["settlement"]["retained"] == 91


def test_daily_loss_no_wallet_debit(client, admin_headers, investor_headers):
    post(
        client,
        "/investor/transaction/buy",
        investor_headers,
        {"business_id": "lpg", "shares": 1},
    )
    d = daily(client, admin_headers, "EXPENSE", 10000, business="lpg")
    result = post(
        client, "/admin/ledger/close", admin_headers, {"day_id": d["day"]["id"]}
    )
    assert result["settlement"]["net_profit"] == -10000
    assert result["settlement"]["distributed"] == 0
    assert (
        client.get(P + "/investor/portfolio", headers=investor_headers).json()[
            "wallet_balance"
        ]
        == 990000
    )


def test_duplicate_batch_log_rolls_back(client, admin_headers):
    b = post(
        client,
        "/admin/batch/create",
        admin_headers,
        {
            "business_id": "broiler",
            "name": "Batch",
            "chicks": 10,
            "total_shares": 10,
            "share_price": 10000,
            "initial_cost": 100,
        },
    )
    post(client, "/admin/batch/" + b["id"] + "/start", admin_headers, {})
    payload = {"date": str(today()), "feed_kg": "1", "deaths": 1, "expense": 10}
    url = P + "/admin/batch/" + b["id"] + "/update"
    assert (
        client.put(
            url,
            json=payload,
            headers={**admin_headers, "Idempotency-Key": "batch-log-01"},
        ).status_code
        == 200
    )
    assert (
        client.put(
            url,
            json=payload,
            headers={**admin_headers, "Idempotency-Key": "batch-log-02"},
        ).status_code
        == 409
    )
    r = client.get(
        P + "/admin/batch/" + b["id"] + "/logs", headers=admin_headers
    ).json()
    assert r["batch"]["deaths"] == 1
    assert r["batch"]["expenses"] == 110
    assert len(r["logs"]) == 1


def test_unverified_investor_and_private_history(client, investor_headers):
    r = post(
        client,
        "/auth/register",
        {},
        {"phone": "+923009876500", "name": "Second user", "password": "Investor123!"},
        status=201,
    )
    h = {"Authorization": "Bearer " + r["access_token"]}
    post(
        client,
        "/investor/transaction/buy",
        h,
        {"business_id": "lpg", "shares": 1},
        status=403,
    )
    assert (
        client.get(P + "/investor/wallet/transactions", headers=h).json()[
            "transactions"
        ]
        == []
    )
    assert (
        client.get(P + "/investor/portfolio", headers=h).json()["wallet_balance"] == 0
    )


def test_password_change_revokes_old_tokens(client, investor_headers):
    post(
        client,
        "/auth/change-password",
        investor_headers,
        {"old_password": "Investor123!", "new_password": "ChangedPassword123!"},
    )
    assert client.get(P + "/me", headers=investor_headers).status_code == 401
    post(
        client,
        "/auth/login",
        {},
        {"phone": "+923009876543", "password": "Investor123!"},
        status=401,
    )
    post(
        client,
        "/auth/login",
        {},
        {"phone": "+923009876543", "password": "ChangedPassword123!"},
    )


def test_concurrent_withdrawal_cannot_overdraw(client, investor_headers):
    def withdraw(i):
        return client.post(
            P + "/investor/wallet/withdraw",
            json={"amount": 600000, "provider": "UBL", "destination": "test-account"},
            headers={**investor_headers, "Idempotency-Key": f"concurrent-withdraw-{i}"},
        ).status_code

    with ThreadPoolExecutor(max_workers=2) as pool:
        assert sorted(pool.map(withdraw, range(2))) == [200, 409]
    assert (
        client.get(P + "/investor/portfolio", headers=investor_headers).json()[
            "wallet_balance"
        ]
        == 400000
    )


def test_mock_routes_absent_in_production():
    import os, subprocess, sys

    env = {**os.environ, "APP_ENV": "production", "ENABLE_MOCK_PAYMENTS": "false"}
    result = subprocess.run(
        [
            sys.executable,
            "-c",
            "from app.main import app; assert not any('/dev/' in r.path for r in app.routes)",
        ],
        env=env,
        capture_output=True,
        text=True,
    )
    assert result.returncode == 0, result.stderr


def test_impossible_quantities_and_money(client, admin_headers, investor_headers):
    for amount in (-1, 0, 1.5, "100"):
        post(
            client,
            "/dev/wallet/deposit",
            investor_headers,
            {"amount": amount},
            status=422,
        )
    post(
        client,
        "/admin/ledger/daily",
        admin_headers,
        {
            "business_id": "lpg",
            "date": str(today()),
            "kind": "PURCHASE",
            "quantity": "1.5",
            "amount": 100,
        },
        status=422,
    )
    assert client.get("/health").json()["status"] == "ok"


def test_admin_batch_archive_and_day_detail(client, admin_headers, investor_headers):
    batch = post(client, '/admin/batch/create', admin_headers, {
        'business_id': 'broiler', 'name': 'Archived batch', 'chicks': 10,
        'total_shares': 10, 'share_price': 10000, 'initial_cost': 10000,
    })
    post(client, '/admin/batch/' + batch['id'] + '/start', admin_headers, {})
    post(client, '/admin/batch/' + batch['id'] + '/harvest', admin_headers, {
        'yield_kg': '10', 'price_per_kg': 1500,
    })
    result = client.get(P + '/admin/batches?business_id=broiler&limit=1', headers=admin_headers)
    assert result.status_code == 200, result.text
    assert result.json()[0]['status'] == 'HARVESTED'
    assert client.get(P + '/admin/batches?business_id=broiler&offset=1', headers=admin_headers).json() == []
    assert client.get(P + '/admin/batches?business_id=broiler', headers=investor_headers).status_code == 403
    day = daily(client, admin_headers, 'PURCHASE', 10000, 10)
    day_id = day['day']['id']
    assert client.get(P + '/admin/ledger/' + day_id, headers=admin_headers).json()['status'] == 'OPEN'
    post(client, '/admin/ledger/close', admin_headers, {'day_id': day_id})
    assert client.get(P + '/admin/ledger/' + day_id, headers=admin_headers).json()['status'] == 'CLOSED'
    assert client.get(P + '/admin/ledger/' + day_id, headers=investor_headers).status_code == 403
    manager = post(client, '/auth/login', {}, {'phone': '+923001234568', 'password': 'AdminTest123!'})
    headers = {'Authorization': 'Bearer ' + manager['access_token']}
    assert client.get(P + '/admin/batches?business_id=broiler', headers=headers).status_code == 403
    assert client.get(P + '/admin/ledger/' + day_id, headers=headers).status_code == 403


def test_android_api_paths_match_backend_contract():
    """Check the checked-in Retrofit method, URL, query and request-header contract."""
    import re
    from pathlib import Path
    from app.main import app

    interface = Path(__file__).resolve().parents[2] / 'admin-android/app/src/main/java/com/ahsantraders/admin/data/AdminApi.kt'
    if not interface.exists():
        pytest.skip('Android client is not present in this checkout')
    spec = app.openapi()
    normalize = lambda path: re.sub(r'\{[^}]+\}', '{}', '/' + path.lstrip('/'))
    endpoints = {(verb.upper(), normalize(path)): operation for path, methods in spec['paths'].items() for verb, operation in methods.items()}
    methods = re.findall(r'@(GET|POST|PUT|PATCH|DELETE)\("([^"]+)"\)\s+suspend fun ([^\n]+)', interface.read_text())
    assert len(methods) >= 20
    for verb, path, signature in methods:
        operation = endpoints[(verb, normalize(path))]
        params = operation.get('parameters', [])
        query_names = {p['name'] for p in params if p['in'] == 'query'}
        required_queries = {p['name'] for p in params if p['in'] == 'query' and p.get('required')}
        client_queries = set(re.findall(r'@Query\("([^"]+)"\)', signature))
        assert client_queries <= query_names, path
        assert required_queries <= client_queries, path
        if any(p['name'].lower() == 'idempotency-key' and p.get('required') for p in params):
            assert '@Header("Idempotency-Key")' in signature, path


def test_superadmin_users_and_mobile_icons(client, admin_headers, investor_headers):
    # 1. Super admin can see all users
    r = client.get("/api/v1/admin/users", headers=admin_headers)
    assert r.status_code == 200, r.text
    users = r.json()
    assert len(users) >= 3  # Admin, Manager, Investor
    roles = {u["role"] for u in users}
    assert "SUPERADMIN" in roles
    assert "ADMIN" in roles
    assert "INVESTOR" in roles

    # Filter by role
    r_inv = client.get("/api/v1/admin/users?role=INVESTOR", headers=admin_headers)
    assert r_inv.status_code == 200
    for u in r_inv.json():
        assert u["role"] == "INVESTOR"

    # Detail of a specific user
    user_id = users[0]["id"]
    r_detail = client.get(f"/api/v1/admin/users/{user_id}", headers=admin_headers)
    assert r_detail.status_code == 200
    assert r_detail.json()["id"] == user_id

    # Normal investor/admin cannot access superadmin users endpoint
    r_forbidden = client.get("/api/v1/admin/users", headers=investor_headers)
    assert r_forbidden.status_code == 403

    # 2. Superadmin adds / configures image for mobile screen icon
    # Base64 icon upload
    sample_base64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
    r_upload = client.post(
        "/api/v1/admin/icons/upload-base64",
        headers=admin_headers,
        json={"filename": "chicken_icon.png", "data": sample_base64},
    )
    assert r_upload.status_code == 200, r_upload.text
    uploaded_url = r_upload.json()["image_url"]
    assert uploaded_url.startswith("/uploads/icons/")

    # Superadmin sets mobile screen icon
    r_icon = client.put(
        "/api/v1/admin/icons/business_chicken",
        headers=admin_headers,
        json={
            "label": "Chicken Shop Mobile Icon",
            "screen": "dashboard",
            "image_url": uploaded_url,
            "fallback_icon": "fastfood",
        },
    )
    assert r_icon.status_code == 200, r_icon.text
    assert r_icon.json()["key"] == "business_chicken"
    assert r_icon.json()["image_url"] == uploaded_url

    # Superadmin updates business icon
    r_biz_icon = client.put(
        "/api/v1/admin/businesses/chicken/icon",
        headers=admin_headers,
        json={"icon_url": uploaded_url},
    )
    assert r_biz_icon.status_code == 200, r_biz_icon.text
    assert r_biz_icon.json()["icon_url"] == uploaded_url

    # Mobile endpoint fetches screen icons
    r_mobile = client.get("/api/v1/mobile/icons")
    assert r_mobile.status_code == 200
    mobile_data = r_mobile.json()
    assert "business_chicken" in mobile_data["icons"]
    assert mobile_data["icons"]["business_chicken"]["image_url"] == uploaded_url
    assert mobile_data["business_icons"]["chicken"] == uploaded_url

    # Reverting to the default icon accepts an empty image_url
    r_remove = client.put(
        "/api/v1/admin/icons/business_chicken",
        headers=admin_headers,
        json={"label": "Chicken Shop Mobile Icon", "screen": "dashboard", "image_url": ""},
    )
    assert r_remove.status_code == 200, r_remove.text
    assert r_remove.json()["image_url"] == ""

