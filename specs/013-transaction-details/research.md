# Research: Transaction Details Cleanup

All Technical Context items were known from the code; nothing was left as NEEDS CLARIFICATION. The decisions below settle how the spec maps onto what is already there (`TransactionForm` in `app/.../RegisterScreens.kt`, the settings `ListPicker` in `SettingsItems.kt`, `MetroField` in `:core:design`, `DateFormats` in `:core:budget`).

## R1. Where the pickers live: overlays inside the form, not new routes

- **Decision**: The full-screen category list and the date picker are overlay pages drawn by `TransactionForm` itself, over its own content, while the route stays `EditTransaction`. Back closes the overlay through a `BackHandler` registered by the overlay. The form's draft (date, payee, amount, direction, category, note) stays in `remember` state inside the form, so it is never disposed while a picker is open.
- **Rationale**: FR-013 requires that picking a category or date keeps every other unsaved edit. Today the draft lives only in the form's `remember` slots; pushing a new `ShellRoute` would dispose the form and lose it. Hoisting the draft into `ShellViewModel` would fix that but routes every keystroke of payee, amount and note through a `StateFlow`, which is known to make `BasicTextField` cursors jump and costs a shell recomposition per key (Principle I). An overlay keeps the edits where they already are and needs no new route, title rule or back-stack case.
- **Alternatives considered**: New `PickTransactionDate` / `PickTransactionCategory` routes with the draft hoisted into `ShellUiState` (typing lag risk, larger change). A `SaveableStateHolder` keyed by route (works, but every other route would pay for it and it is new machinery for one screen).

## R2. App bar while a picker is open

- **Decision**: Extend the existing `FormSaveHost` pattern with a `FormBarOverride` in `FormSaveBinding.kt`: a form can publish a short list of bar actions (glyph, label, handler) that replace the route's own bar while it is set. The date picker publishes `done` (`AppGlyph.Save`) and `cancel` (`AppGlyph.Cancel`). The full-screen category list publishes an empty bar (WP8.1 single-select list pickers commit on tap and have no bar). `ScreenBar.forState` returns the override when one is bound.
- **Rationale**: FR-007 puts done and cancel on the application bar. The bar is drawn by the shell from the route, outside the form, so the form needs a way to speak to it. `FormSaveHost` already does exactly this for `save`; an override is the same shape and keeps `ScreenBar` the one place bars are built.
- **Alternatives considered**: Drawing done/cancel as body buttons in the picker (breaks FR-007 and Principle II). Adding picker flags to `ShellUiState` so `ScreenBar` can see them (state the view model does not otherwise need).

## R3. Category combo box

- **Decision**: Move the settings `ListPicker` from `SettingsItems.kt` into `:core:design` as `MetroComboBox<T>` and give it `fullScreenThreshold: Int = 5` and `onOpenFull: () -> Unit`. With `choices.size <= fullScreenThreshold` it behaves exactly as `ListPicker` does today (unfolds in place, accent border while open, chosen value in accent). Above the threshold a tap calls `onOpenFull` and the form shows `CategoryListPage`: page title "category", a `LazyColumn` with a group-name heading (caption, secondary) before each group's rows, "no category" first, current choice in accent, `initialFirstVisibleItemIndex` set to the current row. Settings switches to `MetroComboBox` with no other change (its lists never exceed five).
- **Rationale**: FR-010/FR-011 ask for the settings look and the WP8.1 five-item rule. Sharing one component keeps the two in step and gives screenshot tests one thing to pin. `RegisterPage.categories` is already ordered by group sort order then category sort order and already carries `groupName` and `income`.
- **Alternatives considered**: A second combo box in `:app` (two copies). Reusing `CategoryPickerScreen` (it is a route of its own with the payee as title, and it would lose the form's edits per R1).

## R4. Hidden categories

- **Decision**: Add `hidden: Boolean` to `CategoryChoice` (from `c.hidden OR g.hidden` in `RegisterBook.categories()`). The new list and the combo box count leave hidden categories out unless one is the transaction's current category; that one is shown with a "(hidden)" suffix in secondary text. The register quick picker, split form and transfer form ignore the new field and keep today's behavior (out of scope).
- **Rationale**: The spec's edge case keeps a hidden category selectable when it is already set, and its assumption leaves other hidden categories out. Today `categories()` returns hidden ones too, so the field is needed to tell them apart. Leaving the other callers alone keeps the change to this screen.
- **Alternatives considered**: Filtering in SQL (would also change the quick picker and split form, which are out of scope).

## R5. Date picker

- **Decision**: Add `MetroDatePicker` to `:core:design`: three snapping, looping columns built from `LazyColumn` + `rememberSnapFlingBehavior`, each with a virtual item count of `Int.MAX_VALUE / 2` mapped onto its real values with modulo (the standard Compose looping-list technique), the centre row in the foreground color and the others secondary, each row 48 dp+ tall. The column order, the day count per month (leap years included), the year range and the clamp when the month changes come from a pure `DatePick` object in `:core:budget` so they are JVM-tested. Year range is 1900–2100, widened to include the transaction's own year. Labels: month as the short lowercase name ("oct") over its number, day as the number over the short weekday, year as four digits, as WP8.1 draws them.
- **Rationale**: FR-005/FR-006 describe WP8.1's DatePickerFlyout. There is no wheel picker in the app and Compose foundation has none; a `LazyColumn` with snapping holds the frame rate (Principle I) and needs no new library. Putting the date arithmetic in `:core:budget` matches where `DateFormats` already lives.
- **Alternatives considered**: Android's `DatePickerDialog` (a Material widget; Principle II and Technical Constraints forbid it). A third-party wheel library (new dependency for one control; none follow Metro). Non-looping columns (FR-005 asks for looping).

## R6. Date shown in the box

- **Decision**: Add `DateFormats.withWeekday(date: Int, pattern: String): String` returning the short English weekday, a space, and the existing `DateFormats.format` output ("Thu 10/01/2026"). The form keeps the date as the Actual integer (`yyyyMMdd`) instead of a text string; `RegisterEntry.transaction` gains an overload taking `date: Int`, and the `dateText` overload stays for the transfer and import callers.
- **Rationale**: FR-004 uses the person's format, and `DateFormats.format` is what the register already uses, so the box matches the register's day headings (zero padded; the spec's "10/1/2026" illustration reads as "10/01/2026" in the app's format). Holding an `Int` makes FR-008 structural: there is no text to mistype, and `RegisterCopy.ENTER_DATE` can no longer fire from this form.
- **Alternatives considered**: Locale-aware `DateTimeFormatter` (would disagree with the five Actual formats the person picked in settings).

## R7. Fields sized to their content

- **Decision**: Add `fill: Boolean = true` to `MetroField` (today it always calls `fillMaxWidth()`). Sized fields pass `fill = false` and a width from a new `rememberFieldWidth(sample: String, style: TextStyle, extra: Dp)` helper that measures the sample with `rememberTextMeasurer` and adds the field's 8 dp padding on each side plus `extra` (the chevron for the date box). The date sample is the widest weekday ("Wed") plus the chosen pattern filled with "0". The amount sample is `MoneyFormat.format(9_999_999 * 10^decimals)` in the budget's number format with "0" for every digit. Both are wrapped in `widthIn(max = content width)`.
- **Rationale**: Measuring real text in the real style makes the boxes grow with the system font size (FR-019) and with the budget's decimals and separators (FR-017) without hand-tuned dp numbers. "0" is the widest digit in Selawik's tabular figures.
- **Alternatives considered**: Fixed dp widths (break at large font sizes). `IntrinsicSize` on the text field (sizes to the typed value, so the box would jump as the person types).

## R8. Amount line and the expense/income toggle

- **Decision**: Add `MetroSegmentedToggle` to `:core:design`: two adjacent segments sharing one border stroke, each `heightIn(min = 48.dp)`, text in `typography.control`, chosen segment filled with `Metro.accent.fill` and its text in the accent's contrast color, the other outlined in the foreground color with foreground text, `metroPress` tilt on each. The amount row is a `FlowRow` (foundation `ExperimentalLayoutApi`, already in the Compose BOM) holding the sized amount field and the toggle with a 12 dp gap, so at large font sizes the toggle wraps under the amount box instead of shrinking.
- **Rationale**: The owner chose the segmented toggle over radio buttons and a toggle switch (spec, 2026-10-16). `FlowRow` gives the wrap rule in the spec's assumptions with no measuring code. At default font on a 412 dp-wide Pixel 6a the content width is 388 dp; the amount box for two decimals measures about 130 dp and the toggle about 170 dp, so both fit with room to spare (SC-004).
- **Alternatives considered**: Keeping `DirectionChoice` subheader text (too wide to share the line). A custom layout that shrinks the toggle (would drop below 48 dp).

## R9. Multiline note

- **Decision**: Add `minLines` and `maxLines` to `MetroField` and pass `minLines = 2, maxLines = 6` for the note, with `KeyboardOptions(capitalization = Sentences, imeAction = ImeAction.Default)` so return inserts a line break. Above six lines the `BasicTextField` scrolls internally. The form's scroll column gets `imePadding()`, and the activity sets `android:windowSoftInputMode="adjustResize"` so Compose sees the keyboard insets; `BasicTextField` already brings the cursor into view inside a scrolling parent once insets are applied. `registerDetail()` turns each run of whitespace containing a line break into one space before joining.
- **Rationale**: FR-018a and the note edge case. The note is stored as typed (`RegisterEntry.transaction` only trims ends), so line breaks already round-trip through Actual's `notes` column and sync; only the register's one-line summary needs to flatten them.
- **Alternatives considered**: A separate full-screen note editor (an extra tap for a common field).

## R10. Spacing

- **Decision**: Two new constants in `MetroDimens`: `FieldLabelGap = 6.dp` (a quarter of the 24 dp grid) between a label and its field, and `FieldGroupGap = 24.dp` (one grid step) between one field group and the next label. The page title sits one `Grid` above the first label. Every field and label is a child of the gutter-padded column, so all left edges share `MetroDimens.Gutter`. A small `LabeledField` composable (label + gap + field + optional accent error line) builds each group, so the steps cannot drift.
- **Rationale**: FR-020 asks for exactly two steps on the grid. The 1:4 ratio matches WP8.1's field header margin against its control margin. With five groups the form measures about 580 dp at default font, inside a Pixel 6a's ~730 dp of content height above the app bar (SC-001).
- **Alternatives considered**: 12 dp between groups (labels crowd the field above, the "same distance apart" problem the spec calls out).

## R11. Page title area

- **Decision**: The small uppercase line follows the app's existing rule in `SectionTitle.of`, which names the home section showing (ACCOUNTS when opened from a register, INBOX when opened from the inbox). Directly under it, and above the page title, is the account's name as the person wrote it ("Visa"), in the same small page-title-line size and the secondary color. The big title is always "transaction". Tag `transaction-account`. Chosen by the owner on 2026-10-16.
- **Rationale**: Constitution 1.2.0 (Principle II) defines the small line as the home section, so it stays ACCOUNTS. The owner also wants to see which account the transaction belongs to, which matters when the page is opened from the inbox, so the name gets its own line between the section line and the title. `SectionTitled` draws the section line outside the form, and the form's content starts right under it, so `TransactionForm` draws the account line as its first row from `page.account.name`, with the title after it. `SectionTitle.of` needs no change.
- **Alternatives considered**: The account name in place of ACCOUNTS ("VISA", the spec's first illustration; contradicts the constitution). The name under the page title (the owner placed it above).

## R12. Commands on the application bar

- **Decision**: `ScreenBar.specsFor` takes a new `TransactionMenu` value (`NEW`, `ORDINARY`, `TRANSFER`) computed in `forState` from `EditTransaction.transactionId` and the register row's `transferId`. `EditTransaction` keeps `saveCancel()` and adds menu items `split-transaction`, `schedule-transaction`, `delete-transaction` (ordinary), `delete-transaction` only (transfer), or none (new). `ScreenBarHandlers` gains `onSplitTransaction`, `onScheduleTransaction`, `onDeleteTransaction`, wired in `DueNorthApp` to the existing `openSplit`, `createScheduleFromTransaction`, `deleteTransaction` with the route's transaction id. The `MetroButtonGrid` and the in-body save fallback leave `TransactionForm`.
- **Rationale**: FR-023–FR-026. The view model functions are reused unchanged, so behavior matches the old buttons. `saveCancel()` already binds save to `FormSaveHost` and cancel to back (FR-025).
- **Alternatives considered**: Primary bar buttons for delete (WP8.1 keeps destructive commands in the menu; the bar has room for four primaries but save and cancel are the page's main commands).

## R13. Validation messages under their field

- **Decision**: A small mapping in `:app` (`TransactionField.of(reason: String)`) sends `ENTER_PAYEE` to payee and `ENTER_AMOUNT` / `AMOUNT_ZERO` to amount; `LabeledField` draws the message in `Metro.accent.text` under that field. A save error from the view model (`writeError`, for example a reconcile or storage failure) shows under the last field. `RegisterEntry` is unchanged.
- **Rationale**: FR-022 with no change to core validation. `ENTER_DATE` cannot occur any more (R6).
- **Alternatives considered**: Adding a field enum to `EntryResult.Rejected` (touches every caller of `RegisterEntry`, out of scope).

## R14. Screenshot and fluidity coverage

- **Decision**: Roborazzi light and dark for: the form (existing transaction, new transaction, transfer), the combo box unfolded in place (a budget with four categories), the full-screen category list, the date picker, the form with a validation error, and the form at 2× font scale (Robolectric `RuntimeEnvironment.setFontScale`). Frame rate for the picker columns and a 200-category list is checked on a Pixel 6a-class phone as a quickstart scenario, the way earlier specs checked their lists; the repo has no automated frame harness to extend.
- **Rationale**: FR-027, FR-028, Principle V. Existing `RegisterScreenshotTest` / `RegisterContentTest` already compose `TransactionForm` and are the natural home.
