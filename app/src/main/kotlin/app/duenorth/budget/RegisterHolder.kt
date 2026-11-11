package app.duenorth.budget

import app.duenorth.budget.core.LedgerFilter
import app.duenorth.budget.core.MatchPair
import app.duenorth.budget.core.RegisterSearch
import app.duenorth.budget.core.TransactionDraft
import app.duenorth.budget.core.WriteResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The register's own state, inside [ShellUiState.registerUi]. The page it shows stays [ShellUiState.register]. */
data class RegisterUi(
    /** Transactions picked in select mode; null when not selecting. */
    val selection: Set<String>? = null,
    /** The search, open over the rows; null when closed. */
    val search: RegisterSearch? = null,
    /** A transaction to bring into view and flash once the register shows it; cleared once it has. */
    val focus: String? = null,
    /** The filter sheet is up over the rows. */
    val filterOpen: Boolean = false,
)

/** The key a ledger's filters are kept under in [ShellUiState.ledgerFilters]: one per account register. */
fun registerFilterKey(accountId: String): String = "account:$accountId"

/**
 * The register: an account's transactions, the transaction form, select mode, search and matching. Filing from the
 * category picker goes through [inbox] when the transaction came from it.
 */
class RegisterHolder internal constructor(
    private val host: ShellHost,
    private val inbox: InboxHolder,
) {
    private val state get() = host.state.value

    private fun update(transform: (RegisterUi) -> RegisterUi) = host.mutate { it.copy(registerUi = transform(it.registerUi)) }

    /**
     * Home's add: the full transaction form, with no account picked until the owner picks one (the first open
     * budget account's page, or any open account's, supplies the categories); saving comes back home. With no
     * account yet, adding one comes first.
     */
    fun showAdd() {
        val open =
            state.shell
                ?.accounts
                .orEmpty()
                .filter { !it.closed }
        val accountId = (open.firstOrNull { !it.offBudget } ?: open.firstOrNull())?.id
        if (accountId == null) {
            host.openAddAccount()
            return
        }
        host.push(ShellRoute.EditTransaction(accountId, null))
        // The form waits for this account's page rather than drawing over another account's.
        host.mutate { it.copy(editFromHome = true, registerUi = it.registerUi.copy(selection = null), register = null) }
        host.loadPayees()
        host.scope.launch { host.reloadAccount(accountId) }
    }

    fun openAccount(accountId: String) {
        host.push(ShellRoute.Register(accountId))
        host.mutate { it.copy(registerUi = RegisterUi(), editFromHome = false) }
        host.scope.launch { host.reloadAccount(accountId) }
    }

    /** Opens [accountId]'s register scrolled to [transactionId], which flashes so the eye finds it. */
    fun openTransaction(
        accountId: String,
        transactionId: String,
    ) {
        if (accountId.isEmpty()) return
        host.push(ShellRoute.Register(accountId))
        host.mutate { it.copy(registerUi = RegisterUi(focus = transactionId), editFromHome = false) }
        host.scope.launch { host.reloadAccount(accountId) }
    }

    /** The register has scrolled to and flashed its focused transaction. */
    fun focusShown() = update { it.copy(focus = null) }

    fun openNew() {
        val accountId = state.openAccountId() ?: return
        host.mutate { it.copy(editFromHome = false, newTransactionPrefill = null) }
        host.push(ShellRoute.EditTransaction(accountId, null))
        host.loadPayees()
    }

    fun openEdit(transactionId: String) {
        val accountId = state.openAccountId() ?: return
        host.push(ShellRoute.EditTransaction(accountId, transactionId))
        host.loadPayees()
    }

    fun submit(draft: TransactionDraft) {
        // Saving goes back to the page that opened the form, even when the form moved the transaction to another account.
        val from = (state.route as? ShellRoute.EditTransaction)?.accountId ?: draft.accountId
        val adding = (state.route as? ShellRoute.EditTransaction)?.transactionId == null
        host.launchWrite { id ->
            val result = withContext(host.io) { host.library.saveTransaction(id, draft) }
            host.toastFor(
                result,
                if (adding) ToastCopy.TRANSACTION_ADDED else ToastCopy.TRANSACTION_SAVED,
                ToastCopy.TRANSACTION_SAVE_FAILED,
            )
            host.publish(result, from, stayOnRegister = true)
        }
    }

    fun submitCategory(categoryId: String?) {
        val target = state.categoryTarget ?: return
        val queue = inbox.shown().orEmpty()
        val index = queue.indexOfFirst { it.id == target.transactionId }
        val others = (state.route as? ShellRoute.PickCategory)?.others.orEmpty()
        if (categoryId != null && others.isNotEmpty()) {
            val name =
                target.categories
                    .firstOrNull { it.id == categoryId }
                    ?.name
                    .orEmpty()
            val ids = setOf(target.transactionId) + others
            val rows = queue.withIndex().filter { it.value.id in ids }
            inbox.fileRows(
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
            inbox.file(queue[index], index, categoryId, name, fromPicker = true)
            return
        }
        host.launchWrite { id ->
            val result =
                withContext(host.io) {
                    host.library.setTransactionCategory(id, target.transactionId, categoryId, false)
                }
            host.publish(result, target.accountId, stayOnRegister = false)
        }
    }

    /** A long press starts select mode with that transaction picked. */
    fun startSelecting(transactionId: String) {
        update { it.copy(selection = setOf(transactionId)) }
    }

    /** Picks or unpicks a transaction; unpicking the last one leaves select mode. */
    fun toggleSelected(transactionId: String) {
        update {
            val picked = it.selection ?: return@update it
            val next = if (transactionId in picked) picked - transactionId else picked + transactionId
            it.copy(selection = next.ifEmpty { null })
        }
    }

    fun endSelecting() {
        update { it.copy(selection = null) }
    }

    /** The register's search button: opens the search over the rows, or closes it when open. */
    fun toggleSearch() {
        update { it.copy(search = if (it.search == null) RegisterSearch() else null) }
    }

    fun setSearch(search: RegisterSearch) {
        update { if (it.search == null) it else it.copy(search = search) }
    }

    fun closeSearch() {
        update { it.copy(search = null) }
    }

    /** The filters on the open register, kept per account for as long as the app runs. */
    fun filter(): LedgerFilter = state.openAccountId()?.let { state.ledgerFilters[registerFilterKey(it)] } ?: LedgerFilter.NONE

    /** The register's filter button: opens the filter sheet, or closes it when open. */
    fun toggleFilter() {
        update { it.copy(filterOpen = !it.filterOpen, selection = null) }
    }

    fun closeFilter() {
        update { it.copy(filterOpen = false) }
    }

    /** Changes the open register's filters; the rows follow at once. */
    fun setFilter(filter: LedgerFilter) {
        val accountId = state.openAccountId() ?: return
        val key = registerFilterKey(accountId)
        host.mutate {
            it.copy(ledgerFilters = if (filter.active) it.ledgerFilters + (key to filter) else it.ledgerFilters - key)
        }
    }

    /** Drops every filter on the open register. */
    fun clearFilter() = setFilter(LedgerFilter.NONE)

    /** Clears or unclears every picked transaction as one undo step, then leaves select mode. */
    fun setSelectedCleared(cleared: Boolean) {
        val accountId = state.openAccountId() ?: return
        val picked = state.registerUi.selection?.toList() ?: return
        host.launchWrite { id ->
            val result = withContext(host.io) { host.library.setTransactionsCleared(id, picked, cleared, false) }
            host.publish(result, accountId, stayOnRegister = true)
        }
    }

    /** A row menu's clear or unclear: one transaction, without entering select mode. */
    fun setCleared(
        transactionId: String,
        cleared: Boolean,
    ) {
        val accountId = state.openAccountId() ?: return
        host.launchWrite { id ->
            val result = withContext(host.io) { host.library.setTransactionsCleared(id, listOf(transactionId), cleared, false) }
            host.publish(result, accountId, stayOnRegister = true)
        }
    }

    /** Merges the picked transactions into one, as desktop Actual's merge does, then leaves select mode. */
    fun mergeSelected() {
        val accountId = state.openAccountId() ?: return
        val picked = state.registerUi.selection ?: return
        // Keep the register's order, newest first, so the merge reads the same each time.
        val ordered =
            state.register
                ?.rows
                .orEmpty()
                .map { it.id }
                .filter { it in picked }
        if (ordered.size < 2 || host.writing) return
        host.launchWrite { id ->
            val result = withContext(host.io) { host.library.mergeTransactions(id, ordered, false) }
            host.toastFor(result, ToastCopy.TRANSACTIONS_MERGED, ToastCopy.MERGE_FAILED)
            host.publish(result, accountId, stayOnRegister = true)
        }
    }

    fun delete(transactionId: String) {
        val accountId = state.openAccountId() ?: return
        host.launchWrite { id ->
            val result = withContext(host.io) { host.library.deleteTransaction(id, transactionId, false) }
            host.toastFor(result, ToastCopy.TRANSACTION_DELETED, ToastCopy.TRANSACTION_DELETE_FAILED)
            host.publish(result, accountId, stayOnRegister = true)
        }
    }

    /**
     * Match: keeps the bank's transaction with the entry's payee, category and note, and deletes the
     * entry. When neither side had a category the one left leads the inbox, ready to file.
     */
    fun match(
        pair: MatchPair,
        stayOnRegister: Boolean,
    ) {
        host.launchWrite { id ->
            val result = withContext(host.io) { host.library.matchTransactions(id, pair.bank.id, pair.entered.id) }
            host.toastFor(result, ToastCopy.MATCHED, ToastCopy.MATCH_FAILED)
            if (result is WriteResult.Saved) host.mutate { it.copy(inbox = it.inbox.copy(lead = pair.bank.id)) }
            host.publish(result, pair.bank.accountId, stayOnRegister)
        }
    }

    fun notAMatch(
        pair: MatchPair,
        stayOnRegister: Boolean,
    ) {
        host.launchWrite { id ->
            val result = withContext(host.io) { host.library.notAMatch(id, pair.bank.id, pair.entered.id) }
            host.publish(result, pair.bank.accountId, stayOnRegister)
        }
    }
}
