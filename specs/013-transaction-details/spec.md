# Feature Specification: Transaction Details Cleanup

**Feature Branch**: `013-transaction-details`

**Created**: 2026-10-16

**Status**: Draft

**Input**: User description: "Clean up the transaction details screen to adhere to metro ui windows phone 8.1 standards. The category selection should be a dropdown. The date field should be treated like a date. Make the field only as long as it needs to be. Respect whitespace needs"

## Context

The transaction details screen (spec 003, opened by tapping a register row
or adding a transaction) is a single scroll that does not read as a
Windows Phone 8.1 page today:

- The page title is the payee in its own capitalization ("Bus"), with no
  small uppercase line naming where the page sits.
- Every field is a full-width grey box whose only label is placeholder text
  inside it. Once a value is typed, nothing says which box is the date, the
  payee, or the amount.
- The date is free text holding an ISO string ("2026-10-01"). It ignores the
  date format the person picked in settings and has to be typed.
- Category is every category in the budget written out as a stack of
  subheader-size lines. A budget with forty categories pushes the action
  buttons several screens down.
- Split, schedule, and delete are full-width outlined buttons stacked under
  the form, so the form ends in a wall of buttons.
- Vertical spacing is a mix of 8 and 12 dp gaps that do not follow the 24 dp
  grid, so related and unrelated items sit the same distance apart.

This spec reshapes that screen into a Windows Phone 8.1 form: titled page,
labelled fields sized to their content, a date picker for the date, a combo
box for the category, commands on the application bar, and spacing on the
Metro 2 grid. It changes how the screen looks and how values are picked. It
does not change what a transaction can hold or how it is saved.

```text
 Today                              After
 ┌──────────────────────────┐       ┌──────────────────────────┐
 │ Bus                      │       │ ACCOUNTS                 │
 │ [2026-10-01            ] │       │ transaction              │
 │ [Bus                   ] │       │ Visa                     │
 │ [12.40                 ] │       │ date                     │
 │ expense  income          │       │ [Thu 10/1/2026 ▾]        │
 │ [note                  ] │       │ payee                    │
 │ category                 │       │ [Bus                   ] │
 │ no category              │       │ amount                   │
 │ Groceries                │       │ [12.40     ] [exp|inc]   │
 │ Household                │       │                          │
 │ ... every category ...   │       │ category                 │
 │ [        save          ] │       │ [Groceries           ▾ ] │
 │ [        split         ] │       │ note                     │
 │ [       schedule       ] │       │ [                      ] │
 │ [        delete        ] │       │ [                      ] │
 │  (o)  (o)           ...  │       │  (✓)  (✕)           ...  │
 └──────────────────────────┘       └──────────────────────────┘
                                     ... menu: split, schedule, delete
```

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Pick a category from a dropdown (Priority: P1)

A person editing a transaction sees the current category as one closed
combo box under a "category" label. Tapping it opens the choices. Picking
one closes the list and shows the new category in the box. The rest of the
form stays where it was.

**Why this priority**: the category list is what makes the screen long and
pushes everything else off screen. Replacing it does more for the screen
than any other single change, and it is the change the person named first.

**Independent Test**: open a transaction in a budget with more than five
categories, tap the category box, pick a different category, save, and
confirm the register row shows the new category. The form is one screen
tall with the box closed.

**Acceptance Scenarios**:

1. **Given** a transaction in Groceries, **When** the details screen opens,
   **Then** one closed box under the "category" label reads "Groceries" with
   a down chevron, and no other category name is on screen.
2. **Given** the box is closed, **When** the person taps it in a budget with
   more than five choices (counting "no category"), **Then** a full-screen
   list opens with the page title "category", every choice listed under its
   category group name, and the current choice in the accent color.
3. **Given** five or fewer choices, **When** the person taps the box,
   **Then** the choices unfold in place inside the box, the current one in
   the accent color, the same way the settings combo boxes behave.
4. **Given** the choices are open, **When** the person taps one, **Then** the
   list closes, the box shows that choice, and the form is back where it
   was with its other edits kept.
5. **Given** the full-screen list is open, **When** the person presses back,
   **Then** the list closes and the category is unchanged.
6. **Given** a transaction with no category, **When** the screen opens,
   **Then** the box reads "no category".
7. **Given** a split parent or a transfer between two on-budget accounts,
   **When** the screen opens, **Then** no category box is shown (as today).

---

### User Story 2 - Treat the date as a date (Priority: P1)

The date shows in the date format the person chose in settings, inside a
box sized for a date. Tapping it opens the Windows Phone 8.1 date picker:
three looping columns for month, day, and year, with done and cancel on
the application bar. The person never types a date.

**Why this priority**: an ISO string that must be typed by hand is the most
error-prone field on the screen, and it ignores a setting the person
already made.

**Independent Test**: set the date format to MM/dd/yyyy, open a transaction
dated 2026-10-01, confirm the box reads "10/1/2026" style text, change the
date with the picker to 9/28/2026, save, and confirm the register files the
row under that day.

**Acceptance Scenarios**:

1. **Given** the date format setting is dd.MM.yyyy, **When** a transaction
   dated 1 October 2026 opens, **Then** the date box reads "01.10.2026" with
   the weekday before it ("Thu 01.10.2026").
2. **Given** the date box, **When** the person taps it, **Then** the date
   picker opens full screen with the page title "choose date" and month,
   day, and year columns set to the transaction's date, ordered to match
   the date format setting.
3. **Given** the picker is open, **When** the person flicks a column,
   **Then** it loops (December rolls to January, 31 to 1) and the day column
   never offers a day the chosen month does not have.
4. **Given** the picker is open, **When** the person taps done, **Then** the
   picker closes and the date box shows the new date. **When** they tap
   cancel or press back instead, **Then** the date is unchanged.
5. **Given** a new transaction, **When** the screen opens, **Then** the date
   box holds today.
6. **Given** the date box, **When** the person taps it, **Then** no
   keyboard appears.

---

### User Story 3 - A titled, labelled, right-sized form (Priority: P2)

The screen reads as a Windows Phone 8.1 page: a small uppercase line naming
the account over the oversized lowercase title "transaction". Each field
has a small label above it. Fields whose values have a known length (date,
amount) are only as wide as that length needs. Free-text fields (payee,
note) keep the full width. Spacing follows the Metro 2 grid so a label sits
close to its own field and visibly apart from the field before it.

**Why this priority**: this is the "looks like Windows Phone 8.1" part of
the request. It matters, but the screen is usable without it once stories 1
and 2 land.

**Independent Test**: compare light and dark screenshots of a new and an
existing transaction against the acceptance scenarios below.

**Acceptance Scenarios**:

1. **Given** a transaction in the "Visa" account, **When** the details
   screen opens, **Then** the top of the page reads "ACCOUNTS" in the small
   uppercase title line, then "transaction" in the page-title size, then
   "Visa" (the account name as stored) in the subheader size and secondary
   color on its own line under the title. The payee is no longer used as the
   page title.
2. **Given** any field with a value, **When** the screen is shown, **Then**
   the field's name ("date", "payee", "amount", "category", "note") is shown
   above it in lowercase secondary text, and stays shown after a value is
   entered.
3. **Given** the date box, **When** it is drawn, **Then** it is no wider
   than its longest possible value in the chosen format plus its chevron
   and padding, and it does not stretch to the screen edge.
4. **Given** the amount box, **When** it is drawn, **Then** it is wide
   enough for an amount with up to seven whole digits and the budget's
   decimal places, and no wider.
5. **Given** the amount row, **When** it is drawn, **Then** the expense and
   income choice is a compact two-part toggle on the same line as the amount
   box: two adjacent segments ("expense", "income") in the control text size,
   the same height as the amount box, the chosen segment filled with the
   accent and the other outlined. Tapping either segment picks it.
6. **Given** a long payee or note, **When** the person types it, **Then** the
   payee and note boxes span the content width (12 dp gutter on each side).
   The note box wraps long text onto new lines instead of scrolling
   sideways.
7. **Given** the form, **When** it is drawn, **Then** every label-to-field
   gap is the same small step, every field-group-to-next-label gap is the
   same larger step, all gaps fall on the Metro 2 grid, and the left edges of
   the title, labels, and fields line up on the 12 dp gutter.
8. **Given** a phone with the largest system font size, **When** the form
   opens, **Then** no label or value is clipped and the sized fields grow
   with the text.

---

### User Story 4 - Commands on the application bar (Priority: P2)

Save and cancel are icon buttons on the application bar. Split, schedule,
and delete move into the bar's ellipsis menu. The body of the page holds
only the form.

**Why this priority**: Windows Phone 8.1 keeps page commands on the
application bar, not as buttons in the content. Removing three stacked
full-width buttons is most of the whitespace cleanup, but the screen still
works with them in place.

**Independent Test**: open an existing transaction, expand the application
bar, and confirm save, cancel, split, schedule, and delete are reachable
there and no command buttons remain in the page body.

**Acceptance Scenarios**:

1. **Given** an existing ordinary transaction, **When** the details screen
   opens, **Then** the application bar shows a save (check) and a cancel
   (cross) button, and the ellipsis menu lists "split", "schedule", and
   "delete".
2. **Given** a new transaction, **When** the screen opens, **Then** the menu
   offers none of split, schedule, or delete.
3. **Given** a transfer, **When** the screen opens, **Then** the menu offers
   "delete" only (as today, transfers cannot be split or scheduled).
4. **Given** edits on the form, **When** the person taps cancel or presses
   back, **Then** the screen closes and the transaction is unchanged.
5. **Given** the person taps delete, **When** it runs, **Then** it behaves
   exactly as the delete button does today.

---

### Edge Cases

- A note with line breaks: the register's one-line detail under the payee
  shows the note with each line break read as a space, cut to fit as today.
  The note keeps its line breaks when stored and synced.
- A budget with only "no category" and one other category: the combo box
  unfolds in place (five or fewer choices).
- A budget with hundreds of categories: the full-screen list scrolls
  smoothly and opens scrolled so the current choice is visible.
- A hidden category already set on the transaction: the box still shows its
  name, and the full-screen list includes it, marked as hidden, so the
  person can keep it.
- An income category on an expense, or the reverse: allowed, as today. The
  list shows income categories under their own group.
- A date far outside the picker's default year range (an imported 1999
  transaction): the year column includes that year.
- Choosing 31 in a month column then switching to February: the day clamps
  to the last day of February.
- The date format setting changes while the screen is open: the box shows
  the new format the next time the screen opens; an open picker keeps its
  column order until closed.
- A validation error on save (missing amount): the error shows in the accent
  color directly under the field it concerns, not at a fixed place in the
  form.
- Reconciled transaction: opening, editing, and saving still go through the
  existing reconcile warning (spec 006); this spec does not change it.
- Right-to-left or very narrow screens: sized fields never exceed the
  content width.

## Requirements *(mandatory)*

### Functional Requirements

**Page**

- **FR-001**: The transaction details screen MUST show the Windows Phone 8.1
  title area: "ACCOUNTS" in the small uppercase title line, over the
  lowercase page title "transaction", followed by the account's name as
  stored, in the subheader size and secondary color, a small step (6 dp)
  under the title. The first field label sits one 24 dp grid step below the
  account name. This holds for new and existing transactions.
- **FR-002**: Every field MUST have a persistent lowercase label above it in
  secondary text. Placeholder text inside a field MAY remain as a hint but
  MUST NOT be the only label.
- **FR-003**: The body MUST contain only the form fields and any validation
  message. Save, split, schedule, and delete MUST NOT be drawn as buttons in
  the body.

**Date**

- **FR-004**: The date MUST be shown in the date format chosen in settings,
  preceded by the short weekday name.
- **FR-005**: Tapping the date MUST open a full-screen date picker with
  looping month, day, and year columns, ordered to match the chosen date
  format, and set to the current value.
- **FR-006**: The picker MUST offer only real dates; the day column MUST
  adjust to the chosen month and year (leap years included).
- **FR-007**: The picker MUST have done and cancel on the application bar.
  Done applies the date; cancel and back discard it.
- **FR-008**: The date MUST NOT be editable by typing, and tapping it MUST
  NOT raise the keyboard.
- **FR-009**: New transactions MUST default to today, as now.

**Category**

- **FR-010**: Category MUST be a single combo box showing the current
  choice and a down chevron, styled like the settings combo boxes (bordered
  box, accent border while open).
- **FR-011**: With five or fewer choices (including "no category"), tapping
  the box MUST unfold the choices in place. With more than five, tapping it
  MUST open a full-screen list titled "category" with choices grouped under
  their category group names, in the budget's category order.
- **FR-012**: The current choice MUST be shown in the accent color in the
  open list, and the full-screen list MUST open scrolled to it.
- **FR-013**: Picking a choice MUST close the list and update the box
  without discarding other unsaved edits on the form.
- **FR-014**: "no category" MUST remain a choice, listed first.
- **FR-015**: When category is not shown today (split parents, transfers
  between accounts with the same on-budget state), it MUST still not be
  shown.

**Sizing and spacing**

- **FR-016**: The date box MUST be sized to the longest value its format can
  produce, plus its chevron and padding, and MUST NOT stretch to the content
  width.
- **FR-017**: The amount box MUST be sized to hold seven whole digits plus
  the budget's decimal places.
- **FR-017a**: The expense and income choice MUST be a compact two-segment
  toggle on the same line as the amount box, to its right: segments in the
  control text size, the same height as the amount box, the chosen segment
  filled with the accent (text in the accent's contrast color) and the other
  outlined in the foreground color. Together, the amount box and the toggle
  MUST fit the content width of a Pixel 6a-class phone at the default font
  size.
- **FR-018**: Payee and note MUST span the content width.
- **FR-018a**: The note MUST be a multiline box: it opens two lines tall,
  wraps long text, accepts line breaks from the keyboard's return key, and
  grows with its text up to six lines, then scrolls inside the box. The page
  MUST keep the line being typed visible above the keyboard.
- **FR-019**: All sized fields MUST grow with the system font size and MUST
  NOT exceed the content width.
- **FR-020**: Spacing MUST use two steps on the Metro 2 grid: a small step
  between a label and its field, and a larger step between one field group
  and the next label. Title, labels, and fields MUST share the 12 dp left
  gutter.
- **FR-021**: Every tap target (fields, combo box, picker columns, list
  rows, expense/income) MUST be at least 48 dp tall.
- **FR-022**: A validation message MUST appear in the accent color under the
  field it concerns.

**Commands**

- **FR-023**: The application bar MUST show save (check) and cancel (cross)
  icon buttons on the transaction details screen.
- **FR-024**: For an existing ordinary transaction, the application bar menu
  MUST list "split", "schedule", and "delete". For a transfer, it MUST list
  "delete" only. For a new transaction it MUST list none of the three.
- **FR-025**: Cancel and back MUST close the screen without saving.
- **FR-026**: Save, split, schedule, and delete MUST behave exactly as their
  current buttons do.

**Quality**

- **FR-027**: The date picker, category list, and combo box MUST open with
  visible feedback within 100 ms and hold the phone's frame rate while
  scrolling or flicking (constitution Principle I).
- **FR-028**: The screen, the open combo box, the full-screen category list,
  and the date picker MUST each have light and dark screenshot checks
  (Principle V).

### Key Entities

- **Transaction draft**: the values on the form (date, payee, amount,
  direction, category, note). Unchanged by this spec.
- **Category choice**: a category's name, its group name, and whether it is
  an income category; "no category" is the empty choice.
- **Date format setting**: the person's chosen display format from settings
  (spec 009); it decides how the date reads and the picker's column order.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In a budget with 40 categories, the whole form of an existing
  transaction (title through note) fits on one screen of a Pixel 6a-class
  phone at the default font size, with no scrolling.
- **SC-002**: A person can change a transaction's category in two taps plus
  any scrolling of the list (open, pick), down from scrolling past every
  category.
- **SC-003**: A person can change a transaction's date without using the
  keyboard, and no typed-date format error can occur.
- **SC-004**: The date box and amount box each take less than half the
  content width at the default font size, and the amount box and the
  expense/income toggle share one line.
- **SC-005**: Every field on the screen can be identified by its label with
  a value filled in, in light and dark.
- **SC-006**: No command buttons remain in the page body; all five commands
  are reachable from the application bar.
- **SC-007**: The date picker and category list open and scroll with no
  visible dropped frames on a Pixel 6a-class phone.

## Assumptions

- "Windows Phone 8.1 standards" means the platform's own form patterns:
  persistent field headers, the DatePicker flyout (full-screen looping
  columns with done/cancel on the app bar), the ComboBox (expands in place
  for five or fewer items, opens a full-screen picker above that), and page
  commands on the application bar. The five-item threshold is the
  platform's own rule.
- The small title line names the home section (ACCOUNTS), and the account
  the register is open on gets its own line under the page title (decided
  2026-10-16).
- The page title is "transaction" for both new and existing transactions.
  The payee shows in its own field.
- "Make the field only as long as it needs to be" applies to fields whose
  length is known (date, amount). Payee and note hold free text of any
  length, so they keep the full width.
- Seven whole digits (up to 9,999,999) covers everyday transactions; larger
  amounts still type and scroll within the box.
- Expense and income stay a two-way choice rather than becoming a combo
  box, drawn as a compact segmented toggle so it fits on the amount's line.
  Windows Phone 8.1 has no segmented control; this is two Metro toggle
  buttons joined edge to edge, chosen over radio buttons and a toggle switch
  on 2026-10-16.
  At font sizes where the amount box and toggle no longer fit side by side,
  the toggle wraps to the line directly under the amount box rather than
  shrinking below the 48 dp touch target.
- The full-screen category list shows hidden categories only when the
  transaction already uses one; otherwise hidden categories are left out,
  matching the register's category picker.
- Out of scope: the transfer form, the split form, and the register's
  quick category picker. They keep their current layout; the same combo
  box and date picker can be applied to them in a later spec.
- No change to how transactions are stored, synced, or validated beyond
  the date no longer being typed.
