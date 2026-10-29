package app.duenorth.budget

import androidx.lifecycle.viewModelScope
import app.duenorth.budget.core.EditResult
import app.duenorth.budget.core.GetStarted
import app.duenorth.budget.core.GetStartedLine
import app.duenorth.budget.core.GetStartedStep
import app.duenorth.budget.core.StarterGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// The get started list a budget made on this phone shows over its month, as extensions of ShellViewModel.

/** The open budget's get started list, or null when it has none or every line is done. */
fun ShellUiState.getStarted(): List<GetStartedLine>? = GetStarted.showing(settings, shell, schedules)

/** A get started line: each opens the page that does that job, so the person learns the real one. */
fun ShellViewModel.openGetStarted(step: GetStartedStep) {
    val shell = state.value.shell ?: return
    when (step) {
        GetStartedStep.ACCOUNTS -> openAddAccount()
        GetStartedStep.CATEGORIES ->
            if (shell.groups.all { it.categories.isEmpty() }) {
                push(ShellRoute.StarterCategories)
                mutate { it.copy(editError = null) }
            } else {
                showManageCategories()
            }
        GetStartedStep.ASSIGN -> startBudgetEdit()
        GetStartedStep.SCHEDULE -> openNewSchedule()
    }
}

/** Get started → hide this list: the budget leaves the list behind for good. */
fun ShellViewModel.hideGetStarted() {
    val budgetId = state.value.shell?.budgetId ?: return
    retireGetStarted(budgetId)
}

/** The starter categories page's add: the checked groups and their categories, then back to the month. */
fun ShellViewModel.addStarterCategories(groups: List<StarterGroup>) {
    val budgetId = state.value.settings.openBudgetId ?: return
    viewModelScope.launch {
        when (val result = withContext(Dispatchers.IO) { sync.addStarterCategories(budgetId, groups) }) {
            is EditResult.Rejected -> mutate { it.copy(editError = result.reason) }
            else -> {
                back()
                months.invalidate()
                mutate { it.copy(editError = null, undoLabel = library.undoLabel(budgetId)) }
                refresh()
                withContext(Dispatchers.IO) { runCatching { sync.sendUnsent(budgetId) } }
            }
        }
    }
}

/** A budget just made on this phone starts its get started list, and opens on the budget item where it shows. */
internal suspend fun ShellViewModel.startGetStarted(budgetId: String) {
    withContext(Dispatchers.IO) {
        library.updateSettings { it.copy(getStarted = (it.getStarted - budgetId) + budgetId) }
    }
    mutate { it.copy(homeSection = ScreenBar.HOME_BUDGET) }
}

/** Once every line is done the list goes for good, so removing a schedule later cannot bring it back. */
internal fun ShellViewModel.retireFinishedGetStarted() {
    val current = state.value
    val shell = current.shell ?: return
    if (shell.budgetId !in current.settings.getStarted) return
    if (GetStarted.lines(shell, current.schedules).all { it.done }) retireGetStarted(shell.budgetId)
}

private fun ShellViewModel.retireGetStarted(budgetId: String) {
    mutate { it.copy(settings = it.settings.copy(getStarted = it.settings.getStarted - budgetId)) }
    viewModelScope.launch {
        withContext(Dispatchers.IO) { library.updateSettings { it.copy(getStarted = it.getStarted - budgetId) } }
    }
}
