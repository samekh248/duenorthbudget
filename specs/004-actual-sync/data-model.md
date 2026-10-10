# Data Model: Actual Sync

## Server connection

Stored outside the budget file.

| Field | Where | Rules |
|---|---|---|
| address | `phone.json` `serverAddress` | Saved only after a successful sign-in. Not a secret. |
| token | Android keystore secret store, key `actual.token` | Cleared on sign-out and when the server rejects the token. Never written into a budget directory. |
| remote list | Memory, from `list-user-files` | Only the server just signed in to. Cleared on sign-out. `deleted` files are omitted. |

A wrong address or a rejected sign-in does not create, replace, or delete a budget directory.

## Budget copy

The spec 001 directory. Sync adds three tables to `db.sqlite`.

### messages

| Column | Rules |
|---|---|
| timestamp | Primary key. Actual HLC text. Inserting the same timestamp twice changes nothing. |
| dataset, row, column, value | One cell write. `value` is the decimal text of a number or the text itself. |
| pending | `1` until a sync accepts it. A pending cell is not overwritten by a remote value. |

### conflicts

| Column | Rules |
|---|---|
| id | `dataset\|row\|column` |
| local_value, remote_value | Both kept. The row is not deleted when the person sees it. |
| local_timestamp, remote_timestamp | The two messages. Neither message is removed. |
| seen | Set when the conflict screen is shown. The values stay. |

### sync_state

| id | value |
|---|---|
| cloudFileId | Server file id. Empty for a phone-only budget. |
| groupId | Actual group id returned at upload or list time. |
| keyId | Budget encryption key id, or empty. |
| encrypted | `1` when the file needs the budget password. |
| lastRemote | Last envelope timestamp incorporated from the server. The next `since`. |
| clock | Last HLC this phone sent for this file, so a restart does not reuse it. |
| lastSuccessEpoch | Epoch millis of the last sync that returned. |
| lastFailure | Empty, `failed`, or `auth`. `auth` is shown as "sign in again" and wins over an older success. |

Opening a server file uses the server file id as the directory name. A second local budget is a different directory and is not written.

## Encryption password

| Field | Where | Rules |
|---|---|---|
| password | Not stored | Checked against the server's `test` ciphertext. Not sent to the server. Not written into metadata, notes, or category names. |
| AES key | Memory while unlocked. Keystore only when "ask each time" is off. | Dropped from memory on `ON_STOP` when this file is in `phone.json` `askEachTime`. |
| askEachTime | `phone.json` list of budget ids | Default on when the person unlocks an encrypted file. |

Until the key is in memory, the shell passed to the panorama is null and the password screen is the only route.

## Conflict

See `conflicts` above. The on-screen cell stays at the pending local value. The conflict screen shows both formatted amounts.

## Local edits that sync

An assign writes `zero_budgets` with id `{YYYYMM}-{categoryId}` and one pending message per changed column (`month`, `category`, `amount`, `carryover`).

An added transaction writes an on-budget account if needed, a payee, and a transaction with `description` set to the payee id (the column spec 001's inbox already joins). Each column is a pending message.

## State

- `DownloadTracker` remembers bytes received. A failure sets the count back to zero. A later progress callback while the same download is healthy never decreases it.
- `RefreshGate` is unchanged. Sync publishes a new shell through it, so a gesture keeps the visible rows.
- `RowArrivals` treats the first composition as already seen, then fades ids that appear later.
