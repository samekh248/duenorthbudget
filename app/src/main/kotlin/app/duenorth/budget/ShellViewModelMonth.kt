package app.duenorth.budget

import androidx.lifecycle.viewModelScope
import app.duenorth.budget.core.AutoHoldChoice
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.MonthFillCopy
import app.duenorth.budget.core.MonthFillKind
import app.duenorth.budget.core.MonthFillResult
import app.duenorth.budget.core.SyncCopy
import app.duenorth.budget.core.UndoResult
import app.duenorth.budget.core.withBudgetedAssignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Spec 010 phase 3 (T024): month actions and undo for the shell, as extensions of ShellViewModel. See contracts/month-actions.md and undo.md.

/** Home `•••` → show or leave out hidden groups and categories. Remembered across launches. */
fun ShellViewModel.toggleShowHiddenCategories() {
    val settings = state.value.settings.let { it.copy(showHiddenCategories = !it.showHiddenCategories) }
    mutate { it.copy(settings = settings) }
    viewModelScope.launch {
        withContext(io) { library.updateSettings { it.copy(showHiddenCategories = settings.showHiddenCategories) } }
        refresh()
    }
}

/** Copy last month, 3 month average or set to zero. Asks first when [MonthFillResult.NeedsConfirm]. */
fun ShellViewModel.fillMonth(
    kind: MonthFillKind,
    confirmed: Boolean = false,
) {
    // In edit mode a fill only changes the boxes, so there is nothing to confirm.
    if (state.value.budgetEdit != null) {
        fillBudgetEdit(kind)
        return
    }
    val shell = state.value.shell ?: return
    val month = editorMonth()
    mutate { it.copy(monthNotice = null, fillConfirm = null) }
    viewModelScope.launch {
        val result = withContext(io) { sync.fillMonth(shell.budgetId, month, kind, confirmed) }
        when (result) {
            MonthFillResult.Filled -> afterMonthWrite(shell.budgetId)
            MonthFillResult.NothingToCopy -> mutate { it.copy(monthNotice = MonthFillCopy.NOTHING_TO_COPY) }
            is MonthFillResult.NeedsConfirm -> mutate { it.copy(fillConfirm = FillConfirm(kind, result.reason)) }
            is MonthFillResult.Rejected -> mutate { it.copy(monthNotice = result.reason) }
        }
    }
}

fun ShellViewModel.confirmFill() {
    val pending = state.value.fillConfirm ?: return
    fillMonth(pending.kind, confirmed = true)
}

fun ShellViewModel.keepFill() {
    mutate { it.copy(fillConfirm = null) }
}

/** From category budget: move part of the category's budget back to to-budget. */
fun ShellViewModel.openToBudget() {
    val categoryId = (state.value.route as? ShellRoute.CategoryBudget)?.categoryId ?: return
    if (state.value.shell?.mode != BudgetMode.ENVELOPE) return
    push(ShellRoute.ToBudget(categoryId))
    mutate { it.copy(editError = null) }
}

/** Paints the lower budget and higher to-budget first, and rolls back on a refusal. */
fun ShellViewModel.submitToBudget(amountText: String) {
    val categoryId = (state.value.route as? ShellRoute.ToBudget)?.categoryId ?: return
    transferToBudget(categoryId, amountText)
}

/** Moves [amountText] of [categoryId]'s budget back to to budget, then goes back a page. Envelope only. */
fun ShellViewModel.transferToBudget(
    categoryId: String,
    amountText: String,
) {
    val shell = state.value.shell ?: return
    val category = shell.groups.flatMap { it.categories }.firstOrNull { it.id == categoryId } ?: return
    val amount = MoneyFormat.parse(amountText, shell.currency.decimals)
    if (amount == null || amount <= 0L) {
        mutate { it.copy(editError = SyncCopy.ENTER_AMOUNT) }
        return
    }
    val month = editorMonth()
    if (amount <= category.availableMinor) {
        mutate {
            it.copy(
                shell = shell.withBudgetedAssignment(categoryId, category.budgetedMinor - amount),
                editError = null,
            )
        }
    }
    viewModelScope.launch {
        val result = withContext(io) { sync.transferToBudget(shell.budgetId, categoryId, month, amountText) }
        when (result) {
            MonthFillResult.Filled -> {
                back()
                afterMonthWrite(shell.budgetId)
            }
            is MonthFillResult.Rejected -> mutate { it.copy(shell = shell, editError = result.reason) }
            is MonthFillResult.NeedsConfirm, MonthFillResult.NothingToCopy -> mutate { it.copy(shell = shell) }
        }
    }
}

/** Income auto-hold for the open income category (envelope only). */
fun ShellViewModel.chooseAutoHold(choice: AutoHoldChoice) {
    val budgetId = state.value.shell?.budgetId ?: return
    val categoryId = (state.value.route as? ShellRoute.CategoryBudget)?.categoryId ?: return
    val month = editorMonth()
    viewModelScope.launch {
        when (val result = withContext(io) { sync.setAutoHold(budgetId, categoryId, month, choice) }) {
            MonthFillResult.Filled -> {
                mutate { it.copy(editError = null) }
                afterMonthWrite(budgetId)
            }
            is MonthFillResult.Rejected -> mutate { it.copy(editError = result.reason) }
            is MonthFillResult.NeedsConfirm, MonthFillResult.NothingToCopy -> Unit
        }
    }
}

/** Undoes the newest step; a conflict leaves the figures alone and says so. */
fun ShellViewModel.undo() {
    val budgetId = state.value.settings.openBudgetId ?: return
    val accountId = (state.value.route as? ShellRoute.Register)?.accountId
    viewModelScope.launch {
        val result = withContext(io) { sync.undo(budgetId) }
        val notice =
            when (result) {
                is UndoResult.Undone -> "${MonthActionCopy.UNDONE} ${result.label}"
                is UndoResult.Conflict -> "${result.label}: ${MonthActionCopy.UNDO_CONFLICT}"
                UndoResult.Empty -> null
            }
        mutate { it.copy(monthNotice = notice) }
        if (result is UndoResult.Conflict) {
            // The changed cell is listed with the server's value (contracts/undo.md).
            val conflicts = withContext(io) { sync.conflicts(budgetId) }
            push(ShellRoute.Conflicts)
            mutate { it.copy(conflicts = conflicts) }
        }
        if (result is UndoResult.Undone) {
            if (accountId != null) {
                val register = withContext(io) { library.readRegister(budgetId, accountId) }
                if (register != null) mutate { it.copy(register = register) }
            }
            afterMonthWrite(budgetId)
        } else {
            mutate { it.copy(undoLabel = library.undoLabel(budgetId)) }
        }
    }
}

/**
 * Repaints the open month after a month action or undo, drops other cached months
 * (a fill or hold changes every later month), then syncs and repaints again.
 */
internal suspend fun ShellViewModel.afterMonthWrite(budgetId: String) {
    repaintMonth(budgetId)
    withContext(io) { runCatching { sync.sendUnsent(budgetId) } }
    repaintMonth(budgetId)
}

private suspend fun ShellViewModel.repaintMonth(budgetId: String) {
    val month = editorMonth()
    val loaded =
        withContext(io) {
            val shell = library.readShell(budgetId, month) ?: return@withContext null
            MonthSnapshot(month, shell, library.readMonthReview(budgetId, month))
        }
    months.invalidate()
    if (loaded != null) {
        shellGate.offer(loaded.shell)
        if (!shellGate.gestureActive) months.applyLoaded(loaded, gestureActive = false)
    }
    mutate { current ->
        val open = loaded != null && !shellGate.gestureActive
        current.copy(
            shell = if (open) loaded!!.shell else current.shell,
            monthReview = if (open && current.pinnedMonth == null) loaded!!.review else current.monthReview,
            undoLabel = library.undoLabel(budgetId),
        )
    }
    warmMonthWindow()
}
