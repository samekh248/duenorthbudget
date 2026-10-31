package app.duenorth.budget.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToLong

/** Words for spec 010's budget templates (`contracts/templates.md`). */
object TemplateCopy {
    const val TITLE = "templates"
    const val CHECK = "check"
    const val APPLY = "apply"
    const val OVERWRITE = "overwrite"
    const val ALL_VALID = "all templates valid"
    const val OVERWRITE_TITLE = "overwrite templated amounts?"
    const val OVERWRITE_CONFIRM = "this replaces every templated amount this month, including ones already budgeted"
    const val OFF = "turn on budget templates in settings first"
    const val NOTE = "note"
    const val APPLIED = "templates applied"
}

data class TemplateError(
    val categoryName: String,
    val line: String,
) {
    /** As desktop shows it: `Fixed: #template fifty dollars`. */
    override fun toString(): String = "$categoryName: $line"
}

/** The templates screen's lanes for one month ([TemplateEngine.plan]). */
data class TemplatePlan(
    /** To budget plus what the templated categories already hold: what an overwrite shares out. */
    val start: Long,
    val lanes: List<TemplateLane>,
    /** Lines that do not read, each with its category. */
    val unreadable: List<TemplateChip>,
)

data class TemplateLane(
    val kind: Kind,
    /** The lane's priority; null for the remainder and goal lanes. */
    val priority: Int?,
    val chips: List<TemplateChip>,
    /** What is left to share after this lane; null for goals, which take nothing. */
    val leftAfter: Long?,
) {
    enum class Kind { PRIORITY, REMAINDER, GOAL }
}

/** One template line in a lane: its category, its place in that category's note, and the money it asks for and gets. */
data class TemplateChip(
    val categoryId: String,
    val categoryName: String,
    val index: Int,
    val template: Template?,
    val line: String,
    val asks: Long?,
    val given: Long?,
    /** Desktop's budget automations own the category: [index] counts its automations, not note lines, and the phone does not change them yet. */
    val managed: Boolean = false,
)

sealed interface TemplateResult {
    data object Valid : TemplateResult

    data class Applied(
        val categories: Int,
    ) : TemplateResult

    data class Invalid(
        val errors: List<TemplateError>,
    ) : TemplateResult

    data class NeedsConfirm(
        val reason: String,
    ) : TemplateResult

    data class Rejected(
        val reason: String,
    ) : TemplateResult
}

/**
 * Port of Actual 26.8.1's goal template run (R12, R16 #11): notes are parsed into `categories.goal_def`, then each
 * visible templated category gets a [TemplateContext]. Priorities run lowest first, each limited by what to-budget
 * has left, then remainder lines share the rest by weight.
 */
class TemplateEngine(
    private val session: SqlSession,
    private val today: LocalDate,
) {
    private data class Category(
        val id: String,
        val name: String,
        val visible: Boolean,
        val income: Boolean,
        val templates: List<Template>,
        val errors: List<String>,
        /** The note's template lines in order, for pointing back at one line. */
        val entries: List<TemplateNote.Entry> = emptyList(),
        /** Desktop's budget automations own it (`template_settings.source = ui`): `goal_def` is the truth, not the note. */
        val managed: Boolean = false,
        /** A managed category holding an automation this port cannot read; runs leave it exactly as it is. */
        val unsupported: Boolean = false,
    ) {
        /** Where each of [templates] sits in [entries]. */
        val entryIndex: List<Int> get() = entries.indices.filter { entries[it].template != null }
    }

    /** What a run works out before it writes anything. */
    private class Computed(
        val contexts: List<Pair<Category, TemplateContext>>,
        val errors: List<TemplateError>,
        /** To budget plus what the templated categories already hold. */
        val start: Long,
        val lanes: List<TemplateLane> = emptyList(),
    )

    /** Lists bad lines; writes nothing. */
    fun check(): TemplateResult {
        val errors = categories().flatMap { category -> category.errors.map { TemplateError(category.name, it) } }
        return if (errors.isEmpty()) TemplateResult.Valid else TemplateResult.Invalid(errors)
    }

    /** Apply (zero categories only) or overwrite (every templated category, after confirm). */
    fun run(
        month: YearMonth,
        overwrite: Boolean,
        confirmed: Boolean = false,
    ): TemplateResult {
        val categories = categories()
        val errors = categories.flatMap { category -> category.errors.map { TemplateError(category.name, it) } }
        if (errors.isNotEmpty()) return TemplateResult.Invalid(errors)
        if (overwrite && !confirmed) return TemplateResult.NeedsConfirm(TemplateCopy.OVERWRITE_CONFIRM)
        val sheet = TemplateSheet(session, today)
        val computed = compute(categories, month, sheet, force = overwrite)
        if (computed.errors.isNotEmpty()) return TemplateResult.Invalid(computed.errors)
        val table = if (sheet.tracking) "reflect_budgets" else "zero_budgets"
        session.transaction {
            storeDefinitions(categories)
            for ((category, context) in computed.contexts) {
                val values = context.values()
                setBudget(table, month, category.id, values.budgeted)
                val existing = existingRow(table, month, category.id) ?: BudgetEdits.assignmentId(month.toActualMonth(), category.id)
                SyncSchema.write(session, table, existing, "goal", values.goal)
                SyncSchema.write(session, table, existing, "long_goal", if (values.longGoal) 1 else null)
            }
            // Desktop clears the goal of every other visible category, which leaves a zero row behind.
            for (category in candidates(categories, sheet)) {
                if (category.templates.isNotEmpty()) continue
                val existing = existingRow(table, month, category.id)
                if (existing == null) {
                    SyncSchema.writeRow(
                        session,
                        table,
                        BudgetEdits.assignmentId(month.toActualMonth(), category.id),
                        "month" to month.toActualMonth(),
                        "category" to category.id,
                        "goal" to null,
                        "long_goal" to null,
                    )
                } else {
                    SyncSchema.write(session, table, existing, "goal", null)
                    SyncSchema.write(session, table, existing, "long_goal", null)
                }
            }
        }
        return TemplateResult.Applied(computed.contexts.size)
    }

    /**
     * What each of [templates] gives category [categoryId] in [month], as desktop's automations editor projects it
     * (`budget/dry-run-category-template`): the category on its own, not held back by other priorities. Remainder,
     * limit and goal lines have no amount of their own and come back null, as does every line when they cannot run.
     */
    fun preview(
        categoryId: String,
        month: YearMonth,
        templates: List<Template>,
    ): List<Long?> {
        val none = templates.map { null }
        val row =
            session
                .query("SELECT IFNULL(is_income, 0) AS is_income FROM categories WHERE id = ?", listOf(categoryId))
                .firstOrNull() ?: return none
        val sheet = TemplateSheet(session, today)
        val category = Category(categoryId, "", visible = true, income = row.bool("is_income"), templates = templates, errors = emptyList())
        val computed = compute(listOf(category), month, sheet, force = true, skipAvailableClamp = true, only = true)
        val context = computed.contexts.firstOrNull()?.second ?: return none
        return templates.mapIndexed { index, template ->
            when (template) {
                is Template.Remainder, is Template.Goal, is Template.Limit -> null
                else -> context.contribution[index] ?: 0L
            }
        }
    }

    /**
     * The goal a run would write for each visible templated category in [month], as (amount, long goal), so the
     * budget rows can measure templates that were never applied this month, as the category page does. Categories
     * whose templates cannot run are left out. Writes nothing.
     */
    fun goals(month: YearMonth): Map<String, Pair<Long, Boolean>> {
        val computed = compute(categories(), month, TemplateSheet(session, today), force = true, keepGoing = true)
        return computed.contexts
            .mapNotNull { (category, context) ->
                val values = context.values()
                values.goal?.let { category.id to (it to values.longGoal) }
            }.toMap()
    }

    /**
     * Every visible category's templates laid out the way an overwrite would run them (the templates screen's
     * lanes): priorities lowest first, each template given what it gets while to budget lasts, then remainder lines
     * by weight, then goals, which budget nothing. Lines that do not read are listed apart. Writes nothing.
     */
    fun plan(month: YearMonth): TemplatePlan {
        val categories = categories()
        val computed = compute(categories, month, TemplateSheet(session, today), force = true, keepGoing = true, laneOut = true)
        val unreadable =
            categories.filter { it.visible && !it.unsupported }.flatMap { category ->
                category.entries.mapIndexedNotNull { index, entry ->
                    if (entry.template ==
                        null
                    ) {
                        chip(category, index, null, null)
                    } else {
                        null
                    }
                }
            }
        val goals =
            computed.contexts.flatMap { (category, _) ->
                category.templates.indices
                    .filter { category.templates[it] is Template.Goal }
                    .map { chip(category, category.entryIndex[it], null, null) }
            }
        val lanes = computed.lanes + if (goals.isEmpty()) emptyList() else listOf(TemplateLane(TemplateLane.Kind.GOAL, null, goals, null))
        return TemplatePlan(computed.start, lanes, unreadable)
    }

    private fun chip(
        category: Category,
        index: Int,
        asks: Long?,
        given: Long?,
    ): TemplateChip {
        val entry = category.entries[index]
        return TemplateChip(category.id, category.name, index, entry.template, entry.line, asks, given, category.managed)
    }

    /** Categories a run looks at: visible ones, income too in a tracking budget, minus any it cannot read. */
    private fun candidates(
        categories: List<Category>,
        sheet: TemplateSheet,
    ): List<Category> = categories.filter { it.visible && (sheet.tracking || !it.income) && !it.unsupported }

    /**
     * `computeTemplates`: builds a context for each templated category that is unbudgeted (or every one when
     * [force]), runs the priorities and then the remainders. A category whose templates fail a check is an error;
     * with [keepGoing] it is skipped instead and the rest still run. [only] runs [categories] as given.
     */
    private fun compute(
        categories: List<Category>,
        month: YearMonth,
        sheet: TemplateSheet,
        force: Boolean,
        skipAvailableClamp: Boolean = false,
        keepGoing: Boolean = false,
        only: Boolean = false,
        laneOut: Boolean = false,
    ): Computed {
        val detailed = sheet.month(month)
        var available = detailed.figures.headerMinor
        val contexts = mutableListOf<Pair<Category, TemplateContext>>()
        val errors = mutableListOf<TemplateError>()
        for (category in if (only) categories else candidates(categories, sheet)) {
            if (category.templates.isEmpty()) continue
            val budgeted = sheet.budget(month, category.id)
            if (budgeted != 0L && !force) continue
            val context =
                try {
                    TemplateContext.init(
                        category.id,
                        category.income,
                        category.templates,
                        month,
                        budgeted,
                        sheet,
                        skipAvailableClamp,
                    ) { template, carried -> schedule(template, month, carried) }
                } catch (failure: TemplateFailure) {
                    errors += TemplateError(category.name, failure.message.orEmpty())
                    continue
                }
            // Money budgeted by hand in a goal-only category is not the templates' to share.
            if (!context.goalOnly) available += budgeted
            available += context.limitExcess()
            contexts += category to context
        }
        if (errors.isNotEmpty() && !keepGoing) return Computed(emptyList(), errors, available)
        val start = available
        val lanes = mutableListOf<TemplateLane>()
        val priorities = contexts.flatMap { it.second.priorities() }.distinct().sorted()
        for (priority in priorities) {
            val availStart = available
            for ((_, context) in contexts) available -= context.runPriority(priority, available, availStart)
            if (laneOut) {
                val chips =
                    contexts.flatMap { (category, context) ->
                        category.templates.indices
                            .filter { category.templates[it].priority == priority && category.templates[it] !is Template.Remainder }
                            .map { chip(category, category.entryIndex[it], context.asked[it] ?: 0L, context.contribution[it] ?: 0L) }
                    }
                lanes += TemplateLane(TemplateLane.Kind.PRIORITY, priority, chips, available)
            }
        }
        val before = contexts.associate { (category, context) -> category.id to HashMap(context.contribution) }
        available = distributeRemainder(contexts.map { it.second }, available)
        if (laneOut) {
            val chips =
                contexts.flatMap { (category, context) ->
                    category.templates.indices
                        .filter { category.templates[it] is Template.Remainder }
                        .map { index ->
                            val given = (context.contribution[index] ?: 0L) - (before[category.id]?.get(index) ?: 0L)
                            chip(category, category.entryIndex[index], null, given)
                        }
                }
            if (chips.isNotEmpty()) lanes += TemplateLane(TemplateLane.Kind.REMAINDER, null, chips, available)
        }
        return Computed(contexts, errors, start, lanes)
    }

    /** The schedule's occurrences this month; one that comes less often is saved for month by month unless `full`. */
    private fun schedule(
        template: Template.Schedule,
        month: YearMonth,
        carried: Long,
    ): Long {
        val id = template.scheduleId
        val row =
            session
                .query(
                    """
                    SELECT r.conditions AS conditions FROM schedules s JOIN rules r ON r.id = s.rule
                    WHERE ${if (id != null) "s.id = ?" else "TRIM(s.name) = ?"} AND IFNULL(s.tombstone, 0) = 0 AND IFNULL(s.completed, 0) = 0
                    """.trimIndent(),
                    listOf(id ?: template.name.trim()),
                ).firstOrNull() ?: return 0L
        val conditions =
            runCatching {
                kotlinx.serialization.json.Json
                    .parseToJsonElement(row.str("conditions").orEmpty()) as JsonArray
            }.getOrNull() ?: return 0L
        val field = { name: String ->
            conditions.mapNotNull { it as? JsonObject }.firstOrNull {
                (it["field"] as? JsonPrimitive)?.content ==
                    name
            }
        }
        val amountValue = field("amount")?.get("value")
        var scheduled =
            when (amountValue) {
                is JsonPrimitive -> amountValue.content.toDoubleOrNull() ?: 0.0
                is JsonObject -> {
                    val a = (amountValue["num1"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0
                    val b = (amountValue["num2"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0
                    jsRound(a + b) / 2.0
                }
                else -> 0.0
            }
        val adjustment = template.adjustment
        if (adjustment != null) {
            scheduled =
                if (adjustment.percent) {
                    scheduled * (1 + adjustment.amount.toDouble() / 100)
                } else {
                    scheduled + (if (scheduled < 0) -1 else 1) * cents(adjustment.amount)
                }
        }
        val amount = abs(jsRound(scheduled))
        val date = field("date") ?: return 0L
        val value = date["value"]
        val inMonth: Int
        val next: LocalDate?
        if (value is JsonObject) {
            val occurrences = ScheduleRecurrence.occurrences(value, month.atDay(1), limit = 64)
            inMonth = occurrences.count { YearMonth.from(it) == month }
            next = occurrences.firstOrNull()
        } else {
            next = (value as? JsonPrimitive)?.content?.let { ScheduleDates.parseIso(it) }
            inMonth = if (next != null && YearMonth.from(next) == month) 1 else 0
        }
        if (inMonth > 0) return amount * inMonth
        if (template.full || next == null) return 0L
        // A one-off date already past (a missed bill not yet marked done) is due now: what is still missing, in one
        // month. Counting months to it would divide by zero or by a negative.
        val left = (ChronoUnit.MONTHS.between(month, YearMonth.from(next)) + 1).coerceAtLeast(1L)
        return ((amount - carried).coerceAtLeast(0L).toDouble() / left).roundToLong()
    }

    private fun categories(): List<Category> {
        val notes =
            session
                .query("SELECT id, note FROM notes")
                .associate { it.str("id").orEmpty() to it.str("note").orEmpty() }
        val schedules by lazy {
            session
                .query("SELECT id, name FROM schedules WHERE IFNULL(tombstone, 0) = 0")
                .associate { it.str("id").orEmpty() to it.str("name") }
        }
        return session
            .query(
                """
                SELECT c.id, c.name, IFNULL(c.hidden, 0) AS hidden, IFNULL(g.hidden, 0) AS group_hidden, IFNULL(c.is_income, 0) AS is_income,
                       c.goal_def, c.template_settings
                FROM categories c LEFT JOIN category_groups g ON g.id = c.cat_group
                WHERE IFNULL(c.tombstone, 0) = 0
                ORDER BY g.sort_order, c.sort_order, c.id
                """.trimIndent(),
            ).map { row ->
                val id = row.str("id").orEmpty()
                val name = row.str("name").orEmpty()
                val visible = !row.bool("hidden") && !row.bool("group_hidden")
                if (TemplateJson.managed(row.str("template_settings"))) {
                    val read = TemplateJson.parse(row.str("goal_def")) { scheduleId -> schedules[scheduleId] }
                    val templates = read.filterNotNull()
                    Category(
                        id = id,
                        name = name,
                        visible = visible,
                        income = row.bool("is_income"),
                        templates = if (read.any { it == null }) emptyList() else templates,
                        errors = emptyList(),
                        entries = if (read.any { it == null }) emptyList() else templates.map { TemplateNote.Entry.of(it) },
                        managed = true,
                        unsupported = read.any { it == null },
                    )
                } else {
                    val entries = TemplateNote.parse(notes[id].orEmpty()).entries
                    Category(
                        id = id,
                        name = name,
                        visible = visible,
                        income = row.bool("is_income"),
                        templates = entries.mapNotNull { it.template },
                        errors = entries.filter { it.template == null }.map { it.line },
                        entries = entries,
                    )
                }
            }
    }

    /**
     * `goal_def` and `template_settings` as desktop stores them before a run. A category the budget automations own
     * keeps its `goal_def`: its note may still hold the `#template` lines it was migrated from, and writing those back
     * would undo every change made since and hand the category back to notes.
     */
    private fun storeDefinitions(categories: List<Category>) {
        for (category in categories) {
            if (category.managed || category.templates.isEmpty()) continue
            SyncSchema.write(session, "categories", category.id, "goal_def", JsonArray(category.templates.map { it.toJson() }).toString())
            SyncSchema.write(session, "categories", category.id, "template_settings", """{"source":"notes"}""")
        }
    }

    private fun existingRow(
        table: String,
        month: YearMonth,
        categoryId: String,
    ): String? =
        session
            .query("SELECT id FROM $table WHERE month = ? AND category = ?", listOf(month.toActualMonth(), categoryId))
            .firstOrNull()
            ?.str("id")

    private fun setBudget(
        table: String,
        month: YearMonth,
        categoryId: String,
        amount: Long,
    ) {
        val existing = existingRow(table, month, categoryId)
        if (existing != null) {
            SyncSchema.write(session, table, existing, "amount", amount)
            return
        }
        SyncSchema.writeRow(
            session,
            table,
            BudgetEdits.assignmentId(month.toActualMonth(), categoryId),
            "month" to month.toActualMonth(),
            "category" to categoryId,
            "amount" to amount,
        )
    }

    private fun toMinor(amount: BigDecimal): Long = amount.movePointRight(2).toDouble().roundToLong()
}

/** Category notes, synced as the `notes` dataset with the category id as the row (T060). */
object CategoryNotes {
    fun read(
        session: SqlSession,
        categoryId: String,
    ): String =
        session
            .query("SELECT note FROM notes WHERE id = ?", listOf(categoryId))
            .firstOrNull()
            ?.str("note")
            .orEmpty()

    fun write(
        session: SqlSession,
        categoryId: String,
        note: String,
    ) {
        SyncSchema.write(session, "notes", categoryId, "note", note.ifBlank { null })
    }

    /** The category's automations when desktop's budget automations own it; null when its note holds its templates. */
    fun automations(
        session: SqlSession,
        categoryId: String,
    ): CategoryAutomations? {
        val row =
            session
                .query("SELECT goal_def, template_settings FROM categories WHERE id = ?", listOf(categoryId))
                .firstOrNull() ?: return null
        if (!TemplateJson.managed(row.str("template_settings"))) return null
        return CategoryAutomations(TemplateJson.read(row.str("goal_def")) { scheduleName(session, it) })
    }

    /** Saves [automations] as the category's `goal_def`, owned by the budget automations from now on. */
    fun saveAutomations(
        session: SqlSession,
        categoryId: String,
        automations: CategoryAutomations,
    ) {
        SyncSchema.write(session, "categories", categoryId, "goal_def", automations.goalDef())
        SyncSchema.write(session, "categories", categoryId, "template_settings", UI_SOURCE)
    }

    /**
     * Hands a notes category to the budget automations, as desktop's migration does: its template lines become its
     * automations and the note keeps them as they were. Null (nothing written) when a line does not read.
     */
    fun migrate(
        session: SqlSession,
        categoryId: String,
        today: LocalDate,
    ): CategoryAutomations? {
        val entries = TemplateNote.parse(read(session, categoryId)).entries
        if (entries.any { it.template == null }) return null
        val schedules =
            session
                .query("SELECT id, name FROM schedules WHERE IFNULL(tombstone, 0) = 0")
                .map { it.str("id").orEmpty() to it.str("name") }
        val automations = CategoryAutomations.fromNote(entries.map { it.template!! }, schedules, today)
        saveAutomations(session, categoryId, automations)
        return automations
    }

    /**
     * Back to note templates, as desktop's un-migrate does: the automations are written into the note in place of its
     * template lines and the note owns the category again. False (nothing written) while it holds an automation the
     * phone cannot read, or a refill with no limit to fold into.
     */
    fun unmigrate(
        session: SqlSession,
        categoryId: String,
    ): Boolean {
        val automations = automations(session, categoryId) ?: return false
        if (!automations.runnable || !TemplateLines.writable(automations.templates)) return false
        val note = TemplateNote.parse(read(session, categoryId))
        val lines = TemplateLines.unparse(automations.templates)
        write(session, categoryId, note.compose(lines.map { TemplateNote.Entry(it, TemplateParser.parseLine(it)) }))
        SyncSchema.write(session, "categories", categoryId, "template_settings", NOTES_SOURCE)
        return true
    }

    private const val UI_SOURCE = """{"source":"ui"}"""
    private const val NOTES_SOURCE = """{"source":"notes"}"""

    private fun scheduleName(
        session: SqlSession,
        scheduleId: String,
    ): String? =
        session
            .query("SELECT name FROM schedules WHERE id = ? AND IFNULL(tombstone, 0) = 0", listOf(scheduleId))
            .firstOrNull()
            ?.str("name")
}
