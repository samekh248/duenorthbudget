# Tasks: Envelope Month

**Input**: Design documents from `/specs/002-envelope-month/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: The spec's independent tests and success criteria require automated checks.

**Organization**: Tasks are grouped by user story so each story can be tested on its own.

## Format: `[ID] [P?] [Story] Description`

## Phase 1: Setup

**Purpose**: Confirm the shell modules this spec extends

- [X] T001 Confirm `:core:budget`, `:core:design`, and `:app` match `specs/002-envelope-month/plan.md`

---

## Phase 2: Foundational

**Purpose**: Month book, leftover figures, and ordered saves. Blocks every story.

- [X] T002 Add per-category leftovers and the tracking carryover walk in `core/budget/src/main/kotlin/app/duenorth/budget/core/ActualMonthMath.kt`. Tracking carries the previous leftover only when that month's carryover is on. Envelope math stays as it is.
- [X] T003 Add `BudgetBook`, `MonthPage`, and `CategoryMonth` in `core/budget/src/main/kotlin/app/duenorth/budget/core/BudgetBook.kt`. Group available sums visible expense categories only. Hidden rows stay in the sheet math.
- [X] T004 Load and save groups, categories, assignments, and buffers in `core/budget/src/main/kotlin/app/duenorth/budget/core/BudgetBookStore.kt`. Envelope writes `zero_budgets`. Tracking writes `reflect_budgets`. Do not write `preferences`.
- [X] T005 Point `core/budget/src/main/kotlin/app/duenorth/budget/core/ShellReader.kt` and `BudgetLibrary.kt` at the book.

**Checkpoint**: The October shell fixture still matches, and a saved book reloads.

---

## Phase 3: User Story 1 - Give money to a category (Priority: P1)

**Goal**: Setting a budgeted amount updates to-budget and available before the save.

**Independent Test**: Assign 120 of 500. To-budget is 380 and available is 120. A lower amount reverses the delta.

- [X] T006 [US1] Parse currency amounts in `core/budget/src/main/kotlin/app/duenorth/budget/core/Models.kt`. Blank text, extra fraction digits, and other words yield no amount.
- [X] T007 [US1] Assign in `core/budget/src/main/kotlin/app/duenorth/budget/core/MonthEdits.kt` and cover it in `core/budget/src/test/kotlin/app/duenorth/budget/core/MonthEditsTest.kt`.
- [X] T008 [US1] Publish the new month on the main thread, then save under a mutex, in `app/src/main/kotlin/app/duenorth/budget/ShellViewModel.kt`.

**Checkpoint**: An assignment is visible before the file write returns.

---

## Phase 4: User Story 2 - Move money between categories (Priority: P2)

**Goal**: A move changes two available amounts and leaves to-budget alone.

**Independent Test**: Move 15 from Groceries 80 to Eating Out 20. Refuse a move larger than the source available.

- [X] T009 [US2] Move, including across groups, in `core/budget/src/main/kotlin/app/duenorth/budget/core/MonthEdits.kt` with the refusal "not enough available".

**Checkpoint**: A refused move leaves both categories unchanged.

---

## Phase 5: User Story 3 - Deal with overspending (Priority: P3)

**Goal**: Uncovered overspend, cover, and rollover follow Actual.

**Independent Test**: Overspend by 30. Next month's to-budget is 30 lower and the category starts at 0. Rollover keeps −30.

- [X] T010 [US3] Carryover on and off in `core/budget/src/main/kotlin/app/duenorth/budget/core/MonthEdits.kt`, with the five overspend cases in `MonthEditsTest.kt`.

**Checkpoint**: Turning rollover off applies the default on the following month.

---

## Phase 6: User Story 4 - Hold money for a later month (Priority: P4)

**Goal**: Hold and release this month's buffered amount.

**Independent Test**: Hold 250 of 400. This month shows 150. Next month includes the 250 and does not add it a second time.

- [X] T011 [US4] Hold and release in `core/budget/src/main/kotlin/app/duenorth/budget/core/MonthEdits.kt`. Refuse an amount outside `0..to-budget + current hold` with "not enough to hold".

**Checkpoint**: Tracking files do not gain a hold.

---

## Phase 7: User Story 5 - Walk the months and the groups (Priority: P5)

**Goal**: Month navigation and group/category editing.

**Independent Test**: Add a group and category, assign, open the next month. The category is there and last month's assignment is unchanged.

- [X] T012 [US5] Add, rename, reorder, hide, unhide, and delete in `core/budget/src/main/kotlin/app/duenorth/budget/core/MonthEdits.kt`. Delete of a row that still has an assignment, available money, or a transaction is refused with "move the money or the history first."
- [X] T013 [US5] Month controls on the budget section in `app/src/main/kotlin/app/duenorth/budget/ShellScreens.kt`.
- [X] T014 [US5] Group, category, hold, and new-group screens in `app/src/main/kotlin/app/duenorth/budget/MonthScreens.kt` and `DueNorthApp.kt`.

**Checkpoint**: A hidden category leaves the group total and returns with its amount.

---

## Phase 8: Polish

- [X] T015 Lazy 300-category list and the unparseable-amount note in `app/src/test/kotlin/app/duenorth/budget/MonthContentTest.kt`.
- [X] T016 Re-record shell screenshots and run `specs/002-envelope-month/quickstart.md`.
- [X] T017 Point the spec table in `README.md` at the spec 002 quickstart.

---

## Dependencies & Execution Order

### Phase Dependencies

- Setup, then foundational month book, then stories P1 through P5. Polish last.
- US2 and US3 use the same assignment rows as US1. US4 uses the envelope header. US5 uses the book.

### Parallel Opportunities

- T006's parser tests and T002's tracking test touch different files and can be written together.
- T013 and T014 wait on the view model from T008.

### Parallel Example: User Story 1

```bash
# Parser and envelope assign checks:
core/budget/src/test/kotlin/app/duenorth/budget/core/MonthEditsTest.kt
core/budget/src/main/kotlin/app/duenorth/budget/core/Models.kt
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Finish the month book.
2. Assign on the category row.
3. Stop and check to-budget and available.

### Incremental Delivery

1. Assign.
2. Move.
3. Overspend and rollover.
4. Hold.
5. Months, groups, and categories.

The spec's independent tests are the story checks. MVP is story 1. The phone build includes all five stories.
