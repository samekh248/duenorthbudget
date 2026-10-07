# Feature Specification: Schedules, Rules, and Payees

**Feature Branch**: `005-schedules-rules`

**Created**: 2026-10-06

**Status**: Draft

**Input**: User description: "The Actual Budget phone app should post scheduled transactions, apply rules, and keep payees tidy, without slowing the register."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See and post what is due (Priority: P1)

A person opens a list of upcoming scheduled transactions and posts one that
is due into its account. The new transaction follows the same balance and
category rules as a hand-entered one. They can skip the next date without
deleting the schedule.

**Why this priority**: Rent and paychecks are the transactions people expect
to find waiting. Missing them makes the month look short.

**Independent Test**: Create a monthly schedule for 900 rent due today, post
it, and confirm one transaction appears and the next due date is a month
later. Skip the following occurrence and confirm no transaction is created.

**Acceptance Scenarios**:

1. **Given** a schedule is due today, **When** the person posts it, **Then** one transaction appears in that account with the schedule's payee, amount, and category, and the account balance updates immediately.
2. **Given** a schedule was just posted, **When** the person looks at the upcoming list, **Then** the next date is the following occurrence, not a second copy of today.
3. **Given** a schedule is due, **When** the person skips it, **Then** no transaction is created and the next date moves forward.
4. **Given** a schedule is due next month, **When** the person views the upcoming list, **Then** it is visible and is not posted into the register by merely being visible.

---

### User Story 2 - Rules fill in a new transaction (Priority: P2)

When a transaction is added or imported, rules already on the budget run.
A matching rule can set the payee, category, and note. Rules run in Actual's
order: earlier stage first, and within a stage the more specific rule wins
the way Actual ranks them. The person can turn a rule off.

**Why this priority**: Typing a category for the same payee every time is
the slow part. Rules remove that wait, which is the fluidity the brief asks
for.

**Independent Test**: With a rule that sets Groceries for payee "Market",
add a transaction with that payee and no category. Confirm the category
becomes Groceries before the row settles. Add a second rule in the later
stage that clears the category and confirm the later stage wins.

**Acceptance Scenarios**:

1. **Given** a rule sets the category for a payee, **When** the person saves a matching transaction with no category, **Then** the category is filled and the category's spent amount includes it.
2. **Given** two rules match and one is in a later stage, **When** the transaction is saved, **Then** the later stage's action is the one that remains.
3. **Given** a rule is turned off, **When** a matching transaction is saved, **Then** that rule changes nothing.
4. **Given** no rule matches, **When** the person saves a transaction, **Then** only what they typed is stored.

---

### User Story 3 - Keep payees clean (Priority: P3)

The person can rename a payee. Renaming onto a name that already exists
merges the two, and past transactions follow the surviving name. They can
set a rule from that rename the same way Actual does, so the next import
uses the clean name.

**Why this priority**: Payee clutter makes rules miss. The merge has to be
one step or people will not do it on a phone.

**Independent Test**: Rename "AMZN" to "Amazon" while "Amazon" already
exists. Confirm one payee remains and old transactions show "Amazon".

**Acceptance Scenarios**:

1. **Given** a payee has past transactions, **When** the person renames it to a new name, **Then** those transactions show the new name and balances do not change.
2. **Given** the new name already exists, **When** the person confirms the merge, **Then** one payee remains and every transaction from both names uses it.
3. **Given** the person renames a payee and asks to remember it, **When** the next matching transaction arrives, **Then** a rule applies the clean name.
4. **Given** the person cancels a merge, **When** they return to the register, **Then** both payees and their transactions are unchanged.

---

### User Story 4 - Make a schedule from a transaction (Priority: P4)

From an existing transaction the person creates a schedule: how often, the
next date, the account, payee, amount, and category. They can edit or delete
that schedule later. Deleting a schedule does not delete transactions already
posted.

**Why this priority**: The easiest schedule is the rent payment already in
the register. It is a separate story from posting one that already exists.

**Independent Test**: Turn a paycheck transaction into a biweekly schedule,
confirm the upcoming list shows the next date, delete the schedule, and
confirm the original paycheck is still in the account.

**Acceptance Scenarios**:

1. **Given** a transaction exists, **When** the person creates a schedule from it, **Then** the upcoming list shows the next date with the same payee, amount, account, and category.
2. **Given** a schedule exists, **When** the person changes the amount, **Then** later posts use the new amount and transactions already posted keep the old amount.
3. **Given** a schedule exists, **When** the person deletes it, **Then** it leaves the upcoming list and posted transactions stay in their accounts.
4. **Given** the person picks an end after a number of times, **When** that many have been posted, **Then** the schedule no longer offers a new one.

---

### Edge Cases

- A schedule that matches a transaction already in the account within two
  days of the due date does not post a second copy. The person can still
  post it if they say the existing one is not the match.
- Rules do not run again in a loop on their own output. Each rule runs at
  most once per save.
- A rule and a schedule that disagree: the posted schedule's own category is
  on the transaction first, then rules run as they do for any new
  transaction.
- Hundreds of rules do not add a visible pause to saving one transaction.
- A payee used by no transactions can be deleted. A payee that still has
  transactions cannot be deleted until it is merged or the transactions are
  recategorized to another payee.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The person MUST be able to see upcoming scheduled transactions
  and MUST be able to post or skip the next occurrence of one.
- **FR-002**: Posting a schedule MUST create one transaction and MUST move
  the next due date forward. Viewing the list MUST NOT post anything.
- **FR-003**: Skipping MUST NOT create a transaction and MUST move the next
  due date forward.
- **FR-004**: A schedule MUST NOT post a second transaction when a matching
  transaction is already in that account within two days of the due date,
  unless the person says it is not the match.
- **FR-005**: On save of a transaction, enabled rules on the budget MUST run
  in Actual's stage order, each rule at most once. A later stage MUST be
  able to override an earlier one.
- **FR-006**: The person MUST be able to turn a rule off without deleting it.
- **FR-007**: The person MUST be able to rename a payee. Renaming onto an
  existing name MUST merge them after confirmation, and past transactions
  MUST show the surviving name without changing amounts.
- **FR-008**: The person MUST be able to remember a payee rename as a rule
  that applies to later transactions.
- **FR-009**: The person MUST be able to create a schedule from a
  transaction, edit its amount and next date, and delete it. Deleting a
  schedule MUST NOT delete transactions already posted.
- **FR-010**: Saving a transaction while rules run MUST stay within the same
  fluidity bound as saving a transaction with no rules: the row updates
  without a spinner.
- **FR-011**: This spec does not download bank files and does not reconcile.

### Key Entities

- **Schedule**: A repeating or one-time template. Has account, payee, amount,
  optional category, next date, and a frequency. Posting it creates a
  transaction.
- **Rule**: Conditions and actions stored on the budget. Has a stage and an
  on or off state. Actions may set payee, category, or note.
- **Payee**: A name on transactions. Renaming or merging it updates those
  transactions and does not change amounts.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Posting a due schedule shows the new transaction and the new
  account balance in under 100 ms.
- **SC-002**: Saving a transaction when 100 rules are on the budget is not
  visibly slower than saving one when no rules exist. A side-by-side timing
  check stays within 50 ms.
- **SC-003**: A scripted post, skip, merge, and rule-stage case matches the
  result of the same steps on desktop Actual.
- **SC-004**: A person can post three due schedules from the upcoming list in
  under 20 seconds.

## Assumptions

- The register spec (`specs/003-account-register`) owns how a transaction
  affects balances, splits, transfers, and credit cards. A posted schedule
  is just a new transaction.
- Rule ranking details match Actual's current stages (`pre`, default, and
  `post`) and specificity. This spec does not invent a new ranking.
- Creating every kind of rule condition on the phone is in scope only for
  payee, category, note, and the conditions needed to remember a rename.
  Arbitrary new condition builders can wait.
- Schedules linked to a bank download are still posted or matched the same
  way once the transaction exists. The download itself is spec 007.
