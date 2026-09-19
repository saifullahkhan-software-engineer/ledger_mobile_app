# Ahsan Traders — backend

FastAPI backend for the Admin/Investor PRD. **The Kotlin Admin client lives in [`../admin-android/`](../admin-android/README.md); an Investor client is not implemented.**

## Delivery status

Implemented: password authentication, roles and assigned-business authorization, chicken/LPG inventory and operational records, suppliers and purchase history, daily closure, broiler funding/start/log/harvest lifecycle, share purchases, append-only financial history, atomic distributions, wallet reservations/refunds, marketplace, portfolio, reports, profile/language, OpenAPI, Postman, Docker and backend CI.

**This is a runnable backend MVP, not a launch-ready financial service.** Real OTP, identity verification, Raast/NayaPay/UBL transfers and callbacks are not implemented. The development-only KYC/deposit/withdrawal simulation is deliberately labelled and disabled outside development. Password login uses a phone number as the username; it does **not** verify ownership of that phone. Do not use this build to accept real investor money.

### Verification performed in the development sandbox

- SQLite API/service integration tests, including Admin client contract coverage and the database-image flows (upload, exact-byte serving, MIME validation, malformed/oversized rejection, authorization, assignment/replacement/reset, shared-asset safety, list endpoints never loading image bytes, `migrate-images` legacy import/rerun/interruption paths and the `upgrade-db`/`migrate-images` CLI on old schemas): **41 passed, 1 skipped**.
- Ordered Postman collection executed with Newman: **28 requests, 32 assertions passed**.
- Tests include concurrent share purchases, concurrent withdrawals, concurrent duplicate settlements, rounding, duplicate-key conflicts, rollback, authorization, password revocation, stock valuation, and batch profit/loss.
- The skipped test checks PostgreSQL append-only triggers. PostgreSQL/Docker execution was **not verified in this sandbox**: neither was installed, and system package installation failed. CI is configured to run the suite against PostgreSQL 16. Its remote result has not been observed.
- Test dependencies currently emit third-party deprecation warnings; tests pass.

## 1. Recommended local run: Docker + PostgreSQL

Install **Docker Desktop** (including Docker Compose), and start it. Use a terminal at the repository root:

```bash
cd backend
cp .env.example .env
```

On PowerShell use `Copy-Item .env.example .env` instead of `cp` if needed.

Generate a secret with Python (or another secure random generator):

```bash
python -c "import secrets; print(secrets.token_urlsafe(48))"
```

Put that output in `.env` as `JWT_SECRET`. Keep `APP_ENV=development` and `ENABLE_MOCK_PAYMENTS=true` **only for local testing**. Never commit `.env`.

```bash
# Start the database and build the API image.
docker compose up -d db
docker compose build api

# Create version 1 tables and append-only PostgreSQL triggers.
docker compose run --rm api python -m app.manage init-db

# Interactive: enter YOUR owner phone and password (10+ characters).
docker compose run --rm api python -m app.manage seed

# Start the API.
docker compose up -d api
docker compose logs -f api
```

The seed command creates Ahsan Khan as `SUPERADMIN` and three sample businesses, each initially configured with 1,000 shares at Rs 100/share. It does not create investors, wallet funds, or sales. Running seed again does not overwrite an existing password.

Open:

- **Swagger / interactive API:** http://localhost:8000/docs
- **ReDoc:** http://localhost:8000/redoc
- **Health:** http://localhost:8000/health
- **Live OpenAPI:** http://localhost:8000/openapi.json

The database persists in the Compose `pgdata` volume. `docker compose down` stops services without deleting your data. **`docker compose down -v` destroys the local database.**

The supplied PostgreSQL password and exposed ports are local-development settings, not production configuration. Only the database port is bound to loopback; protect the API with your machine's firewall.

## 2. Run Python locally (without putting the API in Docker)

Requires Python **3.11+**. From `backend/`:

```bash
python -m venv .venv
# macOS/Linux:
source .venv/bin/activate
# Windows PowerShell instead:
# .venv\Scripts\Activate.ps1

pip install -r requirements.txt
```

Copy/edit `.env` as above. To use PostgreSQL locally, start it with `docker compose up -d db` and retain the example `DATABASE_URL`. To try the API **without any Docker installation**, change that line to:

```dotenv
DATABASE_URL=sqlite:///./ahsan.db
```

SQLite is a convenience for development/testing, not the target deployment database. PostgreSQL-specific append-only triggers do not run on SQLite.

```bash
python -m app.manage init-db
python -m app.manage seed
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

Run commands from `backend/`, not the repository root. The API does not auto-create tables on startup. `init-db` is for the initial schema; it **does not migrate existing tables** after future schema changes.

### Upgrading an existing database (screen icons + database image storage)

Icon and business images are now stored as **binary data in the
`image_assets` table** instead of files under `backend/uploads/`. Two
explicit, repeatable operator commands perform the upgrade; `create_all` /
`init-db` never alter existing tables, and restarting Uvicorn alone does not
update the schema.

If `/api/v1/admin/icons` or saving an icon returns **500** with
`relation "app_icons" does not exist`, `no such column: app_icons.asset_id`
or similar, the database predates this release.

**1. Back up the database, stop the API (Ctrl+C), and apply the schema
upgrade.** With your virtual environment activated, run these commands
**from `backend/`** in Windows PowerShell (the same commands work in
macOS/Linux shells):

```powershell
cd backend
.\.venv\Scripts\Activate.ps1   # skip if already active
python -m app.manage upgrade-db
```

`upgrade-db` creates `image_assets` and `app_icons` if absent and adds
`businesses.icon_url`, `businesses.icon_asset_id` and `app_icons.asset_id`
if absent. **No data is altered or deleted**, and it is safe to rerun:
existing accounts, businesses, financial history and icon settings are
preserved. It uses the same `DATABASE_URL` / `backend/.env` as the API — an
exported `DATABASE_URL` (PowerShell: `$env:DATABASE_URL="…"`) overrides
`.env`, so ensure it points at the affected database. Run one upgrade
process at a time, with database schema-owner permissions. This is a
targeted additive upgrade, not a general migration framework. For an empty
database, use `python -m app.manage init-db` instead.

**2. Import the legacy upload files into the database:**

```powershell
python -m app.manage migrate-images
```

This data migration reads local files referenced by `app_icons.image_url`
and `businesses.icon_url` and stores their bytes in `image_assets`:

- Relative `/uploads/…` paths and legacy absolute URLs whose **path** starts
  with `/uploads/` (e.g. `http://192.168.1.20:8000/uploads/icons/x.png` or
  `http://localhost:8000/uploads/…` left over from a LAN setup) are read
  from the local uploads directory. **No HTTP request is ever made** —
  absolute upload URLs are treated purely as local file references.
- Other external URLs (`https://cdn.example.com/…`) are **preserved
  unchanged** as legacy values. Copying third-party images into the database
  is an explicit policy decision that this migration does not make for you.
- Traversal attempts (`/uploads/../…`, back-slashes, paths escaping the
  uploads root) are rejected and reported as `unsafe`, never followed.
- Missing files, oversized files and files that fail content validation
  (including legacy SVGs, which are no longer accepted) are reported under
  `missing`/`unsupported` and their **existing references are kept**, so
  nothing silently breaks.
- Successfully imported rows get their `image_url`/`icon_url` rewritten to
  the new serving URL (`/api/v1/images/{id}`) and their asset foreign key
  set. Identical file contents produce **one shared asset** (SHA-256
  dedupe), so two icons using the same file keep sharing one copy.

The command prints a JSON report (`imported`, `reused`, `already_migrated`,
`external`, `missing`, `unsupported`, `unsafe`) plus a summary line; treat
any `missing`/`unsupported`/`unsafe` entries as action items.

**Interruption and reruns:** each reference is imported in its own small
transaction, so a interrupted run (Ctrl+C, crash, network loss to the
database) leaves completed references migrated and the rest untouched — the
old files are still in place, so nothing is lost. Just rerun the command:
already-migrated rows are skipped (their URLs no longer point at
`/uploads/`), and byte-identical content is matched by SHA-256 and reused,
so **rerunning never creates duplicate assets**.

**3. Start the API again:**

```powershell
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

For Docker Compose, use:

```bash
docker compose stop api
docker compose build api
docker compose run --rm api python -m app.manage upgrade-db
docker compose run --rm api python -m app.manage migrate-images
docker compose up -d api
```

The migration **never deletes the old files**. Once you have verified that
every screen icon/brand logo/business icon renders correctly (check the
Screen icons page in the admin app and `GET /api/v1/mobile/icons`), the
leftover files under `backend/uploads/` are safe to remove manually; the
`/uploads` static mount can stay as long as any preserved external refs or
unmigrated files need it.

**Backups:** because image bytes now live in the database, your normal
PostgreSQL backup (`pg_dump` / volume snapshots) automatically includes all
previously migrated and newly uploaded icons — always take a database backup
**before** deleting old files from `backend/uploads/`, and size future
backups for the additional image data (≤ 2 MB per upload).

`Enable-AppBackgroundTaskDiagnosticLog` is unrelated to FastAPI/PostgreSQL
and is not required to run this app. Its registry-access error does not
cause any of the errors above; you can omit that command.

## 3. Test using Swagger (no coding needed)

1. Open `/docs` and execute `POST /api/v1/auth/login` with the owner phone/password entered during seed.
2. Copy `access_token`. Click **Authorize** and paste the token only (Swagger adds `Bearer`).
3. Call `GET /api/v1/admin/businesses`; copy a chicken business ID.
4. Register an investor using `/auth/register`. Save the returned investor token separately.
5. Authorize as that investor. Execute `/dev/verify-kyc`, then `/dev/wallet/deposit` with `{"amount":1000000}` (Rs 10,000).
6. Buy shares using `/investor/transaction/buy`. Do this **before recording the first operation of the day**.
7. Authorize as the owner again. Use `/admin/ledger/daily` to record purchases, sales and expenses. Copy the returned `day.id`.
8. Execute `/admin/ledger/close` with that `day_id`.
9. Switch to the investor token; check `/investor/portfolio` and `/investor/wallet/transactions`.

Every financial/operational command with an `Idempotency-Key` header requires a unique key of 8–100 characters, e.g. `chicken-purchase-0001`. **Retry the same command with the same key and identical body** after a timeout. Use a new key for a genuinely new command. Reusing a key with different input returns `409`.

### Example daily records

Replace `BUSINESS_UUID` and `YYYY-MM-DD`. Use the **current date in Asia/Karachi**, not a date copied from this document.

```json
{
  "business_id": "BUSINESS_UUID",
  "date": "YYYY-MM-DD",
  "kind": "PURCHASE",
  "quantity": "10",
  "amount": 100000,
  "note": "10 kg purchased for Rs 1,000"
}
```

Then a `SALE` of `quantity: "5"`, `amount: 100000` and an `EXPENSE` of `amount: 10000` (omit quantity or set it to `"0"`). Results:

- Remaining inventory: **5 kg**, carrying cost **Rs 500**.
- Revenue: **Rs 1,000**; cost of sales **Rs 500**; expenses **Rs 100**.
- Net profit: **Rs 400**.
- An investor owning 20% receives **Rs 80**. With the seed business's 1,000 shares, 20% means 200 shares, not two. The Postman workflow creates its own 10-share business and buys two.

## 4. Run the supplied Postman collection

Import `postman/Ahsan-Traders.postman_collection.json` into Postman. Under collection variables set:

- `base_url`: `http://localhost:8000`
- `admin_phone`: your seeded owner phone
- `admin_password`: your seeded owner password (keep local, do not sync/export secrets)

Use **Run collection** to run all requests **in order**. The scripts create a new investor and isolated test businesses, save IDs/tokens, exercise daily and batch payouts, and verify refund/authorization behavior. The collection requires development mode and mock payments enabled. It changes the database and should only run on a development instance.

Optional CLI with Node.js installed:

```bash
npx --yes newman run postman/Ahsan-Traders.postman_collection.json \
  --env-var "admin_phone=YOUR_OWNER_PHONE" \
  --env-var "admin_password=YOUR_LOCAL_TEST_PASSWORD" \
  --bail
```

Use the Postman UI rather than CLI arguments if you do not want a test password in shell history/process arguments. On PowerShell, put the command on one line.

The collection covers an end-to-end workflow, not every endpoint. Import `openapi.json` separately for the complete endpoint reference. To regenerate both exports after code changes:

```bash
python -m scripts.export_api
```

## 5. Run automated tests

From `backend/`, with dependencies installed:

```bash
python -m pytest -q
```

By default tests use a separate `test.db` SQLite file. They do not load the application's `.env` database URL over that default. **An already-exported `DATABASE_URL` takes precedence. Tests DROP and recreate application tables; use a dedicated test database only.** A safety guard rejects database names that do not contain `test`, but that is not a substitute for checking your configuration.

To test PostgreSQL using the Compose database:

```bash
docker compose exec db createdb -U ahsan ahsan_test
# macOS/Linux:
DATABASE_URL=postgresql+psycopg://ahsan:localdev@localhost:5432/ahsan_test python -m pytest -q
```

PowerShell:

```powershell
$env:DATABASE_URL="postgresql+psycopg://ahsan:localdev@localhost:5432/ahsan_test"
python -m pytest -q
Remove-Item Env:DATABASE_URL
```

PostgreSQL should run the additional trigger test instead of skipping it. The GitHub workflow `.github/workflows/backend.yml` runs PostgreSQL tests and builds the API image. The separate `admin-android.yml` workflow defines a native Admin debug APK build; see `admin-android/README.md` for its unverified build status.

## 6. Frontend image → API mapping

The supplied image is an **Admin UI reference**, not an investor design or a full Figma specification. Its example financial numbers are inconsistent across dashboard and reports, so the API computes real aggregates instead of reproducing those placeholders.

| Screen/action in image | Backend support |
|---|---|
| App icon / splash / logo | Client assets; no API needed |
| Dashboard totals / sector cards | `GET /admin/dashboard` |
| Side menu business access | `GET /admin/businesses` returns assigned businesses |
| Chicken/LPG/broiler summary | `GET /admin/businesses/{id}/summary` |
| Add sale / purchase / expense | `POST /admin/ledger/daily` with a typed operation |
| Chicken Pota-Kaliji | `BYPRODUCT` sales, separate quantity and revenue |
| LPG retail / shopkeeper sales | `SALE` with `RETAIL` or `COMMERCIAL` channel |
| Close Day (PRD, not pictured) | `POST /admin/ledger/close` |
| Broiler Add Record | `PUT /admin/batch/{id}/update` |
| Broiler start / harvest | `POST /admin/batch/{id}/start`, `.../harvest` |
| Stock | `GET /admin/stock?business_id=...` |
| Supplier bills | Supplier directory and recorded purchase history; not a full accounts-payable system |
| Expenses list | `GET /admin/expenses` for daily businesses; broiler expenses are in batch logs |
| Daily / weekly / monthly reports | `GET /admin/reports?start=...&end=...` |
| My profile / language | `GET/PATCH /me`; English and Urdu values supported |
| Password / logout | `/auth/change-password`, `/auth/logout` |
| Add manager (ADMIN) / assign business | `POST /admin/managers`, `PUT/DELETE /admin/businesses/{id}/managers/{uid}` (owner only) |
| See / search users, role, KYC | `GET /admin/users`, `PUT /admin/users/{id}/role`, `POST /admin/users/{id}/verify-kyc` (owner only) |
| Notifications / Help / About | Client static content; push notification delivery is not implemented |
| LPG Customers shortcut | Customer-account/receivables management is not specified in the investment PRD and is not implemented here |
| Broiler expected weight | Not estimated: no biological growth model or individual weight logging was specified |

All paths in the table except `/health` are prefixed with `/api/v1`. Text is Unicode, preserved without trimming/transliteration. The Android apps remain responsible for RTL layout and localized labels. Error responses currently use English messages, not localized error codes.

## 7. Financial rules — explicit MVP assumptions requiring owner approval

1. **Units:** All monetary inputs/outputs are integer **paisa**. Rs 100 = `10000`. Never send floating-point rupees. Weights/feed are decimal kg with up to three places. LPG quantities are whole cylinders. `price_per_kg` is paisa per kg.
2. **Inventory:** Weighted-average cost of goods sold, carrying unsold stock forward. Last-unit sale consumes all remaining inventory cost so rounding does not strand paisa. Purchases must be recorded before sales; negative stock is rejected. Byproduct sales do not subtract primary chicken weight to avoid double counting; use the weight of primary sold stock in `SALE`.
3. **Day boundary:** Asia/Karachi. New operations must be dated today. An older open day must be closed before new-day operations or share purchases. Closed records are not editable. Backdated entry/corrections/reversals are not exposed in this MVP.
4. **Ownership cutoff:** Running-business purchases are permitted before that day's first operation. Once operations begin, purchases wait until the next business date and all older days are closed. This avoids buying a known day's profit. No resale/redemption/dilution is implemented.
5. **Denominator:** Daily payouts use the business's **total issued offering shares**, not only sold shares. Unsold equity is economically retained by the business operator. This is an explicit interpretation of the PRD's ambiguous "active shares" wording; confirm it before launch. There is no separate management fee.
6. **Daily losses:** No investor wallet debit and no negative payout. Loss is recorded; **no loss carry-forward** is implemented. A later positive day can distribute profit. This policy needs business/legal approval.
7. **Rounding:** Each payout is floored to paisa. Unsold equity's entitlement and rounding remainders are recorded as `retained`. Investor payouts plus retained equal the nonnegative distribution pool.
8. **Batch lifecycle:** `FUNDING → ACTIVE → HARVESTED`. Investments only while FUNDING, with fixed share price. Starting locks ownership. One feed/mortality log per date. Mortality cannot exceed chicks remaining. Harvest may happen early/late; the PRD's 30–50 days is a target, not a hard gate.
9. **Batch pool:** `max(0, total_shares × share_price + sale_revenue − all_recorded_costs)`. The unsold capital is assumed to be operator-funded. Record initial chick costs, subsequent feed/other expenses and final extra costs **once**, not again at harvest. Principal is repaid only through the harvest settlement, not again through share redemption. Investor losses are limited to invested principal; excess loss belongs to the operator.
10. **Settlement timing:** Close/harvest runs settlement immediately in the **same database transaction**, not a CRON. A failed operation rolls back all changes. Repeating the same idempotency key returns the saved response; attempting another settlement on a closed entity conflicts. A queue/outbox is a future scaling step, not a running feature.
11. **Concurrency:** A single PostgreSQL row lock serializes API database transactions across workers; SQLite uses `BEGIN IMMEDIATE`. This simple MVP trades throughput for correctness. Replace it with consistently ordered per-business/wallet locks only after load/concurrency testing. Do not remove the lock without redesigning settlement and purchase atomicity.
12. **Wallet accounting:** No editable `wallet_balance` field. Balance is derived from signed postings. Each journal balances to zero against business/external/withdrawal-hold accounts. Operational cash postings reflect **admin-entered** activity, not bank-confirmed funds. A pending withdrawal reserves the amount; failure refunds it once; success clears its hold.
13. **Funding limitation:** Business counterpart accounts can be negative when the operator supplies off-platform capital. There is no bank-liquidity reconciliation or solvency check. A posted dividend is an internal entitlement, not proof that external cash is available to withdraw.
14. **Valuation:** Portfolio shows acquisition cost and realized profit/loss, not a live market valuation. ROI history is actual recorded P&L divided by offering capital, not a forecast or guaranteed yield. Open daily reports are provisional; broiler revenue/profit is recognized only when harvested, not smoothed into daily sales.

### Icon and brand-image storage (database-backed)

Uploaded screen icons, the brand/header logo and business icons are stored as
**binary image bytes in the `image_assets` table** (PostgreSQL `BYTEA`;
SQLAlchemy `LargeBinary`, which maps to a BLOB under SQLite for tests), never
as base64 text and never as new files in `backend/uploads/`.

- **Uploads** (`POST /api/v1/admin/icons/upload` multipart,
  `POST /api/v1/admin/icons/upload-base64`) are SUPERADMIN-only. Both decode
  at most 2 MB of image data with bounded reads (the base64 payload itself is
  size-capped before decoding); the actual bytes are then decoded with Pillow
  and only PNG, JPEG, WebP and ICO pass. Filename extensions and client MIME
  types are ignored; SVG and other malformed/scriptable content is rejected.
- **Serving:** `GET /api/v1/images/{id}` is public (like the legacy
  `/uploads` files it replaces, so unauthenticated mobile clients can load
  icons) and returns the exact stored bytes with the validated
  `Content-Type`, correct `Content-Length`, `Cache-Control: public,
  max-age=31536000, immutable`, `X-Content-Type-Options: nosniff` and a
  content-hash `ETag` (304 revalidation supported). Unknown IDs return 404.
- **Immutable assets:** replacements are new uploads with new IDs and URLs,
  so aggressively cached images never serve stale content. An asset is only
  deleted when no `app_icons.asset_id` or `businesses.icon_asset_id`
  references it anymore (shared assets are safe); uploads that were never
  assigned are retained until an explicit cleanup policy removes them.
- **Compatibility:** rows keep their `image_url`/`icon_url` value — it simply
  points at `/api/v1/images/{id}` for database-stored images. Plain URLs
  (legacy `/uploads/…` files, approved external HTTPS links) still work as
  before with a null asset reference. Lists (`/api/v1/admin/icons`,
  `/api/v1/mobile/icons`, business lists) carry URLs only, never image
  bytes; the binary column is deferred and never joined, so ordinary icon
  and business queries do not read image data.
- **Size budget:** each image is capped at 2 MB decoded bytes and
  25 megapixels, bounding both storage and decoding cost.

## 8. Data model and code organization

```text
backend/
  app/
    db.py          database sessions and transaction lock
    models.py      SQLAlchemy tables
    images.py      image validation, storage and legacy-file classification
    schemas.py     validated request contracts
    security.py    Argon2 passwords, expiring JWTs and role dependencies
    services.py    inventory, journal, idempotency and settlement logic
    main.py        API routes and reports
    manage.py      explicit schema/data migrations and owner initialization
  tests/           API, transaction and concurrency tests
  postman/         tested ordered request collection
  scripts/         API export generator
  openapi.json     complete generated API contract
  compose.yaml     local PostgreSQL and API services
```

| Table | Responsibility |
|---|---|
| `users` | Phone, password hash, role, KYC flag, language, JWT revocation version |
| `businesses`, `admin_assignments` | Offering/stock configuration and business-scoped admin access |
| `suppliers` | Business supplier directory |
| `daily_ledgers`, `operations` | Daily state and typed purchases/sales/byproduct/expenses |
| `batches`, `batch_logs` | Broiler funding/lifecycle, costs, feed and mortality |
| `share_ledger` | Append-only acquisition events with user, business, optional batch, shares, paid amount and time |
| `journal`, `postings` | Balanced accounting events; wallet balance is a sum, not a mutable field |
| `settlements` | Unique source, exact ownership snapshot, principal, profit, payouts, retained amount |
| `withdrawals` | Reserved withdrawal amount and pending/paid/failed lifecycle |
| `idempotency` | User/operation/key scope, request hash and committed response |
| `image_assets` | Uploaded icon/logo image bytes (deferred BLOB), validated MIME, original filename, size, SHA-256 and creation time; referenced by `app_icons.asset_id` and `businesses.icon_asset_id` |
| `write_lock` | Cross-worker transaction serialization |

UUIDs identify entities; monetary columns are BIGINT, quantity columns NUMERIC. Foreign keys enforce relationships. Unique constraints prevent duplicate business dates, batch dates, settlement sources and journal references. PostgreSQL triggers reject UPDATE/DELETE on financial history tables. Administrative DDL/table-owner permissions can bypass protections: provision a restricted runtime database role before production. Service code enforces balanced journal creation; direct database writes are unsupported.

## 9. Before any real-money launch

- Approve loss allocation, ownership denominator/cutoff, redemption rights, management fees, taxes, batch funding, disclosure and jurisdiction-specific legal/regulatory requirements. This implementation is not regulatory approval.
- Obtain payment-provider sandbox/production access. Implement signed callbacks, provider-reference uniqueness, deposit reconciliation, withdrawal confirmation/retries, fraud checks, limits and treasury/escrow controls. Do **not** simply enable mock routes in production.
- Implement actual phone OTP delivery/verification, recovery, KYC evidence/review/retention and admin MFA. Add distributed rate limiting and abuse controls for registration/login at the gateway or application layer.
- Keep development registration/mock self-verification away from real funds, and use a separate clean production database. Mock verification stores `MOCK_VERIFIED`, which is not accepted outside development. No production KYC approval workflow is provided yet; real-money routes cannot be safely activated as-is.
- Add reviewed schema migrations (e.g. Alembic), backups and restore drills, restricted DB roles, TLS, secret management, centralized audit/security logs, monitoring and alerting.
- Add documented correction/reversal workflows, bank/cash reconciliation, and settlement approval controls. Current financial history is not editable through the API.
- Load-test PostgreSQL, run provider integration/security tests, and review journal reconciliation before handling real funds.
- Add notifications, investor Figma mapping and Investor Kotlin client integration separately. Supplier payable balances, LPG customer accounts and secondary share trading are outside this backend MVP.

## 10. Android connectivity and troubleshooting

- Android emulator → local API: `http://10.0.2.2:8000`. This special emulator address is **not** for hosted browser previews.
- Physical Android phone → your computer's LAN IP, e.g. `http://192.168.1.20:8000`, on the same Wi-Fi. Permit port 8000 in your firewall. Android development builds may need a debug-only cleartext-network policy; use HTTPS in production.
- Hosted clients/previews → the API's public HTTPS host. Never use `localhost` from a remote browser to reach this server. Native clients do not need CORS. No wildcard CORS policy is configured; a future web UI should use an allowlisted origin or a same-origin reverse proxy.
- `JWT_SECRET` error: create `.env` in `backend/`, set a random 32+ character secret, and run from that directory.
- Missing `app_icons`/`image_assets` tables or `asset_id` columns on an existing database: stop the API and run `python -m app.manage upgrade-db` (then `migrate-images` if legacy uploads exist), as described above.
- Uninitialized database / missing `write_lock`: run `python -m app.manage init-db` (or its Docker equivalent).
- `401`: log in again; tokens expire after one hour. Password change/logout revokes all previously issued tokens for that account.
- `403`: check role, business assignment or KYC. Investors cannot use admin endpoints.
- `404` on `/dev/...`: mock routes intentionally do not exist outside development with mock payments enabled.
- `409`: inspect `detail` for stock/funds/share availability, closed records, duplicate dates or conflicting idempotency keys.
- `422`: inspect the validation details; check paisa integer values, phone E.164 format, decimal quantity, date and required headers.
- `503` on real payment routes: expected until genuine providers are integrated.
