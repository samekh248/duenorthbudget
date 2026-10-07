# Research: Metro Budget Shell

Phase 0 output for [plan.md](plan.md). Each entry is Decision / Rationale / Alternatives.

## R1. UI toolkit

- **Decision**: Jetpack Compose with `compose.foundation` only. Text is `BasicText` / `BasicTextField`. No Material 3 dependency.
- **Rationale**: Metro 2 forbids ripple, elevation, gradients, and rounded cards. Foundation supplies `HorizontalPager`, lazy lists, and `graphicsLayer`, which is enough for the panorama. This matches Due North Tasks research R1.
- **Alternatives**: Material theming (leaks ripple and shape). Android Views (slower to get the panorama). Flutter (leaves the Android phone stack the constitution asks for).

## R2. Budget file

- **Decision**: Each budget is a directory: `metadata.json` (`id`, `budgetName`) and `db.sqlite`. Tables use Actual's physical column names for the rows this shell reads: `preferences`, `accounts`, `payees`, `category_groups`, `categories`, `transactions` (`isParent`, `isChild`, `acct`, `description`, `transferred_id`), `zero_budgets`, `reflect_budgets`, `zero_budget_months`. A preference `dueNorthSchema=1` marks the subset. `user_version` is left at 0 so the file is not pretending to be a fully migrated Actual database.
- **Rationale**: The shell has to show Actual's numbers and keep envelope vs tracking. Naming columns the way Actual's SQLite does lets a later sync spec migrate the same files. Spec 001 does not download a server file and does not claim desktop Actual can open a phone-created file.
- **Alternatives**: A JSON budget (a second product). Embedding `@actual-app/core` (Node, not a fit for the phone UI). Writing Actual's full migration chain now (belongs with sync, and a wrong `user_version` makes desktop fail closed).

## R3. To-budget and available

- **Decision**: Reproduce Actual's envelope sheet from `envelope.ts` (`@actual-app/core` 26.8.1), in integer minor units:

  - Category leftover = budgeted + spent + (previous leftover if that month's carryover is set, otherwise the positive part of the previous leftover).
  - To budget = income + (previous to-budget + previous buffered) + last month's uncovered overspend + (negated sum of budgeted) − buffered.
  - Buffered is `zero_budget_months.buffered`. When that is 0, buffered is the sum of income-category activity whose carryover flag is set (Actual's `buffered-auto`).
  - A group's available total is the sum of its expense categories' leftovers, including hidden categories, excluding tombstones.
  - Tracking mode does not roll leftovers forward. A category balance is budgeted + spent for the open month. The header is that sum, labeled "balance". The file's mode is never converted.

  Spent and balances sum transactions with `isParent = 0` and `tombstone = 0`, and drop a child whose parent is tombstoned. Account balance uses the same filter (`getAccountBalance` in Actual). Inbox rows are on-budget, not a transfer (`transferred_id` is null), category null, not a parent, newest date then `sort_order` then id. Off-budget and transfers do not need a category in Actual, so they are not "still to categorize".
- **Rationale**: The constitution forbids a parallel set of numbers. The formulas above are the ones Actual's spreadsheet runs.
- **Alternatives**: Summing group budgets only (drops rollover and uncovered overspend). Showing every null-category row including transfers (those are not the inbox Actual's uncategorized list is).

## R4. Metro 2 look

- **Decision**: Tokens from Due North Tasks research R2 and R5, which the constitution points at. Selawik (OFL) Light, Semilight, Regular, Semibold. Panorama title 118sp Light, tracking −4%. Section headers 40sp Light, lowercase. Peek 40dp. Title shift across the first-to-last span is 15% of the title width (`TITLE_TRAVEL`), so the title moves less than a section and stays on screen. Sections snap. The app bar is 72dp, round outlined buttons, ellipsis expands labels. Gutter 12dp, touch target 48dp. Light background `#FFFFFF` / foreground `#111111`. Dark background `#000000`. Default accent `#B0005E` on light and `#F0389A` on dark. The other accents are the Windows Phone 8.1 set plus light orange and coral, each with a light-theme and a dark-theme value checked at 4.5:1.
- **Rationale**: This repo has no budget mockups. Those notes are the reference the constitution names.
- **Alternatives**: A new type ramp. Stock Windows Phone magenta `#D80073` on white (fails 4.5:1 for captions, which is why the tasks notes darkened it).

## R5. Motion and refresh

- **Decision**: Parallax and tilt use `graphicsLayer` only. When `Settings.Global.ANIMATOR_DURATION_SCALE` is 0, section changes jump and tilt is skipped; a pressed row still flips to a flat pressed fill in the same frame. A shell reload that finishes while a panorama or list gesture is active is kept in `RefreshGate` and applied when the gesture ends.
- **Rationale**: Principle I: the row under the finger must not jump, and reduced motion still changes state. Holding the update is the same rule Due North Tasks uses while a list is dragging.
- **Alternatives**: Applying rows immediately with stable keys (still moves a row if the update inserts above the finger). Animating the snap when animations are off.

## R6. Tests

- **Decision**: JVM tests for the formulas, currency formatting (including a currency with no minor units), create/switch/reopen, contrast, title parallax, and the refresh gate. Robolectric tests for the panorama text, a long name beside a large amount, and a 300-row lazy list. Roborazzi captures the shell in light and dark.
- **Rationale**: Principle V. The math must fail the build without a device. Screenshot checks need a headless renderer; Roborazzi is what Due North Tasks uses.
- **Alternatives**: Instrumented tests only (no device in this environment). Paparazzi (also fine; Roborazzi matches the sibling app).
