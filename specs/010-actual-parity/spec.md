# Feature Specification: Actual Parity

**Feature Branch**: `010-actual-parity`

**Created**: 2026-10-12

**Status**: Draft

**Input**: User description: "I want a 1-1 reproduction of the actualbudget functionality if possible. Make sure everything is still offline-first."

## User Scenarios & Testing *(mandatory)*

Specs 001 through 007 already cover the daily loop: the month, assignments,
the register, sync, schedules and rules in a limited form, reconcile and
review, and import. This spec adds the remaining Actual behaviors a person
can perform on that same file. The phone and desktop Actual must agree on
the numbers. The screen keeps reading the copy on the phone.

### User Story 1 - Fill and explain the month (Priority: P1)

From the open month the person can see why the to-budget number is what it
is, copy last month's budget, use a three-month average, or clear this
month's budgeted amounts. They can send money from a category back to
to-budget, and they can set an income category to hold its income for next
month automatically.

**Why this priority**: These are the month actions a person already expects
from Actual once they can assign, move, and hold. Without them the month
is only half of Actual.

**Independent Test**: On a fixture month, open the to-budget breakdown and
check it adds up. Copy last month, then set the month to zero, and confirm
both results match the same steps in desktop Actual. Turn on auto-hold for
an income category and confirm next month's to-budget rises by that income.

**Acceptance Scenarios**:

1. **Given** a month has income, assignments, an uncovered overspend, and an amount held for later, **When** the person opens the to-budget breakdown, **Then** they see income from this month, amounts carried in, the total budgeted, the uncovered overspend, and the amount held, and those parts add up to the to-budget figure on the month.
2. **Given** last month has budgeted amounts, **When** the person copies last month's budget and confirms, **Then** this month's budgeted amounts match last month for categories that are visible, and hidden categories and hidden groups are left unchanged.
3. **Given** an envelope budget, **When** the person copies last month, **Then** income categories are not given a budgeted amount by that copy.
4. **Given** the person chooses the three-month average and confirms, **When** it finishes, **Then** each visible category's budgeted amount is the average of its spending over the previous three months, counting only months since that category first had activity, as desktop Actual does.
5. **Given** the person sets this month's budgeted amounts to zero and confirms, **When** it finishes, **Then** every budgeted amount for this month is zero and no category, transaction, or other month is deleted.
6. **Given** a category has available money, **When** the person transfers some of it back to to-budget, **Then** the category's available amount falls and to-budget rises by that amount before the finger lifts.
7. **Given** the person turns on automatic holding for an income category, **When** income is categorized there, **Then** that income is held for the next month for this month and the following 12 months.
8. **Given** automatic holding is on, **When** the person turns it off for this month only, **Then** this month can be held by hand and later months stay automatic. **When** they turn it off entirely, **Then** later months stop holding that income automatically.

---

### User Story 2 - Accounts the person can manage (Priority: P2)

The person can add an account, rename it, give it a note, reorder it, and
close it. Closing an account with a balance asks where that balance should
go. Closed accounts stay out of the normal list until the person asks to
see them, and a closed account can be reopened.

**Why this priority**: A budget file from Actual already has accounts the
phone can only view. The person cannot yet do what the accounts screen in
Actual does.

**Independent Test**: Add an on-budget account with a starting balance,
close it by transferring the balance to another account, find it under
closed accounts, and reopen it. Confirm the balances match desktop Actual
for the same steps.

**Acceptance Scenarios**:

1. **Given** a budget is open, **When** the person adds an account with a name, an on-budget or off-budget choice, and a current balance, **Then** the account appears and its balance is that amount.
2. **Given** an account exists, **When** the person renames it or edits its note, **Then** the register and the account list show the new name and the note is still there after a restart.
3. **Given** the person reorders accounts, **When** they return to the account list, **Then** the order matches what they set.
4. **Given** an account has a balance, **When** the person closes it, **Then** they must choose another account to receive the balance before the close finishes. If that transfer leaves the budget, they can choose a category.
5. **Given** the person confirms a force close, **When** it finishes, **Then** the account is closed even without a transfer, and the month figures change to match the money leaving.
6. **Given** an account is closed, **When** the person has not asked to see closed accounts, **Then** it is absent from the normal list. **When** they reopen it, **Then** it returns to that list.

---

### User Story 3 - Schedules and rules match Actual (Priority: P2)

The person can create a schedule from scratch, pick the repeat Actual
supports, and let it post itself when the date arrives and the app is open.
They can create and edit a rule's conditions and actions, turn category
learning on or off, and still run a rule the file already contains even if
they do not edit that part.

**Why this priority**: Spec 005 posts schedules and applies rules. It does
not yet give the person Actual's editors, so a file built on the desktop
cannot be maintained on the phone.

**Independent Test**: Create a monthly schedule that posts itself, open the
app on the due date with the network off, and see one transaction. Create
a rule that sets a category when the payee matches, save a transaction with
that payee, and see the category filled in. Turn learning off and confirm
a new category choice does not create a learning rule.

**Acceptance Scenarios**:

1. **Given** no existing transaction, **When** the person creates a schedule with an account, payee, amount, optional category, a repeat, a next date, and an optional end, **Then** the upcoming list shows it and posting uses those fields.
2. **Given** a schedule is set to post itself, **When** the due date has arrived and the person opens the app, **Then** one transaction is created without a server, and a second copy is not created if a matching transaction is already in that account.
3. **Given** the person edits a rule, **When** they add a condition on payee, account, category, amount, date, or notes, and an action that sets payee, category, notes, amount, date, or cleared, **Then** the next matching save uses that rule.
4. **Given** a rule in the file uses a condition the phone does not edit, **When** a transaction is saved, **Then** the rule still runs and is shown as kept from the file, not dropped.
5. **Given** category learning is on, **When** the person sets a category on a payee that had none, **Then** a later transaction with that payee is categorized the way Actual would learn it. **Given** learning is off, **When** they set a category, **Then** no new learning rule is created and existing rules still run.

---

### User Story 4 - See cash flow, net worth, and spending (Priority: P3)

The person can open three reports from the on-phone budget: cash flow, net
worth over time, and spending. Each uses a date range they choose. The
figures match desktop Actual for that file and range.

**Why this priority**: The month review answers "this month." Actual's
reports answer "over this range." The phone does not have those yet.

**Independent Test**: For a fixture file, open each report for a known
range and compare income, expenses, net worth by month, and spending by
category with desktop Actual.

**Acceptance Scenarios**:

1. **Given** a date range, **When** the person opens cash flow, **Then** they see income, expenses, and the difference for that range.
2. **Given** a date range, **When** the person opens net worth, **Then** they see the account balances at each month in the range. Off-budget accounts follow the include-or-not choice they already have.
3. **Given** a date range, **When** the person opens spending, **Then** they can group it by category, by group, or by payee, with the largest amounts first.
4. **Given** the range has no transactions, **When** a report opens, **Then** the amounts are zero and the app does not show an error.
5. **Given** the same file and range in desktop Actual, **When** the three reports are compared, **Then** the totals match.

---

### User Story 5 - Budget templates, when enabled (Priority: P3)

Category notes can hold the same template lines a person types in Actual.
With experimental features off, the notes are editable and are not applied.
With budget templates on, the person can check the lines, apply them only
where this month is still zero, or overwrite this month from the lines.
The results match desktop Actual.

**Why this priority**: Templates are how many Actual files fill a new month.
The lines already live in the file as notes. The phone has to honor them
when the person asks, and leave them alone when the feature is off.

**Independent Test**: With templates off, confirm a note is saved and the
budgeted amount does not change. Turn templates on, check a bad line, and
confirm nothing is budgeted. Fix it, apply, and confirm only the zero
categories change, matching desktop Actual for a fixed amount, a schedule,
a periodic amount, a percentage, an average, a remainder, and a goal.

**Acceptance Scenarios**:

1. **Given** experimental features are off, **When** the person edits a category note, **Then** the note is saved and no budgeted amount changes because of it.
2. **Given** templates are on and a line is invalid, **When** the person checks templates, **Then** the category is named and no budgeted amount changes.
3. **Given** templates are on, **When** the person applies templates, **Then** only categories whose budgeted amount is zero are filled, and categories that already have an amount stay as they are.
4. **Given** templates are on, **When** the person overwrites with templates and confirms, **Then** this month's budgeted amounts follow the notes, including priority between lines, and hidden categories are not filled.
5. **Given** the same notes on desktop Actual, **When** the same apply or overwrite is run, **Then** the budgeted amounts match.

---

### User Story 6 - Link a bank, and undo a mistake (Priority: P3)

The person can connect, replace, or remove a bank link on an account, using
the providers Actual already supports. They can also undo their own recent
changes from this session, newest first.

**Why this priority**: Import already fetches from a link the file has.
Creating the link, and undoing a bad edit, are the two gaps that still make
the phone feel incomplete.

**Independent Test**: Link an account, remove the link, and confirm a fetch
is no longer offered. Assign an amount, undo it, and confirm to-budget
returns to the earlier figure with the network off.

**Acceptance Scenarios**:

1. **Given** an account has no bank link, **When** the person connects a provider Actual supports and completes that provider's sign-in, **Then** the account can fetch, and the secret is not shown again and is not stored in a note or a payee.
2. **Given** a link exists, **When** the person replaces it or removes it, **Then** later fetches use the new link, or are not offered after a removal.
3. **Given** the provider cannot be reached, **When** the person tries to link or fetch, **Then** the budget remains usable and the failure is shown.
4. **Given** the person just assigned, edited an account, or edited a transaction, **When** they undo, **Then** the previous figures return before the finger lifts.
5. **Given** there is nothing to undo, **When** the person looks for undo, **Then** it is absent.
6. **Given** the server has since accepted a different value for something they undo, **When** the undo is sent, **Then** the disagreement is shown as a conflict and is not dropped.

---

### Edge Cases

- Copy or average when the previous months have no budgeted amounts. The
  action reports that there is nothing to copy and does not clear amounts
  the person already entered this month.
- The three-month average uses only the months since the category first had
  activity when there are fewer than three. A category with no activity is
  set to zero.
- Set-to-zero, overwrite templates, and force close always ask first. Until
  the person confirms, nothing changes.
- A tracking budget follows Actual's tracking rules for these actions. The
  phone does not convert it to an envelope budget.
- Closing the only on-budget account is allowed. The month stays readable.
- A schedule set to post itself does nothing while the app is not opened.
  Opening later posts the occurrence that came due, and does not post a
  pile of skipped duplicates when a match is already in the account.
- Hundreds of rules do not add a visible pause to one save.
- Reports include only living transactions. A deleted transaction
  disappears from the next time the report is opened.
- Undo is limited to changes the person made in this session on this phone.
  It does not undo another device's edits.
- Template apply with the feature off is not offered.
- All of the above that do not need a bank or a server finish with the
  network off.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The person MUST be able to open a breakdown of the open
  month's to-budget amount showing income from this month, amounts carried
  in, the total budgeted, uncovered overspending, and the amount held. The
  parts MUST add up to the to-budget figure.
- **FR-002**: The person MUST be able to copy the previous month's budgeted
  amounts, set this month from the average spending of the previous three
  months, and set this month's budgeted amounts to zero. Each MUST ask
  before replacing amounts already entered. Hidden categories and hidden
  groups MUST be skipped. On an envelope budget, a copy MUST NOT assign a
  budgeted amount to an income category.
- **FR-003**: The person MUST be able to transfer available money from a
  category back to to-budget, and the month MUST update before the finger
  lifts. A transfer larger than the available amount MUST be refused.
- **FR-004**: The person MUST be able to turn on automatic holding for an
  income category so that its income is held for the next month, for this
  month and the following 12 months. They MUST be able to turn that off for
  the current month only, or turn it off entirely.
- **FR-005**: The person MUST be able to add an account with a name, an
  on-budget or off-budget choice, and a starting balance, then rename it,
  edit its note, and reorder accounts.
- **FR-006**: Closing an account with a balance MUST require a destination
  account, and a category when the transfer leaves the budget. Force close
  MUST be a separate confirmed action. Closed accounts MUST be hidden until
  the person asks for them, and MUST be reopenable.
- **FR-007**: The person MUST be able to create a schedule without starting
  from a transaction, choose a one-time, daily, weekly, monthly, or yearly
  repeat, including a repeat every several weeks or months, set a next date
  and an optional end, and choose whether it posts itself when the app is
  opened on or after the due date.
- **FR-008**: A self-posting schedule MUST create one transaction without a
  server and MUST NOT create a second copy when a matching transaction is
  already in that account.
- **FR-009**: The person MUST be able to create, edit, disable, and delete
  rules whose conditions use payee, account, category, amount, date, and
  notes, and whose actions set payee, category, notes, amount, date, and
  cleared, in Actual's pre, default, and post stages.
- **FR-010**: A rule already stored on the file MUST still run even when the
  phone does not offer an editor for one of its conditions. The phone MUST
  NOT drop that rule.
- **FR-011**: The person MUST be able to turn category learning on or off.
  When it is off, categorizing a payee MUST NOT create a learning rule.
  Existing rules MUST still run.
- **FR-012**: The person MUST be able to view cash flow, net worth by month,
  and spending by category, group, or payee, for a date range they choose,
  from the copy of the budget on the phone. An empty range MUST show zeros.
- **FR-013**: With experimental features off, category notes MUST be
  editable and MUST NOT change budgeted amounts. With budget templates on,
  the person MUST be able to check template lines, apply them only to
  categories whose budgeted amount is zero, and overwrite this month from
  those lines after confirmation. Results MUST match desktop Actual for the
  same notes, including a fixed amount, a schedule, a periodic amount, a
  percentage, an average, a remainder, a goal, and priority between lines.
- **FR-014**: The person MUST be able to connect, replace, and remove a bank
  link on an account using a provider Actual already supports. A secret
  MUST NOT be shown again after it is saved and MUST NOT be written into a
  note or a payee. A provider the phone cannot reach MUST NOT block the
  rest of the budget.
- **FR-015**: The person MUST be able to undo their own recent changes from
  this session, newest first, covering assignments, account edits, and
  transaction edits. The screen MUST update before the finger lifts. Undo
  MUST be absent when there is nothing to undo. If the server has accepted
  a different value, the result MUST be a visible conflict.
- **FR-016**: Every action in this spec that does not itself talk to a bank
  or a server MUST finish with the network off, and MUST NOT show a
  full-screen wait.
- **FR-017**: The phone MUST NOT convert an envelope budget into a tracking
  budget or the reverse. Tracking files MUST follow Actual's tracking rules
  for the actions in this spec.
- **FR-018**: Custom report dashboards, Sankey diagrams, Monte Carlo
  analysis, and crossover-point projections are out of scope. So are
  hosting the server and administering other people's access.

### Key Entities

- **To-budget breakdown**: The parts that add up to the open month's
  to-budget figure.
- **Month fill**: A confirmed action that sets this month's budgeted
  amounts from last month, from a three-month average, from zero, or from
  template notes.
- **Account**: A named on-budget or off-budget account with a balance, a
  note, an order, an open or closed state, and an optional bank link.
- **Schedule**: A one-time or repeating transaction the person can post by
  hand or have posted when the app is open on the due date.
- **Rule**: Conditions and actions stored on the budget, in a stage, on or
  off. Learning is a separate on or off choice for the whole budget.
- **Report**: Cash flow, net worth over a range, or spending over a range,
  read from the on-phone budget.
- **Template note**: Text on a category that desktop Actual already treats
  as a budget template. Applied only when experimental features are on.
- **Undo step**: One change the person made in this session, able to be
  reversed in order.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For a fixture month, the to-budget breakdown adds up to the
  figure on the month, and copy, three-month average, set-to-zero, transfer
  back to to-budget, and auto-hold each match desktop Actual for the same
  steps.
- **SC-002**: Adding, closing with a transfer, and reopening an account
  leaves balances that match desktop Actual, and the close is visible on
  screen in under 100 ms after confirmation.
- **SC-003**: A self-posting schedule creates one transaction within 1
  second of opening the app on the due date with the network off, and does
  not create a duplicate when a matching transaction is already there.
- **SC-004**: Saving a transaction when 100 rules are on the budget is not
  visibly slower than saving one when no rules exist. A side-by-side check
  stays within 50 ms.
- **SC-005**: Cash flow, net worth by month, and spending for a fixture
  range match desktop Actual for that file.
- **SC-006**: Apply and overwrite for the template kinds in FR-013 match
  desktop Actual on the same notes. A check of an invalid line changes no
  budgeted amount.
- **SC-007**: Undoing an assignment restores the previous to-budget and
  category available amounts in under 100 ms with the network off.

## Assumptions

- Specs 001 through 007 remain the source for the daily loop. This spec
  adds what those specs left out. Where spec 006 deferred a net-worth
  history, this spec adds it. Where spec 002 deferred goal templates, this
  spec adds them because the daily loop has shipped and the lines already
  live in the file.
- Spec 005 still owns posting, skipping, and the rule that a near-duplicate
  is not posted twice. This spec adds the editors, the repeats, self-posting,
  and learning.
- Spec 007 still owns file import and fetching from a link the file already
  has. This spec adds creating, replacing, and removing that link.
- Spec 009 owns the experimental-features switch. This spec defines what
  budget templates do when that switch is on.
- "Match desktop Actual" means the same file, the same actions, and the
  same resulting amounts. The phone does not invent a second formula.
- The phone does not add a bank provider Actual does not already support.
- Actions from this spec appear in the bar and menu from
  `specs/008-streamlined-controls` when that spec is present. Until then
  they are still reachable from the month, the account, and settings.
- Tablet, widget, and watch layouts stay out of scope.
