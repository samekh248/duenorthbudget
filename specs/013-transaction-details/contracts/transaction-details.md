# Contract: Transaction Details UI

The transaction details screen is a UI contract: what it shows, its app bar, the shared Metro components it introduces, and the test tags tests rely on. `amount-field` keeps its name.

## Screen (`TransactionForm`, route `EditTransaction`)

Title area, top to bottom: the section line from `SectionTitle.of` (unchanged rule, research R11); the account name as stored (subheader size, secondary, tag `transaction-account`); the page title `transaction` (new and existing alike). The first field group starts 24 dp below the title. Root tag `transaction-form`.

Body, top to bottom. Each group is `LabeledField`: label (caption, secondary, lowercase) → 6 dp → control → optional error line (body, accent). Groups are 24 dp apart.

| Label | Control | Width | Tags |
|---|---|---|---|
| date | `MetroComboBox`-styled box showing `DateFormats.withWeekday`, chevron; tap opens the date picker; never focusable for text | widest sample (R7) | `transaction-date`, `transaction-date-value` |
| payee | `MetroField`, single line | content width | `transaction-payee` |
| amount | `MetroField` (decimal keyboard) + `MetroSegmentedToggle` in a `FlowRow` | amount: 7 digits + decimals (R7); toggle: intrinsic | `amount-field`, `transaction-direction`, `transaction-direction-expense`, `transaction-direction-income` |
| category | `MetroComboBox` (omitted for split parents and same-budget transfers) | content width | `transaction-category`, `transaction-category-value`, `transaction-category-<id>`, `transaction-category-none` |
| note | `MetroField`, `minLines = 2`, `maxLines = 6`, return inserts a line break | content width | `transaction-note` |

Error lines: `transaction-error-<field>` (`date`, `payee`, `amount`, `category`, `note`). A view-model `writeError` uses `transaction-error-note`.

No `MetroButton` or `MetroButtonGrid` is composed in the body (FR-003).

## Overlays

### Category list (`transaction-category-list`)

Shown when the combo box is tapped with more than five choices. Covers the form's content area.

- Page title `category`.
- `no category` first (`transaction-category-none`), then for each group a heading (caption, secondary, `transaction-category-group-<index>`) and its rows (`transaction-category-<id>`), each ≥ 48 dp, subheader size.
- Current row in `Metro.accent.text`; the list opens scrolled so it is visible.
- A hidden current category shows `(hidden)` after its name in secondary text.
- Tap a row: overlay closes, combo box shows it. Back: overlay closes, unchanged.
- App bar override: empty.

### Date picker (`transaction-date-picker`)

- Page title `choose date`.
- Three `MetroPickerColumn`s in `DatePick.columns(pattern)` order: `date-column-month`, `date-column-day`, `date-column-year` (rows tagged `<column>-<position>`). Each loops, snaps, and highlights its centre row; rows ≥ 48 dp.
- App bar override: `done` (Save glyph, id `picker-done`) applies; `cancel` (Cancel glyph, id `picker-cancel`) and back discard.
- No keyboard is shown at any point (FR-008).

## App bar (`ScreenBar.specsFor`, route `EditTransaction`)

| Transaction | Primary | Menu |
|---|---|---|
| new (`transactionId == null`) | save, cancel | — |
| existing, not a transfer | save, cancel | split (`split-transaction`), schedule (`schedule-transaction`), delete (`delete-transaction`) |
| existing transfer | save, cancel | delete (`delete-transaction`) |

While an overlay is open its override replaces the whole bar. Handlers call the existing `openSplit`, `createScheduleFromTransaction`, `deleteTransaction` with the route's transaction id.

## Shared components (`:core:design`)

```kotlin
@Composable
fun <T> MetroComboBox(
    choices: List<T>,
    selected: T,
    label: (T) -> String,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
    tag: String,
    fullScreenThreshold: Int = Int.MAX_VALUE,
    onOpenFull: () -> Unit = {},
)

@Composable
fun MetroSegmentedToggle(
    first: String,
    second: String,
    firstSelected: Boolean,
    onSelect: (first: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    tag: String,
)

@Composable
fun MetroPickerColumn(          // one looping, snapping strip; the app composes three into the date picker
    count: Int,
    position: Int,
    label: (Int) -> Pair<String, String>,   // main text, smaller line under it
    onPick: (Int) -> Unit,                  // fires when a fling or tap settles
    tag: String,
    modifier: Modifier = Modifier,
    rowHeight: Dp = 72.dp,
)

@Composable
fun MetroField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    conceal: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    fill: Boolean = true,       // new: false lets the caller size the box
    lines: IntRange? = null,    // new: 2..6 for the note; null keeps today's single-line rule
)
```

`MetroComboBox` with the default threshold is today's settings `ListPicker`; settings moves to it with unchanged tags (`settings-date`, `settings-date-value`, …).

`MetroDimens` gains `FieldLabelGap = 6.dp` and `FieldGroupGap = 24.dp`.
