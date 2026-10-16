# Tasks: Transaction Details Cleanup

**Input**: Design documents from `/specs/013-transaction-details/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Included. Principle V and the plan's Testing section require JVM tests for date arithmetic, Robolectric content tests for the form and its overlays, and Roborazzi light and dark screenshots.

**Organization**: Tasks are grouped by user story so each story can be implemented and tested on its own.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1–US4)

## Phase 1: Setup

- [X] T001 Confirm no new Gradle module; work stays in `:core:budget`, `:core:design` and `:app` per `plan.md`

---

## Phase 2: Foundational (Blocking Prerequisites)

- [X] T002 [P] Add `MetroDimens.FieldLabelGap` (6 dp) and `FieldGroupGap` (24 dp) in `core/design/src/main/kotlin/app/duenorth/budget/design/theme/MetroTheme.kt`
- [X] T003 [P] Add `fill` and `lines` parameters to `MetroField` in `core/design/src/main/kotlin/app/duenorth/budget/design/components/MetroComponents.kt` (existing callers unchanged)
- [X] T004 [P] Add the form bar override next to `FormSaveHost` in `app/src/main/kotlin/app/duenorth/budget/FormSaveBinding.kt`, and make `ScreenBar.forState` return it when bound (research R2)
- [X] T005 Add `LabeledField` (label, 6 dp, control, accent error line) and rebuild the `TransactionForm` title area (ACCOUNTS from the shell, account name in subheader secondary, page title "transaction") in `app/src/main/kotlin/app/duenorth/budget/RegisterScreens.kt`

**Checkpoint**: The form can host labelled groups and swap the app bar.

---

## Phase 3: User Story 1 - Pick a category from a dropdown (P1) 🎯 MVP

- [X] T006 [P] [US1] Add `CategoryChoice.hidden` and read `c.hidden OR g.hidden` in `RegisterBook.categories()` in `core/budget/src/main/kotlin/app/duenorth/budget/core/Register.kt`; test in `RegisterBookTest.kt`
- [X] T007 [P] [US1] Move the settings `ListPicker` to `core/design/.../components/MetroComboBox.kt` with `fullScreenThreshold` and `onOpenFull`; switch `SettingsItems.kt` to it with unchanged tags
- [X] T008 [US1] `CategoryOptions` and the `CategoryListPage` overlay (grouped, current in accent, opens scrolled to it, hidden current marked) in `RegisterScreens.kt`
- [X] T009 [US1] Replace the category `ChoiceLine` list in `TransactionForm` with the combo box and overlay; back closes the overlay
- [X] T010 [US1] Content tests: closed box shows current, in place ≤ 5, full screen > 5, pick keeps other edits, back leaves it unchanged, hidden current listed, transfer/split parent shows no box, in `RegisterContentTest.kt`

---

## Phase 4: User Story 2 - Treat the date as a date (P1)

- [X] T011 [P] [US2] `DatePick` (columns, daysIn, withMonth/withDay/withYear, yearRange) and `DateFormats.withWeekday` / `widestSample` in `core/budget/.../core/Models.kt`; `DatePickTest.kt`
- [X] T012 [P] [US2] `RegisterEntry.transaction(date: Int, …)` overload in `Register.kt`
- [X] T013 [P] [US2] `MetroPickerColumn` (looping snapping column) in `core/design/.../components/MetroPickerColumn.kt`
- [X] T014 [US2] Date box (weekday + format, chevron, not focusable) and the "choose date" overlay with done/cancel bar override in `TransactionForm`; pass the date format from `DueNorthApp.kt`
- [X] T015 [US2] Content tests: format and weekday shown, picker opens set to the date, done applies, cancel and back discard, no text field for the date, in `RegisterContentTest.kt`

---

## Phase 5: User Story 3 - A titled, labelled, right-sized form (P2)

- [X] T016 [P] [US3] `MetroSegmentedToggle` in `core/design/.../components/MetroSegmentedToggle.kt`
- [X] T017 [US3] `rememberFieldWidth` and sized date and amount boxes; amount and toggle in a `FlowRow`; payee and note full width
- [X] T018 [US3] Note as a 2–6 line field; `imePadding()` on the form; `adjustResize` in `app/src/main/AndroidManifest.xml`
- [X] T019 [US3] Field-level validation messages (research R13)
- [X] T020 [US3] `registerDetail` flattens line breaks in the note
- [X] T021 [US3] Content tests: labels present with values, title area rows, amount and toggle share a row, toggle picks direction, errors under their field, note line breaks flattened in the register

---

## Phase 6: User Story 4 - Commands on the application bar (P2)

- [X] T022 [US4] `TransactionMenu` and split/schedule/delete menu items for `EditTransaction` in `ScreenBar.kt`; handlers wired in `DueNorthApp.kt`
- [X] T023 [US4] Remove `MetroButtonGrid` and the body save fallback from `TransactionForm`
- [X] T024 [US4] Bar tests per transaction kind in `ScreenBarTest.kt`; no body buttons in `RegisterContentTest.kt`

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T025 Roborazzi light and dark: existing, new, transfer, combo in place, category list, date picker, validation error, 2× font, in `TransactionDetailsScreenshotTest.kt`; record and review
- [ ] T026 Transaction details, category list and date picker frames in `docs/design/metro-mockups.svg` (light and dark)
- [X] T027 Run `./gradlew ktlintCheck :core:budget:test :core:design:test :app:testDebugUnitTest :app:verifyRoborazziDebug`
- [ ] T028 Build a debug APK and before/after screenshots for the PR

## Dependencies

- Phase 2 blocks every story. US1 and US2 are independent of each other. US3 builds on the form after US1/US2 change it. US4 depends only on Phase 2.
- Within a story, core and design tasks marked [P] go first; form wiring and tests follow.

## Parallel Examples

- Phase 2: T002, T003, T004 together.
- US1: T006 with T007. US2: T011, T012, T013 together. US3: T016 alongside US4's T022.

## Implementation Strategy

MVP is US1 (the category list is what makes the screen long). Then US2, then US3 and US4 together. Each story ends with its content tests passing; screenshots are recorded once in Phase 7.
