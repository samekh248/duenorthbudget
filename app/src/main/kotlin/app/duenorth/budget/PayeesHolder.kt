package app.duenorth.budget

import app.duenorth.budget.core.PayeeRow
import app.duenorth.budget.core.PayeeWriteResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The payees' own state, inside [ShellUiState.payees]. */
data class PayeesState(
    /** The saved payees, which the payees page lists and every payee box offers as you type. */
    val rows: List<PayeeRow> = emptyList(),
    /** The payee a rename would merge into, while the page asks; null otherwise. */
    val mergeTarget: String? = null,
)

/** Payees: the payees page, renaming and merging, and the saved names every payee box offers. */
class PayeesHolder internal constructor(
    private val host: ShellHost,
) {
    private val state get() = host.state.value

    private fun update(transform: (PayeesState) -> PayeesState) = host.mutate { it.copy(payees = transform(it.payees)) }

    fun show() {
        host.clearHistory()
        host.mutate { it.copy(route = ShellRoute.Payees, writeError = null, payees = it.payees.copy(mergeTarget = null)) }
        load()
    }

    /** Reads the saved payees, which the payees page lists and every payee box offers as you type. */
    fun load() {
        host.scope.launch {
            val budgetId = state.settings.openBudgetId ?: return@launch
            val rows = withContext(host.io) { host.library.listPayees(budgetId) }
            update { it.copy(rows = rows) }
        }
    }

    fun rename(
        payeeId: String,
        name: String,
        confirmMerge: Boolean,
        rememberRule: Boolean,
    ) {
        val budgetId = state.settings.openBudgetId ?: return
        host.scope.launch {
            when (
                val result =
                    withContext(host.io) {
                        host.library.renamePayee(budgetId, payeeId, name, confirmMerge, rememberRule)
                    }
            ) {
                is PayeeWriteResult.Saved -> {
                    val rows = withContext(host.io) { host.library.listPayees(budgetId) }
                    host.mutate { it.copy(payees = PayeesState(rows = rows), writeError = null) }
                }
                is PayeeWriteResult.Rejected ->
                    host.mutate { it.copy(writeError = result.reason) }
                is PayeeWriteResult.ConfirmMerge ->
                    host.mutate { it.copy(payees = it.payees.copy(mergeTarget = result.targetId), writeError = null) }
            }
        }
    }

    fun confirmMerge(
        payeeId: String,
        name: String,
    ) {
        rename(payeeId, name, confirmMerge = true, rememberRule = false)
    }

    fun cancelMerge() {
        update { it.copy(mergeTarget = null) }
    }
}
