# Implementation Plan: Settings Pivot

**Branch**: `012-settings-pivot` | **Date**: 2026-10-14 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/012-settings-pivot/spec.md`

## Summary

`SettingsScreen` stops being one 583-line scrolling column and becomes a `MetroPivot` titled "SETTINGS" with five items: budget, server, format, appearance, advanced (format and advanced only while a budget is open). Each item is its own small composable in a new `SettingsItems.kt`; the existing section composables move into them almost unchanged. `ShellUiState` gains `settingsPage`, which the pivot reports on settle and reads on return, so a sub-page always lands back on the item it came from, and `ScreenBar` gives each item its own bar (new, add, export). Inline forms leave settings: "new budget" opens the existing `CreateBudgetScreen` (with a currency, replacing today's hard-coded USD), the encryption password gets one `EncryptionPasswordScreen` with three modes, and removing a server gets a confirm page. "add" on the server item runs the spec 011 onboarding server path with a new `origin = SETTINGS` on its progress, so the same address, check and sign-in pages, messages and resume behavior apply, and finishing returns to the server item. The orphaned `Appearance` route is removed. Settings frames are added to `docs/design/metro-mockups.svg` in light and dark.

## Technical Context

**Language/Version**: Kotlin 2.2, Java 17 bytecode, Android minSdk 26, compileSdk 36

**Primary Dependencies**: Jetpack Compose BOM (foundation, animation, UI; no Material), Metro components in `:core:design` (`MetroPivot`, `MetroButtonGrid`, app bar), `BudgetLibrary` / `SyncCoordinator` / `ServerProbe` in `:core:budget`. No new library.

**Storage**: No new files. `OnboardingProgress` in `onboarding.json` gains an `origin` field (default `FIRST_RUN`, so existing files read unchanged). The selected settings item lives only in view-model state; it is not persisted (spec assumption: settings opens on budget each time).

**Testing**: Robolectric Compose content tests for the pivot (headers present per budget state, header tap, loop), each item's contents, app bar per item, return-to-item after every sub-page, and the settings-origin add-server path (back from address returns to server item; success returns to server item; no budget open goes to the file list). JVM test that `OnboardingProgress` without `origin` reads as `FIRST_RUN` and that a settings-origin progress survives `restoreOnboarding` on a phone with budgets. Roborazzi light and dark for each of the five items, the encryption page in its three modes, and the remove-server confirm page. Existing `SettingsContentTest` and `SettingsScreenshotTest` are rewritten around the items.

**Target Platform**: Android phone (minSdk 26)

**Project Type**: Mobile app

**Performance Goals**: Pivot swipes and header taps at the phone's frame rate, feedback under 100 ms. Items are composed lazily by the pager (`beyondViewportPageCount = 1`), so opening settings composes two items, not five. Reachability probe and server file refresh stay on `Dispatchers.IO` as today.

**Constraints**: Every setting reachable today stays reachable (FR-016). No text field on any item until an action opens a page (FR-013). Server checks and sign-in never block the pivot. Reduced motion makes pivot changes immediate (already handled by `MetroPivot`).

**Scale/Scope**: One screen split into five items; three new routes (`EncryptionPassword`, `ConfirmRemoveInstance`, and settings-origin reuse of the three onboarding routes); one route removed (`Appearance`); about ten new mockup frames.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Result |
|---|---|---|
| I. Fluid First | Pivot reuses `MetroPivot`, which already holds frame rate on home. Items are lightweight columns; only the visible and adjacent items compose. All server and file work stays off the main thread; no full-screen spinner is added. | Pass |
| II. Metro 2 | Constitution: "Secondary screens use a pivot where they need tabs." Small uppercase title, extra-large semilight headers, looping full-width items, app bar on every item with round outlined buttons and ellipsis, full pages instead of inline forms. Frames added to `metro-mockups.svg`. | Pass |
| III. The Same Budget as Actual | Format, experimental, learning and encryption settings still write to the budget file through the same calls. Budget method stays read-only. No setting is added that the file cannot store. | Pass |
| IV. One Budget at a Time | Switching budgets and making a server active keep their confirm pages naming what opens and what is left. Adding a server tells the person before sign-in that it becomes the active server. | Pass |
| V. Test What the Person Can See | Screenshot checks in light and dark for each item and new page; content tests for navigation and return-to-item. | Pass |
| VI. Phone First, Nothing Extra | Phone layout only. No new Gradle module. No server administration added. | Pass |

Post-design re-check: the same gates hold. The add-server path reuses onboarding's pages and view-model functions with an origin flag instead of a second copy, and `CreateBudgetScreen` is reused for new budget. No complexity exception needed.

## Project Structure

### Documentation (this feature)

```text
specs/012-settings-pivot/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── settings-pivot.md
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks, not this command
```

### Source Code (repository root)

```text
core/design/src/main/kotlin/app/duenorth/budget/design/components/
  MetroComponents.kt        # MetroPivot: onSelected(index) callback when a swipe or tap settles
core/budget/src/main/kotlin/app/duenorth/budget/core/
  Onboarding.kt             # OnboardingOrigin { FIRST_RUN, SETTINGS } on OnboardingProgress
core/budget/src/test/kotlin/app/duenorth/budget/core/
  OnboardingProgressTest.kt # origin default and round trip
app/src/main/kotlin/app/duenorth/budget/
  SettingsScreens.kt        # SettingsScreen = MetroPivot over SettingsPage; confirm pages; new EncryptionPasswordScreen
  SettingsItems.kt          # new: BudgetItem, ServerItem, FormatItem, AppearanceItem, AdvancedItem
  ShellViewModel.kt         # settingsPage state, selectSettingsPage, routes EncryptionPassword / ConfirmRemoveInstance,
                            # back() returns to Settings for settings sub-pages, Appearance route removed
  ShellViewModelOnboarding.kt # origin-aware entry (onboardingAddServer), back, finish, restore
  ScreenBar.kt              # Settings bar per SettingsPage; sub-page bars
  DueNorthApp.kt            # route wiring
  ShellScreens.kt           # AppearanceScreen removed (moved into AppearanceItem)
app/src/test/kotlin/app/duenorth/budget/
  SettingsContentTest.kt, SettingsScreenshotTest.kt, OnboardingFlowTest.kt (settings origin), ScreenBarTest.kt
docs/design/
  metro-mockups.svg         # settings pivot rows, light and dark
  README.md                 # mentions the settings frames
```

**Structure Decision**: No new module. The pivot and items live in `:app` next to the current settings code; `SettingsItems.kt` keeps each file under the size of today's `SettingsScreens.kt`. The only core changes are an origin field on onboarding progress and a selection callback on `MetroPivot`.

## Complexity Tracking

No constitution violations.
