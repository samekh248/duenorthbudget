package app.duenorth.budget

import androidx.lifecycle.viewModelScope
import app.duenorth.budget.core.Template
import app.duenorth.budget.core.TemplateChip
import app.duenorth.budget.core.TemplateKind
import app.duenorth.budget.core.TemplateNote
import app.duenorth.budget.core.TemplateResult
import app.duenorth.budget.core.withPriority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// Spec 010 US5 (T062): budget templates from category notes. See contracts/templates.md.

/** Home `•••` → templates; checks the notes straight away. */
fun ShellViewModel.openTemplates() {
    if (!state.value.formatPrefs.templatesOn) return
    push(ShellRoute.Templates)
    mutate { it.copy(templateResult = null, templateConfirm = null, templateApplyPreview = null) }
    checkTemplates()
    refreshTemplatePlan()
}

fun ShellViewModel.checkTemplates() {
    val budgetId = state.value.settings.openBudgetId ?: return
    mutate { it.copy(templateBusy = true) }
    viewModelScope.launch {
        val result = withContext(io) { library.checkTemplates(budgetId) }
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
        val result = withContext(io) { library.applyTemplates(budgetId, month, overwrite, confirmed) }
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
        val note = withContext(io) { library.readCategoryNote(budgetId, categoryId) }
        mutate { current ->
            if ((current.route as? ShellRoute.CategoryBudget)?.categoryId == categoryId) current.copy(categoryNote = note) else current
        }
    }
}

/** The category the open template page (the category page, the kinds grid or a template's form) belongs to. */
private fun ShellViewModel.templateCategoryId(): String? =
    when (val route = state.value.route) {
        is ShellRoute.CategoryBudget -> route.categoryId
        is ShellRoute.TemplateKinds -> route.categoryId
        is ShellRoute.TemplateEditor -> route.categoryId
        else -> null
    }

/** Saves the open category's note as one undo step; templates read it on the next check. */
fun ShellViewModel.saveCategoryNote(note: String) {
    val budgetId = state.value.settings.openBudgetId ?: return
    val categoryId = templateCategoryId() ?: return
    mutate { it.copy(categoryNote = note) }
    viewModelScope.launch {
        // Tiles can change faster than a write lands (drag, then remove); the last change must win.
        noteWrites.withLock { withContext(io) { library.saveCategoryNote(budgetId, categoryId, note) } }
        mutate { it.copy(undoLabel = library.undoLabel(budgetId)) }
        withContext(io) { runCatching { sync.sendUnsent(budgetId) } }
    }
}

// The template editor (option A, template tiles): tiles on the category page, a grid of kinds, and one form.

/** The note's free text changed; its template lines stay as they are. */
fun ShellViewModel.saveCategoryNoteText(text: String) {
    val note = state.value.categoryNote ?: return
    saveCategoryNote(TemplateNote.parse(note).withText(text))
}

/** Works out what each template line in the open category's note asks for in the open month. */
fun ShellViewModel.refreshTemplateAmounts() {
    val budgetId = state.value.settings.openBudgetId ?: return
    val categoryId = templateCategoryId() ?: return
    val note = state.value.categoryNote ?: return
    val entries = TemplateNote.parse(note).entries
    val month = editorMonth()
    viewModelScope.launch {
        val templates = entries.mapNotNull { it.template }
        val asks = withContext(io) { library.previewCategoryTemplates(budgetId, categoryId, month, templates) }.iterator()
        val amounts = entries.map { entry -> if (entry.template != null && asks.hasNext()) asks.next() else null }
        mutate { if (it.categoryNote == note) it.copy(categoryTemplateAmounts = amounts) else it }
    }
}

/** Category page → add template, or (with [index]) a line that does not read: the grid of kinds. */
fun ShellViewModel.openTemplateKinds(index: Int? = null) {
    val categoryId = templateCategoryId() ?: return
    push(ShellRoute.TemplateKinds(categoryId, index))
    loadTemplateSchedules()
}

/** A tile tapped: its form, or the kinds grid for a line that does not read. */
fun ShellViewModel.openTemplate(index: Int) {
    val categoryId = templateCategoryId() ?: return
    val entry = state.value.categoryNote?.let { TemplateNote.parse(it).entries.getOrNull(index) } ?: return
    val template = entry.template ?: return openTemplateKinds(index)
    mutate { it.copy(templatePreview = null) }
    push(ShellRoute.TemplateEditor(categoryId, index, TemplateKind.of(template)))
    loadTemplateSchedules()
}

/** A kind picked from the grid: its form takes the grid's place, so save and back both land on the category. */
fun ShellViewModel.pickTemplateKind(kind: TemplateKind) {
    val route = state.value.route as? ShellRoute.TemplateKinds ?: return
    mutate { it.copy(templatePreview = null) }
    replaceRoute(ShellRoute.TemplateEditor(route.categoryId, route.index, kind))
}

/** The template a form starts from: the line being edited, or a new one of its kind after the category's others. */
fun ShellUiState.templateDraftStart(route: ShellRoute.TemplateEditor): Template {
    val entries = categoryNote?.let { TemplateNote.parse(it).entries }.orEmpty()
    val current = route.index?.let { entries.getOrNull(it)?.template }
    if (current != null && TemplateKind.of(current) == route.kind) return current
    val priorities = entries.mapNotNull { it.template?.priority }
    val priority = priorities.maxOrNull()?.plus(1) ?: 0
    return route.kind.blank(priority, pinnedMonth ?: openMonth, templateSchedules.firstOrNull().orEmpty())
}

/** Saves the form's template into the note (as a new line or in place of the one edited) and goes back. */
fun ShellViewModel.saveTemplate(template: Template) {
    val route = state.value.route as? ShellRoute.TemplateEditor ?: return
    val split = TemplateNote.parse(state.value.categoryNote ?: return)
    val index = route.index?.takeIf { it in split.entries.indices }
    saveCategoryNote(if (index == null) split.add(template) else split.replace(index, template))
    back()
}

/** Removes the template being edited (or the unreadable line being replaced) and goes back. */
fun ShellViewModel.removeTemplate() {
    val index =
        when (val route = state.value.route) {
            is ShellRoute.TemplateEditor -> route.index
            is ShellRoute.TemplateKinds -> route.index
            else -> null
        } ?: return
    val split = TemplateNote.parse(state.value.categoryNote ?: return)
    if (index !in split.entries.indices) return
    saveCategoryNote(split.remove(index))
    back()
}

/** A tile dragged from [from] to [to] on the category page. */
fun ShellViewModel.moveTemplate(
    from: Int,
    to: Int,
) {
    val split = TemplateNote.parse(state.value.categoryNote ?: return)
    if (from !in split.entries.indices) return
    saveCategoryNote(split.move(from, to))
}

/** What the form's template would give this month, shown under the form. */
fun ShellViewModel.previewTemplate(template: Template) {
    val budgetId = state.value.settings.openBudgetId ?: return
    val categoryId = templateCategoryId() ?: return
    val month = editorMonth()
    viewModelScope.launch {
        val amount =
            withContext(
                io,
            ) { library.previewCategoryTemplates(budgetId, categoryId, month, listOf(template)) }.firstOrNull()
        mutate { it.copy(templatePreview = template to amount) }
    }
}

private fun ShellViewModel.loadTemplateSchedules() {
    val budgetId = state.value.settings.openBudgetId ?: return
    viewModelScope.launch {
        val names =
            withContext(io) { library.readSchedules(budgetId) }
                .filter { !it.completed && it.name.isNotBlank() }
                .map { it.name }
                .distinct()
        mutate { it.copy(templateSchedules = names) }
    }
}

// The templates screen (option D, priority lanes): every category's templates by priority, and apply's before and after.

/** The month the templates screen works on: edit mode's month, or the open one. */
private fun ShellViewModel.templatesMonth() = state.value.budgetEdit?.month ?: editorMonth()

/** Works out the lanes again from the saved notes. */
fun ShellViewModel.refreshTemplatePlan() {
    val budgetId = state.value.settings.openBudgetId ?: return
    if (!state.value.formatPrefs.templatesOn) return
    val month = templatesMonth()
    viewModelScope.launch {
        val plan = withContext(io) { library.templatePlan(budgetId, month) }
        mutate { it.copy(templatePlan = plan) }
    }
}

/** A chip tapped: its template's form, or the kinds grid for a line that does not read. Save and back land on the lanes. */
fun ShellViewModel.openTemplateChip(chip: TemplateChip) {
    val budgetId = state.value.settings.openBudgetId ?: return
    viewModelScope.launch {
        val note = withContext(io) { library.readCategoryNote(budgetId, chip.categoryId) }
        val template =
            TemplateNote
                .parse(note)
                .entries
                .getOrNull(chip.index)
                ?.template
        mutate { it.copy(categoryNote = note, templatePreview = null) }
        push(
            if (template == null) {
                ShellRoute.TemplateKinds(chip.categoryId, chip.index)
            } else {
                ShellRoute.TemplateEditor(chip.categoryId, chip.index, TemplateKind.of(template))
            },
        )
        loadTemplateSchedules()
    }
}

/** A chip dropped on another lane: its line takes that lane's [priority], as one undo step. */
fun ShellViewModel.moveTemplateToPriority(
    chip: TemplateChip,
    priority: Int,
) {
    val budgetId = state.value.settings.openBudgetId ?: return
    viewModelScope.launch {
        noteWrites.withLock {
            withContext(io) {
                val split = TemplateNote.parse(library.readCategoryNote(budgetId, chip.categoryId))
                val template = split.entries.getOrNull(chip.index)?.template ?: return@withContext
                library.saveCategoryNote(budgetId, chip.categoryId, split.replace(chip.index, template.withPriority(priority)))
            }
        }
        mutate { it.copy(undoLabel = library.undoLabel(budgetId)) }
        refreshTemplatePlan()
        withContext(io) { runCatching { sync.sendUnsent(budgetId) } }
    }
}

/** Apply or overwrite: first the list of what would change, each category's amount now and after. */
fun ShellViewModel.previewApplyTemplates(overwrite: Boolean) {
    val current = state.value
    val edit = current.budgetEdit
    val budgetId = edit?.budgetId ?: return applyTemplates(overwrite)
    val month = edit.month ?: return
    mutate { it.copy(templateBusy = true, templateConfirm = null) }
    viewModelScope.launch {
        val (result, amounts) = withContext(io) { library.previewTemplates(budgetId, month, overwrite, edit.budgeted) }
        mutate { now ->
            if (result !is TemplateResult.Applied) return@mutate now.copy(templateResult = result, templateBusy = false)
            val categories =
                now
                    .editingShell()
                    ?.groups
                    ?.flatMap { it.categories }
                    .orEmpty()
                    .associateBy { it.id }
            val rows =
                amounts.mapNotNull { (id, after) ->
                    val category = categories[id] ?: return@mapNotNull null
                    if (category.budgetedMinor == after) null else TemplateApplyRow(id, category.name, category.budgetedMinor, after)
                }
            now.copy(templateApplyPreview = TemplateApplyPreview(overwrite, rows), templateBusy = false)
        }
    }
}

/** The before and after list's apply: fills the boxes. */
fun ShellViewModel.confirmApplyPreview() {
    val preview = state.value.templateApplyPreview ?: return
    mutate { it.copy(templateApplyPreview = null) }
    applyTemplates(preview.overwrite, confirmed = true)
}

fun ShellViewModel.keepApplyPreview() {
    mutate { it.copy(templateApplyPreview = null) }
}
