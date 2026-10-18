package app.duenorth.budget

import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.EnvelopeCopy
import app.duenorth.budget.core.FormatOptions
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.MoneyMath
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.SyncCopy
import app.duenorth.budget.core.withBudgetedAssignment
import java.time.YearMonth

// The budget's edit mode: the budgeted column as boxes, typed into on the app's own keypad, with to
// budget kept in view. It is a sandbox: boxes and moves change only [BudgetEdit.budgeted] until done
// saves them all as one step; cancel, or back, drops them and the budget stays as it was.

/**
 * Edit mode on the budget item; [categoryId] is the box the keypad is typing into, and [text] what was typed.
 * [budgeted] is each changed category's new budgeted amount, not yet saved, for [budgetId]'s [month].
 */
data class BudgetEdit(
    val categoryId: String? = null,
    val text: String = "",
    val budgeted: Map<String, Long> = emptyMap(),
    val budgetId: String? = null,
    val month: YearMonth? = null,
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

/** Home `•••` → edit budget, or a tap on to budget: opens edit mode on the budget item. */
fun ShellViewModel.startBudgetEdit() {
    val shell = state.value.shell ?: return
    mutate {
        it.copy(
            budgetEdit = BudgetEdit(budgetId = shell.budgetId, month = shell.month),
            homeSection = ScreenBar.HOME_BUDGET,
            monthNotice = null,
            editError = null,
        )
    }
}

/** done: takes in the box being typed into, saves every changed amount, and leaves edit mode. */
fun ShellViewModel.endBudgetEdit() {
    if (!commitBudgetBox()) return
    val edit = state.value.budgetEdit ?: return
    val shell = state.value.shell
    mutate { it.copy(budgetEdit = null, editError = null) }
    if (shell == null || edit.budgetId != shell.budgetId || edit.month != shell.month) return
    val saved = shell.groups.flatMap { it.categories }.associate { it.id to it.budgetedMinor }
    val changed = edit.budgeted.filter { (id, minor) -> id in saved && saved[id] != minor }
    if (changed.isNotEmpty()) saveBudgets(shell.month, changed)
}

/** cancel, or back: leaves edit mode and drops everything it changed. Nothing was saved, so nothing is undone. */
fun ShellViewModel.cancelBudgetEdit() {
    mutate { it.copy(budgetEdit = null, editError = null) }
}

/** Opens the keypad on [categoryId]'s box, saving the box it was on first. */
fun ShellViewModel.focusBudgetBox(categoryId: String) {
    val edit = state.value.budgetEdit ?: return
    if (edit.categoryId == categoryId) return
    if (!commitBudgetBox()) return
    mutate { it.copy(budgetEdit = it.budgetEdit?.copy(categoryId = categoryId, text = ""), editError = null) }
}

/** A keypad key: a digit, the decimal point or an operator is typed; ⌫ takes one back; = works the sum out. */
fun ShellViewModel.typeBudgetKey(key: String) {
    val edit = state.value.budgetEdit ?: return
    val category = budgetEditCategory() ?: return
    val text =
        when (key) {
            BudgetKeys.BACKSPACE -> edit.text.dropLast(1)
            BudgetKeys.EQUALS -> {
                val prefs = state.value.formatPrefs
                val decimals =
                    state.value.shell
                        ?.currency
                        ?.decimals ?: 2
                val minor = MoneyMath.evaluate(edit.text, prefs.numberFormat, decimals, category.budgetedMinor)
                // A negative result would read back as "take this off", so it stays as typed.
                if (minor == null || minor < 0) edit.text else prefs.editAmount(minor, decimals)
            }
            else -> edit.text + key
        }
    mutate { it.copy(budgetEdit = edit.copy(text = text), editError = null) }
}

/** Budgets what the open box's category is overspent by, so its available comes to zero. */
fun ShellViewModel.coverBudgetBox() {
    val category = budgetEditCategory() ?: return
    if (category.availableMinor >= 0) return
    setBudgetBox(category, category.budgetedMinor - category.availableMinor)
}

/** Puts everything left in to budget into the open box's category. Envelope only. */
fun ShellViewModel.allToBudgetBox() {
    val shell = state.value.editingShell() ?: return
    val category = budgetEditCategory() ?: return
    if (shell.mode != BudgetMode.ENVELOPE || shell.headerMinor <= 0) return
    setBudgetBox(category, category.budgetedMinor + shell.headerMinor)
}

/** Closes the keypad and forgets what was typed. */
fun ShellViewModel.closeBudgetKeypad() {
    mutate { it.copy(budgetEdit = it.budgetEdit?.copy(categoryId = null, text = ""), editError = null) }
}

/** done on the keypad: saves the box and closes the keypad. False when what was typed does not read. */
fun ShellViewModel.doneBudgetBox(): Boolean {
    if (!commitBudgetBox()) return false
    closeBudgetKeypad()
    return true
}

/**
 * The move screen's save while editing: moves [amountText] of [fromCategoryId]'s available to [toCategoryId]
 * (or back to to budget) in the sandbox, the way a saved move would, and returns to the boxes.
 */
fun ShellViewModel.moveInBudgetEdit(
    fromCategoryId: String,
    toCategoryId: String,
    amountText: String,
) {
    val shell = state.value.editingShell() ?: return
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
        mutate { it.copy(editError = error) }
        return
    }
    mutate { current ->
        val edit = current.budgetEdit ?: return@mutate current
        var budgeted = edit.budgeted + (from.id to from.budgetedMinor - amount)
        if (to != null) budgeted = budgeted + (to.id to to.budgetedMinor + amount)
        current.copy(budgetEdit = edit.copy(budgeted = budgeted), editError = null)
    }
    back()
}

private fun ShellViewModel.budgetEditCategory(): CategoryRow? {
    val id = state.value.budgetEdit?.categoryId ?: return null
    return state.value
        .editingShell()
        ?.groups
        ?.flatMap { it.categories }
        ?.firstOrNull { it.id == id }
}

private fun ShellViewModel.setBudgetBox(
    category: CategoryRow,
    minor: Long,
) {
    draftBudget(category.id, minor)
    closeBudgetKeypad()
}

private fun ShellViewModel.draftBudget(
    categoryId: String,
    minor: Long,
) {
    mutate { current ->
        val edit = current.budgetEdit ?: return@mutate current
        current.copy(budgetEdit = edit.copy(budgeted = edit.budgeted + (categoryId to minor)), editError = null)
    }
}

/**
 * Takes what was typed into the open box into the sandbox. Nothing typed leaves the amount as it was.
 * Returns false, with the error showing and the keypad still open, when the typing does not read as an amount.
 */
private fun ShellViewModel.commitBudgetBox(): Boolean {
    val edit = state.value.budgetEdit ?: return true
    val category = budgetEditCategory() ?: return true
    if (edit.text.isBlank()) return true
    val prefs = state.value.formatPrefs
    val decimals =
        state.value.shell
            ?.currency
            ?.decimals ?: 2
    val canonical = prefs.readAmount(edit.text, decimals, base = category.budgetedMinor)
    val minor = MoneyMath.evaluate(canonical, FormatOptions.NUMBER_COMMA_DOT, decimals)
    if (minor == null) {
        mutate { it.copy(editError = SyncCopy.ENTER_AMOUNT) }
        return false
    }
    if (minor != category.budgetedMinor) draftBudget(category.id, minor)
    return true
}
