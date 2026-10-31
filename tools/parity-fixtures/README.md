# Parity fixtures

Golden fixtures for spec 010 (`specs/010-actual-parity/research.md` R1). Each
scenario runs one action through desktop Actual's own handlers
(`@actual-app/api` 26.8.1) and records the rows Actual wrote. The phone's
parity tests in `core/budget/src/test/kotlin/.../core/parity/` replay the same
action and diff against these rows.

**CI never runs this.** The fixtures are generated once by hand and committed.
Regenerating changes every id, so only regenerate when a scenario is added or
Actual's version is bumped, and review the diff.

## Run

Node 20 or later.

```bash
cd tools/parity-fixtures
npm ci
npm run generate
```

`node generate.mjs <area> [<area> ...]` regenerates only those areas and
leaves the other areas' ids alone.

`schedule-order` needs desktop handlers newer than 26.8.1, so it runs on
`@actual-app/api` 26.10.0 (the `api-26-10` alias in `package.json`) in a
process of its own: name it alone, as `node generate.mjs schedule-order`. A full
run generates it last the same way.

Output goes to `core/budget/src/test/resources/parity/<area>/`:

| File | Contents |
|---|---|
| `<base>.zip` | The budget before any scenario action (Actual export shape: `db.sqlite` + `metadata.json`). |
| `<scenario>.json` | `base`, `today`, `actualVersion`, the `action` that ran, `ids` by name, the `tables` Actual wrote, and sheet `values` (month totals, balances, report query results). |

Every scenario in an area starts from a copy of the same base, so ids match
across scenarios. The clock is pinned to `2026-10-07` so starting balances,
close transfers, averages, and schedules are reproducible.

## Areas

| Area | Base | Scenarios |
|---|---|---|
| `month` | envelope and tracking budgets, Jul–Oct 2026, a hidden category and group, a September hold | breakdown, copy, copy with nothing to copy, 3-month average, set to zero, transfer to to-budget, auto-hold on / off this month / off, tracking copy and zero |
| `accounts` | checking, savings, off-budget brokerage, a card with a transfer partner, an unused and a zero-balance account | create on/off budget with and without balance, rename and note, reorder, close (no transactions, zero balance, on→on, on→off with category), force close, close then reopen |
| `schedules-rules` | checking with Cafe and Corner Shop history | payee→category rule, a rule with parts the phone does not edit, auto-post schedule run twice, schedule with a pattern, learning on and off |
| `reports` | four months of income, bills, a split, transfers on→on and on→off, a deleted transaction | raw results of desktop's cash flow, net worth, and custom-report queries for a range and an empty range |
| `templates` | categories with each template kind in notes, `flags.goalTemplatesEnabled` on | check valid, check invalid, apply, overwrite |
| `automations` | every template type as budget automations (`source: ui`), the newer note forms, Jul–Oct 2026 budgets and spending, `flags.goalTemplatesUIEnabled` on | how desktop parses 70-odd note lines and writes automations back as notes, overwrite, overwrite with `hideFraction`, the automations editor's dry run per category |
| `schedule-templates` | schedules that come monthly, weekly, every two weeks, daily, quarterly, yearly and once; a ranged amount moved off a weekend; a split rule across three categories; past, done and ended schedules; savings carried in from September | overwrite in October, overwrite September then October (carried savings and last month's goal), the dry run per category |
| `cleanup` | sources, sinks with weights, group-scoped lines and an overspending line in notes, Oct 2026 budgets with leftovers and overspending | how desktop parses cleanup note lines, storing cleanup definitions and groups, end-of-month cleanup, cleanup when sources run short |
| `schedule-order` | generated with Actual 26.10.0, where schedule ordering first shipped: four schedules due the same day at the default order, one due later | move after another with no room (shoves the rest down), move to the top, top then after (midpoint), move after a later one, three moves in a row |
| `undo` | the month base | one assignment, to undo back to the base |
