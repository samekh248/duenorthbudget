# Tasks: Metro Budget Shell

**Input**: Design documents from `/specs/001-metro-budget-shell/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Included. The constitution requires screenshot checks, envelope math tests that do not draw, and a fluidity check for a refresh during a gesture.

**Organization**: Tasks are grouped by user story so each story can be implemented and tested on its own.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Android project that can host the shell

- [X] T001 Create the Gradle build at `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, and `gradle/wrapper/gradle-wrapper.properties` (Kotlin 2.2, Compose foundation only, compileSdk 36, minSdk 26)
- [X] T002 Add `.gitignore` for Gradle, Android build outputs, and `local.properties`
- [X] T003 [P] Bundle Selawik (OFL) in `core/design/src/main/res/font/` and `core/design/src/main/assets/licenses/selawik_OFL.txt`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Actual-shaped storage and Metro tokens every story uses

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T004 Create the SQLite session and Actual-shaped schema in `core/budget/src/main/kotlin/app/duenorth/budget/core/SqlSession.kt`, `JdbcSqlSession.kt`, and `ActualSchema.kt` (budget name required after trim; currency from the fixed list; `budgetType` `envelope` or `tracking`)
- [X] T005 [P] Implement envelope and tracking month math in `core/budget/src/main/kotlin/app/duenorth/budget/core/ActualMonthMath.kt` per `contracts/budget-file.md`
- [X] T006 [P] Add Metro colors, the 22 accents, Selawik type ramp, and 4.5:1 contrast in `core/design/src/main/kotlin/app/duenorth/budget/design/theme/MetroTheme.kt`
- [X] T007 Add the phone file library (create, list, switch, reopen) in `core/budget/src/main/kotlin/app/duenorth/budget/core/BudgetLibrary.kt` and `Models.kt`. A blank trimmed name does not create a directory.

**Checkpoint**: A budget file can be created and read without a screen.

---

## Phase 3: User Story 1 - See the month on the panorama (Priority: P1) 🎯 MVP

**Goal**: Opening a budget shows this month's to-budget amount and each category group's available total, with no network.

**Independent Test**: Preload groups, categories, and a to-budget amount. The panorama shows those figures offline, and a sideways swipe moves the title more slowly than the sections.

### Tests for User Story 1

- [X] T008 [P] [US1] Envelope fixture tests in `core/budget/src/test/kotlin/app/duenorth/budget/core/ActualMonthMathTest.kt` and `ShellReaderTest.kt` (October to-budget 180000 minor units; group available follows rollover; 300 groups project in under 100 ms)
- [X] T009 [P] [US1] Title-parallax test in `core/design/src/test/kotlin/app/duenorth/budget/design/DesignRulesTest.kt` (title delta from section 0 to 1 is negative and smaller than one section width)

### Implementation for User Story 1

- [X] T010 [US1] Read the open month in `core/budget/src/main/kotlin/app/duenorth/budget/core/ShellReader.kt` (alive non-parent transactions; hidden categories stay inside the group total; tombstones omitted)
- [X] T011 [US1] Build `MetroPanorama` in `core/design/src/main/kotlin/app/duenorth/budget/design/components/MetroComponents.kt` (40 dp peek, snap, title shift from `PanoramaMotion`, section header tap)
- [X] T012 [US1] Show the budget section in `app/src/main/kotlin/app/duenorth/budget/ShellScreens.kt` (month label, header amount, one row per expense group, `nothing to budget` when there are no groups, long names do not overlap the amount)

**Checkpoint**: User Story 1 is visible on the panorama.

---

## Phase 4: User Story 2 - Move across budget, accounts, and inbox (Priority: P2)

**Goal**: Three sections, each showing only its own rows. A refresh during a swipe does not move the row under the finger.

**Independent Test**: One on-budget account, one off-budget account, and one uncategorized transaction. Each section shows only its rows. Inbox empty state is a short note.

### Tests for User Story 2

- [X] T013 [P] [US2] Inbox order, transfer exclusion, and on/off-budget balances in `core/budget/src/test/kotlin/app/duenorth/budget/core/ShellReaderTest.kt`
- [X] T014 [P] [US2] Refresh-during-gesture test in `core/budget/src/test/kotlin/app/duenorth/budget/core/BudgetLibraryTest.kt` (`RefreshGate` keeps the visible shell until the gesture ends)

### Implementation for User Story 2

- [X] T015 [US2] Accounts and inbox sections in `app/src/main/kotlin/app/duenorth/budget/ShellScreens.kt` (`on budget` above `off budget`; inbox newest first with payee and amount; `no accounts` and `nothing to categorize`)
- [X] T016 [US2] Hold shell updates while the panorama or a list is scrolling in `app/src/main/kotlin/app/duenorth/budget/ShellViewModel.kt`

**Checkpoint**: User Stories 1 and 2 work together.

---

## Phase 5: User Story 3 - Light, dark, and accent (Priority: P3)

**Goal**: Follow the phone unless the person picks light or dark, and keep one accent. Both choices survive a new process.

**Independent Test**: Dark phone with "follow phone" is black. Override to light stays light after the settings file is read again. A non-magenta accent is the highlight.

### Tests for User Story 3

- [X] T017 [P] [US3] Accent contrast test in `core/design/src/test/kotlin/app/duenorth/budget/design/DesignRulesTest.kt` (every accent text at least 4.5:1 on white and on black)
- [X] T018 [P] [US3] Settings round-trip in `core/budget/src/test/kotlin/app/duenorth/budget/core/BudgetLibraryTest.kt` (`themeMode` `light`, accent `coral`)

### Implementation for User Story 3

- [X] T019 [US3] Appearance screen in `app/src/main/kotlin/app/duenorth/budget/ShellScreens.kt` (`follow phone`, `light`, `dark`, accent grid) and apply it in `app/src/main/kotlin/app/duenorth/budget/DueNorthApp.kt`
- [X] T020 [US3] Persist appearance in `phone.json` via `BudgetLibrary.save` from `ShellViewModel.setTheme` and `setAccent`. Store it outside the budget file.

**Checkpoint**: Theme and accent survive a new `BudgetLibrary` on the same directory.

---

## Phase 6: User Story 4 - Start a budget on the phone (Priority: P4)

**Goal**: Create an empty budget with a name and a currency, switch files without merging them, and reopen the last file.

**Independent Test**: Create "Home" in USD, see an empty panorama, create a second budget, switch back, and see Home unchanged.

### Tests for User Story 4

- [X] T021 [P] [US4] Create, reject a blank name, switch, and reopen tests in `core/budget/src/test/kotlin/app/duenorth/budget/core/BudgetLibraryTest.kt`
- [X] T022 [P] [US4] Currency formatting including a currency with no minor units in `core/budget/src/test/kotlin/app/duenorth/budget/core/MoneyFormatTest.kt`

### Implementation for User Story 4

- [X] T023 [US4] Create and confirm-switch screens in `app/src/main/kotlin/app/duenorth/budget/ShellScreens.kt`. Confirmation names the file opening and the file left (`leave {name} on the phone`). Switching writes only `openBudgetId`.
- [X] T024 [US4] Wire Android SQLite and launch in `app/src/main/kotlin/app/duenorth/budget/AndroidSqlSession.kt` and `DueNorthApp.kt`. No network permission.

**Checkpoint**: Two files can be created and switched on the phone.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Screenshot checks, lazy list, press feedback

- [X] T025 [P] Roborazzi light and dark shell captures in `app/src/test/kotlin/app/duenorth/budget/ShellScreenshotTest.kt`
- [X] T026 [P] Compose checks for the empty note, a non-overlapping long name, and a lazy 300-group list in `app/src/test/kotlin/app/duenorth/budget/ShellContentTest.kt`
- [X] T027 Press feedback that appears on pointer down, and no decorative snap when the animator scale is 0, in `core/design/src/main/kotlin/app/duenorth/budget/design/PanoramaMotion.kt` and `MetroComponents.kt`
- [X] T028 Document build and test commands in `specs/001-metro-budget-shell/quickstart.md` and the spec table in `README.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- Setup, then foundational, then user stories in priority order (US1 → US2 → US3 → US4), then polish.
- US2 depends on the panorama from US1. US3 and US4 depend on the file library. US4's screens depend on the app bar from the shell.

### User Story Dependencies

- **US1**: After foundational. No other story.
- **US2**: After US1 (same panorama).
- **US3**: After foundational. Uses the shell to show the accent.
- **US4**: After foundational. Lands on the US1 panorama.

### Parallel Opportunities

- T003, T005, and T006 can run beside each other.
- Tests for a story (T008/T009, T013/T014, T017/T018, T021/T022) can run beside each other.

---

## Parallel Example: User Story 1

```text
T008 Envelope fixture tests in core/budget
T009 Title parallax test in core/design
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Setup and foundational math.
2. Budget section on the panorama.
3. Stop and check the October fixture before accounts and inbox.

### Incremental Delivery

1. US1 month picture.
2. US2 accounts, inbox, and the refresh gate.
3. US3 theme and accent.
4. US4 create and switch.
5. Screenshots and the 300-row lazy check.

---

## Notes

- Checkbox, task id, optional `[P]`, story label on story tasks, and a file path on every task.
- This spec does not edit categories, assignments, or transactions.
