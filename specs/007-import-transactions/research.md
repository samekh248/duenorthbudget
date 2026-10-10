# Research: Import Transactions

## File formats

Actual's desktop import accepts QIF, OFX, and CSV. Due North implements QIF (bank type) and a minimal OFX (`STMTTRN` blocks) plus comma-separated CSV with header row `date,payee,amount` or `Date,Payee,Amount`.

## Duplicate detection

Match order: same account and non-tombstone row with the same `financial_id` when present; otherwise same date, amount, and payee name (case-insensitive). Two identical new rows in one file both import when neither is already in the account.

## Bank fetch

Accounts linked on desktop store `account_sync_source` and `account_id`. Actual pulls via a SimpleFIN proxy POST with `X-ACTUAL-TOKEN`. The phone reuses the signed-in server address and token from sync settings, posting to `{server}/simplefin/transactions` with the external account id and start date. Unsupported providers return a plain error without offering setup.

## Import batch

After confirm, preference `dueNorthImportBatch` holds JSON `{ accountId, batchId, transactionIds[] }`. Review reads those ids in sort order; finishing clears the preference.
