# Contract: Accounts

Book: `AccountsBook`. Screens: `AccountScreens.kt`.

## Create

Input: name (non-blank), on/off budget, balance (may be 0).

Writes, in one undo step:

1. `accounts` row: `name`, `offbudget`, `closed = 0`, `sort_order` after the last account.
2. Transfer payee for the account (`payees.transfer_acct`).
3. When balance ≠ 0: a transaction dated today, payee "Starting Balance", `starting_balance_flag = 1`, and, on budget, the starting-balances income category (created like Actual when absent). Off budget: no category.

## Edit

| Action | Writes |
|---|---|
| rename | `accounts.name` |
| note | `notes` row `account-<id>`, column `note` |
| reorder | `accounts.sort_order` midpoint between new neighbours |

## Close

| Case | Required | Writes |
|---|---|---|
| balance = 0 | — | `closed = 1` |
| balance ≠ 0, on → on or off → any | destination account | transfer transaction today for the balance, then `closed = 1` |
| balance ≠ 0, on → off | destination account and category | as above, the on-budget side carries the category |
| forced | separate confirm | transfer partners unlinked and set to an unknown payee; account and its transactions `tombstone = 1` |

Nothing is written until the person confirms the close screen.

## List

Closed accounts are absent unless `phone.json` `showClosedAccounts` is true (menu `show-closed`). Reopen writes `closed = 0` and the account returns to the list.

## Tags

`accounts-screen`, `account-add`, `account-edit`, `account-close`, `account-close-destination`, `account-close-category`, `account-force-close`, `account-reopen`.
