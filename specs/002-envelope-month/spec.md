# Feature Specification: Envelope Month

**Feature Branch**: `002-envelope-month`

**Created**: 2026-10-06

**Status**: Draft

**Input**: User description: "Android app based on Actual Budget, using the Metro 2 design. The person assigns and moves envelope money for a month. The screen must stay fluid."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Give money to a category (Priority: P1)

A person looks at this month and puts some of the money left to budget into
a category. The category's available amount goes up and to-budget goes down
before their finger lifts. They can also take money back out of a category.

**Why this priority**: Assigning money you already have is the envelope
budget. Without it the panorama is only a report.

**Independent Test**: Start a month with 500 to budget and one category at
zero. Assign 120. Confirm to-budget is 380 and the category's available is
120, with no network. Assign a negative amount and confirm the reverse.

**Acceptance Scenarios**:

1. **Given** the month has money to budget and a category with available 0, **When** the person assigns 120 to that category, **Then** the category shows budgeted 120 and available 120, and to-budget falls by 120, before the finger lifts.
2. **Given** a category has budgeted money, **When** the person reduces the budgeted amount, **Then** to-budget rises by the same amount and the available amount falls by the same amount.
3. **Given** the person types an amount they cannot parse, **When** they confirm, **Then** nothing changes and the field explains what it expected.
4. **Given** the budget is a tracking budget, **When** the person sets a category's budgeted amount, **Then** the file stays a tracking budget and the month does not demand that to-budget reach zero.

---

### User Story 2 - Move money between categories (Priority: P2)

A person moves available money from one category to another in the same
month. To-budget does not change. Both rows update together.

**Why this priority**: Covering a short category from a long one is the
everyday correction, and it must not look like new income.

**Independent Test**: With Groceries available 80 and Eating Out available
20, move 15 from Groceries to Eating Out. Confirm the two available amounts
and that to-budget is unchanged.

**Acceptance Scenarios**:

1. **Given** two categories in the same month, **When** the person moves 15 from the first to the second, **Then** the first available falls by 15, the second rises by 15, and to-budget is unchanged.
2. **Given** the person tries to move more than the source has available, **When** they confirm, **Then** the app refuses the move and both categories stay as they were.
3. **Given** the person moves money, **When** they leave the month and come back, **Then** the same available amounts are still shown.

---

### User Story 3 - Deal with overspending (Priority: P3)

A category that was spent below zero is obvious. The person can cover it
from another category, or leave it so the overspend comes out of next month's
to-budget and this category returns to zero next month. They can also set a
category to keep a negative balance into future months.

**Why this priority**: Actual's envelope rules are easy to get wrong. The
phone must do the same thing the desktop does, or the two copies diverge.

**Independent Test**: Overspend Groceries by 30 with no cover. Open next
month and confirm to-budget is 30 lower and Groceries starts at zero. Repeat
on a category set to roll the negative balance forward, and confirm next
month still shows −30 on that category.

**Acceptance Scenarios**:

1. **Given** a category is overspent and is not set to roll the negative balance, **When** the person opens the next month without covering it, **Then** that category starts at zero and next month's to-budget is lower by the overspent amount.
2. **Given** a category is overspent, **When** the person covers it from another category that has enough available, **Then** both categories show the moved amount and next month's to-budget is not reduced for that overspend.
3. **Given** the person sets "rollover overspending" on a category, **When** they open the next month, **Then** the negative available amount is still on that category and was not taken from to-budget.
4. **Given** the person turns "rollover overspending" back off, **When** the following month opens, **Then** the category returns to Actual's default overspend rule from that month forward.

---

### User Story 4 - Hold money for a later month (Priority: P4)

A person can hold this month's leftover to-budget so it becomes available in
a chosen future month, and can release it back.

**Why this priority**: People set aside a paycheck for next month's rent.
The phone has to show the same held amount Actual shows.

**Independent Test**: With 400 to budget, hold 250 for next month. Confirm
this month's to-budget is 150 and next month's to-budget includes the 250.

**Acceptance Scenarios**:

1. **Given** this month has money to budget, **When** the person holds 250 for next month, **Then** this month's to-budget falls by 250 and next month's to-budget rises by 250.
2. **Given** money is held for next month, **When** the person releases it, **Then** it returns to this month's to-budget.
3. **Given** the person tries to hold more than this month has to budget, **When** they confirm, **Then** the app refuses and no month changes.

---

### User Story 5 - Walk the months and the groups (Priority: P5)

The person moves to the previous or next month and sees that month's
budgeted, spent, and available amounts. They can add, rename, hide, and
reorder category groups and categories. Hidden categories stay out of the
month until shown again.

**Why this priority**: A single month with fixed categories is not a budget
someone can live in. It is still the same spec because it is the month
surface, not the register.

**Independent Test**: Add a group and a category, assign money, go forward a
month, and confirm the new category is there and last month's assignment did
not change.

**Acceptance Scenarios**:

1. **Given** the person is on the current month, **When** they move one month forward and one month back, **Then** each month shows its own budgeted, spent, and available amounts.
2. **Given** the person adds a category group and a category, **When** they return to the month, **Then** the new category is listed in that group and can receive an assignment.
3. **Given** a category is hidden, **When** the month is shown, **Then** the category is absent and its group total no longer includes it. Unhiding brings it back with its amounts.
4. **Given** the person renames or reorders groups, **When** they restart the app, **Then** the names and order are unchanged.
5. **Given** a group contains many categories, **When** the person scrolls and edits one amount, **Then** the row they are editing stays under their finger and the list does not jump.

---

### Edge Cases

- Assigning on a month with no income leaves to-budget negative, and the
  screen says so. The app does not invent income.
- Income categorized to an income category increases to-budget. That entry
  itself is a transaction in the register spec; this spec must reflect the
  new to-budget as soon as the file has it.
- Two categories in different groups can still exchange money.
- A category with rollover from many previous months shows one available
  number, not a running history, on the month row.
- Deleting a category that still has money or transactions is refused, with
  a note that the money or history has to move first.
- Envelope and tracking files use the same month screen. Tracking files do
  not reset leftover available into next month unless the category is set to
  roll overspending.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The person MUST be able to set a category's budgeted amount for
  the open month. To-budget and that category's available amount MUST change
  by the same delta before the finger lifts.
- **FR-002**: The person MUST be able to move a stated amount of available
  money from one category to another in the same month without changing
  to-budget.
- **FR-003**: A move larger than the source available amount MUST be refused
  and MUST leave both categories unchanged.
- **FR-004**: For an envelope budget, uncovered overspending MUST reduce next
  month's to-budget and the overspent category MUST start the next month at
  zero, unless that category is set to roll the negative balance.
- **FR-005**: The person MUST be able to turn rollover-overspending on or off
  for a category. The following months MUST follow Actual's rule for that
  setting.
- **FR-006**: The person MUST be able to hold a portion of this month's
  to-budget for a future month and to release it. The app MUST refuse a hold
  larger than the amount available to hold.
- **FR-007**: The person MUST be able to move at least one month forward and
  one month back and see that month's own budgeted, spent, and available
  figures.
- **FR-008**: The person MUST be able to add, rename, hide, unhide, and
  reorder category groups and categories. A hidden category MUST leave the
  month view and MUST return with its amounts when unhidden.
- **FR-009**: The person MUST NOT be able to delete a category or group that
  still has assignments, available money, or transactions.
- **FR-010**: The phone MUST NOT change a file from envelope mode to tracking
  mode, or the reverse, as a side effect of these actions.
- **FR-011**: Every edited figure MUST be readable again after a restart and
  MUST match the on-phone budget file.
- **FR-012**: Editing, month changes, and scrolling a long category list MUST
  stay smooth. A save MUST NOT block the screen and MUST NOT jump the list.
- **FR-013**: This spec does not enter or edit transactions, sync to a
  server, or reconcile accounts.

### Key Entities

- **Month**: A calendar month in the open budget. Has a to-budget amount and
  a set of category rows.
- **Category**: Belongs to one group. For a month it has budgeted, spent,
  and available amounts, plus a rollover-overspending setting.
- **Category group**: An ordered, named collection of categories. Can be
  hidden as a group only by hiding its categories; the group itself can be
  renamed and reordered.
- **Hold**: An amount of this month's to-budget reserved for a later month.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: After the person confirms an assignment, the new to-budget and
  the category's available amount are both visible in under 100 ms, measured
  without a network.
- **SC-002**: In a checklist of the five overspend and hold cases in the
  stories above, the phone's amounts match a desktop Actual file that
  received the same actions, for every case.
- **SC-003**: A person can assign to three categories in one month in under
  30 seconds, with no full-screen wait between them.
- **SC-004**: Scrolling a month of 300 categories while changing one amount
  shows no visible stutter and the edited row does not leave the finger.

## Assumptions

- The Metro shell from `specs/001-metro-budget-shell` is present. This spec
  adds the month editor on that panorama and on a category row.
- "To budget", available, and rollover follow Actual's envelope rules. This
  spec does not define a different formula.
- Income arrives through categorized transactions (spec 003) or through a
  preloaded file. This spec does not type a new transaction.
- Credit-card payment categories are updated by spending in spec 003. This
  spec shows whatever available amount the file already has for them.
- Goal templates and scheduled targets are out of scope. Category notes may
  exist on the file and are left unchanged.
- Tests may preload a budget so each story can run without the later specs.
