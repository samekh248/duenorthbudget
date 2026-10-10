# Implementation Plan: Import Transactions

**Branch**: `007-import-transactions` | **Date**: 2026-10-10 | **Spec**: [spec.md](spec.md)

## Summary

The register gains file import and bank fetch into a shared preview: rows are classified as new or duplicate before anything is written. Confirm applies rules, inserts non-duplicates with Actual-shaped `financial_id`, and opens a batch review list that categorizes in place without scroll jumps. Bank fetch uses accounts that already have `account_sync_source` and calls the same SimpleFIN proxy shape Actual uses when the person is signed in to a server; otherwise fetch explains there is nothing to pull.

## Technical Context

**Language/Version**: Kotlin 2.2, Java 17, Android minSdk 26

**Primary Dependencies**: Jetpack Compose (Metro components), sqlite-jdbc + JUnit for core tests, Robolectric for Compose tests

**Storage**: Actual-shaped SQLite. New columns: `transactions.financial_id`, `transactions.imported_description`; `accounts.account_id`, `accounts.account_sync_source`. Import batch ids live in preference `dueNorthImportBatch`.

**Testing**: JVM tests for parsers, duplicate detection, confirm/cancel, and bank preview. Compose test for stable batch row keys.

**Target Platform**: Android phone

**Performance Goals**: Preview first rows within 1s for 200-row files; batch categorize updates in place.

**Constraints**: No full-screen spinner during fetch; no bank connection setup on phone; disk I/O off main thread.

## Constitution Check

| Principle | Gate | Result |
|---|---|---|
| I. Fluid First | Preview and fetch off main thread; batch list uses stable keys; small progress only | Pass |
| II. Metro 2 | Import and review screens use existing Metro components | Pass |
| III. Same Budget as Actual | Duplicates match date/amount/payee and `financial_id`; rules run on insert | Pass |
| IV. One Budget | Imports target one account in the open budget | Pass |
| V. Test What the Person Can See | JVM import tests; batch UI test tag | Pass |
| VI. Phone First | Core in `:core:budget`, screens in `:app` | Pass |

## Project Structure

```text
core/budget/src/main/kotlin/app/duenorth/budget/core/
  ImportFileParser.kt, TransactionImport.kt, BankSync.kt
app/src/main/kotlin/app/duenorth/budget/
  ImportScreens.kt
specs/007-import-transactions/
  plan.md, research.md, data-model.md, quickstart.md, contracts/, tasks.md
```
