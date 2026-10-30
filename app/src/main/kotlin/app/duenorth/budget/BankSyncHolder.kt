package app.duenorth.budget

import app.duenorth.budget.core.BankLinkResult
import app.duenorth.budget.core.BankSyncFailure
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Bank sync's own state, inside [ShellUiState.bankSync]. */
data class BankSyncState(
    /** Every SimpleFIN account is fetching, from the accounts bar or at launch. */
    val syncing: Boolean = false,
    /** What the accounts page's bank sync panel shows; null when there is nothing to show. */
    val panel: BankSyncUi? = null,
    /** The failed account whose unlink is waiting on a yes; null when nothing is being asked. */
    val unlinking: BankSyncFailure? = null,
)

/** Bank sync: every SimpleFIN account fetches, then the budget syncs. It reaches the rest of the shell through [host]. */
class BankSyncHolder internal constructor(
    private val host: ShellHost,
) {
    private val state get() = host.state.value

    private fun update(transform: (BankSyncState) -> BankSyncState) = host.mutate { it.copy(bankSync = transform(it.bankSync)) }

    /** The accounts bar's sync, and the panel's retry: every SimpleFIN account fetches, then the budget syncs. */
    fun syncAll() {
        val id = state.settings.openBudgetId ?: return
        if (state.bankSync.syncing || state.locked) return
        update { it.copy(panel = BankSyncUi(), unlinking = null) }
        host.scope.launch { run(id, quiet = false) }
    }

    /** Closes the tile a failed bank sync left open, and its page if that is showing. */
    fun dismiss() {
        if (state.bankSync.syncing) return
        update { it.copy(panel = null, unlinking = null) }
        if (state.route == ShellRoute.BankSyncReport) host.back()
    }

    /** A tap on the failed sync's tile: the full page, with every account and why each failed. */
    fun openReport() {
        if (state.route != ShellRoute.BankSyncReport) host.push(ShellRoute.BankSyncReport)
    }

    /** A failed account's unlink on the bank sync page: asks first, since the account stops syncing from the bank. */
    fun askUnlink(failure: BankSyncFailure) {
        if (failure.accountId.isEmpty() || state.bankSync.syncing) return
        update { it.copy(unlinking = failure) }
    }

    /** Keep on the unlink question: the link stays as it was. */
    fun keepLink() = update { it.copy(unlinking = null) }

    /**
     * Yes on the unlink question: clears the account's bank link, keeping the account and its transactions, and takes
     * it off the page. The page and its tile close once no failure is left.
     */
    fun unlink() {
        val id = state.settings.openBudgetId ?: return
        val failure = state.bankSync.unlinking ?: return
        update { it.copy(unlinking = null) }
        host.scope.launch {
            when (val result = withContext(host.io) { host.sync.unlinkBankAccount(id, failure.accountId) }) {
                BankLinkResult.Saved -> {
                    update { sync ->
                        val left =
                            sync.panel?.let { shown ->
                                shown.copy(
                                    started = shown.started - failure.accountName,
                                    failures = shown.failures.filterNot { it.accountId == failure.accountId },
                                )
                            }
                        sync.copy(panel = left?.takeIf { it.failures.isNotEmpty() || it.error != null })
                    }
                    if (state.bankSync.panel == null && state.route == ShellRoute.BankSyncReport) host.back()
                    host.mutate { it.copy(undoLabel = host.library.undoLabel(id)) }
                    host.reload()
                    withContext(host.io) { runCatching { host.sync.sendUnsent(id) } }
                }
                is BankLinkResult.Rejected -> update { sync -> sync.copy(panel = sync.panel?.copy(error = result.reason)) }
            }
        }
    }

    /**
     * Fetches every linked account, naming each in the panel as it starts and any that fail, then
     * syncs the budget so the server has what they brought. The panel stays only when something
     * failed. [quiet] leaves it closed when bank sync could not run at all (signed out, locked).
     */
    internal suspend fun run(
        id: String,
        quiet: Boolean,
    ) {
        update { it.copy(syncing = true) }
        val banks =
            withContext(host.io) {
                runCatching {
                    host.sync.syncBanks(
                        id,
                        onAccount = { account ->
                            update {
                                val shown = it.panel ?: BankSyncUi()
                                it.copy(panel = shown.copy(accountName = account.name, started = shown.started + account.name))
                            }
                        },
                        onFailure = { failure ->
                            update {
                                val shown = it.panel ?: BankSyncUi()
                                it.copy(panel = shown.copy(failures = shown.failures + failure))
                            }
                        },
                    )
                }
            }
        withContext(host.io) { runCatching { host.sync.sync(id) } }
        val summary = banks.getOrNull()
        val error =
            when {
                banks.isFailure -> BankSyncCopy.FAILED
                summary == null && !quiet -> BankSyncCopy.UNAVAILABLE
                else -> null
            }
        val failures = summary?.failures.orEmpty()
        update {
            it.copy(
                syncing = false,
                panel =
                    if (error == null && failures.isEmpty()) {
                        null
                    } else {
                        BankSyncUi(running = false, started = it.panel?.started.orEmpty(), failures = failures, error = error)
                    },
            )
        }
        host.reload()
    }
}
