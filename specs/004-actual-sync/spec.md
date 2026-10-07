# Feature Specification: Actual Sync

**Feature Branch**: `004-actual-sync`

**Created**: 2026-10-06

**Status**: Draft

**Input**: User description: "The Android app stays on the same Actual Budget file as the desktop, including optional server sync, without ever making the screen wait."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Open a budget from the server (Priority: P1)

A person points the app at their Actual server, signs in, and sees the budget
files on that server. They open one. The panorama fills with that file. They
can work as soon as the file is on the phone. A later download of the rest
must not freeze the screen.

**Why this priority**: "Based on Actual" means the budget they already keep,
not only a file born on the phone.

**Independent Test**: Against a test server with one budget file, sign in,
open it, and confirm the month's to-budget matches the server file. Turn the
network off afterward and confirm the panorama still opens.

**Acceptance Scenarios**:

1. **Given** valid server address and sign-in, **When** the person connects, **Then** they see the budget files on that server and no other server's files.
2. **Given** a file is listed, **When** the person opens it, **Then** the panorama shows that file's month and accounts, and the phone can open it again with the network off.
3. **Given** the address or the sign-in is wrong, **When** the person connects, **Then** they see a Metro 2 message that says what failed, and no budget file is replaced.
4. **Given** a download is still finishing, **When** the person scrolls the panorama, **Then** scrolling stays smooth and only a small progress indicator is shown, never a full-screen spinner.

---

### User Story 2 - Work offline, sync later (Priority: P2)

A person edits the budget with no network. The edits stay on screen. When
the network returns, those edits are sent and remote edits come down. The
list they are touching does not jump. If both sides changed the same thing,
the person can see the conflict. Nothing is discarded in silence.

**Why this priority**: A phone budget that only works online is not Actual,
and a sync that shoves the list is a failure of the fluidity rule.

**Independent Test**: Go offline, assign 25 to a category, confirm the
screen, reconnect, and confirm the server file shows the same assignment.
In a second run, change the same category on the server and on the phone
while offline, reconnect, and confirm the conflict is listed.

**Acceptance Scenarios**:

1. **Given** the phone is offline, **When** the person assigns money or adds a transaction, **Then** the screen updates immediately and the change is still there after a restart.
2. **Given** offline edits exist, **When** the network returns, **Then** those edits reach the server and the phone picks up newer server edits that do not collide.
3. **Given** the phone and the server both changed the same category while apart, **When** they reconnect, **Then** the person can see both versions and the app does not drop either without a trace.
4. **Given** a sync finishes while the person is scrolling the register, **When** new rows arrive, **Then** the row under the finger stays put and new rows fade in without a jump.
5. **Given** the person never asked to sync, **When** a background sync runs, **Then** they can keep typing and the only motion is the small progress indicator.

---

### User Story 3 - Open an encrypted budget (Priority: P3)

A budget file protected by a password asks for that password before it
opens. A wrong password does not open it and does not create a second copy.
The password is not shown in the clear and is not written into the budget
name or notes.

**Why this priority**: Actual files are often encrypted. Skipping the
password would either lock the person out or leak the file.

**Independent Test**: Open an encrypted test file with the right password
and confirm the month loads. Restart and confirm the password is asked
again when the person chose to require it each time. A wrong password
leaves the file closed, and the password is never shown back.

**Acceptance Scenarios**:

1. **Given** a file is encrypted, **When** the person opens it, **Then** the app asks for the password before showing any category or transaction.
2. **Given** the password is wrong, **When** the person submits it, **Then** the file stays closed and the phone's other budgets are unchanged.
3. **Given** the password is correct, **When** the file opens, **Then** the panorama matches the decrypted file.
4. **Given** the person locks the screen and returns, **When** the budget was set to require the password each time, **Then** amounts are hidden until the password is entered again.

---

### User Story 4 - Leave a server and switch files (Priority: P4)

The person can sync now, see when the last successful sync happened, sign
out, and switch to another file on the phone or on the server. Switching
says which file will open. Files are not merged.

**Why this priority**: People keep more than one file. The switch has to be
deliberate so last month's household budget cannot absorb a second file.

**Independent Test**: Connect, note the last sync time, switch to a second
file, and confirm the first file's categories are absent. Sign out and
confirm the server's files are no longer listed, while on-phone files remain.

**Acceptance Scenarios**:

1. **Given** a sync succeeded, **When** the person looks at the sync status, **Then** they see the time of the last success, or a plain statement that the last try failed.
2. **Given** two files are available, **When** the person switches, **Then** the confirmation names both files, and after it the panorama shows only the newly opened file.
3. **Given** the person signs out, **When** the server list was showing, **Then** it is gone and budgets that live only on the phone still open.
4. **Given** a sync failed because the server rejected the sign-in, **When** the person opens the app, **Then** the on-phone budget is still usable and the status says they need to sign in again.

---

### Edge Cases

- A budget created only on the phone can be uploaded to the server as a new
  file. It does not overwrite an existing server file unless the person
  picked that file.
- The server has no files: the person sees an empty list and can keep using
  a phone-only budget.
- The password for encryption is not the server sign-in. Mixing them up
  shows two separate prompts.
- A very large first download still lets the person leave the screen. Coming
  back shows progress, not a restart from zero, unless the download failed.
- Clock skew does not duplicate an edit that was saved once.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The person MUST be able to enter a server address and sign in,
  then see only that server's budget files.
- **FR-002**: Opening a server file MUST put a copy on the phone that the
  shell can show with the network off.
- **FR-003**: A wrong address or sign-in MUST NOT replace or delete any
  budget already on the phone.
- **FR-004**: Edits made offline MUST remain on the phone across a restart
  and MUST be sent when a connection is available.
- **FR-005**: Remote edits that do not collide with a pending local edit MUST
  appear on the phone. A collision MUST be visible to the person. The app
  MUST NOT discard either version without a trace.
- **FR-006**: Sync MUST run without a full-screen spinner, MUST NOT block
  typing or scrolling, and MUST NOT reorder a list under the finger.
- **FR-007**: An encrypted file MUST require its password before any amounts
  are shown. A wrong password MUST leave the file closed.
- **FR-008**: The person MUST be able to require the budget password again
  after the screen locks.
- **FR-009**: The person MUST be able to see the last successful sync time,
  or that the last try failed, without opening a log first.
- **FR-010**: Switching files MUST name the file being left and the file
  being opened, and MUST NOT merge them.
- **FR-011**: Signing out MUST remove access to the server list and MUST NOT
  delete phone-only budgets.
- **FR-012**: Uploading a phone-only budget MUST create a new server file
  unless the person explicitly chose an existing server file to update.
- **FR-013**: The screen MUST keep reading the on-phone copy during sync.
  Sync MUST NOT freeze, delay, or block what the person is looking at or
  typing.

### Key Entities

- **Server connection**: Address, sign-in, and the list of files the person
  may open. Separate from a budget encryption password.
- **Budget copy**: The on-phone file the screens read, plus pending local
  edits not yet accepted by the server.
- **Conflict**: A fact that the phone and the server both changed, kept until
  the person has seen it.
- **Encryption password**: The secret for one file. Not stored in the budget's
  name, notes, or categories.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: After a file is on the phone, opening the panorama with the
  network off still meets the one-second start from the constitution.
- **SC-002**: An offline assignment is visible in under 100 ms and is present
  on the server within one successful sync after the network returns.
- **SC-003**: In a test that changes the same category on both sides, both
  values are shown to the person. A test that looks for a silently dropped
  edit fails the build.
- **SC-004**: A person scrolling a 500-row register while a sync inserts rows
  does not see the row under their finger move.
- **SC-005**: A wrong encryption password never reveals a category amount.
  Verified by a test that submits a bad password and reads the screen.

## Assumptions

- Specs 001 through 003 define the screens this sync updates. This spec does
  not change envelope math.
- The server is an Actual server the person already runs. Building or hosting
  that server is out of scope.
- Multi-user permission administration is out of scope. The signed-in person
  sees the files that server already allows them.
- "The server copy wins" when the local edit is not still pending, matching
  the constitution. A pending local edit is not overwritten blindly.
- How the password is stored uses the phone's protected store. The spec
  requires the outcome (not shown, not in the file), not a product name.
