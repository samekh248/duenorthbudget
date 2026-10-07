# Research: Envelope Month

## R1. Hold matches Actual's buffered amount

**Decision**: Holding sets `zero_budget_months.buffered` on the open month. This month's to-budget falls by the hold. Next month's to-budget is `income + (this to-budget + buffered) + uncovered overspend − budgeted − next buffered`. The held amount is inside that sum, so next month shows the same to-budget as if the money had been left unassigned. It does not rise a second time.

**Rationale**: Constitution III requires Actual's formula. `ActualMonthMathTest.holdForNextMonthLowersThisMonthOnly` already locks this. The story's independent test says next month's to-budget *includes* the 250, which this satisfies. Acceptance scenario 1 also says next month *rises* by 250. That second sentence disagrees with Actual, so the formula wins.

**Alternatives considered**: Add the hold on top of leftover carry. That double-counts and fails the existing October fixture.

## R2. Hold is for the next month only

**Decision**: The control sets this month's buffered amount. There is no separate future-month row.

**Rationale**: Actual stores one `buffered` integer on the source month. It becomes part of the next month's `fromLast`. A later month is funded by holding again once the person is on that month.

**Alternatives considered**: A free-form target month. The on-phone schema has nowhere to put it without inventing a table Actual does not use.

## R3. Hidden categories leave the group total and stay in the sheet math

**Decision**: A hidden category is omitted from the month list and from its group available total. Its assignment row stays. To-budget still counts that assignment and, for envelope files, that category's uncovered overspend. Unhiding shows the same amounts.

**Rationale**: Spec 002 overrides spec 001's group total, which had included hidden categories. Removing the row from to-budget would make hide a way to create money. Actual keeps the budget row; the phone list simply stops showing it.

**Alternatives considered**: Drop hidden categories out of `ActualMonthMath` entirely. That changes to-budget when the person hides a row.

## R4. Tracking carryover

**Decision**: Tracking available for a month is `budgeted + spent` plus the previous month's leftover only when the previous month's carryover flag is on. Otherwise the leftover does not roll. Envelope math is unchanged: with carryover off, only a positive leftover carries, and a negative one reduces next month's to-budget.

**Rationale**: The spec says tracking does not reset leftover into next month unless rollover-overspending is on. `trackingDoesNotRollForward` stays true because that fixture's carryover flag is off.

**Alternatives considered**: Use the envelope leftover rule for tracking. That would start rolling positive leftovers by default and break the shell fixture.

## R5. Optimistic edit, then one ordered save

**Decision**: The view model applies `MonthEdits` to the in-memory book and publishes the new month before launching the save. Saves and reloads share a mutex. A reload that started before an edit is dropped if the edit epoch moved.

**Rationale**: FR-001 needs the new figures before the finger lifts. FR-012 forbids a save on the draw thread and a list jump. Stable lazy-list keys keep the edited row in place.

**Alternatives considered**: Wait for SQLite before updating the text. That misses the 100 ms goal whenever the write stalls.

## R6. Deletes tombstone empty rows only

**Decision**: A category or group with a non-zero assignment, a carryover flag, a non-zero available amount in any loaded month, or any alive categorized transaction is refused with "move the money or the history first." Otherwise the row's `tombstone` is set to 1.

**Rationale**: FR-009. Tombstones match the shell's existing read filter.

**Alternatives considered**: Hard-delete the row. A later sync spec needs the tombstone.

## R7. Amount text

**Decision**: The field is a decimal amount in the file's currency. "120" with two decimal places is 12000 minor units. Extra fraction digits, blank text, and other words change nothing and the screen says "enter an amount".

**Rationale**: The spec's parse failure case. Minor units stay integers in the file, same as the shell.

**Alternatives considered**: Accept any precision and round. That hides a typo.
