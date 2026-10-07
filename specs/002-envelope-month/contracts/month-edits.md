# Contract: Month edits

In-memory edits return either the next `BudgetBook` or a refusal. The screen publishes the next book before `BudgetBookStore.save`. A refusal leaves the book unchanged.

| Action | Writes | Refusal copy |
|---|---|---|
| Assign budgeted amount | That category's assignment for the open month | "enter an amount" when the text is not a currency amount |
| Move available | Source budgeted decreases, destination budgeted increases, same month | "not enough available" when the amount is above the source available. "enter an amount" when the text is not a positive amount |
| Cover | Same as move, into the overspent category | Same as move |
| Rollover on/off | `carryover` on that month's assignment | None |
| Hold | `zero_budget_months.buffered` on this month | "not enough to hold" when the amount is outside `0..to-budget + current hold`. "enter an amount" when the text is not an amount |
| Release | Buffered set to 0 | None |
| Add, rename, reorder group or category | `category_groups` or `categories` name and `sort_order` | "enter a name" when the trimmed name is empty |
| Hide, unhide | `categories.hidden` | None |
| Delete | `tombstone = 1` when the delete guard passes | "move the money or the history first." |

Save rules:

- Envelope files write `zero_budgets`. Tracking files write `reflect_budgets`. The other table is left alone.
- `preferences` is not updated.
- Transactions, accounts, and payees are not updated.
- A reload waits until a queued save finishes, then drops itself if a newer edit exists.

Month projection after each action matches [data-model.md](../data-model.md) and `ActualMonthMath`.
