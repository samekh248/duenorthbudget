# Implementation Plan: Envelope Month

**Branch**: `002-envelope-month` | **Date**: 2026-10-10 | **Spec**: [spec.md](spec.md)

## Summary

The Metro budget panorama becomes an editable envelope month: assign and move
money, hold to-budget for a later month, toggle rollover overspending, and walk
months. Category structure is managed on the phone (groups and categories) on
top of the shell from spec 001. Envelope math stays in `ActualMonthMath` and
`ShellReader`; writes go through `EnvelopeBook` and `SyncCoordinator` so sync
from spec 004 can record local edits.

## Technical Context

**Language/Version**: Kotlin 2.2, Android minSdk 26 (unchanged from spec 001)

**Storage**: Actual-shaped `zero_budgets`, `zero_budget_months` (hold buffer),
`categories`, and `category_groups`. Assignments and carryover use
`BudgetEdits` / `SyncSchema.recordLocal`.

**Testing**: JVM tests in `EnvelopeMonthTest` and `ActualMonthMathTest` for
assign, move, hold, and overspend rules. Compose tests for month UI where
needed.

**Constraints**: No new transaction entry here (spec 003). Edits must not block
the UI thread; list refresh uses `RefreshGate` from spec 001.

## Project Structure

```text
core/budget/.../EnvelopeMonth.kt     # EnvelopeBook, copy strings
core/budget/.../ActualMonthMath.kt   # Envelope sheet (shared with shell)
core/budget/.../SyncCoordinator.kt   # assign, move, hold, category APIs
app/.../EnvelopeScreens.kt           # Category budget, move, hold, manage
app/.../ShellScreens.kt              # Month navigation on budget section
app/.../ShellViewModel.kt              # Routes and envelope edit IO
```
