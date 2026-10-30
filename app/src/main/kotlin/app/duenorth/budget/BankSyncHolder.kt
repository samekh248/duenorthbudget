package app.duenorth.budget

import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Bank sync's own state, inside [ShellUiState.bankSync]. */
data class BankSyncState(
    /** Every SimpleFIN account is fetching, from the accounts bar or at launch. */
    val syncing: Boolean = false,
    /** What the accounts page's bank sync panel shows; null when there is nothing to show. */
    val panel: BankSyncUi? = null,
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
        update { it.copy(panel = BankSyncUi()) }
        host.scope.launch { run(id, quiet = false) }
    }

    /** Closes the tile a failed bank sync left open, and its page if that is showing. */
    fun dismiss() {
        if (state.bankSync.syncing) return
        update { it.copy(panel = null) }
        if (state.route == ShellRoute.BankSyncReport) host.back()
    }

    /** A tap on the failed sync's tile: the full page, with every account and why each failed. */
    fun openReport() {
        if (state.route != ShellRoute.BankSyncReport) host.push(ShellRoute.BankSyncReport)
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
