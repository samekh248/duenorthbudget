# Metro 2 design parity

**Purpose**: Mark each shipped surface against constitution Principle II (Metro 2 / Due North Light Panorama), not against pixel-matched budget artboards.

**Created**: 2026-10-11

**References**:

- [Constitution II](../../.specify/memory/constitution.md)
- [Metro 2 notes](README.md)
- Tasks mockups: `docs/design/metro-mockups.svg` in [samekh248/duenorthtasks](https://github.com/samekh248/duenorthtasks)
- Shell contract: [`specs/001-metro-budget-shell/contracts/shell-screens.md`](../../specs/001-metro-budget-shell/contracts/shell-screens.md)

**How to read status**

| Status | Meaning |
|--------|---------|
| **pass** | Matches the written Metro 2 rule for that surface |
| **partial** | Uses Metro tokens, but a named constitution II rule is missing or diluted |
| **n/a** | No budget mockup exists; judged only against written rules |

This is an implementation review. It is not a speckit-checklist requirements-quality file. Checkboxes are not used.

## Shared language vs Tasks SVG

This repo has **no budget-screen drawings**. The Tasks SVG shows **today / lists / task detail / settings** (light and dark). Budget screens inherit the **language**, not those frames.

| Constitution II rule | Shared tokens | Status |
|----------------------|---------------|--------|
| Home is a panorama; oversized lowercase `budget`; `due north` under it; title stays on screen; slower sideways travel; 40 dp peek; snap | `MetroPanorama`, `PanoramaMotion.TITLE_TRAVEL` 15%, `MetroDimens.Peek` | **pass** |
| Secondary screens use a pivot where they need tabs | Appearance, server, register, and envelope editors are single columns. No pivot. Appearance is the closest Tasks-settings analogue | **partial** |
| Every screen has a bottom application bar: round outlined icons and `•••` | `ShellChrome` + `MetroAppBar` (72 dp) wraps every route | **pass** |
| Selawik; oversized lowercase titles; Tasks R2 type ramp | Selawik Light / Semilight / Regular / Semibold. Panorama title 118sp −4%. Section headers 40sp Light. `pageTitle` (13sp semibold, +6% tracking) exists and is unused; secondary screens use 52sp `header` instead of Tasks `SETTINGS`-style page titles | **partial** |
| Flat fills only: no shadow, gradient, rounded card, elevation, Material ripple | Foundation-only Compose; `metroPress` has no indication | **pass** |
| Light `#FFFFFF` / `#111111`; dark `#000000`; default magenta `#B0005E` / `#F0389A`; WP8.1 + light orange + coral; 4.5:1 | `MetroTheme`, `DesignRulesTest` | **pass** |
| Motion: turnstile, tilt, continuum, list stagger (yield to Principle I) | Tilt on press (`PressFeedback`). New panorama rows fade (`RowArrivals`). No turnstile page change, no continuum fly-in, no stagger delay | **partial** |
| 12 dp gutter, 24 dp grid, 48 dp targets | `MetroDimens.Gutter` / `TouchTarget`. Manage-categories rows pack several buttons on one line | **partial** |

## Panorama sections

Spec 001 and the Tasks home mockup describe a short panorama. Later specs added sections.

| Section | Spec | Status vs Metro 2 / 001 contract |
|---------|------|----------------------------------|
| `budget` | 001, 002 | **pass** — month, to-budget, group/category rows, lowercase |
| `accounts` | 001 | **pass** — on/off budget, amounts beside names |
| `due` | 005 | **n/a** — not in 001 or Tasks SVG; same panorama chrome |
| `review` | 006 | **n/a** — same |
| `net worth` | 006 | **n/a** — same |
| `inbox` | 001 | **pass** — payee + amount; empty note |

Adding sections is allowed by later specs. It still means the home panorama is **not** the three-section 001 / Tasks-length home.

## Screen-by-screen

### Spec 001 — shell

| Surface | Status | Notes |
|---------|--------|-------|
| Home panorama | **pass** | Title, subtitle tuck, peek, snap, parallax, lazy lists, Roborazzi light/dark |
| Create budget | **pass** | Lowercase `new budget`, Metro field + list, app bar |
| Budgets list | **pass** | Lowercase title, open file marked |
| Confirm switch | **pass** | Names both files; Metro copy |
| Appearance | **partial** | Theme + accent grid match settings *content*. No pivot (`theme` / other tabs). Title is `header`, not Tasks `SETTINGS` page title |

### Spec 002 — envelope month

| Surface | Status | Notes |
|---------|--------|-------|
| Category budget | **partial** | Metro type and gutter. Secondary page, not a pivot. No continuum from the tapped category |
| Move money | **partial** | Same |
| Hold / release | **partial** | Same |
| Manage categories | **partial** | Functional (rename, hide, delete, up/down). Dense control rows; not a Metro 2 composition. Weakest visual match in the app |

### Spec 003 — register

| Surface | Status | Notes |
|---------|--------|-------|
| Register | **partial** | Metro header + lazy list. Secondary screen (correct). No continuum from the account row. Action stack (`add` / `transfer` / `reconcile` / import) is utilitarian |
| Add / edit transaction | **partial** | Lowercase forms, Metro fields |
| Split | **partial** | Same |
| Transfer | **partial** | Same |
| Inbox category picker | **partial** | Same |
| Reconcile warning | **partial** | Metro header; overlay, not a designed dialog |

### Spec 004 — sync

| Surface | Status | Notes |
|---------|--------|-------|
| Server | **partial** | Metro form. Thin progress on chrome (constitution I). No settings pivot |
| Budget password | **partial** | Distinct screen, no amounts until unlock — matches spec, not a Tasks frame |
| Conflicts | **partial** | List + Metro copy |
| Assign / spend (sync helpers) | **partial** | Reuse envelope/register language |
| Confirm replace | **pass** | Same confirm pattern as switch |

### Spec 005 — schedules, rules, payees

| Surface | Status | Notes |
|---------|--------|-------|
| Due section | **n/a** | Extra panorama pane; Metro section header |
| Schedule duplicate | **partial** | Confirm overlay |
| Payees | **partial** | List + rename/merge. No page title style; no pivot |

### Spec 006 — reconcile and review

| Surface | Status | Notes |
|---------|--------|-------|
| Month review section | **n/a** | Extra panorama pane |
| Category review | **partial** | Secondary list |
| Net worth section | **n/a** | Extra panorama pane |
| Reconcile start / session | **partial** | Metro forms and running difference |

### Spec 007 — import

| Surface | Status | Notes |
|---------|--------|-------|
| Import preview | **partial** | Metro header (`import`), add/skip rows |
| Import review | **partial** | Same |

## Closest Tasks artboards

| Tasks frame | Budget analogue | Match |
|-------------|-----------------|-------|
| home: today / lists (light, dark) | Home panorama | Language **pass**; section set **partial** (more panes) |
| task detail | Register, category budget, category review | Type + app bar **pass**; no continuum; titles use `header` not detail page-title lockup |
| settings: theme | Appearance | Accents and follow-phone **pass**; no pivot; title weight **partial** |

## Highest-value gaps (if budget mockups are not drawn)

1. **Motion**: turnstile between routes; continuum from panorama/register rows (or document a Principle I exemption).
2. **Secondary title**: use `pageTitle` (or a budget lockup) the way Tasks uses `DUE NORTH · …` / `SETTINGS`.
3. **Pivot**: appearance (and any future settings) as theme / sync tabs instead of a single stack.
4. **Manage categories**: a Metro composition (one row, one action) instead of a toolbar per line.
5. **Budget mockups**: draw the panorama (six sections), register, and manage screens so this file can score against art, not only Principle II.

## Automated coverage today

| Check | What it proves |
|-------|----------------|
| `DesignRulesTest` | Accent contrast 4.5:1; title parallax smaller than a section |
| `ShellContentTest` | Title + `due north` tuck; long names; lazy 300 groups / 300 categories |
| `ShellScreenshotTest` / `RegisterScreenshotTest` | Light/dark shell and register — not a mockup diff |
