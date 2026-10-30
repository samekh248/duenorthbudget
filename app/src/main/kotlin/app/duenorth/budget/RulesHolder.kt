package app.duenorth.budget

import app.duenorth.budget.core.RuleDraft
import app.duenorth.budget.core.RuleNames
import app.duenorth.budget.core.RuleSaveResult
import app.duenorth.budget.core.RuleSummary
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The rules' own state, inside [ShellUiState.rules]. */
data class RulesUi(
    val list: List<RuleSummary> = emptyList(),
    /** The rule the editor has open; null while it loads. */
    val draft: RuleDraft? = null,
)

// Spec 010 US3 (T049): rules. See contracts/schedules-rules.md.

/** Rules: the list, the rule editor, and the names and categories the editors pick from. */
class RulesHolder internal constructor(
    private val host: ShellHost,
) {
    private val state get() = host.state.value

    /** Home `•••` → rules. */
    fun open() {
        host.push(ShellRoute.Rules)
        host.mutate { it.copy(editError = null) }
        reloadList()
        loadCategories()
    }

    fun openNew() {
        host.push(ShellRoute.RuleEditor(null))
        host.mutate { it.copy(editError = null, rules = it.rules.copy(draft = RuleDraft(null, null, "and", emptyList(), emptyList()))) }
    }

    fun openEditor(ruleId: String) {
        val budgetId = state.settings.openBudgetId ?: return
        host.push(ShellRoute.RuleEditor(ruleId))
        host.mutate { it.copy(editError = null, rules = it.rules.copy(draft = null)) }
        host.scope.launch {
            val draft = withContext(host.io) { host.library.readRuleDraft(budgetId, ruleId) }
            host.mutate { it.copy(rules = it.rules.copy(draft = draft)) }
        }
    }

    fun submit(draft: RuleDraft) {
        val budgetId = state.settings.openBudgetId ?: return
        host.scope.launch {
            when (val result = withContext(host.io) { host.library.saveRule(budgetId, draft) }) {
                is RuleSaveResult.Saved -> afterWrite(budgetId, leave = true)
                is RuleSaveResult.Rejected -> host.mutate { it.copy(editError = result.reason) }
            }
        }
    }

    fun deleteOpen() {
        val budgetId = state.settings.openBudgetId ?: return
        val ruleId = (state.route as? ShellRoute.RuleEditor)?.ruleId ?: return
        host.scope.launch {
            withContext(host.io) { host.library.deleteRule(budgetId, ruleId) }
            afterWrite(budgetId, leave = true)
        }
    }

    /** Names for the rule editor: payees, accounts, and categories by id. */
    fun names(): RuleNames =
        RuleNames(
            payees =
                state.payees.rows
                    .associate { it.id to it.name },
            accounts =
                state.shell
                    ?.accounts
                    .orEmpty()
                    .associate { it.id to it.name },
            categories = state.categories.associate { it.id to it.name },
        )

    private fun reloadList() {
        val budgetId = state.settings.openBudgetId ?: return
        host.scope.launch {
            val rules = withContext(host.io) { host.library.listRules(budgetId) }
            host.mutate { it.copy(rules = it.rules.copy(list = rules)) }
        }
    }

    internal fun loadCategories() {
        val budgetId = state.settings.openBudgetId ?: return
        host.scope.launch {
            val (categories, payees) =
                withContext(host.io) { host.sync.categories(budgetId) to host.library.listPayees(budgetId).filter { !it.transfer } }
            host.mutate { it.copy(categories = categories, payees = it.payees.copy(rows = payees)) }
        }
    }

    internal fun afterWrite(
        budgetId: String,
        leave: Boolean,
    ) {
        if (leave) host.back()
        host.forgetMonths()
        host.mutate { it.copy(editError = null, undoLabel = host.library.undoLabel(budgetId)) }
        reloadList()
        host.refresh()
        host.scope.launch {
            withContext(host.io) { runCatching { host.sync.sendUnsent(budgetId) } }
            host.refresh()
        }
    }
}
