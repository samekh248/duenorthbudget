# Implementation Plan: Envelope Month

**Branch**: `cursor/envelope-month-078b` | **Date**: 2026-10-07 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-envelope-month/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

The open budget's month becomes editable. The person sets a category's budgeted amount, moves available money between categories, covers or rolls overspending, and holds this month's leftover to-budget for next month. They can walk one month at a time and add, rename, hide, unhide, reorder, and delete empty groups and categories.

Figures use Actual's envelope sheet (`ActualMonthMath`). A confirmed edit updates the on-screen to-budget and available amounts on the main thread, then writes the SQLite file on a background dispatcher. Hidden categories leave the month list and their group total. The file's envelope or tracking mode is never flipped.

## Technical Context

**Language/Version**: Kotlin 2.2, Java 17 bytecode on Android modules, JVM 21 for `:core:budget`, Android minSdk 26, compileSdk 36

**Primary Dependencies**: Jetpack Compose BOM 2025.09 (foundation only), Android SQLite, sqlite-jdbc in JVM tests, Robolectric, Roborazzi

**Storage**: Existing on-phone budget directory. Writes go to `zero_budgets` or `reflect_budgets`, `zero_budget_months.buffered`, and `category_groups` / `categories`. No new tables. `preferences.budgetType` is read and never updated here.

**Testing**: JUnit for assign, move, overspend, hold, hide, and delete. Robolectric for the month list (including 300 categories) and the amount field. Roborazzi re-records the shell because the budget section gains month controls.

**Target Platform**: Android phone (minSdk 26)

**Project Type**: Mobile app

**Performance Goals**: The new to-budget and available figures are in the UI state before the save starts (under 100 ms, no network). Recomputing a 300-category month stays under 100 ms on the JVM. Saves run off the main thread. Lazy lists keep stable row keys.

**Constraints**: No network. No Material. No transaction entry, sync, or reconcile. Disk writes off the main thread. A save must not reorder the list under the finger.

**Scale/Scope**: One open budget, one viewed month, groups and categories on that file. Hold targets the next month only, matching Actual's buffered amount.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Result |
|---|---|---|
| I. Fluid First | Assign updates state before the disk write. Lists stay lazy and keyed. Refresh during a gesture still uses `RefreshGate`. Month changes are a pure recompute. | Pass |
| II. Metro 2 | Editor screens use the existing Metro type, app bar, and flat controls. No new panorama section. | Pass |
| III. The Same Budget as Actual | To-budget, leftover, rollover, and hold use the envelope sheet already tested against Actual. Tracking rolls a leftover forward only when that month's carryover is on. | Pass |
| IV. One Budget at a Time | Edits apply only to the open file. | Pass |
| V. Test What the Person Can See | Math cases are unit tests. The month list and the unparseable-amount note are Compose tests. Screenshots cover the budget section's new controls. | Pass |
| VI. Phone First | Phone layout only. Editing lives in the existing app and `:core:budget` modules. | Pass |

Post-design re-check: the same gates hold. Hold matches Actual's buffered column rather than adding the held amount a second time on the next month (research R1). No constitution violation needs a complexity exception.

## Project Structure

### Documentation (this feature)

```text
specs/002-envelope-month/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── month-edits.md
│   └── month-screens.md
└── tasks.md
```

### Source Code (repository root)

```text
core/budget/src/main/kotlin/app/duenorth/budget/core/
├── ActualMonthMath.kt      # category leftovers; tracking carryover walk
├── BudgetBook.kt           # in-memory file and month projection
├── BudgetBookStore.kt      # load and save the editable slice
├── MonthEdits.kt           # assign, move, hold, groups, categories
├── ShellReader.kt          # shell for the clock month, via the book
└── BudgetLibrary.kt        # readBook and saveBook
app/src/main/kotlin/app/duenorth/budget/
├── ShellViewModel.kt       # optimistic edits, month routes
├── ShellScreens.kt         # month controls on the budget section
├── MonthScreens.kt         # group, category, hold
└── DueNorthApp.kt          # routes
```

**Structure Decision**: Keep the shell modules. The month book is JVM-testable in `:core:budget`. The app only paints it and writes through the library.

## Complexity Tracking

No constitution violations.
