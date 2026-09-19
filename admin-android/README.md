# Ahsan Traders — native Android Admin app

**Open this `admin-android/` folder in Android Studio.** This is an independent Kotlin project — the Ahsan Traders Admin app.

An Admin-first implementation of the supplied screen reference, using Kotlin, Jetpack Compose Material 3 and MVVM. The green/gold branding, business-color cards, dashboard, drawer, bottom navigation, modules and settings follow the reference's visual direction. The AT mark is a locally drawn approximation, not an official supplied logo. No investor client is included in this delivery.

## What is implemented

- Configurable API server address and phone/password login for ADMIN/SUPERADMIN accounts.
- Dashboard with real sales/profit totals and yesterday comparisons. No fabricated sample metrics.
- Assigned-business drawer and red Chicken, green Broiler and blue LPG module cards.
- Chicken/LPG purchase, sale, expense and byproduct forms; LPG retail/commercial channel selection.
- Supplier creation and purchase history; optional supplier on a purchase.
- Stock and carrying cost; dated transaction history and operation detail.
- Close Day confirmation and investor-distribution feedback.
- Broiler funding-stage batch creation, start confirmation, feed/mortality/expense logs, harvest confirmation and historical batch detail (including harvested batches).
- Daily/last-seven-days/month-to-date reports, custom dates, per-business filtering and positive-profit chart. Losses remain included in the report table and totals.
- Settlement history, profile name/language editing, password change and logout.
- Core navigation/form labels in English/Urdu, RTL layout, Unicode user input preserved. Supporting explanations, server errors and some confirmations remain English.
- Loading, empty, validation, connection-error and expired-session states. User-triggered refresh and paginated history lists.
- Keystore-encrypted session token. No stored passwords, network payload logs, hardcoded credentials or demo-data fallback.
- Exact rupee-to-paisa conversion and persistent idempotency keys for supported financial commands.
- Owner-only Users & Access module: list/search users, add managers (with one or more business assignments) or investors, change roles, assign/remove businesses, and verify investor KYC.
- Owner-only Screen icons module: set the image for each mobile screen (app logo, business cards, quick actions) primarily by uploading from the device gallery (with an on-screen preview before saving). Uploaded image bytes are stored by the backend in PostgreSQL and served back from a short relative `/api/v1/images/{id}` URL, which the app resolves against the configured server — pasting an external image URL remains available as a separate option.
- Owner-only business icons: set, replace or remove a custom image per business (upload or external URL) next to the screen icons.
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
5. On the app's sign-in screen, enter the API server address:

| Android device | Server address |
|---|---|
| Standard Android Studio emulator | `http://10.0.2.2:8000` |
| Physical phone on the same Wi-Fi | `http://YOUR_COMPUTER_LAN_IP:8000` |
| Physical phone over USB with `adb reverse tcp:8000 tcp:8000` | `http://127.0.0.1:8000` |
| Hosted API | Your actual HTTPS origin, e.g. `https://api.example.com` |

Do not add `/docs` or `/api/v1` to the server address. Ordinary `localhost` on a phone points to the phone, not your computer (unless using the explicit USB reverse setup).

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
5. Close Day; review the irreversible-action confirmation and distribution result.
6. Open Transaction history → that date. Confirm CLOSED and inspect the operations.
7. Verify another write for the closed date is rejected.

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

- `POST /api/v1/admin/icons/upload` (multipart) and `POST /api/v1/admin/icons/upload-base64` — validate the actual image bytes (PNG/JPEG/WebP/ICO, max 2 MB decoded; SVG rejected) and store them as binary in the PostgreSQL `image_assets` table, returning a relative `/api/v1/images/{id}` URL. The admin app calls `upload-base64` from the gallery picker.
- `GET /api/v1/images/{id}` — public, serves the exact stored bytes with the validated `Content-Type`, `Content-Length`, `Cache-Control: immutable`, `ETag` and `X-Content-Type-Options: nosniff`; 404 for unknown IDs. Asset IDs are immutable: a replacement upload returns a new URL, so cached images never go stale.
- `PUT /api/v1/admin/businesses/{business_id}/icon` — save a custom business icon URL (pass an empty `icon_url` to revert to the default sector tile).
- `PUT /api/v1/admin/icons/{key}` — save a screen-icon URL (pass an empty `image_url` to revert to the default icon). When the URL is an `/api/v1/images/{id}` reference, the icon row is associated with that image asset.
- `GET /api/v1/admin/batches?business_id=...&offset=...&limit=...` — includes historical harvested batches.
- `GET /api/v1/admin/ledger/{day_id}` — refreshes a specific day including current closure status.

The database stores the image bytes themselves (BYTEA) plus a short serving URL on each icon/business row; icon lists and the mobile configuration only ever carry URLs, never base64/binary content. Files previously uploaded under `backend/uploads/` continue to be served from the legacy static mount until you run `python -m app.manage migrate-images` and remove them manually (see `../backend/README.md`).

## Verification status — important

- Backend suite, including database-image upload/serving/migration tests and Retrofit path/query/header contract checks: **41 passed, 1 PostgreSQL-only test skipped** in this sandbox (SQLite; the PostgreSQL run happens in CI and has not been observed).
- All Kotlin files passed structural checks; these checks do not establish compilation or UI correctness.
- Native source includes JVM test methods for exact money, quantity limits, Unicode, batch rules, server-URL resolution (including `/api/v1/images/…` relative URLs), retry hashing and Retrofit requests/deserialization.
- **Android Gradle build, JVM tests, lint, emulator UI and device networking have not been executed here.** The sandbox has no JDK/Android SDK and the official tool-download attempts failed. No APK is being claimed as built or verified.
- CI configuration is provided but has not been run remotely. Please run the build command above locally and share any errors for correction.

## Deliberate boundaries

This is the Admin app only. An Investor Android app is still separate future work. Real OTP, actual KYC evidence review (the app sets the verification flag for development), gateway transfers, push notifications, customer receivables, supplier payable balances, share trading and production compliance remain outside this client delivery.

Close/harvest have confirmation dialogs but no reopen/reversal workflow because the backend does not provide one. Daily losses, unsold equity and batch funding follow the policies documented in `backend/README.md`; those policies require approval before real-money use.
