# Ahsan Traders — Admin / Investor platform

Fractional-investment platform for the Ahsan Traders businesses (Chicken, LPG/Gas,
Broiler/Poultry Farm). An owner (**SUPERADMIN**) runs the businesses through a native
Android **Admin** app; investors buy shares and receive payouts. A FastAPI backend is
the single source of truth for every record, account and settlement.

## What is in this repository

| Path | What it is |
|---|---|
| [`admin-android/`](admin-android/README.md) | Native Kotlin + Jetpack Compose **Admin** app (owner + managers). |
| [`backend/`](backend/README.md) | FastAPI backend: auth, roles, businesses, ledger, batches, wallet, settlements. |

> An **Investor** client is planned but not implemented yet — the backend exposes the
> investor endpoints, and the Admin app deliberately cannot buy shares or fund wallets.

## Roles

- **SUPERADMIN** (owner) — full authority: manages users, assigns businesses, changes
  roles, verifies KYC and configures the mobile screen icons.
- **ADMIN** (manager) — runs only the businesses assigned to them.
- **INVESTOR** — buys shares and receives payouts (no client app yet).

Every role boundary is enforced **server-side** (`Depends(root)` / `Depends(admin)` /
`Depends(investor)` in `backend/app/security.py`), and the apps additionally hide
owner-only screens for non-owner accounts.

## Quick start

1. **Backend** — see [`backend/README.md`](backend/README.md):
   initialize the schema, run the seed command to create the owner (SUPERADMIN)
   account, then start the API:

   ```bash
   cd backend
   cp .env.example .env            # set JWT_SECRET, DATABASE_URL
   python -m app.manage init-db
   python -m app.manage seed
   uvicorn app.main:app --host 0.0.0.0 --port 8000
   ```

   Screen/brand/business icon images are stored as bytes in PostgreSQL
   (`image_assets` table) and served from `/api/v1/images/{id}`. Existing
   installations migrate with two explicit, repeatable commands:

   ```bash
   python -m app.manage upgrade-db       # additive schema upgrade (no data changes)
   python -m app.manage migrate-images   # import legacy uploads/ files into the DB
   ```

2. **Admin app** — open [`admin-android/`](admin-android/README.md) in Android Studio
   and run it. Sign in with the owner credentials from `seed`. Use the owner-only
   **Users** and **Screen icons** entries in the drawer to create managers and
   investors and customize the mobile screen images.

## Testing

Backend integration/contract tests live in `backend/tests/` and validate the Admin
client's API contract against the live OpenAPI schema:

```bash
cd backend
python -m pytest -q     # 41 passed, 1 PostgreSQL-only skipped (SQLite; PostgreSQL in CI)
```

## Before real-money use

This is a runnable MVP, not a launch-ready financial service. Real OTP delivery,
identity verification (KYC evidence review), Raast/NayaPay/UBL callbacks, migrations,
treasury controls and regulatory review are not implemented. See the "Before any
real-money launch" section in [`backend/README.md`](backend/README.md).
