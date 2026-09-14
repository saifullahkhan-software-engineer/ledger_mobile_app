> **New native Admin frontend:** Open [`admin-android/`](admin-android/README.md) in Android Studio. It connects to the Python backend and runs without Docker. The original Flutter app below remains separate.

> **New Ahsan Traders backend:** See [backend/README.md](backend/README.md) for the Admin/Investor API, PostgreSQL/Docker setup, Postman tests, financial assumptions and production limitations. The Flutter project below is preserved and is not yet integrated with this API.

# Ledger — Udhaar (Credit) Ledger App

A minimal, offline-first **udhaar / credit ledger** MVP built with **Flutter** for
**Android** (local use only). It keeps track of customers, the credit (udhaar) you
give them, payments you receive, and their running balance — entirely on-device
using **SQLite**. No backend, no accounts, no network calls.

The app ships **bilingual** out of the box: **English (LTR)** and **Urdu (RTL)**,
switchable at runtime from a simple language selector in the app bar. User-entered
data (customer names, item names, descriptions, notes) is stored and displayed
**exactly as typed** — English, Urdu, or mixed.

---

## Features

- **Customers management** — add, edit, delete customers (name, phone, notes).
- **Add credit (udhaar)** — record items sold on credit with item details.
- **Receive payments** — record payments against a customer's account.
- **Current balance** — net outstanding balance per customer
  (credit − payments). Positive = customer owes you; negative = you owe them.
- **Transaction history** — dated list of all credit/payment entries per customer.
- **Basic validation** — required name/amount, phone format, item line checks.
- **Offline-first** — everything is local; works with no internet connection.
- **English + Urdu** — runtime language switch, correct LTR/RTL layout.

## Tech stack

| Layer      | Choice                          |
| ---------- | ------------------------------- |
| Framework  | Flutter (Dart)                  |
| Storage    | SQLite via [`sqflite`](https://pub.dev/packages/sqflite) |
| Paths      | [`path`](https://pub.dev/packages/path)                  |
| Prefs      | [`shared_preferences`](https://pub.dev/packages/shared_preferences) (language only) |
| Localization | `flutter_localizations` + a tiny hand-rolled `AppL10n` (en/ur) |

## Project structure

Simple MVC-like layout — Models / Repositories (data) / Controllers (logic) /
Views (UI). No code generation, no over-engineering.

```
lib/
├── main.dart                     # App entry, MaterialApp, locale wiring
├── models/                       # Plain data classes (the "M")
│   ├── customer.dart
│   ├── ledger_entry.dart         # LedgerEntry + EntryItem
│   ├── entry_type.dart           # CREDIT | PAYMENT enum
│   └── app_language.dart         # en / ur
├── repositories/                 # Data access (SQL stays here)
│   ├── customer_repository.dart
│   └── ledger_repository.dart
├── services/                     # Infrastructure
│   ├── database_service.dart     # SQLite open/create/migrate
│   └── language_service.dart     # Persist selected language
├── controllers/                  # Business logic + validation (the "C")
│   ├── customers_controller.dart
│   └── ledger_controller.dart
├── core/
│   ├── l10n/
│   │   ├── app_l10n.dart         # English + Urdu strings
│   │   └── l10n_ext.dart         # context.l10n shortcut
│   ├── theme/app_theme.dart
│   └── utils/money_format.dart   # int minor-units <-> "12.34"
├── views/                        # Screens (the "V")
│   ├── home_view.dart            # App shell + language selector
│   ├── customers_view.dart       # Customer list
│   ├── customer_form_view.dart   # Add/edit customer
│   ├── ledger_view.dart          # Balance + history for one customer
│   └── entry_form_view.dart      # Add credit / receive payment
└── widgets/                      # Small reusable widgets
    ├── empty_state.dart
    └── language_selector.dart
```

## Setup

Prerequisites:

- [Flutter SDK](https://docs.flutter.dev/get-started/install) (stable channel,
  Dart SDK `>=3.3.0`)
- Android toolchain (Android Studio or command-line tools + a device/emulator)
- JDK 17 (bundled with recent Android Studio; required by the Android Gradle
  Plugin version used here)

> The Android Gradle config (AGP 8.3.2 / Gradle 8.6 / Kotlin 1.9.22, compileSdk 34)
> is pinned for a deterministic build. If a future Flutter SDK requires newer
> versions, update `android/settings.gradle` and
> `android/gradle/wrapper/gradle-wrapper.properties`, or regenerate the Android
> shell with `flutter create --platforms=android .`.

```bash
git clone <repo-url>
cd ledger_mobile_app

# Fetch dependencies
flutter pub get

# Verify everything is wired up
flutter doctor
```

## Run locally (Android)

```bash
# List devices / emulators
flutter devices

# Run on a connected device or emulator
flutter run

# Optional: specify a device id
flutter run -d <device-id>
```

> The database is created automatically on first launch at the app's default
> SQLite location (e.g. `/data/data/com.example.ledger/databases/ledger.db`).
> There is no seed data and no login — the app is immediately usable offline.

### Building an APK

```bash
flutter build apk --release
# output: build/app/outputs/flutter-apk/app-release.apk
```

## Database schema

Three tables, one SQLite file (`ledger.db`, schema version `1`). Amounts are
stored as **integers in minor units** (e.g. `1500` = `15.00`) to avoid floating
point rounding. All text columns are UTF-8 and preserved byte-for-byte, so
English/Urdu/mixed input is safe.

```sql
CREATE TABLE customers (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  name       TEXT NOT NULL,
  phone      TEXT,
  notes      TEXT,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE entries (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  customer_id INTEGER NOT NULL,
  type        TEXT NOT NULL CHECK (type IN ('CREDIT', 'PAYMENT')),
  amount      INTEGER NOT NULL CHECK (amount > 0),   -- minor units
  description TEXT,
  entry_date  TEXT NOT NULL,                          -- ISO yyyy-MM-dd
  created_at  TEXT NOT NULL DEFAULT (datetime('now')),
  FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE
);

CREATE TABLE entry_items (
  id        INTEGER PRIMARY KEY AUTOINCREMENT,
  entry_id  INTEGER NOT NULL,
  item_name TEXT NOT NULL,
  quantity  REAL NOT NULL,
  unit      TEXT NOT NULL,
  price     INTEGER NOT NULL,                         -- minor units, per unit
  FOREIGN KEY (entry_id) REFERENCES entries (id) ON DELETE CASCADE
);

CREATE INDEX idx_entries_customer ON entries (customer_id);
CREATE INDEX idx_entry_items_entry ON entry_items (entry_id);
```

Notes:

- A **CREDIT** entry increases a customer's balance; a **PAYMENT** decreases it.
- `entry_items` rows are the item detail for udhaar (credit) transactions:
  item name, quantity, unit, and per-unit price.
- Deleting a customer cascades to their entries and item lines. Deleting an
  entry cascades to its item lines.
- Schema migrations: bump `dbVersion` and add cases to `_onUpgrade` in
  `lib/services/database_service.dart`.

## Future FastAPI integration (planning notes)

The app is deliberately local-only today, but the data model maps cleanly onto a
REST API later. Suggested direction:

1. **Add an API layer** in `lib/repositories/` (or a thin `api/` folder) behind
   the same repository interfaces the controllers already use — controllers
   should not change.
2. **Proposed endpoints** (FastAPI):
   - `GET    /customers` — list with computed balance
   - `POST   /customers` / `PUT /customers/{id}` / `DELETE /customers/{id}`
   - `GET    /customers/{id}/entries`
   - `POST   /customers/{id}/entries` — body: `{type, amount, description, entry_date, items[]}`
3. **Schema parity** — the FastAPI SQLAlchemy models can mirror the tables above;
   use `Decimal`/`int` cents server-side and keep `amount`/`price` in minor units.
4. **Sync strategy (later)** — keep SQLite as the offline cache and add a
   last-write-wins sync keyed on `updated_at`; for the MVP, stay local-only.
5. **Auth** — add OAuth2/JWT endpoints when a real backend lands; none needed now.

Keep the FastAPI service out of the app's core until offline-first is truly
required to sync; the local-only path already covers the MVP's needs.

## Localization

- Supported locales: `en` (LTR) and `ur` (RTL).
- Add/change strings in `lib/core/l10n/app_l10n.dart` (one class per language).
- Layout direction flips automatically via `MaterialApp.locale` + `Directionality`;
  the UI uses `EdgeInsetsDirectional` and `CrossAxisAlignment.start/end` so RTL
  works without extra effort.
- The selected language is persisted in `shared_preferences`.
