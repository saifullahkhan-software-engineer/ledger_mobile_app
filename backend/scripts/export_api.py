"""Regenerate OpenAPI and the ordered Postman smoke workflow from the app."""

import json
from pathlib import Path

from app.main import app

root = Path(__file__).resolve().parents[1]
(root / "openapi.json").write_text(json.dumps(app.openapi(), indent=2) + "\n")
items = []


def request(
    name, method, path, body=None, role="admin", expect=200, save=None, checks=None
):
    headers = [
        {"key": "Content-Type", "value": "application/json"},
        {"key": "Idempotency-Key", "value": "{{run_id}}-" + str(len(items))},
    ]
    if role:
        headers.append(
            {"key": "Authorization", "value": "Bearer {{" + role + "_token}}"}
        )
    req = {"method": method, "header": headers, "url": "{{base_url}}" + path}
    if body is not None:
        req["body"] = {
            "mode": "raw",
            "raw": json.dumps(body),
            "options": {"raw": {"language": "json"}},
        }
    tests = [f"pm.test('HTTP {expect}', () => pm.response.to.have.status({expect}));"]
    if save:
        tests.extend(
            f"pm.collectionVariables.set('{key}', pm.response.json(){access});"
            for key, access in save.items()
        )
    tests.extend(checks or [])
    items.append(
        {
            "name": name,
            "request": req,
            "event": [
                {"listen": "test", "script": {"type": "text/javascript", "exec": tests}}
            ],
        }
    )


request(
    "Owner login",
    "POST",
    "/api/v1/auth/login",
    {"phone": "{{admin_phone}}", "password": "{{admin_password}}"},
    None,
    save={"admin_token": ".access_token"},
)
request(
    "Register investor",
    "POST",
    "/api/v1/auth/register",
    {
        "phone": "{{investor_phone}}",
        "name": "احسن Test Investor",
        "password": "LocalInvestor123!",
    },
    None,
    201,
    {"investor_token": ".access_token"},
)
request("Development KYC only", "POST", "/api/v1/dev/verify-kyc", role="investor")
request(
    "Development deposit only",
    "POST",
    "/api/v1/dev/wallet/deposit",
    {"amount": 1000000},
    "investor",
)
request(
    "Create isolated chicken shop",
    "POST",
    "/api/v1/admin/businesses",
    {
        "name": "Postman Chicken {{run_id}}",
        "type": "CHICKEN",
        "total_shares": 10,
        "share_price": 10000,
    },
    expect=201,
    save={"chicken_id": ".id"},
)
request(
    "Buy two chicken shares",
    "POST",
    "/api/v1/investor/transaction/buy",
    {"business_id": "{{chicken_id}}", "shares": 2},
    "investor",
)
request(
    "Add supplier",
    "POST",
    "/api/v1/admin/suppliers",
    {"business_id": "{{chicken_id}}", "name": "Supplier A", "phone": "+923001111111"},
    expect=201,
    save={"supplier_id": ".id"},
)
request(
    "Purchase ten kg",
    "POST",
    "/api/v1/admin/ledger/daily",
    {
        "business_id": "{{chicken_id}}",
        "date": "{{today}}",
        "kind": "PURCHASE",
        "quantity": "10",
        "amount": 100000,
        "supplier_id": "{{supplier_id}}",
    },
    save={"day_id": ".day.id"},
)
request(
    "Sell five kg",
    "POST",
    "/api/v1/admin/ledger/daily",
    {
        "business_id": "{{chicken_id}}",
        "date": "{{today}}",
        "kind": "SALE",
        "quantity": "5",
        "amount": 100000,
    },
)
request(
    "Add expense",
    "POST",
    "/api/v1/admin/ledger/daily",
    {
        "business_id": "{{chicken_id}}",
        "date": "{{today}}",
        "kind": "EXPENSE",
        "amount": 10000,
    },
)
request(
    "Close and distribute profit",
    "POST",
    "/api/v1/admin/ledger/close",
    {"day_id": "{{day_id}}"},
    checks=[
        "pm.test('Profit and investor payout', () => { const r=pm.response.json(); pm.expect(r.day.net_profit).to.eql(40000); pm.expect(r.settlement.distributed).to.eql(8000); });"
    ],
)
request(
    "Prevent second close",
    "POST",
    "/api/v1/admin/ledger/close",
    {"day_id": "{{day_id}}"},
    expect=409,
)
request("Chicken summary", "GET", "/api/v1/admin/businesses/{{chicken_id}}/summary")
request("Supplier bills", "GET", "/api/v1/admin/suppliers/{{supplier_id}}/bills")
request("Dashboard", "GET", "/api/v1/admin/dashboard")
request(
    "Date range reports", "GET", "/api/v1/admin/reports?start={{today}}&end={{today}}"
)
request(
    "Create broiler business",
    "POST",
    "/api/v1/admin/businesses",
    {
        "name": "Postman Broiler {{run_id}}",
        "type": "BROILER",
        "total_shares": 10,
        "share_price": 10000,
    },
    expect=201,
    save={"broiler_id": ".id"},
)
request(
    "Create batch",
    "POST",
    "/api/v1/admin/batch/create",
    {
        "business_id": "{{broiler_id}}",
        "name": "Batch A",
        "chicks": 100,
        "total_shares": 10,
        "share_price": 10000,
        "initial_cost": 100000,
    },
    save={"batch_id": ".id"},
)
request(
    "Buy batch shares",
    "POST",
    "/api/v1/investor/transaction/buy",
    {"business_id": "{{broiler_id}}", "batch_id": "{{batch_id}}", "shares": 2},
    "investor",
)
request("Start batch", "POST", "/api/v1/admin/batch/{{batch_id}}/start")
request(
    "Daily batch record",
    "PUT",
    "/api/v1/admin/batch/{{batch_id}}/update",
    {"date": "{{today}}", "feed_kg": "10", "deaths": 1, "expense": 10000},
)
request(
    "Harvest batch",
    "POST",
    "/api/v1/admin/batch/{{batch_id}}/harvest",
    {"yield_kg": "100", "price_per_kg": 1500},
    checks=[
        "pm.test('Batch principal and profit payout', () => pm.expect(pm.response.json().settlement.distributed).to.eql(28000));"
    ],
)
request(
    "Portfolio",
    "GET",
    "/api/v1/investor/portfolio",
    role="investor",
    checks=[
        "pm.test('Wallet reconciles', () => { const r=pm.response.json(); pm.expect(r.wallet_balance).to.eql(996000); pm.expect(r.total_profit_earned).to.eql(16000); });"
    ],
)
request(
    "Request mock withdrawal",
    "POST",
    "/api/v1/investor/wallet/withdraw",
    {"amount": 10000, "provider": "RAAST", "destination": "test-account-only"},
    "investor",
    save={"withdrawal_id": ".id"},
)
request(
    "Simulate withdrawal failure",
    "POST",
    "/api/v1/dev/withdrawals/{{withdrawal_id}}/resolve",
    {"status": "FAILED"},
)
request(
    "Wallet history",
    "GET",
    "/api/v1/investor/wallet/transactions",
    role="investor",
    checks=[
        "pm.test('Failed withdrawal refunded', () => pm.expect(pm.response.json().balance).to.eql(996000));"
    ],
)
request(
    "Investor cannot access admin",
    "GET",
    "/api/v1/admin/dashboard",
    role="investor",
    expect=403,
)
request("Marketplace", "GET", "/api/v1/investor/marketplace", role="investor")

variables = {
    "base_url": "http://localhost:8000",
    "admin_phone": "+923001234567",
    "admin_password": "",
    "admin_token": "",
    "investor_token": "",
    "run_id": "",
    "investor_phone": "",
    "today": "",
}
collection = {
    "info": {
        "name": "Ahsan Traders — Backend Smoke Test",
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
        "description": "Development ONLY. Creates isolated businesses and a new investor. Set admin_phone/admin_password, then run in order. All amounts are paisa. Full endpoint reference is openapi.json.",
    },
    "variable": [{"key": k, "value": v} for k, v in variables.items()],
    "event": [
        {
            "listen": "prerequest",
            "script": {
                "type": "text/javascript",
                "exec": [
                    "if (pm.info.requestName === 'Owner login') {",
                    "pm.collectionVariables.set('run_id', Date.now().toString());",
                    "pm.collectionVariables.set('investor_phone', '+923' + Date.now().toString().slice(-9));",
                    "pm.collectionVariables.set('today', new Date(Date.now() + 5*60*60*1000).toISOString().slice(0,10));",
                    "}",
                ],
            },
        }
    ],
    "item": items,
}
(root / "postman" / "Ahsan-Traders.postman_collection.json").write_text(
    json.dumps(collection, indent=2, ensure_ascii=False) + "\n"
)
print("Exported OpenAPI and Postman collection")
