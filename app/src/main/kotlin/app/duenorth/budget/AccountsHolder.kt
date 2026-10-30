package app.duenorth.budget

import app.duenorth.budget.core.AccountDetail
import app.duenorth.budget.core.AccountWriteResult
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.SyncCopy
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The accounts' own state, inside [ShellUiState.accounts]. */
data class AccountsUi(
    /** Every account, closed ones included, for the close page's destinations. */
    val list: List<AccountDetail> = emptyList(),
    /** The account the edit or close page has open; null while it loads. */
    val detail: AccountDetail? = null,
    /** Home's accounts list shows drag handles. */
    val reorder: Boolean = false,
    /** Why closing needs a forced close, while the page asks. */
    val forceConfirm: String? = null,
)

// Spec 010 US2 (T034): account management. See contracts/accounts.md.

/** Accounts: adding, editing, closing, reopening and reordering them. */
class AccountsHolder internal constructor(
    private val host: ShellHost,
) {
    private val state get() = host.state.value

    fun toggleShowClosed() {
        val settings = state.settings.let { it.copy(showClosedAccounts = !it.showClosedAccounts) }
        host.mutate { it.copy(settings = settings) }
        host.scope.launch {
            withContext(host.io) { host.library.updateSettings { it.copy(showClosedAccounts = settings.showClosedAccounts) } }
            reloadList()
            host.refresh()
        }
    }

    /** Accounts `•••` → reorder, and its done: the home accounts list shows drag handles in between. */
    fun toggleReorder() {
        host.mutate { it.copy(accounts = it.accounts.copy(reorder = !it.accounts.reorder)) }
    }

    fun openAdd() {
        host.push(ShellRoute.AddAccount)
        host.mutate { it.copy(editError = null) }
    }

    fun submitAdd(
        name: String,
        offBudget: Boolean,
        balanceText: String,
    ) {
        val budgetId = state.settings.openBudgetId ?: return
        val decimals =
            state.shell
                ?.currency
                ?.decimals ?: 2
        val balance =
            if (balanceText.isBlank()) {
                0L
            } else {
                MoneyFormat.parse(balanceText, decimals) ?: run {
                    host.mutate { it.copy(editError = SyncCopy.ENTER_AMOUNT) }
                    return
                }
            }
        host.scope.launch {
            when (val result = withContext(host.io) { host.library.createAccount(budgetId, name, offBudget, balance) }) {
                is AccountWriteResult.Saved -> afterWrite(budgetId, leave = true)
                is AccountWriteResult.Rejected -> host.mutate { it.copy(editError = result.reason) }
                is AccountWriteResult.NeedsConfirm -> Unit
            }
        }
    }

    /** Register `•••` → edit account. */
    fun openEdit() {
        val accountId = currentAccountId() ?: return
        host.push(ShellRoute.EditAccount(accountId))
        host.mutate { it.copy(editError = null, accounts = it.accounts.copy(detail = null)) }
        loadDetail(accountId)
    }

    fun submitEdit(
        name: String,
        note: String,
    ) {
        val budgetId = state.settings.openBudgetId ?: return
        val accountId = (state.route as? ShellRoute.EditAccount)?.accountId ?: return
        host.scope.launch {
            val result =
                withContext(host.io) {
                    val detail = host.library.readAccount(budgetId, accountId)
                    val renamed =
                        if (detail?.name ==
                            name.trim()
                        ) {
                            AccountWriteResult.Saved(accountId)
                        } else {
                            host.library.renameAccount(budgetId, accountId, name)
                        }
                    if (renamed !is AccountWriteResult.Saved ||
                        detail?.note == note
                    ) {
                        renamed
                    } else {
                        host.library.setAccountNote(budgetId, accountId, note)
                    }
                }
            when (result) {
                is AccountWriteResult.Saved -> afterWrite(budgetId, leave = true)
                is AccountWriteResult.Rejected -> host.mutate { it.copy(editError = result.reason) }
                is AccountWriteResult.NeedsConfirm -> Unit
            }
        }
    }

    /** Register `•••` → close. */
    fun openClose() {
        val accountId = currentAccountId() ?: return
        host.push(ShellRoute.CloseAccount(accountId))
        host.mutate { it.copy(editError = null, accounts = it.accounts.copy(detail = null, forceConfirm = null)) }
        loadDetail(accountId)
        reloadList()
        val budgetId = state.settings.openBudgetId ?: return
        host.scope.launch {
            val categories = withContext(host.io) { host.sync.categories(budgetId) }
            host.mutate { it.copy(categories = categories) }
        }
    }

    /** Closes and paints the account gone before the write lands; a refusal puts it host.back. */
    fun submitClose(
        destinationId: String?,
        categoryId: String?,
        forced: Boolean = false,
        confirmed: Boolean = false,
    ) {
        val budgetId = state.settings.openBudgetId ?: return
        val accountId = (state.route as? ShellRoute.CloseAccount)?.accountId ?: return
        val before = state.shell
        val detail = state.accounts.detail
        val optimistic =
            detail != null &&
                (forced && confirmed || detail.balanceMinor == 0L || detail.transactionCount == 0 || destinationId != null)
        if (optimistic && before != null && !state.settings.showClosedAccounts) {
            host.mutate { it.copy(shell = before.copy(accounts = before.accounts.filter { a -> a.id != accountId })) }
        }
        host.scope.launch {
            val result =
                withContext(host.io) { host.library.closeAccount(budgetId, accountId, destinationId, categoryId, forced, confirmed) }
            when (result) {
                is AccountWriteResult.Saved -> {
                    host.mutate { it.copy(accounts = it.accounts.copy(forceConfirm = null)) }
                    leaveFlow()
                    afterWrite(budgetId, leave = false)
                }
                is AccountWriteResult.Rejected -> host.mutate { it.copy(shell = before ?: it.shell, editError = result.reason) }
                is AccountWriteResult.NeedsConfirm ->
                    host.mutate {
                        it.copy(shell = before ?: it.shell, accounts = it.accounts.copy(forceConfirm = result.reason))
                    }
            }
        }
    }

    fun confirmForceClose() {
        submitClose(null, null, forced = true, confirmed = true)
    }

    fun keep() {
        host.mutate { it.copy(accounts = it.accounts.copy(forceConfirm = null)) }
    }

    fun reopen(accountId: String) {
        val budgetId = state.settings.openBudgetId ?: return
        host.mutate { current ->
            current.copy(
                accounts =
                    current.accounts.copy(
                        list =
                            current.accounts.list.map {
                                if (it.id ==
                                    accountId
                                ) {
                                    it.copy(closed = false)
                                } else {
                                    it
                                }
                            },
                    ),
            )
        }
        host.scope.launch {
            withContext(host.io) { host.library.reopenAccount(budgetId, accountId) }
            afterWrite(budgetId, leave = false)
        }
    }

    /** Register `•••` → reopen, for a closed account's register. */
    fun reopenOpen() {
        val accountId = currentAccountId() ?: return
        host.mutate { current ->
            val register = current.register?.takeIf { it.account.id == accountId } ?: return@mutate current
            current.copy(register = register.copy(account = register.account.copy(closed = false)))
        }
        reopen(accountId)
    }

    /**
     * A drop from reorder mode: the account goes just before [targetId], or last on its side of the budget when
     * [targetId] is null. The list moves before the write lands.
     */
    fun move(
        accountId: String,
        targetId: String?,
    ) {
        val budgetId = state.settings.openBudgetId ?: return
        val shell = state.shell ?: return
        val account = shell.accounts.firstOrNull { it.id == accountId && !it.closed } ?: return
        if (targetId == accountId) return
        val moved = shell.accounts.toMutableList()
        moved.removeAll { it.id == accountId }
        val to =
            if (targetId == null) {
                moved.indexOfLast { !it.closed && it.offBudget == account.offBudget } + 1
            } else {
                moved.indexOfFirst { it.id == targetId }.takeIf { it >= 0 } ?: return
            }
        moved.add(to, account)
        host.mutate { it.copy(shell = shell.copy(accounts = moved)) }
        host.scope.launch {
            withContext(host.io) { host.library.moveAccount(budgetId, accountId, targetId) }
            afterWrite(budgetId, leave = false)
        }
    }

    private fun currentAccountId(): String? =
        when (val route = state.route) {
            is ShellRoute.Register -> route.accountId
            is ShellRoute.EditAccount -> route.accountId
            is ShellRoute.CloseAccount -> route.accountId
            else ->
                state.register
                    ?.account
                    ?.id
        }

    fun reloadList() {
        val budgetId = state.settings.openBudgetId ?: return
        host.scope.launch {
            val list = withContext(host.io) { host.library.readAccounts(budgetId, includeClosed = true) }
            host.mutate { it.copy(accounts = it.accounts.copy(list = list)) }
        }
    }

    private fun loadDetail(accountId: String) {
        val budgetId = state.settings.openBudgetId ?: return
        host.scope.launch {
            val detail = withContext(host.io) { host.library.readAccount(budgetId, accountId) }
            host.mutate { it.copy(accounts = it.accounts.copy(detail = detail)) }
        }
    }

    /** A closed account's register makes no sense to return to; go host.back past it. */
    private fun leaveFlow() {
        host.back()
        if (state.route is ShellRoute.Register && !state.settings.showClosedAccounts) host.back()
    }

    private fun afterWrite(
        budgetId: String,
        leave: Boolean,
    ) {
        if (leave) host.back()
        host.forgetMonths()
        host.mutate { it.copy(editError = null, undoLabel = host.library.undoLabel(budgetId)) }
        reloadList()
        host.refresh()
        host.scope.launch {
            withContext(host.io) { runCatching { host.sync.sendUnsent(budgetId) } }
            host.refresh()
        }
    }
}
