# Contract: Sync datasets and the write funnel

Applies to every write in spec 010. Extends `SyncSchema` from spec 004.

## Funnel

Every write calls `SyncSchema.recordLocal(session, clock, dataset, row, column, value)`. No book in this spec writes with plain `INSERT` or `UPDATE` on a synced table. `recordLocal` writes the cell, appends a pending message, and advances the clock in one transaction.

When an `EditJournal` step is open, `recordLocal` first reads the cell's current value (null when the row does not exist) and appends `(dataset, row, column, old, new)` to the step.

## Column map additions

| Dataset | Added columns |
|---|---|
| accounts | `account_id`, `bank`, `account_sync_source`, `balance_current`, `balance_available`, `balance_limit`, `last_sync` |
| notes | `note` |
| banks | `bank_id`, `name`, `tombstone` |
| rules | `stage`, `conditions_op`, `conditions`, `actions`, `tombstone` |
| schedules | `name`, `rule`, `active`, `completed`, `posts_transaction`, `tombstone` |
| schedules_next_date | `schedule_id`, `local_next_date`, `local_next_date_ts`, `base_next_date`, `base_next_date_ts`, `tombstone` |
| categories | `goal_def` |
| zero_budgets, reflect_budgets | `goal`, `long_goal` |

Numeric columns join the existing numeric set: `active`, `completed`, `posts_transaction`, `local_next_date`, `local_next_date_ts`, `base_next_date`, `base_next_date_ts`, `balance_current`, `balance_available`, `balance_limit`, `goal`, `long_goal`. JSON columns (`conditions`, `actions`, `goal_def`) are sent as their string.

`ensureRow` gains an `INSERT OR IGNORE` for `notes`, `banks`, `rules`, `schedules`, `schedules_next_date`.

## Upgrade backfill

Once per budget, guarded by `sync_state` id `parity-backfill`: for each live row in `rules`, `schedules`, `schedules_next_date` with no message in `messages` for that dataset and row, emit pending messages for every mapped column. Only when the file has sync state.

## Remote apply

`applyRemote` accepts the new datasets and columns. A remote `rules` change invalidates the rule cache. A remote change to a cell that is in an undo step does not edit the step; the conflict check at undo time handles it.
