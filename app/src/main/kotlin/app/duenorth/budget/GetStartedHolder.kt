package app.duenorth.budget

import app.duenorth.budget.core.EditResult
import app.duenorth.budget.core.GetStarted
import app.duenorth.budget.core.GetStartedLine
import app.duenorth.budget.core.GetStartedStep
import app.duenorth.budget.core.StarterGroup
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The open budget's get started list, or null when it has none or every line is done. */
fun ShellUiState.getStarted(): List<GetStartedLine>? = GetStarted.showing(settings, shell, schedules)

/**
 * The get started list a budget made on this phone shows over its month. Its lines open the real pages, so it keeps
 * no state of its own beyond which budgets still show it, in the settings.
 */
class GetStartedHolder internal constructor(
    private val host: ShellHost,
    private val envelopes: EnvelopesHolder,
) {
    private val state get() = host.state.value

    /** A get started line: each opens the page that does that job, so the person learns the real one. */
    fun open(step: GetStartedStep) {
        val shell = state.shell ?: return
        when (step) {
            GetStartedStep.ACCOUNTS -> host.openAddAccount()
            GetStartedStep.CATEGORIES ->
                if (shell.groups.all { it.categories.isEmpty() }) {
                    host.push(ShellRoute.StarterCategories)
                    host.mutate { it.copy(editError = null) }
                } else {
                    envelopes.showManage()
                }
            GetStartedStep.ASSIGN -> host.startBudgetEdit()
            GetStartedStep.SCHEDULE -> host.openNewSchedule()
        }
    }

    /** Get started → hide this list: the budget leaves the list behind for good. */
    fun hide() {
        val budgetId = state.shell?.budgetId ?: return
        retire(budgetId)
    }

    /** The starter categories page's add: the checked groups and their categories, then back to the month. */
    fun addStarterCategories(groups: List<StarterGroup>) {
        val budgetId = state.settings.openBudgetId ?: return
        host.scope.launch {
            when (val result = withContext(host.io) { host.sync.addStarterCategories(budgetId, groups) }) {
                is EditResult.Rejected -> host.mutate { it.copy(editError = result.reason) }
                else -> {
                    host.back()
                    host.forgetMonths()
                    host.mutate { it.copy(editError = null, undoLabel = host.library.undoLabel(budgetId)) }
                    host.reload()
                    withContext(host.io) { runCatching { host.sync.sendUnsent(budgetId) } }
                }
            }
        }
    }

    /** A budget just made on this phone starts its get started list, and opens on the budget item where it shows. */
    internal suspend fun start(budgetId: String) {
        withContext(host.io) {
            host.library.updateSettings { it.copy(getStarted = (it.getStarted - budgetId) + budgetId) }
        }
        host.mutate { it.copy(homeSection = ScreenBar.HOME_BUDGET) }
    }

    /** Once every line is done the list goes for good, so removing a schedule later cannot bring it back. */
    internal fun retireFinished() {
        val current = state
        val shell = current.shell ?: return
        if (shell.budgetId !in current.settings.getStarted) return
        if (GetStarted.lines(shell, current.schedules).all { it.done }) retire(shell.budgetId)
    }

    private fun retire(budgetId: String) {
        host.mutate { it.copy(settings = it.settings.copy(getStarted = it.settings.getStarted - budgetId)) }
        host.scope.launch {
            withContext(host.io) { host.library.updateSettings { it.copy(getStarted = it.getStarted - budgetId) } }
        }
    }
}
