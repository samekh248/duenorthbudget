# Feature Specification: Metro Budget Shell

**Feature Branch**: `001-metro-budget-shell`

**Created**: 2026-10-06

**Status**: Draft

**Input**: User description: "Android app based on Actual Budget, using the Metro 2 design in the mockups. Performance of the experience comes before everything else. Break the app into multiple specs."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See the month on the panorama (Priority: P1)

A person opens the app and lands on a Metro 2 panorama of the open budget.
The oversized "budget" title sits above the word "due north" and the "budget" section. They see how
much is left to budget this month, and each category group with the amount
still available. The next section peeks in from the right. Nothing on this
screen waits for a network.

**Why this priority**: If the month is not instantly visible, the app has no
job. Later specs add editing; this story is the picture they edit.

**Independent Test**: Open a budget that already has groups, categories, and
a to-budget amount. The panorama shows those figures with no network, and a
sideways swipe reveals the next section peeking in.

**Acceptance Scenarios**:

1. **Given** a budget is already on the phone, **When** the person opens the app, **Then** the "budget" section is visible within one second and shows this month's to-budget amount and each category group's available total.
2. **Given** the panorama is on "budget", **When** the person swipes toward the next section, **Then** the title moves more slowly than the sections, the next section peeks from the right, and the section snaps into place when they let go.
3. **Given** the system "remove animations" setting is on, **When** the person swipes sections, **Then** the sections still change and the numbers stay correct, without a decorative animation.
4. **Given** a category group has a long name, **When** it is shown, **Then** the name stays readable and the available amount stays visible.

---

### User Story 2 - Move across budget, accounts, and inbox (Priority: P2)

The panorama has three sections: "budget", "accounts", and "inbox". Accounts
lists on-budget and off-budget balances. Inbox lists transactions that still
need a category, newest first. The person can swipe or tap a peeked header to
move between them.

**Why this priority**: The month, the accounts, and the uncategorized pile
are the three places a person looks before they edit anything.

**Independent Test**: With one on-budget account, one off-budget account, and
one uncategorized transaction, swipe the panorama and confirm each section
shows only its own rows.

**Acceptance Scenarios**:

1. **Given** on-budget and off-budget accounts exist, **When** the person opens "accounts", **Then** each account shows its name and balance, and off-budget accounts are grouped apart from on-budget accounts.
2. **Given** two uncategorized transactions exist, **When** the person opens "inbox", **Then** both appear, newest first, with payee and amount, and categorized transactions do not appear.
3. **Given** the inbox is empty, **When** the person opens it, **Then** they see a short empty note, not a blank screen and not an error.
4. **Given** the person is mid-swipe, **When** a background refresh finishes, **Then** the section under their finger does not jump or reorder.

---

### User Story 3 - Light, dark, and accent (Priority: P3)

The app follows the phone's light or dark setting. The person can override
it and can pick an accent. Dark is pure black. The default accent is magenta.
Money and titles stay readable on both themes.

**Why this priority**: Metro 2 is defined in both themes. The shell is the
first place that promise is visible.

**Independent Test**: Force the phone to dark, open the panorama, then set
the in-app override to light and pick a non-magenta accent. Confirm the
override wins and the accent is used on highlights.

**Acceptance Scenarios**:

1. **Given** the phone is in dark mode and the app is set to follow the phone, **When** the person opens the app, **Then** the background is black and text is light.
2. **Given** the phone is in dark mode, **When** the person sets the app to light, **Then** the panorama becomes white with dark text and stays light after a restart.
3. **Given** the person picks a new accent, **When** they return to the panorama, **Then** highlights use that accent and body text still meets a readable contrast on the background.
4. **Given** an accent is saved, **When** the app is restarted, **Then** the same accent and theme choice are still in effect.

---

### User Story 4 - Start a budget on the phone (Priority: P4)

A person with nothing on the phone can create one empty budget, give it a
name and a currency, and land on an honest empty panorama. They can also
leave that budget and open a different one that is already on the phone,
without the two files being mixed.

**Why this priority**: Later specs need a budget to edit. Creating one must
not require a server.

**Independent Test**: Create a budget named "Home" in US dollars, confirm the
empty panorama, create a second budget, switch back, and confirm "Home" is
unchanged.

**Acceptance Scenarios**:

1. **Given** no budget is on the phone, **When** the person creates one with a name and a currency, **Then** the panorama opens on the current month with nothing to budget and no accounts.
2. **Given** two budgets are on the phone, **When** the person switches from one to the other, **Then** the panorama shows only the chosen file and the previous file is unchanged.
3. **Given** a budget name is blank, **When** the person tries to create it, **Then** the app asks for a name and does not create a file.
4. **Given** a budget exists, **When** the person restarts the app, **Then** that same budget opens, not a new empty one.

---

### Edge Cases

- A budget with hundreds of categories still scrolls inside the "budget"
  section without a visible stutter, and group totals stay aligned.
- A month with no income and no assignments shows a to-budget amount of zero,
  not a placeholder and not an error.
- Amounts use the budget's currency and decimal places, including a currency
  with no minor units.
- The person rotates the phone or returns from another app mid-swipe: the
  panorama settles on a section and does not lose the open budget.
- Very large balances stay on one line or wrap without overlapping the name.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The home screen MUST be a Metro 2 panorama, as defined in the
  constitution (Principle II), titled "budget", with "due north" directly
  under that title, and the sections "budget", "accounts", and "inbox".
  The title MUST stay fully on screen.
- **FR-002**: The "budget" section MUST show the current month, the amount
  left to budget, and one row per category group with that group's available
  total.
- **FR-003**: The "accounts" section MUST list every account in the open
  budget with its name and balance, separating on-budget and off-budget
  accounts.
- **FR-004**: The "inbox" section MUST list only transactions that have no
  category, newest first.
- **FR-005**: The person MUST be able to move between sections by swipe and
  by tapping a visible section header. The next section MUST peek from the
  right while another section is selected.
- **FR-006**: The app MUST follow the phone's light or dark setting unless
  the person chooses light or dark explicitly. The choice MUST persist.
- **FR-007**: The person MUST be able to pick one accent from the Metro 2
  accent set. The choice MUST persist. Text on the accent and on the
  background MUST stay readable.
- **FR-008**: The person MUST be able to create an empty budget on the phone
  by giving a name and a currency, with no server account.
- **FR-009**: The person MUST be able to switch the open budget. Switching
  MUST NOT merge files or change the file that was left.
- **FR-010**: The last open budget MUST open again on the next launch.
- **FR-011**: Every amount on these screens MUST update from the on-phone
  budget only. These screens MUST NOT wait on a network.
- **FR-012**: A tap on these screens MUST show visible feedback within 100 ms.
  Swipes and scrolling MUST stay smooth on a mid-range phone, including while
  a background refresh completes.
- **FR-013**: Empty sections MUST show a short note. They MUST NOT show a
  blank screen or an error.
- **FR-014**: This spec MUST NOT add, edit, or delete categories,
  assignments, or transactions. Those actions belong to later specs.

### Key Entities

- **Budget**: The one open Actual budget file. Has a name, a currency, a
  budget mode (envelope or tracking), and a current month.
- **Category group**: A named group of categories in that file. The shell
  shows the group's available total, not the editor.
- **Account**: A named on-budget or off-budget account with a balance.
- **Inbox transaction**: A transaction in the file that has no category yet.
- **Appearance**: The person's theme choice (follow phone, light, or dark)
  and accent. Stored on the phone, not inside the budget file.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: With a budget already on the phone, a person sees the "budget"
  section within 1 second of opening the app on a mid-range phone.
- **SC-002**: A person can swipe from "budget" to "accounts" and to "inbox"
  and back, and identify the correct section every time, in under 2 seconds
  per swipe.
- **SC-003**: In a test with 300 category rows, scrolling the "budget"
  section shows no visible stutter to a person watching the list.
- **SC-004**: A background refresh that finishes during a swipe does not move
  the row under the finger. Verified by a test that applies new rows during
  the gesture.
- **SC-005**: Nine out of ten first-time viewers, shown the panorama beside
  the Metro 2 reference, identify it as the same design language (flat color,
  oversized lowercase title, peeking next section, bottom application bar).

## Assumptions

- Metro 2 is the Due North Light Panorama language from Due North Tasks
  (design C, mockups in that repo's `docs/design/metro-mockups.svg`). This
  repo does not contain those files. Budget-specific drawings come later.
- A budget can exist only on the phone. Connecting to an Actual server is a
  separate spec.
- Category, assignment, and transaction editing are out of scope here. Tests
  may preload a budget that already contains them.
- The shell shows the file's real to-budget and available figures. It does
  not compute a different formula than Actual.
- One currency per budget, chosen at creation. Changing currency later is
  out of scope.
- Tablet, widget, and watch layouts are out of scope.
