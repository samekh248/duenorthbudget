# Data Model: Transaction Details Cleanup

Nothing new is stored. No table, column, preference or sync message changes. The entities below are in-memory shapes the screen uses; see [research.md](research.md) for why each exists.

## CategoryChoice (changed, `:core:budget` `Register.kt`)

| Field | Type | Notes |
|---|---|---|
| id | String | unchanged |
| name | String | unchanged |
| groupName | String | unchanged; list headings |
| income | Boolean | unchanged |
| **hidden** | Boolean | new; `c.hidden OR g.hidden`, default `false` so existing constructors compile |

Ordering is unchanged: group sort order, then category sort order (`RegisterBook.categories()`).

## CategoryOptions (new, `:app`)

Derived from `RegisterPage.categories` and the transaction's current category id; built once per form with `remember`.

| Field | Type | Notes |
|---|---|---|
| groups | List<Pair<String, List<CategoryChoice>>> | visible categories by `groupName`, in order; a hidden category appears only when it is `current` |
| current | String? | `null` means "no category" |
| count | Int | 1 ("no category") + categories in `groups`; decides in-place (≤ 5) versus full-screen (> 5) |

Rules: "no category" is always first (FR-014). Income categories stay in their own group, as Actual groups them.

## Transaction form state (changed, `:app` `TransactionForm`)

Still local `remember` state keyed by row id (research R1). One field changes type, two are new.

| Field | Type | Was | Notes |
|---|---|---|---|
| date | Int (`yyyyMMdd`) | String (ISO text) | seeded from `row.date` or today; only the picker changes it |
| payee | String | same | |
| amount | String | same | parsed on save by `MoneyParse` |
| expense | Boolean | same | drawn by `MetroSegmentedToggle` |
| categoryId | String? | same | |
| note | String | same | may contain `\n` |
| fieldError | Pair<TransactionField, String>? | `localError: String?` | which field a rejection belongs to (R13) |
| overlay | FormOverlay | new | `None`, `CategoryList`, `DatePicker(draft: Int)` |

`TransactionField` = `DATE`, `PAYEE`, `AMOUNT`, `CATEGORY`, `NOTE`.

### Overlay transitions

```text
None ──tap date box──────────────▶ DatePicker(draft = date)
DatePicker ──flick column────────▶ DatePicker(draft = clamped new value)
DatePicker ──done────────────────▶ None, date = draft
DatePicker ──cancel / back───────▶ None, date unchanged
None ──tap category (count > 5)──▶ CategoryList
CategoryList ──tap row───────────▶ None, categoryId = row
CategoryList ──back──────────────▶ None, categoryId unchanged
None ──tap category (count ≤ 5)──▶ None (combo box unfolds in place; its own open flag)
```

All other fields are untouched by every transition (FR-013).

## DatePick (new, `:core:budget` `Models.kt` next to `DateFormats`)

Pure functions, no state.

| Member | Signature | Rule |
|---|---|---|
| columns | `(pattern: String) -> List<DateColumn>` | order of `MONTH`, `DAY`, `YEAR` as they appear in the pattern (`yyyy-MM-dd` → year, month, day) |
| daysIn | `(year: Int, month: Int) -> Int` | 28–31, leap years by the Gregorian rule |
| withMonth / withDay / withYear | `(date: Int, value: Int) -> Int` | sets one part and clamps the day to `daysIn` (31 Jan → Feb gives 28 or 29) |
| yearRange | `(date: Int) -> IntRange` | `1900..2100`, widened to include `date`'s year |

## DateFormats (changed)

| Member | Notes |
|---|---|
| `withWeekday(date: Int, pattern: String): String` | new; `"Thu 10/01/2026"`; short English weekday, matching the app's English copy |
| `widestSample(pattern: String): String` | new; `"Wed "` + pattern with every letter as `0`, for the date box width (R7) |

## RegisterEntry (changed)

| Member | Notes |
|---|---|
| `transaction(id, accountId, date: Int, payee, amountText, decimals, expense, categoryId, note, force)` | new overload; same checks minus date parsing |
| `transaction(..., dateText: String, ...)` | unchanged; still used by other callers |

## registerDetail (changed, `:app`)

The note part replaces every run of whitespace that contains a line break with one space before joining, so a multiline note reads as one line under the payee. The stored note is unchanged.
