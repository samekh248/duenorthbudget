# Contract: Month screens

Metro foundation only. The panorama title stays "budget" with "due north" under it. These controls sit on the budget section and on pushed screens, not in a new panorama section.

## Budget section

- "previous" and "next" change the viewed month by one. Each month shows its own budgeted, spent, and available figures.
- The header is "to budget" or "balance", then the formatted amount.
- When that amount is negative, the section also says "to budget is negative".
- Envelope files show "hold". The held amount, when non-zero, is labeled "held for next month".
- Each expense group row shows the group name and the visible categories' available total. The row opens the group.
- No expense groups: "nothing to budget".
- "new group" opens a name field.

## Group

- Categories that are not hidden, in sort order. Each row shows the name, spent, available, a budgeted field, and "assign".
- Assign confirms the field as the new budgeted amount.
- The name opens the category screen.
- Hidden categories are listed separately with "show".
- "new category", rename, "up", "down", and "delete" act on the group.
- Row keys are category ids. Updating an amount does not reorder the list.

## Category

- Budgeted field and "assign".
- Move amount, then one destination per other expense category. The button says "cover" when this category's available is negative and "move" otherwise.
- "rollover overspending" or "stop rollover".
- "hide" and "delete".

## Hold

- A field for the amount held for next month, prefilled with the current hold.
- "hold" saves it. "release" sets it to 0.
- Tracking files do not open this screen.

## Notes

Refusal copy from [month-edits.md](month-edits.md) replaces any previous note. A successful edit clears the note. Copy stays lowercase, in the Metro body style.
