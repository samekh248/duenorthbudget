# Tasks: Account Register

**Input**: Design documents from `/specs/003-account-register/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Included. The constitution requires screenshot checks, envelope math tests that do not draw, and a fluidity check for a long register and a refresh during a gesture.

**Organization**: Tasks are grouped by user story so each story can be implemented and tested on its own.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4, US5, US6, US7)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Extend the existing Actual-shaped file so a register can name a credit card

- [X] T001 Add `accounts.type TEXT NOT NULL DEFAULT 'checking'` and `ActualSchema.ensure` in `core/budget/src/main/kotlin/app/duenorth/budget/core/ActualSchema.kt` (missing column becomes `checking`; `dueNorthSchema` stays `1`)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared read and write types every story uses

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T002 Exclude off-budget accounts from category spent in `core/budget/src/main/kotlin/app/duenorth/budget/core/ShellReader.kt` (on-budget and missing accounts still count; parents stay excluded)
- [X] T003 Add register page, draft, and validation types in `core/budget/src/main/kotlin/app/duenorth/budget/core/Register.kt` (blank payee is "enter a payee"; missing amount is "enter an amount"; zero is "amount cannot be zero"; bad date is "enter a date")
- [X] T004 Open the budget database through `ActualSchema.ensure` in `core/budget/src/main/kotlin/app/duenorth/budget/core/BudgetLibrary.kt` before shell reads and register writes

**Checkpoint**: Older files gain `accounts.type`. Off-budget activity cannot change to-budget.

---

## Phase 3: User Story 1 - Read an account (Priority: P1) 🎯 MVP

**Goal**: Opening an account shows its balance and transactions, newest first, with date, payee, category, and amount.

**Independent Test**: Open an account preloaded with 500 transactions. The latest row is visible immediately, and the balance equals the transaction total.

### Tests for User Story 1

- [X] T005 [P] [US1] Register order, off-budget balance, and a 500-row read in under 1 second in `core/budget/src/test/kotlin/app/duenorth/budget/core/RegisterBookTest.kt`
- [X] T006 [P] [US1] Newest row, owed credit balance, and a lazy 500-row list in `app/src/test/kotlin/app/duenorth/budget/RegisterContentTest.kt`

### Implementation for User Story 1

- [X] T007 [US1] Read one account's register in `core/budget/src/main/kotlin/app/duenorth/budget/core/Register.kt` (newest first by date, `sort_order`, then id; parents shown once with nested children; balance is the alive sum)
- [X] T008 [US1] Register screen in `app/src/main/kotlin/app/duenorth/budget/RegisterScreens.kt` (account name, "balance", signed amount, "owed" when a credit balance is negative, "no transactions" when empty, list state not saved)
- [X] T009 [US1] Open the register from an account row in `app/src/main/kotlin/app/duenorth/budget/ShellScreens.kt`, `ShellViewModel.kt`, and `DueNorthApp.kt`

**Checkpoint**: User Story 1 is visible from the accounts section.

---

## Phase 4: User Story 2 - Record spending or income (Priority: P2)

**Goal**: Adding a transaction updates the account balance and, for an on-budget category, that category's spent and available amounts.

**Independent Test**: Add a 12.40 grocery expense. Balance, spent, and available move by 1240 minor units with no network.

### Tests for User Story 2

- [X] T010 [P] [US2] Expense, income, validation, other month, and one row for a repeated id in `core/budget/src/test/kotlin/app/duenorth/budget/core/RegisterBookTest.kt`

### Implementation for User Story 2

- [X] T011 [US2] Save a draft by id in `core/budget/src/main/kotlin/app/duenorth/budget/core/Register.kt` (reuse a payee by case-insensitive name; second save updates; zero and blank fields refuse)
- [X] T012 [US2] Entry form in `app/src/main/kotlin/app/duenorth/budget/RegisterScreens.kt` and the save path in `ShellViewModel.kt` (draft id created once per form; disk write on the IO dispatcher)

**Checkpoint**: User Stories 1 and 2 work together.

---

## Phase 5: User Story 3 - Categorize from the inbox (Priority: P3)

**Goal**: Setting, changing, or clearing a category updates both categories and the inbox.

**Independent Test**: Categorize one inbox transaction. It leaves the inbox and the chosen category's spent includes it.

### Tests for User Story 3

- [X] T013 [P] [US3] Assign, change, and clear a category in `core/budget/src/test/kotlin/app/duenorth/budget/core/RegisterBookTest.kt`

### Implementation for User Story 3

- [X] T014 [US3] `setCategory` in `core/budget/src/main/kotlin/app/duenorth/budget/core/Register.kt` (null category returns an on-budget non-transfer to the inbox)
- [X] T015 [US3] Inbox category screen in `app/src/main/kotlin/app/duenorth/budget/RegisterScreens.kt`, opened from `ShellScreens.kt`

**Checkpoint**: The inbox count falls when a row gains a category.

---

## Phase 6: User Story 4 - Split one purchase (Priority: P4)

**Goal**: A transaction can be split across categories, and the parts sum to the amount.

**Independent Test**: Split 30.00 into 20.00 and 10.00. Both categories move, and the balance moves once.

### Tests for User Story 4

- [X] T016 [P] [US4] Split, refused sum, and unsplit in `core/budget/src/test/kotlin/app/duenorth/budget/core/RegisterBookTest.kt` (a refused split leaves the row unchanged)

### Implementation for User Story 4

- [X] T017 [US4] Split and unsplit in `core/budget/src/main/kotlin/app/duenorth/budget/core/Register.kt` (at least two parts; each amount non-zero; each category living; sum must equal the parent)
- [X] T018 [US4] Split form in `app/src/main/kotlin/app/duenorth/budget/RegisterScreens.kt` (parts shown on the register row)

**Checkpoint**: One register row can carry two categories.

---

## Phase 7: User Story 5 - Transfer between accounts (Priority: P5)

**Goal**: A transfer moves both balances and deletes as a pair.

**Independent Test**: Transfer 100.00 between two on-budget accounts. To-budget is unchanged. Deleting one side removes the other.

### Tests for User Story 5

- [X] T019 [P] [US5] On-budget transfer, off-budget transfer with a category, and paired delete in `core/budget/src/test/kotlin/app/duenorth/budget/core/RegisterBookTest.kt`

### Implementation for User Story 5

- [X] T020 [US5] Transfer writes in `core/budget/src/main/kotlin/app/duenorth/budget/core/Register.kt` (opposite amounts; `transferred_id`; category only on the on-budget side when exactly one account is off-budget; both-on-budget categories stay null)
- [X] T021 [US5] Transfer form in `app/src/main/kotlin/app/duenorth/budget/RegisterScreens.kt`

**Checkpoint**: Transfers do not look like a second expense.

---

## Phase 8: User Story 6 - Credit card spending (Priority: P6)

**Goal**: On an envelope budget, card spending moves available into the payment category. Paying the card moves it back. Tracking budgets do not.

**Independent Test**: Spend 40.00 on a card from groceries that had 40.00. Groceries available is 0 and the payment category available is 40.00. Transfer 40.00 from checking and the payment category falls by 40.00.

### Tests for User Story 6

- [X] T022 [P] [US6] Envelope card spend and payment, plus a tracking file with no move, in `core/budget/src/test/kotlin/app/duenorth/budget/core/RegisterBookTest.kt`

### Implementation for User Story 6

- [X] T023 [US6] Apply the `ccPayment:<accountId>` projection inside `core/budget/src/main/kotlin/app/duenorth/budget/core/ShellReader.kt` (envelope only; negation of card spending and of on-budget card-side transfer amounts; no extra transaction row)

**Checkpoint**: Envelope and tracking card behavior both match the spec scenarios.

---

## Phase 9: User Story 7 - Correct a transaction (Priority: P7)

**Goal**: Edits and deletes update balances. A reconciled row warns first. A note-only edit does not move money.

**Independent Test**: Change 10.00 to 14.00 and both the account and the category move by 4.00. Delete another row and both totals release it.

### Tests for User Story 7

- [X] T024 [P] [US7] Amount edit, delete, note-only edit, and the reconcile warning in `core/budget/src/test/kotlin/app/duenorth/budget/core/RegisterBookTest.kt` (message "this reconciliation will no longer match"; no write until force)

### Implementation for User Story 7

- [X] T025 [US7] Edit and delete, including the reconcile guard, in `core/budget/src/main/kotlin/app/duenorth/budget/core/Register.kt`
- [X] T026 [US7] Warning screen in `app/src/main/kotlin/app/duenorth/budget/RegisterScreens.kt` ("change" writes, "keep" does not) wired in `DueNorthApp.kt`

**Checkpoint**: Corrections follow the row, and a reconciled row waits for confirmation.

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: Filter, screenshots, and the gesture gate

- [X] T027 Payee filter that does not delete rows in `app/src/main/kotlin/app/duenorth/budget/RegisterScreens.kt`, covered in `app/src/test/kotlin/app/duenorth/budget/RegisterContentTest.kt`
- [X] T028 Hold a register refresh while the list scrolls, using `RefreshGate` in `app/src/main/kotlin/app/duenorth/budget/ShellViewModel.kt`
- [X] T029 [P] Light and dark screenshots of the register and the entry form in `app/src/test/kotlin/app/duenorth/budget/RegisterScreenshotTest.kt`
- [X] T030 Quickstart commands in `specs/003-account-register/quickstart.md` and the spec index line in `README.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- Setup (Phase 1) before foundational (Phase 2).
- Foundational before any user story.
- US1 before the screens that open a row. US2 through US7 depend on the register types from Phase 2 and on the US1 read model.
- US6's projection depends on spent from Phase 2 and on transfers from US5 only for the payment half. The spend half can be tested as soon as US2 save exists.
- Polish after the screens exist.

### User Story Dependencies

- **US1**: After foundational. No other story.
- **US2**: After US1 so the new row has a register to appear on.
- **US3**: After US2's category field. Inbox already exists from spec 001.
- **US4**: After US2. Uses the same row.
- **US5**: After US2's accounts. Does not require a split.
- **US6**: After US2 and US5. Tracking path does not require the envelope move.
- **US7**: After US2. Reuses save and delete.

### Parallel Opportunities

- T005 and T006 can be written beside each other.
- T010, T013, T016, T019, T022, and T024 touch the same test file, so they land in one test class in story order.
- T029 can be written beside the filter test once the composables exist.

---

## Parallel Example: User Story 1

```text
T005 Register read tests in core/budget
T006 Register compose tests in app
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Schema ensure and off-budget spent.
2. Read the register and show it from the accounts section.
3. Stop and check a 500-row account before entry.

### Incremental Delivery

1. US1 read.
2. US2 add, then US3 categorize.
3. US4 split and US5 transfer.
4. US6 card projection.
5. US7 edit, delete, and the reconcile warning.
6. Filter, gesture hold, and screenshots.

---

## Notes

- Checkbox, task id, optional `[P]`, story label on story tasks, and a file path on every task.
- Payee rules, schedules, reconciliation ceremony, and file import stay in later specs.
