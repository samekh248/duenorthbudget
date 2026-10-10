# Contract: Actual server

The phone is a client of an Actual sync server. Paths are appended to the address the person entered, with one trailing slash removed. The token header is `X-ACTUAL-TOKEN`.

## POST /account/login

Request JSON: `{ "password": "...", "loginMethod": "password" }`.

- 200 `{ "status": "ok", "data": { "token": "..." } }` saves the token.
- 400 `{ "reason": "invalid-password" }` shows "that sign-in was not accepted" and does not change a budget directory.

## GET /sync/list-user-files

Response `{ "status": "ok", "data": [ { "fileId", "groupId", "name", "encryptKeyId", "deleted" } ] }`.

The phone lists rows with `deleted` not set. An empty `data` array shows "no budgets on this server". Files from a previous server are not kept in the list.

## GET /sync/download-user-file

Header `X-ACTUAL-FILE-ID`. Body is the file bytes. Unencrypted bytes start with the SQLite header. Encrypted bytes start with `DNENC1`.

The phone reports progress as bytes read over `Content-Length`. The directory is replaced only after the bytes open as a sqlite file (after decrypt, when the file is encrypted). A failure deletes the partial file and leaves every existing directory as it was.

## POST /sync/upload-user-file

Headers `X-ACTUAL-FILE-ID`, `X-ACTUAL-NAME` (percent-encoded), optional `X-ACTUAL-GROUP-ID`, optional `X-ACTUAL-ENCRYPT-META` JSON `{ "keyId", "salt", "test" }`. Body is the file bytes.

Response `{ "status": "ok", "groupId": "..." }`.

A phone-only budget sends a new id. An existing server id is sent only after the person picks that file.

## POST /sync/user-get-key

Request `{ "fileId": "..." }`. Response `{ "status": "ok", "data": { "id", "salt", "test" } }`.

`salt` and `test` are base64. `test` decrypts to the UTF-8 bytes `due-north-ok` when the password is right. The budget password is not in this request.

## POST /sync/sync

`Content-Type: application/actual-sync`. Body is protobuf `SyncRequest`:

| Field | Number | Contents |
|---|---|---|
| messages | 1 | `MessageEnvelope`: timestamp (1), isEncrypted (2), content (3) |
| fileId | 2 | Server file id |
| groupId | 3 | Group id |
| keyId | 5 | Encryption key id, or omitted |
| since | 6 | HLC timestamp. Required. |

`content` is protobuf `Message` (`dataset`, `row`, `column`, `value`) or, when `isEncrypted` is true, protobuf `EncryptedData` (`iv` 1, `authTag` 2, `data` 3) wrapping that message.

Response `SyncResponse`: messages (1), merkle JSON string (2). The phone applies envelopes with a timestamp it does not already have. String order matches time order. The merkle string is stored on `sync_state` and does not by itself accept or reject a cell.

## Encryption blob

`DNENC1` (6 bytes) + IV (12) + AES-256-GCM ciphertext including the 16-byte tag. Key: PBKDF2-HMAC-SHA512, 10,000 iterations, 256-bit AES key, salt from `user-get-key`.

## What the phone shows

| Condition | Message |
|---|---|
| Address missing, not http(s), or the connection failed | the address could not be reached |
| Sign-in rejected | that sign-in was not accepted |
| Later request rejected as unauthorized | sign in again |
| Sync failed for another reason | the last try failed |
| Sync returned | synced h:mm am/pm |
| Budget password rejected | that password did not open this budget |
| Amount blank | enter an amount |
