# Contract: Budget preferences and recovery

Applies to the open budget's `db.sqlite` `preferences` table and sync state. Theme and accent are not in this contract; they stay in `phone.json`.

## Formatting keys

| id | Allowed values (Actual strings) | Default when absent |
|---|---|---|
| dateFormat | `MM/dd/yyyy`, `dd/MM/yyyy`, `yyyy-MM-dd`, `MM.dd.yyyy`, `dd.MM.yyyy` | `MM/dd/yyyy` |
| numberFormat | Actual's number-format enum strings (comma-dot, dot-comma, space-dot, comma-dot-without-commas as shipped by Actual) | comma thousands, dot decimals |
| hideFraction | `true`, `false` | `false` |
| firstDayOfWeekIdx | `0` … `6` (0 = Sunday) | `0` |

Write path: `INSERT OR REPLACE INTO preferences (id, value)`. When `sync_state.cloudFileId` is set, also append pending messages for dataset `preferences`, row = preference id, column `value`.

Read path: shell, register, and review formatters take these prefs into account so the next date and amount on screen use the new values in the same UI update that records the tap.

## Method and experimental

| id | Rules |
|---|---|
| budgetType | Shown as envelope or tracking. Settings has no write control. |
| flags.experimental | `true` / `false`. Absent means off. Toggle does not by itself change a budgeted amount. |

## Ids

Settings may show:

- Budget id: `metadata.json` `id` (directory name).
- Sync id: `sync_state.cloudFileId` when non-empty; otherwise omitted.

## Export

Zip bytes:

```text
metadata.json
db.sqlite
```

Produced from the open directory on the phone. Share/save file name uses only the display name (sanitized). Works with the network off.

## Recovery (confirmed)

| Action | Local effect after confirm |
|---|---|
| Rebuild calculated totals | Drop in-memory month cache; delete on-disk cache tables if the schema has them; next shell read matches transactions. |
| Repair split transactions | Fix inconsistent split parents/children (orphans) without changing unrelated transactions. |
| Reset sync | Delete `messages` rows; reset clock / `lastRemote` (and related sync clock fields); keep `cloudFileId` / `groupId` / accounts / transactions. |

No action runs before confirm. Rebuild and repair do not require the network.

## Encryption password

Set, change, and remove use `BudgetCrypto` and the secret store rules from spec 004:

- Password is never stored as plaintext.
- Wrong password does not reveal amounts.
- Password does not appear in budget name, notes, categories, or export file name.
