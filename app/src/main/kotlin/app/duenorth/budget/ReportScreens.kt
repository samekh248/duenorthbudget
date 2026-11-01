package app.duenorth.budget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.CurrencySpec
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.NetWorthPage
import app.duenorth.budget.core.RegisterEntry
import app.duenorth.budget.core.ReportPreset
import app.duenorth.budget.core.ReportRange
import app.duenorth.budget.core.ReportsCopy
import app.duenorth.budget.core.ReportsPage
import app.duenorth.budget.core.SpendingDrill
import app.duenorth.budget.core.SpendingFilter
import app.duenorth.budget.core.SpendingGroupBy
import app.duenorth.budget.core.SpendingRow
import app.duenorth.budget.core.SpendingTransaction
import app.duenorth.budget.core.monthLabel
import app.duenorth.budget.design.components.MetroComboBox
import app.duenorth.budget.design.components.MetroPivot
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.PivotItem
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val MONTH = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)

/** The [ReportsScreen] pivot items, in order; [ShellRoute.ReportPivot] opens one. */
object ReportItems {
    val headers = listOf(ReportsCopy.CASH_FLOW, ReportsCopy.SPENDING)
    val CASH_FLOW = headers.indexOf(ReportsCopy.CASH_FLOW)
    val SPENDING = headers.indexOf(ReportsCopy.SPENDING)
}

/**
 * Home pivot → reports: the reporting hub, listing every report. Net worth shows today's
 * total on its line and opens the graph over time; month review opens the open month's
 * spending by category; the rest open the range pivot at their item.
 */
@Composable
fun ReportsHub(
    netWorth: NetWorthPage?,
    month: YearMonth,
    onNetWorth: () -> Unit,
    onMonthReview: () -> Unit,
    onReport: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().testTag("reports-hub")) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                HubLine(
                    ReportsCopy.NET_WORTH,
                    netWorth?.let { "${MoneyFormat.format(it.totalMinor, it.currency, formatOptions())} today" }
                        ?: ReportsCopy.NET_WORTH_TODAY,
                    "reports-hub-networth",
                    onNetWorth,
                )
            }
            item {
                HubLine(
                    ReportsCopy.MONTH_REVIEW,
                    "${ReportsCopy.MONTH_REVIEW_NOTE} ${monthLabel(month)}",
                    "reports-hub-review",
                    onMonthReview,
                )
            }
            item { HubLine(ReportsCopy.CASH_FLOW, ReportsCopy.CASH_FLOW_NOTE, "reports-hub-cashflow") { onReport(ReportItems.CASH_FLOW) } }
            item { HubLine(ReportsCopy.SPENDING, ReportsCopy.SPENDING_NOTE, "reports-hub-spending") { onReport(ReportItems.SPENDING) } }
        }
    }
}

@Composable
private fun HubLine(
    title: String,
    note: String,
    tag: String,
    onOpen: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .metroPress(onOpen)
            .padding(horizontal = MetroDimens.Gutter, vertical = MetroDimens.Row)
            .testTag(tag),
    ) {
        MetroText(title, Metro.typography.subheader)
        MetroText(note, Metro.typography.body, color = Metro.colors.secondary)
    }
}

/**
 * Reports → cash flow or spending: a pivot with cash flow and spending over one shared range.
 * While a report computes, content-shaped placeholders fade in (`contracts/reports.md`).
 */
@Composable
fun ReportsScreen(
    page: ReportsPage?,
    loading: Boolean,
    preset: ReportPreset,
    groupBy: SpendingGroupBy,
    onPreset: (ReportPreset) -> Unit,
    onCustom: (ReportRange) -> Unit,
    onGroupBy: (SpendingGroupBy) -> Unit,
    modifier: Modifier = Modifier,
    initialItem: Int = 0,
    onRow: (SpendingRow) -> Unit = {},
) {
    MetroPivot(
        title = ReportsCopy.TITLE.uppercase(),
        items =
            listOf(
                PivotItem(ReportsCopy.CASH_FLOW) {
                    ReportBody(page, loading, preset, onPreset, onCustom, "report-cashflow") { CashFlow(it) }
                },
                PivotItem(ReportsCopy.SPENDING) {
                    ReportBody(page, loading, preset, onPreset, onCustom, "report-spending") { Spending(it, groupBy, onGroupBy, onRow) }
                },
            ),
        modifier = modifier.background(Metro.colors.background),
        initialItem = initialItem,
    )
}

@Composable
private fun ReportBody(
    page: ReportsPage?,
    loading: Boolean,
    preset: ReportPreset,
    onPreset: (ReportPreset) -> Unit,
    onCustom: (ReportRange) -> Unit,
    tag: String,
    content: @Composable (ReportsPage) -> Unit,
) {
    Column(Modifier.fillMaxSize().testTag(tag)) {
        RangePicker(preset, page?.range, onPreset, onCustom)
        if (page == null || loading) {
            AnimatedVisibility(visible = true, enter = fadeIn()) { Placeholder() }
        } else {
            content(page)
        }
    }
}

@Composable
private fun RangePicker(
    preset: ReportPreset,
    range: ReportRange?,
    onPreset: (ReportPreset) -> Unit,
    onCustom: (ReportRange) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = MetroDimens.Gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReportPreset.entries.forEach { option ->
            Choice(option.label, option == preset, "report-range-${option.name.lowercase()}") { onPreset(option) }
        }
    }
    if (preset == ReportPreset.CUSTOM) {
        // Two month pickers: each pick applies at once, and a start after the end moves the end with it.
        val latest = maxOf(YearMonth.now(), range?.end ?: YearMonth.now())
        val months = remember(latest) { (0L until CUSTOM_MONTHS).map { latest.minusMonths(it) } }
        val start = range?.start ?: latest.minusMonths(5)
        val end = range?.end ?: latest
        val label: (YearMonth) -> String = { it.format(MONTH).lowercase() }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = MetroDimens.Gutter),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                MetroText("from", Metro.typography.caption, color = Metro.colors.secondary)
                MetroComboBox(months, start, label, { onCustom(ReportRange(it, maxOf(it, end))) }, "report-custom-start")
            }
            Column(Modifier.weight(1f)) {
                MetroText("to", Metro.typography.caption, color = Metro.colors.secondary)
                MetroComboBox(months, end, label, { onCustom(ReportRange(minOf(start, it), it)) }, "report-custom-end")
            }
        }
    }
    MetroText(
        range?.let { "${it.start.format(MONTH)} – ${it.end.format(MONTH)}".lowercase() }.orEmpty(),
        Metro.typography.caption,
        Modifier.padding(horizontal = MetroDimens.Gutter).testTag("report-range-label"),
        color = Metro.colors.secondary,
    )
}

@Composable
private fun CashFlow(page: ReportsPage) {
    val report = page.cashFlow
    val scale = report.months.maxOfOrNull { maxOf(it.incomeMinor, abs(it.expenseMinor)) }?.takeIf { it > 0 } ?: 1L
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Totals(
                listOf(
                    ReportsCopy.INCOME to report.incomeMinor,
                    ReportsCopy.EXPENSES to report.expenseMinor,
                    ReportsCopy.NET to report.netMinor,
                ),
                page.currency,
                "report-cashflow-totals",
            )
        }
        items(report.months.reversed(), key = { it.month.toString() }) { month ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MetroDimens.Gutter, vertical = MetroDimens.Row)
                    .testTag("report-cashflow-${month.month}"),
            ) {
                Row(Modifier.fillMaxWidth()) {
                    MetroText(month.month.format(MONTH).lowercase(), Metro.typography.subheader, Modifier.weight(1f))
                    MetroText(MoneyFormat.format(month.netMinor, page.currency, formatOptions()), Metro.typography.subheader)
                }
                Bar(month.incomeMinor, scale, Metro.accent.fill)
                Bar(abs(month.expenseMinor), scale, Metro.colors.secondary)
                MetroText(
                    "${ReportsCopy.INCOME} ${MoneyFormat.format(month.incomeMinor, page.currency, formatOptions())} · " +
                        "${ReportsCopy.EXPENSES} ${MoneyFormat.format(month.expenseMinor, page.currency, formatOptions())}",
                    Metro.typography.caption,
                    color = Metro.colors.secondary,
                )
            }
        }
    }
}

@Composable
private fun Spending(
    page: ReportsPage,
    groupBy: SpendingGroupBy,
    onGroupBy: (SpendingGroupBy) -> Unit,
    onRow: (SpendingRow) -> Unit,
) {
    val report = page.spending
    val scale = report.rows.maxOfOrNull { abs(it.amountMinor) }?.takeIf { it > 0 } ?: 1L
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = MetroDimens.Gutter)) {
                SpendingGroupBy.entries.forEach { option ->
                    Choice(option.label, option == groupBy, "report-spending-by-${option.name.lowercase()}") { onGroupBy(option) }
                }
            }
            Totals(listOf(ReportsCopy.TOTAL to report.totalMinor), page.currency, "report-spending-total")
            if (report.rows.isEmpty()) {
                MetroText(
                    ReportsCopy.NOTHING,
                    Metro.typography.body,
                    Modifier.padding(MetroDimens.Gutter).testTag("report-spending-empty"),
                )
            }
        }
        items(report.rows, key = { it.id ?: "none" }) { row ->
            SpendingLine(row, scale, page.currency, "report-spending-row-${row.id ?: "none"}") { onRow(row) }
        }
    }
}

/** One spending row: its name and amount over a bar; tapping it drills in. */
@Composable
private fun SpendingLine(
    row: SpendingRow,
    scale: Long,
    currency: CurrencySpec,
    tag: String,
    onOpen: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .metroPress(onOpen)
            .padding(horizontal = MetroDimens.Gutter, vertical = MetroDimens.Row)
            .testTag(tag),
    ) {
        Row(Modifier.fillMaxWidth()) {
            MetroText(row.label, Metro.typography.subheader, Modifier.weight(1f), maxLines = 2)
            MetroText(MoneyFormat.format(row.amountMinor, currency, formatOptions()), Metro.typography.subheader)
        }
        Bar(abs(row.amountMinor), scale, Metro.accent.fill)
    }
}

/**
 * Spending → a row → ...: the row's name as the page header under the trail tapped to reach it, its
 * total over the report's range, then either what it is made of one level down (group → categories,
 * category → payees, payee → categories; each row drills on) or, at the bottom, its transactions, each
 * opening its form. Back walks up the trail.
 */
@Composable
fun SpendingDrillScreen(
    path: List<SpendingFilter>,
    drill: SpendingDrill?,
    onRow: (SpendingRow, SpendingGroupBy) -> Unit,
    onTransaction: (SpendingTransaction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(Metro.colors.background).testTag("report-drill")) {
        val trail = (listOf(ReportsCopy.SPENDING) + path.dropLast(1).map { it.label }).joinToString(DRILL_TRAIL_SEPARATOR)
        MetroText(
            trail,
            Metro.typography.caption,
            Modifier.padding(horizontal = MetroDimens.Gutter).testTag("report-drill-trail"),
            color = Metro.colors.secondary,
            maxLines = 1,
        )
        MetroText(
            path.lastOrNull()?.label.orEmpty(),
            Metro.typography.header,
            Modifier.padding(horizontal = MetroDimens.Gutter).testTag("report-drill-title"),
            maxLines = 1,
        )
        if (drill == null) {
            AnimatedVisibility(visible = true, enter = fadeIn()) { Placeholder() }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = MetroDimens.Gutter, vertical = MetroDimens.Grid)) {
                    MetroText(
                        "${drill.range.start.format(MONTH)} – ${drill.range.end.format(MONTH)}".lowercase(),
                        Metro.typography.caption,
                        color = Metro.colors.secondary,
                    )
                    MetroText(
                        MoneyFormat.format(drill.totalMinor, drill.currency, formatOptions()),
                        Metro.typography.subheader,
                        Modifier.testTag("report-drill-total"),
                    )
                }
                val breakdown = drill.breakdown
                MetroText(
                    if (breakdown != null) "${ReportsCopy.BY} ${breakdown.groupBy.label}" else ReportsCopy.TRANSACTIONS,
                    Metro.typography.caption,
                    Modifier.padding(horizontal = MetroDimens.Gutter).padding(top = MetroDimens.Row).testTag("report-drill-level"),
                    color = Metro.colors.secondary,
                )
                if (breakdown == null && drill.transactions.isEmpty()) {
                    MetroText(ReportsCopy.NOTHING, Metro.typography.body, Modifier.padding(MetroDimens.Gutter))
                }
            }
            val breakdown = drill.breakdown
            if (breakdown != null) {
                val scale = breakdown.rows.maxOfOrNull { abs(it.amountMinor) }?.takeIf { it > 0 } ?: 1L
                items(breakdown.rows, key = { it.id ?: "none" }) { row ->
                    SpendingLine(row, scale, drill.currency, "report-drill-row-${row.id ?: "none"}") { onRow(row, breakdown.groupBy) }
                }
            } else {
                // Each line names what the trail has not: its category under a payee, else its payee.
                val byPayee = path.any { it.groupBy == SpendingGroupBy.PAYEE }
                items(drill.transactions, key = { it.id }) { line ->
                    DrillTransaction(line, drill.currency, byPayee) { onTransaction(line) }
                }
            }
        }
    }
}

@Composable
private fun DrillTransaction(
    line: SpendingTransaction,
    currency: CurrencySpec,
    byPayee: Boolean,
    onOpen: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .metroPress(onOpen)
            .padding(horizontal = MetroDimens.Gutter, vertical = MetroDimens.Row)
            .testTag("report-drill-transaction-${line.id}"),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            MetroText(if (byPayee) line.category else line.payee, Metro.typography.subheader, maxLines = 2)
            MetroText(
                RegisterEntry.registerDateLabel(line.date, dateFormat()),
                Metro.typography.caption,
                color = Metro.colors.secondary,
                maxLines = 1,
            )
        }
        MetroText(MoneyFormat.format(line.amountMinor, currency, formatOptions()), Metro.typography.subheader)
    }
}

@Composable
private fun Totals(
    lines: List<Pair<String, Long>>,
    currency: CurrencySpec,
    tag: String,
) {
    Column(Modifier.fillMaxWidth().padding(MetroDimens.Gutter).testTag(tag)) {
        lines.forEach { (label, amount) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                MetroText(label, Metro.typography.body, Modifier.weight(1f), color = Metro.colors.secondary)
                MetroText(MoneyFormat.format(amount, currency, formatOptions()), Metro.typography.subheader)
            }
        }
    }
}

/** A flat bar, no gradient or rounding: Metro draws data as plain rectangles. */
@Composable
private fun Bar(
    value: Long,
    scale: Long,
    color: Color,
) {
    val fraction = (value.toFloat() / scale).coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth().padding(vertical = 3.dp).height(10.dp)) {
        if (fraction > 0f) {
            Box(Modifier.fillMaxWidth(fraction).height(10.dp).background(color))
        } else {
            Box(Modifier.width(2.dp).height(10.dp).background(Metro.colors.chrome))
        }
    }
}

/** Between the steps of a drilled page's trail. */
private const val DRILL_TRAIL_SEPARATOR = " › "

/** How far back the custom range's month pickers reach. */
private const val CUSTOM_MONTHS = 24L
