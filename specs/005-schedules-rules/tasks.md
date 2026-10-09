# Tasks: Schedules, Rules, and Payees

**Input**: `/specs/005-schedules-rules/`

## Phase 1: Schema

- [X] T001 Actual-shaped `rules`, `schedules`, `schedules_next_date`, and `transactions.schedule` in `ActualSchema.kt`

## Phase 2: Core

- [X] T002 Rules engine and disabled-rule preference in `RuleEngine.kt`
- [X] T003 Schedules read/post/skip/create in `Schedules.kt`
- [X] T004 Payee rename, merge, delete in `Payees.kt`
- [X] T005 Rule application on save in `Register.kt`; library APIs in `BudgetLibrary.kt`
- [X] T006 JVM tests in `ScheduleRulesBookTest.kt`

## Phase 3: UI

- [X] T007 Due panorama section in `ScheduleScreens.kt` and `ShellScreens.kt`
- [X] T008 Payees screen and schedule actions in `PayeeScreens.kt`, `DueNorthApp.kt`, `ShellViewModel.kt`
- [X] T009 Create schedule from transaction in `RegisterScreens.kt`
