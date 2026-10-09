# Implementation Plan: Actual Sync

**Branch**: `004-actual-sync` | **Date**: 2026-10-07 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/004-actual-sync/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

The phone can sign in to an Actual server, open that server's files, and keep working from the on-phone copy when the network is gone. Local assigns and new transactions land in the file immediately and sync in the background. A cell both sides changed is listed with both values. Encrypted files ask for the budget password, which is not the server sign-in and is not written into the budget. Sync status, sign-out, and an explicit file switch sit on a Metro screen. Nothing in this path shows a full-screen spinner or moves a row under the finger.

## Technical Context

**Language/Version**: Kotlin 2.2, Java 17 bytecode, Android minSdk 26, compileSdk 36

**Primary Dependencies**: Existing Compose shell and `:core:budget`. HTTP via `HttpURLConnection` (present on Android 26; `java.net.http` is not). kotlinx.serialization for the JSON account and file endpoints. JDK crypto (PBKDF2-HMAC-SHA512 and AES-GCM). A small protobuf codec for Actual's `sync.proto`. AndroidX Security for the on-phone secret store.

**Storage**: The open budget stays a directory (`metadata.json` + `db.sqlite`). Sync adds `messages`, `conflicts`, and `sync_state` inside that sqlite file. The server token and a remembered budget key live in the Android keystore-backed secret store, never in the budget file. `phone.json` holds the server address, the sync node id, and which files ask for the password again. It does not hold secrets.

**Testing**: JUnit on the JVM against a localhost Actual-shaped server (`HttpServer`) for sign-in, download, offline edit, conflict, upload, encryption, and duplicate timestamps. Robolectric Compose tests for the password screen, the conflict list, and the small progress bar. Roborazzi screenshots of the server screen in light and dark, plus updated shell shots that include the sync button.

**Target Platform**: Android phone (minSdk 26)

**Project Type**: Mobile app

**Performance Goals**: An offline assign finishes in the local file in under 100 ms. Sync and download run off the main thread. A download in progress keeps its byte count when the person leaves the screen. A 500-row list held by `RefreshGate` does not change the visible row while a gesture is active.

**Constraints**: No full-screen spinner. No network call on the thread that draws. Failed sign-in does not replace a budget directory. One open file. Envelope math from spec 001 is unchanged. The budget password is not the server password and is not stored in the file's name, notes, or categories.

**Scale/Scope**: One server connection. The file list that server returns. Message sync for the tables spec 001 already creates, plus local assign and add-transaction writes so an offline edit has something to send.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Result |
|---|---|---|
| I. Fluid First | Local edits write sqlite and return before sync. HTTP runs on `Dispatchers.IO`. Progress is a thin bar. `RefreshGate` still holds the visible shell during a gesture. New rows fade only after the finger lifts. | Pass |
| II. Metro 2 | Server, password, conflict, assign, and spend screens use the existing type ramp, gutter, flat fill, and app bar. The password prompt and the server prompt are different screens. | Pass |
| III. The Same Budget as Actual | Login, list, download, upload, and `POST /sync/sync` follow Actual's HTTP contract and `sync.proto`. Cell writes use Actual's column names. The server copy wins when the local edit is not still pending. | Pass |
| IV. One Budget at a Time | Opening another file reuses the spec 001 confirmation and names both files. Upload of a phone-only budget creates a new server file unless the person picked an existing one. | Pass |
| V. Test What the Person Can See | Password screen test reads the screen after a bad password. Conflict test requires both values. Screenshots cover the new server screen in light and dark. | Pass |
| VI. Phone First | No new module. Sync classes live in `:core:budget`. Screens stay in `:app`. | Pass |

Post-design re-check: the same gates hold. Message encryption uses the budget key when one is loaded, so an encrypted file's cell values are not plaintext envelopes. No constitution violation needs a complexity exception.

## Project Structure

### Documentation (this feature)

```text
specs/004-actual-sync/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── actual-server.md
└── tasks.md
```

### Source Code (repository root)

```text
app/                         # Server, password, conflict, assign, and spend screens
core/budget/                 # Sync clock, protobuf, crypto, HTTP client, coordinator
core/design/                 # Unchanged Metro pieces; password field conceal flag
```

**Structure Decision**: Keep the spec 001 split. `:core:budget` gains the sync engine and stays free of Android so the server tests run on a plain JVM. `:app` adds the screens, the keystore secret store, and the `INTERNET` permission. No server module is added; tests start a localhost `HttpServer` inside the JVM test source set.

## Complexity Tracking

No constitution violations.
