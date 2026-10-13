# Contract: Schedules and rules

Books: `SchedulesBook`, `RulesBook`. Screens: `ScheduleScreens.kt`, `RuleScreens.kt`.

## Schedule editor

Fields: name (optional), account (required), payee, amount (required) with op `is` / `isapprox` / `isbetween`, category (optional), repeat, next date, end, auto-post.

Repeat values:

| UI | Stored `date` condition value |
|---|---|
| once | `"YYYY-MM-DD"` |
| every N days / weeks / months / years | `{frequency, interval: N, start, endMode: "never"}` |
| … ending after K | `endMode: "after_n_occurrences", endOccurrences: K` |
| … ending on a date | `endMode: "on_date", endDate` |

Existing `patterns`, `skipWeekend`, `weekendSolveMode` are kept unchanged and shown as "kept from the file".

Save writes, in one undo step: the `rules` row (conditions + `link-schedule` action + optional category action), the `schedules` row (`posts_transaction` = auto-post), and `schedules_next_date`.

## Auto-post

Runs after budget open and after each sync completes, on `Dispatchers.IO`.

For each schedule with `posts_transaction = 1`, `completed = 0`, and next date ≤ today:

1. If spec 005's match rule finds a matching transaction in the account for that occurrence, advance next date only.
2. Otherwise post the transaction (rules applied) and advance next date.
3. Repeat while the next date is still ≤ today.

Each posted occurrence is one undo step. No server is needed.

## Rule editor

| Condition field | Ops |
|---|---|
| payee | is, isNot, oneOf, notOneOf, contains, doesNotContain, matches |
| account | is, isNot, oneOf, notOneOf |
| category | is, isNot, oneOf, notOneOf |
| amount | is, isapprox, isbetween, gt, gte, lt, lte (with inflow / outflow) |
| date | is, isapprox, gt, gte, lt, lte |
| notes | is, isNot, contains, doesNotContain, matches, oneOf, notOneOf |

| Action field | Ops |
|---|---|
| payee, category, amount, date, cleared | set |
| notes | set, prepend-notes, append-notes |

Stage: pre, default, post. Conditions op: all / any. Anything else in `conditions` or `actions` is kept as raw JSON, shown read-only with "kept from the file", and written back unchanged. Delete writes `tombstone = 1`. Enable / disable keeps the `disabledRules` preference.

## Engine

Rules are ranked and cached per open budget; any `rules` write, local or remote, clears the cache. An op the engine cannot evaluate makes that condition false; it never throws.

## Learning

Preference `learn-categories` (`"true"` / `"false"`, absent = true). Settings row tag `settings-learning`. When true, after a save that sets a category for a payee, run the `updateCategoryRules` port. When false, skip it.

## Tags

`schedule-editor`, `schedule-repeat`, `schedule-autopost`, `rules-screen`, `rule-editor`, `rule-kept-from-file`, `rule-stage`.
