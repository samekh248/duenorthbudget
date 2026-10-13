# Tasks: Settings and Instance

**Input**: Design documents from `/specs/009-settings-home/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Included. The plan's Testing section and constitution Principle V require JVM tests for instances, preferences, export, and recovery; Compose tests for the settings hub; and Roborazzi light and dark screenshots.

**Organization**: Tasks are grouped by user story so each story can be implemented and tested on its own.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4, US5)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm existing modules and ignore rules; no new Gradle module

- [ ] T001 Verify `.gitignore` covers Android and Gradle outputs (`local.properties`, `.gradle/`, `**/build/`, `*.iml`, `.idea/`, secrets) and that `.specify/feature.json` stays ignored
- [ ] T002 Confirm no new module is required in `settings.gradle.kts`; work stays in `:core:budget` and `:app` per `plan.md`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Multi-instance phone settings model and the settings hub shell every story uses

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T003 Add `SavedInstance` (`id`, `address`, optional `label`) and extend `PhoneSettings` with `instances: List<SavedInstance>` and `activeInstanceId: String?` in `core/budget/src/main/kotlin/app/duenorth/budget/core/Models.kt`; keep legacy `serverAddress` for migration only
- [ ] T004 Implement read-time migration in `core/budget/src/main/kotlin/app/duenorth/budget/core/BudgetLibrary.kt`: when `serverAddress` is set and `instances` is empty, create one instance with that address, set `activeInstanceId`, and clear `serverAddress` on the next save
- [ ] T005 [P] JVM tests for migration and active-id rules in `core/budget/src/test/kotlin/app/duenorth/budget/core/InstanceSettingsTest.kt` (legacy `serverAddress` → one instance; at most one active; remove non-active leaves active and local budgets)
- [ ] T006 Replace the stub settings door with a sectioned hub tagged `settings-screen` in `app/src/main/kotlin/app/duenorth/budget/SettingsScreens.kt` (new) and wire `ShellRoute.Settings` in `app/src/main/kotlin/app/duenorth/budget/DueNorthApp.kt` / `ShellScreens.kt` per `contracts/settings-screens.md` (instance, budgets, appearance, payees; open-budget sections stubbed until later stories)

**Checkpoint**: `phone.json` can hold instances; settings hub opens from the bar.

---

## Phase 3: User Story 1 - Choose the Actual server (Priority: P1) 🎯 MVP

**Goal**: From settings, add an Actual server by address, sign in (password or browser OpenID), and make it the active instance. Local edits still work without waiting on that server.

**Independent Test**: With no server saved, add an address, sign in, and confirm settings names that server as the active instance. Budget screens still read the copy on the phone.

### Tests for User Story 1

- [ ] T007 [P] [US1] JVM tests for password connect against localhost in `core/budget/src/test/kotlin/app/duenorth/budget/core/InstanceConnectTest.kt` (success stores token under `actual.token.<instanceId>` and sets active; auth failure leaves no active instance; network failure shows address-failed copy)
- [ ] T008 [P] [US1] JVM tests for OpenID handshake against localhost in `core/budget/src/test/kotlin/app/duenorth/budget/core/OpenIdLoginTest.kt` (`login-methods` advertises openid; login returns redirect; callback token activates instance; cancel leaves prior active unchanged)

### Implementation for User Story 1

- [ ] T009 [US1] Extend `ActualTransport` / `HttpActualTransport` in `core/budget/src/main/kotlin/app/duenorth/budget/core/ActualTransport.kt` with login-methods discovery and OpenID login (`loginMethod: "openid"` + return URL) per `contracts/instances.md`
- [ ] T010 [US1] Point `SyncCoordinator.connect` / token storage at the active instance id (`actual.token.<instanceId>`, active address from `PhoneSettings`) in `core/budget/src/main/kotlin/app/duenorth/budget/core/SyncCoordinator.kt`; failed sign-in must not mark active
- [ ] T011 [US1] Add instance section UI (not connected / active name / add / password sign-in / browser sign-in) in `app/src/main/kotlin/app/duenorth/budget/SettingsScreens.kt` and ViewModel methods in `app/src/main/kotlin/app/duenorth/budget/ShellViewModel.kt`
- [ ] T012 [US1] Wire Custom Tab + `duenorth://oauth` return in `app/src/main/kotlin/app/duenorth/budget/DueNorthApp.kt` (and manifest if needed) so a successful browser sign-in returns to settings with that instance active
- [ ] T013 [US1] Keep offline budget edits on the phone path after an instance is active (no wait on server) — verify assign/add still write sqlite before sync in existing `SyncCoordinator` call sites used from settings flows

**Checkpoint**: US1 works: add server, sign in, settings names it active; local edits stay offline-first.

---

## Phase 4: User Story 2 - Change the instance later (Priority: P1)

**Goal**: Save more than one server, make a different one active (named confirmation), sign out without deleting local budgets, and keep offline choices.

**Independent Test**: Save two servers, sign into the second, then sign out. Both local budgets still open, and the second server is no longer signed in.

### Tests for User Story 2

- [ ] T014 [P] [US2] JVM tests for switch / remove / sign-out in `core/budget/src/test/kotlin/app/duenorth/budget/core/InstanceSwitchTest.kt` (exactly one active; switch does not merge directories; remove non-active keeps active; remove active clears session; sign-out clears token and keeps files; offline activation persists `activeInstanceId`)

### Implementation for User Story 2

- [ ] T015 [US2] Implement make-active, remove, and sign-out on `SyncCoordinator` / `BudgetLibrary` in `core/budget/src/main/kotlin/app/duenorth/budget/core/SyncCoordinator.kt` and `BudgetLibrary.kt` per `contracts/instances.md` (clear remote list on switch/sign-out; never delete local budget directories)
- [ ] T016 [US2] Add instance list UI and switch confirmation that names the server left and the server opening in `app/src/main/kotlin/app/duenorth/budget/SettingsScreens.kt`; show "server has not been reached" when active but unreachable
- [ ] T017 [US2] Ensure sign-out finishes while a sync job may still be running without freezing the UI in `app/src/main/kotlin/app/duenorth/budget/ShellViewModel.kt` (cancel or detach token use; local file remains)

**Checkpoint**: US1–US2: multiple saved instances, safe switch and sign-out, offline activation kept.

---

## Phase 5: User Story 3 - The budget file, from settings (Priority: P2)

**Goal**: From settings, see local budgets and which is open; create and switch with the existing name-both confirmation; blank name creates nothing.

**Independent Test**: With two local budgets, open settings, switch files, confirm the names, and see the panorama show the file just opened.

### Tests for User Story 3

- [ ] T018 [P] [US3] Compose tests for budget rows on the settings hub in `app/src/test/kotlin/app/duenorth/budget/SettingsContentTest.kt` (lists both names and marks open; blank create rejected; formatting/export sections absent when no budget open)

### Implementation for User Story 3

- [ ] T019 [US3] Embed budgets list, create entry, and switch confirmation in the settings hub in `app/src/main/kotlin/app/duenorth/budget/SettingsScreens.kt`, reusing `BudgetLibrary.create` / open-id switch from `ShellViewModel.kt` (blank trimmed name → `enter a name`, no directory)
- [ ] T020 [US3] Keep payees and appearance reachable as settings rows opening existing screens in `app/src/main/kotlin/app/duenorth/budget/DueNorthApp.kt` without duplicating panorama bar actions on the settings route

**Checkpoint**: File create/switch works from settings; open file is clear.

---

## Phase 6: User Story 4 - Everyday preferences (Priority: P2)

**Goal**: Theme and accent in settings (phone). Date format, number format, first day of week, and hide decimals in the open file with Actual keys. Method shown, not convertible. Experimental switch defaults off.

**Independent Test**: Set dark + non-default accent, change date format and hide decimals, restart; both survive. Fixture reading the same sqlite shows matching `dateFormat` and `hideFraction`. Method has no convert control.

### Tests for User Story 4

- [ ] T021 [P] [US4] JVM tests for preference read/write in `core/budget/src/test/kotlin/app/duenorth/budget/core/BudgetPreferencesTest.kt` (`dateFormat`, `numberFormat`, `hideFraction`, `firstDayOfWeekIdx`, `flags.experimental` default off; pending sync message when `cloudFileId` set; `budgetType` not writable via settings API)
- [ ] T022 [P] [US4] JVM tests that `MoneyFormat` / date helpers honor prefs in `core/budget/src/test/kotlin/app/duenorth/budget/core/MoneyFormatTest.kt` (or adjacent) so hide-fraction and number format change the next formatted amount

### Implementation for User Story 4

- [ ] T023 [US4] Add preference get/set helpers on `BudgetLibrary` in `core/budget/src/main/kotlin/app/duenorth/budget/core/BudgetLibrary.kt` for the keys in `contracts/budget-preferences.md`, including pending `preferences` sync messages when synced
- [ ] T024 [US4] Update amount and date formatting paths (`MoneyFormat` in `Models.kt`, register/review date display) to read open-budget prefs so the next painted values update with the settings tap
- [ ] T025 [US4] Add appearance, formatting, method (read-only), and experimental rows to `app/src/main/kotlin/app/duenorth/budget/SettingsScreens.kt` and wire theme/accent/format/experimental methods in `ShellViewModel.kt` (theme/accent stay in `phone.json` only)

**Checkpoint**: Appearance and file formatting match the contracts; method cannot convert.

---

## Phase 7: User Story 5 - Export and recovery (Priority: P3)

**Goal**: Export an Actual-shaped zip; confirmed rebuild totals, repair splits, and reset sync; show budget/sync ids; set/change/remove encryption password without leaking secrets into names.

**Independent Test**: Export a budget with one known account and transaction; open the zip's sqlite and find them. Reset sync does nothing until confirmed.

### Tests for User Story 5

- [ ] T026 [P] [US5] JVM tests for export zip in `core/budget/src/test/kotlin/app/duenorth/budget/core/BudgetExportTest.kt` (contains `metadata.json` + `db.sqlite`; known account/transaction present; file name has no password strings)
- [ ] T027 [P] [US5] JVM tests for recovery confirmations in `core/budget/src/test/kotlin/app/duenorth/budget/core/BudgetRecoveryTest.kt` (no-op without confirm; rebuild refreshes figures from transactions; repair fixes orphan splits; reset sync clears messages / clock fields but keeps `cloudFileId` and local rows)

### Implementation for User Story 5

- [ ] T028 [US5] Implement export zip builder in `core/budget/src/main/kotlin/app/duenorth/budget/core/BudgetLibrary.kt` per `contracts/budget-preferences.md` (offline; display-name-only file name)
- [ ] T029 [US5] Implement rebuild totals, repair splits, and reset sync (confirmed) in `core/budget/src/main/kotlin/app/duenorth/budget/core/BudgetLibrary.kt` / `SyncCoordinator.kt`
- [ ] T030 [US5] Add encryption set/change/remove using `BudgetCrypto` in `SyncCoordinator.kt` / settings UI; wrong password reveals no amount; never write password into name, notes, or categories
- [ ] T031 [US5] Add export, ids, encryption, and advanced confirmation UI in `app/src/main/kotlin/app/duenorth/budget/SettingsScreens.kt` and share/save wiring in `ShellViewModel.kt` / `DueNorthApp.kt`; hide these sections when no budget is open

**Checkpoint**: Export and recovery match FR-011–FR-014; offline export/rebuild work.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Screenshots, offline fluidity, and quickstart validation across stories

- [ ] T032 [P] Roborazzi light and dark screenshots of the settings hub (instance + appearance visible) in `app/src/test/kotlin/app/duenorth/budget/SettingsScreenshotTest.kt`
- [ ] T033 Compose/offline check: settings opens with network off; theme change survives restart; open-budget edit still under 100 ms path in `app/src/test/kotlin/app/duenorth/budget/SettingsContentTest.kt`
- [ ] T034 Run `./gradlew :core:budget:test :app:testDebugUnitTest :app:verifyRoborazziDebug` and walk `specs/009-settings-home/quickstart.md` scenarios; fix any regressions in sync conflict tests from spec 004

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories
- **US1 (Phase 3)**: After Foundational — MVP
- **US2 (Phase 4)**: After Foundational; naturally builds on US1 instance model
- **US3 (Phase 5)**: After Foundational; uses settings hub from Phase 2
- **US4 (Phase 6)**: After Foundational; needs open budget from existing shell / US3
- **US5 (Phase 7)**: After Foundational; needs open budget; reset sync needs active instance from US1
- **Polish (Phase 8)**: After desired stories complete

### User Story Dependencies

- **US1 (P1)**: After Phase 2 — MVP (add + sign in)
- **US2 (P1)**: After Phase 2; shares instance storage with US1
- **US3 (P2)**: After Phase 2; independent of sync once hub exists
- **US4 (P2)**: After Phase 2; independent of multi-instance once a budget can open
- **US5 (P3)**: After Phase 2; export/rebuild offline without US1; reset sync needs US1 active instance

### Parallel Opportunities

- T001–T002 sequential with Phase 2 start
- T005 parallel with T006 after T003–T004
- T007–T008 parallel; T014, T018, T021–T022, T026–T027 parallel within their phases
- After Phase 2, US3 and US4 can proceed in parallel with US1/US2 if staffed (different files: preferences vs instances)

---

## Parallel Example: User Story 1

```bash
# Tests in parallel:
Task: "JVM tests for password connect in InstanceConnectTest.kt"
Task: "JVM tests for OpenID handshake in OpenIdLoginTest.kt"

# Then transport → coordinator → UI → Custom Tab sequentially
```

---

## Parallel Example: User Story 4

```bash
# Tests in parallel:
Task: "BudgetPreferencesTest.kt"
Task: "MoneyFormat preference tests"

# Then library helpers → formatters → settings UI
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 + Phase 2
2. Phase 3 (US1): add server, sign in, active instance named
3. **STOP and VALIDATE** independent test for US1
4. Then US2 (second P1) before preferences/export

### Incremental Delivery

1. Setup + Foundational → hub + instance model
2. US1 → connect one server (MVP)
3. US2 → switch / sign out
4. US3 → budgets from settings
5. US4 → appearance + formatting
6. US5 → export + recovery
7. Polish → screenshots and quickstart

### Suggested MVP scope

Phases 1–3 only (settings hub + one active instance via password/OpenID).

---

## Notes

- [P] tasks = different files, no dependencies on incomplete work
- Do not merge local budgets into a server file when changing instance
- Theme/accent stay in `phone.json`; formatting keys stay in the budget file
- Experimental *behavior* is out of scope (spec 010); only the switch lives here
- Commit after each task or logical group
