# Research: Account Register

Phase 0 output for [plan.md](plan.md). Each entry is Decision / Rationale / Alternatives.

## R1. Where a transaction lives

- **Decision**: Keep Actual's columns. `transactions.amount` is a signed integer in minor units (outflow negative). `date` is `YYYYMMDD`. `description` is a payee id. A split parent has `isParent = 1`, `category` null, and children with `isChild = 1` and `parent_id`. Account balance and category spent keep ignoring parents, which spec 001 already does. A transfer is two rows with `transferred_id` pointing at each other. Each row's payee is the payee whose `transfer_acct` is the other account. Deleting one side tombstones the other. Tombstone is `tombstone = 1`, not a hard delete.
- **Rationale**: The shell already sums non-parent rows. Using the same rows means a new expense shows up in the group total without a second number system.
- **Alternatives**: A single row with a JSON split list (Actual would not read it). Hard deletes (they drop the audit trail Actual keeps via tombstones).

## R2. What counts as spent

- **Decision**: Envelope and tracking spent sum alive non-parent amounts whose category is set and whose account is not off-budget. An off-budget transaction can still be categorized for the register, and it still changes that account's balance, but it does not change to-budget or a category leftover.
- **Rationale**: FR-004 and User Story 1 say an off-budget account does not change the month's to-budget. Actual treats off-budget activity as outside the envelope.
- **Alternatives**: Leaving the spec 001 sum, which included every categorized row regardless of `offbudget`. That fails FR-004 as soon as someone categorizes an off-budget row.

## R3. Credit cards

- **Decision**: An account is a card when `accounts.type` is `credit`. The payment category is the preference `ccPayment:<accountId>` (value is a category id). On an envelope budget only:
  - Each alive non-parent, non-transfer expense-category transaction on that card adds the negation of its amount to the payment category's spent for that transaction's month. A −40 grocery spend makes groceries spent −40 and the payment category spent +40, so groceries available falls by 40 and the payment category available rises by 40.
  - Each alive non-parent transfer on that card whose other side is an on-budget account adds the negation of the card-side amount to the payment category. A +40 payment onto the card makes the payment category spent −40. Groceries do not move.
  - Tracking budgets do not apply either adjustment.
- **Rationale**: Those are User Story 6's numbers. Desktop Actual's current docs do not move envelope money into a payment category; see the plan's Complexity Tracking. The adjustment is an input to the existing leftover formula, not a new transaction, so the file's rows stay transfers and normal expenses. `accounts.type` is Actual's own column. The preference avoids a column Actual's `accounts` table does not have.
- **Alternatives**: A stored budget transfer that rewrites `zero_budgets` (a grocery spend would then hit the category twice if spent is also recorded). Categorizing the on-budget payment transfer (Actual leaves those categories null, and a later sync would fight the server). Doing nothing (fails the spec's 40 / 40 / 0 scenario).

## R4. Entry, filter, and the finger

- **Decision**: The register screen is a lazy column ordered by `date DESC`, `sort_order DESC`, `id DESC`. List state is not saved, so a new visit opens on the newest row. Payee filter is a view over the loaded rows; clearing it shows the same rows. Save uses the draft's id as the primary key, so a second tap updates that row instead of inserting another. Validation that needs no disk (blank payee, blank or zero amount, bad date, split that does not add up) returns before a write. Reconciled rows return a confirm result and write only when the caller passes `force`. Disk work stays on the IO dispatcher. `RefreshGate` holds a newer register while the list is scrolling.
- **Rationale**: FR-012, FR-013, and FR-014, plus Principle I. The press tilt from spec 001 is the immediate tap feedback; the numbers replace the register as soon as the local write returns.
- **Alternatives**: Saving the scroll index (the spec forbids reopening at a stale position). Debouncing saves into a queue that could double-insert. Blocking the main thread on SQLite (StrictMode already flags that).

## R5. Screens

- **Decision**: Reuse Metro text, fields, buttons, and the app bar. The register is a secondary screen, not a new panorama section. Tapping an account opens it. Tapping an inbox row opens the category list. The entry form, split form, and transfer form are further screens on the same back stack.
- **Rationale**: Principle II and VI. The panorama already shows accounts and the inbox; this spec adds the place those rows lead.
- **Alternatives**: A new Material dialog. A fourth panorama section for the open account (the panorama is the home of the month, not of one account).
