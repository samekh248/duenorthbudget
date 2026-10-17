package app.duenorth.budget

import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.FormatOptions
import app.duenorth.budget.core.MoneyMath
import app.duenorth.budget.core.MoneyParse
import app.duenorth.budget.core.SyncCopy

// The budget's edit mode: the budgeted column as boxes, typed into on the app's own keypad, with to
// budget kept in view. Each box saves when the keypad closes on it, so undo steps back one box at a time.

/** Edit mode on the budget item; [categoryId] is the box the keypad is typing into, and [text] what was typed. */
data class BudgetEdit(
    val categoryId: String? = null,
    val text: String = "",
)

object BudgetKeys {
    const val BACKSPACE = "⌫"
    const val EQUALS = "="
}

/** Home `•••` → edit budget, or a tap on to budget: opens edit mode on the budget item. */
fun ShellViewModel.startBudgetEdit() {
    if (state.value.shell == null) return
    mutate { it.copy(budgetEdit = BudgetEdit(), homeSection = ScreenBar.HOME_BUDGET, monthNotice = null, editError = null) }
}

/** done: saves the box being typed into, if any, and leaves edit mode. */
fun ShellViewModel.endBudgetEdit() {
    if (!commitBudgetBox()) return
    mutate { it.copy(budgetEdit = null, editError = null) }
}

/** Opens the keypad on [categoryId]'s box, saving the box it was on first. */
fun ShellViewModel.focusBudgetBox(categoryId: String) {
    val edit = state.value.budgetEdit ?: return
    if (edit.categoryId == categoryId) return
    if (!commitBudgetBox()) return
    mutate { it.copy(budgetEdit = BudgetEdit(categoryId), editError = null) }
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
    val shell = state.value.shell ?: return
    val category = budgetEditCategory() ?: return
    if (shell.mode != BudgetMode.ENVELOPE || shell.headerMinor <= 0) return
    setBudgetBox(category, category.budgetedMinor + shell.headerMinor)
}

/** Closes the keypad and forgets what was typed. */
fun ShellViewModel.closeBudgetKeypad() {
    mutate { it.copy(budgetEdit = it.budgetEdit?.copy(categoryId = null, text = ""), editError = null) }
}

/** done on the keypad: saves the box and closes the keypad. */
fun ShellViewModel.doneBudgetBox() {
    if (commitBudgetBox()) closeBudgetKeypad()
}

private fun ShellViewModel.budgetEditCategory(): CategoryRow? {
    val id = state.value.budgetEdit?.categoryId ?: return null
    return state.value.shell
        ?.groups
        ?.flatMap { it.categories }
        ?.firstOrNull { it.id == id }
}

private fun ShellViewModel.setBudgetBox(
    category: CategoryRow,
    minor: Long,
) {
    val decimals =
        state.value.shell
            ?.currency
            ?.decimals ?: 2
    assignBudget(category.id, MoneyParse.formatMagnitude(minor, decimals).let { if (minor < 0) "-$it" else it })
    closeBudgetKeypad()
}

/**
 * Saves what was typed into the open box. Nothing typed leaves the amount as it was. Returns false, with
 * the error showing and the keypad still open, when the typing does not read as an amount.
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
    if (minor != category.budgetedMinor) assignBudget(category.id, canonical)
    return true
}
