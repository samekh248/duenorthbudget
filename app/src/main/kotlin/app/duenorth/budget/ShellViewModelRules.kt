package app.duenorth.budget

import androidx.lifecycle.viewModelScope
import app.duenorth.budget.core.RuleDraft
import app.duenorth.budget.core.RuleNames
import app.duenorth.budget.core.RuleSaveResult
import app.duenorth.budget.core.ScheduleDraft
import app.duenorth.budget.core.ScheduleWriteResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Spec 010 US3 (T049): schedule editor and rules, as extensions of ShellViewModel. See contracts/schedules-rules.md.

/** Home `•••` → new schedule; starts today in the first open on-budget account. */
fun ShellViewModel.openNewSchedule() {
    val accounts =
        state.value.shell
            ?.accounts
            .orEmpty()
            .filter { !it.closed }
    val account = (accounts.firstOrNull { !it.offBudget } ?: accounts.firstOrNull())?.id.orEmpty()
    push(ShellRoute.ScheduleEditor(null))
    mutate { it.copy(editError = null, scheduleDraft = ScheduleDraft(accountId = account, startIso = it.today)) }
    loadRuleCategories()
}

/** Due → tap a schedule. */
fun ShellViewModel.openSchedule(scheduleId: String) {
    val budgetId = state.value.settings.openBudgetId ?: return
    push(ShellRoute.ScheduleEditor(scheduleId))
    mutate { it.copy(editError = null, scheduleDraft = null) }
    loadRuleCategories()
    viewModelScope.launch {
        val draft = withContext(Dispatchers.IO) { library.readScheduleDraft(budgetId, scheduleId) }
        mutate { it.copy(scheduleDraft = draft) }
    }
}

fun ShellViewModel.submitSchedule(form: ScheduleForm) {
    val budgetId = state.value.settings.openBudgetId ?: return
    val base = state.value.scheduleDraft ?: return
    val decimals =
        state.value.shell
            ?.currency
            ?.decimals ?: 2
    val draft =
        form.toDraft(base, decimals) ?: run {
            mutate { it.copy(editError = ScheduleScreenCopy.ENTER_AMOUNT) }
            return
        }
    viewModelScope.launch {
        when (val result = withContext(Dispatchers.IO) { library.saveSchedule(budgetId, draft) }) {
            is ScheduleWriteResult.Rejected -> mutate { it.copy(editError = result.reason) }
            else -> afterRuleWrite(budgetId, leave = true)
        }
    }
}

fun ShellViewModel.deleteOpenSchedule() {
    val budgetId = state.value.settings.openBudgetId ?: return
    val scheduleId = (state.value.route as? ShellRoute.ScheduleEditor)?.scheduleId ?: return
    viewModelScope.launch {
        withContext(Dispatchers.IO) { library.deleteSchedule(budgetId, scheduleId) }
        afterRuleWrite(budgetId, leave = true)
    }
}

/** Home `•••` → rules. */
fun ShellViewModel.openRules() {
    push(ShellRoute.Rules)
    mutate { it.copy(editError = null) }
    reloadRules()
    loadRuleCategories()
}

fun ShellViewModel.openNewRule() {
    push(ShellRoute.RuleEditor(null))
    mutate { it.copy(editError = null, ruleDraft = RuleDraft(null, null, "and", emptyList(), emptyList())) }
}

fun ShellViewModel.openRule(ruleId: String) {
    val budgetId = state.value.settings.openBudgetId ?: return
    push(ShellRoute.RuleEditor(ruleId))
    mutate { it.copy(editError = null, ruleDraft = null) }
    viewModelScope.launch {
        val draft = withContext(Dispatchers.IO) { library.readRuleDraft(budgetId, ruleId) }
        mutate { it.copy(ruleDraft = draft) }
    }
}

fun ShellViewModel.submitRule(draft: RuleDraft) {
    val budgetId = state.value.settings.openBudgetId ?: return
    viewModelScope.launch {
        when (val result = withContext(Dispatchers.IO) { library.saveRule(budgetId, draft) }) {
            is RuleSaveResult.Saved -> afterRuleWrite(budgetId, leave = true)
            is RuleSaveResult.Rejected -> mutate { it.copy(editError = result.reason) }
        }
    }
}

fun ShellViewModel.deleteOpenRule() {
    val budgetId = state.value.settings.openBudgetId ?: return
    val ruleId = (state.value.route as? ShellRoute.RuleEditor)?.ruleId ?: return
    viewModelScope.launch {
        withContext(Dispatchers.IO) { library.deleteRule(budgetId, ruleId) }
        afterRuleWrite(budgetId, leave = true)
    }
}

/** Names for the rule editor: payees, accounts, and categories by id. */
fun ShellViewModel.ruleNames(): RuleNames =
    RuleNames(
        payees = state.value.payees.associate { it.id to it.name },
        accounts =
            state.value.shell
                ?.accounts
                .orEmpty()
                .associate { it.id to it.name },
        categories = state.value.categories.associate { it.id to it.name },
    )

private fun ShellViewModel.reloadRules() {
    val budgetId = state.value.settings.openBudgetId ?: return
    viewModelScope.launch {
        val rules = withContext(Dispatchers.IO) { library.listRules(budgetId) }
        mutate { it.copy(rulesList = rules) }
    }
}

private fun ShellViewModel.loadRuleCategories() {
    val budgetId = state.value.settings.openBudgetId ?: return
    viewModelScope.launch {
        val (categories, payees) =
            withContext(Dispatchers.IO) { sync.categories(budgetId) to library.listPayees(budgetId).filter { !it.transfer } }
        mutate { it.copy(categories = categories, payees = payees) }
    }
}

private fun ShellViewModel.afterRuleWrite(
    budgetId: String,
    leave: Boolean,
) {
    if (leave) back()
    months.invalidate()
    mutate { it.copy(editError = null, undoLabel = library.undoLabel(budgetId)) }
    reloadRules()
    refresh()
    viewModelScope.launch {
        withContext(Dispatchers.IO) { runCatching { sync.sync(budgetId) } }
        refresh()
    }
}
