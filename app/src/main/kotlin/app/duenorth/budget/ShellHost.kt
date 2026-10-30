package app.duenorth.budget

import app.duenorth.budget.core.BankSyncTransport
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.CreateResult
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.OnboardingProgress
import app.duenorth.budget.core.RefreshGate
import app.duenorth.budget.core.ServerProbe
import app.duenorth.budget.core.SyncCoordinator
import app.duenorth.budget.core.WriteResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import java.time.YearMonth

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
    val probe: ServerProbe

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

    /** Sets the pages back returns through to [routes], the first one last. */
    fun replaceHistory(routes: List<ShellRoute>)

    /** Forgets the page back would return to next. */
    fun dropLastHistory()

    /** The add account page, or its first step. */
    fun openAddAccount()

    /** Ends first run, once a budget is made or opened. */
    fun finishOnboarding()

    /** Starts the get started list for a budget made empty. */
    suspend fun startGetStarted(budgetId: String)

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

    /** Opens [route] from settings, so back and finish return to [page]. */
    fun openFromSettings(
        route: ShellRoute,
        page: SettingsPage,
    )

    /** Back to settings, on [page] or the page it was on. */
    fun returnToSettings(page: SettingsPage? = null)

    /** Signs out of the server; [preferSettings] lands on settings rather than where sign out started. */
    fun signOut(preferSettings: Boolean)

    /** Asks the server for its budgets again, for the pick-a-budget page. */
    fun refreshOnboardingFiles()

    /** First run's end of a browser sign-in: on to the server's budgets, or back to sign in with [error]. */
    fun finishBrowserSignIn(
        saved: OnboardingProgress,
        error: String?,
    )

    /** Leaves the budget's edit mode without saving. */
    fun cancelBudgetEdit()

    /** Makes a budget the way this build does: empty, or from the walkthrough sample in debug builds. */
    fun createBudget(
        name: String,
        currency: String,
    ): CreateResult

    /** Reschedules the background sync job for a new interval. */
    fun onBackgroundSyncHours(hours: Int)

    /** The month the budget editors write to: the pinned month, or the open one. */
    fun editorMonth(): YearMonth

    /** Holds month reads back while a gesture runs, so a swipe never jumps. */
    val shellGate: RefreshGate<MonthShell>

    /** Reads [categoryId]'s note, for its templates. */
    fun loadCategoryNote(categoryId: String)

    /** Edit mode's move: changes the amounts being edited rather than writing. */
    fun moveInBudgetEdit(
        fromCategoryId: String,
        toCategoryId: String,
        amountText: String,
    )

    /** Moves [amountText] from [categoryId] back to to-budget. */
    fun transferToBudget(
        categoryId: String,
        amountText: String,
    )

    /** Reloads the shell with [accountId]'s register, for a page about to show it. */
    suspend fun reloadAccount(accountId: String)
}
