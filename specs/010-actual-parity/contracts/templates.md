# Contract: Budget templates

Books: `TemplateParser`, `TemplateEngine`. Screen: `TemplateScreens.kt`.

## Gate

| `flags.experimental` | `flags.goalTemplatesEnabled` | Behavior |
|---|---|---|
| false | any | Notes editable; templates never applied; menu id `templates` absent |
| true | false | Same as above; settings shows the "budget templates" switch |
| true | true | `templates` menu offers check, apply, overwrite |

## Lines

Recognised only at the start of a line in a category's note:

| Form | Kind |
|---|---|
| `#template 50` | simple |
| `#template up to 200` / `#template 50 up to 200` | upTo |
| `#template 600 by 2026-12` (+ `repeat every N months|years`) | by |
| `#template schedule Rent` (+ `full`) | schedule |
| `#template 10% of Paycheck` / `of all income` / `of available funds` (+ `previous`) | percentage |
| `#template average 3 months` | average |
| `#template remainder` / `#template remainder 2` | remainder |
| `#template-2 …` | any kind at priority 2 |
| `#goal 500` | goal (sets long goal, budgets nothing) |

## Actions

| Action | Confirm | Writes |
|---|---|---|
| check | — | nothing; lists invalid lines by category name, or "all templates valid" |
| apply | — | budgeted for templated categories whose budgeted is 0 in this month |
| overwrite | always | budgeted for every templated, visible category this month |

Engine order: priorities ascending, each limited by remaining to-budget; remainder lines last, split by weight. Hidden categories skipped. Each run also writes `goal` / `long_goal` and `categories.goal_def` as Actual does, in one undo step.

Any invalid line in a category stops apply and overwrite for the whole month and names the category.
