package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.AccountChoice
import app.duenorth.budget.core.CategoryChoice
import app.duenorth.budget.core.LedgerDates
import app.duenorth.budget.core.LedgerFilter
import app.duenorth.budget.core.LedgerFilterCopy
import app.duenorth.budget.core.LedgerFlag
import app.duenorth.budget.core.LedgerStatus
import app.duenorth.budget.core.RegisterCopy
import app.duenorth.budget.core.RegisterSearch
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.AppGlyphIcon
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.components.MetroSegmentedToggle
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens

/**
 * Under a filtered ledger's balance: what the filters keep on one line, how many rows they leave under it, and
 * edit, which brings the sheet back. A stripe and a faint tint of the accent set it apart from the rows.
 */
@Composable
internal fun LedgerFilterStrip(
    summary: String,
    count: Int,
    shown: Int,
    total: Int,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = Metro.accent.text
    val tint = Metro.accent.fill.copy(alpha = if (Metro.colors.isDark) 0.16f else 0.08f)
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = MetroDimens.Gutter)
            .background(tint)
            .drawBehind { drawRect(accent, Offset.Zero, Size(3.dp.toPx(), size.height)) }
            .metroPress(onEdit)
            .padding(start = 3.dp + 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)
            .testTag("ledger-filter-strip"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppGlyphIcon(AppGlyph.Filter, color = accent)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            MetroText(summary, Metro.typography.body, Modifier.testTag("ledger-filter-summary"), maxLines = 2)
            MetroText(
                LedgerFilterCopy.shown(count, shown, total),
                Metro.typography.caption,
                Modifier.testTag("ledger-filter-count"),
                color = Metro.colors.secondary,
            )
        }
        MetroText(LedgerFilterCopy.EDIT, Metro.typography.body, color = accent)
    }
}

/**
 * Every filter a ledger offers, on one panel in the app bar's colour that runs to both screen edges and scrolls.
 * Each change applies to the rows at once; the app bar's done closes the panel and its clear drops every filter.
 * [accounts] is offered only on a ledger that spans accounts; a register passes null.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LedgerFilterSheet(
    filter: LedgerFilter,
    onFilter: (LedgerFilter) -> Unit,
    categories: List<CategoryChoice>,
    accounts: List<AccountChoice>?,
    shown: Int,
    total: Int,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var addingCategory by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .bleedGutter(true)
            .background(Metro.colors.chrome)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MetroDimens.Gutter)
            .padding(top = MetroDimens.Gutter, bottom = MetroDimens.Grid)
            .testTag("ledger-filter-sheet"),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            MetroText(LedgerFilterCopy.TITLE, Metro.typography.subheader, Modifier.weight(1f))
            MetroText(
                RegisterSearch.count(shown, total),
                Metro.typography.body,
                Modifier.padding(end = MetroDimens.Grid).testTag("ledger-filter-sheet-count"),
                color = Metro.colors.secondary,
            )
            if (filter.active) {
                MetroText(
                    LedgerFilterCopy.CLEAR_ALL,
                    Metro.typography.body,
                    Modifier.metroPress(onClear).testTag("ledger-filter-clear"),
                    color = Metro.accent.text,
                )
            }
        }

        SheetLabel(LedgerFilterCopy.CATEGORY)
        val names = categories.associate { it.id to it.name }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            filter.categoryIds
                .sortedBy { names[it] ?: RegisterCopy.NO_CATEGORY }
                .forEach { id ->
                    FilterTag(names[id] ?: RegisterCopy.NO_CATEGORY, "ledger-filter-category-$id") {
                        onFilter(filter.withCategory(id, false))
                    }
                }
            AddTag(LedgerFilterCopy.ADD, "ledger-filter-category-add") { addingCategory = !addingCategory }
        }
        if (addingCategory) {
            CategoryAdder(categories, filter.categoryIds) { id ->
                onFilter(filter.withCategory(id, true))
                addingCategory = false
            }
        }

        if (accounts != null) {
            SheetLabel(LedgerFilterCopy.ACCOUNT)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                accounts.filter { !it.closed || it.id in filter.accountIds }.forEach { account ->
                    val on = account.id in filter.accountIds
                    ToggleTag(account.name, on, "ledger-filter-account-${account.id}") {
                        onFilter(filter.copy(accountIds = if (on) filter.accountIds - account.id else filter.accountIds + account.id))
                    }
                }
            }
        }

        SheetLabel(LedgerFilterCopy.DATES)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            LedgerDates.entries.forEach { dates ->
                val on = dates == filter.dates
                MetroText(
                    dates.label,
                    Metro.typography.groupHeader,
                    Modifier
                        .heightIn(min = MetroDimens.TouchTarget)
                        .metroPress { onFilter(filter.copy(dates = dates)) }
                        .semantics { selected = on }
                        .padding(end = MetroDimens.Grid, top = 8.dp, bottom = 8.dp)
                        .testTag("ledger-filter-dates-${dates.name.lowercase()}"),
                    color = if (on) Metro.accent.text else Metro.colors.foreground,
                    maxLines = 1,
                )
            }
        }

        SheetLabel(LedgerFilterCopy.AMOUNT)
        val money = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MetroField(
                filter.minAmount,
                { onFilter(filter.copy(minAmount = it)) },
                LedgerFilterCopy.FROM,
                Modifier.weight(1f).testTag("ledger-filter-min"),
                keyboardOptions = money,
            )
            MetroText(
                LedgerFilterCopy.TO,
                Metro.typography.body,
                Modifier.padding(horizontal = MetroDimens.Gutter),
                color = Metro.colors.secondary,
            )
            MetroField(
                filter.maxAmount,
                { onFilter(filter.copy(maxAmount = it)) },
                LedgerFilterCopy.NO_LIMIT,
                Modifier.weight(1f).testTag("ledger-filter-max"),
                keyboardOptions = money,
            )
        }

        SheetLabel(LedgerFilterCopy.STATUS)
        MetroSegmentedToggle(
            LedgerStatus.entries.map { it.label },
            filter.status.ordinal,
            { onFilter(filter.copy(status = LedgerStatus.entries[it])) },
            "ledger-filter-status",
            Modifier.fillMaxWidth(),
        )

        SheetLabel(LedgerFilterCopy.TEXT)
        MetroField(
            filter.text,
            { onFilter(filter.copy(text = it)) },
            LedgerFilterCopy.TEXT_HINT,
            Modifier.testTag("ledger-filter-text"),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
        )

        SheetLabel(LedgerFilterCopy.FLAGS)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LedgerFlag.entries.forEach { flag ->
                val on = flag in filter.flags
                ToggleTag(flag.label, on, "ledger-filter-flag-${flag.name.lowercase()}") { onFilter(filter.withFlag(flag, !on)) }
            }
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    MetroText(
        text,
        Metro.typography.caption,
        Modifier.padding(top = MetroDimens.Gutter + 6.dp, bottom = MetroDimens.FieldLabelGap),
        color = Metro.colors.secondary,
    )
}

/** A filter that is on: the accent filled, its name and a ×; a tap takes it off. */
@Composable
private fun FilterTag(
    label: String,
    tag: String,
    onRemove: () -> Unit,
) {
    Row(
        Modifier
            .heightIn(min = 36.dp)
            .background(Metro.accent.fill)
            .metroPress(onRemove)
            .semantics { contentDescription = "$label, ${RegisterCopy.CLEAR}" }
            .padding(start = 10.dp, end = 8.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MetroText(label, Metro.typography.body, color = Metro.accent.onFill, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Mark(cross = true, Metro.accent.onFill)
    }
}

/** One of a fixed set that can each be on or off: filled with the accent when on, outlined when off. */
@Composable
private fun ToggleTag(
    label: String,
    on: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    val box =
        if (on) {
            Modifier.background(Metro.accent.fill)
        } else {
            Modifier.border(2.dp, Metro.colors.secondary)
        }
    MetroText(
        label,
        Metro.typography.body,
        Modifier
            .heightIn(min = 36.dp)
            .then(box)
            .metroPress(onClick)
            .semantics { selected = on }
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(tag),
        color = if (on) Metro.accent.onFill else Metro.colors.foreground,
        maxLines = 1,
    )
}

/** The dashed outline that adds another of a kind, such as a category. */
@Composable
private fun AddTag(
    label: String,
    tag: String,
    onClick: () -> Unit,
) {
    val line = Metro.colors.secondary
    Row(
        Modifier
            .heightIn(min = 36.dp)
            .drawBehind {
                val w = 2.dp.toPx()
                val dash =
                    androidx.compose.ui.graphics.PathEffect
                        .dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                drawRect(
                    line,
                    Offset(w / 2, w / 2),
                    Size(size.width - w, size.height - w),
                    style =
                        androidx.compose.ui.graphics.drawscope
                            .Stroke(width = w, pathEffect = dash),
                )
            }.metroPress(onClick)
            .padding(horizontal = 10.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Mark(cross = false, line)
        Spacer(Modifier.width(6.dp))
        MetroText(label, Metro.typography.body, color = line, maxLines = 1)
    }
}

/** A box to find a category by name and the categories it matches; one tap adds it. No category is a flag. */
@Composable
private fun CategoryAdder(
    categories: List<CategoryChoice>,
    taken: Set<String>,
    onPick: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(top = MetroDimens.Gutter)) {
        MetroField(
            query,
            { query = it },
            LedgerFilterCopy.CATEGORY_HINT,
            Modifier.testTag("ledger-filter-category-find"),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
        )
        val shown = CategoryFilter.choices(categories.filter { !it.hidden && it.id !in taken }, query).take(MAX_CATEGORY_CHOICES)
        shown.forEach { category ->
            Choice(category.name, false, "ledger-filter-category-pick-${category.id}") { onPick(category.id) }
        }
    }
}

private const val MAX_CATEGORY_CHOICES = 8

/** A small × (or +) drawn in [color], sized to sit beside body text in a tag. */
@Composable
private fun Mark(
    cross: Boolean,
    color: androidx.compose.ui.graphics.Color,
) {
    androidx.compose.foundation.Canvas(Modifier.size(12.dp)) {
        val w = 1.6.dp.toPx()
        if (cross) {
            drawLine(color, Offset(0f, 0f), Offset(size.width, size.height), w)
            drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), w)
        } else {
            drawLine(color, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), w)
            drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), w)
        }
    }
}
