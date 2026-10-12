# Feature Specification: Streamlined Metro Controls

**Feature Branch**: `008-streamlined-controls`

**Created**: 2026-10-12

**Status**: Draft

**Input**: User description: "The UX is very clunky. Continue to use the Metro UX guidelines, but make it more streamlined, especially month selection, actions, etc. The buttons are not working well at all. Some dont even have real icons. I want the parallax to work better. Make sure everything is still offline-first."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Change the month in one place (Priority: P1)

A person standing on the panorama can see which month they are in and can
move to another month without a row of word buttons. Previous month, next
month, the current calendar month, and a month they pick from a list are
the same control. The budget figures, the month review, and anything else
on the panorama that belongs to a month all follow that one month.

**Why this priority**: Month selection is the control they use every time
they open the app. The current previous and next buttons are the clunkiest
part of the shell.

**Independent Test**: With October showing a known to-budget amount, move
to September, to a month a year away, and back to the current calendar
month. Each stop shows that month's name, year, and to-budget, and the
person never leaves the panorama.

**Acceptance Scenarios**:

1. **Given** a budget is open on the panorama, **When** the person looks at the month control, **Then** they see the month name and year of the month whose figures are on screen.
2. **Given** the panorama is on October, **When** the person chooses the previous month, **Then** September's figures replace October's before the finger lifts.
3. **Given** the panorama is on a past month, **When** the person chooses the current calendar month, **Then** the panorama shows that month in one action.
4. **Given** the person picks a month twelve months away, **When** the panorama updates, **Then** the to-budget and category amounts are that month's amounts.
5. **Given** the person changes the month, **When** they look at the review for the panorama, **Then** the review is the same month as the budget section.

---

### User Story 2 - Buttons that work, with real icons (Priority: P1)

Every round button on the application bar has a drawn icon that depicts its
action, and tapping the icon does that action whether or not the labels are
showing. The Metro ellipsis still expands labels and the rest of the menu.
No button is an empty circle. A button that does not apply on the current
screen is not shown.

**Why this priority**: A control with no icon, or a tap that does nothing
until a second try, makes the rest of the budget unusable.

**Independent Test**: On the home panorama and on an account register, tap
each visible application-bar icon with the labels hidden. Each tap opens
the place or runs the action its label names, and none of the icons are
blank.

**Acceptance Scenarios**:

1. **Given** the labels are hidden, **When** the person taps an application-bar icon, **Then** that action runs once and the icon was a drawing of the action, not an empty circle.
2. **Given** the labels are showing, **When** the person taps the same icon, **Then** the same action runs, and the ellipsis was not required to reach it.
3. **Given** the person taps the ellipsis, **When** the menu opens, **Then** they see labels for the icons already on the bar and the secondary actions for this screen, and the ellipsis itself does not navigate away.
4. **Given** a screen has no use for an action, **When** that screen is showing, **Then** that action is absent rather than visible and dead.
5. **Given** the person taps near the edge of an icon, **When** the tap lands on that control, **Then** only that control runs.

---

### User Story 3 - Actions live on the screen they change (Priority: P2)

The application bar follows the screen. The panorama offers the budget
actions the person already has: hold for next month, manage categories, and
add a transaction, plus settings. An account register offers add, transfer,
reconcile, and import. A form offers save and cancel. Secondary actions sit
in the ellipsis menu. The budget list is not crowded with a separate row of
word buttons for the same jobs.

**Why this priority**: Scattered word buttons and a bar that never changes
are why the app feels clumsy even when the underlying action exists.

**Independent Test**: From the panorama, hold, manage categories, and add a
transaction are reachable from the bar or its menu and are not duplicated
as word buttons on the list. Open a register and confirm its bar offers
add, transfer, reconcile, and import. Open a form and confirm save and
cancel are the bar actions.

**Acceptance Scenarios**:

1. **Given** the person is on the panorama, **When** they look at the budget list, **Then** previous month, next month, hold, and manage are not a row of word buttons on that list.
2. **Given** the person is on the panorama, **When** they open the bar or its menu, **Then** they can hold for next month, manage categories, and add a transaction.
3. **Given** the person is in a register, **When** they use the bar, **Then** they can add a transaction, start a transfer, start reconciliation, and import.
4. **Given** the person is on a form, **When** they choose save, **Then** the form writes. **When** they choose cancel, **Then** the form closes and does not write.
5. **Given** the person is on any screen, **When** they look at the bar, **Then** settings is reachable in one tap from the panorama, and the bar does not still show the panorama's budget actions on top of a form.

---

### User Story 4 - The title lags the sections (Priority: P2)

Swiping the panorama moves the oversized "budget" title sideways more
slowly than the sections, and a person can see that lag. The whole title
stays on screen. Sections still peek from the side and snap when the
finger lifts. With the phone's "remove animations" setting on, the title
does not travel and the sections still change.

**Why this priority**: The Metro 2 panorama is defined by that slower
title. A title that barely moves, or that gets clipped, does not read as
the panorama.

**Independent Test**: Record a swipe from the budget section to the next
section. The title moves a visible distance, less than the sections move,
and no letter of "budget" is cut off. Repeat with remove-animations on and
confirm the section changes with the title staying put.

**Acceptance Scenarios**:

1. **Given** the panorama has more than one section, **When** the person swipes to the next section, **Then** the title moves sideways more slowly than the sections and the lag is obvious.
2. **Given** that swipe finishes, **When** the section snaps, **Then** the title rests fully on screen, including every letter of "budget" and the "due north" line under it.
3. **Given** the person lets go halfway, **When** the section snaps back or forward, **Then** the title ends aligned with that section and does not jump to a different offset.
4. **Given** remove-animations is on, **When** the person changes sections, **Then** the title does not slide and the new section's contents are correct.
5. **Given** the panorama has a single section, **When** the person tries to swipe, **Then** the title stays still.

---

### Edge Cases

- No budget is open. The month control and the budget actions are absent.
  Settings still opens.
- The chosen month has no assignments and no spending. The month name shows
  and the amounts are zero. The app does not show an error.
- A category editor was opened for October and the person then changes the
  panorama month. The editor stays on October until they leave it. The
  panorama shows the newly chosen month when they return.
- A month change arrives while the category list is scrolling. The list
  under the finger does not jump. The new month appears when the gesture
  ends.
- The budget name is very long. It does not cover the month control or the
  to-budget amount.
- The application bar does not cover the last row. The list scrolls clear
  of the bar.
- The network is off. Month changes and bar actions still run against the
  budget already on the phone.
- A background sync finishes during a swipe. The section and the title do
  not jump under the finger.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The panorama MUST have one open month. The budget section and
  the month review MUST both show that month. Changing one MUST change the
  other.
- **FR-002**: From the panorama, the person MUST be able to select the
  previous month, the next month, the current calendar month, and any month
  from 24 months before the current calendar month through 24 months after
  it, without leaving the panorama.
- **FR-003**: The open month's name and year MUST be visible, and the
  figures on the budget section and the review MUST be that month's figures.
- **FR-004**: A month change MUST update the visible month before the finger
  lifts and MUST NOT wait for a network or for sync.
- **FR-005**: Every application-bar control other than the Metro ellipsis
  MUST show a drawn icon that depicts its action. An empty circle MUST NOT
  be a control. The ellipsis MUST remain the Metro ellipsis that expands
  labels and the rest of the menu.
- **FR-006**: Tapping an application-bar icon MUST run that action once,
  whether or not the labels are visible. The person MUST NOT have to open
  the ellipsis before a primary action will respond.
- **FR-007**: A control that does not apply on the current screen MUST NOT
  be shown. A tap that lands on one control MUST NOT also fire a neighbor.
- **FR-008**: The panorama's bar or its menu MUST offer hold for next month,
  manage categories, add a transaction, and settings. The register's bar
  MUST offer add, transfer, reconcile, and import. A form's bar MUST offer
  save and cancel. Save MUST write. Cancel MUST leave the form without
  writing.
- **FR-009**: Previous month, next month, hold, and manage MUST NOT appear
  as a row of word buttons on the budget list. Month changes MUST use the
  month control from FR-002.
- **FR-010**: A tap MUST show visible feedback within 100 ms. Each control
  MUST be large enough that a thumb tap on the icon does not hit the
  control beside it.
- **FR-011**: During a sideways swipe across two sections, the title MUST
  move sideways more slowly than the sections, the lag MUST be visible, and
  every letter of the title and the subtitle MUST stay on screen. When the
  section snaps, the title MUST rest with that section. With remove
  animations on, the title MUST NOT travel and the section MUST still
  change.
- **FR-012**: The next section MUST still peek in from the side, and
  sections MUST still snap, as the Metro shell already requires.
- **FR-013**: Month changes, bar actions, and panorama swipes MUST work
  with the network off, using the copy of the budget on the phone.
- **FR-014**: This spec does not change envelope math, account balances, or
  sync rules. It changes how the person reaches the actions the app already
  has.

### Key Entities

- **Open month**: The single month the panorama is showing. Shared by the
  budget section and the month review.
- **Screen action**: An action offered for the current screen, with a drawn
  icon, a label, and one result. Primary actions are on the bar. Secondary
  actions are in the ellipsis menu.
- **Title lag**: The sideways motion of the panorama title while the
  sections move. Slower than the sections, visible, and kept fully on
  screen.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A person moves from the current month to a month a year away
  and back to the current month in under 10 seconds, without leaving the
  panorama, and the to-budget amount matches the selected month each time.
- **SC-002**: On the panorama and on a register, every application-bar
  control other than the ellipsis has a distinct drawn icon, and a tap with
  the labels hidden performs the labeled action. The count of blank icons
  is zero.
- **SC-003**: In a recorded swipe from one section to the next, the title
  moves at least as far as the word "due" in the subtitle is wide, less far
  than the sections move, and no letter of "budget" or of "due north" is
  clipped.
- **SC-004**: With the network disabled, a month change and a bar action
  both complete, and the resulting figures are still there after a restart.
- **SC-005**: In a set of 20 taps aimed at the edge of a bar icon, at least
  19 activate that icon once and do not activate the neighbor.

## Assumptions

- Specs 001 through 007 are already the behavior of the shell, the month,
  the register, sync, schedules, review, and import. This spec does not
  reopen those behaviors.
- Metro 2 still applies. The bottom application bar, the ellipsis, the flat
  color, the oversized lowercase title, and the peeking section stay. Where
  a fuller motion would stutter, the shorter motion from the constitution
  wins.
- "Settings" on the bar opens the settings place specified in
  `specs/009-settings-home`. Until that place exists, the control still
  opens a single screen the person can recognize as settings.
- New budget actions from `specs/010-actual-parity` join this same bar and
  menu. This spec places the actions the app already has.
- Hold, manage categories, add, transfer, reconcile, and import keep the
  results defined in the earlier specs.
- The review month and the budget month become one open month. That
  replaces the separate review-month control from spec 006. A category
  editor keeps the month it was opened on.
- Tablet, widget, and watch layouts stay out of scope.
