# Data Model: Import Transactions

## Preview row

| Field | Meaning |
|---|---|
| date | Integer YYYYMMDD |
| payee | Display payee |
| amountMinor | Signed integer amount |
| financialId | External id when known |
| importedPayee | Raw bank payee text |
| status | `new` or `duplicate` |
| existingId | Account transaction id when duplicate |

## Import batch

Stored in `preferences.dueNorthImportBatch` as JSON. Cleared when review finishes.

## Bank connection (read-only)

From `accounts`: `account_sync_source`, `account_id`. No writes to create connections.
