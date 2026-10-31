package app.duenorth.budget

import app.duenorth.budget.core.AssistantCopy
import app.duenorth.budget.core.AssistantReport
import app.duenorth.budget.core.Finding
import app.duenorth.budget.core.FindingAction
import app.duenorth.budget.core.FindingKind
import app.duenorth.budget.core.PayeeWriteResult
import app.duenorth.budget.core.ScheduleWriteResult
import app.duenorth.budget.core.WriteResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The assistant's own state, inside [ShellUiState.assistant]. */
data class AssistantUi(
    /** What the checks found; null until the first read lands. */
    val report: AssistantReport? = null,
    /** Kinds showing every finding rather than the first few. */
    val expanded: Set<FindingKind> = emptySet(),
    /** Findings acted on whose write has not landed yet: hidden at once, so the tap shows, except the [working] one. */
    val settling: Set<String> = emptySet(),
    /** The finding whose fix is saving now, shown with what it is doing in place of its buttons. */
    val working: Working? = null,
    /** A refresh the person asked for is still reading the checks again. */
    val refreshing: Boolean = false,
) {
    data class Working(
        val key: String,
        val label: String,
    )
}

/**
 * The assistant: reads what the checks found after every reload, and carries out the button the person taps. It
 * keeps its state in [AssistantUi] and reaches the rest of the shell through [host].
 */
class AssistantHolder internal constructor(
    private val host: ShellHost,
    private val register: RegisterHolder,
    private val reconcile: ReconcileHolder,
    private val bankSync: BankSyncHolder,
    private val envelopes: EnvelopesHolder,
    private val schedules: SchedulesHolder,
    private val budgetFiles: BudgetFilesHolder,
) {
    private val state get() = host.state.value

    private fun update(transform: (AssistantUi) -> AssistantUi) = host.mutate { it.copy(assistant = transform(it.assistant)) }

    /** Reads the checks again, on io; a newer read wins over an older one still running. */
    fun load() {
        val budgetId = state.settings.openBudgetId ?: return
        val generation = ++reads
        host.scope.launch {
            val report = withContext(host.io) { runCatching { host.library.readAssistant(budgetId) }.getOrNull() }
            if (generation != reads) return@launch
            if (report == null) return@launch update { it.copy(refreshing = false) }
            val keys = report.findings.map { it.key }.toSet()
            // A finished write's finding is gone from the new read; one a bank sync is still working on stays hidden.
            val syncing = host.state.value.bankSync.syncing
            update {
                it.copy(
                    report = report,
                    settling = if (syncing || host.writing) it.settling intersect keys else emptySet(),
                    // Gone from the read means the fix landed; still there means a read from before it, while it saves.
                    working = it.working?.takeIf { working -> working.key in keys && host.writing },
                    refreshing = false,
                )
            }
        }
    }

    /**
     * The refresh button: with a server budget it first pulls in what other devices sent, as the inbox's sync does,
     * and the reload after it reads the checks again; a budget on this phone only reads the checks again.
     */
    fun refresh() {
        if (state.settings.openBudgetId == null || state.syncing || state.assistant.refreshing) return
        update { it.copy(refreshing = true) }
        if (state.signedIn && !state.cloudFileId.isNullOrBlank()) budgetFiles.syncInbox() else load()
    }

    private var reads = 0

    fun toggleExpanded(kind: FindingKind) {
        update { it.copy(expanded = if (kind in it.expanded) it.expanded - kind else it.expanded + kind) }
    }

    fun act(
        finding: Finding,
        action: FindingAction,
    ) {
        when (action) {
            is FindingAction.Open -> return open(action)
            is FindingAction.OpenAccount -> return register.openAccount(action.accountId)
            is FindingAction.Reconcile -> {
                register.openAccount(action.accountId)
                reconcile.open()
                return
            }
            is FindingAction.BudgetCategory -> return envelopes.openBudget(action.categoryId)
            FindingAction.HoldForNext -> return envelopes.showHold()
            FindingAction.OpenSchedules -> return schedules.open()
            FindingAction.SyncBank -> {
                // Hidden while the sync runs; the reload after it shows the account again if it is still behind.
                update { it.copy(settling = it.settling + finding.key) }
                return bankSync.syncAll()
            }
            else -> Unit
        }
        if (host.writing) return
        update { it.copy(settling = it.settling + finding.key, working = AssistantUi.Working(finding.key, AssistantCopy.working(action))) }
        host.launchWrite { id ->
            val result =
                try {
                    withContext(host.io) {
                        when (action) {
                            is FindingAction.Match -> host.library.matchTransactions(id, action.bankId, action.enteredId)
                            is FindingAction.Merge -> host.library.mergeTransactions(id, action.transactionIds, false)
                            is FindingAction.MoveAndMatch -> host.library.moveAndMatch(id, action.enteredId, action.bankId)
                            is FindingAction.Delete -> host.library.deleteTransaction(id, action.transactionId, false)
                            is FindingAction.Dismiss -> host.library.dismissFinding(id, finding.key)
                            is FindingAction.MoveTo -> host.library.moveTransaction(id, action.transactionId, action.accountId)
                            is FindingAction.AdoptAndMatch -> host.library.adoptAndMatch(id, action.enteredId, action.bankId)
                            is FindingAction.LinkTransfer -> host.library.linkTransfer(id, action.fromId, action.toId)
                            is FindingAction.MakeSchedule ->
                                host.library
                                    .createScheduleFromTransaction(
                                        id,
                                        action.transactionId,
                                        "monthly",
                                        action.nextDateIso,
                                    ).asWrite()
                            is FindingAction.PostSchedule -> host.library.postSchedule(id, action.scheduleId).asWrite()
                            is FindingAction.SkipSchedule -> host.library.skipSchedule(id, action.scheduleId).asWrite()
                            is FindingAction.UpdateScheduleAmount ->
                                host.library.updateScheduleAmount(id, action.scheduleId, action.amountMinor).asWrite()
                            is FindingAction.MakeRule -> host.library.makeCategoryRule(id, action.payeeId, action.categoryId)
                            is FindingAction.MergePayees ->
                                host.library
                                    .renamePayee(
                                        id,
                                        action.payeeId,
                                        action.intoName,
                                        confirmMerge = true,
                                        rememberRule = true,
                                    ).asWrite()
                            is FindingAction.Open,
                            is FindingAction.OpenAccount,
                            is FindingAction.Reconcile,
                            FindingAction.SyncBank,
                            is FindingAction.BudgetCategory,
                            FindingAction.HoldForNext,
                            FindingAction.OpenSchedules,
                            -> WriteResult.Saved
                        }
                    }
                } catch (error: Throwable) {
                    update { it.copy(settling = it.settling - finding.key, working = null) }
                    throw error
                }
            // A saved fix keeps its working line until the next read of the checks takes the finding away.
            if (result !is WriteResult.Saved) update { it.copy(settling = it.settling - finding.key, working = null) }
            // A budget finding belongs to no account: a save just reads the budget again.
            if (finding.accountId.isBlank() && result is WriteResult.Saved) {
                host.reload()
            } else {
                host.publish(result, finding.accountId, stayOnRegister = false)
            }
        }
    }

    /** Opens the transaction's form; saving it comes back to the assistant. */
    private fun open(action: FindingAction.Open) {
        host.push(ShellRoute.EditTransaction(action.accountId, action.transactionId))
        host.mutate { it.copy(editFromHome = true, registerUi = it.registerUi.copy(selection = null)) }
        host.loadPayees()
        host.scope.launch { host.reloadAccount(action.accountId) }
    }
}

/** A schedule write as the assistant shows it; a duplicate it would post is refused with the reason. */
private fun ScheduleWriteResult.asWrite(): WriteResult =
    when (this) {
        ScheduleWriteResult.Posted, ScheduleWriteResult.Skipped -> WriteResult.Saved
        is ScheduleWriteResult.Rejected -> WriteResult.Rejected(reason)
        is ScheduleWriteResult.ConfirmDuplicate -> WriteResult.Rejected(reason)
    }

private fun PayeeWriteResult.asWrite(): WriteResult =
    when (this) {
        PayeeWriteResult.Saved -> WriteResult.Saved
        is PayeeWriteResult.Rejected -> WriteResult.Rejected(reason)
        is PayeeWriteResult.ConfirmMerge -> WriteResult.Rejected(reason)
    }
