package app.duenorth.budget

import androidx.lifecycle.viewModelScope
import app.duenorth.budget.core.ReportPreset
import app.duenorth.budget.core.ReportRange
import app.duenorth.budget.core.SpendingGroupBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

// Spec 010 US4 (T054): reports, computed on Dispatchers.IO. See contracts/reports.md.

/** Reports → month review, what the review pivot item used to show. */
fun ShellViewModel.openMonthReview() {
    push(ShellRoute.MonthReview)
}

/** Reports → net worth, the totals that used to sit on home. */
fun ShellViewModel.openNetWorth() {
    push(ShellRoute.NetWorth)
}

/** Reports → cash flow, net worth by month, or spending: one pivot over a shared range. */
fun ShellViewModel.openReport(item: Int) {
    push(ShellRoute.ReportPivot(item))
    loadReports()
}

fun ShellViewModel.setReportPreset(preset: ReportPreset) {
    mutate { it.copy(reportPreset = preset) }
    if (preset != ReportPreset.CUSTOM || state.value.reportCustom != null) loadReports()
}

fun ShellViewModel.setReportRange(range: ReportRange) {
    mutate { it.copy(reportPreset = ReportPreset.CUSTOM, reportCustom = range) }
    loadReports()
}

fun ShellViewModel.setReportGroupBy(groupBy: SpendingGroupBy) {
    mutate { it.copy(reportGroupBy = groupBy) }
    loadReports()
}

/** The same include choice as reports → net worth (spec 006). */
fun ShellViewModel.setReportIncludeOffBudget(include: Boolean) {
    setNetWorthIncludeOffBudget(include)
    mutate { current -> current.copy(reports = current.reports?.let { it.copy(netWorth = it.netWorth.copy(includeOffBudget = include)) }) }
}

internal fun ShellViewModel.loadReports() {
    val budgetId = state.value.settings.openBudgetId ?: return
    val current = state.value
    val today = runCatching { LocalDate.parse(current.today) }.getOrElse { LocalDate.now() }
    val range = current.reportPreset.range(today, current.reportCustom)
    val include = current.settings.netWorthIncludeOffBudget
    val groupBy = current.reportGroupBy
    mutate { it.copy(reportsLoading = true) }
    viewModelScope.launch {
        val page = withContext(Dispatchers.IO) { library.readReports(budgetId, range, include, groupBy) }
        mutate {
            // A later change may have asked for another range or grouping; keep the newest.
            if (it.reportPreset.range(today, it.reportCustom) != range || it.reportGroupBy != groupBy) {
                it
            } else {
                it.copy(reports = page, reportsLoading = false)
            }
        }
    }
}
