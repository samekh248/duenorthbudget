# Tasks: Import Transactions

**Input**: `/specs/007-import-transactions/`

## Phase 1: Schema

- [X] T001 `financial_id`, `imported_description`, and bank columns in `ActualSchema.kt`

## Phase 2: Core

- [X] T002 QIF, OFX, and CSV parsing in `ImportFileParser.kt`
- [X] T003 Preview, confirm, duplicate detection, and batch in `TransactionImport.kt`
- [X] T004 Rules on import insert in `Register.kt` (`insertImportedTransaction`)
- [X] T005 SimpleFIN-shaped bank fetch in `BankSync.kt`
- [X] T006 Library APIs in `BudgetLibrary.kt`
- [X] T007 JVM tests in `ImportBookTest.kt`

## Phase 3: UI

- [X] T008 Preview and review screens in `ImportScreens.kt`
- [X] T009 Register actions, routes, and file picker in `RegisterScreens.kt`, `ShellViewModel.kt`, `DueNorthApp.kt`
