package app.duenorth.budget

import app.duenorth.budget.core.ReportPreset
import app.duenorth.budget.core.ReportRange
import app.duenorth.budget.core.ReportsPage
import app.duenorth.budget.core.SpendingGroupBy
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** The reports' own state, inside [ShellUiState.reports]. */
data class ReportsUi(
    val page: ReportsPage? = null,
    val loading: Boolean = false,
    val preset: ReportPreset = ReportPreset.LAST_6,
    val custom: ReportRange? = null,
    val groupBy: SpendingGroupBy = SpendingGroupBy.CATEGORY,
)

// Spec 010 US4 (T054): reports, computed on io. See contracts/reports.md.

/** Reports: the hub's pages, and the cash flow, net worth by month and spending pivot over one shared range. */
class ReportsHolder internal constructor(
    private val host: ShellHost,
) {
    private val state get() = host.state.value

    private fun update(transform: (ReportsUi) -> ReportsUi) = host.mutate { it.copy(reports = transform(it.reports)) }

    /** Reports → month review, what the review pivot item used to show. */
    fun openMonthReview() {
        host.push(ShellRoute.MonthReview)
    }

    /** Reports → net worth, the totals that used to sit on home. */
    fun openNetWorth() {
        host.push(ShellRoute.NetWorth)
    }

    /** Reports → cash flow, net worth by month, or spending: one pivot over a shared range. */
    fun open(item: Int) {
        host.push(ShellRoute.ReportPivot(item))
        load()
    }

    fun setPreset(preset: ReportPreset) {
        update { it.copy(preset = preset) }
        if (preset != ReportPreset.CUSTOM || state.reports.custom != null) load()
    }

    fun setRange(range: ReportRange) {
        update { it.copy(preset = ReportPreset.CUSTOM, custom = range) }
        load()
    }

    fun setGroupBy(groupBy: SpendingGroupBy) {
        update { it.copy(groupBy = groupBy) }
        load()
    }

    /** The same include choice as reports → net worth (spec 006). */
    fun setIncludeOffBudget(include: Boolean) {
        host.setNetWorthIncludeOffBudget(include)
        update { reports -> reports.copy(page = reports.page?.let { it.copy(netWorth = it.netWorth.copy(includeOffBudget = include)) }) }
    }

    private fun load() {
        val budgetId = state.settings.openBudgetId ?: return
        val current = state
        val today = runCatching { LocalDate.parse(current.today) }.getOrElse { LocalDate.now() }
        val range = current.reports.preset.range(today, current.reports.custom)
        val include = current.settings.netWorthIncludeOffBudget
        val groupBy = current.reports.groupBy
        update { it.copy(loading = true) }
        host.scope.launch {
            val page = withContext(host.io) { host.library.readReports(budgetId, range, include, groupBy) }
            update {
                // A later change may have asked for another range or grouping; keep the newest.
                if (it.preset.range(today, it.custom) != range || it.groupBy != groupBy) {
                    it
                } else {
                    it.copy(page = page, loading = false)
                }
            }
        }
    }
}
