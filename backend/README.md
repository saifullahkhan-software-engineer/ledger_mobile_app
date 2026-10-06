# Ahsan Traders — backend

FastAPI backend for the Admin/Investor PRD. **The Kotlin Admin client lives in [`../admin-android/`](../admin-android/README.md); an Investor client is not implemented.**

## Delivery status

Implemented: password authentication, roles and assigned-business authorization, chicken/LPG inventory and operational records, suppliers and purchase history, daily closure, broiler funding/start/log/harvest lifecycle, share purchases, append-only financial history, atomic distributions, wallet reservations/refunds, marketplace, portfolio, reports, profile/language, OpenAPI, Postman, Docker and backend CI.

Daily operations record money as a **fixed total amount** (not weight × a per-kg or per-gram rate); profit, revenue and settlements are always computed from those totals, never from weight or bird count. Chicken purchases/sales additionally carry an optional bird **count** that moves `businesses.stock_count` alongside weight-based stock. Chicken **sales** may also carry a **wastage** weight (and a standalone `WASTAGE` kind exists) so leftover cutting loss is deducted from stock without adding revenue, keeping the day-end weight in line. Chicken **purchases** may carry a **live weight**: live birds lose at least 35% of their weight when slaughtered, so the dressed meat that enters stock is capped at 65% of it, and the whole purchase amount is carried against those dressed kilos (Rs 15,000 for 100 kg live is 65 kg at Rs 230.77/kg, not Rs 150/kg). Profit and loss is plain business accounting, **net profit = revenue − cost of sales − wastage loss − expenses**, and day payloads and reports expose those lines as `cogs`, `wastage_cost` and `expenses`. A correction keeps the cost a sale or wastage record was booked at (rules 2 and 15). Expenses carry an optional **category** (e.g. Worker Salary, Electricity). `GET /api/v1/admin/stock` returns remaining kg, bird/cylinder count and carrying price; `PUT /api/v1/admin/stock` lets an assigned admin set those three figures. `GET /api/v1/admin/operations/{id}` returns one transaction's full details (operation + day + business); `PATCH /api/v1/admin/operations/{id}` corrects a single transaction **only for the SUPERADMIN**, on today's open day and on previous dates that have already closed and settled. The SUPERADMIN may also **add** a new record to a previous date (`POST /admin/ledger/daily` with that date); a manager cannot. Adding or correcting on a closed date rebuilds the date summary but never rewrites a payout that already went out (see rule 3 below). Existing databases gain `operations.count`, `operations.category`, `operations.wastage`, `operations.live_weight` and `businesses.stock_count` automatically: the API self-heals missing columns on the first request, so a redeploy onto an old database needs no manual step; `python -m app.manage upgrade-db` remains available as the explicit operator command and is a no-op once healed.

**This is a runnable backend MVP, not a launch-ready financial service.** Real OTP, identity verification, Raast/NayaPay/UBL transfers and callbacks are not implemented. The development-only KYC/deposit/withdrawal simulation is deliberately labelled and disabled outside development. Password login uses a phone number as the username; it does **not** verify ownership of that phone. Do not use this build to accept real investor money.

### Verification performed in the development sandbox

- SQLite API/service integration tests, including Admin client contract coverage, the transaction feed (kind/date filters, ordering, pagination, access), day-summary navigation (past/today/future, gaps, bounds, no rows created for future dates), midnight auto-close/settlement, owner past-date entry and corrections of settled dates, chicken wastage, editable stock, live-weight purchases and the 65% cap, frozen cost of sales on corrections, the profit-and-loss lines on days and reports, the `upgrade-db`/`drop-icon-schema` CLI on old schemas and the `clear-data` reset (dry run, confirmation, what is kept/deleted, business options, refusal when no account would be kept, and a working API afterwards): **74 passed, 2 skipped** on SQLite. The PostgreSQL 16 run happens in CI and has not been observed here.
- Ordered Postman collection executed with Newman: **28 requests, 32 assertions passed**.
- Tests include concurrent share purchases, concurrent withdrawals, concurrent duplicate settlements, rounding, duplicate-key conflicts, rollback, authorization, password revocation, stock valuation, and batch profit/loss.
- The two skipped tests need PostgreSQL append-only triggers: that history cannot be deleted directly, and that `clear-data` leaves those triggers enabled (`pg_trigger.tgenabled = 'O'`) after using them. **PostgreSQL 16.2 was verified** by running the whole suite against a real server started from a self-contained PostgreSQL binary in the sandbox: **76 passed, 0 skipped** (re-run after the live-weight and profit-and-loss changes). Docker itself is still not installed there and system package installation is unavailable, so the Compose stack (`docker compose up`) was not exercised; CI runs the same suite against the `postgres:16` service and is green.
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
- **Database health:** http://localhost:8000/health/db — public, no token, no
  transaction lock. Returns `200` with `latency_ms` when `SELECT 1` succeeds and
  `503` when the database is unreachable. Intended for uptime/keep-alive cron
  jobs that must keep a scaled-to-zero host (e.g. Render free tier) warm; it
  answers in milliseconds and sends `Cache-Control: no-store` so a CDN or proxy
  cannot serve a cached reply instead of waking the app.
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

Business days close and settle themselves when their date ends (midnight
`Asia/Karachi` by default; set `DAY_CLOSE_HOUR` to shift that moment). No
scheduler is required — the API sweeps finished days before it reports or
changes any day status. To have the close happen on time even with no traffic,
run the same sweep from cron, for example every 5 minutes:

```bash
python -m app.manage auto-close-days
```

Run commands from `backend/`, not the repository root. The API does not auto-create tables on startup. `init-db` is for the initial schema; it **does not migrate existing tables** after future schema changes.

### Removing the retired icon schema (`drop-icon-schema`)

The custom screen-icon feature has been removed: the Admin app draws every
icon and logo from artwork bundled in the APK, and the API exposes no image
endpoints. Databases created before that removal may still hold the retired
tables and columns:

| Retired object | What it held |
|---|---|
| `app_icons` | one row per configurable screen-icon slot |
| `image_assets` | the uploaded image bytes (`BYTEA`) |
| `businesses.icon_url`, `businesses.icon_asset_id` | the per-business custom image |
| `app_icons.asset_id` | foreign key to `image_assets` |

`init-db` and `upgrade-db` never recreate them — they are gone from the ORM
metadata — so an existing database keeps them until you remove them:

```bash
python -m app.manage drop-icon-schema          # dry run: lists what would go
python -m app.manage drop-icon-schema --yes    # asks you to type DROP, then drops
```

Add `--force` alongside `--yes` to skip the typed confirmation in scripts. The
command is repeatable: a second run reports "Nothing to do". It drops the
columns before the tables so the foreign keys disappear with them, and on an
SQLite build too old for `ALTER TABLE DROP COLUMN` it reports those columns as
`SKIPPED` rather than failing. With Docker Compose:
`docker compose run --rm api python -m app.manage drop-icon-schema --yes`.

**This is destructive.** Uploaded icon images are permanently lost, which is
the point — nothing reads them any more. Accounts, businesses and financial
history are untouched. Take a normal database backup first if you might want
the old artwork back.

`upgrade-db` remains the repeatable additive upgrade for the columns the
current schema does need (`operations.count`, `operations.category`,
`operations.wastage`, `operations.live_weight`, `businesses.stock_count` and
the `operations.day_id` index). It alters no data and is a no-op once a database is current.


### Clearing all data (keep the owner and the managers)

`clear-data` removes every business record while **keeping the SUPERADMIN
(owner) and ADMIN (manager) accounts**, so an installation can start over
without recreating logins or reassigning businesses to managers.

**Take a backup first** (`pg_dump`, or a copy of the SQLite file), stop the
API and any `auto-close-days` cron, then run from `backend/`:

```bash
python -m app.manage clear-data          # dry run: prints exactly what would go
python -m app.manage clear-data --yes    # asks you to type DELETE, then deletes
```

With Docker Compose: `docker compose run --rm api python -m app.manage clear-data --yes`.

| Deleted | Kept |
|---|---|
| `operations`, `daily_ledgers`, `suppliers`, `batches`, `batch_logs`, `share_ledger`, `journal`, `postings`, `settlements`, `withdrawals`, `idempotency` | `write_lock` (the API's serialization row, not data) |
| every `users` row that is not SUPERADMIN/ADMIN (investors), and the `admin_assignments` rows of those accounts | SUPERADMIN and ADMIN accounts and their business assignments |
| — | `businesses`, with `stock`, `stock_cost` and `stock_count` reset to 0 |

Flags:

| Flag | Effect |
|---|---|
| `--yes` | actually delete; without it the command only prints the plan |
| `--force` | skip the typed `DELETE` confirmation (with `--yes`, for scripts) |
| `--keep-roles SUPERADMIN,ADMIN` | which roles survive (default shown) |
| `--delete-businesses` | also delete the businesses and every manager assignment |
| `--force-relogin` | bump `token_version` on the kept accounts so signed-in devices must log in again |

Notes:

- Everything happens in **one serialized transaction** (the same `write_lock`
  the API takes), so it either completes fully or leaves the database
  untouched. The dry run only counts rows and rolls its transaction back.
- The command **refuses to run when no account matches the kept roles** — it
  never deletes every user. Run `python -m app.manage seed` to recreate an
  owner if you cleared with a role that no longer exists.
- On PostgreSQL the append-only `immutable_history` triggers on `journal`,
  `postings`, `share_ledger` and `settlements` are disabled **for that
  transaction only** and re-enabled before it commits; foreign-key
  enforcement stays on the whole time. Run as the role that owns the tables
  (the user in `DATABASE_URL`) — if the guard cannot be lifted the command
  aborts and deletes nothing.
- Retired icon tables (`app_icons`, `image_assets`) are **not** touched by
  this command — see `drop-icon-schema` above.

## 3. Test using Swagger (no coding needed)

1. Open `/docs` and execute `POST /api/v1/auth/login` with the owner phone/password entered during seed.
2. Copy `access_token`. Click **Authorize** and paste the token only (Swagger adds `Bearer`).
3. Call `GET /api/v1/admin/businesses`; copy a chicken business ID.
4. Register an investor using `/auth/register`. Save the returned investor token separately.
5. Authorize as that investor. Execute `/dev/verify-kyc`, then `/dev/wallet/deposit` with `{"amount":1000000}` (Rs 10,000).
6. Buy shares using `/investor/transaction/buy`. Do this **before recording the first operation of the day**.
7. Authorize as the owner again. Use `/admin/ledger/daily` to record purchases, sales and expenses. Copy the returned `day.id`.
8. Execute `/admin/ledger/close` with that `day_id` to close the date early, or wait until midnight (Pakistan time) — the day then closes and settles automatically.
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

A chicken purchase bought by live weight sends `live_weight` and may leave `quantity` at `"0"`:

```json
{
  "business_id": "BUSINESS_UUID",
  "date": "YYYY-MM-DD",
  "kind": "PURCHASE",
  "live_weight": "100",
  "quantity": "0",
  "amount": 1500000
}
```

- Stock gains **65 kg** (65% of the live weight) at a carrying cost of **Rs 15,000**, i.e. **Rs 230.77/kg**. Selling 40 kg for Rs 12,000 then costs Rs 9,230.77, so the profit is **Rs 2,769.23**.
- A `quantity` above 65 kg is clamped to 65 kg and the response says `"yield_capped": true`; a lower one (a poorer real yield) is kept as sent.

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
| Chicken/LPG/broiler summary | `GET /admin/businesses/{id}/summary?on=DATE` — any date, with `relation` (PAST/TODAY/FUTURE), `bounds` (first recorded date, today), `settled_net_profit`/`variance` for a closed date and that day's batch logs |
| Add sale / purchase / expense / wastage | `POST /admin/ledger/daily` with a typed operation. Amount is the fixed total (paisa), not a per-kg rate. Chicken sales accept optional `wastage` kg; `kind: WASTAGE` records leftover weight with no sale amount. Chicken purchases accept optional `live_weight` kg (dressed `quantity` is capped at 65% of it). SUPERADMIN may use a previous date. |
| All Transactions list | `GET /admin/operations` lists single transactions newest-date-first; pass `kind` for a type tab |
| Chicken "Other sale" (shown as Pota-Kaliji in the reference image) | `BYPRODUCT` sales, separate optional weight and revenue |
| LPG retail / shopkeeper sales | `SALE` with `RETAIL` or `COMMERCIAL` channel |
| Close Day (PRD, not pictured) | `POST /admin/ledger/close` |
| Broiler Add Record | `PUT /admin/batch/{id}/update` |
| Broiler start / harvest | `POST /admin/batch/{id}/start`, `.../harvest` |
| Stock | `GET /admin/stock?business_id=...` (kg, count, carrying price); `PUT /admin/stock` sets those three figures |
| Supplier bills | Supplier directory and recorded purchase history; not a full accounts-payable system |
| Expenses list | `GET /admin/expenses` for daily businesses; broiler expenses are in batch logs |
| Daily / weekly / monthly reports | `GET /admin/reports?start=...&end=...` |
| "Last 5 sales / purchases", recent expenses, transaction history | `GET /admin/operations?business_id=...&kind=...&start=...&end=...` |
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
2. **Inventory:** Weighted-average cost of goods sold, carrying unsold stock forward. Last-unit sale consumes all remaining inventory cost so rounding does not strand paisa. Purchases must be recorded before sales; negative stock is rejected. Chicken sale **wastage** (and the `WASTAGE` kind) also leave stock at weighted-average cost, with no revenue, so leftover cutting loss does not remain as phantom kg at day end. Byproduct sales do not subtract primary chicken weight to avoid double counting; use the weight of primary sold stock in `SALE`. `PUT /admin/stock` sets remaining kg, count and carrying price directly. **Live purchases:** a chicken purchase may send `live_weight`; live birds lose at least 35% when dressed, so the kilos that enter stock are `min(quantity, 65% of live_weight)` (omit `quantity` to take the full 65%), rounded down to the gram. The purchase amount is unchanged, so a dressed kilo carries `amount ÷ dressed kg`. Without `live_weight` the entered `quantity` is taken as already-dressed meat. **Corrections keep the booked cost:** a sale or wastage record stores the cost it left stock at, and `PATCH` never re-prices it at today's average; an edit that leaves the weight alone (amount, birds, note) keeps that cost exactly, and a weight change scales the same unit cost. Correcting a purchase therefore changes the stock's carrying cost from then on, not the cost of sales already booked. Selling out the last kilos still takes the remaining carrying cost. A record added to a past date is costed at the average on the day it is entered.
3. **Day boundary and automatic close:** Asia/Karachi. Managers must date new operations today; a future date is always rejected. The **super admin** may add a record to a previous date (including a closed one, or a gap with no records). A business date closes **automatically once it ends** — by default at midnight (`DAY_CLOSE_HOUR=0`) — which finalizes the day summary and runs the same settlement as a manual close. The sweep runs before any endpoint that reports or changes day status, so it needs no scheduler; `python -m app.manage auto-close-days` does the same from cron for installations that want the close to happen on time with no traffic, and a manual `/admin/ledger/close` still closes today early.
   - **Corrections and past-date entry:** the super admin (and only the super admin) may correct a transaction — including one on an already settled date — through `PATCH /admin/operations/{id}`, and may add a new sale/purchase/expense/wastage to a previous date through `POST /admin/ledger/daily`. The date summary is rebuilt from its records, but the payout already distributed is never rewritten, and the difference is returned as `variance` alongside `settled_net_profit` so the owner can settle it outside the app. A brand-new past date (the shop was closed that day) is created and then settled the same way midnight would have. Managers can only add records to today's open date and can never correct one.
4. **Ownership cutoff:** Running-business purchases are permitted before that day's first operation. Once operations begin, purchases wait until the next business date and all older days are closed. This avoids buying a known day's profit. No resale/redemption/dilution is implemented.
5. **Denominator:** Daily payouts use the business's **total issued offering shares**, not only sold shares. Unsold equity is economically retained by the business operator. This is an explicit interpretation of the PRD's ambiguous "active shares" wording; confirm it before launch. There is no separate management fee.
6. **Daily losses:** No investor wallet debit and no negative payout. Loss is recorded; **no loss carry-forward** is implemented. A later positive day can distribute profit. This policy needs business/legal approval.
7. **Rounding:** Each payout is floored to paisa. Unsold equity's entitlement and rounding remainders are recorded as `retained`. Investor payouts plus retained equal the nonnegative distribution pool.
8. **Batch lifecycle:** `FUNDING → ACTIVE → HARVESTED`. Investments only while FUNDING, with fixed share price. Starting locks ownership. One feed/mortality log per date. Mortality cannot exceed chicks remaining. Harvest may happen early/late; the PRD's 30–50 days is a target, not a hard gate.
9. **Batch pool:** `max(0, total_shares × share_price + sale_revenue − all_recorded_costs)`. The unsold capital is assumed to be operator-funded. Record initial chick costs, subsequent feed/other expenses and final extra costs **once**, not again at harvest. Principal is repaid only through the harvest settlement, not again through share redemption. Investor losses are limited to invested principal; excess loss belongs to the operator.
10. **Settlement timing:** Close/harvest runs settlement immediately in the **same database transaction**, not a CRON job. The automatic midnight close is the same in-process settlement triggered by the next request (or by the `auto-close-days` cron command), never a partial state. A failed operation rolls back all changes. Repeating the same idempotency key returns the saved response; attempting another settlement on a closed entity conflicts. A queue/outbox is a future scaling step, not a running feature.
11. **Concurrency:** A single PostgreSQL row lock serializes API database transactions across workers; SQLite uses `BEGIN IMMEDIATE`. This simple MVP trades throughput for correctness. Replace it with consistently ordered per-business/wallet locks only after load/concurrency testing. Do not remove the lock without redesigning settlement and purchase atomicity.
12. **Wallet accounting:** No editable `wallet_balance` field. Balance is derived from signed postings. Each journal balances to zero against business/external/withdrawal-hold accounts. Operational cash postings reflect **admin-entered** activity, not bank-confirmed funds. A pending withdrawal reserves the amount; failure refunds it once; success clears its hold.
13. **Funding limitation:** Business counterpart accounts can be negative when the operator supplies off-platform capital. There is no bank-liquidity reconciliation or solvency check. A posted dividend is an internal entitlement, not proof that external cash is available to withdraw.
14. **Valuation:** Portfolio shows acquisition cost and realized profit/loss, not a live market valuation. ROI history is actual recorded P&L divided by offering capital, not a forecast or guaranteed yield. Open daily reports are provisional; broiler revenue/profit is recognized only when harvested, not smoothed into daily sales.
15. **Profit and loss lines:** the business's profit is **revenue − cost of sales − wastage loss − expenses**. Revenue is sale and byproduct amounts. Cost of sales is the weighted-average cost of the kilos sold. Wastage loss is the cost of kilos that left stock without revenue: `WASTAGE` records plus the wastage kg on a sale, which is split from the sale's cost by weight. Expenses are `EXPENSE` records (broiler batch costs count as expenses). Day payloads carry `cost` (= `cogs` + `wastage_cost`) and reports carry `cogs`, `wastage_cost` and `expenses` per business and as `total_*`; `cost_and_expenses` and `net_profit` are unchanged. This is the business side only: the share, payout and settlement rules above are separate, and nothing in this rule reserves or withholds part of the profit.

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
    manage.py      explicit schema/data migrations, owner initialization and the clear-data reset
  tests/           API, transaction and concurrency tests
  postman/         tested ordered request collection
  scripts/         API export generator
  openapi.json     complete generated API contract
  compose.yaml     local PostgreSQL and API services
```

| Table | Responsibility |
|---|---|
| `users` | Phone, password hash, role, KYC flag, language, JWT revocation version |
| `businesses`, `admin_assignments` | Offering/stock configuration (weight stock, counted stock via `stock_count`) and business-scoped admin access |
| `suppliers` | Business supplier directory |
| `daily_ledgers`, `operations` | Daily state and typed purchases/sales/byproduct ("other sale")/expenses/wastage; operations carry weight, optional bird `count`, optional chicken-sale `wastage` and optional expense `category` |
| `batches`, `batch_logs` | Broiler funding/lifecycle, costs, feed and mortality |
| `share_ledger` | Append-only acquisition events with user, business, optional batch, shares, paid amount and time |
| `journal`, `postings` | Balanced accounting events; wallet balance is a sum, not a mutable field |
| `settlements` | Unique source, exact ownership snapshot, principal, profit, payouts, retained amount |
| `withdrawals` | Reserved withdrawal amount and pending/paid/failed lifecycle |
| `idempotency` | User/operation/key scope, request hash and committed response |
| `write_lock` | Cross-worker transaction serialization |

UUIDs identify entities; monetary columns are BIGINT, quantity columns NUMERIC. Foreign keys enforce relationships. Unique constraints prevent duplicate business dates, batch dates, settlement sources and journal references. PostgreSQL triggers reject UPDATE/DELETE on financial history tables. Administrative DDL/table-owner permissions can bypass protections: provision a restricted runtime database role before production. Service code enforces balanced journal creation; direct database writes are unsupported.

## 9. Before any real-money launch

- Approve loss allocation, ownership denominator/cutoff, redemption rights, management fees, taxes, batch funding, disclosure and jurisdiction-specific legal/regulatory requirements. This implementation is not regulatory approval.
- Obtain payment-provider sandbox/production access. Implement signed callbacks, provider-reference uniqueness, deposit reconciliation, withdrawal confirmation/retries, fraud checks, limits and treasury/escrow controls. Do **not** simply enable mock routes in production.
- Implement actual phone OTP delivery/verification, recovery, KYC evidence/review/retention and admin MFA. Add distributed rate limiting and abuse controls for registration/login at the gateway or application layer.
- Keep development registration/mock self-verification away from real funds, and use a separate clean production database. Mock verification stores `MOCK_VERIFIED`, which is not accepted outside development. No production KYC approval workflow is provided yet; real-money routes cannot be safely activated as-is.
- Add reviewed schema migrations (e.g. Alembic), backups and restore drills, restricted DB roles, TLS, secret management, centralized audit/security logs, monitoring and alerting.
- Add bank/cash reconciliation and settlement approval controls, and decide how an owner pays/collects the `variance` left by a post-settlement correction: journals and payouts stay append-only, so a correction after the close changes the date summary without adjusting wallets.
- Load-test PostgreSQL, run provider integration/security tests, and review journal reconciliation before handling real funds.
- Add notifications, investor Figma mapping and Investor Kotlin client integration separately. Supplier payable balances, LPG customer accounts and secondary share trading are outside this backend MVP.

## 10. Android connectivity and troubleshooting

- Android emulator → local API: `http://10.0.2.2:8000`. This special emulator address is **not** for hosted browser previews.
- Physical Android phone → your computer's LAN IP, e.g. `http://192.168.1.20:8000`, on the same Wi-Fi. Permit port 8000 in your firewall. Android development builds may need a debug-only cleartext-network policy; use HTTPS in production.
- Hosted clients/previews → the API's public HTTPS host. Never use `localhost` from a remote browser to reach this server. Native clients do not need CORS. No wildcard CORS policy is configured; a future web UI should use an allowlisted origin or a same-origin reverse proxy.
- `JWT_SECRET` error: create `.env` in `backend/`, set a random 32+ character secret, and run from that directory.
- Missing `operations.count` / `operations.category` / `operations.wastage` / `operations.live_weight` / `businesses.stock_count` on an existing database: the API adds them on the first request; `python -m app.manage upgrade-db` does the same explicitly.
- Uninitialized database / missing `write_lock`: run `python -m app.manage init-db` (or its Docker equivalent).
- `401`: log in again; tokens expire after one hour. Password change/logout revokes all previously issued tokens for that account.
- `403`: check role, business assignment or KYC. Investors cannot use admin endpoints.
- `404` on `/dev/...`: mock routes intentionally do not exist outside development with mock payments enabled.
- `409`: inspect `detail` for stock/funds/share availability, closed records, duplicate dates or conflicting idempotency keys.
- `422`: inspect the validation details; check paisa integer values, phone E.164 format, decimal quantity, date and required headers.
- `503` on real payment routes: expected until genuine providers are integrated.
