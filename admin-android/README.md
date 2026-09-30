# Ahsan Traders — native Android Admin app

**Open this `admin-android/` folder in Android Studio.** This is an independent Kotlin project — the Ahsan Traders Admin app.

An Admin-first implementation of the supplied screen reference, using Kotlin, Jetpack Compose Material 3 and MVVM. The green/gold branding, business-color cards, dashboard, drawer, bottom navigation, modules and settings follow the reference's visual direction. The AT mark is a locally drawn approximation, not an official supplied logo. No investor client is included in this delivery.

## What is implemented

- Backend address baked in at build time (`APP_SERVER_URL` environment variable or `appServerUrl` in `gradle.properties` → `BuildConfig.SERVER_URL`); the sign-in screen only asks for phone and password. Login for ADMIN/SUPERADMIN accounts.
- Dashboard with real sales/profit totals and yesterday comparisons. No fabricated sample metrics.
- Assigned-business drawer and red Chicken, green Broiler and blue LPG module cards.
- Chicken/LPG purchase, sale, expense and "Other sale" (byproduct) forms; LPG retail/commercial channel selection.
- Supplier creation and purchase history; optional supplier on a purchase.
- Stock and carrying cost; transaction history that lists **single transactions** (All / Sale / Purchase / Expense / Other sale chips plus Latest / Today / 7 days / This month ranges) with the day overview on a "Days" tab, and operation detail.
- "Last 5 Sales", "Last 5 Purchases" and "Recent Expenses" tables under the matching entry forms, each with **View all** that opens the history filtered to that type.
- Close Day confirmation and investor-distribution feedback. A business date also closes and settles itself when it ends (midnight Pakistan time), so a manager never has to close yesterday by hand.
- Day screen is addressed by **date** rather than by ledger row, with a `‹ date ›` stepper, Today / Yesterday chips and a bounded calendar picker: a closed past date shows what it settled (owner can still correct it), a date with no records and a date that has not started each get their own empty state, `‹` stops at the first recorded date and `›` stops at today.
- Broiler funding-stage batch creation, start confirmation, feed/mortality/expense logs, harvest confirmation and historical batch detail (including harvested batches).
- Daily/last-seven-days/month-to-date reports, custom dates, per-business filtering and positive-profit chart. Losses remain included in the report table and totals.
- Settlement history, profile name/language editing, password change and logout.
- Owner-only corrections: the super admin can correct a transaction on an open date or on a previous, already settled date. The date summary is rebuilt, the payout already made is never changed, and the difference is shown on the date.
- Core navigation/form labels in English/Urdu, RTL layout, Unicode user input preserved. Supporting explanations, server errors and some confirmations remain English.
- Loading, empty, validation, connection-error and expired-session states. User-triggered refresh and paginated history lists.
- Keystore-encrypted session token. No stored passwords, network payload logs, hardcoded credentials or demo-data fallback.
- Exact rupee-to-paisa conversion and persistent idempotency keys for supported financial commands.
- Owner-only Users & Access module: list/search users, add managers (with one or more business assignments) or investors, change roles, assign/remove businesses, and verify investor KYC.
- Drawer shows the signed-in admin's name, phone and role; owner-only entries appear only for SUPERADMIN.
- Phone inputs accept local (0300…), +92… and 92… formats on login and add-user; they are normalized to E.164 before sending.

The app uses the existing backend's financial policies; it does not reimplement settlement calculations on the device. All changes require a server response. It is **not offline-first**: there is no offline transaction queue or persistent data cache.

## 1. Install the local tools (no Docker)

1. Install **Android Studio Ladybug (2024.2.1) or newer** with its Android SDK tooling.
2. In **SDK Manager**, install Android SDK Platform **35**, SDK Build-Tools **35.0.0**, Platform-Tools and the Android Emulator.
3. In **Settings → Build, Execution, Deployment → Build Tools → Gradle**, select **JDK 17**. Install/select a JDK 17 if your Studio bundle uses a different Java version. Command-line builds also need `JAVA_HOME` pointing to JDK 17.
4. In **Device Manager**, create/start an emulator (API 26 or newer; API 35 recommended), or connect a physical Android phone with USB debugging enabled.
5. For the API, install **Python 3.11+**.

First sync needs internet access to Google Maven, Maven Central and the Gradle distribution servers. Build versions are pinned: Gradle 8.9, AGP 8.7.3, Kotlin/Compose plugin 2.0.21, Compose BOM 2024.12.01. The official Gradle 8.9 wrapper JAR/scripts are included (sourced from the Gradle GitHub repository's `v8.9.0` tag).

## 2. Start the Python backend locally

From the repository's `backend/` folder, follow `../backend/README.md`, or use:

```bash
python -m venv .venv
```

Activate it:

```powershell
# Windows PowerShell
.venv\Scripts\Activate.ps1
```

```bash
# macOS/Linux
source .venv/bin/activate
```

Then:

```bash
python -m pip install -r requirements.txt
```

Create `backend/.env` from `.env.example`, generate a random secret with:

```bash
python -c "import secrets; print(secrets.token_urlsafe(48))"
```

Set these values in `.env` (replace the JWT placeholder with the output):

```dotenv
APP_ENV=development
JWT_SECRET=YOUR_RANDOM_SECRET_OF_AT_LEAST_32_CHARACTERS
DATABASE_URL=sqlite:///./ahsan.db
ENABLE_MOCK_PAYMENTS=true
```

For a new database only:

```bash
python -m app.manage init-db
python -m app.manage seed
```

The seed command asks for the owner phone and password and creates the sample Chicken/LPG/Broiler businesses. Existing accounts are not overwritten. If you already initialized the backend, keep your database and credentials; the two new read endpoints require no schema migration.

Start the API:

```bash
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

Check `http://127.0.0.1:8000/health` and `/docs` on your computer. Keep this terminal open while using the app.

## 3. Open and run the Android app

1. Android Studio → **Open** → select **`admin-android`**.
2. Wait for Gradle sync and SDK downloads to finish.
3. Select the **app** run configuration and your emulator/device.
4. Click **Run ▶**.
5. Point the app at the API at **build time** (the sign-in screen no longer shows a server field — it picks the address up from the build environment automatically):

| Android device | How to set the backend URL before building |
|---|---|
| Standard Android Studio emulator | Nothing — the built-in default is `http://10.0.2.2:8000` |
| Physical phone on the same Wi-Fi | `APP_SERVER_URL=http://YOUR_COMPUTER_LAN_IP:8000` |
| Physical phone over USB with `adb reverse tcp:8000 tcp:8000` | `APP_SERVER_URL=http://127.0.0.1:8000` |
| Hosted API | `APP_SERVER_URL=https://api.example.com` |

Set it as an environment variable, or put `appServerUrl=…` in `gradle.properties` (or pass `-PappServerUrl=…` once on the command line), then rebuild. The value is compiled into the APK as `BuildConfig.SERVER_URL` and used app-wide; the active address is shown under **Settings → About this app**.

Do not add `/docs` or `/api/v1` to the URL. Ordinary `localhost` on a phone points to the phone, not your computer (unless using the explicit USB reverse setup).

### How the backend URL works (standard mobile practice)

The URL is a **build-time constant**, not something the app asks for:

1. `app/build.gradle.kts` resolves it once per build — priority: `APP_SERVER_URL` environment variable → `appServerUrl` in `gradle.properties` (or `-PappServerUrl=…`) → emulator default `http://10.0.2.2:8000`.
2. It is injected with `buildConfigField("String", "SERVER_URL", …)` and compiled into `BuildConfig.SERVER_URL` inside the APK.
3. `SessionStore` uses it as the default backend address; login, the API client and Settings all read the same value.

This is the usual way Android apps pin an API origin: a Gradle/environment-driven `BuildConfig` field (larger teams do the same with `dev/staging/prod` build flavors or CI environment variables). Benefits: no user can accidentally point the app at the wrong server, the production URL never appears in the UI, and switching backends is just a rebuild with a new value.

6. Sign in with the **owner credentials created by `seed`**. There is no hardcoded login. Investor credentials are intentionally rejected.

For Wi-Fi use, allow port 8000 through your computer's firewall and use the same local network. Debug builds permit HTTP to support local development. **Release builds reject HTTP and require HTTPS.** Do not expose the development API to the public internet.

## 4. Build from the terminal

From `admin-android/`:

```powershell
# Windows
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

```bash
# macOS/Linux
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Android Studio normally writes `local.properties` with your SDK location. If building without opening Studio first, set `ANDROID_HOME` to your installed SDK or create an untracked `local.properties` containing `sdk.dir=/path/to/Android/Sdk`.

The debug APK will be generated at:

```text
admin-android/app/build/outputs/apk/debug/app-debug.apk
```

Install it on a connected device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

A debug APK is not a production release. Release signing is not configured and no signing credentials are stored in the repository. `.github/workflows/admin-android.yml` is configured to build/test and upload a debug APK; the GitHub connection must have workflow-write permission before that file can be pushed.

## 5. End-to-end manual checklist

Use a development database, not real financial records.

### Chicken
1. Open Chicken Shop → Add purchase: quantity **10 kg**, amount **Rs 1,000**.
2. Add sale: quantity **5 kg**, amount **Rs 1,000**.
3. Add expense: **Rs 100**.
4. Verify remaining stock **5 kg**, inventory cost **Rs 500**, provisional net profit **Rs 400**.
5. Close Day early, or leave the app and let the date close itself at midnight Pakistan time; review the distribution result.
6. Open Transaction history → that date. Confirm CLOSED and inspect the operations.
7. Verify another write for the closed date is rejected, and that as the owner you can still correct an operation on that closed date (its summary changes, the settled payout does not).

If there are no investor holdings, distribution is zero and the pool is retained. To test a payout, buy shares with an investor through the backend/Postman **before the first daily operation**. The Admin app cannot buy shares or fund investor wallets.

### LPG
1. Record whole-cylinder purchases.
2. Add retail and commercial sales separately.
3. Verify channel quantities, remaining stock and cost/profit totals.
4. Check that fractional cylinders and selling more than stock are rejected.

### Broiler
1. Create a batch with chick count, shares, share price and initial costs.
2. Optionally invest through the API while FUNDING, then Start batch.
3. Open the batch → Add daily record for feed, deaths and new expenses.
4. Verify mortality limits and duplicate-date rejection.
5. Harvest using total yield, price/kg and only **additional unrecorded** expenses.
6. Revisit the HARVESTED batch through Batches and inspect settlement history.

### Other checks
- Add a supplier; link a purchase to it; inspect Supplier bills.
- Filter reports by dates and business. No-data ranges should show zero/empty results, not fake demo data.
- Edit profile → Urdu; check RTL navigation and mixed-language names/notes. Switch back to English.
- Rotate while editing a form: fields remain in the ViewModel. Force-stop/process death clears drafts (they are **not** persisted).
- Stop the backend; refresh/save should display a connection error. Financial requests are not queued.
- After an uncertain write, retry unchanged fields: the request key is reused. If abandoning the form, inspect server records before entering a new transaction.
- Change password; the app returns to login, and previous tokens are revoked server-side.
- Test a restricted manager: only assigned businesses should be returned; unauthorized endpoints still reject direct requests.

## Architecture

```text
app/src/main/java/com/ahsantraders/admin/
  MainActivity.kt             activity, ViewModel factory and theme wiring
  data/
    Models.kt                 typed backend response DTOs
    AdminApi.kt               Retrofit endpoint contract
    AdminRepository.kt        HTTP client, authentication and retry-key boundary
    SessionStore.kt           Keystore token and request-key persistence
    Validation.kt             exact money/quantity validation and request mapping
  ui/
    AdminViewModel.kt         state, navigation, server operations and refresh
    AdminApp.kt               login, drawer, shell, dialogs and bottom navigation
    Screens.kt                dashboard/modules/history/reports/settings
    Forms.kt                  validated operational/profile forms
    Components.kt             shared visual components
    Theme.kt                  brand palette, core English/Urdu labels and direction
```

No Hilt, code generation, WebView or bundled mock backend. The financial source of truth stays in FastAPI.

### Small backend additions

- `GET /api/v1/admin/batches?business_id=...&offset=...&limit=...` — includes historical harvested batches.
- `GET /api/v1/admin/ledger/{day_id}` — refreshes a specific day including current closure status.
- `GET /api/v1/admin/operations?business_id=…&kind=…&start=…&end=…&offset=…&limit=…` — individual transactions of one business, newest business date first, each row carrying its business `date`; `kind` serves the last-5 tables and the history type chips. Backed by a new `operations.day_id` index that legacy databases gain automatically.

All icon and logo artwork is bundled in the APK (`app/src/main/res/drawable*`). The app never downloads, uploads or caches images, and the backend has no image endpoints.

## Verification status — important

- Backend suite, including midnight auto-close/settlement, owner corrections, the schema-upgrade/`drop-icon-schema` CLI and Retrofit path/query/header contract checks: **48 passed, 2 skipped** in this sandbox (SQLite; the PostgreSQL run happens in CI and has not been observed).
- All Kotlin files passed structural checks; these checks do not establish compilation or UI correctness.
- Native source includes JVM test methods for exact money, quantity limits, Unicode, batch rules, server-URL resolution, retry hashing and Retrofit requests/deserialization.
- **Android Gradle build, JVM tests, lint, emulator UI and device networking have not been executed here.** The sandbox has no JDK/Android SDK and the official tool-download attempts failed. No APK is being claimed as built or verified.
- CI configuration is provided but has not been run remotely. Please run the build command above locally and share any errors for correction.

## Deliberate boundaries

This is the Admin app only. An Investor Android app is still separate future work. Real OTP, actual KYC evidence review (the app sets the verification flag for development), gateway transfers, push notifications, customer receivables, supplier payable balances, share trading and production compliance remain outside this client delivery.

Close/harvest have confirmation dialogs. A closed date is never reopened; the owner corrects a single transaction instead, and a payout that already settled is not rewritten by that correction. Daily losses, unsold equity and batch funding follow the policies documented in `backend/README.md`; those policies require approval before real-money use.
