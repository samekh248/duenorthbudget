package app.duenorth.budget

import androidx.lifecycle.viewModelScope
import app.duenorth.budget.core.BankLinkCopy
import app.duenorth.budget.core.BankLinkResult
import app.duenorth.budget.core.BankProvider
import app.duenorth.budget.core.BankSyncFailure
import app.duenorth.budget.core.BankSyncFix
import app.duenorth.budget.core.ExternalAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Spec 010 US6 (T068): bank link from the register. Provider calls run on io. See contracts/bank-link.md.

/** Register `•••` → bank link. */
fun ShellViewModel.openBankLink() {
    val accountId = (state.value.route as? ShellRoute.Register)?.accountId ?: return
    openBankLinkFor(BankLinkUi(accountId))
}

/**
 * A failed account's fix on the bank sync page that the phone can carry out: SimpleFIN picked on its bank
 * link, asking for a new setup token when SimpleFIN refused the old one, or listing its accounts to link again.
 */
fun ShellViewModel.repairBankLink(failure: BankSyncFailure) {
    if (failure.accountId.isEmpty()) return
    openBankLinkFor(
        BankLinkUi(failure.accountId, provider = BankProvider.SIMPLEFIN, replacingToken = failure.fix == BankSyncFix.TOKEN),
    )
}

private fun ShellViewModel.openBankLinkFor(ui: BankLinkUi) {
    val name =
        state.value.shell
            ?.accounts
            ?.firstOrNull { it.id == ui.accountId }
            ?.name
            .orEmpty()
    push(ShellRoute.BankLink(ui.accountId))
    mutate { it.copy(bankLink = ui.copy(accountName = name)) }
    refreshBankLink()
}

private fun ShellViewModel.refreshBankLink() {
    val budgetId = state.value.settings.openBudgetId ?: return
    val accountId = state.value.bankLink?.accountId ?: return
    viewModelScope.launch {
        val linked = withContext(io) { sync.bankLinkOf(budgetId, accountId) }
        val configured = withContext(io) { sync.bankCall { it.configured() } }
        updateBankLink {
            it.copy(
                loading = false,
                linked = linked,
                configured = configured.getOrDefault(emptyList()),
                error = configured.exceptionOrNull()?.message,
            )
        }
        // Opened to relink: SimpleFIN's accounts come up at once.
        val ui = state.value.bankLink ?: return@launch
        if (ui.provider == BankProvider.SIMPLEFIN && !ui.askingToken) loadBankAccounts(BankProvider.SIMPLEFIN)
    }
}

fun ShellViewModel.pickBankProvider(provider: BankProvider) {
    updateBankLink {
        it.copy(
            provider = provider,
            accounts = null,
            goCardless = null,
            goCardlessBanks = emptyList(),
            error = null,
            replacingToken = false,
        )
    }
    val ui = state.value.bankLink ?: return
    if (provider != BankProvider.GOCARDLESS && provider in ui.configured) loadBankAccounts(provider)
}

/** SimpleFIN is set up but its token was refused or revoked: the token field shows again. */
fun ShellViewModel.replaceSimpleFinToken() {
    updateBankLink { it.copy(provider = BankProvider.SIMPLEFIN, replacingToken = true, accounts = null, error = null) }
}

private fun ShellViewModel.loadBankAccounts(provider: BankProvider) {
    viewModelScope.launch {
        val accounts = withContext(io) { sync.bankCall { it.accounts(provider) } }
        updateBankLink { it.copy(accounts = accounts.getOrNull(), error = accounts.exceptionOrNull()?.message) }
    }
}

/** Sends the SimpleFIN setup token to the server; the phone keeps no copy. */
fun ShellViewModel.sendSimpleFinToken(token: String) {
    viewModelScope.launch {
        val sent = withContext(io) { sync.bankCall { it.saveSimpleFinToken(token) } }
        if (sent.isFailure) {
            updateBankLink { it.copy(error = sent.exceptionOrNull()?.message) }
            return@launch
        }
        updateBankLink { it.copy(configured = (it.configured + BankProvider.SIMPLEFIN).distinct(), replacingToken = false, error = null) }
        loadBankAccounts(BankProvider.SIMPLEFIN)
    }
}

fun ShellViewModel.findGoCardlessBanks(country: String) {
    if (country.isBlank()) return
    viewModelScope.launch {
        val banks = withContext(io) { sync.bankCall { it.goCardlessBanks(country) } }
        updateBankLink { it.copy(goCardlessBanks = banks.getOrDefault(emptyList()), error = banks.exceptionOrNull()?.message) }
    }
}

fun ShellViewModel.startGoCardless(institutionId: String) {
    viewModelScope.launch {
        val link = withContext(io) { sync.bankCall { it.goCardlessStart(institutionId) } }
        updateBankLink { it.copy(goCardless = link.getOrNull(), error = link.exceptionOrNull()?.message) }
    }
}

/** After the bank's consent page: the requisition's accounts, or a nudge to finish there first. */
fun ShellViewModel.checkGoCardless() {
    val requisition =
        state.value.bankLink
            ?.goCardless
            ?.requisitionId ?: return
    viewModelScope.launch {
        val accounts = withContext(io) { sync.bankCall { it.goCardlessAccounts(requisition) } }
        val list = accounts.getOrNull()
        updateBankLink {
            it.copy(
                accounts = list?.takeIf { found -> found.isNotEmpty() },
                error = accounts.exceptionOrNull()?.message ?: if (list.isNullOrEmpty()) BankLinkCopy.WAITING else null,
            )
        }
    }
}

fun ShellViewModel.linkBank(external: ExternalAccount) {
    val budgetId = state.value.settings.openBudgetId ?: return
    val ui = state.value.bankLink ?: return
    val provider = ui.provider ?: return
    viewModelScope.launch {
        when (val result = withContext(io) { sync.linkBankAccount(budgetId, ui.accountId, provider, external) }) {
            BankLinkResult.Saved -> afterBankLinkWrite(budgetId, leave = true)
            is BankLinkResult.Rejected -> updateBankLink { it.copy(error = result.reason) }
        }
    }
}

fun ShellViewModel.removeBankLink() {
    val budgetId = state.value.settings.openBudgetId ?: return
    val accountId = state.value.bankLink?.accountId ?: return
    viewModelScope.launch {
        when (val result = withContext(io) { sync.unlinkBankAccount(budgetId, accountId) }) {
            BankLinkResult.Saved -> afterBankLinkWrite(budgetId, leave = false)
            is BankLinkResult.Rejected -> updateBankLink { it.copy(error = result.reason) }
        }
    }
}

private suspend fun ShellViewModel.afterBankLinkWrite(
    budgetId: String,
    leave: Boolean,
) {
    mutate { it.copy(undoLabel = library.undoLabel(budgetId)) }
    if (leave) back() else refreshBankLink()
    withContext(io) { runCatching { sync.sendUnsent(budgetId) } }
}

private fun ShellViewModel.updateBankLink(transform: (BankLinkUi) -> BankLinkUi) {
    mutate { current -> current.copy(bankLink = current.bankLink?.let(transform)) }
}
