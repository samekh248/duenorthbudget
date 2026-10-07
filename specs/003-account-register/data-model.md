# Data Model: Account Register

Amounts are minor units. Dates are `YYYYMMDD`. Months are `YYYYMM` (`date / 100`). Alive still means `tombstone = 0`, `isParent = 0`, and not a child of a tombstoned parent.

## Account

| Field | Rules |
|---|---|
| type | `accounts.type`. Missing column is added as `checking`. A card is exactly `credit`. |
| offbudget | Unchanged from spec 001. Off-budget balances count. Off-budget amounts do not enter category spent or to-budget. |
| balance | Sum of alive transactions on `acct`. |

## Card payment link

Preference id `ccPayment:<accountId>`, value = category id. Absent or blank means no payment move. The category must be a living expense category or the move is skipped. Envelope files only.

## Payee

| Field | Rules |
|---|---|
| name | Required on a hand-entered transaction after trim. Blank is refused with "enter a payee". An existing non-transfer payee with the same name, ignoring case, is reused. |
| transfer_acct | Set on the payee that represents an account in a transfer. The row in account A uses the payee whose `transfer_acct` is account B. |

## Transaction

| Field | Rules |
|---|---|
| id | Primary key. The entry form generates it once. A second save with the same id updates that row. |
| acct | Required. Changing it moves the balance. A split's children follow the parent account and date. |
| date | Required, a real calendar date. Spent lands in `date / 100`, including a future month. |
| amount | Non-zero. Zero is refused with "amount cannot be zero". A missing or unparseable amount is "enter an amount". Expense is negative. Income is positive. |
| description | Payee id. The register shows the payee name, or "no payee". |
| notes | Optional. A note-only edit does not change balances. |
| category | Optional on an on-budget non-transfer. Null and not a transfer: the row is in the inbox. Set, change, or clear updates both categories' spent. |
| reconciled | Stored. Edit, delete, split, unsplit, categorize, or transfer-update of a reconciled row (or its transfer partner) is refused until the caller confirms. The flag stays set. |
| cleared | New hand entry is cleared (`1`), matching the table default. |
| sort_order | New rows use max + 1. Newest-first order is date, then sort_order, then id, all descending. |

## Split

The parent stays one register row. `isParent = 1` and `category` null. Two or more children hold `category` and `amount`. The children sum to the parent amount or the save is refused with "split must add up" and nothing is written. Each child needs a category ("choose a category") and a non-zero amount. Removing the split tombstones the children and puts the parent amount back on the first child's category as a normal row.

## Transfer

Two rows, opposite amounts, `transferred_id` linked, categories null when both accounts are on-budget or both are off-budget. No category is required in that case, and to-budget does not change. When exactly one account is off-budget, the on-budget row holds the category and the off-budget row does not. A missing category then is "choose a category". Deleting either row tombstones both. Amount edits mirror. Notes are copied to both sides. Dates stay independent.

## Register page

A read model, not a table: the account, its balance, currency, the visible rows (parents and standalone rows, newest first, children nested), the category list, and the other accounts. Payee filter does not remove rows from the file.

## Validation messages

| Case | Message |
|---|---|
| Blank payee | enter a payee |
| Missing or unparseable amount | enter an amount |
| Zero amount | amount cannot be zero |
| Bad date | enter a date |
| Missing category where one is required | choose a category |
| Split sum does not match | split must add up |
| Reconciled row | this reconciliation will no longer match |
