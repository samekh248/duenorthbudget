# Contract: Month control

The control is the only way to change the panorama month. It does not open a route. It does not read the network.

## When it shows

- A budget is open, and the route is Home, `GroupCategories`, `CategoryBudget`, `MoveCategory`, or `HoldMonth`.
- Otherwise it is not in the tree. With no budget open, settings is still on the bar. See [screen-bar.md](screen-bar.md).

## What it shows

- The label is `monthLabel` of the painted month: lowercase month name, a space, and the year (`october 2026`). Test tag `month-label`.
- Previous, test tag `month-previous`, selects the prior month in the window. Hidden on the oldest month.
- Next, test tag `month-next`, selects the following month. Hidden on the newest month.
- Current calendar month, test tag `month-current`, selects today's calendar month in one action. Hidden when the painted month is already that month.
- The label toggles the list, test tag `month-list`. The list is every month in the window, oldest first, each tagged `month-option-YYYY-MM`. Choosing one selects it and hides the list.

Icons are drawn glyphs (chevron left, chevron right, a calendar mark). They are not word buttons and not empty circles.

## Selection

- The window is the current calendar month minus 24 months through plus 24 months, inclusive.
- A cache hit while nothing is scrolling replaces the budget header amount, the category rows, and the review with that month's snapshot before the click handler returns.
- The budget section and the review show the same month after every selection.
- A selection while a list or the panorama is scrolling does not replace the rows under the finger. The new month is painted when the gesture ends.
- A month with no assignments and no spending shows the month name and zero amounts. No error string.
- The budget name, if long, is one ellipsized line and does not overlap `month-label` or `to-budget`.

## Editors

- Opening `GroupCategories`, `CategoryBudget`, `MoveCategory`, or `HoldMonth` pins the painted month onto that route.
- Later selections change the control and, on return, the panorama. They do not change the pinned month. Writes from that route use the pinned month.

## Gone from the lists

- `BudgetSection` does not contain previous month, next month, hold, or manage as word buttons.
- The review section does not contain its own previous-month button.

## Offline

- Selection calls `readShell` and `readMonthReview` on the local file only.
- It does not call sync.
