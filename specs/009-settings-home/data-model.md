# Data Model: Settings and Instance

## Saved instance

An Actual server the phone can sync with. Stored in `phone.json`, never inside a budget directory.

| Field | Rules |
|---|---|
| id | UUID. Stable key for the secret-store token. |
| address | Normalized http(s) URL. Saved after a successful sign-in, or when the person adds a server and chooses it active offline (then status shows not reached). |
| label | Optional display name. Default is the host portion of `address`. |

| Phone field | Rules |
|---|---|
| instances | List of saved instances. Order is the order shown in settings. |
| activeInstanceId | Id of the active instance, or null when none. At most one active. |
| serverAddress | Legacy single address. On read, if set and `instances` is empty, migrate to one instance and clear this field on the next save. |

| Secret | Rules |
|---|---|
| `actual.token.<instanceId>` | Session token for that instance. Cleared on sign-out, remove, or auth rejection. Never written into a budget file or into export zip names. |

Sign-in failure does not add an instance to the active slot. Removing a non-active instance deletes its row and token only. Removing the active instance clears the token, clears `activeInstanceId`, and leaves local budgets.

## Phone preferences

Unchanged ownership from spec 001; editable from settings.

| Field | Where | Rules |
|---|---|---|
| themeMode | `phone.json` | `system`, `light`, or `dark`. Survives restart. Offline-safe. |
| accent | `phone.json` | One of the 22 accent ids. Unknown → magenta. |
| openBudgetId | `phone.json` | Open file. Switch names opening and leaving files; never merges. |

## Budget file (settings view)

The open directory from spec 001 / 004. Settings reads and writes:

| Preference id | Values | Rules |
|---|---|---|
| budgetType | `envelope` / `tracking` | Display only. No convert control. |
| currency | ISO code | Shown at create; not changed here. |
| dateFormat | Actual string (`MM/dd/yyyy`, …) | Part of the file. Visible on dates before the finger lifts. |
| numberFormat | Actual string | Part of the file. Feeds `MoneyFormat`. |
| hideFraction | `true` / `false` | Part of the file. Hides decimal places when true. |
| firstDayOfWeekIdx | `0`–`6` | Sunday = 0. Part of the file. |
| flags.experimental | `true` / `false` | Default absent = off. Toggle alone does not change a budgeted amount. |

When the file has sync state, preference writes also enqueue pending `preferences` messages so desktop Actual receives them.

| Sync state id | Shown in settings |
|---|---|
| (budget id) | Directory / `metadata.json` `id` |
| cloudFileId | Sync id when non-empty |

## Export

| Field | Rules |
|---|---|
| contents | Zip with `db.sqlite` and `metadata.json` from the open budget. |
| file name | From budget display name only. No encryption password, no server password. |
| network | Not required. |

## Recovery

| Action | Writes only after confirm | Offline |
|---|---|---|
| Rebuild calculated totals | Yes | Yes |
| Repair split transactions | Yes | Yes |
| Reset sync | Yes | Clears local sync tables; next reach of the active instance starts fresh |
| Set / change / remove encryption password | Yes (each step) | Local key material; server key metadata updated when reachable |

Until confirm, the budget directory is unchanged.

## Validation

- Blank budget name on create: reject; no directory.
- Failed sign-in: error message; active instance unchanged.
- Unreachable server on activation: choice kept; status "not reached"; local budget still editable.
- Formatting / export / encryption / recovery rows absent when no budget is open.
- Two instances with remote files that share a display name stay distinct by server file id and local directory id.

## State

- Instance remote file lists stay in memory for the active signed-in instance (spec 004). Cleared on sign-out.
- Month cache (spec 008) is cleared on rebuild totals.
- `RefreshGate` still holds painted rows during a gesture when a preference sync or rebuild finishes.
