# Feature Specification: Import Transactions

**Feature Branch**: `007-import-transactions`

**Created**: 2026-10-06

**Status**: Draft

**Input**: User description: "Bring transactions into the Actual Budget phone app from a file or from a bank connection the budget already has. Review them without the list jumping."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Import a file into an account (Priority: P1)

A person picks an account and a file of transactions the way Actual already
accepts (a bank export the desktop app can import). The phone shows how many
rows it understood and how many it skipped. Nothing is added until they
confirm. Confirmed rows become transactions in that account, and duplicates
of transactions already in the account are not added again.

**Why this priority**: Hand entry does not survive a month of card purchases.
A file import is the smallest way to bring a real statement in.

**Independent Test**: Import a file of four transactions, one of which
matches an existing date, payee, and amount. Confirm three new transactions,
one skipped duplicate, and balances that include only the three.

**Acceptance Scenarios**:

1. **Given** a file the budget can import, **When** the person previews it for an account, **Then** they see the rows that will be added and the rows that will be skipped, and the account is unchanged until they confirm.
2. **Given** the person confirms, **When** the import finishes, **Then** the new transactions are in that account and the balance includes them.
3. **Given** a row matches an existing transaction on date, amount, and payee, **When** they confirm, **Then** that row is not added a second time.
4. **Given** the file cannot be read, **When** the person picks it, **Then** the account is unchanged and a Metro 2 message says the file was not understood.
5. **Given** the person cancels the preview, **When** they return to the account, **Then** no new transactions exist.

---

### User Story 2 - Review a batch without losing their place (Priority: P2)

After an import, the new rows are easy to spot and categorize. Rules from
spec 005 run on them. The person can categorize down the batch. The list does
not jump as each category is set. They can finish the batch and land back on
the account.

**Why this priority**: An import that dumps fifty uncategorized rows into a
jumping list will not get categorized. The review is the actual work.

**Independent Test**: Import five uncategorized expenses, categorize the
third one, and confirm the third row stays on screen, leaves the inbox when
categorized, and the other four stay in the batch.

**Acceptance Scenarios**:

1. **Given** an import just finished, **When** the person reviews it, **Then** they see those new transactions as a batch, separate from older ones.
2. **Given** a rule matches a new row, **When** the row is added, **Then** the rule's category or payee is already filled.
3. **Given** the person categorizes one row in the batch, **When** the row updates, **Then** the row they are reading stays put and the inbox count falls if that row was uncategorized.
4. **Given** the person finishes the review, **When** they return to the account, **Then** the imported transactions are part of the normal register.

---

### User Story 3 - Pull from a bank connection the budget already has (Priority: P3)

If the budget file already has a bank connection set up in Actual, the
person can ask the phone to fetch new transactions for it. The fetch runs
without freezing the screen. New transactions go through the same preview
and duplicate checks as a file. The phone does not create a new kind of bank
connection.

**Why this priority**: Many Actual files already sync a bank. The phone
should use that, not invent a second setup. It is lower priority because a
file import already unblocks the person.

**Independent Test**: With a test connection that returns two new
transactions and one duplicate, fetch, confirm, and verify two added and one
skipped. Scrolling during the fetch does not stutter.

**Acceptance Scenarios**:

1. **Given** the open budget has a bank connection, **When** the person asks to fetch, **Then** a preview lists new rows and duplicates, and the account does not change until they confirm.
2. **Given** the fetch is running, **When** the person scrolls the register, **Then** scrolling stays smooth and only the small progress indicator shows.
3. **Given** the budget has no bank connection, **When** the person opens the fetch action, **Then** the app says there is nothing to fetch and does not ask them to create a connection.
4. **Given** the fetch fails, **When** the person dismisses the message, **Then** the account is unchanged.

---

### Edge Cases

- A file with no transaction rows confirms as zero added, not as an error.
- Importing into the wrong account is corrected by the person before
  confirm. After confirm, moving a transaction to another account is the
  register spec's edit, not a second import.
- Two identical new rows in the same file that are not already in the
  account are both kept. Duplicate detection is against the account, not
  against a twin in the same file, unless Actual itself would collapse them.
- A huge file shows the preview in a scrollable list. The preview itself
  must not stutter.
- Amounts and dates use the budget's currency and the file's own dates.
  Ambiguous dates follow Actual's reading of that file type.
- The person can import while offline only for a file already on the phone.
  A bank fetch requires a network and fails plainly when there is none.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The person MUST be able to preview a transaction file against
  one account before any row is added.
- **FR-002**: The preview MUST show which rows would be added and which match
  an existing transaction. Confirming MUST add only the new rows. Cancelling
  MUST add none.
- **FR-003**: A file the app cannot read MUST leave the account unchanged and
  MUST explain that the file was not understood.
- **FR-004**: A row that matches an existing transaction on date, amount, and
  payee MUST NOT be added again.
- **FR-005**: After confirm, the new transactions MUST be reviewable as a
  batch, and enabled rules MUST already have run on them.
- **FR-006**: Categorizing a row in the batch MUST update that row in place
  without moving the list under the finger, and MUST update the inbox.
- **FR-007**: If the budget already has a bank connection, the person MUST be
  able to fetch new transactions into the same preview and duplicate checks.
- **FR-008**: If the budget has no bank connection, the phone MUST NOT offer
  to create one.
- **FR-009**: A fetch MUST NOT block scrolling or typing, and MUST NOT use a
  full-screen spinner.
- **FR-010**: A failed fetch or a cancelled preview MUST leave existing
  transactions unchanged.
- **FR-011**: Imported transactions MUST follow the register spec for
  balances, categories, and credit-card payment categories once they are
  saved.

### Key Entities

- **Import batch**: The set of transactions added by one confirmed file or
  one confirmed fetch. Used to review them together, then they are ordinary
  transactions.
- **Preview row**: A candidate from a file or a fetch that is either new or
  a duplicate of one already in the account.
- **Bank connection**: A connection already stored in the budget file. The
  phone can fetch it. The phone does not create it.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Previewing a file of 200 transactions shows the first rows
  within 1 second of the person picking the file, without a full-screen
  wait.
- **SC-002**: Confirming that preview adds each non-duplicate once. A repeat
  of the same file adds zero transactions.
- **SC-003**: Categorizing a row in a 50-row batch updates that row in under
  100 ms and does not scroll the batch.
- **SC-004**: A fetch that returns duplicates and new rows matches the same
  added and skipped counts as desktop Actual for the same payload.
- **SC-005**: Scrolling the register during a fetch shows no visible stutter.

## Assumptions

- The register (`specs/003-account-register`) and rules
  (`specs/005-schedules-rules`) are present. This spec only brings rows in
  and then uses those behaviors.
- "A file Actual can import" means the file types Actual's desktop app
  already accepts. This spec does not add a new file format.
- Creating or repairing a bank connection is done on the desktop. The phone
  uses a connection the file already contains.
- Duplicate matching is date, amount, and payee, which is the check a person
  can verify. When the file already carries Actual's own identifier for a
  bank row, the phone MUST treat that identifier as the same transaction
  even if the payee text differs slightly.
