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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.CurrencySpec
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.NetWorthPage
import app.duenorth.budget.core.ReportPreset
import app.duenorth.budget.core.ReportRange
import app.duenorth.budget.core.ReportsCopy
import app.duenorth.budget.core.ReportsPage
import app.duenorth.budget.core.ReviewCopy
import app.duenorth.budget.core.SpendingGroupBy
import app.duenorth.budget.core.monthLabel
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroField
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
    val headers = listOf(ReportsCopy.CASH_FLOW, ReportsCopy.NET_WORTH, ReportsCopy.SPENDING)
    val CASH_FLOW = headers.indexOf(ReportsCopy.CASH_FLOW)
    val NET_WORTH = headers.indexOf(ReportsCopy.NET_WORTH)
    val SPENDING = headers.indexOf(ReportsCopy.SPENDING)
}

/**
 * Home pivot → reports: the reporting hub, listing every report. Net worth shows today's
 * total on its line and opens the on/off budget totals; month review opens the open month's
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
            item {
                HubLine(ReportsCopy.NET_WORTH_BY_MONTH, ReportsCopy.NET_WORTH_BY_MONTH_NOTE, "reports-hub-networth-months") {
                    onReport(ReportItems.NET_WORTH)
                }
            }
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
 * Reports → cash flow, net worth by month, or spending: a pivot with cash flow, net worth, and spending over one shared range.
 * While a report computes, content-shaped placeholders fade in (`contracts/reports.md`).
 */
@Composable
fun ReportsScreen(
    page: ReportsPage?,
    loading: Boolean,
    preset: ReportPreset,
    groupBy: SpendingGroupBy,
    includeOffBudget: Boolean,
    onPreset: (ReportPreset) -> Unit,
    onCustom: (ReportRange) -> Unit,
    onGroupBy: (SpendingGroupBy) -> Unit,
    onIncludeOffBudget: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    initialItem: Int = 0,
) {
    MetroPivot(
        title = ReportsCopy.TITLE.uppercase(),
        items =
            listOf(
                PivotItem(ReportsCopy.CASH_FLOW) {
                    ReportBody(page, loading, preset, onPreset, onCustom, "report-cashflow") { CashFlow(it) }
                },
                PivotItem(ReportsCopy.NET_WORTH) {
                    ReportBody(page, loading, preset, onPreset, onCustom, "report-networth") {
                        NetWorth(it, includeOffBudget, onIncludeOffBudget)
                    }
                },
                PivotItem(ReportsCopy.SPENDING) {
                    ReportBody(page, loading, preset, onPreset, onCustom, "report-spending") { Spending(it, groupBy, onGroupBy) }
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
        var start by remember(range) { mutableStateOf(range?.start?.toString().orEmpty()) }
        var end by remember(range) { mutableStateOf(range?.end?.toString().orEmpty()) }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = MetroDimens.Gutter),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetroField(start, { start = it }, "from yyyy-mm", Modifier.weight(1f).testTag("report-custom-start"))
            MetroField(end, { end = it }, "to yyyy-mm", Modifier.weight(1f).testTag("report-custom-end"))
            MetroButton("apply", Modifier.testTag("report-custom-apply")) {
                val from = runCatching { YearMonth.parse(start.trim()) }.getOrNull()
                val to = runCatching { YearMonth.parse(end.trim()) }.getOrNull()
                if (from != null && to != null && from <= to) onCustom(ReportRange(from, to))
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
private fun NetWorth(
    page: ReportsPage,
    includeOffBudget: Boolean,
    onIncludeOffBudget: (Boolean) -> Unit,
) {
    val months = page.netWorth.months
    val scale = months.maxOfOrNull { abs(it.total(includeOffBudget)) }?.takeIf { it > 0 } ?: 1L
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            val last = months.lastOrNull()
            Totals(
                listOfNotNull(
                    ReportsCopy.ON_BUDGET to (last?.onBudgetMinor ?: 0L),
                    (ReportsCopy.OFF_BUDGET to (last?.offBudgetMinor ?: 0L)).takeIf { includeOffBudget },
                    ReportsCopy.TOTAL to (last?.total(includeOffBudget) ?: 0L),
                ),
                page.currency,
                "report-networth-totals",
            )
            Box(Modifier.padding(horizontal = MetroDimens.Gutter)) {
                Choice(
                    if (includeOffBudget) "hide off budget" else ReviewCopy.INCLUDE_OFF_BUDGET,
                    includeOffBudget,
                    "report-networth-offbudget",
                ) { onIncludeOffBudget(!includeOffBudget) }
            }
        }
        items(months.reversed(), key = { it.month.toString() }) { month ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MetroDimens.Gutter, vertical = MetroDimens.Row)
                    .testTag("report-networth-${month.month}"),
            ) {
                Row(Modifier.fillMaxWidth()) {
                    MetroText(month.month.format(MONTH).lowercase(), Metro.typography.subheader, Modifier.weight(1f))
                    MetroText(MoneyFormat.format(month.total(includeOffBudget), page.currency, formatOptions()), Metro.typography.subheader)
                }
                Bar(abs(month.total(includeOffBudget)), scale, Metro.accent.fill)
            }
        }
    }
}

@Composable
private fun Spending(
    page: ReportsPage,
    groupBy: SpendingGroupBy,
    onGroupBy: (SpendingGroupBy) -> Unit,
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
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MetroDimens.Gutter, vertical = MetroDimens.Row)
                    .testTag("report-spending-row-${row.id ?: "none"}"),
            ) {
                Row(Modifier.fillMaxWidth()) {
                    MetroText(row.label, Metro.typography.subheader, Modifier.weight(1f), maxLines = 2)
                    MetroText(MoneyFormat.format(row.amountMinor, page.currency, formatOptions()), Metro.typography.subheader)
                }
                Bar(abs(row.amountMinor), scale, Metro.accent.fill)
            }
        }
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
