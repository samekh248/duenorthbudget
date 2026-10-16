# Contract: Reports

Reader: `ReportsReader` (read only). Opened from Home `•••` → `reports`, the reporting hub: a top-level view titled `reports` that lists `net worth` (today's total; opens the on/off budget totals that used to be a home pivot item), `cash flow`, `net worth by month`, and `spending`. Tag `reports-hub`. The last three open `ReportScreens.kt`'s pivot titled `reports`, with items `cash flow`, `net worth`, `spending`, at the one tapped.

## Range

Shared across the pivot. Presets: this month, last 3 months, last 6 months, last 12 months, year to date, custom (start month, end month). Default last 6 months.

## Living transactions

`tombstone = 0`, account not tombstoned, split parents excluded when they have children.

## Cash flow

On-budget accounts. Transfers between on-budget accounts excluded. Per month: income (sum of amounts > 0), expenses (sum of amounts < 0), net. Totals row. Tag `report-cashflow`.

## Net worth

For each month end in the range: sum of each account's running balance through that date. On-budget and off-budget shown separately; off-budget included only when spec 006's include choice is on. Tag `report-networth`.

## Spending

Expenses only: amount < 0, on-budget, not a transfer. Group by category (default), group, or payee. Sorted by absolute amount, largest first. Uncategorized shown as its own row. Tag `report-spending`.

## Empty and loading

An empty range shows zero totals and no error. While a report computes, content-shaped placeholders fade in; no full-screen spinner.
