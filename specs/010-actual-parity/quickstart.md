# Quickstart: Actual Parity

Validation guide for the contracts in [contracts/](contracts/). Implementation tasks belong in `tasks.md`.

## Prerequisites

- JDK 21 (bytecode target 17)
- Android SDK 36 (`sdk.dir` in `local.properties` or `ANDROID_HOME`)
- Golden fixtures in `core/budget/src/test/resources/parity/` (R1). To regenerate, Node 20 and `@actual-app/api` 26.8.1; never in CI.
- The spec 004 localhost Actual-shaped server for sync and bank-link harness tests
- Desktop Actual 26.8.1 for the manual side-by-side checks
- Airplane mode / disabled network for offline scenarios

## Checks

```bash
./gradlew :core:budget:test :app:testDebugUnitTest :app:verifyRoborazziDebug
```

Parity tests only:

```bash
./gradlew :core:budget:test --tests 'app.duenorth.budget.core.parity.*'
```

## Scenarios

1. **Breakdown.** Open the fixture month with income, assignments, an overspend, and a hold. Tap to-budget. The five parts add up to the header.
2. **Fill the month.** Copy last month and confirm: visible expense categories match last month; hidden and income are unchanged. Three-month average sets each category to its average spending; a category with only two months of activity uses two. Set to zero, cancel: nothing changed; confirm: every budgeted amount is zero and no row was deleted. Compare `zero_budgets` with the fixture's expected rows.
3. **To-budget and auto-hold.** Transfer 40 from a category to to-budget offline; both figures change before the finger lifts. Try 1 more than available; refused. Turn on auto-hold for Paycheck; next month's to-budget rises by this month's paycheck income. Turn it off for this month only; next month stays automatic.
4. **Accounts.** Add an on-budget account with a 500 balance; it shows 500 and to-budget rises by 500. Rename and add a note; restart; both remain. Reorder. Close it into checking; it disappears; show closed; reopen. Force close another; the month drops its money. Sync with desktop; balances match.
5. **Schedules.** Create a monthly auto-post schedule due today. Force-stop, turn the network off, open: one transaction within 1 second. Open again: still one. Edit a desktop-made schedule that uses "last Friday": the pattern is kept.
6. **Rules and learning.** Create "payee is Corner Shop → category Groceries"; save a Corner Shop transaction; category filled. Open a desktop rule with `imported_payee contains`; it shows "kept from the file", still runs, and survives a save. Turn learning off; categorize a new payee; no rule appears. Benchmark: 100 rules vs none within 50 ms.
7. **Reports.** For the fixture range, cash flow, net worth by month, and spending by category/group/payee match the expected totals. An empty range shows zeros.
8. **Templates.** Experimental off: edit a note with `#template 50`; nothing budgeted. Turn on experimental and budget templates. Check a note with a bad line: the category is named and nothing changes. Apply: only zero categories fill. Overwrite after confirm: all templated categories follow notes. Each template kind matches the fixture.
9. **Bank link (harness + one real pass per provider).** Link an account through the localhost server's fake provider; fetch is offered; the setup token is not visible anywhere afterwards and is not in `db.sqlite`. Remove the link; fetch disappears. Stop the server; linking shows an error and the budget still edits.
10. **Undo.** Assign, edit an account, edit a transaction; undo three times, newest first; each paints under 100 ms offline. Undo is absent with nothing to undo. Assign, let a remote change to the same cell arrive, undo: a conflict appears and the server value stays.

## Expected results

- Parity tests diff rows against desktop fixtures for every story.
- Sync tests show each new dataset round-tripping through the spec 004 harness, and the upgrade backfill emitting messages for pre-existing phone rules and schedules.
- Light and dark screenshots exist for every new screen and confirm gate.
- Spec 001 fluidity budgets still pass; new budgets cover the 100-rule save and undo.
