package app.duenorth.budget

import app.duenorth.budget.core.AssistantReport
import app.duenorth.budget.core.Finding
import app.duenorth.budget.core.FindingAction
import app.duenorth.budget.core.FindingKind
import app.duenorth.budget.core.WriteResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The assistant's own state, inside [ShellUiState.assistant]. */
data class AssistantUi(
    /** What the checks found; null until the first read lands. */
    val report: AssistantReport? = null,
    /** Kinds showing every finding rather than the first few. */
    val expanded: Set<FindingKind> = emptySet(),
    /** Findings acted on whose write has not landed yet: hidden at once, so the tap shows. */
    val settling: Set<String> = emptySet(),
)

/**
 * The assistant: reads what the checks found after every reload, and carries out the button the person taps. It
 * keeps its state in [AssistantUi] and reaches the rest of the shell through [host].
 */
class AssistantHolder internal constructor(
    private val host: ShellHost,
) {
    private val state get() = host.state.value

    private fun update(transform: (AssistantUi) -> AssistantUi) = host.mutate { it.copy(assistant = transform(it.assistant)) }

    /** Reads the checks again, on io; a newer read wins over an older one still running. */
    fun load() {
        val budgetId = state.settings.openBudgetId ?: return
        val generation = ++reads
        host.scope.launch {
            val report = withContext(host.io) { runCatching { host.library.readAssistant(budgetId) }.getOrNull() } ?: return@launch
            if (generation != reads) return@launch
            val keys = report.findings.map { it.key }.toSet()
            update { it.copy(report = report, settling = it.settling intersect keys) }
        }
    }

    private var reads = 0

    fun toggleExpanded(kind: FindingKind) {
        update { it.copy(expanded = if (kind in it.expanded) it.expanded - kind else it.expanded + kind) }
    }

    fun act(
        finding: Finding,
        action: FindingAction,
    ) {
        if (action is FindingAction.Open) {
            open(action)
            return
        }
        if (host.writing) return
        update { it.copy(settling = it.settling + finding.key) }
        host.launchWrite { id ->
            val result =
                withContext(host.io) {
                    when (action) {
                        is FindingAction.Match -> host.library.matchTransactions(id, action.bankId, action.enteredId)
                        is FindingAction.Merge -> host.library.mergeTransactions(id, action.transactionIds, false)
                        is FindingAction.MoveAndMatch -> host.library.moveAndMatch(id, action.enteredId, action.bankId)
                        is FindingAction.Delete -> host.library.deleteTransaction(id, action.transactionId, false)
                        is FindingAction.Dismiss -> host.library.dismissFinding(id, finding.key)
                        is FindingAction.Open -> WriteResult.Saved
                    }
                }
            if (result !is WriteResult.Saved) update { it.copy(settling = it.settling - finding.key) }
            host.publish(result, finding.accountId, stayOnRegister = false)
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
