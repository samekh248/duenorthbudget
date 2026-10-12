# Implementation Plan: Streamlined Metro Controls

**Branch**: `008-streamlined-controls` | **Date**: 2026-10-12 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/008-streamlined-controls/spec.md`

## Summary

The panorama keeps one open month, shared by the budget section and the month review. Previous month, next month, the current calendar month, and a list from 24 months before the current calendar month through 24 months after it are one control on the panorama, not a row of word buttons on the budget list. A month that is already in memory replaces the on-screen figures in the same tap. The bottom bar follows the screen: drawn icons, no empty circles, and no action that does not apply. The panorama title moves at least the width of the word "due" on each section change, stays fully on screen, and stays put when animations are off. Envelope math, balances, and sync rules stay as they are. Everything reads and writes the budget already on the phone.

## Technical Context

**Language/Version**: Kotlin 2.2, Java 17 bytecode, Android minSdk 26, compileSdk 36

**Primary Dependencies**: Jetpack Compose BOM (foundation, animation, UI; no Material), existing Metro components in `:core:design`, existing Actual-shaped reads in `:core:budget`

**Storage**: No schema change. Month figures and bar writes use the open budget's `db.sqlite` through `BudgetLibrary` and `SyncCoordinator`. The open month is view state. It is not a new preference and it is not written into the budget file.

**Testing**: JUnit for the month window, the cache-hit swap, pinned editor months, the title-shift function, and which actions a route shows. Robolectric Compose tests for the month control, bar taps with labels hidden, non-overlapping targets, and a list that stays still during a gesture. Roborazzi light and dark screenshots of the panorama and a register after the bar changes.

**Target Platform**: Android phone (minSdk 26)

**Project Type**: Mobile app

**Performance Goals**: A cached month change updates the month name and the header amount in the same frame as the tap (before the finger lifts). A tap still paints feedback within 100 ms. Title motion is a `graphicsLayer` translation only. Prefetch of the month window runs off the main thread and must not block a swipe.

**Constraints**: No new network calls. No Material ripple, shadow, gradient, or rounded card. Touch targets stay at least 48 dp. Motion yields when the animator scale is 0. Disk reads stay off the main thread; a month that is not cached yet updates its name immediately and its figures when the read returns, and a read that finishes during a scroll waits until the finger lifts. This spec does not change envelope math or sync conflict rules. Settings content beyond a recognizable screen belongs to spec 009. New budget actions belong to spec 010.

**Scale/Scope**: One open budget. One open month. A 49-month window (current calendar month ± 24). The panorama sections already on the home screen. Per-route application bars for the routes that exist today.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Result |
|---|---|---|
| I. Fluid First | Cached month swaps on the main thread. Uncached reads and the 49-month warm run on `Dispatchers.IO`. A gesture holds the painted month until the finger lifts. Title motion is a translation. Reduced motion keeps the title still and still changes sections. | Pass |
| II. Metro 2 | Panorama, peek, snap, ellipsis, round outlined buttons, flat color, light and dark, 48 dp targets. The title stays 118 sp unless that size would clip the required lag, in which case it scales down only enough to keep every letter on screen. | Pass |
| III. The Same Budget as Actual | Month figures still come from `ShellReader` and `readMonthReview`. Tracking files stay tracking files. No parallel totals. | Pass |
| IV. One Budget at a Time | The month control and the bar operate on the open file only. | Pass |
| V. Test What the Person Can See | JVM tests for motion and action mapping. Compose tests for the control and the bar. Screenshot checks in light and dark. | Pass |
| VI. Phone First | Changes stay in `:core:design` and `:app`. No new module. | Pass |

Post-design re-check: the same gates hold. The title scale-down is the constitution's own rule that the title stays on screen and that motion yields when the full treatment would fail. No constitution violation needs a complexity exception.

## Project Structure

### Documentation (this feature)

```text
specs/008-streamlined-controls/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── month-control.md
│   ├── screen-bar.md
│   └── panorama-title.md
└── tasks.md             # /speckit-tasks, not this command
```

### Source Code (repository root)

```text
core/design/src/main/kotlin/app/duenorth/budget/design/
  PanoramaMotion.kt          # title lag, including reduced motion
  MonthWindow.kt             # ±24 month range (new)
  components/MetroComponents.kt
                             # glyphs, bar, ellipsis menu, panorama title
app/src/main/kotlin/app/duenorth/budget/
  ScreenBar.kt               # route → primary and secondary actions (new)
  ShellViewModel.kt          # one open month, cache, pinned editor month
  DueNorthApp.kt             # bar follows the route
  ShellScreens.kt            # month control; drop word buttons on the list
  RegisterScreens.kt         # drop add/transfer/reconcile/import word buttons
  ReviewScreens.kt           # drop the separate review-month button
```

**Structure Decision**: No new Gradle module and no sqlite migration. Presentation rules that do not touch the budget file live in `:core:design` so JVM tests can cover them. The route-to-action map lives in `:app` next to `ShellRoute`. Writes keep using the coordinators specs 002–007 already have.

## Complexity Tracking

No constitution violations.
