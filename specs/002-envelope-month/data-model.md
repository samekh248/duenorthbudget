# Data Model: Envelope Month

Amounts are integer minor units. Months are Actual integers `YYYYMM`.

## BudgetBook

The open file in memory.

| Field | Rule |
|---|---|
| id, name | From `metadata.json`. Edits do not rename the file. |
| mode | `envelope` or `tracking` from `preferences.budgetType`. No edit writes this preference. |
| currency | From `preferences.currency`. Unchanged here. |
| groups | Alive and tombstoned category groups. |
| categories | Alive and tombstoned categories. |
| spent | Sum of alive categorized transactions by `(month, category)`. |
| categorized | Category ids that have at least one alive transaction. |
| assignments | `zero_budgets` or `reflect_budgets` rows. A missing row means amount 0 and carryover off. |
| buffers | `zero_budget_months.buffered` for envelope files. Tracking ignores this map. |
| accounts, inbox | Unchanged by this spec. Carried so the panorama still paints. |

## GroupRecord

| Field | Rule |
|---|---|
| id | Stable text id. New ids are random UUIDs. |
| name | Trimmed. Blank is refused with "enter a name". |
| isIncome | Preserved. New groups are expense groups. |
| hidden | Loaded and written. The month does not hide a group by this flag. Hiding a group means hiding its categories. |
| sortOrder | Number. Reorder swaps neighbors among expense groups and rewrites their order. |
| tombstone | 1 when deleted. Deleted groups stay out of the month. |

## CategoryRecord

| Field | Rule |
|---|---|
| id | Stable text id. |
| groupId | `categories.cat_group`. |
| name | Trimmed. Blank is refused with "enter a name". |
| isIncome | Preserved. New categories are expense categories. |
| hidden | When 1, the category is absent from the month list and from the group available total. Amounts remain on the row. |
| sortOrder | Order inside the group. |
| tombstone | 1 when deleted. |

## Assignment

| Field | Rule |
|---|---|
| month, category | Identity. The table has no unique constraint, so a save selects then updates or inserts. |
| amount | Budgeted minor units for that month. |
| carryover | Rollover-overspending for that month. It changes how *the next* month treats this month's leftover. |

Rows with amount 0 and carryover off are not stored.

## Hold

| Field | Rule |
|---|---|
| month | `zero_budget_months.id` |
| buffered | Minor units held for the following month. 0 removes the row. |

A new hold must be between 0 and `to-budget + current buffered` inclusive. Larger values are refused with "not enough to hold". Tracking files have no hold control.

## MonthPage

Derived, not stored.

| Field | Rule |
|---|---|
| shell | Panorama model for the viewed month. Group available sums visible expense categories only. |
| categories | Expense categories that are not tombstoned, including hidden ones, with budgeted, spent, available, and carryover for this month. |
| bufferedMinor | Hold on this month. |

Available is one number: the envelope or tracking leftover through this month. There is no per-month history on the row.

## Delete guard

Refuse when any of these are true for a category:

- an assignment with a non-zero amount or carryover on
- available other than 0 in any month from the first loaded month through the month after the last
- the category id is in `categorized`

A group is refused when any of its live categories would be refused. Empty groups and empty categories are tombstoned together with the group.

## State transitions

- Assign replaces that month's budgeted amount. To-budget and available move by the same delta.
- Move subtracts the amount from the source budgeted amount and adds it to the destination. To-budget is unchanged. The source's available must cover the amount.
- Cover is a move into the overspent category.
- Carryover on or off updates that month's flag only.
- Hide and unhide flip `hidden` and do not change amounts.
