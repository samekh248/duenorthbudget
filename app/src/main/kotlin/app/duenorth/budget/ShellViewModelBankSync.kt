package app.duenorth.budget

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Bank sync: every SimpleFIN account fetches, then the budget syncs.

/** The accounts bar's sync, and the panel's retry: every SimpleFIN account fetches, then the budget syncs. */
fun ShellViewModel.syncBanks() {
    val id = state.value.settings.openBudgetId ?: return
    if (state.value.bankSyncing || state.value.locked) return
    mutate { it.copy(bankSync = BankSyncUi()) }
    viewModelScope.launch { runBankSync(id, quiet = false) }
}

/** Closes the tile a failed bank sync left open, and its page if that is showing. */
fun ShellViewModel.dismissBankSync() {
    if (state.value.bankSyncing) return
    mutate { it.copy(bankSync = null) }
    if (state.value.route == ShellRoute.BankSyncReport) back()
}

/** A tap on the failed sync's tile: the full page, with every account and why each failed. */
fun ShellViewModel.openBankSyncReport() {
    if (state.value.route != ShellRoute.BankSyncReport) push(ShellRoute.BankSyncReport)
}

/**
 * Fetches every linked account, naming each in the panel as it starts and any that fail, then
 * syncs the budget so the server has what they brought. The panel stays only when something
 * failed. [quiet] leaves it closed when bank sync could not run at all (signed out, locked).
 */
internal suspend fun ShellViewModel.runBankSync(
    id: String,
    quiet: Boolean,
) {
    mutate { it.copy(bankSyncing = true) }
    val banks =
        withContext(io) {
            runCatching {
                sync.syncBanks(
                    id,
                    onAccount = { account ->
                        mutate {
                            val shown = it.bankSync ?: BankSyncUi()
                            it.copy(bankSync = shown.copy(accountName = account.name, started = shown.started + account.name))
                        }
                    },
                    onFailure = { failure ->
                        mutate {
                            val shown = it.bankSync ?: BankSyncUi()
                            it.copy(bankSync = shown.copy(failures = shown.failures + failure))
                        }
                    },
                )
            }
        }
    withContext(io) { runCatching { sync.sync(id) } }
    val summary = banks.getOrNull()
    val error =
        when {
            banks.isFailure -> BankSyncCopy.FAILED
            summary == null && !quiet -> BankSyncCopy.UNAVAILABLE
            else -> null
        }
    val failures = summary?.failures.orEmpty()
    mutate {
        it.copy(
            bankSyncing = false,
            bankSync =
                if (error == null && failures.isEmpty()) {
                    null
                } else {
                    BankSyncUi(running = false, started = it.bankSync?.started.orEmpty(), failures = failures, error = error)
                },
        )
    }
    reload()
}
