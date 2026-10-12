# Tasks: Streamlined Metro Controls

**Input**: Design documents from `/specs/008-streamlined-controls/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Included. The plan's Testing section and constitution Principle V require JVM tests for the month window, cache-hit swap, pinned editors, title shift, and route actions; Compose tests for the control and the bar; and Roborazzi light and dark screenshots.

**Organization**: Tasks are grouped by user story so each story can be implemented and tested on its own.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm the existing modules and ignore rules; no new Gradle module and no sqlite migration

- [ ] T001 Verify `.gitignore` covers Android and Gradle outputs (`local.properties`, `.gradle/`, `**/build/`, `*.iml`, `.idea/`) and that `.specify/feature.json` stays ignored
- [ ] T002 Confirm no new module is required in `settings.gradle.kts`; work stays in `:core:design` and `:app` per `plan.md`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared month-window and screen-action types every story uses

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T003 Add `MonthWindow` in `core/design/src/main/kotlin/app/duenorth/budget/design/MonthWindow.kt` with `anchor: YearMonth`, `radius` 24, and `months` from `anchor.minusMonths(24)` through `anchor.plusMonths(24)` inclusive, oldest first
- [ ] T004 [P] Expand `AppGlyph` and add `ScreenAction` (`id`, `glyph`, `label`, `primary`, `run`) in `core/design/src/main/kotlin/app/duenorth/budget/design/components/MetroComponents.kt`; remove `AppGlyph.More`; keep the ellipsis as chrome, not a `ScreenAction`
- [ ] T005 [P] JVM tests for the ±24 window in `core/design/src/test/kotlin/app/duenorth/budget/design/MonthWindowTest.kt` (49 months; oldest and newest bounds)

**Checkpoint**: Month window and action types exist without UI wiring.

---

## Phase 3: User Story 1 - Change the month in one place (Priority: P1) 🎯 MVP

**Goal**: One open month drives the budget section and the review. Previous, next, current calendar month, and a list from the ±24 window are one control. Cached changes paint before the finger lifts.

**Independent Test**: With October showing a known to-budget amount, move to September, to a month a year away, and back to the current calendar month. Each stop shows that month's name, year, and to-budget. The review matches. The person never leaves the panorama.

### Tests for User Story 1

- [ ] T006 [P] [US1] JVM tests for open-month selection in `app/src/test/kotlin/app/duenorth/budget/OpenMonthTest.kt` (cache hit swaps shell and review in one update; gesture sets `pendingMonth` and applies when the gesture ends; cache miss sets `displayedMonth` immediately; zero-amount month is not an error)
- [ ] T007 [P] [US1] JVM tests for pinned editor months in `app/src/test/kotlin/app/duenorth/budget/OpenMonthTest.kt` (`GroupCategories`, `CategoryBudget`, `MoveCategory`, `HoldMonth` pin `openMonth` at push; later `openMonth` changes do not change `pinnedMonth`; writes use `pinnedMonth`)

### Implementation for User Story 1

- [ ] T008 [US1] Replace `budgetMonth` and `reviewMonth` with `openMonth` (initial value current calendar month) and `pinnedMonth` in `app/src/main/kotlin/app/duenorth/budget/ShellViewModel.kt`; `load`/`apply`/`refreshBudgetShell` read shell and review for `openMonth`; switching budgets drops the month cache
- [ ] T009 [US1] Add `MonthCache` and the painted-month transitions from `data-model.md` in `app/src/main/kotlin/app/duenorth/budget/ShellViewModel.kt` (warm the 49-month window on `Dispatchers.IO` when a budget opens; cache hit with no gesture applies in the same state update; gesture holds painted rows via the existing refresh gate; newer selection discards an older read)
- [ ] T010 [US1] Add `MonthControl` (tags `month-label`, `month-previous`, `month-next`, `month-current`, `month-list`, `month-option-YYYY-MM`) in `app/src/main/kotlin/app/duenorth/budget/ShellScreens.kt` per `contracts/month-control.md`; previous hidden on oldest; next hidden on newest; current hidden when already on the calendar month
- [ ] T011 [US1] Show `MonthControl` on Home and the four pinned editor routes, and remove previous/next word buttons from `BudgetSection` and the review previous-month button from `app/src/main/kotlin/app/duenorth/budget/ReviewScreens.kt` and `ShellScreens.kt`; show `budgetName` as one ellipsized line that does not overlap `month-label` or `to-budget`
- [ ] T012 [US1] Wire selection and pin-on-push in `app/src/main/kotlin/app/duenorth/budget/DueNorthApp.kt` and `ShellViewModel.kt` so envelope editors use `pinnedMonth` and returning to Home shows the current `openMonth`

**Checkpoint**: User Story 1 works offline with one month control and no list word buttons for month changes.

---

## Phase 4: User Story 2 - Buttons that work, with real icons (Priority: P1)

**Goal**: Every round bar button has a drawn icon. A tap runs the action whether or not labels are showing. The ellipsis expands labels and the menu. No empty circles. Inapplicable actions are absent.

**Independent Test**: On the home panorama and on an account register, tap each visible application-bar icon with the labels hidden. Each tap opens the place or runs the action its label names. None of the icons are blank.

### Tests for User Story 2

- [ ] T013 [P] [US2] Glyph ink tests in `core/design/src/test/kotlin/app/duenorth/budget/design/AppGlyphTest.kt` (every `AppGlyph` draws non-empty ink; no `More`)
- [ ] T014 [P] [US2] Compose bar-tap tests in `app/src/test/kotlin/app/duenorth/budget/ScreenBarContentTest.kt` (collapsed `app-bar-{id}` runs once; expanded runs the same action; `app-bar-ellipsis` toggles and does not navigate; adjacent targets do not share a hit)

### Implementation for User Story 2

- [ ] T015 [US2] Draw stroked icons for hold, categories, add, settings, transfer, reconcile, import, save, cancel, budgets, appearance, sync, and payees in `core/design/src/main/kotlin/app/duenorth/budget/design/components/MetroComponents.kt`; circles stay 48 dp with at least 8 dp gaps
- [ ] T016 [US2] Update `MetroAppBar` in `core/design/src/main/kotlin/app/duenorth/budget/design/components/MetroComponents.kt` so the clickable area is the circle, labels show only when expanded, secondary actions appear only as `app-bar-menu-{id}` while expanded, and the ellipsis is tagged `app-bar-ellipsis`

**Checkpoint**: Bar icons are drawn and respond with labels hidden.

---

## Phase 5: User Story 3 - Actions live on the screen they change (Priority: P2)

**Goal**: The bar follows the route. Home offers hold, manage categories, add, and settings. Register offers add, transfer, reconcile, and import. Forms offer save and cancel. Those actions are not duplicated as word buttons on the lists.

**Independent Test**: From the panorama, hold, manage categories, and add are reachable from the bar and are not word buttons on the list. A register bar offers add, transfer, reconcile, and import. A form bar offers save and cancel.

### Tests for User Story 3

- [ ] T017 [P] [US3] Route-action mapping tests in `app/src/test/kotlin/app/duenorth/budget/ScreenBarTest.kt` (Home envelope: `add`, `hold`, `categories`, `settings`; Home tracking: no hold; no budget: `settings` only; Register: `add`, `transfer`, `reconcile`, `import`; write forms: `save`, `cancel`; form bar has no panorama actions)

### Implementation for User Story 3

- [ ] T018 [US3] Implement `ScreenBar.forState` in `app/src/main/kotlin/app/duenorth/budget/ScreenBar.kt` per `contracts/screen-bar.md`, wiring each id to the existing `ShellViewModel` methods
- [ ] T019 [US3] Add `ShellRoute.Settings` and a settings screen tagged `settings-screen` in `app/src/main/kotlin/app/duenorth/budget/ShellScreens.kt` and `ShellViewModel.kt` with rows for budgets, appearance, sync, and payees (no instance switching; that is spec 009)
- [ ] T020 [US3] Drive `ShellChrome` from `ScreenBar.forState` in `app/src/main/kotlin/app/duenorth/budget/DueNorthApp.kt`; remove hold/manage word buttons from `BudgetSection` in `ShellScreens.kt`; remove add/transfer/reconcile/import word buttons from `RegisterScreens.kt`; remove in-body save/cancel duplicates on write forms listed in `contracts/screen-bar.md`

**Checkpoint**: User Stories 1–3 work together; the bar matches the route.

---

## Phase 6: User Story 4 - The title lags the sections (Priority: P2)

**Goal**: On a swipe, the title moves sideways by at least the width of "due", less than the sections, and stays fully on screen. With remove-animations on, the title does not travel and the section still changes.

**Independent Test**: Record a swipe from the budget section to the next. The title moves a visible distance less than the sections, and no letter of "budget" is cut off. Repeat with remove-animations on.

### Tests for User Story 4

- [ ] T021 [P] [US4] Replace the title-shift assertions in `core/design/src/test/kotlin/app/duenorth/budget/design/DesignRulesTest.kt` (adjacent pages differ by `stepPx`; motion off or one section yields 0; fractional pages lerp; absolute shift never exceeds `stepPx`)

### Implementation for User Story 4

- [ ] T022 [US4] Rewrite `PanoramaMotion.titleShiftPx` in `core/design/src/main/kotlin/app/duenorth/budget/design/PanoramaMotion.kt` per `contracts/panorama-title.md` (even page rest 0, odd page rest `-stepPx`, lerp between; motion disabled → 0)
- [ ] T023 [US4] Apply the new shift in `MetroPanorama` in `core/design/src/main/kotlin/app/duenorth/budget/design/components/MetroComponents.kt`: measure `stepPx` as the width of "due"; rest inset at least `stepPx`; scale the title block down from 118 sp only until inset plus block width fits; remove the gutter `coerceIn` clamp; keep clip off; one section disables sideways scroll; peek and snap stay

**Checkpoint**: Title lag is visible and letters stay on screen.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Screenshots, offline confirmation, and quickstart validation

- [ ] T024 [P] Update Roborazzi light and dark captures for the month control and the new bar in `app/src/test/kotlin/app/duenorth/budget/ShellScreenshotTest.kt` and `RegisterScreenshotTest.kt`
- [ ] T025 [P] Compose checks for month tags and collapsed bar actions in `app/src/test/kotlin/app/duenorth/budget/ShellContentTest.kt` (`month-label`, `month-previous`, `app-bar-add`, `app-bar-ellipsis`; no previous/next/hold/manage word buttons on the budget list)
- [ ] T026 Run the validation commands in `specs/008-streamlined-controls/quickstart.md` (`:core:design:testDebugUnitTest`, `:app:testDebugUnitTest`, `:app:verifyRoborazziDebug`, and `:core:budget:test` when month snapshots use `readShell`)
- [ ] T027 Confirm offline bar writes and month figures still come from the local file only (no new network calls) across `ShellViewModel.kt` and `ScreenBar.kt`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories
- **US1 (Phase 3)**: After Foundational — MVP
- **US2 (Phase 4)**: After Foundational — can start beside US1 (different files until `DueNorthApp` wiring)
- **US3 (Phase 5)**: After US2 (needs drawn glyphs and `MetroAppBar`); uses US1's `pinnedMonth` for form saves
- **US4 (Phase 6)**: After Foundational — independent of US1–US3 files except shared `MetroPanorama`
- **Polish (Phase 7)**: After the stories you are shipping

### User Story Dependencies

- **US1 (P1)**: After Foundational. No other story. MVP.
- **US2 (P1)**: After Foundational. Independently testable with temporary bar buttons.
- **US3 (P2)**: After US2 for real icons; after US1 for pinned-month form saves.
- **US4 (P2)**: After Foundational. No dependency on month or bar stories.

### Parallel Opportunities

- T003 and T004 in Foundational
- T006 and T007 before or beside US1 implementation
- T013 and T014 before US2 implementation
- T021 beside US1–US3
- T024 and T025 in Polish

### Parallel Example: User Story 1

```bash
# Tests first:
Task: "JVM tests for open-month selection in app/src/test/.../OpenMonthTest.kt"
Task: "JVM tests for pinned editor months in app/src/test/.../OpenMonthTest.kt"

# Then implementation in order (same ViewModel file is sequential):
Task: "Replace budgetMonth/reviewMonth with openMonth in ShellViewModel.kt"
Task: "Add MonthCache and painted-month transitions in ShellViewModel.kt"
```

### Parallel Example: User Story 2

```bash
Task: "Glyph ink tests in core/design/.../AppGlyphTest.kt"
Task: "Compose bar-tap tests in app/src/test/.../ScreenBarContentTest.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 and Phase 2
2. Complete Phase 3 (US1)
3. Stop and validate the Independent Test for US1
4. Ship the month control before the bar rewrite if needed

### Incremental Delivery

1. Setup + Foundational
2. US1 → month control MVP
3. US2 → drawn icons that respond
4. US3 → per-screen actions and settings door
5. US4 → title lag
6. Polish → screenshots and quickstart

### Parallel Team Strategy

1. Team finishes Setup + Foundational
2. Developer A: US1
3. Developer B: US2, then US3
4. Developer C: US4
5. Polish together

---

## Notes

- No sqlite schema changes. No new Gradle module.
- Settings content beyond the door is spec 009. New budget actions are spec 010.
- Envelope math and sync conflict rules stay as they are (FR-014).
- Commit after each task or logical group.
- Stop at any checkpoint to validate the story independently.
