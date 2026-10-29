# Contract: Bank link

Book: `BankLinkBook`. Transport: `ActualTransport` provider calls on the active instance (spec 009). Screen: `BankLinkScreens.kt`, from register `•••` → `bank-link`.

## Providers

Only those the server reports as configured.

| Provider | Status | Connect | Accounts |
|---|---|---|---|
| GoCardless | `POST /gocardless/status` | `POST /gocardless/create-web-token` → open link in Custom Tab → poll `POST /gocardless/get-accounts` | from requisition |
| SimpleFIN | `POST /simplefin/status` | when not configured: `POST /secret` `{name: "simplefin_token", value}` | `POST /simplefin/accounts` |
| Pluggy.ai | `POST /pluggyai/status` | server-side secrets via `POST /secret` | `POST /pluggyai/accounts` |

## Link

The person picks one external account for the phone account. Writes, as one undo step: `banks` row (`bank_id`, `name`), and on the account `account_id`, `bank`, `account_sync_source`, `balance_current`, `balance_available`, `balance_limit`, `last_sync = null`.

Replace: link again; columns overwritten. Remove: `account_id`, `bank`, `account_sync_source` cleared. Spec 007 offers fetch only when `account_id` and `account_sync_source` are set.

## Secrets

A token typed by the person is sent only to `POST /secret`. The input is cleared after send and never re-displayed. It is never written to `db.sqlite`, `phone.json`, a note, a payee, a log line, or the export zip.

## Failure

Server or provider unreachable: inline error on the link screen; no write; the rest of the budget is usable.

## Repair

A SimpleFIN fetch that fails names why, from the `error_code` Actual's server sends inside `data`, and the bank sync page offers the fix under the account:

| `error_code` | Shown | Fix |
|---|---|---|
| `ACCOUNT_NEEDS_ATTENTION` | the bank needs you to sign in again at SimpleFIN | **fix at SimpleFIN**: opens `https://bridge.simplefin.org/auth/login` in a Custom Tab. The bank's sign-in lives there; nothing on the phone or the server can renew it. The next sync picks the account up with no relink. |
| `INVALID_ACCESS_TOKEN` | SimpleFIN refused the server's token | **new setup token**: the account's bank link with SimpleFIN picked and the token field shown again |
| `ACCOUNT_MISSING` | SimpleFIN no longer has this account | **link again**: the account's bank link, listing SimpleFIN's accounts |
| `SERVER_DOWN` | SimpleFIN didn't answer | none; retry |

Saving a setup token first clears `simplefin_accessKey` (`POST /secret` with a null value), as Actual's reset does, so the server claims the new token rather than keep the access key it claimed from the old one. The bank link screen also offers **new setup token** whenever SimpleFIN is picked and set up.
