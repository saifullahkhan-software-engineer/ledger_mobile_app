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
  roles, verifies KYC and is the only role that can correct a previous date's
  transactions.
- **ADMIN** (manager) — runs only the businesses assigned to them; can add today's records and close the current date, but cannot correct an older one.
- **INVESTOR** — buys shares and receives payouts (no client app yet).

Business dates (Asia/Karachi) close and settle **automatically when they end**
(midnight by default, `DAY_CLOSE_HOUR` to shift it), so no one has to close yesterday
by hand. After that, a super-admin correction rebuilds that date's summary — the payout
already distributed is never rewritten. See `backend/README.md` §7.

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

   Existing installations pick up new columns and indexes with one explicit,
   repeatable command:

   ```bash
   python -m app.manage upgrade-db       # additive schema upgrade (no data changes)
   ```

   Databases created before the custom-icon feature was removed still carry the
   retired `app_icons` / `image_assets` tables; `python -m app.manage
   drop-icon-schema` reports what it would drop and removes it with `--yes`.

   To wipe every record but keep the owner and manager accounts,
   `python -m app.manage clear-data` prints a dry run first; `--yes` deletes.
   See the "Clearing all data" section in [`backend/README.md`](backend/README.md).

2. **Admin app** — open [`admin-android/`](admin-android/README.md) in Android Studio
   and run it. Sign in with the owner credentials from `seed`. Use the owner-only
   **Users** entry in the drawer to create managers and investors.

## Testing

Backend integration/contract tests live in `backend/tests/` and validate the Admin
client's API contract against the live OpenAPI schema:

```bash
cd backend
python -m pytest -q     # 61 passed, 2 PostgreSQL-only skipped (SQLite; 63 passed on PostgreSQL)
```

## Before real-money use

This is a runnable MVP, not a launch-ready financial service. Real OTP delivery,
identity verification (KYC evidence review), Raast/NayaPay/UBL callbacks, migrations,
treasury controls and regulatory review are not implemented. See the "Before any
real-money launch" section in [`backend/README.md`](backend/README.md).
