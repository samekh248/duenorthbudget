# Data Model: Metro Budget Shell

## Budget

The one open file. Stored as a directory.

| Field | Rules |
|---|---|
| id | UUID, directory name, also `metadata.json` `id` |
| budgetName | Required after trim. A blank name does not create a directory. Stored in `metadata.json`, not rewritten when another file is opened. |
| currency | ISO code from the shell's currency list, chosen at creation. Decimal places come from that list (JPY and KRW have 0). Stored as `preferences.currency`. Not changed by this spec. |
| budgetType | `envelope` (default) or `tracking`. Stored as `preferences.budgetType`. The shell never converts one into the other. |
| month | The phone's current calendar month. Not stored. |

## Category group

| Field | Rules |
|---|---|
| id, name | From `category_groups`. Tombstoned groups are omitted. |
| is_income | Income groups are not rows. Their categories feed to-budget. |
| hidden | A hidden expense group is still a row. Its categories still count. |
| sort_order | Rows sort by `sort_order`, then id. |
| available | Sum of expense-category leftovers in the group for the open month. See [budget-file.md](contracts/budget-file.md). |

## Category

Read-only here. `categories.cat_group` points at the group. Tombstoned categories are omitted from totals. Hidden categories are omitted from nothing: they stay inside the group total. `is_income` selects the income side of the formula.

## Assignment

One row in `zero_budgets` (envelope) or `reflect_budgets` (tracking): `month` as `YYYYMM`, `category`, `amount` in minor units, `carryover` as 0 or 1. Missing row means amount 0 and carryover off.

## Buffer

`zero_budget_months.id` is `YYYYMM`, `buffered` is the hold-for-next-month amount. Envelope only. Missing row means 0.

## Account

| Field | Rules |
|---|---|
| name, offbudget, closed | Every non-tombstoned account, including closed. On-budget rows are grouped above off-budget rows. |
| balance | Sum of alive non-parent transactions on `acct`, in minor units. |

## Inbox transaction

A non-parent, non-tombstoned transaction on an on-budget account with `category` null and `transferred_id` null. A child of a tombstoned parent is omitted. Ordered by `date` descending, then `sort_order` descending, then id. Payee is the payee name, or "no payee" when missing. Amount is the stored integer.

## Appearance

Stored in `phone.json` beside the budget directories, never inside a budget file.

| Field | Rules |
|---|---|
| openBudgetId | Last opened budget. Launch opens this id when that directory still exists. |
| themeMode | `system`, `light`, or `dark`. `system` follows the phone. |
| accent | One of the 22 accent ids. Unknown values fall back to magenta. |

## Validation

- Create with a blank trimmed name: reject with "enter a name", create no directory.
- Create with a currency code outside the list: reject with "choose a currency".
- Switch: update `openBudgetId` only. Neither sqlite file is written.

## State

- `RefreshGate` holds the shell the screen is showing. While a panorama or list gesture is active, a newer shell is pending and the visible shell stays put. The pending shell replaces it when the gesture ends.
