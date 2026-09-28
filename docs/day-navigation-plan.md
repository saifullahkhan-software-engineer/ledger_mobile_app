# Day navigation plan — previous / next day sales, closed days and future dates

## 1. Answer: what exists today, what does not

**Implemented**
- `GET /admin/businesses/{id}/summary?on=DATE` accepts **any** date and returns that date's
  sales/purchase/expense totals (verified: past, today and future all return HTTP 200).
- `GET /admin/operations?business_id&start=DATE&end=DATE` returns the individual
  transactions of one date (added last change).
- `GET /admin/ledger/{day_id}` + `GET /admin/ledger/{day_id}/operations` return one day with
  `settled_net_profit`/`variance` and its transactions.
- The app has a **"Days" tab** in Transaction history listing every day that has records
  (tap → that day's detail + transactions, works for CLOSED days).
- Auto-close already settles a date when it ends, so past days are CLOSED, not stuck OPEN.

**Missing (this is what the question is about)**
- There is **no previous/next stepper** anywhere, and no date picker, so browsing back
  through days means going to the Days tab and loading again.
- The day screen is **`day_id`-addressed**, not date-addressed: a date with no records
  (or a future date) has no row in `daily_ledgers`, so it cannot be opened at all.
- The summary response carries **no `settled_net_profit`/`variance`**, no
  `is_today`/`is_future`, and no earliest date — so a stepper cannot label or bound itself.
- **Future dates are silently accepted**: `on=tomorrow` returns `day: null` with zeros and
  no signal that the date has not started (nothing is created — the endpoint is read-only).

Verified behaviour, one business with a closed day on 2026-09-27:

| `?on=` | HTTP | `day` | meaning |
|---|---|---|---|
| 2026-09-27 | 200 | status `CLOSED`, revenue 25000, settled fields **absent** | past, settled by the auto-close |
| 2026-09-28 | 200 | status `OPEN` | today |
| 2026-09-29 | 200 | `null`, zeros | **future — indistinguishable from "no records"** |

## 2. Design

### 2.1 Make the day view date-addressed
A date always exists; a `daily_ledgers` row may not. So the day screen should be keyed by
**date**, not by day id:

- state: `dayDate: String` (replaces `day: Day?` as the key; keep the loaded `Day` for display)
- load, in parallel: `summary(business_id, on=dayDate)` + `operationsFeed(business_id, start=dayDate, end=dayDate)`
- keep `day.id` from the summary response so the existing owner-correction flow
  (`PATCH /admin/operations/{id}`, "Edit transaction") keeps working on closed dates.

This one change is what makes previous/next possible at all, and it also makes deep links
work: History row → its date, Days tab → that date, business screen → today.

### 2.2 Backend addition (small, additive — one endpoint)
Extend the existing `business_summary` response instead of adding routes:

```jsonc
{
  "business": {...}, "date": "2026-09-27",
  "relation": "PAST",            // PAST | TODAY | FUTURE  (server decides, Asia/Karachi)
  "day": { ..., "status": "CLOSED" },
  "settled_net_profit": 25000,   // reuse day_payloads(), null when there is no settlement
  "variance": 0,
  "bounds": { "first_date": "2026-09-01", "last_date": "2026-09-28" },  // earliest day with records, and today
  "purchased_quantity": 40, "sold_quantity": 12, ...   // unchanged
}
```
- `first_date` = `min(Day.date)` for the business (served by the existing unique
  `(business_id, date)` index); `last_date` = today. `null` `first_date` = no records yet.
- `relation` is computed server-side so the app never guesses "today" from the device clock.
- Future dates stay HTTP 200 with `relation: "FUTURE"` and `day: null` (a well-defined empty
  state beats an error for a stepper UI). Alternative if you prefer hard blocking: return 422
  for `on > today`; the stepper then greys the arrow and never calls it.
- No new permissions, no schema change, no settlement/close logic touched.

### 2.3 Client
- **Header stepper:** `‹  27-09-2026  ›` — `‹`/`›` shift the date by one day; tapping the
  date opens a Material 3 date picker (bounds as above), so a jump to any date is one tap.
- Quick chips above the transactions: **Today · Yesterday · Pick a date** (Today/Yesterday
  are by far the most used and match how the shop actually works).
- Status banner, one line, always explicit:
  - `OPEN` (today): entries allowed, "Close day" button, "closes automatically at midnight".
  - `CLOSED` (past): "Settled with investors: Rs 25,000 · difference after corrections: Rs 0",
    owner still sees "Edit transaction" on each row.
  - `FUTURE`: "This date has not started. Sales and purchases can only be recorded on the day."
  - `PAST` with no day row (a gap — shop was closed): "No records for this date."
- Transactions of that date listed under the summary (the same `TransactionRow` used by
  history), so "sales of a closed day" are visible without opening the Days tab.
- Rapid arrow taps: cancel the in-flight read (`readJob` already does this) and let the last
  tap win; no debounce needed beyond that.
- Urdu labels for the new strings; «» arrows are direction-neutral, dates stay LTR.

### 2.4 What each state does

| State | Summary/transactions | Entry (add sale/purchase/expense) | Close day | Owner correction |
|---|---|---|---|---|
| Today, OPEN | shown live | allowed | allowed (auto at midnight anyway) | allowed |
| Past, CLOSED | shown incl. settled + variance | not allowed (existing rule) | n/a | allowed |
| Past, no records | empty state | not allowed | n/a | n/a |
| Future | empty state, labelled | not allowed | n/a | n/a |

## 3. Broiler (worth deciding separately)
Broiler businesses have **no `daily_ledgers`**; their dated records are `batch_logs`
(feed/mortality/expense per date) and the state lives on the batch. So a day stepper would
show an empty summary for every date. Options:
1. Skip the stepper for broiler and keep the Batches screen (smallest change),
2. Extend `business_summary` to also return the batch logs of `on` and label the screen
   "Farm log — 27-09-2026" (medium; makes broiler consistent with the rest),
3. Do nothing for broiler now.

## 4. Edge cases
- **Midnight rollover while the screen is open:** on resume, re-fetch; yesterday flips to
  CLOSED (auto-close), today becomes the new date. The server's `relation` keeps this right
  even if the phone clock/timezone is wrong.
- **Gaps:** days without records legitimately do not exist (shop closed). Stepping into a gap
  shows the empty state instead of stopping the stepper.
- **New business:** `first_date` null → both arrows disabled, "No records yet".
- **Stepping past the edges:** `‹` disabled at `first_date`, `›` disabled at today (pending
  decision below) — the app never asks for a date outside the bounds.
- **Corrections:** the settled/variance line must come from the summary so a corrected closed
  date reads correctly without opening the day list.
- **Consistency:** the numbers on the day screen must equal the `Days` tab and Reports for the
  same date — same `daily_ledgers` row, so yes by construction.

## 5. Work plan
1. **Backend (S):** `relation`, `settled_net_profit`, `variance`, `bounds` in
   `business_summary`; tests for past today future, no-records and bounds; regenerate
   `openapi.json`. No migration.
2. **Client (M):** date-addressed `Page.DAY`, stepper + date picker + quick chips, status
   banners, per-date transactions, wire entry points (Days tab, History row, business screen),
   Urdu strings.
3. **Docs (S):** README/API table rows for the extended summary + the new navigation rules.
4. **Optional follow-up:** broiler day view (decision 3 below).

## 6. Decisions needed
1. **Next arrow at today:** disabled (recommended) / allowed into future with the "not started"
   empty state / blocked with an error.
2. **How far back:** stop at the first recorded date (recommended) / keep stepping back
   indefinitely through empty days / fixed window (e.g. last 365 days).
3. **Broiler:** skip the stepper (recommended for now) / show that date's batch logs / out of scope.
