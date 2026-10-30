package app.duenorth.budget

import androidx.lifecycle.viewModelScope
import app.duenorth.budget.core.MatchPair
import app.duenorth.budget.core.ReconcileFinishResult
import app.duenorth.budget.core.ReconcileStartResult
import app.duenorth.budget.core.RegisterEntry
import app.duenorth.budget.core.RegisterSearch
import app.duenorth.budget.core.TransactionDraft
import app.duenorth.budget.core.WriteResult
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// The register: an account's transactions, the transaction form, selection, search and reconcile.

/**
 * Home's add: the full transaction form, with no account picked until the owner picks one (the first open
 * budget account's page, or any open account's, supplies the categories); saving comes back home. With no
 * account yet, adding one comes first.
 */
fun ShellViewModel.showAddTransaction() {
    val open =
        state.value.shell
            ?.accounts
            .orEmpty()
            .filter { !it.closed }
    val accountId = (open.firstOrNull { !it.offBudget } ?: open.firstOrNull())?.id
    if (accountId == null) {
        openAddAccount()
        return
    }
    push(ShellRoute.EditTransaction(accountId, null))
    // The form waits for this account's page rather than drawing over another account's.
    mutate { it.copy(editFromHome = true, registerSelection = null, register = null) }
    payees.load()
    viewModelScope.launch { apply(load(accountId), accountId) }
}

fun ShellViewModel.openAccount(accountId: String) {
    push(ShellRoute.Register(accountId))
    mutate { it.copy(registerSelection = null, registerSearch = null, editFromHome = false) }
    viewModelScope.launch { apply(load(accountId), accountId) }
}

fun ShellViewModel.openNewTransaction() {
    val accountId = openAccountId() ?: return
    mutate { it.copy(editFromHome = false, newTransactionPrefill = null) }
    push(ShellRoute.EditTransaction(accountId, null))
    payees.load()
}

/** A reconcile's "add it": a new transaction for the difference, dated on the statement, so saving it balances. */
fun ShellViewModel.addMissingForReconcile() {
    val page = state.value.reconcile ?: return openNewTransaction()
    openNewTransaction()
    mutate {
        it.copy(newTransactionPrefill = NewTransactionPrefill(page.differenceMinor, RegisterEntry.formatIso(page.statementDate)))
    }
}

fun ShellViewModel.openEdit(transactionId: String) {
    val accountId = openAccountId() ?: return
    push(ShellRoute.EditTransaction(accountId, transactionId))
    payees.load()
}

fun ShellViewModel.openReconcile() {
    val accountId = openAccountId() ?: return
    push(ShellRoute.Reconcile(accountId))
    reloadReconcile(accountId)
}

fun ShellViewModel.startReconcile(
    balanceText: String,
    dateText: String,
) {
    val accountId = reconcileAccountId() ?: return
    val budgetId = state.value.settings.openBudgetId ?: return
    viewModelScope.launch {
        when (
            val result =
                withContext(io) {
                    library.startReconcile(budgetId, accountId, balanceText, dateText)
                }
        ) {
            is ReconcileStartResult.Started ->
                mutate { it.copy(reconcile = result.page, reconcileError = null) }
            is ReconcileStartResult.Rejected ->
                mutate { it.copy(reconcileError = result.reason) }
        }
    }
}

fun ShellViewModel.toggleReconcileCleared(transactionId: String) {
    val accountId = reconcileAccountId() ?: return
    val budgetId = state.value.settings.openBudgetId ?: return
    viewModelScope.launch {
        val page =
            withContext(io) {
                library.toggleReconcileCleared(budgetId, accountId, transactionId)
            }
        if (page != null) mutate { it.copy(reconcile = page) }
    }
}

/** A hint's "clear both" or "unclear it": several rows' cleared marks set in one go. */
fun ShellViewModel.setReconcileCleared(
    transactionIds: List<String>,
    cleared: Boolean,
) {
    val accountId = reconcileAccountId() ?: return
    val budgetId = state.value.settings.openBudgetId ?: return
    viewModelScope.launch {
        val page =
            withContext(io) {
                library.setReconcileCleared(budgetId, accountId, transactionIds, cleared)
            }
        if (page != null) mutate { it.copy(reconcile = page) }
    }
}

fun ShellViewModel.finishReconcile(adjust: Boolean = false) {
    val accountId = reconcileAccountId() ?: return
    val budgetId = state.value.settings.openBudgetId ?: return
    viewModelScope.launch {
        when (val result = withContext(io) { library.finishReconcile(budgetId, accountId, adjust) }) {
            ReconcileFinishResult.Finished -> {
                history.clear()
                history.addLast(ShellRoute.Home)
                mutate {
                    it.copy(
                        route = ShellRoute.Register(accountId),
                        reconcile = null,
                        reconcileError = null,
                    )
                }
                apply(load(accountId), accountId)
            }
            is ReconcileFinishResult.Rejected ->
                mutate { state -> state.copy(reconcileError = result.reason) }
        }
    }
}

fun ShellViewModel.cancelReconcile() {
    val accountId = reconcileAccountId() ?: return
    val budgetId = state.value.settings.openBudgetId ?: return
    viewModelScope.launch {
        withContext(io) { library.cancelReconcile(budgetId, accountId) }
        history.removeLastOrNull()
        mutate {
            it.copy(
                route = ShellRoute.Register(accountId),
                reconcile = null,
                reconcileError = null,
            )
        }
    }
}

fun ShellViewModel.submitTransaction(draft: TransactionDraft) {
    // Saving goes back to the page that opened the form, even when the form moved the transaction to another account.
    val from = (state.value.route as? ShellRoute.EditTransaction)?.accountId ?: draft.accountId
    launchWrite { id ->
        val result = withContext(io) { library.saveTransaction(id, draft) }
        publish(result, from, stayOnRegister = true)
    }
}

fun ShellViewModel.submitCategory(categoryId: String?) {
    val target = state.value.categoryTarget ?: return
    val inbox = this.inbox.shown().orEmpty()
    val index = inbox.indexOfFirst { it.id == target.transactionId }
    val others = (state.value.route as? ShellRoute.PickCategory)?.others.orEmpty()
    if (categoryId != null && others.isNotEmpty()) {
        val name =
            target.categories
                .firstOrNull { it.id == categoryId }
                ?.name
                .orEmpty()
        val ids = setOf(target.transactionId) + others
        val rows = inbox.withIndex().filter { it.value.id in ids }
        this.inbox.fileRows(
            rows.associate { it.value.id to categoryId },
            rows.map { InboxFiled(it.value, it.index, name) },
            fromPicker = true,
        )
        return
    }
    if (categoryId != null && index >= 0) {
        val name =
            target.categories
                .firstOrNull { it.id == categoryId }
                ?.name
                .orEmpty()
        this.inbox.file(inbox[index], index, categoryId, name, fromPicker = true)
        return
    }
    launchWrite { id ->
        val result =
            withContext(io) {
                library.setTransactionCategory(id, target.transactionId, categoryId, false)
            }
        publish(result, target.accountId, stayOnRegister = false)
    }
}

/** A long press starts select mode with that transaction picked. */
fun ShellViewModel.startSelecting(transactionId: String) {
    mutate { it.copy(registerSelection = setOf(transactionId)) }
}

/** Picks or unpicks a transaction; unpicking the last one leaves select mode. */
fun ShellViewModel.toggleSelected(transactionId: String) {
    mutate {
        val picked = it.registerSelection ?: return@mutate it
        val next = if (transactionId in picked) picked - transactionId else picked + transactionId
        it.copy(registerSelection = next.ifEmpty { null })
    }
}

fun ShellViewModel.endSelecting() {
    mutate { it.copy(registerSelection = null) }
}

/** The register's search button: opens the search over the rows, or closes it when open. */
fun ShellViewModel.toggleRegisterSearch() {
    mutate { it.copy(registerSearch = if (it.registerSearch == null) RegisterSearch() else null) }
}

fun ShellViewModel.setRegisterSearch(search: RegisterSearch) {
    mutate { if (it.registerSearch == null) it else it.copy(registerSearch = search) }
}

fun ShellViewModel.closeRegisterSearch() {
    mutate { it.copy(registerSearch = null) }
}

/** Clears or unclears every picked transaction as one undo step, then leaves select mode. */
fun ShellViewModel.setSelectedCleared(cleared: Boolean) {
    val accountId = openAccountId() ?: return
    val picked = state.value.registerSelection?.toList() ?: return
    launchWrite { id ->
        val result = withContext(io) { library.setTransactionsCleared(id, picked, cleared, false) }
        publish(result, accountId, stayOnRegister = true)
    }
}

fun ShellViewModel.deleteTransaction(transactionId: String) {
    val accountId = openAccountId() ?: return
    launchWrite { id ->
        val result = withContext(io) { library.deleteTransaction(id, transactionId, false) }
        publish(result, accountId, stayOnRegister = true)
    }
}

internal fun ShellViewModel.openAccountId(): String? =
    when (val route = state.value.route) {
        is ShellRoute.Register -> route.accountId
        is ShellRoute.EditTransaction -> route.accountId
        is ShellRoute.Reconcile -> route.accountId
        else ->
            state.value.register
                ?.account
                ?.id
    }

private fun ShellViewModel.reconcileAccountId(): String? =
    when (val route = state.value.route) {
        is ShellRoute.Reconcile -> route.accountId
        else -> openAccountId()
    }

internal fun ShellViewModel.reloadReconcile(accountId: String) {
    val budgetId = state.value.settings.openBudgetId ?: return
    viewModelScope.launch {
        val page = withContext(io) { library.readReconcile(budgetId, accountId) }
        mutate { it.copy(reconcile = page, reconcileError = null) }
    }
}

/**
 * Match: keeps the bank's transaction with the entry's payee, category and note, and deletes the
 * entry. When neither side had a category the one left leads the inbox, ready to file.
 */
fun ShellViewModel.matchTransactions(
    pair: MatchPair,
    stayOnRegister: Boolean,
) {
    launchWrite { id ->
        val result = withContext(io) { library.matchTransactions(id, pair.bank.id, pair.entered.id) }
        if (result is WriteResult.Saved) mutate { it.copy(inbox = it.inbox.copy(lead = pair.bank.id)) }
        publish(result, pair.bank.accountId, stayOnRegister)
    }
}

fun ShellViewModel.notAMatch(
    pair: MatchPair,
    stayOnRegister: Boolean,
) {
    launchWrite { id ->
        val result = withContext(io) { library.notAMatch(id, pair.bank.id, pair.entered.id) }
        publish(result, pair.bank.accountId, stayOnRegister)
    }
}
