package app.duenorth.budget

import app.duenorth.budget.core.ScheduleDetails
import app.duenorth.budget.core.ScheduleDraft
import app.duenorth.budget.core.ScheduleWriteResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The schedules' own state, inside [ShellUiState.schedulesUi]. */
data class SchedulesUi(
    /** The schedule [ShellRoute.ScheduleDetails] shows; null while it loads. */
    val details: ScheduleDetails? = null,
    /** The schedules pivot item showing: [SchedulesSection.UPCOMING] or [SchedulesSection.ALL]. */
    val section: Int = 0,
    /** Upcoming shows a month calendar instead of the list. */
    val calendar: Boolean = false,
    /** The schedule whose post would duplicate a transaction, while the page asks. */
    val duplicateId: String? = null,
    /** The schedule the editor has open; null while it loads. */
    val draft: ScheduleDraft? = null,
)

// Spec 010 US3 (T049): the schedule editor. See contracts/schedules-rules.md.

/** Schedules: the upcoming and all pivot, a schedule's page and editor, and posting or skipping one. */
class SchedulesHolder internal constructor(
    private val host: ShellHost,
    private val rules: RulesHolder,
) {
    private val state get() = host.state.value

    /** Accounts → schedules: the pivot of upcoming and all schedules. */
    fun open() {
        host.push(ShellRoute.Schedules)
        host.mutate { it.copy(schedulesUi = it.schedulesUi.copy(section = SchedulesSection.UPCOMING)) }
        host.refresh()
    }

    fun setSection(index: Int) {
        host.mutate { it.copy(schedulesUi = it.schedulesUi.copy(section = index)) }
    }

    /** Schedules → calendar or list: how upcoming shows what is coming. */
    fun toggleCalendar() {
        host.mutate { it.copy(schedulesUi = it.schedulesUi.copy(calendar = !it.schedulesUi.calendar)) }
    }

    /** Schedules → tap a schedule: its own page. */
    fun openDetails(scheduleId: String) {
        val budgetId = state.settings.openBudgetId ?: return
        host.push(ShellRoute.ScheduleDetails(scheduleId))
        host.mutate { it.copy(schedulesUi = it.schedulesUi.copy(details = null)) }
        host.scope.launch {
            val details = withContext(host.io) { host.library.readScheduleDetails(budgetId, scheduleId) }
            host.mutate { current ->
                if ((current.route as? ShellRoute.ScheduleDetails)?.scheduleId ==
                    scheduleId
                ) {
                    current.copy(schedulesUi = current.schedulesUi.copy(details = details))
                } else {
                    current
                }
            }
        }
    }

    /** The schedule a details page or the editor has open. */
    private fun openId(): String? =
        when (val route = state.route) {
            is ShellRoute.ScheduleDetails -> route.scheduleId
            is ShellRoute.ScheduleEditor -> route.scheduleId
            else -> null
        }

    /** A schedule's page → edit. */
    fun editOpen() {
        openId()?.let(::openEditor)
    }

    /** A schedule's page → post: adds its next transaction now, early when it is not yet due. */
    fun postOpen() {
        openId()?.let(::post)
    }

    /** A schedule's page → skip: moves it to its next date without a transaction. */
    fun skipOpen() {
        openId()?.let(::skip)
    }

    /** Schedules `+` → new schedule; starts today in the first open on-budget account. */
    fun openNew() {
        val accounts =
            state.shell
                ?.accounts
                .orEmpty()
                .filter { !it.closed }
        val account = (accounts.firstOrNull { !it.offBudget } ?: accounts.firstOrNull())?.id.orEmpty()
        host.push(ShellRoute.ScheduleEditor(null))
        host.mutate {
            it.copy(editError = null, schedulesUi = it.schedulesUi.copy(draft = ScheduleDraft(accountId = account, startIso = it.today)))
        }
        rules.loadCategories()
    }

    /** A schedule's page → edit: the schedule editor. */
    fun openEditor(scheduleId: String) {
        val budgetId = state.settings.openBudgetId ?: return
        host.push(ShellRoute.ScheduleEditor(scheduleId))
        host.mutate { it.copy(editError = null, schedulesUi = it.schedulesUi.copy(draft = null)) }
        rules.loadCategories()
        host.scope.launch {
            val draft = withContext(host.io) { host.library.readScheduleDraft(budgetId, scheduleId) }
            host.mutate { it.copy(schedulesUi = it.schedulesUi.copy(draft = draft)) }
        }
    }

    fun submit(form: ScheduleForm) {
        val budgetId = state.settings.openBudgetId ?: return
        val base = state.schedulesUi.draft ?: return
        val decimals =
            state.shell
                ?.currency
                ?.decimals ?: 2
        val draft =
            form.toDraft(base, decimals) ?: run {
                host.mutate { it.copy(editError = ScheduleScreenCopy.ENTER_AMOUNT) }
                return
            }
        host.scope.launch {
            when (val result = withContext(host.io) { host.library.saveSchedule(budgetId, draft) }) {
                is ScheduleWriteResult.Rejected -> host.mutate { it.copy(editError = result.reason) }
                else -> rules.afterWrite(budgetId, leave = true)
            }
        }
    }

    fun deleteOpen() {
        val budgetId = state.settings.openBudgetId ?: return
        val scheduleId = openId() ?: return
        host.scope.launch {
            withContext(host.io) { host.library.deleteSchedule(budgetId, scheduleId) }
            rules.afterWrite(budgetId, leave = true)
            // Deleted from the editor: the schedule's page under it is gone too.
            if ((state.route as? ShellRoute.ScheduleDetails)?.scheduleId == scheduleId) host.back()
        }
    }

    fun post(scheduleId: String) {
        launchPost(scheduleId, forceDuplicate = false)
    }

    fun confirmDuplicate() {
        val id = state.schedulesUi.duplicateId ?: return
        host.mutate { it.copy(schedulesUi = it.schedulesUi.copy(duplicateId = null)) }
        launchPost(id, forceDuplicate = true)
    }

    fun cancelDuplicate() {
        host.mutate { it.copy(schedulesUi = it.schedulesUi.copy(duplicateId = null)) }
    }

    fun skip(scheduleId: String) {
        val budgetId = state.settings.openBudgetId ?: return
        host.scope.launch {
            when (withContext(host.io) { host.library.skipSchedule(budgetId, scheduleId) }) {
                is ScheduleWriteResult.Skipped,
                is ScheduleWriteResult.Posted,
                -> host.refresh()
                is ScheduleWriteResult.Rejected -> Unit
                is ScheduleWriteResult.ConfirmDuplicate -> Unit
            }
        }
    }

    fun createFromTransaction(transactionId: String) {
        val budgetId = state.settings.openBudgetId ?: return
        val next = state.today
        host.scope.launch {
            withContext(host.io) {
                host.library.createScheduleFromTransaction(budgetId, transactionId, "monthly", next)
            }
            host.refresh()
        }
    }

    private fun launchPost(
        scheduleId: String,
        forceDuplicate: Boolean,
    ) {
        val budgetId = state.settings.openBudgetId ?: return
        host.scope.launch {
            when (
                withContext(host.io) {
                    host.library.postSchedule(budgetId, scheduleId, forceDuplicate)
                }
            ) {
                is ScheduleWriteResult.Posted -> host.refresh()
                is ScheduleWriteResult.ConfirmDuplicate ->
                    host.mutate { it.copy(schedulesUi = it.schedulesUi.copy(duplicateId = scheduleId)) }
                is ScheduleWriteResult.Rejected -> Unit
                is ScheduleWriteResult.Skipped -> host.refresh()
            }
        }
    }
}
