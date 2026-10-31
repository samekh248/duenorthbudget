package app.duenorth.budget.core

/** Undo labels; the bar shows "undo " + label. */
object UndoCopy {
    const val ASSIGN = "assign"
    const val MOVE = "move"
    const val BUDGET_EDIT = "budget edit"
    const val HOLD = "hold"
    const val CARRYOVER = "rollover"
    const val CATEGORY = "category change"
    const val ADD_TRANSACTION = "add transaction"
    const val SAVE_TRANSACTION = "transaction"
    const val CATEGORIZE = "categorize"
    const val DELETE_TRANSACTION = "delete"
    const val SPLIT = "split"
    const val UNSPLIT = "unsplit"
    const val RECONCILE = "reconcile"
    const val POST_SCHEDULE = "post"
    const val SKIP_SCHEDULE = "skip"
    const val SAVE_SCHEDULE = "schedule"
    const val DELETE_SCHEDULE = "delete schedule"
    const val RENAME_PAYEE = "rename payee"
    const val IMPORT = "import"
    const val AUTO_POST = "auto-post"
    const val TEMPLATES = "templates"
    const val CLEANUP = "end-of-month cleanup"
    const val CATEGORY_NOTE = "note"
    const val AUTOMATIONS = "automations"
    const val TO_AUTOMATIONS = "switch to automations"
    const val TO_NOTES = "back to notes"
    const val BANK_LINK = "bank link"
    const val CLEAR = "clear"
    const val UNCLEAR = "unclear"
    const val MATCH = "match"
    const val NOT_A_MATCH = "not a match"
}

/** One person-level action, as the cells it wrote. */
data class JournalStep(
    val budgetId: String,
    val label: String,
    val cells: List<JournalCell>,
)

sealed interface UndoResult {
    data class Undone(
        val label: String,
    ) : UndoResult

    /** A cell changed after the step, usually by sync. Nothing was written; a conflict was recorded. */
    data class Conflict(
        val label: String,
    ) : UndoResult

    data object Empty : UndoResult
}

/**
 * Undo for spec 010 (`contracts/undo.md`). Every write a step makes goes through
 * [SyncSchema.recordLocal], which reports each cell's old and new value here.
 * Undo writes the old values back through the same funnel, so it syncs like any edit.
 */
class EditJournal(
    private val cap: Int = MAX_STEPS,
) {
    private val steps = ArrayDeque<JournalStep>()
    private var budgetId: String? = null

    /**
     * Runs [block] as one step. A step inside a step joins the outer one.
     * A block that writes nothing leaves no step.
     */
    fun <T> step(
        session: SqlSession,
        budgetId: String,
        label: String,
        block: () -> T,
    ): T {
        val cells = SyncSchema.beginCapture(session) ?: return block()
        try {
            val box = arrayOfNulls<Any?>(1)
            session.transaction { box[0] = block() }
            if (cells.isNotEmpty()) push(JournalStep(budgetId, label, collapse(cells)))
            @Suppress("UNCHECKED_CAST")
            return box[0] as T
        } finally {
            SyncSchema.endCapture(session)
        }
    }

    @Synchronized
    fun canUndo(budgetId: String): Boolean = this.budgetId == budgetId && steps.isNotEmpty()

    @Synchronized
    fun newestLabel(budgetId: String): String? = if (this.budgetId == budgetId) steps.firstOrNull()?.label else null

    @Synchronized
    fun newest(budgetId: String): JournalStep? = if (this.budgetId == budgetId) steps.firstOrNull() else null

    @Synchronized
    fun size(budgetId: String): Int = if (this.budgetId == budgetId) steps.size else 0

    /** Budget switch: the stack belongs to one open budget. */
    @Synchronized
    fun switchTo(budgetId: String?) {
        if (this.budgetId == budgetId) return
        steps.clear()
        this.budgetId = budgetId
    }

    @Synchronized
    fun clear() {
        steps.clear()
    }

    fun undo(
        session: SqlSession,
        budgetId: String,
        clock: SyncClock,
    ): UndoResult {
        val step = pop(budgetId) ?: return UndoResult.Empty
        val cells = step.cells
        var result: UndoResult = UndoResult.Undone(step.label)
        session.transaction {
            val changed =
                cells.mapNotNull { cell ->
                    val current = SyncSchema.readCell(session, cell.dataset, cell.row, cell.column)?.second
                    if (current != cell.new) cell to current else null
                }
            if (changed.isNotEmpty()) {
                changed.forEach { (cell, current) -> SyncSchema.recordConflict(session, cell, current) }
                result = UndoResult.Conflict(step.label)
                return@transaction
            }
            val tombstoned = HashSet<Pair<String, String>>()
            for (cell in cells.asReversed()) {
                if (cell.rowExisted) {
                    SyncSchema.recordLocal(session, clock, cell.dataset, cell.row, cell.column, cell.old)
                    continue
                }
                if (SyncSchema.hasTombstone(cell.dataset)) {
                    if (tombstoned.add(cell.dataset to cell.row)) {
                        SyncSchema.recordLocal(session, clock, cell.dataset, cell.row, "tombstone", "1")
                    }
                    continue
                }
                if (SyncSchema.isIdentity(cell.column)) continue
                val emptied = if (cell.column in SyncSchema.numericColumns) "0" else null
                SyncSchema.recordLocal(session, clock, cell.dataset, cell.row, cell.column, emptied)
            }
        }
        return result
    }

    @Synchronized
    private fun push(step: JournalStep) {
        if (budgetId != step.budgetId) {
            steps.clear()
            budgetId = step.budgetId
        }
        steps.addFirst(step)
        while (steps.size > cap) steps.removeLast()
    }

    @Synchronized
    private fun pop(budgetId: String): JournalStep? = if (this.budgetId == budgetId) steps.removeFirstOrNull() else null

    /** One entry per cell, in first-write order: the first old value and the last new one. */
    private fun collapse(cells: List<JournalCell>): List<JournalCell> {
        val byCell = LinkedHashMap<Triple<String, String, String>, JournalCell>()
        for (cell in cells) {
            val key = Triple(cell.dataset, cell.row, cell.column)
            val first = byCell[key]
            byCell[key] = first?.copy(new = cell.new) ?: cell
        }
        // A row created in this step counts as created for every one of its cells, with no old value.
        val created =
            byCell.values
                .filter { !it.rowExisted }
                .map { it.dataset to it.row }
                .toSet()
        return byCell.values.map { if ((it.dataset to it.row) in created) it.copy(old = null, rowExisted = false) else it }
    }

    companion object {
        const val MAX_STEPS = 100
    }
}
