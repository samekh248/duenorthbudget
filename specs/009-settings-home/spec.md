# Feature Specification: Settings and Instance

**Feature Branch**: `009-settings-home`

**Created**: 2026-10-12

**Status**: Draft

**Input**: User description: "I also need a whole settings area where I can select the instance I want to use, or change it later and other settings as well. Make sure everything is still offline-first."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Choose the Actual server (Priority: P1)

A person opens settings and sees which Actual server, if any, this phone
uses. They add a server by its address, sign in, and that server becomes
the one the phone syncs with. An instance is that server, not a budget
file. Only one instance is active.

**Why this priority**: The phone is useless as a companion to an existing
Actual server until the person can point it at the server they already run.

**Independent Test**: With no server saved, add an address, sign in, and
confirm settings names that server as the active instance. The budget
screens still read the copy on the phone.

**Acceptance Scenarios**:

1. **Given** no instance is saved, **When** the person opens settings, **Then** they see that the phone is not connected to a server and they can add one.
2. **Given** the person enters a server address and signs in, **When** sign-in succeeds, **Then** settings names that server as the active instance.
3. **Given** the server expects a browser sign-in, **When** the person finishes that sign-in, **Then** they return to settings signed in to that instance.
4. **Given** the address is wrong or the password is refused, **When** sign-in fails, **Then** the phone tells them it failed and does not mark the instance active.
5. **Given** an instance is active, **When** the person edits a budget, **Then** the edit appears on the phone without waiting for that server.

---

### User Story 2 - Change the instance later (Priority: P1)

The person can save more than one server and later make a different one
active. Settings names the server they are leaving and the server they are
opening. Budgets already on the phone stay on the phone. Nothing is merged
with a file on the new server. Signing out drops the session and keeps the
phone's files.

**Why this priority**: People move servers and keep old ones. Changing
instance has to be ordinary, and it must not destroy the local budget.

**Independent Test**: Save two servers, sign into the second, then sign
out. Both local budgets still open, and the second server is no longer
signed in.

**Acceptance Scenarios**:

1. **Given** two servers are saved, **When** the person looks at settings, **Then** they see both, and exactly one is marked active.
2. **Given** server A is active, **When** the person makes server B active, **Then** settings names A as the one left and B as the one opening, and a local budget is not merged into a file on B.
3. **Given** the person signs out, **When** they return to the panorama, **Then** the budgets that were already on the phone are still there and the server list is not available until they sign in again.
4. **Given** the network is off, **When** the person selects a different saved server, **Then** the choice is kept, settings says the server has not been reached, and the open local budget can still be edited.

---

### User Story 3 - The budget file, from settings (Priority: P2)

From settings the person sees the budgets on the phone, which one is open,
and can create a budget or switch to another. Switching still names the
file opening and the file left on the phone. The phone does not merge two
files.

**Why this priority**: Choosing a server and choosing a file are the two
"which Actual am I in" questions. They belong in one settings place.

**Independent Test**: With two local budgets, open settings, switch files,
confirm the names, and see the panorama show the file just opened.

**Acceptance Scenarios**:

1. **Given** two budgets are on the phone, **When** the person opens settings, **Then** they see both names and which one is open.
2. **Given** the person switches files, **When** they confirm, **Then** the confirmation names the file opening and the file left, and only the opened file's figures show.
3. **Given** the person starts a new budget from settings, **When** they save a name and a currency, **Then** that budget is on the phone and is the open one.
4. **Given** a blank name, **When** they try to create the budget, **Then** nothing is created.

---

### User Story 4 - Everyday preferences (Priority: P2)

Theme and accent live in settings and stay on the phone. For the open
budget, the person sets the date format, the number format, the first day
of the week, and whether decimal places are hidden. Those four are part of
the budget file, so another Actual app that opens the same file shows the
same dates and numbers. The budgeting method is shown and cannot be
switched.

**Why this priority**: Appearance is already possible, but it is not in a
settings place, and the file's own formatting is missing.

**Independent Test**: Set the phone to dark and a non-default accent, set
the open budget to a different date format and hidden decimals, restart,
and confirm both survived. Open the same file where Actual shows formatting
and confirm the date format and hidden decimals match.

**Acceptance Scenarios**:

1. **Given** the person sets the theme to light and picks an accent, **When** they leave settings and later restart, **Then** the panorama uses that theme and accent.
2. **Given** the person changes the date format and hides decimals, **When** they look at a transaction date and an amount, **Then** both use the new choices before the finger lifts.
3. **Given** those formatting choices were saved, **When** the same file is opened in desktop Actual, **Then** the date format, number format, first day of the week, and hidden decimals match.
4. **Given** the file is an envelope budget, **When** the person views settings, **Then** the method is shown as envelope and there is no control that turns it into a tracking budget.

---

### User Story 5 - Export and recovery (Priority: P3)

The person can export a copy of the on-phone budget. Advanced actions are
behind a confirmation: rebuild the calculated totals, repair split
transactions, and reset sync with the active instance. Settings can show
the budget's own id and, when the file is synced, its sync id. The person
can set, change, or remove the file's encryption password.

**Why this priority**: These are the settings a person needs when something
is wrong. They are not the daily path.

**Independent Test**: Export a budget that has one known account and one
known transaction, and open that copy in desktop Actual. Confirm the
account and the transaction are there. Confirm reset sync does nothing
until the person confirms.

**Acceptance Scenarios**:

1. **Given** a budget is open, **When** the person exports a copy, **Then** they receive a file that contains that budget's accounts and transactions.
2. **Given** the person chooses reset sync, rebuild totals, or repair splits, **When** they have not confirmed, **Then** the budget is unchanged.
3. **Given** they confirm rebuild totals, **When** it finishes, **Then** the month figures match the transactions on the phone.
4. **Given** they set an encryption password, **When** they reopen the file, **Then** the amounts stay hidden until the password is entered. A wrong password does not reveal an amount.
5. **Given** the network is off, **When** they export or rebuild totals, **Then** both finish using the copy on the phone.

---

### Edge Cases

- Settings opens with the network off. Theme changes still save. The active
  instance shows that it has not been reached.
- The person removes a saved server that is not active. The active server
  and the local budgets stay.
- The person removes the active server. They are signed out of it. Local
  budgets stay. No server is active.
- Two servers have files with the same display name. Switching instance
  does not treat them as the same file.
- No budget is open. Formatting, export, encryption, and recovery are
  absent. Instance and the budget list are still there.
- The person turns experimental features on or off. The choice is stored
  with the open budget and defaults to off. Turning it on does not by
  itself change any budgeted amount.
- A sync is in progress while they sign out. Sign-out finishes, the local
  file remains, and the screen does not freeze.
- The exported copy is produced without putting the encryption password or
  the server password into the file's name, notes, or categories.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The app MUST have one settings place that includes the active
  instance, saved instances, the budgets on the phone, appearance, the open
  budget's formatting, and the advanced actions in this spec.
- **FR-002**: The person MUST be able to add an Actual server by address,
  sign in, and make it the active instance. Sign-in MUST include the
  password method and a browser sign-in when that server requires one.
- **FR-003**: Exactly one instance MUST be active. The person MUST be able
  to save more than one and later choose a different one. The choice MUST
  name the server left and the server opened.
- **FR-004**: Changing, removing, or signing out of an instance MUST NOT
  delete budgets already on the phone and MUST NOT merge a local budget
  with a file on the server.
- **FR-005**: A failed sign-in or an unreachable server MUST be told to the
  person and MUST leave the on-phone budget editable.
- **FR-006**: From settings the person MUST be able to see which budget is
  open, create a budget, and switch budgets. Switching MUST name the file
  opening and the file left, and MUST NOT merge files. A blank name MUST
  NOT create a budget.
- **FR-007**: Theme and accent MUST be editable in settings, MUST apply
  across the app, MUST survive a restart, and MUST be stored on the phone
  rather than inside the budget file.
- **FR-008**: For the open budget, the person MUST be able to set the date
  format, the number format, the first day of the week, and whether decimal
  places are hidden. Those choices MUST be part of the budget file and MUST
  appear on dates and amounts before the finger lifts.
- **FR-009**: Settings MUST show whether the open budget is an envelope
  budget or a tracking budget and MUST NOT offer to convert one into the
  other.
- **FR-010**: The person MUST be able to turn experimental features on or
  off for the open budget. The default MUST be off. The switch MUST NOT by
  itself change a budgeted amount.
- **FR-011**: The person MUST be able to export a copy of the on-phone
  budget. The copy MUST include that budget's accounts and transactions.
- **FR-012**: Rebuild calculated totals, repair split transactions, and
  reset sync MUST require confirmation. Until then the budget MUST be
  unchanged. Rebuild totals and repair splits MUST be able to finish with
  the network off.
- **FR-013**: Settings MUST be able to show the open budget's id and, when
  the file is synced, its sync id.
- **FR-014**: The person MUST be able to set, change, or remove the open
  file's encryption password. A wrong password MUST NOT reveal an amount.
  The password MUST NOT appear in the budget's name, notes, or categories.
- **FR-015**: Settings MUST open and save phone preferences with the network
  off. Instance and file choices made offline MUST be kept and applied when
  the server can next be reached, without blocking the screen.
- **FR-016**: This spec does not add budget math, reports, or bank-link
  setup. Those are `specs/010-actual-parity` and the earlier feature specs.

### Key Entities

- **Instance**: An Actual server the phone can sync with. It has an address
  and a sign-in. At most one is active.
- **Budget file**: One Actual budget on the phone. It has a name, a
  currency, a method, formatting choices, an experimental-features choice,
  and optional encryption.
- **Phone preferences**: Theme and accent. They belong to the phone, not
  to a budget file.
- **Export**: A copy of the on-phone budget the person can take elsewhere.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A person adds a server, signs in, and sees it named as the
  active instance in under 60 seconds when the server accepts the sign-in.
- **SC-002**: After switching from one saved instance to another, every
  budget that was already on the phone still opens, and no local budget
  has been merged into a server file.
- **SC-003**: With the network disabled, settings opens, a theme change
  survives a restart, and an edit to the open budget is visible in under
  100 ms.
- **SC-004**: A date-format change and a hide-decimals change are visible
  on the next date and amount in under 100 ms, and the same choices are
  what desktop Actual shows for that file.
- **SC-005**: An exported copy, opened in desktop Actual, shows the same
  budget name, the same accounts, and a known transaction from the phone.

## Assumptions

- "Instance" means the Actual server this phone syncs with. The budget file
  is chosen separately, in the same settings place.
- Spec 004 already defines sync, conflicts, offline edits, and how a server
  file is opened. This spec is where the person selects and replaces the
  server. It does not change which copy wins a conflict.
- Spec 001 already defines theme, accent, and file switching. This spec
  gathers them into settings and adds the file's formatting.
- The phone does not host the server, does not administer other people's
  permissions, and does not install community themes or translation packs.
- In-app release notes and announcement bells are out of scope. The phone's
  own update channel is separate.
- Experimental features default off. What those features do when turned on,
  including budget templates, is spec 010.
- Creating or replacing a bank connection is spec 010. A connection the
  file already has continues to sync as spec 007 describes.
- One currency per budget, chosen at creation. Changing the currency later
  is out of scope.
