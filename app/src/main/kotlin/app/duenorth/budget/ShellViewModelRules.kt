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

/** Accounts → schedules: the pivot of upcoming and all schedules. */
fun ShellViewModel.openSchedules() {
    push(ShellRoute.Schedules)
    mutate { it.copy(schedulesSection = SchedulesSection.UPCOMING) }
    refresh()
}

fun ShellViewModel.setSchedulesSection(index: Int) {
    mutate { it.copy(schedulesSection = index) }
}

/** Schedules → calendar or list: how upcoming shows what is coming. */
fun ShellViewModel.toggleSchedulesCalendar() {
    mutate { it.copy(schedulesCalendar = !it.schedulesCalendar) }
}

/** Schedules → tap a schedule: its own page. */
fun ShellViewModel.openScheduleDetails(scheduleId: String) {
    val budgetId = state.value.settings.openBudgetId ?: return
    push(ShellRoute.ScheduleDetails(scheduleId))
    mutate { it.copy(scheduleDetails = null) }
    viewModelScope.launch {
        val details = withContext(Dispatchers.IO) { library.readScheduleDetails(budgetId, scheduleId) }
        mutate { current ->
            if ((current.route as? ShellRoute.ScheduleDetails)?.scheduleId ==
                scheduleId
            ) {
                current.copy(scheduleDetails = details)
            } else {
                current
            }
        }
    }
}

/** The schedule a details page or the editor has open. */
private fun ShellViewModel.openScheduleId(): String? =
    when (val route = state.value.route) {
        is ShellRoute.ScheduleDetails -> route.scheduleId
        is ShellRoute.ScheduleEditor -> route.scheduleId
        else -> null
    }

/** A schedule's page → edit. */
fun ShellViewModel.editOpenSchedule() {
    openScheduleId()?.let(::openSchedule)
}

/** A schedule's page → post: adds its next transaction now, early when it is not yet due. */
fun ShellViewModel.postOpenSchedule() {
    openScheduleId()?.let(::postSchedule)
}

/** A schedule's page → skip: moves it to its next date without a transaction. */
fun ShellViewModel.skipOpenSchedule() {
    openScheduleId()?.let(::skipSchedule)
}

/** Schedules `+` → new schedule; starts today in the first open on-budget account. */
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

/** A schedule's page → edit: the schedule editor. */
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
    val scheduleId = openScheduleId() ?: return
    viewModelScope.launch {
        withContext(Dispatchers.IO) { library.deleteSchedule(budgetId, scheduleId) }
        afterRuleWrite(budgetId, leave = true)
        // Deleted from the editor: the schedule's page under it is gone too.
        if ((state.value.route as? ShellRoute.ScheduleDetails)?.scheduleId == scheduleId) back()
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
