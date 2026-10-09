# Tasks: Actual Sync

**Input**: Design documents from `/specs/004-actual-sync/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/actual-server.md, quickstart.md

**Tests**: Required by the spec (offline edit, conflict, wrong password reads the screen, scroll during sync) and by constitution principle V.

**Organization**: Tasks are grouped by user story so each story can be implemented and tested on its own.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1–US4)

## Phase 1: Setup

**Purpose**: Ignore rules and the secret-store dependency the plan calls for

- [X] T001 Confirm `.gitignore` covers `.env*` and local build output for this Kotlin app
- [X] T002 Add `androidx.security:security-crypto` to `gradle/libs.versions.toml` and `app/build.gradle.kts`

---

## Phase 2: Foundational (Blocking)

**Purpose**: Codec, clock, schema, and HTTP client every story uses

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T003 [P] Implement the HLC in `core/budget/src/main/kotlin/app/duenorth/budget/core/SyncClock.kt`
- [X] T004 [P] Implement Actual `sync.proto` codec in `core/budget/src/main/kotlin/app/duenorth/budget/core/SyncProto.kt`
- [X] T005 [P] Implement PBKDF2 and AES-GCM in `core/budget/src/main/kotlin/app/duenorth/budget/core/BudgetCrypto.kt`
- [X] T006 Implement `messages`, `conflicts`, and `sync_state` plus cell apply in `core/budget/src/main/kotlin/app/duenorth/budget/core/SyncSchema.kt`
- [X] T007 Implement `HttpActualTransport` in `core/budget/src/main/kotlin/app/duenorth/budget/core/HttpActualTransport.kt`
- [X] T008 Add `installDownloaded` and `useDatabase` to `core/budget/src/main/kotlin/app/duenorth/budget/core/BudgetLibrary.kt`

**Checkpoint**: A protobuf round-trip and a key check can run without a screen

---

## Phase 3: User Story 1 - Open a budget from the server (Priority: P1) 🎯 MVP

**Goal**: Sign in, list that server's files, open one, and read it with the network off. A wrong sign-in replaces nothing. Download progress stays a thin bar.

**Independent Test**: Against the localhost server, open a file and match to-budget. Stop the server and open the shell again.

### Tests for User Story 1

- [X] T009 [P] [US1] Codec and clock tests in `core/budget/src/test/kotlin/app/duenorth/budget/core/SyncProtoTest.kt` and `SyncClockTest.kt`
- [X] T010 [US1] Sign-in, download, offline reopen, and failed sign-in tests in `core/budget/src/test/kotlin/app/duenorth/budget/core/SyncCoordinatorTest.kt`

### Implementation for User Story 1

- [X] T011 [US1] Implement connect, list, download, and open in `core/budget/src/main/kotlin/app/duenorth/budget/core/SyncCoordinator.kt`
- [X] T012 [US1] Add the server screen and thin progress bar in `app/src/main/kotlin/app/duenorth/budget/SyncScreens.kt` and wire it from `ShellViewModel.kt`
- [X] T013 [US1] Declare `INTERNET` and cleartext in `app/src/main/AndroidManifest.xml`

**Checkpoint**: User Story 1 works with the network turned off after the file is on the phone

---

## Phase 4: User Story 2 - Work offline, sync later (Priority: P2)

**Goal**: Assign money or add a transaction offline, keep it across a restart, and sync it. Conflicts stay visible. A sync during a scroll does not move the row under the finger.

**Independent Test**: Assign 25 offline, restart, sync, and read the assignment back. A second run conflicts on the same category and lists both amounts.

### Tests for User Story 2

- [X] T014 [P] [US2] Offline assign, restart, sync, and conflict tests in `SyncCoordinatorTest.kt`
- [X] T015 [P] [US2] 500-row refresh-gate test in `core/budget/src/test/kotlin/app/duenorth/budget/core/BudgetLibraryTest.kt`

### Implementation for User Story 2

- [X] T016 [US2] Implement assign, add transaction, and conflict retention in `SyncSchema.kt` and `SyncCoordinator.kt`
- [X] T017 [US2] Add assign, spend, and conflict screens in `SyncScreens.kt` and fade new rows in `ShellScreens.kt`
- [X] T018 [US2] Run sync on `Dispatchers.IO` without holding the database across HTTP in `ShellViewModel.kt`

**Checkpoint**: Offline edits survive a restart, and a conflict lists both values

---

## Phase 5: User Story 3 - Open an encrypted budget (Priority: P3)

**Goal**: An encrypted file asks for its own password. A wrong password opens nothing. Ask-each-time hides amounts after the screen locks.

**Independent Test**: Wrong password leaves directories unchanged and the password screen shows no category amount. The right password loads the month. Locking asks again.

### Tests for User Story 3

- [X] T019 [P] [US3] Crypto and wrong-password file tests in `BudgetCryptoTest.kt` and `SyncCoordinatorTest.kt`
- [X] T020 [P] [US3] Screen test that submits a bad password and reads the UI in `app/src/test/kotlin/app/duenorth/budget/ShellContentTest.kt`

### Implementation for User Story 3

- [X] T021 [US3] Verify the test ciphertext before installing a directory in `SyncCoordinator.kt`
- [X] T022 [US3] Add the budget password screen and `ON_STOP` lock in `SyncScreens.kt` and `DueNorthApp.kt`
- [X] T023 [US3] Store the key in `AndroidSecretStore` in `app/src/main/kotlin/app/duenorth/budget/AndroidSecretStore.kt`

**Checkpoint**: A wrong budget password never reveals an amount

---

## Phase 6: User Story 4 - Leave a server and switch files (Priority: P4)

**Goal**: Show the last sync time or the last failure, sign out, and switch files by name without merging them. Uploading a phone-only budget creates a new server file.

**Independent Test**: Note the sync time, switch files, and confirm the first file's categories are absent. Sign out and confirm the server list is gone while the on-phone file still opens.

### Tests for User Story 4

- [X] T024 [US4] Status, sign-out, switch, and upload tests in `SyncCoordinatorTest.kt`

### Implementation for User Story 4

- [X] T025 [US4] Implement status text, sign-out, and upload in `SyncCoordinator.kt`
- [X] T026 [US4] Show status and sign-out on the server screen, reusing `ConfirmSwitchScreen` for remote files

**Checkpoint**: Sign-out drops the server list and leaves phone-only budgets in place

---

## Phase 7: Polish

**Purpose**: Screenshots and the quickstart path

- [X] T027 [P] Screenshot the server screen in light and dark and refresh shell shots in `app/src/test/kotlin/app/duenorth/budget/ShellScreenshotTest.kt`
- [X] T028 Run `quickstart.md` scenarios via `:core:budget:test` and `:app:testDebugUnitTest`

---

## Dependencies & Execution Order

### Phase Dependencies

- Setup, then Foundational, then user stories in order P1 → P4, then Polish
- US2 depends on US1's open file. US3 depends on US1's download. US4 depends on US1's list and US2's sync status

### User Story Dependencies

- **US1**: After Foundational. No other story required
- **US2**: Needs a local file from US1's install path
- **US3**: Needs US1 download plus the crypto task
- **US4**: Needs US1 list and the sync bookkeeping from US2

### Parallel Opportunities

- T003, T004, and T005 touch different files
- T009 and T010 can be written together once the codec exists
- T014 and T015 touch different test files
- T019 and T020 touch different modules

### Parallel Example: Foundational

```bash
Task: "Implement the HLC in SyncClock.kt"
Task: "Implement Actual sync.proto codec in SyncProto.kt"
Task: "Implement PBKDF2 and AES-GCM in BudgetCrypto.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Finish Setup and Foundational
2. Finish US1 and run the offline reopen test
3. Then US2, US3, and US4

### Incremental Delivery

Each story leaves the earlier tests passing. Envelope math from spec 001 is not edited.

---

## Notes

- Tests in this feature are required by the spec and the constitution, not optional
- Sync HTTP stays off the main thread and off the sqlite connection
- Do not store the server token or the budget password inside a budget directory
