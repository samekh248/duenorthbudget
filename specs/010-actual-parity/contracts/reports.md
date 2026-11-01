# Contract: Reports

Reader: `ReportsReader` (read only). The reporting hub is the home pivot's `reports` item. It lists `net worth` (today's total; opens the net worth graph), `month review` (the open month's income and spending by category, formerly the home `review` item), `cash flow`, and `spending`. Tag `reports-hub`. The last two open `ReportScreens.kt`'s pivot titled `reports`, with items `cash flow` and `spending`, at the one tapped.

## Range

Shared across the pivot. Presets: this month, last 3 months, last 6 months, last 12 months, year to date, custom (start month, end month). Default last 6 months.

## Living transactions

`tombstone = 0`, account not tombstoned, split parents excluded when they have children.

## Cash flow

On-budget accounts. Transfers between on-budget accounts excluded. Per month: income (sum of amounts > 0), expenses (sum of amounts < 0), net. Totals row. Tag `report-cashflow`.

## Net worth

Its own page (`NetWorthScreens.kt`, state in `NetWorthHolder`), not part of the shared range. A line of the total over a span ending today; spans `1m`, `3m`, `6m` (default), `ytd`, `1y`, `all` (from the first transaction). Each point is the sum of every account's running balance through the end of that day, at most 120 evenly spaced days so a long span stays one smooth line; transactions after today are left out. Today's total sits above the line with how far it moved over the span; pressing and sliding along the line reads any day. On-budget and off-budget are shown separately under it; off-budget is in the line only when spec 006's include choice is on. Tags `net-worth-graph-screen`, `net-worth-graph`, `net-worth-span-<span>`.

`ReportsReader.netWorth(range)` (by month end) stays for the parity fixture.

## Spending

Expenses only: amount < 0, on-budget, not a transfer. Group by category (default), group, or payee. Sorted by absolute amount, largest first. Uncategorized shown as its own row. Tag `report-spending`.

The total sits over one flat bar split by each row's share (`report-spending-share`): the accent, a deeper accent, then greys. Under it, `what stood out` lists up to four highlights (`ReportsReader.spendingHighlights`), each a line with its figure in the accent over a caption, beside an accent rule (`report-spending-highlight-N`):

- **share**: the largest row's share of the total, and about how much a month; left out with a single row.
- **change**: the row that moved the most (in money) against the same number of months just before the range; left out when nothing earlier matches or nothing moved.
- **biggest month**: the costliest month and how far it sat over the range's monthly average; left out for a one-month range or when no month stands above it.
- **largest purchase**: the single largest expense, its payee and date.

Each row then shows its swatch, name, amount, and `N% of spending`.

## Empty and loading

An empty range shows zero totals and no error. While a report computes, content-shaped placeholders fade in; no full-screen spinner.
