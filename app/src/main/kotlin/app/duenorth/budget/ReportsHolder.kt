package app.duenorth.budget

import app.duenorth.budget.core.ReportPreset
import app.duenorth.budget.core.ReportRange
import app.duenorth.budget.core.ReportsPage
import app.duenorth.budget.core.SpendingDrill
import app.duenorth.budget.core.SpendingFilter
import app.duenorth.budget.core.SpendingGroupBy
import app.duenorth.budget.core.SpendingRow
import app.duenorth.budget.core.SpendingTransaction
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
    /** Each drilled spending page read so far, by the rows tapped to reach it; back shows the one before at once. */
    val drills: Map<List<SpendingFilter>, SpendingDrill> = emptyMap(),
)

// Spec 010 US4 (T054): reports, computed on io. See contracts/reports.md.

/** Reports: the hub's pages, and the cash flow and spending pivot over one shared range. */
class ReportsHolder internal constructor(
    private val host: ShellHost,
) {
    private val state get() = host.state.value

    private fun update(transform: (ReportsUi) -> ReportsUi) = host.mutate { it.copy(reports = transform(it.reports)) }

    /** Reports → month review, what the review pivot item used to show. */
    fun openMonthReview() {
        host.push(ShellRoute.MonthReview)
    }

    /** Reports → cash flow or spending: one pivot over a shared range. */
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

    /** Spending → [row] (under [path], the rows already tapped): what it is made of, a level further down. */
    fun drill(
        path: List<SpendingFilter>,
        row: SpendingRow,
        groupBy: SpendingGroupBy,
    ) {
        val next = path + SpendingFilter(groupBy, row.id, row.label)
        host.push(ShellRoute.ReportDrill(next))
        loadDrill(next)
    }

    /** The spending pivot's own rows, grouped as the report is. */
    fun drill(row: SpendingRow) {
        val groupBy =
            state.reports.page
                ?.spending
                ?.groupBy ?: state.reports.groupBy
        drill(emptyList(), row, groupBy)
    }

    /** Reads [path]'s page over the report's range; the last one read stays on screen until it lands. */
    fun loadDrill(path: List<SpendingFilter>) {
        val budgetId = state.settings.openBudgetId ?: return
        val current = state
        val today = runCatching { LocalDate.parse(current.today) }.getOrElse { LocalDate.now() }
        val range = current.reports.preset.range(today, current.reports.custom)
        host.scope.launch {
            val drill = withContext(host.io) { host.library.readSpendingDrill(budgetId, range, path) } ?: return@launch
            update { it.copy(drills = it.drills + (path to drill)) }
        }
    }

    /** A drilled transaction opens its form; saving comes back to the drilled page. */
    fun openTransaction(transaction: SpendingTransaction) {
        host.push(ShellRoute.EditTransaction(transaction.accountId, transaction.openId))
        host.mutate { it.copy(editFromHome = true, registerUi = it.registerUi.copy(selection = null)) }
        host.loadPayees()
        host.scope.launch { host.reloadAccount(transaction.accountId) }
    }

    private fun load() {
        val budgetId = state.settings.openBudgetId ?: return
        val current = state
        val today = runCatching { LocalDate.parse(current.today) }.getOrElse { LocalDate.now() }
        val range = current.reports.preset.range(today, current.reports.custom)
        val include = current.settings.netWorthIncludeOffBudget
        val groupBy = current.reports.groupBy
        update { it.copy(loading = true, drills = emptyMap()) }
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
