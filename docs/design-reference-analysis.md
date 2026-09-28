# Design reference review — "Ahsan Chicken Shop" 8-screen mockup

Analysis of the supplied mockup (Main Dashboard, Sale, Purchase, Expenses, Monthly
Report, Daily Summary, All Transactions, Chicken Shop) against the code in this
repository: what already matches, what is missing, and where the mockup's
arithmetic disagrees with our accounting.

> The uploaded image was not written into the workspace, so this document refers to
> screens by number and name rather than embedding it. All figures quoted below are
> read from the mockup.

## 1. What the mockup is

A single chicken-shop walkthrough — dashboard → the three entry forms → daily and
monthly summaries → a filterable transaction list → a shop settings/info screen.
Every screen carries the same 4-item bottom bar (**Home · Transactions · Reports ·
More**) and a module-coloured app bar (green sale, blue purchase, orange expense,
purple report). It is a *chicken/KG* flow only: no LPG cylinders, no broiler batches,
no investor/settlement surface.

**Arithmetic check:** the mockup is internally consistent, unlike many design files —
Monthly Report: `157,500 purchase / 180,000 sale / 22,500 gross / 8,000 expenses /
14,500 net` and Daily Summary: `3,500 − 5,250 − 600 = −2,350`. Both are
`net = sales − purchases − expenses`, i.e. **cash-flow basis**. That is the single
most important thing to reconcile before building (see §4.1).

## 2. Screen-by-screen

Legend: ✅ already supported · ⚠️ supported but rendered differently today ·
❌ missing (needs backend or new UI).

### Screen 1 — Main Dashboard
| Element | Status |
|---|---|
| Shop logo + name + tagline in header, notification bell | ⚠️ We show a greeting + brand strip; logo/name come from `businesses`. Tagline and bell need new UI (bell has no backend events to show). |
| Location pill "Mainwala Bangla Stop" | ❌ `businesses` has no address/location field. |
| Hero promo image with script text | ⚠️ Our `app_icons` image-slot system can serve this as an uploaded banner (new key, e.g. `dashboard_hero`); no backend change. |
| 2×2 tiles: SALE / PURCHASE / EXPENSES / REPORT (with sub-labels) | ⚠️ We currently have Home quick-actions (Add sale, Add expense) + per-business cards; tiles would replace them. Purple "Report" accent is new to the palette. |
| Bottom nav Home · Transactions · Reports · More | ⚠️ Ours is Home · Sales · Expenses · Reports; "Sales/Expenses" are form shortcuts, and the drawer opens from ☰ only on Home. Adopting the mockup means Sales/Expenses become real screens and **More** opens the drawer. |
| "Other sale" tile (our recent addition) | ❌ not in the mockup (it predates the request). Keep it as a 4th tile for chicken businesses. |

### Screen 2/3 — Sale & Purchase forms
| Element | Status |
|---|---|
| Date field with calendar picker, `DD-MM-YYYY` | ⚠️ We have a text field in `YYYY-MM-DD` and the date **must be today** (rule just shipped: past dates are super-admin corrections). The picker must therefore be read-only-today or owner-only. |
| KG field with unit suffix; Price per KG with `Rs / KG` suffix | ⚠️ Same maths (`amount = quantity × price` via `totalInput`) but plain text fields. Unit must switch to *cylinders* for LPG. |
| Live "Total Amount" tinted card | ⚠️ We already compute and show a live total; only the styling differs. |
| Full-width coloured SAVE button | ⚠️ Styling only (green sale, blue purchase, orange expense). |
| "Last 5 Sales / Purchases" table + **View All** | ❌ **No backend endpoint**: operations can only be listed per day (`/admin/ledger/{day_id}/operations`) or expenses-only (`/admin/expenses`). |
| Missing from mockup, present in ours: bird **count** (chicken), sale **channel** (LPG), **supplier** (purchase), note field | ⚠️ Keep them (they drive `stock_count`, the LPG channel requirement and supplier bills); suggest collapsing them under an optional "More details" row to keep the clean look. |

### Screen 4 — Expenses form
| Element | Status |
|---|---|
| Category dropdown, Description, Amount | ✅ `category` + `note` + `amount` already exist — and our `EXPENSE_CATEGORIES` list is exactly the mockup's dropdown (Worker Salary, Electricity, Ice / Cold, Transport, Feed, Rent, Other). |
| Recent Expenses table (Date · Category · Amount) | ❌ same missing list endpoint as above (`/admin/expenses` exists but is date-range + paginated with no category-name display today). |

### Screen 5 — Monthly Report
| Element | Status |
|---|---|
| Month selector (`< September 2026 >`) | ⚠️ Our reports use From/To text fields + Daily/Weekly/Monthly chips. Month paging is new UI. |
| 2×2 cards: Total Purchase (450 KG / Rs 157,500), Total Sale (400 KG / Rs 180,000), Gross Profit, Total Expenses | ❌ `/admin/reports` returns revenue, cost+expenses and profit per business — **no quantities (KG)**, no purchase value, no day series. |
| NET PROFIT card + expand arrow | ⚠️ Covered by `total_profit`, different presentation; semantics differ (§4.1). |
| Two-series line chart (Sale vs Purchase, 0–30 days, y in "K") | ❌ Reports have no per-day series. Our current chart is a per-business horizontal bar (positive profit only). |

### Screen 6 — Daily Summary
| Element | Status |
|---|---|
| Date stepper `< 22-09-2026 >` | ✅ `GET /admin/businesses/{id}/summary?on=DATE` already exists and was built for exactly this. |
| Sales / Purchases / Expenses tinted rows with quantities | ✅ `purchased_quantity`, `sold_quantity`, `day.expenses`, `byproduct_revenue`, retail/commercial — all present. |
| "Net Profit (Today)" in red when negative | ⚠️ We already colour losses red; the **formula** is the open question (§4.1). |
| "Today's Notes" card | ❌ `daily_ledgers` has no note column. Operations each carry a note; simplest v1 is to show the day's operation notes instead of a new field. |

### Screen 7 — All Transactions
| Element | Status |
|---|---|
| Sale / Purchase / Expense segmented tabs | ❌ needs the kind-filtered operations endpoint. |
| Date range chip `01-09-2026 — 30-09-2026` + filter icon | ❌ same endpoint; our Expenses screen already has range filters to reuse. |
| Table: Date · Type (coloured chip) · KG · Amount, "-" for expenses | ❌ same endpoint; `EXPENSE` rows have no quantity, so the "-" is correct. |
| Bottom nav | ⚠️ This is the screen our bottom-bar "Transactions" item should open. Today the ledger screen lists *days*, not operations. |

### Screen 8 — Chicken Shop (settings/info)
| Element | Status |
|---|---|
| Banner with shop logo + name + location, gear icon | ⚠️ Name/icon exist; location and a gear route to Settings are new (small). |
| Shop Info | ❌ no address/phone/tagline fields on `businesses`. |
| Item Price (Optional) | ❌ no `default_price`; would be a new additive column + owner-only PATCH. |
| Set Target | ❌ no target field (daily/monthly); new additive column. |
| Backup Data | ❌ nothing exists. Cheapest useful version: client-side CSV export/share of the visible table, or an owner-only `GET /admin/export/operations.csv`. |
| Help & Support | ⚠️ static client content. |
| Bottom promo card ("Clean Shop / Happy Customers / More Profit") | ⚠️ static asset, fits the existing brand strip. |

## 3. Design language to adopt (cross-cutting)

- **Module accents:** green = sale/primary, blue = purchase, orange = expenses, purple =
  reports (purple is not in our palette yet — needs `BrandPurple ≈ #7C4DBE` + tint).
- **Tinted summary rows** (~10 % colour over white) for Sales/Purchases/Expenses/Net
  profit; white cards on a very light background; 14–16 dp radii; soft shadows.
- **Leading icon in a coloured rounded square**, label outside the input, unit suffix
  inside the field (`KG`, `Rs / KG`).
- **Tables:** header row with muted small caps, right-aligned numbers, "-" for
  not-applicable quantities, coloured type chips.
- **Money display:** `Rs 3,500` — whole rupees, no decimals (ours prints
  `Rs. 3,500.00` from paisa). Recommend: show decimals only when paisa ≠ 0, and keep
  paisa on the wire.
- **Dates:** display `DD-MM-YYYY` (mockup) while the API keeps ISO `YYYY-MM-DD`.
- **Negative numbers:** red (we already do).
- **Bottom bar** with an active-item pill and a "More" item that opens the drawer.
- **RTL:** tables/chips must mirror in Urdu; numbers and dates stay LTR.

## 4. Conflicts and decisions

### 4.1 Profit formula — the important one
The mockup computes **net profit = sales − purchases − expenses** (cash basis, using
this period's purchases). Our backend computes **net profit = revenue − COGS −
expenses**, where COGS is the weighted-average cost of the goods actually sold
(`Operation.cost`), and purchases only move stock. The two differ whenever stock
carries across days — which is normal.

Our figure is the one that is **settled to investors** and is stored per day
(`daily_ledgers.net_profit`) and per batch, so it must not be replaced. Options:

1. Keep COGS profit as the headline (recommended) and label the mockup's number as a
   secondary line, e.g. *"Cash movement today (sales − purchases − expenses)"*.
2. Show "Gross profit = sales − COGS" as the mockup's "Gross Profit" card.
3. Switch the app to cash-basis profit — rejected: it would disagree with settlements,
   reports and the append-only journal.

The Sale/Purchase/Expense **amounts and the total = KG × price** in the mockup do match
our implementation exactly, so only the profit line is affected.

### 4.2 Date field vs. the close rule
The mockup's calendar picker invites backdating, but new records are today-only and
past dates are super-admin corrections. Safe choices: (a) read-only today field with a
calendar icon, (b) a picker where only today is selectable, or (c) a deliberate backend
change to let the owner backdate (stock/cost effects would apply as of *today*).
Recommendation: (a) for managers, (c) only if the owner explicitly wants backdating.

### 4.3 Bottom navigation
Adopting Home · Transactions · Reports · More turns "Sales"/"Expenses" from shortcuts
into screens and moves the drawer behind "More". This is the largest navigation change
in the mockup and affects muscle memory — worth confirming.

### 4.4 Currency, dates and units
Paisa → whole-rupee display, `DD-MM-YYYY`, and unit switching (KG / cylinders / birds)
must be explicit, otherwise a chicken mockup leaks into LPG and broiler screens.

## 5. Backend work implied

| # | Change | Serves | Size |
|---|---|---|---|
| 1 | `GET /admin/operations?business_id&kind&start&end&offset&limit` (row = date, kind, quantity, amount, cost, category, channel, note) + index on `operations.day_id` | Last 5 Sales/Purchases, Recent Expenses, All Transactions, View All | M |
| 2 | Extend `GET /admin/reports` with `series[]` per day (quantities, revenue, cost, expenses, profit) and quantity/purchase-value totals | Monthly Report cards + line chart | M |
| 3 | Day-level note (additive `daily_ledgers.note` + owner PATCH) — or skip and show operation notes | Daily Summary "Today's Notes" | S |
| 4 | Business profile fields: `location`, `tagline`, `default_price`, `target_amount` (additive columns + owner-only PATCH) | Screen 8 rows, dashboard location pill, prefilled price | M |
| 5 | Optional CSV export endpoint (owner only) for Backup Data | Screen 8 | S |

No change is needed to settlement, close or correction logic for any of this.

## 6. Suggested order

1. **Design tokens + shell (no backend):** purple accent, tinted cards, table and chip
   components, `Rs`/date formatting, module-coloured app bars, bottom nav
   Home · Transactions · Reports · More, restyled SALE/PURCHASE/EXPENSE forms with the
   live total card, and the **Daily Summary** screen (already fully supported today,
   including the close rule and the settled/variance lines).
2. **Lists (#1 above):** Last 5 tables on the forms, All Transactions with tabs + range,
   View All — this is what makes the mockup feel complete.
3. **Monthly Report (#2):** month pager, four stat cards, Net Profit card and the
   two-series line chart, with the COGS-vs-cash label from §4.1.
4. **Shop screen (#3–#5):** fields first, then Item Price/Set Target, then Backup Data;
   Help & Support is static.
5. **Dashboard polish:** hero banner via the existing `app_icons` slots, location pill,
   tile sub-labels.

## 7. Risks

- Building the mockup's profit maths as drawn would put the app at odds with investor
  settlements — decide §4.1 before touching the summary screens.
- Two "Sales/Expenses" navigation models would confuse managers; migrate the bottom bar
  in one release, with a note in the release checklist.
- The operations feed is the first business-wide, kind-filtered list; add the
  `operations.day_id` index (currently only the FK) to keep it fast as history grows.
- LPG/Broiler still need their own screens (cylinders, sale channel, batches) — the
  mockup covers none of that.
