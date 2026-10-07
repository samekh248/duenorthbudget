# Implementation Plan: Metro Budget Shell

**Branch**: `001-metro-budget-shell` | **Date**: 2026-10-07 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-metro-budget-shell/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

The home screen is a Metro 2 panorama titled "due north" with the sections "budget", "accounts", and "inbox". It reads one Actual-shaped budget file already on the phone, shows that file's to-budget figure and each category group's available total, and never waits on a network. The person can start an empty budget, switch files without merging them, and keep a light, dark, or accent choice on the phone rather than inside the budget.

The UI is Jetpack Compose on foundation only (no Material widgets), with Selawik and the Due North Tasks type ramp. Envelope and tracking totals use Actual's published formulas. A refresh that arrives during a swipe is held until the finger lifts, so the row under the finger does not jump.

## Technical Context

**Language/Version**: Kotlin 2.2, Java 17 bytecode, Android minSdk 26, compileSdk 36

**Primary Dependencies**: Jetpack Compose BOM 2025.09 (foundation, animation, UI; no Material), kotlinx.serialization, Android SQLite on the phone, sqlite-jdbc for JVM tests, Robolectric and Roborazzi for screenshot checks

**Storage**: One directory per budget (`metadata.json` + `db.sqlite`) under app files. Phone theme, accent, and the open budget id live in `phone.json`, not in the budget file. SQLite tables use Actual's physical column names for the entities this spec reads.

**Testing**: JUnit 4 for envelope math, file switching, contrast, panorama parallax, and the refresh-during-gesture gate. Robolectric Compose tests for empty notes, long names, and a lazy list of 300 groups. Roborazzi screenshots of the shell in light and dark.

**Target Platform**: Android phone (minSdk 26)

**Project Type**: Mobile app

**Performance Goals**: Budget section visible within 1 second of open on a mid-range phone. Tap feedback in the same frame as the press (within 100 ms). Panorama and list motion stay on the draw thread. A 300-group month projects in under 100 ms on the JVM. List rows are lazy.

**Constraints**: No network permission. No Material ripple, shadow, gradient, or rounded card. Motion yields when the system animator scale is 0. Disk reads for the budget run off the main thread. This spec does not add, edit, or delete categories, assignments, or transactions.

**Scale/Scope**: One open budget. Three panorama sections. Theme and accent settings. Create and switch files. Later specs add editing, sync, schedules, review, and import.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Result |
|---|---|---|
| I. Fluid First | Tap feedback without waiting on a spring. Lists are lazy. Refresh during a gesture is held. Disk I/O is off the main thread. Reduced motion still changes sections. | Pass |
| II. Metro 2 | Panorama, peek, snap, slower title, Selawik ramp from research R2, app bar, flat color, light and dark, accent set from research R5. | Pass |
| III. The Same Budget as Actual | To-budget and group available follow Actual's envelope formulas. Tracking files keep tracking math. Amounts use the file's currency and decimal places. | Pass |
| IV. One Budget at a Time | Switch is explicit and names the file opening and the file left. Files are separate directories. | Pass |
| V. Test What the Person Can See | Screenshot checks in light and dark. Envelope math tests do not draw. Refresh-during-gesture test. | Pass |
| VI. Phone First | Phone layout only. No extra module that exists only to organize. | Pass |

Post-design re-check: the same gates hold. The on-disk schema is an Actual-shaped subset (see research R2), not a claim that desktop Actual can open the file yet. That migration belongs to the sync spec. No constitution violation needs a complexity exception.

## Project Structure

### Documentation (this feature)

```text
specs/001-metro-budget-shell/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── budget-file.md
│   └── shell-screens.md
└── tasks.md
```

### Source Code (repository root)

```text
app/                         # Phone shell: panorama, create, switch, appearance
core/budget/                 # JVM: Actual month math, sqlite session, budget files
core/design/                 # Compose Metro theme, panorama, app bar (no Material)
```

**Structure Decision**: Two core modules plus the app. `:core:budget` has no Android dependency so envelope math and file tests run on a plain JVM. `:core:design` holds Metro components the later specs will reuse. `:app` wires Android SQLite, the activity, and the screens. No server module.

## Complexity Tracking

No constitution violations.
