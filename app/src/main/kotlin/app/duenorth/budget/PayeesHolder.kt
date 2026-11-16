package app.duenorth.budget

import app.duenorth.budget.core.PayeeLookalike
import app.duenorth.budget.core.PayeeRow
import app.duenorth.budget.core.PayeeWriteResult
import app.duenorth.budget.core.WriteResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The payees' own state, inside [ShellUiState.payees]. */
data class PayeesState(
    /** The saved payees, which the payees page lists and every payee box offers as you type. */
    val rows: List<PayeeRow> = emptyList(),
    /** Names that look like one payee, the payees page's suggestions, most used first. */
    val lookalikes: List<PayeeLookalike> = emptyList(),
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
            val page = withContext(host.io) { host.library.readPayees(budgetId) }
            update { it.copy(rows = page.rows, lookalikes = page.lookalikes) }
        }
    }

    /** Makes [payeeIds] one payee called [name]: a suggestion's merge, or the checked payees in batch mode. */
    fun mergeAll(
        payeeIds: List<String>,
        name: String,
        rememberRule: Boolean,
    ) {
        val budgetId = state.settings.openBudgetId ?: return
        host.scope.launch {
            val result = withContext(host.io) { host.library.mergePayees(budgetId, payeeIds, name, rememberRule) }
            when (result) {
                is PayeeWriteResult.Saved -> {
                    host.toastSuccess(ToastCopy.PAYEES_MERGED, name.trim())
                    reload(budgetId)
                }
                is PayeeWriteResult.Rejected -> host.toastFailure(ToastCopy.PAYEES_MERGE_FAILED, result.reason)
                is PayeeWriteResult.ConfirmMerge -> Unit
            }
        }
    }

    /** Deletes payees no transaction uses. */
    fun deleteAll(payeeIds: List<String>) {
        val budgetId = state.settings.openBudgetId ?: return
        host.scope.launch {
            when (val result = withContext(host.io) { host.library.deletePayees(budgetId, payeeIds) }) {
                is PayeeWriteResult.Saved -> {
                    host.toastSuccess(ToastCopy.PAYEES_DELETED, "${payeeIds.size}")
                    reload(budgetId)
                }
                is PayeeWriteResult.Rejected -> host.toastFailure(ToastCopy.PAYEES_DELETE_FAILED, result.reason)
                is PayeeWriteResult.ConfirmMerge -> Unit
            }
        }
    }

    /** "Not the same": the suggestion goes, here and in the assistant. */
    fun notTheSame(group: PayeeLookalike) {
        val budgetId = state.settings.openBudgetId ?: return
        // Gone at once, so the next suggestion rises while the write runs.
        update { it.copy(lookalikes = it.lookalikes.filter { other -> other.id != group.id }) }
        host.scope.launch {
            val result = withContext(host.io) { host.library.dismissLookalikes(budgetId, group.keys) }
            if (result is WriteResult.Saved) host.toastSuccess(ToastCopy.LOOKALIKE_DISMISSED, group.keep.name)
            reload(budgetId)
        }
    }

    private suspend fun reload(budgetId: String) {
        val page = withContext(host.io) { host.library.readPayees(budgetId) }
        host.mutate { it.copy(payees = PayeesState(rows = page.rows, lookalikes = page.lookalikes), writeError = null) }
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
                    host.toastSuccess(ToastCopy.PAYEE_SAVED, name.trim())
                    reload(budgetId)
                }
                is PayeeWriteResult.Rejected -> {
                    host.toastFailure(ToastCopy.PAYEE_SAVE_FAILED, result.reason)
                    host.mutate { it.copy(writeError = result.reason) }
                }
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
