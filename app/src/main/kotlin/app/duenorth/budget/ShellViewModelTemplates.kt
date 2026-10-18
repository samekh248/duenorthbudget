package app.duenorth.budget

import androidx.lifecycle.viewModelScope
import app.duenorth.budget.core.TemplateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Spec 010 US5 (T062): budget templates from category notes. See contracts/templates.md.

/** Home `•••` → templates; checks the notes straight away. */
fun ShellViewModel.openTemplates() {
    if (!state.value.formatPrefs.templatesOn) return
    push(ShellRoute.Templates)
    mutate { it.copy(templateResult = null, templateConfirm = null) }
    checkTemplates()
}

fun ShellViewModel.checkTemplates() {
    val budgetId = state.value.settings.openBudgetId ?: return
    mutate { it.copy(templateBusy = true) }
    viewModelScope.launch {
        val result = withContext(Dispatchers.IO) { library.checkTemplates(budgetId) }
        mutate { it.copy(templateResult = result, templateBusy = false) }
    }
}

/** Apply fills zero categories; overwrite asks first, then refills every templated category. */
fun ShellViewModel.applyTemplates(
    overwrite: Boolean,
    confirmed: Boolean = false,
) {
    // Opened from edit mode, templates fill its boxes and save with done; overwriting needs no asking there.
    if (state.value.budgetEdit != null) {
        applyTemplatesInBudgetEdit(overwrite)
        return
    }
    val budgetId = state.value.settings.openBudgetId ?: return
    val month = editorMonth()
    mutate { it.copy(templateBusy = true, templateConfirm = null) }
    viewModelScope.launch {
        val result = withContext(Dispatchers.IO) { library.applyTemplates(budgetId, month, overwrite, confirmed) }
        when (result) {
            is TemplateResult.NeedsConfirm -> mutate { it.copy(templateConfirm = result.reason, templateBusy = false) }
            is TemplateResult.Applied -> {
                mutate { it.copy(templateResult = result, templateBusy = false) }
                afterMonthWrite(budgetId)
            }
            else -> mutate { it.copy(templateResult = result, templateBusy = false) }
        }
    }
}

fun ShellViewModel.confirmTemplateOverwrite() = applyTemplates(overwrite = true, confirmed = true)

fun ShellViewModel.keepTemplates() {
    mutate { it.copy(templateConfirm = null) }
}

internal fun ShellViewModel.loadCategoryNote(categoryId: String) {
    val budgetId = state.value.settings.openBudgetId ?: return
    if (!state.value.formatPrefs.templatesOn) return
    viewModelScope.launch {
        val note = withContext(Dispatchers.IO) { library.readCategoryNote(budgetId, categoryId) }
        mutate { current ->
            if ((current.route as? ShellRoute.CategoryBudget)?.categoryId == categoryId) current.copy(categoryNote = note) else current
        }
    }
}

/** Saves the open category's note as one undo step; templates read it on the next check. */
fun ShellViewModel.saveCategoryNote(note: String) {
    val budgetId = state.value.settings.openBudgetId ?: return
    val categoryId = (state.value.route as? ShellRoute.CategoryBudget)?.categoryId ?: return
    mutate { it.copy(categoryNote = note) }
    viewModelScope.launch {
        withContext(Dispatchers.IO) { library.saveCategoryNote(budgetId, categoryId, note) }
        mutate { it.copy(undoLabel = library.undoLabel(budgetId)) }
        withContext(Dispatchers.IO) { runCatching { sync.sync(budgetId) } }
    }
}
