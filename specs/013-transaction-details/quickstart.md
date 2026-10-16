# Quickstart: Transaction Details Cleanup

Validation guide for [contracts/transaction-details.md](contracts/transaction-details.md). Implementation tasks belong in `tasks.md`.

## Prerequisites

- JDK 21 (bytecode target 17), Android SDK 36
- A phone or emulator with the debug APK and a budget that has more than five categories, one hidden category, and one account with a transfer (the "large" mock budget from spec 001 works)
- A Pixel 6a-class phone for the fluidity scenario

## Checks

```bash
./gradlew ktlintCheck :core:budget:test :core:design:test :app:testDebugUnitTest :app:verifyRoborazziDebug
```

Record screenshots only after a deliberate visual change:

```bash
./gradlew :app:recordRoborazziDebug
```

## Scenarios

1. **One screen (SC-001).** Open an existing transaction in a 40-category budget. Title area reads ACCOUNTS, then the account name ("Visa"), then "transaction". Date, payee, amount with toggle, category, and note are all visible with no scrolling. No buttons in the body.
2. **Category, full screen (US1).** Tap the category box → "category" page, groups with headings, current choice in accent and on screen. Pick another → back on the form with every other edit kept. Repeat and press back → unchanged.
3. **Category, in place.** In a budget with four or fewer categories, tap the box → choices unfold inside it with the accent border. Pick one → box folds.
4. **Hidden category.** Give a transaction a category, hide that category in manage categories, reopen the transaction → box shows it; the list shows it with "(hidden)". Other hidden categories are not listed.
5. **Date (US2).** Set date format to dd.MM.yyyy → box reads "Thu 01.10.2026" for 1 Oct 2026. Tap → "choose date" with columns day, month, year; no keyboard. Flick month from December → January. Set day 31, then month February → day becomes 28 (or 29 in a leap year). Done → box updates; save → register files the row under the new day. Cancel and back leave the date alone.
6. **Import year.** Open a 1999 transaction → year column includes 1999.
7. **Sizes (US3, SC-004).** Date and amount boxes each under half the content width; amount and toggle on one line. Switch the phone to the largest font → nothing clipped, boxes grow, toggle wraps under the amount box and stays 48 dp tall.
8. **Toggle.** Tap income → it fills with the accent; save → amount is positive in the register.
9. **Note.** Type three lines with return → box grows to three lines; type past six → it scrolls inside. The line being typed stays above the keyboard. Register shows the note on one line with spaces.
10. **Errors.** Clear the payee and save → message under payee. Clear the amount → message under amount.
11. **Commands (US4).** Existing ordinary transaction: bar has save and cancel; `•••` lists split, schedule, delete, and each does what the old button did. Transfer: delete only. New transaction: none of the three. Cancel and back leave the transaction unchanged.
12. **Reconciled.** Edit a reconciled transaction and save → the spec 006 warning still appears.
13. **Fluidity (FR-027, SC-007).** On a Pixel 6a-class phone with a 200-category budget, open the list and fling; flick each date column. Feedback under 100 ms, no visible dropped frames. With "remove animations" on, the pickers open without motion.

## Screenshots

Roborazzi light and dark for: existing transaction, new transaction, transfer, combo box unfolded in place, category list, date picker, validation error, 2× font scale. Before-and-after of the old form goes in the PR.
