package app.duenorth.budget

import app.duenorth.budget.core.Template
import app.duenorth.budget.core.TemplateChip
import app.duenorth.budget.core.TemplateKind
import app.duenorth.budget.core.TemplateNote
import app.duenorth.budget.core.TemplatePlan
import app.duenorth.budget.core.TemplateResult
import app.duenorth.budget.core.withPriority
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The templates' own state, inside [ShellUiState.templates]. */
data class TemplatesUi(
    /** What the last check or apply found. */
    val result: TemplateResult? = null,
    val busy: Boolean = false,
    /** Why overwrite asks first, while it asks. */
    val confirm: String? = null,
    /** The open category's note, which holds its templates. */
    val note: String? = null,
    /** What each line of [note] asks for in the open month; null for a line that is not a template. */
    val amounts: List<Long?> = emptyList(),
    /** The form's template and what it would give this month. */
    val preview: Pair<Template, Long?>? = null,
    /** The schedule names a schedule template can pick from. */
    val schedules: List<String> = emptyList(),
    /** The templates screen's lanes: every category's templates by priority. */
    val plan: TemplatePlan? = null,
    /** Apply's before and after list, while it shows. */
    val applyPreview: TemplateApplyPreview? = null,
)

// Spec 010 US5 (T062): budget templates from category notes. See contracts/templates.md.

/**
 * Templates: checking and applying them, the template tiles on a category page with their kinds grid and form, and
 * the templates screen's priority lanes.
 */
class TemplatesHolder internal constructor(
    private val host: ShellHost,
) {
    /** Note writes, one at a time and in order. */
    private val noteWrites = Mutex()

    private val state get() = host.state.value

    /** Home `•••` → templates; checks the notes straight away. */
    fun open() {
        if (!state.formatPrefs.templatesOn) return
        host.push(ShellRoute.Templates)
        host.mutate { it.copy(templates = it.templates.copy(result = null, confirm = null, applyPreview = null)) }
        check()
        refreshPlan()
    }

    fun check() {
        val budgetId = state.settings.openBudgetId ?: return
        host.mutate { it.copy(templates = it.templates.copy(busy = true)) }
        host.scope.launch {
            val result = withContext(host.io) { host.library.checkTemplates(budgetId) }
            host.mutate { it.copy(templates = it.templates.copy(result = result, busy = false)) }
        }
    }

    /** Apply fills zero categories; overwrite asks first, then refills every templated category. */
    fun apply(
        overwrite: Boolean,
        confirmed: Boolean = false,
    ) {
        // Opened from edit mode, templates fill its boxes and save with done; overwriting needs no asking there.
        if (state.budgetEdit != null) {
            host.applyTemplatesInBudgetEdit(overwrite)
            return
        }
        val budgetId = state.settings.openBudgetId ?: return
        val month = host.editorMonth()
        host.mutate { it.copy(templates = it.templates.copy(busy = true, confirm = null)) }
        host.scope.launch {
            val result = withContext(host.io) { host.library.applyTemplates(budgetId, month, overwrite, confirmed) }
            when (result) {
                is TemplateResult.NeedsConfirm ->
                    host.mutate {
                        it.copy(
                            templates = it.templates.copy(confirm = result.reason, busy = false),
                        )
                    }
                is TemplateResult.Applied -> {
                    host.mutate { it.copy(templates = it.templates.copy(result = result, busy = false)) }
                    host.afterMonthWrite(budgetId)
                }
                else -> host.mutate { it.copy(templates = it.templates.copy(result = result, busy = false)) }
            }
        }
    }

    fun confirmOverwrite() = apply(overwrite = true, confirmed = true)

    fun keep() {
        host.mutate { it.copy(templates = it.templates.copy(confirm = null)) }
    }

    internal fun loadNote(categoryId: String) {
        val budgetId = state.settings.openBudgetId ?: return
        if (!state.formatPrefs.templatesOn) return
        host.scope.launch {
            val note = withContext(host.io) { host.library.readCategoryNote(budgetId, categoryId) }
            host.mutate { current ->
                if ((current.route as? ShellRoute.CategoryBudget)?.categoryId ==
                    categoryId
                ) {
                    current.copy(templates = current.templates.copy(note = note))
                } else {
                    current
                }
            }
        }
    }

    /** The category the open template page (the category page, the kinds grid or a template's form) belongs to. */
    private fun openCategoryId(): String? =
        when (val route = state.route) {
            is ShellRoute.CategoryBudget -> route.categoryId
            is ShellRoute.TemplateKinds -> route.categoryId
            is ShellRoute.TemplateEditor -> route.categoryId
            else -> null
        }

    /** Saves the open category's note as one undo step; templates read it on the next check. */
    fun saveNote(note: String) {
        val budgetId = state.settings.openBudgetId ?: return
        val categoryId = openCategoryId() ?: return
        host.mutate { it.copy(templates = it.templates.copy(note = note)) }
        host.scope.launch {
            // Tiles can change faster than a write lands (drag, then remove); the last change must win.
            noteWrites.withLock { withContext(host.io) { host.library.saveCategoryNote(budgetId, categoryId, note) } }
            host.mutate { it.copy(undoLabel = host.library.undoLabel(budgetId)) }
            withContext(host.io) { runCatching { host.sync.sendUnsent(budgetId) } }
        }
    }

    // The template editor (option A, template tiles): tiles on the category page, a grid of kinds, and one form.

    /** The note's free text changed; its template lines stay as they are. */
    fun saveNoteText(text: String) {
        val note = state.templates.note ?: return
        saveNote(TemplateNote.parse(note).withText(text))
    }

    /** Works out what each template line in the open category's note asks for in the open month. */
    fun refreshAmounts() {
        val budgetId = state.settings.openBudgetId ?: return
        val categoryId = openCategoryId() ?: return
        val note = state.templates.note ?: return
        val entries = TemplateNote.parse(note).entries
        val month = host.editorMonth()
        host.scope.launch {
            val templates = entries.mapNotNull { it.template }
            val asks = withContext(host.io) { host.library.previewCategoryTemplates(budgetId, categoryId, month, templates) }.iterator()
            val amounts = entries.map { entry -> if (entry.template != null && asks.hasNext()) asks.next() else null }
            host.mutate { if (it.templates.note == note) it.copy(templates = it.templates.copy(amounts = amounts)) else it }
        }
    }

    /** Category page → add template, or (with [index]) a line that does not read: the grid of kinds. */
    fun openKinds(index: Int? = null) {
        val categoryId = openCategoryId() ?: return
        host.push(ShellRoute.TemplateKinds(categoryId, index))
        loadSchedules()
    }

    /** A tile tapped: its form, or the kinds grid for a line that does not read. */
    fun openTemplate(index: Int) {
        val categoryId = openCategoryId() ?: return
        val entry = state.templates.note?.let { TemplateNote.parse(it).entries.getOrNull(index) } ?: return
        val template = entry.template ?: return openKinds(index)
        host.mutate { it.copy(templates = it.templates.copy(preview = null)) }
        host.push(ShellRoute.TemplateEditor(categoryId, index, TemplateKind.of(template)))
        loadSchedules()
    }

    /** A kind picked from the grid: its form takes the grid's place, so save and back both land on the category. */
    fun pickKind(kind: TemplateKind) {
        val route = state.route as? ShellRoute.TemplateKinds ?: return
        host.mutate { it.copy(templates = it.templates.copy(preview = null)) }
        host.replaceRoute(ShellRoute.TemplateEditor(route.categoryId, route.index, kind))
    }

    /** Saves the form's template into the note (as a new line or in place of the one edited) and goes back. */
    fun save(template: Template) {
        val route = state.route as? ShellRoute.TemplateEditor ?: return
        val split = TemplateNote.parse(state.templates.note ?: return)
        val index = route.index?.takeIf { it in split.entries.indices }
        saveNote(if (index == null) split.add(template) else split.replace(index, template))
        host.back()
    }

    /** Removes the template being edited (or the unreadable line being replaced) and goes back. */
    fun remove() {
        val index =
            when (val route = state.route) {
                is ShellRoute.TemplateEditor -> route.index
                is ShellRoute.TemplateKinds -> route.index
                else -> null
            } ?: return
        val split = TemplateNote.parse(state.templates.note ?: return)
        if (index !in split.entries.indices) return
        saveNote(split.remove(index))
        host.back()
    }

    /** A tile dragged from [from] to [to] on the category page. */
    fun move(
        from: Int,
        to: Int,
    ) {
        val split = TemplateNote.parse(state.templates.note ?: return)
        if (from !in split.entries.indices) return
        saveNote(split.move(from, to))
    }

    /** What the form's template would give this month, shown under the form. */
    fun preview(template: Template) {
        val budgetId = state.settings.openBudgetId ?: return
        val categoryId = openCategoryId() ?: return
        val month = host.editorMonth()
        host.scope.launch {
            val amount =
                withContext(
                    host.io,
                ) { host.library.previewCategoryTemplates(budgetId, categoryId, month, listOf(template)) }.firstOrNull()
            host.mutate { it.copy(templates = it.templates.copy(preview = template to amount)) }
        }
    }

    private fun loadSchedules() {
        val budgetId = state.settings.openBudgetId ?: return
        host.scope.launch {
            val names =
                withContext(host.io) { host.library.readSchedules(budgetId) }
                    .filter { !it.completed && it.name.isNotBlank() }
                    .map { it.name }
                    .distinct()
            host.mutate { it.copy(templates = it.templates.copy(schedules = names)) }
        }
    }

    // The templates screen (option D, priority lanes): every category's templates by priority, and apply's before and after.

    /** The month the templates screen works on: edit mode's month, or the open one. */
    private fun planMonth() = state.budgetEdit?.month ?: host.editorMonth()

    /** Works out the lanes again from the saved notes. */
    fun refreshPlan() {
        val budgetId = state.settings.openBudgetId ?: return
        if (!state.formatPrefs.templatesOn) return
        val month = planMonth()
        host.scope.launch {
            val plan = withContext(host.io) { host.library.templatePlan(budgetId, month) }
            host.mutate { it.copy(templates = it.templates.copy(plan = plan)) }
        }
    }

    /** A chip tapped: its template's form, or the kinds grid for a line that does not read. Save and back land on the lanes. */
    fun openChip(chip: TemplateChip) {
        val budgetId = state.settings.openBudgetId ?: return
        host.scope.launch {
            val note = withContext(host.io) { host.library.readCategoryNote(budgetId, chip.categoryId) }
            val template =
                TemplateNote
                    .parse(note)
                    .entries
                    .getOrNull(chip.index)
                    ?.template
            host.mutate { it.copy(templates = it.templates.copy(note = note, preview = null)) }
            host.push(
                if (template == null) {
                    ShellRoute.TemplateKinds(chip.categoryId, chip.index)
                } else {
                    ShellRoute.TemplateEditor(chip.categoryId, chip.index, TemplateKind.of(template))
                },
            )
            loadSchedules()
        }
    }

    /** A chip dropped on another lane: its line takes that lane's [priority], as one undo step. */
    fun moveToPriority(
        chip: TemplateChip,
        priority: Int,
    ) {
        val budgetId = state.settings.openBudgetId ?: return
        host.scope.launch {
            noteWrites.withLock {
                withContext(host.io) {
                    val split = TemplateNote.parse(host.library.readCategoryNote(budgetId, chip.categoryId))
                    val template = split.entries.getOrNull(chip.index)?.template ?: return@withContext
                    host.library.saveCategoryNote(budgetId, chip.categoryId, split.replace(chip.index, template.withPriority(priority)))
                }
            }
            host.mutate { it.copy(undoLabel = host.library.undoLabel(budgetId)) }
            refreshPlan()
            withContext(host.io) { runCatching { host.sync.sendUnsent(budgetId) } }
        }
    }

    /** Apply or overwrite: first the list of what would change, each category's amount now and after. */
    fun previewApply(overwrite: Boolean) {
        val current = state
        val edit = current.budgetEdit
        val budgetId = edit?.budgetId ?: return apply(overwrite)
        val month = edit.month ?: return
        host.mutate { it.copy(templates = it.templates.copy(busy = true, confirm = null)) }
        host.scope.launch {
            val (result, amounts) = withContext(host.io) { host.library.previewTemplates(budgetId, month, overwrite, edit.budgeted) }
            host.mutate { now ->
                if (result !is TemplateResult.Applied) return@mutate now.copy(templates = now.templates.copy(result = result, busy = false))
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
                now.copy(templates = now.templates.copy(applyPreview = TemplateApplyPreview(overwrite, rows), busy = false))
            }
        }
    }

    /** The before and after list's apply: fills the boxes. */
    fun confirmApplyPreview() {
        val preview = state.templates.applyPreview ?: return
        host.mutate { it.copy(templates = it.templates.copy(applyPreview = null)) }
        apply(preview.overwrite, confirmed = true)
    }

    fun keepApplyPreview() {
        host.mutate { it.copy(templates = it.templates.copy(applyPreview = null)) }
    }
}

/** The template a form starts from: the line being edited, or a new one of its kind after the category's others. */
fun ShellUiState.templateDraftStart(route: ShellRoute.TemplateEditor): Template {
    val entries = templates.note?.let { TemplateNote.parse(it).entries }.orEmpty()
    val current = route.index?.let { entries.getOrNull(it)?.template }
    if (current != null && TemplateKind.of(current) == route.kind) return current
    val priorities = entries.mapNotNull { it.template?.priority }
    val priority = priorities.maxOrNull()?.plus(1) ?: 0
    return route.kind.blank(priority, pinnedMonth ?: openMonth, templates.schedules.firstOrNull().orEmpty())
}
