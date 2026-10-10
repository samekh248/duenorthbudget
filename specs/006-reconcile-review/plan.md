# Implementation Plan: Reconcile and Review

**Branch**: `006-reconcile-review` | **Date**: 2026-10-09 | **Spec**: [spec.md](spec.md)

## Summary

Add account reconciliation (statement balance, cleared toggles, finish/cancel/resume), a month spending review that matches envelope spent math, category drill-down, and a net-worth screen with a persisted off-budget toggle. Core rules live in `:core:budget`; Compose screens and routes live in `:app`.

## Technical Context

**Language/Version**: Kotlin 2.2, Android minSdk 26

**Storage**: Actual-shaped SQLite plus `phone.json` for net-worth preference. In-progress reconciliation and last statement values use `preferences` rows with `dueNorth` prefixes. `accounts.last_reconciled` is added via `ActualSchema.ensure`.

**Testing**: JVM tests for reconcile math, review totals vs `ShellReader`, and net worth. Robolectric tests for review/reconcile copy and tags.

**Constraints**: No network. Lazy lists with the same gesture gate as the register. Reconciliation does not touch budget assignments.

## Project Structure

```text
core/budget/src/main/kotlin/.../Reconcile.kt
core/budget/src/main/kotlin/.../Review.kt
app/src/main/kotlin/.../ReviewScreens.kt
app/src/main/kotlin/.../ReconcileScreens.kt
```
