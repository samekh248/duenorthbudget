package app.duenorth.budget

import app.duenorth.budget.core.NetWorthHistory
import app.duenorth.budget.core.NetWorthSpan
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The net worth graph's own state, inside [ShellUiState.netWorthGraph]. */
data class NetWorthUi(
    val history: NetWorthHistory? = null,
    val loading: Boolean = false,
    val span: NetWorthSpan = NetWorthSpan.SIX_MONTHS,
)

/** Reports → net worth: a line of the total over a span ending today, read on io. */
class NetWorthHolder internal constructor(
    private val host: ShellHost,
) {
    private val state get() = host.state.value

    private fun update(transform: (NetWorthUi) -> NetWorthUi) = host.mutate { it.copy(netWorthGraph = transform(it.netWorthGraph)) }

    /** Opens the graph at the span last picked, read fresh so it shows today's balances. */
    fun open() {
        host.push(ShellRoute.NetWorth)
        load()
    }

    fun setSpan(span: NetWorthSpan) {
        if (span == state.netWorthGraph.span && state.netWorthGraph.history?.span == span) return
        update { it.copy(span = span) }
        load()
    }

    /** The same include choice as the hub's total (spec 006); the history holds both sides, so nothing is read again. */
    fun setIncludeOffBudget(include: Boolean) {
        host.setNetWorthIncludeOffBudget(include)
    }

    private fun load() {
        val budgetId = state.settings.openBudgetId ?: return
        val span = state.netWorthGraph.span
        update { it.copy(loading = true) }
        host.scope.launch {
            val history = withContext(host.io) { host.library.readNetWorthHistory(budgetId, span) }
            // A later pick may have asked for another span; keep the newest.
            update { if (it.span != span) it else it.copy(history = history, loading = false) }
        }
    }
}
