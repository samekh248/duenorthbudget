# Contract: Register writes

The phone writes the open budget's `db.sqlite`. Column names stay Actual's. `dueNorthSchema` stays `1`.

## accounts.type

TEXT, default `checking`. `ActualSchema.ensure` adds the column when an older file lacks it. A credit card is `type = 'credit'`.

## Preference `ccPayment:<accountId>`

Value is the payment category id. Envelope projection only, and only when that category is a living expense category.

For each such card, add to that category's spent (the same map `ActualMonthMath` already consumes):

1. Negation of each alive, categorized, non-transfer expense-category amount on the card, in that transaction's month, except amounts already categorized to the payment category.
2. Negation of each alive card-side transfer amount whose other account is on-budget, in the card-side transaction's month.

Tracking budgets skip both steps. No extra transaction is inserted for the move.

## Spent

Category spent is the sum of alive non-parent amounts with a category whose account is missing or on-budget. Off-budget accounts are excluded. Parents are excluded. Children count.

## Save

`TransactionDraft.id` is the primary key.

- Insert when the id is new.
- Update when the id exists and is not tombstoned.
- A tombstoned id is left tombstoned.
- Payee is required after trim. Amount is required and non-zero. Date is a real `YYYYMMDD`.
- A split parent accepts payee, note, account, and date. An amount or category change on that path is refused with "split must add up".
- A transfer update mirrors the amount, copies the note, and keeps categories null unless exactly one side is off-budget.

## Split

`SplitPart.amountMinor` values sum to the parent amount. On success the previous children are tombstoned and the new children inserted in the same transaction. On failure the row is unchanged.

## Transfer

`TransferDraft.amountMinor` is a positive magnitude. The source row stores the negation. The other row's id is `<source id>-to`. A second save with the same source id updates the pair.

## Delete

Sets `tombstone = 1` on the row, its transfer partner, and its split children.

## Reconcile guard

When the row or its transfer partner has `reconciled = 1`, the call returns without writing until `force` is true. The message is "this reconciliation will no longer match".
