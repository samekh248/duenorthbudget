# Contract: Register screens

Metro 2, foundation only. Page title type is the existing header style. Gutter 12 dp. Touch targets at least 48 dp. Copy is lowercase.

## Register

- Shows the account name, the word "balance", and the formatted balance.
- A credit account with a negative balance also shows "owed". The amount stays signed.
- Rows show date (`oct 7`), payee, category, and amount. A split shows "split" and one line per part. A transfer with no category shows "transfer". A missing category shows "no category".
- Order is newest first. The list starts at the top whenever the screen is opened. Scroll position is not restored.
- Payee field filters the rows in memory. The balance does not change. Clearing the field shows every row. Nothing is deleted.
- "add" opens entry. "transfer" opens the transfer form. A row opens entry for that id.
- Empty register copy is "no transactions".
- While the list scrolls, a newer page is held and applied when the scroll ends.

## Entry

- Fields: date, payee, amount, expense or income, note, category list including "no category".
- Save with a blank payee shows "enter a payee". A blank or bad amount shows "enter an amount". Zero shows "amount cannot be zero".
- The draft id is generated when the form opens and reused for every tap on that form.
- "delete" is present only when editing. "split" is present when the row is not a transfer.

## Split

- At least two parts. Each part has an amount and a category. Save refuses a sum that is not the transaction amount, and the register row stays as it was.
- "one category" removes the split.

## Transfer

- Other accounts are listed. Amount and date are required.
- When exactly one of the two accounts is off-budget, a category list is shown and save requires a category.
- Otherwise no category control is shown.

## Inbox category

- Tapping an inbox row lists categories plus "no category".
- Choosing one returns to the panorama. The row leaves the inbox when it has a category.

## Reconciled warning

- Copy: "this reconciliation will no longer match".
- "change" writes. "keep" returns to the form without writing.

## Screenshots

Register and entry form, light and dark, under `app/src/test/snapshots/`.
