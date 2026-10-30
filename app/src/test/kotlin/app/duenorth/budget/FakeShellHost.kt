package app.duenorth.budget

import app.duenorth.budget.core.BankSyncTransport
import app.duenorth.budget.core.BudgetClock
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.CreateResult
import app.duenorth.budget.core.HttpActualTransport
import app.duenorth.budget.core.HttpBankSyncTransport
import app.duenorth.budget.core.MemorySecretStore
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.OnboardingProgress
import app.duenorth.budget.core.RefreshGate
import app.duenorth.budget.core.ServerProbe
import app.duenorth.budget.core.SyncCoordinator
import app.duenorth.budget.core.WriteResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import java.time.YearMonth

/** A stand-in shell for holder tests: work runs at once, and pushes, backs, reloads and publishes are only recorded. */
internal class FakeShellHost(
    override val library: BudgetLibrary,
    start: ShellUiState,
    override val sync: SyncCoordinator = SyncCoordinator(library, HttpActualTransport(), MemorySecretStore(), BudgetClock.System),
    override val bankSyncTransport: BankSyncTransport = HttpBankSyncTransport(),
    override val probe: ServerProbe = ServerProbe(),
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

    val histories = mutableListOf<List<ShellRoute>>()

    override fun replaceHistory(routes: List<ShellRoute>) {
        histories += routes
    }

    override fun dropLastHistory() = Unit

    override fun openAddAccount() = push(ShellRoute.AddAccount)

    var onboardingFinished = 0

    override fun finishOnboarding() {
        onboardingFinished++
    }

    val getStarted = mutableListOf<String>()

    override fun startBudgetEdit() = Unit

    override fun applyTemplatesInBudgetEdit(overwrite: Boolean) = Unit

    override suspend fun afterMonthWrite(budgetId: String) = Unit

    val replaced = mutableListOf<ShellRoute>()

    override fun replaceRoute(route: ShellRoute) {
        replaced += route
    }

    override fun openNewSchedule() = Unit

    override fun forgetMonths() = Unit

    override suspend fun startGetStarted(budgetId: String) {
        getStarted += budgetId
    }

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

    override fun openFromSettings(
        route: ShellRoute,
        page: SettingsPage,
    ) = push(route)

    override fun returnToSettings(page: SettingsPage?) {
        mutate { it.copy(route = ShellRoute.Settings) }
    }

    var signOuts = 0

    override fun signOut(preferSettings: Boolean) {
        signOuts++
    }

    override fun refreshOnboardingFiles() = Unit

    override fun finishBrowserSignIn(
        saved: OnboardingProgress,
        error: String?,
    ) = Unit

    var budgetEditsCancelled = 0

    override fun cancelBudgetEdit() {
        budgetEditsCancelled++
    }

    override fun createBudget(
        name: String,
        currency: String,
    ): CreateResult = library.create(name, currency)

    val backgroundSyncHours = mutableListOf<Int>()

    override fun onBackgroundSyncHours(hours: Int) {
        backgroundSyncHours += hours
    }

    override fun setNetWorthIncludeOffBudget(include: Boolean) {
        mutate { it.copy(settings = it.settings.copy(netWorthIncludeOffBudget = include)) }
    }

    var month: YearMonth = YearMonth.now()

    override fun editorMonth() = month

    override val shellGate = RefreshGate<MonthShell>()

    override fun loadCategoryNote(categoryId: String) = Unit

    val budgetEditMoves = mutableListOf<Triple<String, String, String>>()

    override fun moveInBudgetEdit(
        fromCategoryId: String,
        toCategoryId: String,
        amountText: String,
    ) {
        budgetEditMoves += Triple(fromCategoryId, toCategoryId, amountText)
    }

    override fun transferToBudget(
        categoryId: String,
        amountText: String,
    ) = Unit

    override fun loadPayees() = Unit

    val reloadedAccounts = mutableListOf<String>()

    override suspend fun reloadAccount(accountId: String) {
        reloadedAccounts += accountId
    }
}
