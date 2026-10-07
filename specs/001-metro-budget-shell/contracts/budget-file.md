# Contract: Budget file

The phone reads and creates this shape. It is Actual's column names for the tables below, not a full Actual migration (`dueNorthSchema` = `1`).

## metadata.json

```json
{ "id": "uuid", "budgetName": "Home" }
```

## preferences

| id | value |
|---|---|
| budgetType | `envelope` or `tracking` |
| currency | ISO code, for example `USD` |
| dueNorthSchema | `1` |

## Amounts

Integers in minor units. Display divides by `10 ^ decimals` for that currency. A currency with no minor units (decimals 0) shows a whole number. Zero is `0`, never a placeholder. Negatives keep a leading minus.

## Dates

`transactions.date` is an integer `YYYYMMDD`. A budget month is `YYYYMM` (`date / 100`, and the `month` column on budget rows).

## Envelope figures

For each expense category, walking from the first month that has data through the open month (a month with no row is amount 0, carryover off, spent 0):

```text
carried = previous carryover ? previous leftover : max(previous leftover, 0)
leftover = budgeted + spent + carried
```

Spent is the sum of alive non-parent transaction amounts in that category and month.

```text
fromLast = previous to-budget + previous buffered
lastOver = sum over expense categories of (previous carryover ? 0 : min(previous leftover, 0))
totalBudgeted = -sum(budgeted this month)
buffered = manual buffered if it is not 0, else sum of income spent where that income category's carryover is set
to-budget = income + fromLast + lastOver + totalBudgeted - buffered
```

Income is the sum of alive non-parent amounts on income categories for the month. The group's available total is the sum of leftovers of its expense categories. The header label is "to budget".

## Tracking figures

No rollover. For the open month only, category balance = budgeted + spent. Group available is the sum of those balances. The header label is "balance" and the header amount is the sum of the group totals. `reflect_budgets` supplies budgeted amounts.

## Alive transaction

`tombstone = 0`, `isParent = 0`, and not a child whose parent is tombstoned.

## Inbox

Alive, `category` is null, `transferred_id` is null, account exists, account is on budget and not tombstoned. Newest first: `date DESC`, `sort_order DESC`, `id DESC`.
