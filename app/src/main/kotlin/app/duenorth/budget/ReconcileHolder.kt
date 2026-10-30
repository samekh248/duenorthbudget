package app.duenorth.budget

import app.duenorth.budget.core.ReconcileFinishResult
import app.duenorth.budget.core.ReconcilePage
import app.duenorth.budget.core.ReconcileStartResult
import app.duenorth.budget.core.RegisterEntry
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Reconcile's own state, inside [ShellUiState.reconcile]. */
data class ReconcileUi(
    /** The reconcile under way: the statement, the cleared rows and the difference left; null before it starts. */
    val page: ReconcilePage? = null,
    val error: String? = null,
)

/** Reconcile: matching an account's cleared transactions to a statement. Its "add it" opens [register]'s form. */
class ReconcileHolder internal constructor(
    private val host: ShellHost,
    private val register: RegisterHolder,
) {
    private val state get() = host.state.value

    private fun update(transform: (ReconcileUi) -> ReconcileUi) = host.mutate { it.copy(reconcile = transform(it.reconcile)) }

    /** A reconcile's "add it": a new transaction for the difference, dated on the statement, so saving it balances. */
    fun addMissing() {
        val page = state.reconcile.page ?: return register.openNew()
        register.openNew()
        host.mutate {
            it.copy(newTransactionPrefill = NewTransactionPrefill(page.differenceMinor, RegisterEntry.formatIso(page.statementDate)))
        }
    }

    fun open() {
        val accountId = state.openAccountId() ?: return
        host.push(ShellRoute.Reconcile(accountId))
        reload(accountId)
    }

    fun start(
        balanceText: String,
        dateText: String,
    ) {
        val accountId = reconcilingAccount() ?: return
        val budgetId = state.settings.openBudgetId ?: return
        host.scope.launch {
            when (
                val result =
                    withContext(host.io) {
                        host.library.startReconcile(budgetId, accountId, balanceText, dateText)
                    }
            ) {
                is ReconcileStartResult.Started ->
                    update { ReconcileUi(page = result.page) }
                is ReconcileStartResult.Rejected ->
                    update { it.copy(error = result.reason) }
            }
        }
    }

    fun toggleCleared(transactionId: String) {
        val accountId = reconcilingAccount() ?: return
        val budgetId = state.settings.openBudgetId ?: return
        host.scope.launch {
            val page =
                withContext(host.io) {
                    host.library.toggleReconcileCleared(budgetId, accountId, transactionId)
                }
            if (page != null) update { it.copy(page = page) }
        }
    }

    /** A hint's "clear both" or "unclear it": several rows' cleared marks set in one go. */
    fun setCleared(
        transactionIds: List<String>,
        cleared: Boolean,
    ) {
        val accountId = reconcilingAccount() ?: return
        val budgetId = state.settings.openBudgetId ?: return
        host.scope.launch {
            val page =
                withContext(host.io) {
                    host.library.setReconcileCleared(budgetId, accountId, transactionIds, cleared)
                }
            if (page != null) update { it.copy(page = page) }
        }
    }

    fun finish(adjust: Boolean = false) {
        val accountId = reconcilingAccount() ?: return
        val budgetId = state.settings.openBudgetId ?: return
        host.scope.launch {
            when (val result = withContext(host.io) { host.library.finishReconcile(budgetId, accountId, adjust) }) {
                ReconcileFinishResult.Finished -> {
                    host.replaceHistory(listOf(ShellRoute.Home))
                    host.mutate {
                        it.copy(
                            route = ShellRoute.Register(accountId),
                            reconcile = ReconcileUi(),
                        )
                    }
                    host.reloadAccount(accountId)
                }
                is ReconcileFinishResult.Rejected ->
                    update { it.copy(error = result.reason) }
            }
        }
    }

    fun cancel() {
        val accountId = reconcilingAccount() ?: return
        val budgetId = state.settings.openBudgetId ?: return
        host.scope.launch {
            withContext(host.io) { host.library.cancelReconcile(budgetId, accountId) }
            host.dropLastHistory()
            host.mutate {
                it.copy(
                    route = ShellRoute.Register(accountId),
                    reconcile = ReconcileUi(),
                )
            }
        }
    }

    private fun reconcilingAccount(): String? =
        when (val route = state.route) {
            is ShellRoute.Reconcile -> route.accountId
            else -> state.openAccountId()
        }

    internal fun reload(accountId: String) {
        val budgetId = state.settings.openBudgetId ?: return
        host.scope.launch {
            val page = withContext(host.io) { host.library.readReconcile(budgetId, accountId) }
            update { ReconcileUi(page = page) }
        }
    }
}
