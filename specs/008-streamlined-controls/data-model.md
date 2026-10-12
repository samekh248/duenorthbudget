# Data Model: Streamlined Metro Controls

No sqlite tables or columns change. These are view-state types. Money fields stay the ones `MonthShell` and `MonthReviewPage` already have.

## Open month

| Field | Type | Rules |
|---|---|---|
| openMonth | `YearMonth` | The month the panorama is showing. Initial value is the current calendar month. |
| pinnedMonth | `YearMonth?` | Set when `GroupCategories`, `CategoryBudget`, `MoveCategory`, or `HoldMonth` is pushed. Equal to `openMonth` at that moment. Cleared when that route is popped. Editors read and write `pinnedMonth`. |

`openMonth` and the review month are the same value. There is no second review month.

## Month window

| Field | Type | Rules |
|---|---|---|
| anchor | `YearMonth` | The current calendar month when the window was built. |
| radius | Int | 24. |
| months | list of `YearMonth` | `anchor.minusMonths(24)` through `anchor.plusMonths(24)`, inclusive, oldest first. |

A month outside that list is not selectable. Previous on the oldest month is not shown. Next on the newest month is not shown.

## Month snapshot

| Field | Type | Rules |
|---|---|---|
| month | `YearMonth` | Key. |
| shell | `MonthShell` | From `BudgetLibrary.readShell` for the open budget. `shell.month` equals the key. |
| review | `MonthReviewPage` | From `readMonthReview` for the same budget and month. |

The cache is a map from month to snapshot for one budget id. Switching files drops the map. A snapshot whose header and groups are zero is valid. It is not an error.

## Painted month

| Field | Type | Rules |
|---|---|---|
| displayedMonth | `YearMonth` | What the control and the sections show. |
| pendingMonth | `YearMonth?` | Set when a selection happens while a panorama or list gesture is active. |
| shell | `MonthShell?` | The rows on screen. |
| review | `MonthReviewPage?` | The review on screen. |

Transitions:

1. Cache hit, no gesture: `displayedMonth`, `shell`, and `review` become the snapshot in the same update. `pendingMonth` is null.
2. Cache miss, no gesture: `displayedMonth` becomes the requested month immediately. `shell` and `review` stay until the read is applied, then they match that month. A newer selection discards an older read.
3. Gesture active: `pendingMonth` becomes the requested month. `displayedMonth`, `shell`, and `review` stay. When the gesture ends, rule 1 or 2 runs for `pendingMonth`.
4. Editor pinned: the editor's figures stay on `pinnedMonth` across all three rules. The control shows `displayedMonth`.

## Screen action

| Field | Type | Rules |
|---|---|---|
| id | String | Stable. Used in the test tag `app-bar-{id}`. |
| glyph | `AppGlyph` | A drawn icon. Not the ellipsis. |
| label | String | Shown under the icon when the bar is expanded, and in the menu for a secondary action. |
| primary | Boolean | True draws a round button. False is only in the ellipsis menu. |
| run | action | One call. The same call with the labels hidden or shown. |

The ellipsis is not a screen action. It only expands or collapses labels and the menu.

Save on a write form calls that form's existing write. Cancel pops the route and does not call the write. The pairs are in [contracts/screen-bar.md](contracts/screen-bar.md).

## Title lag

| Field | Type | Rules |
|---|---|---|
| pagePosition | Float | Unwrapped pager position, including the fractional offset. |
| sectionCount | Int | Panorama sections. |
| stepPx | Float | Width of the word "due" in the subtitle style. |
| motionEnabled | Boolean | False when the system animator scale is 0. |

The shift in pixels is defined in [contracts/panorama-title.md](contracts/panorama-title.md). It is not stored.

## Budget name on the budget section

`MonthShell.budgetName` is shown as one line, ellipsized, in its own row. It does not share a row with the month control or the header amount.
