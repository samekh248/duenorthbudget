# Quickstart: Envelope Month

## Checks

From the repo root, with the Android SDK in `local.properties`:

```sh
./gradlew :core:budget:test :core:design:testDebugUnitTest :app:testDebugUnitTest :app:verifyRoborazziDebug
```

`:core:budget:test` covers the envelope cases below. The app tests cover the empty month, a long group name, a lazy list of 300 groups, a lazy list of 300 categories, and the unparseable-amount note.

Record screenshots only after a deliberate visual change:

```sh
./gradlew :app:recordRoborazziDebug
```

## Cases the budget tests lock

1. Assign 120.00 of 500.00. To-budget is 380.00 and the category available is 120.00. Assigning 0 reverses it.
2. Move 15.00 from Groceries (available 80.00) to Eating Out (available 20.00). To-budget is unchanged. A larger move is refused and both amounts stay put.
3. Overspend Groceries by 30.00 with no cover. Next month's to-budget is 30.00 lower and Groceries starts at 0. With rollover on, next month still shows −30.00 and to-budget is not reduced. Turning rollover off applies that default on the following month.
4. Cover that overspend from a category with enough available. Next month's to-budget is not reduced for it.
5. Hold 250.00 of 400.00. This month's to-budget is 150.00. Next month's to-budget includes the 250.00 inside Actual's carry and does not rise a second time. Releasing the hold restores this month. A larger hold is refused.
6. Add a group and a category, assign, and open the next month. The category is there. The previous month's assignment is unchanged.
7. Hide a category. It leaves the group total. Unhide restores the amount.
8. Delete is refused while the category has an assignment, available money, or a transaction. The note is "move the money or the history first."
9. A tracking file stays a tracking file. Leftover rolls forward only when rollover is on.
