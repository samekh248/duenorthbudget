# Implementation Plan: Transaction Details Cleanup

**Branch**: `013-transaction-details` | **Date**: 2026-10-16 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/013-transaction-details/spec.md`

## Summary

`TransactionForm` stops being a stack of unlabelled full-width boxes, a list of every category, and four body buttons, and becomes a labelled Windows Phone 8.1 form: ACCOUNTS, then the account's name, then the page title "transaction". Three shared Metro controls are added to `:core:design`: `MetroComboBox` (the settings `ListPicker`, moved and given the WP8.1 five-item rule), `MetroDatePicker` (three looping, snapping columns), and `MetroSegmentedToggle` (the owner's compact expense/income toggle). The category box unfolds in place for five or fewer choices and otherwise opens a grouped full-screen list; the date box shows the settings format with the weekday and opens the picker, so the date is never typed. Both pickers are overlays inside the form, so no unsaved edit is lost, and they swap the app bar through a small extension of the existing `FormSaveHost`. Date and amount boxes are measured from sample text so they are only as wide as their longest value and grow with the font; the amount and toggle share a `FlowRow`. The note becomes a 2–6 line box. Split, schedule and delete move into the `EditTransaction` app bar menu. Spacing uses two new `MetroDimens` steps (6 dp label gap, 24 dp group gap). Nothing stored or synced changes.

```text
            :core:design                              :app
 ┌──────────────────────────────┐     ┌────────────────────────────────────┐
 │ MetroComboBox  (from         │◀────│ TransactionForm                    │
 │   settings ListPicker)       │     │  ├ LabeledField × 5                │
 │ MetroDatePicker (new)        │◀────│  ├ overlay: CategoryListPage       │
 │ MetroSegmentedToggle (new)   │◀────│  └ overlay: date picker page       │
 │ MetroField (+fill, +lines)   │◀────│                                    │
 │ MetroDimens (+2 gaps)        │     │ FormSaveBinding: + bar override ──┐│
 └──────────────────────────────┘     │ ScreenBar: EditTransaction menu ◀─┘│
            :core:budget              │ SettingsItems: ListPicker → combo  │
 ┌──────────────────────────────┐     └────────────────────────────────────┘
 │ DatePick (new, pure)         │
 │ DateFormats.withWeekday      │
 │ CategoryChoice.hidden        │
 │ RegisterEntry.transaction    │
 │   (date: Int overload)       │
 └──────────────────────────────┘
```

## Technical Context

**Language/Version**: Kotlin 2.2, Java 17 bytecode, Android minSdk 26, compileSdk 36

**Primary Dependencies**: Jetpack Compose BOM (foundation, animation, UI; no Material). `LazyColumn` + `rememberSnapFlingBehavior` for picker columns, `FlowRow` for the amount line, `rememberTextMeasurer` for field widths, `BackHandler` from activity-compose (already used). Metro components in `:core:design`. No new library.

**Storage**: None. `CategoryChoice` gains an in-memory `hidden` flag read from the existing `categories.hidden` and `category_groups.hidden` columns. Notes with line breaks already round-trip through Actual's `notes` column.

**Testing**: JVM tests in `:core:budget` for `DatePick` (column order for all five formats, days in month, leap years, clamping, year range) and `DateFormats.withWeekday`; `RegisterBookTest` for `hidden`. `:core:design` tests for `MetroComboBox` threshold behavior and `MetroSegmentedToggle` selection. Robolectric Compose content tests in `RegisterContentTest` for: labels present, no body buttons, combo box in place vs. full-screen, edits kept across both overlays, back closes overlays, done/cancel, no IME on date tap, error placement, hidden category, bar menu per transaction kind (`ScreenBarTest`). Roborazzi light and dark per [quickstart.md](quickstart.md#screenshots).

**Target Platform**: Android phone (minSdk 26)

**Project Type**: Mobile app

**Performance Goals**: Combo box, list and picker open with feedback under 100 ms (they are local state flips, no I/O). Picker columns and the category list are lazy and hold the phone's frame rate. The form no longer composes every category row, so opening it gets cheaper on large budgets.

**Constraints**: Every command the old body buttons offered stays reachable (FR-026). No change to how a transaction is validated or saved beyond the date being an `Int`. The transfer form, split form and register quick picker keep their layouts (spec scope). Settings combo boxes look and behave exactly as today after the move.

**Scale/Scope**: One screen reworked; three new shared components and two changed ones; one bar route extended; about sixteen new screenshots (eight states × light/dark).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Result |
|---|---|---|
| I. Fluid First | Pickers are lazy columns with snapping; the full list is a `LazyColumn`. Opening either is a local state change, no I/O. The form drops the eager list of every category. Typing stays in local state (no `StateFlow` per key). Reduced motion: overlays appear without transition. Frame rate checked on a Pixel 6a-class phone (quickstart 13). | Pass |
| II. Metro 2 | WP8.1 patterns throughout: section line over lowercase page title, persistent field headers, combo box with the five-item rule, DatePickerFlyout-style looping columns with done/cancel on the app bar, commands on the bar and destructive ones in the menu. No Material widgets (`DatePickerDialog` rejected). Flat fills, no rounded corners. 12 dp gutter, 24 dp grid steps, 48 dp targets. The segmented toggle is not a stock WP8.1 control; it is two Metro toggle buttons joined, chosen by the owner and recorded in the spec. | Pass |
| III. The Same Budget as Actual | Same fields, same validation, same writes. Dates still stored as Actual integers; notes stored as typed. Hidden is read from Actual's own columns. | Pass |
| IV. One Budget at a Time | Not touched. | Pass |
| V. Test What the Person Can See | Light and dark screenshots for the form, both overlays, the in-place combo box, an error and a large font. Content tests for every acceptance scenario that a screenshot cannot show (edits kept, back, bar menus). | Pass |
| VI. Phone First, Nothing Extra | Phone layout only. No new module; components go in the existing `:core:design`. | Pass |

Post-design re-check: the same gates hold. The page's small title line follows constitution 1.2.0 (the section, ACCOUNTS), and the account name sits between it and the page title as the owner chose (research R11). No complexity exception needed.

## Project Structure

### Documentation (this feature)

```text
specs/013-transaction-details/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── transaction-details.md
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks, not this command
```

### Source Code (repository root)

```text
core/budget/src/main/kotlin/app/duenorth/budget/core/
  Models.kt                 # DatePick, DateColumn; DateFormats.withWeekday, widestSample
  Register.kt               # CategoryChoice.hidden; categories() reads hidden; RegisterEntry.transaction(date: Int) overload
core/budget/src/test/kotlin/app/duenorth/budget/core/
  DatePickTest.kt           # new
  RegisterBookTest.kt       # hidden flag
core/design/src/main/kotlin/app/duenorth/budget/design/
  components/MetroComponents.kt   # MetroField fill + lines
  components/MetroComboBox.kt     # new (moved from SettingsItems ListPicker)
  components/MetroDatePicker.kt   # new
  components/MetroSegmentedToggle.kt # new
  theme/MetroTheme.kt             # MetroDimens.FieldLabelGap, FieldGroupGap
core/design/src/test/kotlin/app/duenorth/budget/design/
  MetroComboBoxTest.kt, MetroSegmentedToggleTest.kt
app/src/main/
  AndroidManifest.xml       # windowSoftInputMode="adjustResize"
app/src/main/kotlin/app/duenorth/budget/
  RegisterScreens.kt        # TransactionForm rebuilt; LabeledField; CategoryListPage; date picker page;
                            # registerDetail flattens line breaks; DirectionChoice removed if unused
  FormSaveBinding.kt        # bar override alongside save
  ScreenBar.kt              # TransactionMenu; split/schedule/delete menu items; handlers; honours override
  DueNorthApp.kt            # wires the three new handlers; passes date format to TransactionForm
  SettingsItems.kt          # ListPicker → MetroComboBox
app/src/test/kotlin/app/duenorth/budget/
  RegisterContentTest.kt, RegisterScreenshotTest.kt, ScreenBarTest.kt, SettingsContentTest.kt (unchanged tags)
docs/design/
  metro-mockups.svg         # transaction details, category list, date picker frames, light and dark
```

**Structure Decision**: No new module. Reusable controls go in `:core:design` beside the other Metro components so the transfer and split forms can adopt them in a later spec; date arithmetic goes in `:core:budget` beside `DateFormats` so it is tested on the JVM. Screen-specific pieces (`LabeledField`, the two overlay pages, `CategoryOptions`) stay in `RegisterScreens.kt`.

## Complexity Tracking

No constitution violations.
