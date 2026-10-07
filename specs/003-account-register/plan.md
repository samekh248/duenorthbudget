# Implementation Plan: Account Register

**Branch**: `003-account-register` | **Date**: 2026-10-07 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/003-account-register/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Opening an account shows its balance and transactions, newest first, in a lazy Metro list. Adding, editing, categorizing, splitting, transferring, and deleting write the Actual-shaped SQLite file on the phone and the shell's to-budget and group totals follow on the next read, with no network. A register of 500 rows stays lazy. A refresh during a fling is held until the finger lifts.

Envelope budgets project a credit-card payment move when an account is `type = credit` and a `ccPayment:<accountId>` preference names its payment category. Tracking budgets do not. The projection is not a second transaction. On-budget transfers stay uncategorized in the file.

## Technical Context

**Language/Version**: Kotlin 2.2, Java 17 bytecode, Android minSdk 26, compileSdk 36

**Primary Dependencies**: Jetpack Compose BOM 2025.09 (foundation, animation, UI; no Material), Android SQLite on the phone, sqlite-jdbc for JVM tests, Robolectric and Roborazzi for screenshot checks

**Storage**: The open budget directory from spec 001. Transactions, splits, and transfers use Actual's `transactions` and `payees` columns. `accounts.type` is added when missing (`checking` by default). The card-to-payment-category link is the preference id `ccPayment:<accountId>`.

**Testing**: JUnit 4 for register math: income, expense, split, transfer, card payment, tracking, reconcile warning, idempotent save, and a 500-row read. Robolectric Compose tests for the newest row, payee filter, owed balance, and validation copy. Roborazzi screenshots of the register and the entry form in light and dark.

**Target Platform**: Android phone (minSdk 26)

**Project Type**: Mobile app

**Performance Goals**: Newest row of 500 transactions available from the local file in under 1 second. One save completes in under 100 ms on the JVM after a warmup write. The register list is lazy. Tap feedback is the existing press tilt, in the same frame as the press.

**Constraints**: No network permission. No Material widgets. Disk reads and writes run off the main thread. A list gesture holds the visible register until the finger lifts. This spec does not import files, run schedules, or perform the reconciliation ceremony. It only warns before changing a row that is already reconciled.

**Scale/Scope**: One open budget. Register, entry, split, transfer, inbox category picker, and the reconcile warning. Later specs add sync, rules, reconciliation, and import.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Result |
|---|---|---|
| I. Fluid First | Register rows are lazy. Writes do not wait on a network. The press tilt is immediate. A refresh during a scroll is held. Disk I/O stays off the main thread. | Pass |
| II. Metro 2 | Register and forms use the existing type ramp, gutter, flat fill, and app bar. No new Material surface. | Pass |
| III. The Same Budget as Actual | Amounts, dates, splits, and transfers use Actual's columns and signs. Off-budget rows do not enter envelope spent. Card payment available is a projection; see Complexity Tracking. | Pass with a listed exception |
| IV. One Budget at a Time | Writes go to the open budget directory only. | Pass |
| V. Test What the Person Can See | Register and entry form screenshots in light and dark. Math tests do not draw. 500-row read and the refresh gate are tested. | Pass |
| VI. Phone First | No new module. Screens live in `:app`. Rules live in `:core:budget`. | Pass |

Post-design re-check: the same gates hold. The card projection is the only constitution exception, and it is listed below. Stored rows stay Actual-shaped.

## Project Structure

### Documentation (this feature)

```text
specs/003-account-register/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── register.md
│   └── register-screens.md
└── tasks.md
```

### Source Code (repository root)

```text
app/src/main/kotlin/app/duenorth/budget/          # Register, entry, split, transfer, inbox picker
app/src/test/kotlin/app/duenorth/budget/         # Compose and screenshot checks
core/budget/src/main/kotlin/app/duenorth/budget/core/   # RegisterBook, card projection, schema ensure
core/budget/src/test/kotlin/app/duenorth/budget/core/   # Register math
```

**Structure Decision**: No new Gradle module. `:core:budget` gains the register writer and the card projection inside the existing month math. `:app` adds screens and routes on the shell that spec 001 already hosts.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| III. Payment-category available does not match today's desktop Actual | User Story 6 and FR-009 require an envelope card spend to move available from the spending category into that card's payment category, and a later on-budget payment to move it back out. Desktop Actual, as of the 2026 credit-card docs, does not do this move. | Ignoring US6 would fail the spec's own acceptance scenarios. Writing a second transaction would put rows in the file that Actual would count as income. The projection adjusts only the payment category's spent input to the existing envelope sheet, and only for `type = credit` with a `ccPayment:` preference. Tracking files skip it. On-budget transfers stay uncategorized in SQLite. |
