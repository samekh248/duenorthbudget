# Contract: Undo

Owner: `EditJournal` in `:core:budget`; stack held by `ShellViewModel`.

## Steps

One step per person-level action: an assignment, a move, a fill, a transfer to to-budget, an auto-hold change, any account action, a transaction save/edit/delete/split, a schedule or rule save, a template run, a bank link change, and each auto-posted occurrence.

Stack: newest first, max 100, scoped to the open budget, cleared on budget switch and on process death.

## Control

Bar menu id `undo` on Home and Register, present only when the stack is not empty. Its label names the newest step ("undo assign groceries").

## Undo

1. Pop the newest step.
2. For every cell, read the current value. If any cell is not the value the step wrote, go to Conflict.
3. Otherwise write each cell's old value through `recordLocal` in reverse order. A row the step created gets `tombstone = 1`.
4. Paint the previous figures before the finger lifts.

## Conflict

No write. Add an entry to spec 004's conflict list with the cell, the value before the person's edit, and the current server value. The step is discarded.

## Offline

Undo never needs the network. The re-written values sync later like any edit.
