# Implementation Plan: Schedules, Rules, and Payees

**Branch**: `005-schedules-rules` | **Date**: 2026-10-09 | **Spec**: [spec.md](spec.md)

## Summary

The phone budget file gains Actual-shaped `rules`, `schedules`, and `schedules_next_date` tables plus a `transactions.schedule` column. Posting and skipping advance the next date locally. Saving a transaction runs enabled rules in Actual stage order with specificity ranking. Payee rename and merge rewrite transaction payee ids; optional merge rules live in the `pre` stage. The home panorama adds a **due** section; payees are edited from the app bar.

## Technical Context

**Language/Version**: Kotlin 2.2, Android minSdk 26 (unchanged from spec 003)

**Storage**: Actual-compatible JSON in `rules.conditions` / `rules.actions`. Disabled rules are listed in preference `disabledRules` (JSON array of rule ids). Schedule recurrence uses a subset of Actual `RecurConfig` (daily, weekly, monthly, yearly, once).

**Testing**: JVM tests in `ScheduleRulesBookTest` for post, skip, rules, merge. Register tests unchanged.

**Constraints**: No network. Rule application stays in-process on save. Schedule posting reuses `RegisterBook.save`.

## Project Structure

```text
core/budget/.../RuleEngine.kt      # RulesBook, ranking, apply
core/budget/.../Schedules.kt       # SchedulesBook, upcoming list, post/skip
core/budget/.../Payees.kt          # rename, merge, delete
app/.../ScheduleScreens.kt         # Due panorama section
app/.../PayeeScreens.kt            # Payee rename flow
```
