package app.duenorth.budget

import androidx.lifecycle.viewModelScope
import app.duenorth.budget.core.EditResult
import app.duenorth.budget.core.ManageGroupRow
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.SyncCopy
import app.duenorth.budget.core.withBudgetedAssignment
import app.duenorth.budget.core.withHold
import app.duenorth.budget.core.withMoveAvailable
import app.duenorth.budget.core.withReleaseHold
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth

// Categories: budgeting, moving and holding money, and managing groups and categories.

fun ShellViewModel.openCategoryBudget(categoryId: String) {
    push(ShellRoute.CategoryBudget(categoryId))
    mutate { it.copy(editError = null, categoryNote = null) }
    loadCategoryNote(categoryId)
}

fun ShellViewModel.openMoveCategory() {
    val from = (state.value.route as? ShellRoute.CategoryBudget)?.categoryId ?: return
    push(ShellRoute.MoveCategory(from))
    mutate { it.copy(editError = null) }
}

/** Opens the move screen for [categoryId] from wherever the person is, as edit mode's available figures do. */
fun ShellViewModel.openMoveFrom(categoryId: String) {
    push(ShellRoute.MoveCategory(categoryId))
    mutate { it.copy(editError = null) }
}

fun ShellViewModel.showHoldMonth() {
    push(ShellRoute.HoldMonth)
    mutate { it.copy(editError = null) }
}

fun ShellViewModel.showManageCategories() {
    push(ShellRoute.ManageCategories)
    val budgetId = state.value.shell?.budgetId ?: state.value.settings.openBudgetId
    if (budgetId == null) return
    viewModelScope.launch {
        val page = withContext(io) { sync.readCategoryManage(budgetId) }
        mutate { it.copy(editError = null, categoryManage = page) }
    }
}

fun ShellViewModel.saveCategoryBudget(amountText: String) {
    val categoryId = (state.value.route as? ShellRoute.CategoryBudget)?.categoryId ?: return
    assignBudget(categoryId, amountText)
}

/** Sets [categoryId]'s budgeted amount for the month being edited; [amountText] reads like "1234.56". */
fun ShellViewModel.assignBudget(
    categoryId: String,
    amountText: String,
) {
    val shell = state.value.shell ?: return
    val budgeted = MoneyFormat.parse(amountText, shell.currency.decimals)
    if (budgeted == null) {
        mutate { it.copy(editError = SyncCopy.ENTER_AMOUNT) }
        return
    }
    applyEnvelopeEdit(optimistic = { it.withBudgetedAssignment(categoryId, budgeted) }) {
        sync.assign(shell.budgetId, categoryId, editorMonth(), amountText)
    }
}

/** Edit mode's done: saves each changed budgeted amount for [month] as one undo step. */
fun ShellViewModel.saveBudgets(
    month: YearMonth,
    amounts: Map<String, Long>,
    templates: Boolean? = null,
) {
    val shell = state.value.shell ?: return
    applyEnvelopeEdit(optimistic = { amounts.entries.fold(it) { s, (id, minor) -> s.withBudgetedAssignment(id, minor) } }) {
        sync.assignBudgets(shell.budgetId, month, amounts, templates)
    }
}

fun ShellViewModel.toggleCategoryCarryover(enabled: Boolean) {
    val shell = state.value.shell ?: return
    val categoryId = (state.value.route as? ShellRoute.CategoryBudget)?.categoryId ?: return
    applyEnvelopeEdit {
        sync.setCategoryCarryover(shell.budgetId, categoryId, editorMonth(), enabled)
    }
}

fun ShellViewModel.submitMoveCategory(
    toCategoryId: String,
    amountText: String,
) {
    val shell = state.value.shell ?: return
    val from = (state.value.route as? ShellRoute.MoveCategory)?.fromCategoryId ?: return
    if (state.value.budgetEdit != null) {
        moveInBudgetEdit(from, toCategoryId, amountText)
        return
    }
    if (toCategoryId == MOVE_TO_BUDGET) {
        transferToBudget(from, amountText)
        return
    }
    if (toCategoryId.isBlank()) {
        mutate { it.copy(editError = SyncCopy.ENTER_AMOUNT) }
        return
    }
    val amount = MoneyFormat.parse(amountText, shell.currency.decimals)
    if (amount == null || amount <= 0L) {
        mutate { it.copy(editError = SyncCopy.ENTER_AMOUNT) }
        return
    }
    applyEnvelopeEdit(
        stayOnRoute = false,
        optimistic = { it.withMoveAvailable(from, toCategoryId, amount) },
    ) {
        sync.moveCategory(shell.budgetId, from, toCategoryId, editorMonth(), amountText)
    }
}

fun ShellViewModel.submitHold(amountText: String) {
    val shell = state.value.shell ?: return
    val amount = MoneyFormat.parse(amountText, shell.currency.decimals)
    if (amount == null) {
        mutate { it.copy(editError = SyncCopy.ENTER_AMOUNT) }
        return
    }
    applyEnvelopeEdit(optimistic = { it.withHold(amount) }) {
        sync.holdForNextMonth(shell.budgetId, editorMonth(), amountText)
    }
}

fun ShellViewModel.releaseHold() {
    val shell = state.value.shell ?: return
    applyEnvelopeEdit(optimistic = { it.withReleaseHold() }) {
        sync.releaseMonthHold(shell.budgetId, editorMonth())
    }
}

fun ShellViewModel.addCategoryGroup(name: String) {
    val budgetId = state.value.settings.openBudgetId ?: return
    if (name.isBlank()) {
        mutate { it.copy(editError = SyncCopy.ENTER_AMOUNT) }
        return
    }
    applyEnvelopeEdit {
        sync.addCategoryGroup(budgetId, name)
    }
}

fun ShellViewModel.addEnvelopeCategory(
    groupId: String,
    name: String,
) {
    val budgetId = state.value.settings.openBudgetId ?: return
    if (groupId.isBlank() || name.isBlank()) {
        mutate { it.copy(editError = SyncCopy.ENTER_AMOUNT) }
        return
    }
    applyEnvelopeEdit {
        sync.addEnvelopeCategory(budgetId, groupId, name)
    }
}

fun ShellViewModel.renameCategoryGroup(
    groupId: String,
    name: String,
) {
    val budgetId = state.value.settings.openBudgetId ?: return
    if (name.isNotBlank()) renameOnPage { group -> if (group.id == groupId) group.copy(name = name.trim()) else group }
    applyEnvelopeEdit { sync.renameCategoryGroup(budgetId, groupId, name) }
}

fun ShellViewModel.renameEnvelopeCategory(
    categoryId: String,
    name: String,
) {
    val budgetId = state.value.settings.openBudgetId ?: return
    if (name.isNotBlank()) {
        renameOnPage { group ->
            group.copy(categories = group.categories.map { if (it.id == categoryId) it.copy(name = name.trim()) else it })
        }
    }
    applyEnvelopeEdit { sync.renameEnvelopeCategory(budgetId, categoryId, name) }
}

/**
 * A rename shows on the categories page as soon as it is saved: the write and the month read after it can
 * wait behind a sync, and the row would show its old name until then. A blank name is refused, so it never shows.
 */
private fun ShellViewModel.renameOnPage(rename: (ManageGroupRow) -> ManageGroupRow) {
    mutate { current ->
        val manage = current.categoryManage ?: return@mutate current
        current.copy(categoryManage = manage.copy(groups = manage.groups.map(rename)))
    }
}

fun ShellViewModel.hideEnvelopeCategory(
    categoryId: String,
    hidden: Boolean,
) {
    val budgetId = state.value.settings.openBudgetId ?: return
    applyEnvelopeEdit { sync.hideEnvelopeCategory(budgetId, categoryId, hidden) }
}

fun ShellViewModel.deleteEnvelopeCategory(categoryId: String) {
    val budgetId = state.value.settings.openBudgetId ?: return
    applyEnvelopeEdit { sync.deleteEnvelopeCategory(budgetId, categoryId) }
}

fun ShellViewModel.deleteCategoryGroup(groupId: String) {
    val budgetId = state.value.settings.openBudgetId ?: return
    applyEnvelopeEdit { sync.deleteCategoryGroup(budgetId, groupId) }
}

fun ShellViewModel.hideCategoryGroup(
    groupId: String,
    hidden: Boolean,
) {
    val budgetId = state.value.settings.openBudgetId ?: return
    applyEnvelopeEdit { sync.hideCategoryGroup(budgetId, groupId, hidden) }
}

fun ShellViewModel.moveCategoryToGroup(
    categoryId: String,
    groupId: String,
) {
    val budgetId = state.value.settings.openBudgetId ?: return
    applyEnvelopeEdit { sync.moveCategoryToGroup(budgetId, categoryId, groupId) }
}

/** A drop from the categories page's reorder: the list moves before the write lands. */
fun ShellViewModel.placeCategoryGroup(
    groupId: String,
    targetId: String?,
) {
    val budgetId = state.value.settings.openBudgetId ?: return
    mutate { current ->
        val manage = current.categoryManage ?: return@mutate current
        current.copy(categoryManage = manage.copy(groups = placed(manage.groups, groupId, targetId) { it.id }))
    }
    applyEnvelopeEdit { sync.placeCategoryGroup(budgetId, groupId, targetId) }
}

fun ShellViewModel.placeEnvelopeCategory(
    categoryId: String,
    targetId: String?,
) {
    val budgetId = state.value.settings.openBudgetId ?: return
    mutate { current ->
        val manage = current.categoryManage ?: return@mutate current
        val groups =
            manage.groups.map { group ->
                val holds = group.categories.any { it.id == categoryId }
                if (holds) group.copy(categories = placed(group.categories, categoryId, targetId) { it.id }) else group
            }
        current.copy(categoryManage = manage.copy(groups = groups))
    }
    applyEnvelopeEdit { sync.placeEnvelopeCategory(budgetId, categoryId, targetId) }
}

/** [rows] with [id] moved just before [targetId], or last when null. */
private fun <T> ShellViewModel.placed(
    rows: List<T>,
    id: String,
    targetId: String?,
    key: (T) -> String,
): List<T> {
    val row = rows.firstOrNull { key(it) == id } ?: return rows
    val rest = rows.filter { key(it) != id }
    val to = if (targetId == null) rest.size else rest.indexOfFirst { key(it) == targetId }.takeIf { it >= 0 } ?: return rows
    return rest.toMutableList().apply { add(to, row) }
}

private fun ShellViewModel.applyEnvelopeEdit(
    stayOnRoute: Boolean = true,
    optimistic: ((MonthShell) -> MonthShell)? = null,
    block: suspend () -> EditResult,
) {
    val budgetId = state.value.shell?.budgetId ?: state.value.settings.openBudgetId ?: return
    val previousShell = state.value.shell
    if (optimistic != null && previousShell != null) {
        mutate { it.copy(shell = optimistic(previousShell), editError = null) }
    }
    viewModelScope.launch {
        val result = withContext(io) { block() }
        mutate { it.copy(undoLabel = library.undoLabel(budgetId)) }
        when (result) {
            is EditResult.Rejected -> {
                // Puts back anything the page showed before the write was refused, such as a rename.
                refreshCategoryManage(budgetId)
                val month = editorMonth()
                val shell = withContext(io) { library.readShell(budgetId, month) }
                mutate { current ->
                    current.copy(
                        editError = result.reason,
                        shell =
                            if (shell != null && !shellGate.gestureActive) {
                                shell
                            } else {
                                current.shell
                            },
                    )
                }
            }
            EditResult.Saved -> {
                mutate { it.copy(editError = null) }
                // The categories page first: it reads in a moment, while the month can take a while.
                refreshCategoryManage(budgetId)
                val month = editorMonth()
                val shell = withContext(io) { library.readShell(budgetId, month) }
                if (shell != null) {
                    shellGate.offer(shell)
                    mutate { current ->
                        current.copy(shell = if (shellGate.gestureActive) current.shell else shell)
                    }
                }
                if (!stayOnRoute) back()
                withContext(io) { runCatching { sync.sendUnsent(budgetId) } }
                val refreshed = withContext(io) { library.readShell(budgetId, month) }
                if (refreshed != null) {
                    shellGate.offer(refreshed)
                    mutate { current ->
                        current.copy(shell = if (shellGate.gestureActive) current.shell else refreshed)
                    }
                }
            }
        }
    }
}

private suspend fun ShellViewModel.refreshCategoryManage(budgetId: String) {
    if (state.value.route !is ShellRoute.ManageCategories) return
    val manage = withContext(io) { sync.readCategoryManage(budgetId) }
    mutate { it.copy(categoryManage = manage) }
}
