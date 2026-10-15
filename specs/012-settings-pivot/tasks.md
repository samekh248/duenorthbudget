# Tasks: Settings Pivot

**Input**: Design documents from `/specs/012-settings-pivot/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Included. Principle V and the plan's Testing section require Robolectric content tests for the pivot and return-to-item, a JVM test for the onboarding origin, and Roborazzi light and dark screenshots of each item and new page.

**Organization**: Tasks are grouped by user story so each story can be implemented and tested on its own.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1–US5)

## Phase 1: Setup

- [X] T001 Confirm no new Gradle module; work stays in `:core:design`, `:core:budget` and `:app` per `plan.md`

---

## Phase 2: Foundational (Blocking Prerequisites)

- [X] T002 [P] Add `onSelected: (Int) -> Unit` to `MetroPivot`, fired when the settled page changes, in `core/design/src/main/kotlin/app/duenorth/budget/design/components/MetroComponents.kt`
- [X] T003 [P] Add `OnboardingOrigin { FIRST_RUN, SETTINGS }` and `OnboardingProgress.origin` in `core/budget/src/main/kotlin/app/duenorth/budget/core/Onboarding.kt`; JVM test that a file without `origin` reads as `FIRST_RUN`
- [X] T004 Add `SettingsPage`, `ShellUiState.settingsPage` / `settingsScroll`, `selectSettingsPage()`, and reset to `BUDGET` in `showSettings()` in `app/src/main/kotlin/app/duenorth/budget/ShellViewModel.kt`

**Checkpoint**: State exists to know and restore the settings item.

---

## Phase 3: User Story 1 - Find a setting without scrolling past everything (P1) 🎯 MVP

- [X] T005 [US1] Split sections into `BudgetItem`, `ServerItem`, `FormatItem`, `AppearanceItem`, `AdvancedItem` in new `app/src/main/kotlin/app/duenorth/budget/SettingsItems.kt` per `contracts/settings-pivot.md`
- [X] T006 [US1] Make `SettingsScreen` a `MetroPivot` titled "SETTINGS" over the visible `SettingsPage`s in `app/src/main/kotlin/app/duenorth/budget/SettingsScreens.kt`
- [X] T007 [US1] Settings app bar per item (budget: new, payees; server: add, server budgets, sign out; advanced: export) in `app/src/main/kotlin/app/duenorth/budget/ScreenBar.kt` and handlers in `DueNorthApp.kt`
- [X] T008 [US1] Remove the unreachable `ShellRoute.Appearance`, `showAppearance()` and `AppearanceScreen`
- [X] T009 [US1] Content tests: headers with and without a budget, header tap, each item's controls, app bar per item in `app/src/test/kotlin/app/duenorth/budget/SettingsContentTest.kt` and `ScreenBarTest.kt`

---

## Phase 4: User Story 2 - Add and choose a server the onboarding way (P1)

- [X] T010 [US2] `onboardingAddServer()` and origin-aware back, sign-in finish, browser finish and restore in `app/src/main/kotlin/app/duenorth/budget/ShellViewModelOnboarding.kt` (research R4)
- [X] T011 [US2] Settings-origin sign-in page line naming the server that stays saved in `app/src/main/kotlin/app/duenorth/budget/OnboardingScreens.kt` (research R6)
- [X] T012 [US2] "server budgets": `Server` page with a budget open, `OnboardingFiles` without (research R5)
- [X] T013 [US2] Confirm page for removing a server (`ShellRoute.ConfirmRemoveInstance`) (research R8)
- [X] T014 [US2] Flow tests: add from settings returns to the server item; back from address returns with nothing saved; remove asks first, in `app/src/test/kotlin/app/duenorth/budget/OnboardingFlowTest.kt`

---

## Phase 5: User Story 3 - Edit forms open only when asked (P2)

- [X] T015 [US3] "new" opens `CreateBudgetScreen` with the region currency; success returns to settings on the budget item (replaces `createFromSettings`' hard-coded USD)
- [X] T016 [US3] `EncryptionPasswordScreen(mode)` and `ShellRoute.EncryptionPassword` with save and cancel; success returns to advanced
- [X] T017 [US3] Test that no `settings-item-*` contains a text field

---

## Phase 6: User Story 4 - Keep risky actions apart (P2)

- [X] T018 [US4] Rebuild, repair, reset sync, ids, export and encryption only on the advanced item; test that the other items have none

---

## Phase 7: User Story 5 - Come back to the same place (P3)

- [X] T019 [US5] Every settings sub-page (create, payees, server, confirm pages, encryption, add server) returns to `Settings` on its item; scroll offset restored
- [X] T020 [US5] Test return-to-item for advanced and server; reopening from home shows budget

---

## Phase 8: Polish

- [X] T021 [P] Roborazzi light and dark for each item, encryption page modes, remove-server confirm in `app/src/test/kotlin/app/duenorth/budget/SettingsScreenshotTest.kt`; record snapshots
- [X] T022 [P] Settings rows in `docs/design/metro-mockups.svg` and a line in `docs/design/README.md`
- [ ] T023 Run `./gradlew ktlintCheck :core:budget:test :app:testDebugUnitTest :app:verifyRoborazziDebug`; before-and-after screenshots for the PR
