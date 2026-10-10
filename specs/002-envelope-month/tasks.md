# Tasks: Envelope Month

**Input**: Design documents from `/specs/002-envelope-month/`

**Prerequisites**: plan.md, spec.md, shell from spec 001

**Tests**: Envelope math in `ActualMonthMathTest`; envelope writes in `EnvelopeMonthTest`.

## Phase 1: Foundational (Blocking)

- [X] T001 Envelope and tracking month math in `core/budget/.../ActualMonthMath.kt` (overspend, carryover, hold buffer) per spec 001 contracts
- [X] T002 Read month figures in `core/budget/.../ShellReader.kt` including hidden categories excluded from group totals

## Phase 2: User Story 1 - Give money to a category (P1)

- [X] T003 `EnvelopeBook.assignBudgeted` and `SyncCoordinator.assign` in `EnvelopeMonth.kt` / `SyncCoordinator.kt`
- [X] T004 Category budget screen and save path in `EnvelopeScreens.kt`, `ShellViewModel.kt`, `DueNorthApp.kt`
- [X] T005 JVM test `assignChangesToBudgetAndAvailable` in `EnvelopeMonthTest.kt`

## Phase 3: User Story 2 - Move money between categories (P2)

- [X] T006 `moveAvailable` with refusal when amount exceeds source in `EnvelopeMonth.kt`
- [X] T007 Move screen in `EnvelopeScreens.kt` and `submitMoveCategory` in `ShellViewModel.kt`
- [X] T008 JVM tests `moveKeepsToBudget` and `moveRefusesTooMuch` in `EnvelopeMonthTest.kt`

## Phase 4: User Story 3 - Overspending (P3)

- [X] T009 Rollover overspending flag via `setCarryover` in `EnvelopeMonth.kt`
- [X] T010 Toggle on category budget screen in `EnvelopeScreens.kt`
- [X] T011 Overspend and carryover cases in `ActualMonthMathTest.kt`

## Phase 5: User Story 4 - Hold for a later month (P4)

- [X] T012 `setHold` / `releaseMonthHold` in `EnvelopeMonth.kt` and `SyncCoordinator.kt`
- [X] T013 Hold screen and release in `EnvelopeScreens.kt` / `ShellViewModel.kt`
- [X] T014 JVM test `holdLowersThisMonth` in `EnvelopeMonthTest.kt`

## Phase 6: User Story 5 - Months and categories (P5)

- [X] T015 Previous/next month on budget section in `ShellScreens.kt` and `ShellViewModel.kt`
- [X] T016 Add group and category in `EnvelopeBook` and `ManageCategoriesScreen` (add only)

---

## Phase 7: Convergence

- [X] T017 Expose `renameGroup` and `renameCategory` through `SyncCoordinator` and `BudgetLibrary`, with JVM coverage per FR-008 (missing)
- [X] T018 Extend `ManageCategoriesScreen` (or a detail route) so the person can rename groups and categories per FR-008 / US5 (missing)
- [X] T019 Wire `hideEnvelopeCategory` from `ShellViewModel` and add hide/unhide controls; confirm hidden categories leave the month view per FR-008 (partial)
- [X] T020 Implement reorder for groups and categories (`sort_order` updates in `EnvelopeBook`, sync APIs, drag or move-up/down UI) per FR-008 / US5 (missing)
- [X] T021 Add `deleteGroup` with `GROUP_STILL_USED` guard, expose delete for empty categories/groups in UI per FR-009 (missing)
- [X] T022 Show assign/move/hold results on the shell before IO returns (optimistic or in-memory month refresh) per FR-001, SC-001, and Constitution I (partial)
- [X] T023 Compose or JVM test: scroll a long category list while editing one budgeted amount without list jump per SC-004 (missing)
