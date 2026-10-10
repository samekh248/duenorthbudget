# Research: Actual Sync

Phase 0 output for [plan.md](plan.md). Each entry is Decision / Rationale / Alternatives.

## R1. Server contract

- **Decision**: Speak the Actual sync server HTTP API. `POST /account/login` with `loginMethod: password`. `GET /sync/list-user-files`, `GET /sync/download-user-file`, `POST /sync/upload-user-file`, and `POST /sync/user-get-key` use JSON plus the `X-ACTUAL-TOKEN` header. `POST /sync/sync` sends and receives `application/actual-sync` protobuf from `packages/crdt/src/proto/sync.proto` (`SyncRequest`, `SyncResponse`, `MessageEnvelope`, `Message`, `EncryptedData`).
- **Rationale**: The spec says the server is an Actual server the person already runs. Those routes and the proto fields are what current Actual (`app-sync.ts` and `sync.proto`) actually uses.
- **Alternatives**: A private JSON sync format (would not open a real Actual server). Embedding `@actual-app/crdt` (Node, not a phone library).

## R2. Since, not a second merkle implementation

- **Decision**: The client sends `since` as an Actual HLC timestamp (`2015-04-24T22:23:42.123Z-1000-0123456789ABCDEF`). The server returns envelopes whose timestamp is greater, compared as strings, which is valid because the HLC text sorts in time order. The returned merkle JSON is stored and not used as a second source of truth. Applying the same timestamp twice is a no-op.
- **Rationale**: Actual's simple sync (`X-ACTUAL-SYNC-METHOD: simple`) already selects messages by `since`. Rebuilding the radix trie would be a large second copy of `@actual-app/crdt` with no extra behavior this spec tests. The primary key on `messages.timestamp` is what stops a clock skew from duplicating an edit.
- **Alternatives**: Port the full merkle trie (needed only if we had to prove hash equality before accepting a batch). Last-write-wins by wall clock (drops one side silently, which the spec forbids).

## R3. Conflicts

- **Decision**: A remote envelope whose cell still has a pending local message with a different value is stored, and a `conflicts` row keeps both values. The cell on screen stays at the pending local value. When no local message is pending, the remote value is written (server wins). Neither message row is deleted.
- **Rationale**: The constitution says the server wins when the local change is not still pending, and a conflict is visible. Actual's own clients collapse to one value; this spec requires both to remain visible.
- **Alternatives**: Silent last-write-wins (fails SC-003). Blocking the UI until the person picks a winner (fails the fluidity rule).

## R4. Encryption

- **Decision**: A file with `encryptKeyId` is ciphertext on the wire. The key is PBKDF2-HMAC-SHA512, 10,000 iterations, 256 bits, then AES-256-GCM. The blob is the ASCII magic `DNENC1`, a 12-byte IV, and the GCM ciphertext with its 16-byte tag. `user-get-key` returns `salt` and `test`. The test plaintext is `due-north-ok`. The budget password is checked on the phone and is never sent to the server. When a key is loaded, sync envelopes set `isEncrypted` and wrap the inner `Message` in `EncryptedData`. The keystore holds the raw AES key only when the person turned off "ask each time". The password string is not stored.
- **Rationale**: Actual's file key is password-derived AES-GCM, and the server stores salt and a test ciphertext so the client can reject a wrong password without opening the file. Keeping that check on the phone means a wrong password never creates a directory. "Ask each time" drops the in-memory key when the activity stops, so the screen hides amounts until the password is entered again.
- **Alternatives**: Reusing the server password (the spec forbids mixing the prompts). Writing the password into `metadata.json` (the spec forbids it). Encrypting the sqlite only at rest and leaving envelopes in the clear (would leak amounts on a synced encrypted file).

## R5. Where work runs

- **Decision**: `HttpURLConnection` on `Dispatchers.IO`. The sqlite connection is closed before the HTTP call and opened again to apply the result. A thin progress bar (`sync-progress`) shows received bytes. Leaving the screen does not cancel the `viewModelScope` job, so the byte count does not restart unless the download failed. `RefreshGate` still decides what the lists draw. Rows that arrive after the first composition fade from transparent to opaque in 160 ms when animations are on; the first paint of a screen does not fade, so screenshots stay stable.
- **Rationale**: `java.net.http.HttpClient` is not in the Android SDK this app compiles against. Holding a sqlite connection across the network call would make an assign wait on sync, which fails FR-013. The existing gate is the rule spec 001 already uses for "the row under the finger".
- **Alternatives**: OkHttp (new dependency for a single client). A full-screen dialog while the file downloads (forbidden). Animating every row on first paint (would blank the screenshot harness).

## R6. Phone-only upload

- **Decision**: A budget that has no `cloudFileId` uploads under a new UUID. The client never sends an existing server file id unless the person chose that file on the server list. Choosing one replaces that server file's blob and does not copy any other local budget into it.
- **Rationale**: FR-012. Actual's `upload-user-file` creates or replaces the id it is given, so the safety check belongs on the client that picks the id.
- **Alternatives**: Always uploading over the first server file (can destroy a household budget). Merging tables (forbidden by principle IV).
