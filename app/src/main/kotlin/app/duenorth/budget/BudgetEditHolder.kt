package app.duenorth.budget

import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.EnvelopeCopy
import app.duenorth.budget.core.FormatOptions
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.MoneyMath
import app.duenorth.budget.core.MonthFillCopy
import app.duenorth.budget.core.MonthFillKind
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.SyncCopy
import app.duenorth.budget.core.TemplateResult
import app.duenorth.budget.core.withBudgetedAssignment
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth

// The budget's edit mode: the budgeted column as boxes, typed into on the app's own keypad, with to
// budget kept in view. It is a sandbox: boxes and moves change only [BudgetEdit.budgeted] until done
// saves them all as one step; cancel, or back, drops them and the budget stays as it was.

/**
 * Edit mode on the budget item; [categoryId] is the box the keypad is typing into, and [text] what was typed.
 * [budgeted] is each changed category's new budgeted amount, not yet saved, for [budgetId]'s [month].
 * [templates] is null until the templates run here, then whether any run overwrote.
 */
data class BudgetEdit(
    val categoryId: String? = null,
    val text: String = "",
    val budgeted: Map<String, Long> = emptyMap(),
    val budgetId: String? = null,
    val month: YearMonth? = null,
    val templates: Boolean? = null,
)

/** The month as edit mode shows it: the saved month with the unsaved amounts laid over it. */
fun ShellUiState.editingShell(): MonthShell? {
    val shell = shell ?: return null
    val edit = budgetEdit ?: return shell
    if (edit.budgetId != shell.budgetId || edit.month != shell.month) return shell
    return edit.budgeted.entries.fold(shell) { month, (id, minor) -> month.withBudgetedAssignment(id, minor) }
}

object BudgetKeys {
    const val BACKSPACE = "⌫"
    const val EQUALS = "="
}

/** The budget's edit mode: its boxes and keypad, fills and templates into the boxes, and done or cancel. */
class BudgetEditHolder internal constructor(
    private val host: ShellHost,
    private val envelopes: EnvelopesHolder,
) {
    private val state get() = host.state.value

    /** The budget bar's edit budget button, or a tap on to budget: opens edit mode on the budget item. */
    fun start() {
        val shell = state.shell ?: return
        host.mutate {
            it.copy(
                budgetEdit = BudgetEdit(budgetId = shell.budgetId, month = shell.month),
                homeSection = ScreenBar.HOME_BUDGET,
                editError = null,
                monthUi = it.monthUi.copy(notice = null),
            )
        }
    }

    /** done: takes in the box being typed into, saves every changed amount, and leaves edit mode. */
    fun end() {
        if (!commitBox()) return
        val edit = state.budgetEdit ?: return
        val shell = state.shell
        host.mutate { it.copy(budgetEdit = null, editError = null) }
        if (shell == null || edit.budgetId != shell.budgetId || edit.month != shell.month) return
        val saved = shell.groups.flatMap { it.categories }.associate { it.id to it.budgetedMinor }
        // Templates rerun on save, so every amount goes back over theirs; otherwise only what changed. A fill can
        // reach categories the budget is not showing, which are saved as they are.
        val changed = if (edit.templates != null) edit.budgeted else edit.budgeted.filter { (id, minor) -> saved[id] != minor }
        if (changed.isNotEmpty() || edit.templates != null) envelopes.saveBudgets(shell.month, changed, edit.templates)
    }

    /** A month fill in edit mode: copy last month, 3 month average or set to zero land in the boxes, unsaved. */
    fun fill(kind: MonthFillKind) {
        val edit = state.budgetEdit ?: return
        if (!commitBox()) return
        closeKeypad()
        val budgetId = edit.budgetId ?: return
        val month = edit.month ?: return
        host.scope.launch {
            val targets = withContext(host.io) { host.sync.fillTargets(budgetId, month, kind) }
            host.mutate { current ->
                val open = current.budgetEdit?.takeIf { it.budgetId == budgetId && it.month == month } ?: return@mutate current
                if (targets.isNullOrEmpty()) {
                    current.copy(editError = MonthFillCopy.NOTHING_TO_COPY)
                } else {
                    current.copy(budgetEdit = open.copy(budgeted = open.budgeted + targets), editError = null)
                }
            }
        }
    }

    /** Templates in edit mode: works out what they would budget, over edit mode's own amounts, without saving. */
    fun applyTemplates(overwrite: Boolean) {
        val edit = state.budgetEdit ?: return
        val budgetId = edit.budgetId ?: return
        val month = edit.month ?: return
        host.mutate { it.copy(templates = it.templates.copy(busy = true, confirm = null)) }
        host.scope.launch {
            val (result, amounts) =
                withContext(host.io) { host.library.previewTemplates(budgetId, month, overwrite, edit.budgeted) }
            host.mutate { current ->
                val open = current.budgetEdit?.takeIf { it.budgetId == budgetId && it.month == month }
                if (open == null || result !is TemplateResult.Applied) {
                    current.copy(templates = current.templates.copy(result = result, busy = false))
                } else {
                    current.copy(
                        budgetEdit = open.copy(budgeted = open.budgeted + amounts, templates = (open.templates ?: false) || overwrite),
                        templates = current.templates.copy(result = result, busy = false),
                    )
                }
            }
        }
    }

    /** cancel, or back: leaves edit mode and drops everything it changed. Nothing was saved, so nothing is undone. */
    fun cancel() {
        host.mutate { it.copy(budgetEdit = null, editError = null) }
    }

    /** Opens the keypad on [categoryId]'s box, saving the box it was on first. */
    fun focusBox(categoryId: String) {
        val edit = state.budgetEdit ?: return
        if (edit.categoryId == categoryId) return
        if (!commitBox()) return
        host.mutate { it.copy(budgetEdit = it.budgetEdit?.copy(categoryId = categoryId, text = ""), editError = null) }
    }

    /** A keypad key: a digit, the decimal point or an operator is typed; ⌫ takes one back; = works the sum out. */
    fun typeKey(key: String) {
        val edit = state.budgetEdit ?: return
        val category = openCategory() ?: return
        val text =
            when (key) {
                BudgetKeys.BACKSPACE -> edit.text.dropLast(1)
                BudgetKeys.EQUALS -> {
                    val prefs = state.formatPrefs
                    val decimals =
                        state.shell
                            ?.currency
                            ?.decimals ?: 2
                    val minor = MoneyMath.evaluate(edit.text, prefs.numberFormat, decimals, category.budgetedMinor)
                    // A negative result would read back as "take this off", so it stays as typed.
                    if (minor == null || minor < 0) edit.text else prefs.editAmount(minor, decimals)
                }
                else -> edit.text + key
            }
        host.mutate { it.copy(budgetEdit = edit.copy(text = text), editError = null) }
    }

    /** Budgets what the open box's category is overspent by, so its available comes to zero. */
    fun coverBox() {
        val category = openCategory() ?: return
        if (category.availableMinor >= 0) return
        setBox(category, category.budgetedMinor - category.availableMinor)
    }

    /** Puts everything left in to budget into the open box's category. Envelope only. */
    fun allToBudgetBox() {
        val shell = state.editingShell() ?: return
        val category = openCategory() ?: return
        if (shell.mode != BudgetMode.ENVELOPE || shell.headerMinor <= 0) return
        setBox(category, category.budgetedMinor + shell.headerMinor)
    }

    /** Closes the keypad and forgets what was typed. */
    fun closeKeypad() {
        host.mutate { it.copy(budgetEdit = it.budgetEdit?.copy(categoryId = null, text = ""), editError = null) }
    }

    /** done on the keypad: saves the box and closes the keypad. False when what was typed does not read. */
    fun doneBox(): Boolean {
        if (!commitBox()) return false
        closeKeypad()
        return true
    }

    /**
     * The move screen's save while editing: moves [amountText] of [fromCategoryId]'s available to [toCategoryId]
     * (or back to to budget) in the sandbox, the way a saved move would, and returns to the boxes.
     */
    fun move(
        fromCategoryId: String,
        toCategoryId: String,
        amountText: String,
    ) {
        val shell = state.editingShell() ?: return
        val categories = shell.groups.flatMap { it.categories }
        val from = categories.firstOrNull { it.id == fromCategoryId } ?: return
        val to = categories.firstOrNull { it.id == toCategoryId }
        val amount = MoneyFormat.parse(amountText, shell.currency.decimals)
        val error =
            when {
                toCategoryId != MOVE_TO_BUDGET && to == null -> SyncCopy.ENTER_AMOUNT
                amount == null || amount <= 0L -> SyncCopy.ENTER_AMOUNT
                amount > from.availableMinor -> EnvelopeCopy.NOT_ENOUGH
                else -> null
            }
        if (error != null || amount == null) {
            host.mutate { it.copy(editError = error) }
            return
        }
        host.mutate { current ->
            val edit = current.budgetEdit ?: return@mutate current
            var budgeted = edit.budgeted + (from.id to from.budgetedMinor - amount)
            if (to != null) budgeted = budgeted + (to.id to to.budgetedMinor + amount)
            current.copy(budgetEdit = edit.copy(budgeted = budgeted), editError = null)
        }
        host.back()
    }

    private fun openCategory(): CategoryRow? {
        val id = state.budgetEdit?.categoryId ?: return null
        return state
            .editingShell()
            ?.groups
            ?.flatMap { it.categories }
            ?.firstOrNull { it.id == id }
    }

    private fun setBox(
        category: CategoryRow,
        minor: Long,
    ) {
        draft(category.id, minor)
        closeKeypad()
    }

    private fun draft(
        categoryId: String,
        minor: Long,
    ) {
        host.mutate { current ->
            val edit = current.budgetEdit ?: return@mutate current
            current.copy(budgetEdit = edit.copy(budgeted = edit.budgeted + (categoryId to minor)), editError = null)
        }
    }

    /**
     * Takes what was typed into the open box into the sandbox. Nothing typed leaves the amount as it was.
     * Returns false, with the error showing and the keypad still open, when the typing does not read as an amount.
     */
    private fun commitBox(): Boolean {
        val edit = state.budgetEdit ?: return true
        val category = openCategory() ?: return true
        if (edit.text.isBlank()) return true
        val prefs = state.formatPrefs
        val decimals =
            state.shell
                ?.currency
                ?.decimals ?: 2
        val canonical = prefs.readAmount(edit.text, decimals, base = category.budgetedMinor)
        val minor = MoneyMath.evaluate(canonical, FormatOptions.NUMBER_COMMA_DOT, decimals)
        if (minor == null) {
            host.mutate { it.copy(editError = SyncCopy.ENTER_AMOUNT) }
            return false
        }
        if (minor != category.budgetedMinor) draft(category.id, minor)
        return true
    }
}
