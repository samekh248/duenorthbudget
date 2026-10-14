# Tasks: Onboarding and Instance Setup

**Input**: Design documents from `/specs/011-onboarding-instance/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Included. The plan's Testing section and constitution Principle V require JVM tests for the server check, address parsing and saved progress; Robolectric tests for the flow; and Roborazzi light and dark screenshots of each page.

**Organization**: Tasks are grouped by user story so each story can be implemented and tested on its own.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1–US5)

## Phase 1: Setup

- [X] T001 Confirm no new Gradle module; work stays in `:core:budget` and `:app` per `plan.md`, and `onboarding.json` lives beside `phone.json` under the library root

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Address parsing, the server check, and saved progress that every story uses

- [X] T002 [P] Add `ServerAddress.parse` (trim, trailing slash, sub-path, `https://` then `http://` candidates, host) in `core/budget/src/main/kotlin/app/duenorth/budget/core/ServerAddress.kt`
- [X] T003 [P] Add `ServerCheck` outcomes and `ServerProbe` (`needs-bootstrap`, then `login-methods`; string or object method entries; timeouts; TLS failure stops without downgrade) in `core/budget/src/main/kotlin/app/duenorth/budget/core/ServerProbe.kt` per `contracts/server-check.md`
- [X] T004 [P] Add `OnboardingStep`, `OnboardingProgress` and `OnboardingStore` (`onboarding.json`, never a password) in `core/budget/src/main/kotlin/app/duenorth/budget/core/Onboarding.kt`, and `BudgetLibrary.onboarding()` in `BudgetLibrary.kt`
- [X] T005 Let `SyncCoordinator.connect` / `completeOpenId` take the instance label in `core/budget/src/main/kotlin/app/duenorth/budget/core/SyncCoordinator.kt`
- [X] T006 [P] Add `needs-bootstrap` switches (`bootstrapped`, method objects, HTML mode) to `core/budget/src/test/kotlin/app/duenorth/budget/core/LocalActualServer.kt`
- [X] T007 [P] JVM tests in `ServerAddressTest.kt`, `ServerProbeTest.kt`, `OnboardingStoreTest.kt` under `core/budget/src/test/kotlin/app/duenorth/budget/core/`

**Checkpoint**: The server can be checked and progress saved without any screen.

---

## Phase 3: User Story 1 - Connect to my Actual server on first launch (P1) 🎯 MVP

**Goal**: Fresh install → welcome → address → sign in → pick a budget → home pivot, without settings.

- [X] T008 [US1] Add routes `Welcome`, `OnboardingAddress`, `OnboardingSignIn`, `OnboardingFiles` and `OnboardingUi` state in `app/src/main/kotlin/app/duenorth/budget/ShellViewModel.kt`; `apply()` sends a phone with no budget to the welcome page instead of `Create`
- [X] T009 [US1] Add the step logic (choose path, check, sign in, browser sign-in return, open file, finish) in `app/src/main/kotlin/app/duenorth/budget/ShellViewModelOnboarding.kt`
- [X] T010 [US1] Add the welcome, address, sign-in and file-list pages in `app/src/main/kotlin/app/duenorth/budget/OnboardingScreens.kt` per `contracts/onboarding-screens.md`
- [X] T011 [US1] Wire routes, app bar actions (`next`, `sign in`, `refresh`) and the browser sign-in return in `DueNorthApp.kt` and `ScreenBar.kt`
- [X] T012 [US1] Robolectric flow test against `LocalActualServer` in `app/src/test/kotlin/app/duenorth/budget/OnboardingFlowTest.kt` (fresh phone shows welcome; server path ends on home with the instance active and labelled)

---

## Phase 4: User Story 2 - Start a budget on this phone only (P1)

- [X] T013 [US2] Preselect the region's currency (`Currencies.forLocale`) in `Models.kt` and pass it to `CreateBudgetScreen` in `ShellScreens.kt`
- [X] T014 [US2] Phone-only path and its back behavior in `ShellViewModelOnboarding.kt`; test in `OnboardingFlowTest.kt`

---

## Phase 5: User Story 3 - Clear help when the instance is wrong (P2)

- [X] T015 [US3] Messages for each `ServerCheck` outcome, `try again` and `only on this phone` on the address page; refused password clears the field and keeps the label
- [X] T016 [US3] Probe outcome tests in `ServerProbeTest.kt` (closed port, HTML page, not set up, header-only sign-in, scheme-less address)

---

## Phase 6: User Story 4 - Pick up where I left off (P2)

- [X] T017 [US4] Save progress on each change and resume the step on launch in `ShellViewModelOnboarding.kt`; keep the browser sign-in address across a restart
- [X] T018 [US4] Resume test in `OnboardingFlowTest.kt` (new view model on the same files resumes the address step; no password in `onboarding.json`)

---

## Phase 7: User Story 5 - Server with no budget yet (P3)

- [X] T019 [US5] `new budget` from the file list creates on the phone and sends it in the background; failure leaves it editable

---

## Phase 8: Polish

- [X] T020 [P] Roborazzi light and dark screenshots in `app/src/test/kotlin/app/duenorth/budget/OnboardingScreenshotTest.kt`
- [X] T021 Settings' reachability check uses `ServerProbe` in `ShellViewModel.kt`
- [X] T022 Run `ktlintCheck`, `:core:budget:test`, `:app:testDebugUnitTest`, `:app:verifyRoborazziDebug`
