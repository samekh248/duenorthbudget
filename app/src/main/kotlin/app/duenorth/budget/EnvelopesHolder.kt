package app.duenorth.budget

import app.duenorth.budget.core.CategoryManagePage
import app.duenorth.budget.core.EditResult
import app.duenorth.budget.core.ManageGroupRow
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.SyncCopy
import app.duenorth.budget.core.withBudgetedAssignment
import app.duenorth.budget.core.withHold
import app.duenorth.budget.core.withMoveAvailable
import app.duenorth.budget.core.withReleaseHold
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth

/** The envelopes' own state, inside [ShellUiState.envelopes]. */
data class EnvelopesUi(
    /** The categories page: groups and categories to rename, hide, move and reorder; null until read. */
    val manage: CategoryManagePage? = null,
)

/** Envelopes: budgeting into categories, moving and holding money, and managing groups and categories. */
class EnvelopesHolder internal constructor(
    private val host: ShellHost,
) {
    private val state get() = host.state.value

    private fun update(transform: (EnvelopesUi) -> EnvelopesUi) = host.mutate { it.copy(envelopes = transform(it.envelopes)) }

    fun openBudget(categoryId: String) {
        host.push(ShellRoute.CategoryBudget(categoryId))
        host.mutate { it.copy(editError = null, templates = it.templates.copy(note = null, automations = null)) }
        host.loadCategoryNote(categoryId)
    }

    fun openMove() {
        val from = (state.route as? ShellRoute.CategoryBudget)?.categoryId ?: return
        host.push(ShellRoute.MoveCategory(from))
        host.mutate { it.copy(editError = null) }
    }

    /** Opens the move screen for [categoryId] from wherever the person is, as edit mode's available figures do. */
    fun openMoveFrom(categoryId: String) {
        host.push(ShellRoute.MoveCategory(categoryId))
        host.mutate { it.copy(editError = null) }
    }

    fun showHold() {
        host.push(ShellRoute.HoldMonth)
        host.mutate { it.copy(editError = null) }
    }

    fun showManage() {
        host.push(ShellRoute.ManageCategories)
        val budgetId = state.shell?.budgetId ?: state.settings.openBudgetId
        if (budgetId == null) return
        host.scope.launch {
            val page = withContext(host.io) { host.sync.readCategoryManage(budgetId) }
            host.mutate { it.copy(editError = null, envelopes = it.envelopes.copy(manage = page)) }
        }
    }

    fun saveBudget(amountText: String) {
        val categoryId = (state.route as? ShellRoute.CategoryBudget)?.categoryId ?: return
        // Saving goes back to the page that opened the category, as the transaction form does.
        assign(categoryId, amountText, stayOnRoute = false)
    }

    /** Sets [categoryId]'s budgeted amount for the month being edited; [amountText] reads like "1234.56". */
    fun assign(
        categoryId: String,
        amountText: String,
        stayOnRoute: Boolean = true,
    ) {
        val shell = state.shell ?: return
        val budgeted = MoneyFormat.parse(amountText, shell.currency.decimals)
        if (budgeted == null) {
            host.mutate { it.copy(editError = SyncCopy.ENTER_AMOUNT) }
            return
        }
        applyEdit(stayOnRoute = stayOnRoute, optimistic = { it.withBudgetedAssignment(categoryId, budgeted) }) {
            host.sync.assign(shell.budgetId, categoryId, host.editorMonth(), amountText)
        }
    }

    /** Edit mode's done: saves each changed budgeted amount for [month] as one undo step. */
    fun saveBudgets(
        month: YearMonth,
        amounts: Map<String, Long>,
        templates: Boolean? = null,
    ) {
        val shell = state.shell ?: return
        applyEdit(optimistic = { amounts.entries.fold(it) { s, (id, minor) -> s.withBudgetedAssignment(id, minor) } }) {
            host.sync.assignBudgets(shell.budgetId, month, amounts, templates)
        }
    }

    fun toggleCarryover(enabled: Boolean) {
        val shell = state.shell ?: return
        val categoryId = (state.route as? ShellRoute.CategoryBudget)?.categoryId ?: return
        applyEdit {
            host.sync.setCategoryCarryover(shell.budgetId, categoryId, host.editorMonth(), enabled)
        }
    }

    fun submitMove(
        toCategoryId: String,
        amountText: String,
    ) {
        val shell = state.shell ?: return
        val from = (state.route as? ShellRoute.MoveCategory)?.fromCategoryId ?: return
        if (state.budgetEdit != null) {
            host.moveInBudgetEdit(from, toCategoryId, amountText)
            return
        }
        if (toCategoryId == MOVE_TO_BUDGET) {
            host.transferToBudget(from, amountText)
            return
        }
        if (toCategoryId.isBlank()) {
            host.mutate { it.copy(editError = SyncCopy.ENTER_AMOUNT) }
            return
        }
        val amount = MoneyFormat.parse(amountText, shell.currency.decimals)
        if (amount == null || amount <= 0L) {
            host.mutate { it.copy(editError = SyncCopy.ENTER_AMOUNT) }
            return
        }
        applyEdit(
            stayOnRoute = false,
            optimistic = { it.withMoveAvailable(from, toCategoryId, amount) },
        ) {
            host.sync.moveCategory(shell.budgetId, from, toCategoryId, host.editorMonth(), amountText)
        }
    }

    fun submitHold(amountText: String) {
        val shell = state.shell ?: return
        val amount = MoneyFormat.parse(amountText, shell.currency.decimals)
        if (amount == null) {
            host.mutate { it.copy(editError = SyncCopy.ENTER_AMOUNT) }
            return
        }
        applyEdit(optimistic = { it.withHold(amount) }) {
            host.sync.holdForNextMonth(shell.budgetId, host.editorMonth(), amountText)
        }
    }

    fun releaseHold() {
        val shell = state.shell ?: return
        applyEdit(optimistic = { it.withReleaseHold() }) {
            host.sync.releaseMonthHold(shell.budgetId, host.editorMonth())
        }
    }

    fun addGroup(name: String) {
        val budgetId = state.settings.openBudgetId ?: return
        if (name.isBlank()) {
            host.mutate { it.copy(editError = SyncCopy.ENTER_AMOUNT) }
            return
        }
        applyEdit {
            host.sync.addCategoryGroup(budgetId, name)
        }
    }

    fun addCategory(
        groupId: String,
        name: String,
    ) {
        val budgetId = state.settings.openBudgetId ?: return
        if (groupId.isBlank() || name.isBlank()) {
            host.mutate { it.copy(editError = SyncCopy.ENTER_AMOUNT) }
            return
        }
        applyEdit {
            host.sync.addEnvelopeCategory(budgetId, groupId, name)
        }
    }

    fun renameGroup(
        groupId: String,
        name: String,
    ) {
        val budgetId = state.settings.openBudgetId ?: return
        if (name.isNotBlank()) renameOnPage { group -> if (group.id == groupId) group.copy(name = name.trim()) else group }
        applyEdit { host.sync.renameCategoryGroup(budgetId, groupId, name) }
    }

    fun renameCategory(
        categoryId: String,
        name: String,
    ) {
        val budgetId = state.settings.openBudgetId ?: return
        if (name.isNotBlank()) {
            renameOnPage { group ->
                group.copy(categories = group.categories.map { if (it.id == categoryId) it.copy(name = name.trim()) else it })
            }
        }
        applyEdit { host.sync.renameEnvelopeCategory(budgetId, categoryId, name) }
    }

    /**
     * A rename shows on the categories page as soon as it is saved: the write and the month read after it can
     * wait behind a sync, and the row would show its old name until then. A blank name is refused, so it never shows.
     */
    private fun renameOnPage(rename: (ManageGroupRow) -> ManageGroupRow) {
        update { current ->
            val manage = current.manage ?: return@update current
            current.copy(manage = manage.copy(groups = manage.groups.map(rename)))
        }
    }

    fun hideCategory(
        categoryId: String,
        hidden: Boolean,
    ) {
        val budgetId = state.settings.openBudgetId ?: return
        applyEdit { host.sync.hideEnvelopeCategory(budgetId, categoryId, hidden) }
    }

    fun deleteCategory(categoryId: String) {
        val budgetId = state.settings.openBudgetId ?: return
        applyEdit { host.sync.deleteEnvelopeCategory(budgetId, categoryId) }
    }

    fun deleteGroup(groupId: String) {
        val budgetId = state.settings.openBudgetId ?: return
        applyEdit { host.sync.deleteCategoryGroup(budgetId, groupId) }
    }

    fun hideGroup(
        groupId: String,
        hidden: Boolean,
    ) {
        val budgetId = state.settings.openBudgetId ?: return
        applyEdit { host.sync.hideCategoryGroup(budgetId, groupId, hidden) }
    }

    fun moveToGroup(
        categoryId: String,
        groupId: String,
    ) {
        val budgetId = state.settings.openBudgetId ?: return
        applyEdit { host.sync.moveCategoryToGroup(budgetId, categoryId, groupId) }
    }

    /** A drop from the categories page's reorder: the list moves before the write lands. */
    fun placeGroup(
        groupId: String,
        targetId: String?,
    ) {
        val budgetId = state.settings.openBudgetId ?: return
        update { current ->
            val manage = current.manage ?: return@update current
            current.copy(manage = manage.copy(groups = placed(manage.groups, groupId, targetId) { it.id }))
        }
        applyEdit { host.sync.placeCategoryGroup(budgetId, groupId, targetId) }
    }

    fun placeCategory(
        categoryId: String,
        targetId: String?,
    ) {
        val budgetId = state.settings.openBudgetId ?: return
        update { current ->
            val manage = current.manage ?: return@update current
            val groups =
                manage.groups.map { group ->
                    val holds = group.categories.any { it.id == categoryId }
                    if (holds) group.copy(categories = placed(group.categories, categoryId, targetId) { it.id }) else group
                }
            current.copy(manage = manage.copy(groups = groups))
        }
        applyEdit { host.sync.placeEnvelopeCategory(budgetId, categoryId, targetId) }
    }

    /** [rows] with [id] moved just before [targetId], or last when null. */
    private fun <T> placed(
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

    private fun applyEdit(
        stayOnRoute: Boolean = true,
        optimistic: ((MonthShell) -> MonthShell)? = null,
        block: suspend () -> EditResult,
    ) {
        val budgetId = state.shell?.budgetId ?: state.settings.openBudgetId ?: return
        val previousShell = state.shell
        if (optimistic != null && previousShell != null) {
            host.mutate { it.copy(shell = optimistic(previousShell), editError = null) }
        }
        host.scope.launch {
            val result = withContext(host.io) { block() }
            host.mutate { it.copy(undoLabel = host.library.undoLabel(budgetId)) }
            when (result) {
                is EditResult.Rejected -> {
                    // Puts back anything the page showed before the write was refused, such as a rename.
                    refreshManage(budgetId)
                    val month = host.editorMonth()
                    val shell = withContext(host.io) { host.library.readShell(budgetId, month) }
                    host.mutate { current ->
                        current.copy(
                            editError = result.reason,
                            shell =
                                if (shell != null && !host.shellGate.gestureActive) {
                                    shell
                                } else {
                                    current.shell
                                },
                        )
                    }
                }
                EditResult.Saved -> {
                    host.mutate { it.copy(editError = null) }
                    // The categories page first: it reads in a moment, while the month can take a while.
                    refreshManage(budgetId)
                    val month = host.editorMonth()
                    val shell = withContext(host.io) { host.library.readShell(budgetId, month) }
                    if (shell != null) {
                        host.shellGate.offer(shell)
                        host.mutate { current ->
                            current.copy(shell = if (host.shellGate.gestureActive) current.shell else shell)
                        }
                    }
                    if (!stayOnRoute) host.back()
                    withContext(host.io) { runCatching { host.sync.sendUnsent(budgetId) } }
                    val refreshed = withContext(host.io) { host.library.readShell(budgetId, month) }
                    if (refreshed != null) {
                        host.shellGate.offer(refreshed)
                        host.mutate { current ->
                            current.copy(shell = if (host.shellGate.gestureActive) current.shell else refreshed)
                        }
                    }
                }
            }
        }
    }

    private suspend fun refreshManage(budgetId: String) {
        if (state.route !is ShellRoute.ManageCategories) return
        val manage = withContext(host.io) { host.sync.readCategoryManage(budgetId) }
        update { it.copy(manage = manage) }
    }
}
