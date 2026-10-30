package app.duenorth.budget

import app.duenorth.budget.core.BankSyncTransport
import app.duenorth.budget.core.BudgetClock
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.HttpActualTransport
import app.duenorth.budget.core.HttpBankSyncTransport
import app.duenorth.budget.core.MemorySecretStore
import app.duenorth.budget.core.SyncCoordinator
import app.duenorth.budget.core.WriteResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking

/** A stand-in shell for holder tests: work runs at once, and pushes, backs, reloads and publishes are only recorded. */
internal class FakeShellHost(
    override val library: BudgetLibrary,
    start: ShellUiState,
    override val sync: SyncCoordinator = SyncCoordinator(library, HttpActualTransport(), MemorySecretStore(), BudgetClock.System),
    override val bankSyncTransport: BankSyncTransport = HttpBankSyncTransport(),
) : ShellHost {
    override val state = MutableStateFlow(start)
    override val scope = CoroutineScope(Dispatchers.Unconfined)
    override val io = Dispatchers.Unconfined
    override var writing = false
    val pushed = mutableListOf<ShellRoute>()
    var backs = 0
    var reloads = 0

    /** Each publish's result, with the account it was for. */
    val published = mutableListOf<Pair<WriteResult, String>>()

    override fun mutate(transform: (ShellUiState) -> ShellUiState) = state.update(transform)

    override fun push(route: ShellRoute) {
        pushed += route
    }

    override fun back() {
        backs++
    }

    override fun clearHistory() = Unit

    override fun launchWrite(block: suspend (String) -> Unit) {
        val id = state.value.settings.openBudgetId ?: return
        writing = true
        try {
            runBlocking { block(id) }
        } finally {
            writing = false
        }
    }

    override suspend fun publish(
        result: WriteResult,
        accountId: String,
        stayOnRegister: Boolean,
    ) {
        published += result to accountId
    }

    override suspend fun reload() {
        reloads++
    }

    override fun loadPayees() = Unit

    val reloadedAccounts = mutableListOf<String>()

    override suspend fun reloadAccount(accountId: String) {
        reloadedAccounts += accountId
    }
}
