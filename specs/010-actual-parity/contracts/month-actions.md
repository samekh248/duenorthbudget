# Contract: Month actions

Applies to the open month. Book: `MonthFillBook`. Screens: `MonthActionScreens.kt`.

## Breakdown

Envelope only. Opened from the to-budget figure on Home (tag `to-budget-figure`) or `•••` → `breakdown`. Screen tag `breakdown-screen`.

| Row tag | Value |
|---|---|
| `breakdown-income` | income this month |
| `breakdown-carried` | last month's to-budget + last month's held |
| `breakdown-overspent` | uncovered overspend from last month (≤ 0) |
| `breakdown-budgeted` | − total budgeted |
| `breakdown-held` | − held for next month |
| `breakdown-total` | equals the Home to-budget figure |

## Fills

| Menu id | Action | Confirm |
|---|---|---|
| `copy-last` | budgeted(m) = budgeted(m−1) for targets | when any target is non-zero in m |
| `average` | budgeted(m) = round(mean of budgeted over existing months among m−1…m−3) | when any target is non-zero in m |
| `zero` | budgeted(m) = 0 for every category | always |

Targets: visible categories whose group is visible. Envelope copy and average skip income categories. Tracking files write `reflect_budgets` and include income categories.

When no source month has a budget row, the result is "nothing to copy" and nothing is written. Each fill is one undo step.

## Transfer to to-budget

Envelope only. From category budget `•••` → `to-budget`. Amount ≤ category available, else refused with a reason. Writes `budgeted(m, cat) -= amount`. Optimistic update of the category and the header before the write.

## Auto-hold

Envelope, income categories only. From category budget `•••` → `auto-hold`, three choices:

| Choice | Writes `carryover` on income category budget rows |
|---|---|
| on | 1 for m … m+12 |
| off this month | 0 for m |
| off | 0 for m … m+12 |

A manual hold for a month (`zero_budget_months.buffered` ≠ 0) still wins for that month.

## Offline

Everything here finishes with the network off and paints before the finger lifts.
