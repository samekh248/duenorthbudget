package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.AccountChoice
import app.duenorth.budget.core.CategoryChoice
import app.duenorth.budget.core.LedgerCopy
import app.duenorth.budget.core.LedgerFilter
import app.duenorth.budget.core.LedgerPage
import app.duenorth.budget.core.LedgerRow
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.RegisterCopy
import app.duenorth.budget.core.RegisterEntry
import app.duenorth.budget.core.RegisterSearch
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import java.time.LocalDate

/**
 * The ledger: every account's transactions under their days, as the register draws them, each naming its account.
 * The filter button opens the same sheet as a register's, with an account field as well; while filters are on, the
 * strip under the total names them. A category page's "see all" opens it filtered to that category. The total is
 * what the rows shown come to.
 */
@Composable
fun LedgerScreen(
    page: LedgerPage,
    onGesture: (Boolean) -> Unit,
    onOpen: (LedgerRow) -> Unit,
    onClearFilter: () -> Unit,
    modifier: Modifier = Modifier,
    /** Every filter on the ledger, its category (the page's) among them. */
    filter: LedgerFilter = LedgerFilter(categoryIds = setOfNotNull(page.categoryId)),
    onFilter: (LedgerFilter) -> Unit = {},
    filterOpen: Boolean = false,
    onEditFilter: () -> Unit = {},
    /** The categories the sheet can add; the page's own is named from the page when missing. */
    categories: List<CategoryChoice> = emptyList(),
    today: LocalDate = LocalDate.now(),
) {
    val filtered = page.categoryId != null
    // The page arrives already narrowed to its category, split parts as rows of their own, so only the other
    // filters are matched here.
    val rest = filter.copy(categoryIds = emptySet())
    val rows = page.rows.filter { rest.matches(it.row, today, page.currency.decimals, it.accountId) }
    val named =
        categories.let { all ->
            if (page.categoryId == null || all.any { it.id == page.categoryId }) {
                all
            } else {
                all + CategoryChoice(page.categoryId!!, page.categoryName.orEmpty(), "", income = false)
            }
        }
    val accounts =
        page.rows
            .map { it.accountId to it.accountName }
            .distinct()
            .sortedBy { it.second.lowercase() }
            .map { (id, name) -> AccountChoice(id, name, offBudget = false, type = "", closed = false) }
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(horizontal = MetroDimens.Gutter),
    ) {
        MetroText(LedgerCopy.TITLE, Metro.typography.header, maxLines = 1)
        BudgetTotal(
            label = if (filter.active) "${LedgerCopy.TOTAL} · ${LedgerCopy.ALL_ACCOUNTS}" else LedgerCopy.ALL_ACCOUNTS,
            amount = MoneyFormat.format(rows.sumOf { it.row.amountMinor }, page.currency, formatOptions()),
            amountColor = Metro.accent.text,
            amountTag = "ledger-total",
            modifier = Modifier.padding(top = MetroDimens.Gutter),
        )
        if (filterOpen) {
            LedgerFilterSheet(
                filter,
                onFilter,
                named,
                accounts,
                shown = rows.size,
                total = page.rows.size,
                onClear = onClearFilter,
                modifier = Modifier.weight(1f).padding(top = MetroDimens.Gutter),
                singleCategory = true,
            )
            return@Column
        }
        if (filter.active) {
            LedgerFilterStrip(
                filter.summary(named, accounts, page.currency, formatOptions()),
                filter.count,
                shown = rows.size,
                total = page.rows.size,
                onEdit = onEditFilter,
            )
        }
        if (rows.isEmpty()) {
            MetroText(
                when {
                    rest.active -> RegisterSearch.NOTHING
                    filtered -> LedgerCopy.NO_TRANSACTIONS
                    else -> RegisterCopy.NO_TRANSACTIONS
                },
                Metro.typography.body,
                Modifier.padding(top = MetroDimens.Grid).testTag("ledger-empty"),
            )
        } else {
            LedgerList(page, rows, onGesture, onOpen, Modifier.weight(1f).padding(top = MetroDimens.Grid))
        }
    }
}

@Composable
private fun LedgerList(
    page: LedgerPage,
    rows: List<LedgerRow>,
    onGesture: (Boolean) -> Unit,
    onOpen: (LedgerRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val list = rememberLazyListState()
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collect { onGesture(it) }
    }
    val byId = rows.associateBy { it.row.id }
    val days = registerDays(rows.map { it.row })
    val position = rows.withIndex().associate { it.value.row.id to it.index }
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
