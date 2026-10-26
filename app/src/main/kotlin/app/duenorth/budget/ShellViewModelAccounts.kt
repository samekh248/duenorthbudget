package app.duenorth.budget

import androidx.lifecycle.viewModelScope
import app.duenorth.budget.core.AccountWriteResult
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.SyncCopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Spec 010 US2 (T034): account management, as extensions of ShellViewModel. See contracts/accounts.md.

fun ShellViewModel.toggleShowClosedAccounts() {
    val settings = state.value.settings.let { it.copy(showClosedAccounts = !it.showClosedAccounts) }
    mutate { it.copy(settings = settings) }
    viewModelScope.launch {
        withContext(Dispatchers.IO) { library.updateSettings { it.copy(showClosedAccounts = settings.showClosedAccounts) } }
        reloadAccounts()
        refresh()
    }
}

/** Accounts `•••` → reorder, and its done: the home accounts list shows drag handles in between. */
fun ShellViewModel.toggleReorderAccounts() {
    mutate { it.copy(accountsReorder = !it.accountsReorder) }
}

fun ShellViewModel.openAddAccount() {
    push(ShellRoute.AddAccount)
    mutate { it.copy(editError = null) }
}

fun ShellViewModel.submitAddAccount(
    name: String,
    offBudget: Boolean,
    balanceText: String,
) {
    val budgetId = state.value.settings.openBudgetId ?: return
    val decimals =
        state.value.shell
            ?.currency
            ?.decimals ?: 2
    val balance =
        if (balanceText.isBlank()) {
            0L
        } else {
            MoneyFormat.parse(balanceText, decimals) ?: run {
                mutate { it.copy(editError = SyncCopy.ENTER_AMOUNT) }
                return
            }
        }
    viewModelScope.launch {
        when (val result = withContext(Dispatchers.IO) { library.createAccount(budgetId, name, offBudget, balance) }) {
            is AccountWriteResult.Saved -> afterAccountWrite(budgetId, leave = true)
            is AccountWriteResult.Rejected -> mutate { it.copy(editError = result.reason) }
            is AccountWriteResult.NeedsConfirm -> Unit
        }
    }
}

/** Register `•••` → edit account. */
fun ShellViewModel.openEditAccount() {
    val accountId = currentAccountId() ?: return
    push(ShellRoute.EditAccount(accountId))
    mutate { it.copy(editError = null, accountDetail = null) }
    loadAccountDetail(accountId)
}

fun ShellViewModel.submitEditAccount(
    name: String,
    note: String,
) {
    val budgetId = state.value.settings.openBudgetId ?: return
    val accountId = (state.value.route as? ShellRoute.EditAccount)?.accountId ?: return
    viewModelScope.launch {
        val result =
            withContext(Dispatchers.IO) {
                val detail = library.readAccount(budgetId, accountId)
                val renamed =
                    if (detail?.name ==
                        name.trim()
                    ) {
                        AccountWriteResult.Saved(accountId)
                    } else {
                        library.renameAccount(budgetId, accountId, name)
                    }
                if (renamed !is AccountWriteResult.Saved ||
                    detail?.note == note
                ) {
                    renamed
                } else {
                    library.setAccountNote(budgetId, accountId, note)
                }
            }
        when (result) {
            is AccountWriteResult.Saved -> afterAccountWrite(budgetId, leave = true)
            is AccountWriteResult.Rejected -> mutate { it.copy(editError = result.reason) }
            is AccountWriteResult.NeedsConfirm -> Unit
        }
    }
}

/** Register `•••` → close. */
fun ShellViewModel.openCloseAccount() {
    val accountId = currentAccountId() ?: return
    push(ShellRoute.CloseAccount(accountId))
    mutate { it.copy(editError = null, accountDetail = null, accountForceConfirm = null) }
    loadAccountDetail(accountId)
    reloadAccounts()
    val budgetId = state.value.settings.openBudgetId ?: return
    viewModelScope.launch {
        val categories = withContext(Dispatchers.IO) { sync.categories(budgetId) }
        mutate { it.copy(categories = categories) }
    }
}

/** Closes and paints the account gone before the write lands; a refusal puts it back. */
fun ShellViewModel.submitCloseAccount(
    destinationId: String?,
    categoryId: String?,
    forced: Boolean = false,
    confirmed: Boolean = false,
) {
    val budgetId = state.value.settings.openBudgetId ?: return
    val accountId = (state.value.route as? ShellRoute.CloseAccount)?.accountId ?: return
    val before = state.value.shell
    val detail = state.value.accountDetail
    val optimistic =
        detail != null &&
            (forced && confirmed || detail.balanceMinor == 0L || detail.transactionCount == 0 || destinationId != null)
    if (optimistic && before != null && !state.value.settings.showClosedAccounts) {
        mutate { it.copy(shell = before.copy(accounts = before.accounts.filter { a -> a.id != accountId })) }
    }
    viewModelScope.launch {
        val result =
            withContext(Dispatchers.IO) { library.closeAccount(budgetId, accountId, destinationId, categoryId, forced, confirmed) }
        when (result) {
            is AccountWriteResult.Saved -> {
                mutate { it.copy(accountForceConfirm = null) }
                leaveAccountFlow()
                afterAccountWrite(budgetId, leave = false)
            }
            is AccountWriteResult.Rejected -> mutate { it.copy(shell = before ?: it.shell, editError = result.reason) }
            is AccountWriteResult.NeedsConfirm -> mutate { it.copy(shell = before ?: it.shell, accountForceConfirm = result.reason) }
        }
    }
}

fun ShellViewModel.confirmForceClose() {
    submitCloseAccount(null, null, forced = true, confirmed = true)
}

fun ShellViewModel.keepAccount() {
    mutate { it.copy(accountForceConfirm = null) }
}

fun ShellViewModel.reopenAccount(accountId: String) {
    val budgetId = state.value.settings.openBudgetId ?: return
    mutate { current -> current.copy(accountsList = current.accountsList.map { if (it.id == accountId) it.copy(closed = false) else it }) }
    viewModelScope.launch {
        withContext(Dispatchers.IO) { library.reopenAccount(budgetId, accountId) }
        afterAccountWrite(budgetId, leave = false)
    }
}

/** Register `•••` → reopen, for a closed account's register. */
fun ShellViewModel.reopenOpenAccount() {
    val accountId = currentAccountId() ?: return
    mutate { current ->
        val register = current.register?.takeIf { it.account.id == accountId } ?: return@mutate current
        current.copy(register = register.copy(account = register.account.copy(closed = false)))
    }
    reopenAccount(accountId)
}

/**
 * A drop from reorder mode: the account goes just before [targetId], or last on its side of the budget when
 * [targetId] is null. The list moves before the write lands.
 */
fun ShellViewModel.moveAccount(
    accountId: String,
    targetId: String?,
) {
    val budgetId = state.value.settings.openBudgetId ?: return
    val shell = state.value.shell ?: return
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
    mutate { it.copy(shell = shell.copy(accounts = moved)) }
    viewModelScope.launch {
        withContext(Dispatchers.IO) { library.moveAccount(budgetId, accountId, targetId) }
        afterAccountWrite(budgetId, leave = false)
    }
}

private fun ShellViewModel.currentAccountId(): String? =
    when (val route = state.value.route) {
        is ShellRoute.Register -> route.accountId
        is ShellRoute.EditAccount -> route.accountId
        is ShellRoute.CloseAccount -> route.accountId
        else ->
            state.value.register
                ?.account
                ?.id
    }

internal fun ShellViewModel.reloadAccounts() {
    val budgetId = state.value.settings.openBudgetId ?: return
    viewModelScope.launch {
        val list = withContext(Dispatchers.IO) { library.readAccounts(budgetId, includeClosed = true) }
        mutate { it.copy(accountsList = list) }
    }
}

private fun ShellViewModel.loadAccountDetail(accountId: String) {
    val budgetId = state.value.settings.openBudgetId ?: return
    viewModelScope.launch {
        val detail = withContext(Dispatchers.IO) { library.readAccount(budgetId, accountId) }
        mutate { it.copy(accountDetail = detail) }
    }
}

/** A closed account's register makes no sense to return to; go back past it. */
private fun ShellViewModel.leaveAccountFlow() {
    back()
    if (state.value.route is ShellRoute.Register && !state.value.settings.showClosedAccounts) back()
}

private fun ShellViewModel.afterAccountWrite(
    budgetId: String,
    leave: Boolean,
) {
    if (leave) back()
    months.invalidate()
    mutate { it.copy(editError = null, undoLabel = library.undoLabel(budgetId)) }
    reloadAccounts()
    refresh()
    viewModelScope.launch {
        withContext(Dispatchers.IO) { runCatching { sync.sendUnsent(budgetId) } }
        refresh()
    }
}
