# Feature Specification: Reconcile and Review

**Feature Branch**: `006-reconcile-review`

**Created**: 2026-10-06

**Status**: Draft

**Input**: User description: "On the Metro 2 Actual Budget app, reconcile an account against a statement and review spending and net worth. The review must stay as smooth as the register."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Reconcile an account (Priority: P1)

A person compares an account to a statement. They enter the statement
balance and date, mark transactions that have cleared, and finish when the
cleared total matches. The app shows how far off they are while they work.
Finishing locks those transactions as reconciled. Cancelling leaves the
account as it was.

**Why this priority**: Reconciliation is how someone trusts the balance.
Reports on a balance they have not checked are less useful.

**Independent Test**: Start a reconciliation to a statement balance that
matches three of five transactions. Mark those three, finish, and confirm
they are reconciled. Reopen and confirm the other two are not.

**Acceptance Scenarios**:

1. **Given** an account and a statement balance, **When** the person starts a reconciliation, **Then** they see the difference between the cleared transactions and the statement, and it updates as they mark transactions.
2. **Given** the difference is zero, **When** the person finishes, **Then** every marked transaction is reconciled and the account remembers the statement balance and date.
3. **Given** the difference is not zero, **When** the person tries to finish, **Then** the app refuses and tells them the remaining difference.
4. **Given** a reconciliation is in progress, **When** the person cancels, **Then** no transaction changes its reconciled state.
5. **Given** transactions are reconciled, **When** the person later tries to edit one from the register, **Then** the register spec's warning still applies.

---

### User Story 2 - See where the month went (Priority: P2)

A person opens a review of one month and sees spending by category, largest
first, and the month's income. They can move to the previous month. The
figures match the budget screen for that month.

**Why this priority**: After reconciling, the next question is where the
money went. One honest month view is enough for the phone.

**Independent Test**: With two categories spent 40 and 10, and income of
100, open the review and confirm those three numbers and that they match the
month screen.

**Acceptance Scenarios**:

1. **Given** a month has categorized spending, **When** the person opens the review, **Then** each spent category shows its spent amount, largest first, and the total matches the sum of those categories.
2. **Given** the person moves to the previous month, **When** the review updates, **Then** it shows that month's spending and income, not a mix of both months.
3. **Given** a category has no spending, **When** the review is shown, **Then** that category is omitted rather than shown as zero noise.
4. **Given** the person taps a category, **When** the list opens, **Then** they see the transactions that make up that spent amount and no others.

---

### User Story 3 - See net worth (Priority: P3)

A person sees one net-worth figure: on-budget accounts, off-budget accounts,
and the total. It matches the account balances on the panorama. They can
include or exclude off-budget accounts and the choice is remembered.

**Why this priority**: Net worth is the long view. It is a separate story
because it is not needed to finish a statement.

**Independent Test**: With checking 1000, a credit card balance owed of 200,
and an off-budget investment of 500, confirm on-budget net worth of 800 and
a total of 1300 when off-budget is included.

**Acceptance Scenarios**:

1. **Given** on-budget and off-budget accounts exist, **When** the person opens net worth, **Then** they see both subtotals and a total that equals their sum.
2. **Given** the person hides off-budget accounts, **When** the total updates, **Then** it equals the on-budget subtotal only, and the choice remains after a restart.
3. **Given** an account balance changes in the register, **When** the person returns to net worth, **Then** the figure includes that change without a manual refresh.
4. **Given** there are no accounts, **When** the person opens net worth, **Then** they see a zero total and a short note, not an error.

---

### Edge Cases

- A statement balance with more decimal places than the budget's currency is
  refused.
- Starting a second reconciliation on the same account while one is unfinished
  resumes the unfinished one rather than creating two.
- Income categories appear in the month review as income, not as spending.
- Transfers between on-budget accounts do not appear as spending in the
  month review.
- A credit-card payment transfer does not show up as spending in both the
  card and the category that was already spent.
- A long category list in the review scrolls without stutter, same as the
  register.
- Reconciling does not change budgeted amounts. It only changes cleared and
  reconciled state.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The person MUST be able to start a reconciliation by entering
  a statement balance and date for one account.
- **FR-002**: While reconciling, the person MUST see the running difference
  and MUST be able to mark or unmark each transaction as cleared.
- **FR-003**: Finishing MUST be allowed only when the difference is zero.
  Finishing MUST mark the selected transactions reconciled and MUST store the
  statement balance and date.
- **FR-004**: Cancelling a reconciliation MUST leave every reconciled state
  as it was when the reconciliation started.
- **FR-005**: An unfinished reconciliation on an account MUST be resumed
  rather than duplicated.
- **FR-006**: The month review MUST list categories with spending for that
  month, largest first, plus the month's income, and the totals MUST match
  the month screen.
- **FR-007**: Transfers between on-budget accounts MUST NOT be counted as
  spending in the month review.
- **FR-008**: The person MUST be able to open the transactions behind one
  category's spent amount for that month.
- **FR-009**: Net worth MUST show on-budget, off-budget, and combined totals
  that equal the account balances. The person MUST be able to hide
  off-budget accounts, and that choice MUST persist.
- **FR-010**: These screens MUST read the on-phone budget and MUST stay
  smooth while scrolling. They MUST NOT wait on a network.
- **FR-011**: Reconciliation MUST NOT change budgeted amounts or to-budget.

### Key Entities

- **Reconciliation**: An in-progress or finished comparison of one account to
  a statement balance and date.
- **Month review**: Spending and income for one month, derived from
  categorized transactions.
- **Net worth**: The sum of account balances, with off-budget accounts
  optional.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Marking a transaction during reconciliation updates the
  difference on screen in under 100 ms.
- **SC-002**: A finished reconciliation of 50 transactions leaves each marked
  transaction reconciled and the stored statement balance equal to what the
  person entered. A cancelled run changes nothing.
- **SC-003**: For a fixture month, the review's category totals and income
  equal the month screen and equal the same file opened in desktop Actual.
- **SC-004**: A person can mark ten transactions and see the difference reach
  zero in under 60 seconds on a preloaded account.

## Assumptions

- The register (`specs/003-account-register`) is where transactions are
  edited. This spec only marks cleared and reconciled, and relies on the
  register to warn before a reconciled transaction changes.
- Custom reports, graphs beyond a simple ordered list, and exporting a report
  are out of scope.
- "Largest first" uses the spent amount's magnitude for that month.
- Net worth uses the current account balances, not a historical chart. A
  history chart is out of scope.
