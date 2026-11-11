package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.LedgerCopy
import app.duenorth.budget.core.LedgerPage
import app.duenorth.budget.core.LedgerRow
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.RegisterCopy
import app.duenorth.budget.core.RegisterEntry
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.AppGlyphIcon
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens

/**
 * The ledger: every account's transactions under their days, as the register draws them, each naming its account.
 * Filtered to a category, a chip under the title says so and clears it; the total is what the rows come to.
 */
@Composable
fun LedgerScreen(
    page: LedgerPage,
    onGesture: (Boolean) -> Unit,
    onOpen: (LedgerRow) -> Unit,
    onClearFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val filtered = page.categoryId != null
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(horizontal = MetroDimens.Gutter),
    ) {
        MetroText(LedgerCopy.TITLE, Metro.typography.header, maxLines = 1)
        if (filtered) {
            LedgerFilterChip(page.categoryName.orEmpty(), onClearFilter, Modifier.padding(top = MetroDimens.Row))
        }
        BudgetTotal(
            label = if (filtered) "${LedgerCopy.TOTAL} · ${LedgerCopy.ALL_ACCOUNTS}" else LedgerCopy.ALL_ACCOUNTS,
            amount = MoneyFormat.format(page.totalMinor, page.currency, formatOptions()),
            amountColor = Metro.accent.text,
            amountTag = "ledger-total",
            modifier = Modifier.padding(top = MetroDimens.Gutter),
        )
        if (page.rows.isEmpty()) {
            MetroText(
                if (filtered) LedgerCopy.NO_TRANSACTIONS else RegisterCopy.NO_TRANSACTIONS,
                Metro.typography.body,
                Modifier.padding(top = MetroDimens.Grid).testTag("ledger-empty"),
            )
        } else {
            LedgerList(page, onGesture, onOpen, Modifier.weight(1f).padding(top = MetroDimens.Grid))
        }
    }
}

/** "Groceries ✕" in an accent outline: the filter the ledger is under, and a tap that clears it. */
@Composable
private fun LedgerFilterChip(
    categoryName: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = Metro.accent.text
    Row(
        modifier
            .heightIn(min = MetroDimens.TouchTarget)
            .metroPress(onClear)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "${LedgerCopy.CATEGORY} $categoryName, ${LedgerCopy.CLEAR_FILTER}"
            }.testTag("ledger-filter"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .border(1.dp, accent)
                .padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetroText(
                LedgerCopy.CATEGORY,
                Metro.typography.caption,
                Modifier.padding(end = 6.dp),
                color = Metro.colors.secondary,
            )
            MetroText(categoryName, Metro.typography.body, Modifier.testTag("ledger-filter-name"), color = accent, maxLines = 1)
            AppGlyphIcon(AppGlyph.Cancel, accent, Modifier.padding(start = 6.dp).testTag("ledger-filter-clear"), size = 18.dp)
        }
    }
}

@Composable
private fun LedgerList(
    page: LedgerPage,
    onGesture: (Boolean) -> Unit,
    onOpen: (LedgerRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val list = rememberLazyListState()
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collect { onGesture(it) }
    }
    val byId = page.rows.associateBy { it.row.id }
    val days = registerDays(page.rows.map { it.row })
    val position = page.rows.withIndex().associate { it.value.row.id to it.index }
    val filtered = page.categoryId != null
    val cards = ledgerBlocks
    val line: @Composable (LedgerRow, Boolean, Boolean) -> Unit = { entry, banded, last ->
        RegisterLine(
            entry.row,
            page.currency,
            banded = banded,
            picked = null,
            onPress = { onOpen(entry) },
            onLongPress = { onOpen(entry) },
            detail = ledgerDetail(entry, showCategory = !filtered),
            extraAir = if (cards && last) MetroDimens.Row else 0.dp,
            showLock = true,
        )
    }
    LazyColumn(modifier.bleedGutter(cards), state = list, contentPadding = cardListPadding(cards)) {
        days.forEach { day ->
            item(key = "day-${day.first().id}") {
                MetroText(
                    RegisterEntry.registerDateLabel(day.first().date),
                    Metro.typography.caption,
                    Modifier
                        .padding(top = 16.dp, bottom = if (cards) 8.dp else 4.dp)
                        .testTag("ledger-day-${day.first().date}"),
                    color = Metro.accent.text,
                )
                if (cards) {
                    LedgerGroup(Modifier.padding(bottom = 4.dp), bottomAir = false, heading = {}) {
                        day.forEach { row -> line(byId.getValue(row.id), false, row === day.last()) }
                    }
                }
            }
            if (!cards) {
                items(day, key = { it.id }) { row -> line(byId.getValue(row.id), ledgerBanded(position.getValue(row.id)), false) }
            }
        }
    }
}

/** The quieter line under a payee: the category (left out when the ledger is filtered to it), the account, the note. */
internal fun ledgerDetail(
    entry: LedgerRow,
    showCategory: Boolean,
): String =
    listOfNotNull(
        entry.row.categoryLabel
            .takeIf { showCategory }
            ?.ifBlank { null },
        entry.accountName.ifBlank { null },
        entry.row.note
            .trim()
            .replace(Regex("\\s*\\R\\s*"), " ")
            .ifBlank { null },
    ).joinToString(" · ")
