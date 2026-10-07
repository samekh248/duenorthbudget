# Quickstart: Account Register

## Prerequisites

Same as [the shell quickstart](../001-metro-budget-shell/quickstart.md): JDK 21, Android SDK 36.

## Checks

From the repo root:

```bash
./gradlew :core:budget:test :app:testDebugUnitTest :app:verifyRoborazziDebug ktlintCheck
```

`:core:budget:test` covers income, expense, inbox categorize, split, transfer, credit-card payment, tracking cards, reconciled edits, a zero amount, a repeated save, off-budget rows, and a 500-row register. Those tests do not draw.

`:app:testDebugUnitTest` covers the register copy, the payee filter, a credit balance marked owed, a lazy list of 500 rows, and the entry form's missing-payee message.

`verifyRoborazziDebug` compares the register and the entry form in light and dark. To record after a deliberate visual change:

```bash
./gradlew :app:recordRoborazziDebug
```

## What a passing run shows

- A 12.40 grocery expense lowers the checking balance, raises food spent, and lowers food available by 1240 minor units.
- Income on an on-budget account raises to-budget. The same amount on an off-budget account does not.
- A 30.00 split puts 20.00 on one category and 10.00 on the other, and the balance moves once.
- A 100.00 transfer between two on-budget accounts leaves to-budget unchanged, and deleting one side removes the other.
- A 40.00 card spend moves 4000 minor units from groceries available to the payment category. Paying the card moves that 4000 back. A tracking file does not.
- Saving the same draft id twice leaves one row.
