# Baked-in icons — uploaded icons packaged into the APK

## What it does

Icons uploaded through the app's **Screen icons** screen are stored in the
backend database (PostgreSQL `image_assets`). With this feature, **every app
build** downloads those saved icons from the server and packages them inside
the APK under `assets/saved_icons/`. The app then uses them **directly** —
no runtime download, and they keep working **offline**.

## How it works

| Step | Where | What happens |
|---|---|---|
| 1. Build | Gradle task `fetchAppIcons` (`admin-android/app/build.gradle.kts`) | Runs before every app build (`preBuild`). Downloads the icons saved in the database and writes them to `admin-android/app/src/main/assets/saved_icons/<key>.<ext>` (+ `manifest.json`). |
| 2. Package | Android resource packaging | `assets/saved_icons/` is embedded into the APK automatically. |
| 3. Runtime | `Components.kt` → `ResolvedIcon` / `loadBuiltInIcon` | Each icon slot resolves in this order: **live URL from the server** (freshest) → **icon baked into this build** → **bundled default** (sector vector / saved logo lockup). |

The baked directory is generated output and is git-ignored — it is refreshed
on every build from whatever the database currently holds. Stale files from a
previous build are removed, and `manifest.json` records exactly which icons
this build contains.

## Configuration

In `admin-android/gradle.properties`:

```properties
# The backend the app talks to — also baked in as BuildConfig.SERVER_URL
# (see the "Backend URL" section of admin-android/README.md).
appServerUrl=https://your-server.com

# Optional: override the backend used ONLY for icon baking.
# appIconServer=http://…

# Optional — when set, per-business icons (business.icon_url) are also baked.
# appIconPhone is the super-admin phone seeded by the backend.
appIconPhone=03001234567
appIconPassword=your-password
```

- `appIconServer` (or `appServerUrl`/`APP_SERVER_URL` when `appIconServer` is
  empty) not set → task is skipped, build uses the previously baked icons (or
  the bundled defaults).
- No credentials → the public `GET /api/v1/mobile/icons` endpoint bakes all
  slot icons (`app_logo`, `business_chicken`, `business_lpg`,
  `business_broiler`, `quick_sale`, `quick_expense`, `quick_reports`,
  `quick_stock`). Per-business icons need `appIconPhone`/`appIconPassword`
  because the business list is an authenticated endpoint.
- Server unreachable at build time → the task **warns and the build
  continues**; the app falls back to the previously baked icons.

## Where the icons appear

| Slot key | Shown at |
|---|---|
| `app_logo` | Top app bar header, side bar header, login header (fallback: saved `logo_lockup.png`) |
| `business_chicken` / `business_lpg` / `business_broiler` (or the per-business `icon_url`) | Business cards on the home screen, business screen banner, side bar rows, reports rows — always in a **circular** container |
| `quick_sale` / `quick_expense` / `quick_reports` / `quick_stock` | Quick-action tiles and link rows (circular container) |

If nothing is set anywhere (no live URL, no baked asset), the bundled saved
icons are used, so the app always shows the brand artwork.

## Keeping it fresh

Rebuild the app (`./gradlew assembleDebug` / Android Studio ▶) after changing
icons in the Screen icons screen and the new images are baked into the next
APK. Until the next build, devices that can reach the server still see the
newest uploaded icons, because live URLs take priority over baked assets.
