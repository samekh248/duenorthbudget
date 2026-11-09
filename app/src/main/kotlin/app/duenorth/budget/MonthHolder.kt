package app.duenorth.budget

import app.duenorth.budget.core.AutoHoldChoice
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.MonthFillCopy
import app.duenorth.budget.core.MonthFillKind
import app.duenorth.budget.core.MonthFillResult
import app.duenorth.budget.core.SyncCopy
import app.duenorth.budget.core.UndoResult
import app.duenorth.budget.core.withBudgetedAssignment
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The month actions' own state, inside [ShellUiState.monthUi]. */
data class MonthUi(
    /** The fill that asks first, while it asks. */
    val fillConfirm: FillConfirm? = null,
)

// Spec 010 phase 3 (T024): month actions and undo. See contracts/month-actions.md and undo.md.

/** Month actions: fills, moving budget back to to budget, income auto-hold, hidden categories, and undo. */
class MonthHolder internal constructor(
    private val host: ShellHost,
) {
    private val state get() = host.state.value

    /** Home `•••` → show or leave out hidden groups and categories. Remembered across launches. */
    fun toggleShowHidden() {
        val settings = state.settings.let { it.copy(showHiddenCategories = !it.showHiddenCategories) }
        host.mutate { it.copy(settings = settings) }
        host.scope.launch {
            withContext(host.io) { host.library.updateSettings { it.copy(showHiddenCategories = settings.showHiddenCategories) } }
            host.refresh()
        }
    }

    /** Copy last month, 3 month average or set to zero. Asks first when [MonthFillResult.NeedsConfirm]. */
    fun fill(
        kind: MonthFillKind,
        confirmed: Boolean = false,
    ) {
        // In edit mode a fill only changes the boxes, so there is nothing to confirm.
        if (state.budgetEdit != null) {
            host.fillBudgetEdit(kind)
            return
        }
        val shell = state.shell ?: return
        val month = host.editorMonth()
        host.mutate { it.copy(monthUi = it.monthUi.copy(fillConfirm = null)) }
        host.scope.launch {
            val result = withContext(host.io) { host.sync.fillMonth(shell.budgetId, month, kind, confirmed) }
            when (result) {
                MonthFillResult.Filled -> {
                    host.toastSuccess(ToastCopy.MONTH_FILLED)
                    host.afterMonthWrite(shell.budgetId)
                }
                MonthFillResult.NothingToCopy -> host.toastFailure(ToastCopy.NOTHING_DONE, MonthFillCopy.NOTHING_TO_COPY)
                is MonthFillResult.NeedsConfirm ->
                    host.mutate {
                        it.copy(
                            monthUi = it.monthUi.copy(fillConfirm = FillConfirm(kind, result.reason)),
                        )
                    }
                is MonthFillResult.Rejected -> host.toastFailure(ToastCopy.MONTH_FILL_FAILED, result.reason)
            }
        }
    }

    fun confirmFill() {
        val pending = state.monthUi.fillConfirm ?: return
        fill(pending.kind, confirmed = true)
    }

    fun keepFill() {
        host.mutate { it.copy(monthUi = it.monthUi.copy(fillConfirm = null)) }
    }

    /** From category budget: move part of the category's budget back to to-budget. */
    fun openToBudget() {
        val categoryId = (state.route as? ShellRoute.CategoryBudget)?.categoryId ?: return
        if (state.shell?.mode != BudgetMode.ENVELOPE) return
        host.push(ShellRoute.ToBudget(categoryId))
        host.mutate { it.copy(editError = null) }
    }

    /** Paints the lower budget and higher to-budget first, and rolls back on a refusal. */
    fun submitToBudget(amountText: String) {
        val categoryId = (state.route as? ShellRoute.ToBudget)?.categoryId ?: return
        transferToBudget(categoryId, amountText)
    }

    /** Moves [amountText] of [categoryId]'s budget back to to budget, then goes back a page. Envelope only. */
    fun transferToBudget(
        categoryId: String,
        amountText: String,
    ) {
        val shell = state.shell ?: return
        val category = shell.groups.flatMap { it.categories }.firstOrNull { it.id == categoryId } ?: return
        val amount = MoneyFormat.parse(amountText, shell.currency.decimals)
        if (amount == null || amount <= 0L) {
            host.mutate { it.copy(editError = SyncCopy.ENTER_AMOUNT) }
            return
        }
        val month = host.editorMonth()
        if (amount <= category.availableMinor) {
            host.mutate {
                it.copy(
                    shell = shell.withBudgetedAssignment(categoryId, category.budgetedMinor - amount),
                    editError = null,
                )
            }
        }
        host.scope.launch {
            val result = withContext(host.io) { host.sync.transferToBudget(shell.budgetId, categoryId, month, amountText) }
            when (result) {
                MonthFillResult.Filled -> {
                    host.toastSuccess(ToastCopy.MOVED_TO_BUDGET, category.name)
                    host.back()
                    host.afterMonthWrite(shell.budgetId)
                }
                is MonthFillResult.Rejected -> {
                    host.toastFailure(ToastCopy.MOVE_FAILED, result.reason)
                    host.mutate { it.copy(shell = shell, editError = result.reason) }
                }
                is MonthFillResult.NeedsConfirm, MonthFillResult.NothingToCopy -> host.mutate { it.copy(shell = shell) }
            }
        }
    }

    /** Income auto-hold for the open income category (envelope only). */
    fun chooseAutoHold(choice: AutoHoldChoice) {
        val budgetId = state.shell?.budgetId ?: return
        val categoryId = (state.route as? ShellRoute.CategoryBudget)?.categoryId ?: return
        val month = host.editorMonth()
        host.scope.launch {
            when (val result = withContext(host.io) { host.sync.setAutoHold(budgetId, categoryId, month, choice) }) {
                MonthFillResult.Filled -> {
                    host.mutate { it.copy(editError = null) }
                    host.afterMonthWrite(budgetId)
                }
                is MonthFillResult.Rejected -> host.mutate { it.copy(editError = result.reason) }
                is MonthFillResult.NeedsConfirm, MonthFillResult.NothingToCopy -> Unit
            }
        }
    }

    /** Undoes the newest step; a conflict leaves the figures alone and says so. */
    fun undo() {
        val budgetId = state.settings.openBudgetId ?: return
        val accountId = (state.route as? ShellRoute.Register)?.accountId
        host.scope.launch {
            val result = withContext(host.io) { host.sync.undo(budgetId) }
            when (result) {
                // Undo's own toast has no undo: redo is not a step the menu offers either.
                is UndoResult.Undone -> host.toastSuccess("${MonthActionCopy.UNDONE} ${result.label}", undo = false)
                is UndoResult.Conflict -> host.toastFailure(ToastCopy.UNDO_FAILED, "${result.label}: ${MonthActionCopy.UNDO_CONFLICT}")
                UndoResult.Empty -> Unit
            }
            if (result is UndoResult.Conflict) {
                // The changed cell is listed with the server's value (contracts/undo.md).
                val conflicts = withContext(host.io) { host.sync.conflicts(budgetId) }
                host.push(ShellRoute.Conflicts)
                host.mutate { it.copy(conflicts = conflicts) }
            }
            if (result is UndoResult.Undone) {
                if (accountId != null) {
                    val register = withContext(host.io) { host.library.readRegister(budgetId, accountId) }
                    if (register != null) host.mutate { it.copy(register = register) }
                }
                host.afterMonthWrite(budgetId)
                // Schedules, a schedule's page, the assistant and reports keep their own copies, and so does
                // the rules list: read them again so the page undo was picked on shows what it put back.
                host.reload()
                if (state.route == ShellRoute.Rules) {
                    val rules = withContext(host.io) { host.library.listRules(budgetId) }
                    host.mutate { it.copy(rules = it.rules.copy(list = rules)) }
                }
            } else {
                host.mutate { it.copy(undoLabel = host.library.undoLabel(budgetId)) }
            }
        }
    }
}
