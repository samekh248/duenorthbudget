# Contract: Instances and sign-in

Extends [specs/004-actual-sync/contracts/actual-server.md](../../004-actual-sync/contracts/actual-server.md). Password login is unchanged. This contract adds multi-instance storage and OpenID.

## Storage

- Saved instances and `activeInstanceId` live in `phone.json` (see [data-model.md](../data-model.md)).
- Token for instance `id` is secret-store key `actual.token.<id>`.
- Sync HTTP uses the active instance's address and token only.
- Legacy `serverAddress` migrates to one instance on read.

## Add and sign in (password)

1. Person enters address (and password when using password login).
2. Normalize address (strip trailing slash; require http/https).
3. `POST {address}/account/login` with `{ "password", "loginMethod": "password" }` as in spec 004.
4. On 200 with token: upsert `SavedInstance`, set `activeInstanceId`, store token, list remote files.
5. On auth or network failure: show the existing copy (`that sign-in was not accepted` / `the address could not be reached`); do not mark a new instance active; leave local budgets alone.

## Login methods and OpenID

1. `GET {address}/account/login-methods` (or the server's equivalent bootstrap payload that lists methods). If the call fails, fall back to offering password only.
2. When `openid` is allowed, settings also offers browser sign-in.
3. Browser path: `POST {address}/account/login` with `{ "loginMethod": "openid", "returnUrl": "duenorth://oauth" }` (or the JSON shape the server returns for redirect). Open the returned redirect URL in a Custom Tab.
4. On return to `duenorth://oauth` with a token query/fragment: store token, upsert instance, set active, dismiss Custom Tab, show settings signed in.
5. Cancel or error: no active change; error copy as network/auth failure.

## Make active / remove / sign out

| Action | Effect |
|---|---|
| Make B active (A was active) | Confirmation names A left and B opening. Then `activeInstanceId = B`. Remote list cleared until B is signed in / refreshed. Local directories unchanged. |
| Make active while offline | Choice saved. Status shows not reached. Open local budget stays editable. |
| Sign out | Remove active token and remote list. Local budgets remain. Server list actions that need a session stay unavailable until sign-in. |
| Remove non-active | Drop row + token. Active unchanged. |
| Remove active | Sign out that instance, clear `activeInstanceId`, drop row. Local budgets remain. |

## What the phone shows

| Condition | Message |
|---|---|
| No saved instances | not connected; offer add |
| Active, token present, last reach ok | instance label/address as active |
| Active, not reached / offline | server has not been reached (local budget still works) |
| Sign-in rejected | that sign-in was not accepted |
| Address failed | the address could not be reached |
| OpenID cancelled | stay on settings; not signed in to that instance |
