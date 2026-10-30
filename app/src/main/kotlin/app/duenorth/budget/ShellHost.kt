package app.duenorth.budget

import app.duenorth.budget.core.BankSyncTransport
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.SyncCoordinator
import app.duenorth.budget.core.WriteResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * What a feature holder (such as [InboxHolder]) needs from the shell: the shared state, navigation, the
 * one-at-a-time write and reloads. [ShellViewModel] is the real one; a test can pass its own, so a holder runs
 * without the whole app.
 */
internal interface ShellHost {
    val state: StateFlow<ShellUiState>

    /** Where a holder's work runs; cancelled with the view model. */
    val scope: CoroutineScope
    val library: BudgetLibrary
    val sync: SyncCoordinator
    val bankSyncTransport: BankSyncTransport

    /** Where file and network work runs. */
    val io: CoroutineDispatcher

    /** True while a write runs; [launchWrite] drops another until it ends. */
    val writing: Boolean

    fun mutate(transform: (ShellUiState) -> ShellUiState)

    /** Opens [route] over the current page; back returns to it. */
    fun push(route: ShellRoute)

    /** Back, as the system back button does it. */
    fun back()

    /** Forgets the pages back would return to, for a page opened as a new root. */
    fun clearHistory()

    /** Runs [block] with the open budget's id as the one write in flight, unless one already runs. */
    fun launchWrite(block: suspend (String) -> Unit)

    /** Shows what a write did: reloads after a save, or shows its error or the question it asks. */
    suspend fun publish(
        result: WriteResult,
        accountId: String,
        stayOnRegister: Boolean,
    )

    /** Reads the open budget again and shows it. */
    suspend fun reload()

    /** Reads the payees, for a transaction form about to open. */
    fun loadPayees()

    /** Reloads the shell with [accountId]'s register, for a page about to show it. */
    suspend fun reloadAccount(accountId: String)
}
