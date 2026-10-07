# Feature Specification: Account Register

**Feature Branch**: `003-account-register`

**Created**: 2026-10-06

**Status**: Draft

**Input**: User description: "On the Metro 2 Actual Budget app, record and review transactions. The register must stay fluid, and envelope totals must update immediately."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Read an account (Priority: P1)

A person opens an account from the panorama and sees its transactions, newest
first, with date, payee, category, and amount. They can scroll a long
register without a hitch. The account balance at the top matches the sum of
its transactions.

**Why this priority**: Seeing where the money went is the daily check, and it
has to be the fast one.

**Independent Test**: Open an account preloaded with 500 transactions. The
latest row is visible immediately, scrolling does not stutter, and the
balance equals the transaction total.

**Acceptance Scenarios**:

1. **Given** an account has transactions, **When** the person opens it, **Then** the newest transaction is on screen with date, payee, category, and amount, and the balance matches the transactions.
2. **Given** an account has 500 transactions, **When** the person flings the list, **Then** rows stay evenly spaced and do not stutter or pop in as blank blocks.
3. **Given** an off-budget account, **When** the person opens it, **Then** its transactions are visible and they do not change the month's to-budget.
4. **Given** the person leaves the account and comes back, **When** the register opens, **Then** it opens at the newest transactions, not at a stale scroll position from a previous visit.

---

### User Story 2 - Record spending or income (Priority: P2)

A person adds a transaction: account, date, payee, amount, and optional
category and note. The account balance and, when it is an on-budget
category, the category's spent and available amounts update before the
finger lifts.

**Why this priority**: A register you cannot add to is a report. The add has
to feel as fast as the scroll.

**Independent Test**: Add a 12.40 grocery expense to an on-budget checking
account. Confirm the balance, the category spent, and the category available
change by 12.40 with no network.

**Acceptance Scenarios**:

1. **Given** an on-budget account and a category with available 40, **When** the person adds a 12.40 expense in that category, **Then** the account balance falls by 12.40, category spent rises by 12.40, and available falls by 12.40, before the finger lifts.
2. **Given** an on-budget account, **When** the person adds income categorized to an income category, **Then** the account balance rises and this month's to-budget rises by that amount.
3. **Given** the person saves a transaction with no payee or no amount, **When** they confirm, **Then** it is not saved and the form says what is missing.
4. **Given** the person adds a transaction dated in a different month, **When** they open that month's budget, **Then** the spent amount is on that month, not the month that was open when they typed.

---

### User Story 3 - Categorize from the inbox (Priority: P3)

From the inbox, or from a transaction row, the person sets or changes the
category. The row leaves the inbox once it has a category. The old and new
categories both update their spent and available amounts immediately.

**Why this priority**: Uncategorized transactions are how a budget goes
stale. Clearing them is a separate journey from typing a new expense.

**Independent Test**: Categorize one inbox transaction. Confirm it disappears
from the inbox and the chosen category's spent amount includes it.

**Acceptance Scenarios**:

1. **Given** a transaction has no category, **When** the person assigns one, **Then** it leaves the inbox and that category's spent and available amounts include it.
2. **Given** a transaction already has a category, **When** the person changes it, **Then** the old category releases the amount and the new category takes it.
3. **Given** the person clears the category, **When** they confirm, **Then** the transaction returns to the inbox and the old category's spent amount no longer includes it.

---

### User Story 4 - Split one purchase (Priority: P4)

A person splits a transaction across two or more categories. The split
amounts add up to the transaction amount. Each category receives only its
share.

**Why this priority**: A grocery trip that includes household goods is
normal. A single category would make the month lie.

**Independent Test**: Split a 30 transaction into 20 groceries and 10
household. Confirm both categories and that the register shows one
transaction with two parts.

**Acceptance Scenarios**:

1. **Given** a transaction of 30, **When** the person splits it into 20 and 10 in two categories, **Then** each category's spent amount includes only its share and the account balance changes by 30 once.
2. **Given** the split parts do not add up to the transaction, **When** the person tries to save, **Then** the split is refused and the transaction stays as it was.
3. **Given** a saved split, **When** the person removes the split, **Then** the whole amount returns to one category and the other category releases its share.

---

### User Story 5 - Transfer between accounts (Priority: P5)

A person records a transfer between two accounts. Both registers show the
movement, linked, for the same amount. A transfer between two on-budget
accounts does not need a category and does not change to-budget. A transfer
involving an off-budget account is treated as spending or income and can
carry a category.

**Why this priority**: Moving money to savings or paying a card from checking
is not an expense. Recording it as one would double-count.

**Independent Test**: Transfer 100 between two on-budget accounts. Confirm
both balances, that to-budget is unchanged, and that deleting one side
removes the other.

**Acceptance Scenarios**:

1. **Given** two on-budget accounts, **When** the person transfers 100 from the first to the second, **Then** both balances move by 100, no category is required, and to-budget is unchanged.
2. **Given** a transfer to an off-budget account, **When** the person saves it with a category, **Then** that category's spent amount includes the transfer and the off-budget balance rises.
3. **Given** a linked transfer, **When** the person deletes one side, **Then** the other side is deleted too and both balances return.

---

### User Story 6 - Credit card spending (Priority: P6)

When the person spends from a credit-card account using an on-budget
category, the available amount moves to that card's payment category, matching
Actual. Paying the card is a transfer from a bank account to the card, not a
second expense.

**Why this priority**: If the phone leaves the money in groceries and also
asks for it in the card payment, the person will budget twice.

**Independent Test**: Spend 40 on a card from Groceries, which had 40
available. Confirm Groceries available is 0 and the card payment category
available is 40. Then transfer 40 from checking to the card and confirm the
payment category falls by 40 and groceries does not move.

**Acceptance Scenarios**:

1. **Given** Groceries has 40 available and a card payment category has 0, **When** the person spends 40 on that card in Groceries, **Then** Groceries available is 0 and the card payment category available is 40.
2. **Given** the card payment category has 40, **When** the person transfers 40 from checking to the card, **Then** the payment category available falls by 40 and checking and the card balances move by 40.
3. **Given** a tracking-budget file, **When** the person spends on a card, **Then** the phone does not apply the envelope payment-category move. It keeps the tracking file's own treatment.

---

### User Story 7 - Correct a transaction (Priority: P7)

The person can edit the date, payee, note, amount, account, or category of a
transaction, and can delete it. Balances and category amounts follow the
edit. A reconciled transaction warns before it changes.

**Why this priority**: Mistakes are constant. Correction has to be as fast as
entry and must not quietly rewrite a finished reconciliation.

**Independent Test**: Change an amount from 10 to 14 and confirm the account
and the category. Delete another transaction and confirm both totals release
it.

**Acceptance Scenarios**:

1. **Given** a transaction of 10, **When** the person changes it to 14, **Then** the account balance and the category spent amount move by 4 more.
2. **Given** a transaction that is not reconciled, **When** the person deletes it, **Then** it disappears and the account and category release the amount.
3. **Given** a transaction is already reconciled, **When** the person edits or deletes it, **Then** the app warns that the reconciliation will no longer match, and proceeds only after confirmation.
4. **Given** the person edits a note only, **When** they save, **Then** balances do not change.

---

### Edge Cases

- A zero-amount transaction is refused.
- Future-dated transactions count in that future month's spent amount, not
  in the current month.
- Searching or filtering the register (by payee text) does not change which
  transactions exist. Clearing the filter shows the full register again.
- A sync or file refresh that arrives while the person is scrolling does not
  reorder the visible rows under the finger. New rows appear without a jump.
- Duplicate taps on save create one transaction, not two.
- The register shows the budget's currency. A negative balance on a credit
  card is shown as a balance owed, in the same way Actual shows it.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Opening an account MUST show its balance and its transactions,
  newest first, with date, payee, category, and amount.
- **FR-002**: The person MUST be able to add a transaction with account,
  date, payee, and amount, plus an optional category and note. Missing payee
  or amount MUST be refused.
- **FR-003**: Adding, editing, categorizing, splitting, transferring, or
  deleting a transaction MUST update the affected account balance and
  category spent and available amounts before the finger lifts.
- **FR-004**: Income in an income category on an on-budget account MUST
  increase that month's to-budget. An off-budget transaction MUST NOT change
  to-budget.
- **FR-005**: The person MUST be able to set, change, or clear a transaction's
  category. A transaction with no category MUST appear in the inbox. One
  with a category MUST NOT.
- **FR-006**: The person MUST be able to split a transaction across two or
  more categories. The parts MUST sum to the transaction amount or the save
  is refused.
- **FR-007**: A transfer between two on-budget accounts MUST move both
  balances, MUST NOT require a category, and MUST NOT change to-budget.
  Deleting one side MUST delete the other.
- **FR-008**: A transfer that involves an off-budget account MUST be able to
  carry a category and MUST affect that category the way Actual does.
- **FR-009**: On an envelope budget, spending on a credit card from an
  on-budget category MUST move the available amount into that card's payment
  category. Paying the card MUST be a transfer, not a second expense.
- **FR-010**: On a tracking budget, card spending MUST NOT use the envelope
  payment-category move.
- **FR-011**: Editing or deleting a reconciled transaction MUST warn first
  and MUST proceed only after confirmation.
- **FR-012**: A repeated save tap MUST create only one transaction.
- **FR-013**: Scrolling a register of at least 500 transactions, and editing
  one row, MUST stay smooth. A background refresh MUST NOT jump the list
  under the finger.
- **FR-014**: The person MUST be able to filter the open register by payee
  text without deleting any transaction.

### Key Entities

- **Transaction**: A dated movement in one account. Has payee, amount, optional
  category, optional note, and a cleared or reconciled state.
- **Split**: Parts of one transaction, each with a category and an amount
  that together equal the transaction.
- **Transfer**: Two linked transactions in two accounts for the same amount.
- **Credit card payment category**: The envelope that holds money set aside
  to pay a card, updated when the card is spent on an envelope budget.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Adding an expense updates the account balance and the category
  available amount on screen in under 100 ms, with no network.
- **SC-002**: A person can open an account of 500 transactions and read the
  newest row in under 1 second, then fling through the list with no visible
  stutter.
- **SC-003**: For a scripted set of income, expense, split, transfer, and
  card-payment actions, the phone's balances and category available amounts
  match a desktop Actual file that received the same actions.
- **SC-004**: A person can categorize five inbox transactions in under 30
  seconds, and the inbox count falls by five.

## Assumptions

- The shell (`specs/001-metro-budget-shell`) and the month figures
  (`specs/002-envelope-month`) are present. This spec writes transactions
  that those screens display.
- Payee rules, schedules, and file import are separate specs. A new
  transaction here uses only what the person typed, plus whatever rules
  already exist on a preloaded file.
- Cleared and reconciled flags are stored. The reconciliation ceremony is
  spec 006. This spec only warns once a transaction is already reconciled.
- The phone uses Actual's signs and credit-card payment rules, not a
  simplified substitute.
- Bank download and file import are spec 007. This spec is hand entry and
  review.
