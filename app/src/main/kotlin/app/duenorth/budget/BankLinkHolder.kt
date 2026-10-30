package app.duenorth.budget

import app.duenorth.budget.core.BankLinkCopy
import app.duenorth.budget.core.BankLinkResult
import app.duenorth.budget.core.BankProvider
import app.duenorth.budget.core.BankSyncFailure
import app.duenorth.budget.core.BankSyncFix
import app.duenorth.budget.core.ExternalAccount
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Spec 010 US6 (T068): bank link from the register. Provider calls run on io. See contracts/bank-link.md.

/** Bank link: linking a register's account to SimpleFIN or GoCardless, and repairing a link bank sync could not use. */
class BankLinkHolder internal constructor(
    private val host: ShellHost,
) {
    private val state get() = host.state.value

    /** Register `•••` → bank link. */
    fun open() {
        val accountId = (state.route as? ShellRoute.Register)?.accountId ?: return
        openFor(BankLinkUi(accountId))
    }

    /**
     * A failed account's fix on the bank sync page that the phone can carry out: SimpleFIN picked on its bank
     * link, asking for a new setup token when SimpleFIN refused the old one, or listing its accounts to link again.
     */
    fun repair(failure: BankSyncFailure) {
        if (failure.accountId.isEmpty()) return
        openFor(
            BankLinkUi(failure.accountId, provider = BankProvider.SIMPLEFIN, replacingToken = failure.fix == BankSyncFix.TOKEN),
        )
    }

    private fun openFor(ui: BankLinkUi) {
        val name =
            state.shell
                ?.accounts
                ?.firstOrNull { it.id == ui.accountId }
                ?.name
                .orEmpty()
        host.push(ShellRoute.BankLink(ui.accountId))
        host.mutate { it.copy(bankLink = ui.copy(accountName = name)) }
        load()
    }

    private fun load() {
        val budgetId = state.settings.openBudgetId ?: return
        val accountId = state.bankLink?.accountId ?: return
        host.scope.launch {
            val linked = withContext(host.io) { host.sync.bankLinkOf(budgetId, accountId) }
            val configured = withContext(host.io) { host.sync.bankCall { it.configured() } }
            update {
                it.copy(
                    loading = false,
                    linked = linked,
                    configured = configured.getOrDefault(emptyList()),
                    error = configured.exceptionOrNull()?.message,
                )
            }
            // Opened to relink: SimpleFIN's accounts come up at once.
            val ui = state.bankLink ?: return@launch
            if (ui.provider == BankProvider.SIMPLEFIN && !ui.askingToken) loadAccounts(BankProvider.SIMPLEFIN)
        }
    }

    fun pickProvider(provider: BankProvider) {
        update {
            it.copy(
                provider = provider,
                accounts = null,
                goCardless = null,
                goCardlessBanks = emptyList(),
                error = null,
                replacingToken = false,
            )
        }
        val ui = state.bankLink ?: return
        if (provider != BankProvider.GOCARDLESS && provider in ui.configured) loadAccounts(provider)
    }

    /** SimpleFIN is set up but its token was refused or revoked: the token field shows again. */
    fun replaceSimpleFinToken() {
        update { it.copy(provider = BankProvider.SIMPLEFIN, replacingToken = true, accounts = null, error = null) }
    }

    private fun loadAccounts(provider: BankProvider) {
        host.scope.launch {
            val accounts = withContext(host.io) { host.sync.bankCall { it.accounts(provider) } }
            update { it.copy(accounts = accounts.getOrNull(), error = accounts.exceptionOrNull()?.message) }
        }
    }

    /** Sends the SimpleFIN setup token to the server; the phone keeps no copy. */
    fun sendSimpleFinToken(token: String) {
        host.scope.launch {
            val sent = withContext(host.io) { host.sync.bankCall { it.saveSimpleFinToken(token) } }
            if (sent.isFailure) {
                update { it.copy(error = sent.exceptionOrNull()?.message) }
                return@launch
            }
            update { it.copy(configured = (it.configured + BankProvider.SIMPLEFIN).distinct(), replacingToken = false, error = null) }
            loadAccounts(BankProvider.SIMPLEFIN)
        }
    }

    fun findGoCardlessBanks(country: String) {
        if (country.isBlank()) return
        host.scope.launch {
            val banks = withContext(host.io) { host.sync.bankCall { it.goCardlessBanks(country) } }
            update { it.copy(goCardlessBanks = banks.getOrDefault(emptyList()), error = banks.exceptionOrNull()?.message) }
        }
    }

    fun startGoCardless(institutionId: String) {
        host.scope.launch {
            val link = withContext(host.io) { host.sync.bankCall { it.goCardlessStart(institutionId) } }
            update { it.copy(goCardless = link.getOrNull(), error = link.exceptionOrNull()?.message) }
        }
    }

    /** After the bank's consent page: the requisition's accounts, or a nudge to finish there first. */
    fun checkGoCardless() {
        val requisition =
            state.bankLink
                ?.goCardless
                ?.requisitionId ?: return
        host.scope.launch {
            val accounts = withContext(host.io) { host.sync.bankCall { it.goCardlessAccounts(requisition) } }
            val list = accounts.getOrNull()
            update {
                it.copy(
                    accounts = list?.takeIf { found -> found.isNotEmpty() },
                    error = accounts.exceptionOrNull()?.message ?: if (list.isNullOrEmpty()) BankLinkCopy.WAITING else null,
                )
            }
        }
    }

    fun link(external: ExternalAccount) {
        val budgetId = state.settings.openBudgetId ?: return
        val ui = state.bankLink ?: return
        val provider = ui.provider ?: return
        host.scope.launch {
            when (val result = withContext(host.io) { host.sync.linkBankAccount(budgetId, ui.accountId, provider, external) }) {
                BankLinkResult.Saved -> afterWrite(budgetId, leave = true)
                is BankLinkResult.Rejected -> update { it.copy(error = result.reason) }
            }
        }
    }

    fun remove() {
        val budgetId = state.settings.openBudgetId ?: return
        val accountId = state.bankLink?.accountId ?: return
        host.scope.launch {
            when (val result = withContext(host.io) { host.sync.unlinkBankAccount(budgetId, accountId) }) {
                BankLinkResult.Saved -> afterWrite(budgetId, leave = false)
                is BankLinkResult.Rejected -> update { it.copy(error = result.reason) }
            }
        }
    }

    private suspend fun afterWrite(
        budgetId: String,
        leave: Boolean,
    ) {
        host.mutate { it.copy(undoLabel = host.library.undoLabel(budgetId)) }
        if (leave) host.back() else load()
        withContext(host.io) { runCatching { host.sync.sendUnsent(budgetId) } }
    }

    private fun update(transform: (BankLinkUi) -> BankLinkUi) {
        host.mutate { current -> current.copy(bankLink = current.bankLink?.let(transform)) }
    }
}
