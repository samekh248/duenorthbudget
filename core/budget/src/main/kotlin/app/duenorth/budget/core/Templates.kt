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

/** One parsed line of a category note, with Actual's stored (`goal_def`) form. */
sealed interface Template {
    /** Null for remainder and goal lines, which run outside the priority order. */
    val priority: Int?

    fun toJson(): JsonObject

    data class Simple(
        val monthly: BigDecimal?,
        val limit: BigDecimal?,
        override val priority: Int,
    ) : Template {
        override fun toJson() =
            obj(
                "type" to "simple",
                "monthly" to monthly,
                "limit" to
                    limit?.let {
                        obj("amount" to it, "hold" to null, "period" to "monthly", "start" to null)
                    },
                "priority" to priority,
                "directive" to "template",
            )
    }

    data class By(
        val amount: BigDecimal,
        val month: YearMonth,
        /** Months between targets when it repeats; null when it does not. */
        val repeatMonths: Int?,
        val annual: Boolean,
        val repeat: Int?,
        override val priority: Int,
    ) : Template {
        override fun toJson() =
            obj(
                "type" to "by",
                "amount" to amount,
                "month" to month.toString(),
                *(if (repeat != null) arrayOf("repeat" to repeat) else emptyArray()),
                "annual" to annual,
                "from" to null,
                "priority" to priority,
                "directive" to "template",
            )
    }

    data class Schedule(
        val name: String,
        val full: Boolean,
        override val priority: Int,
    ) : Template {
        override fun toJson() =
            obj(
                "type" to "schedule",
                "name" to name,
                "priority" to priority,
                "directive" to "template",
                "full" to if (full) true else null,
            )
    }

    data class Percentage(
        val percent: BigDecimal,
        val previous: Boolean,
        /** A category name, `all income`, or `available funds`. */
        val category: String,
        override val priority: Int,
    ) : Template {
        override fun toJson() =
            obj(
                "type" to "percentage",
                "percent" to percent,
                "previous" to previous,
                "category" to category,
                "priority" to priority,
                "directive" to "template",
            )
    }

    data class Average(
        val months: Int,
        override val priority: Int,
    ) : Template {
        override fun toJson() = obj("type" to "average", "numMonths" to months, "priority" to priority, "directive" to "template")
    }

    data class Remainder(
        val weight: BigDecimal,
    ) : Template {
        override val priority: Int? = null

        override fun toJson() =
            obj("type" to "remainder", "priority" to null, "directive" to "template", "weight" to weight, "limit" to null)
    }

    data class Goal(
        val amount: BigDecimal,
    ) : Template {
        override val priority: Int? = null

        override fun toJson() = obj("type" to "goal", "amount" to amount, "priority" to null, "directive" to "goal")
    }

    companion object {
        private fun obj(vararg pairs: Pair<String, Any?>): JsonObject =
            JsonObject(linkedMapOf(*pairs.map { (k, v) -> k to json(v) }.toTypedArray()))

        private fun json(value: Any?): JsonElement =
            when (value) {
                null -> JsonNull
                is JsonElement -> value
                is BigDecimal -> JsonPrimitive(value.stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it })
                is Number -> JsonPrimitive(value)
                is Boolean -> JsonPrimitive(value)
                else -> JsonPrimitive(value.toString())
            }
    }
}

data class TemplateError(
    val categoryName: String,
    val line: String,
) {
    /** As desktop shows it: `Fixed: #template fifty dollars`. */
    override fun toString(): String = "$categoryName: $line"
}

/** Actual 26.8.1's note template grammar, for the forms in `contracts/templates.md`. */
object TemplateParser {
    private val head = Regex("""^#(template)(?:-(\d+))?\b\s*(.*)$|^#(goal)\b\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val number = Regex("""^\$?(\d+(?:\.\d+)?)$""")

    /** True for a line Actual reads as a template or goal. */
    fun isTemplateLine(line: String): Boolean = head.containsMatchIn(line.trim())

    /** Every template line in [note]; a line that does not parse comes back as its text. */
    fun parse(note: String): List<Result<Template>> =
        note
            .lines()
            .map { it.trim() }
            .filter { isTemplateLine(it) }
            .map { line -> parseLine(line)?.let { Result.success(it) } ?: Result.failure(IllegalArgumentException(line)) }

    fun parseLine(line: String): Template? {
        val match = head.find(line.trim()) ?: return null
        if (match.groupValues[4].isNotEmpty()) {
            val amount = amount(match.groupValues[5].trim()) ?: return null
            return Template.Goal(amount)
        }
        val priority = match.groupValues[2].toIntOrNull() ?: 0
        val words =
            match.groupValues[3]
                .trim()
                .split(Regex("""\s+"""))
                .filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        return when (words[0].lowercase()) {
            "up" -> upTo(words, 0)?.let { Template.Simple(null, it, priority) }
            "schedule" -> schedule(words.drop(1), priority)
            "average" -> average(words.drop(1), priority)
            "remainder" ->
                when (words.size) {
                    1 -> Template.Remainder(BigDecimal.ONE)
                    2 -> amount(words[1])?.let { Template.Remainder(it) }
                    else -> null
                }
            else -> amountLed(words, priority)
        }
    }

    private fun amountLed(
        words: List<String>,
        priority: Int,
    ): Template? {
        val first = words[0]
        if (first.endsWith("%")) return percentage(words, priority)
        val value = amount(first) ?: return null
        if (words.size == 1) return Template.Simple(value, null, priority)
        return when (words[1].lowercase()) {
            "up" -> if (words.size == 4) upTo(words, 1)?.let { Template.Simple(value, it, priority) } else null
            "by" -> by(value, words.drop(2), priority)
            else -> null
        }
    }

    /** `up to N` starting at [at]. */
    private fun upTo(
        words: List<String>,
        at: Int,
    ): BigDecimal? {
        if (words.size != at + 3) return null
        if (!words[at].equals("up", true) || !words[at + 1].equals("to", true)) return null
        return amount(words[at + 2])
    }

    /** `by YYYY-MM [repeat every month|year|N months|N years]`. */
    private fun by(
        value: BigDecimal,
        rest: List<String>,
        priority: Int,
    ): Template? {
        val month = rest.firstOrNull()?.let { runCatching { YearMonth.parse(it) }.getOrNull() } ?: return null
        if (rest.size == 1) return Template.By(value, month, null, annual = false, repeat = null, priority = priority)
        if (rest.size < 4 || !rest[1].equals("repeat", true) || !rest[2].equals("every", true)) return null
        val tail = rest.drop(3).map { it.lowercase() }
        return when {
            tail == listOf("year") -> Template.By(value, month, 12, annual = true, repeat = null, priority = priority)
            tail == listOf("month") -> Template.By(value, month, 1, annual = false, repeat = 1, priority = priority)
            tail.size == 2 && tail[1] == "years" -> {
                // `repeat every 1 year` is an error in Actual; plural needs 2 or more.
                val n = tail[0].toIntOrNull()?.takeIf { it >= 2 } ?: return null
                Template.By(value, month, 12 * n, annual = true, repeat = n, priority = priority)
            }
            tail.size == 2 && tail[1] == "months" -> {
                val n = tail[0].toIntOrNull()?.takeIf { it >= 2 } ?: return null
                Template.By(value, month, n, annual = false, repeat = n, priority = priority)
            }
            else -> null
        }
    }

    /** `schedule [full] Name` or `schedule Name full`. */
    private fun schedule(
        rest: List<String>,
        priority: Int,
    ): Template? {
        if (rest.isEmpty()) return null
        var words = rest
        var full = false
        if (words.first().equals("full", true) && words.size > 1) {
            full = true
            words = words.drop(1)
        } else if (words.last().equals("full", true) && words.size > 1) {
            full = true
            words = words.dropLast(1)
        }
        return Template.Schedule(words.joinToString(" "), full, priority)
    }

    private fun average(
        rest: List<String>,
        priority: Int,
    ): Template? {
        if (rest.size != 2 || !rest[1].lowercase().startsWith("month")) return null
        val n = rest[0].toIntOrNull()?.takeIf { it > 0 } ?: return null
        return Template.Average(n, priority)
    }

    /** `N% of [previous] Category` (or `of all income`, `of available funds`, `previous` at the end). */
    private fun percentage(
        words: List<String>,
        priority: Int,
    ): Template? {
        val percent = amount(words[0].removeSuffix("%")) ?: return null
        if (words.size < 3 || !words[1].equals("of", true)) return null
        var rest = words.drop(2)
        var previous = false
        if (rest.first().equals("previous", true)) {
            previous = true
            rest = rest.drop(1)
        } else if (rest.last().equals("previous", true)) {
            previous = true
            rest = rest.dropLast(1)
        }
        if (rest.isEmpty()) return null
        return Template.Percentage(percent, previous, rest.joinToString(" "), priority)
    }

    private fun amount(text: String): BigDecimal? =
        number
            .find(text.replace(",", ""))
            ?.groupValues
            ?.get(1)
            ?.toBigDecimalOrNull()
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
 * Port of Actual 26.8.1's goal template run (R12, R16 #11): notes are parsed into
 * `categories.goal_def`, then each visible templated category is budgeted, priorities
 * lowest first and each limited by what to-budget has left, remainder lines last by weight.
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
        /** A managed category holding an automation this port cannot run yet; runs leave it exactly as it is. */
        val unsupported: Boolean = false,
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
        val detailed = ShellReader.loadDetailed(session, month)
        val stats = detailed.categories
        val table = if (tracking()) "reflect_budgets" else "zero_budgets"
        val applied = mutableListOf<Category>()
        session.transaction {
            storeDefinitions(categories)
            // A category holding an automation this port cannot run is left untouched, its goal included.
            val candidates = categories.filter { it.visible && !it.income && !it.unsupported }
            val targets =
                candidates.filter { category ->
                    category.templates.isNotEmpty() && (overwrite || (stats[category.id]?.budgetedMinor ?: 0L) == 0L)
                }
            var available = detailed.figures.headerMinor + targets.sumOf { stats[it.id]?.budgetedMinor ?: 0L }
            val funded = LinkedHashMap<String, Long>()
            val goals = HashMap<String, Long>()
            targets.forEach { funded[it.id] = 0L }
            val priorities =
                targets
                    .flatMap { it.templates.mapNotNull { template -> template.priority } }
                    .distinct()
                    .sorted()
            for (priority in priorities) {
                for (category in targets) {
                    val wanted =
                        category.templates
                            .filter { it.priority == priority }
                            .sumOf { amountFor(it, category, month, stats, detailed.figures.headerMinor) }
                    if (category.templates.any { it.priority == priority }) goals[category.id] = (goals[category.id] ?: 0L) + wanted
                    val given = wanted.coerceAtMost(available.coerceAtLeast(0L)).coerceAtLeast(0L)
                    funded[category.id] = funded.getValue(category.id) + given
                    available -= given
                }
            }
            val remainders =
                targets.mapNotNull { category ->
                    category.templates.filterIsInstance<Template.Remainder>().firstOrNull()?.let {
                        category to
                            it
                    }
                }
            val totalWeight = remainders.fold(BigDecimal.ZERO) { sum, (_, template) -> sum + template.weight }
            // `remainder 0` lines alone share nothing, rather than dividing by a zero weight.
            if (remainders.isNotEmpty() && available > 0 && totalWeight.signum() > 0) {
                val perWeight = available.toDouble() / totalWeight.toDouble()
                var left = available
                remainders.forEachIndexed { index, (category, template) ->
                    val share = if (index == remainders.lastIndex) left else (perWeight * template.weight.toDouble()).roundToLong()
                    funded[category.id] = funded.getValue(category.id) + share
                    left -= share
                }
            }
            for (category in targets) {
                val row = BudgetEdits.assignmentId(month.toActualMonth(), category.id)
                setBudget(table, month, category.id, funded.getValue(category.id))
                val existing = existingRow(table, month, category.id) ?: row
                val goal = category.templates.filterIsInstance<Template.Goal>().firstOrNull()
                when {
                    goal != null -> {
                        SyncSchema.write(session, table, existing, "goal", toMinor(goal.amount))
                        SyncSchema.write(session, table, existing, "long_goal", 1)
                    }
                    goals.containsKey(category.id) -> {
                        SyncSchema.write(session, table, existing, "goal", goals.getValue(category.id))
                        SyncSchema.write(session, table, existing, "long_goal", null)
                    }
                    else -> {
                        SyncSchema.write(session, table, existing, "goal", null)
                        SyncSchema.write(session, table, existing, "long_goal", null)
                    }
                }
            }
            // Desktop clears the goal of every other visible category, which leaves a zero row behind.
            for (category in candidates) {
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
            applied += targets
        }
        return TemplateResult.Applied(applied.size)
    }

    /**
     * What each of [templates] asks for in [month] on its own, for category [categoryId], before priorities share out
     * to budget. Remainder and goal lines ask for nothing of their own and come back null.
     */
    fun preview(
        categoryId: String,
        month: YearMonth,
        templates: List<Template>,
    ): List<Long?> {
        val detailed = ShellReader.loadDetailed(session, month)
        val category = Category(categoryId, "", visible = true, income = false, templates = templates, errors = emptyList())
        return templates.map { template ->
            when (template) {
                is Template.Remainder, is Template.Goal -> null
                else -> amountFor(template, category, month, detailed.categories, detailed.figures.headerMinor)
            }
        }
    }

    /**
     * The goal a run would write for each visible templated category in [month], as (amount, long goal): a `#goal`
     * line's target, else what its priority lines ask. Lets the budget rows measure templates that were never
     * applied this month, as the category page does. Writes nothing.
     */
    fun goals(month: YearMonth): Map<String, Pair<Long, Boolean>> {
        val detailed = ShellReader.loadDetailed(session, month)
        return categories()
            .filter { it.visible && !it.income && it.templates.isNotEmpty() }
            .mapNotNull { category ->
                val goal = category.templates.filterIsInstance<Template.Goal>().firstOrNull()
                val asked = category.templates.filter { it.priority != null }
                when {
                    goal != null -> category.id to (toMinor(goal.amount) to true)
                    asked.isNotEmpty() ->
                        category.id to
                            (asked.sumOf { amountFor(it, category, month, detailed.categories, detailed.figures.headerMinor) } to false)
                    else -> null
                }
            }.toMap()
    }

    /**
     * Every visible category's templates laid out the way an overwrite would run them (the templates screen's
     * lanes): priorities lowest first, each template given what it asks while to budget lasts, then remainder lines
     * by weight, then goals, which budget nothing. Lines that do not read are listed apart. Writes nothing.
     */
    fun plan(month: YearMonth): TemplatePlan {
        val categories = categories().filter { it.visible && !it.income && !it.unsupported }
        val detailed = ShellReader.loadDetailed(session, month)
        val stats = detailed.categories
        val templated = categories.filter { it.templates.isNotEmpty() }
        val start = detailed.figures.headerMinor + templated.sumOf { stats[it.id]?.budgetedMinor ?: 0L }
        var available = start

        fun chips(predicate: (Template) -> Boolean): List<Pair<Category, Int>> =
            categories.flatMap { category ->
                category.entries.mapIndexedNotNull { index, entry -> entry.template?.takeIf(predicate)?.let { category to index } }
            }

        fun chip(
            category: Category,
            index: Int,
            asks: Long?,
            given: Long?,
        ): TemplateChip {
            val entry = category.entries[index]
            return TemplateChip(category.id, category.name, index, entry.template, entry.line, asks, given)
        }
        val lanes = mutableListOf<TemplateLane>()
        val priorities = chips { it.priority != null }.mapNotNull { (c, i) -> c.entries[i].template?.priority }.distinct().sorted()
        for (priority in priorities) {
            val laneChips =
                chips { it.priority == priority }.map { (category, index) ->
                    val asks = amountFor(category.entries[index].template!!, category, month, stats, detailed.figures.headerMinor)
                    val given = asks.coerceAtMost(available.coerceAtLeast(0L)).coerceAtLeast(0L)
                    available -= given
                    chip(category, index, asks, given)
                }
            lanes += TemplateLane(TemplateLane.Kind.PRIORITY, priority, laneChips, available)
        }
        // As a run does, only a category's first remainder line takes a share.
        val remainders = chips { it is Template.Remainder }.distinctBy { (category, _) -> category.id }
        if (remainders.isNotEmpty()) {
            val weights = remainders.map { (c, i) -> (c.entries[i].template as Template.Remainder).weight }
            val total = weights.fold(BigDecimal.ZERO) { sum, w -> sum + w }
            val pool = available.coerceAtLeast(0L)
            var left = pool
            val laneChips =
                remainders.mapIndexed { n, (category, index) ->
                    val share =
                        when {
                            total.signum() <= 0 -> 0L
                            n == remainders.lastIndex -> left
                            else -> (pool.toDouble() * weights[n].toDouble() / total.toDouble()).roundToLong()
                        }
                    left -= share
                    chip(category, index, null, share)
                }
            available -= pool - left
            lanes += TemplateLane(TemplateLane.Kind.REMAINDER, null, laneChips, available)
        }
        val goals = chips { it is Template.Goal }
        if (goals.isNotEmpty()) lanes += TemplateLane(TemplateLane.Kind.GOAL, null, goals.map { (c, i) -> chip(c, i, null, null) }, null)
        val unreadable =
            categories.flatMap { category ->
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
        return TemplatePlan(start, lanes, unreadable)
    }

    private fun amountFor(
        template: Template,
        category: Category,
        month: YearMonth,
        stats: Map<String, ActualMonthMath.CategoryMonthStat>,
        toBudget: Long,
    ): Long {
        val stat = stats[category.id]
        // What the category carries into this month.
        val carried = stat?.let { it.availableMinor - it.budgetedMinor - it.spentMinor } ?: 0L
        return when (template) {
            is Template.Simple -> {
                val limit = template.limit?.let { toMinor(it) }
                val monthly = template.monthly?.let { toMinor(it) }
                when {
                    limit == null -> monthly ?: 0L
                    monthly == null -> (limit - carried).coerceAtLeast(0L)
                    else -> monthly.coerceAtMost((limit - carried).coerceAtLeast(0L))
                }
            }
            is Template.By -> {
                var target = template.month
                val step = template.repeatMonths
                if (step != null) {
                    while (target < month) target = target.plusMonths(step.toLong())
                }
                if (target < month) return 0L
                val left = ChronoUnit.MONTHS.between(month, target) + 1
                ((toMinor(template.amount) - carried).coerceAtLeast(0L).toDouble() / left).roundToLong()
            }
            is Template.Schedule -> schedule(template, month, carried)
            is Template.Percentage -> {
                val base =
                    when (template.category.lowercase()) {
                        "available funds" -> toBudget
                        "all income" -> income(null, if (template.previous) month.minusMonths(1) else month)
                        else -> income(categoryId(template.category), if (template.previous) month.minusMonths(1) else month)
                    }
                (base.toDouble() * template.percent.toDouble() / 100.0).roundToLong().coerceAtLeast(0L)
            }
            is Template.Average -> {
                val total = (1..template.months).sumOf { spent(category.id, month.minusMonths(it.toLong())) }
                (-total.toDouble() / template.months).roundToLong().coerceAtLeast(0L)
            }
            is Template.Remainder, is Template.Goal -> 0L
        }
    }

    /** The schedule's occurrences this month; one that comes less often is saved for month by month unless `full`. */
    private fun schedule(
        template: Template.Schedule,
        month: YearMonth,
        carried: Long,
    ): Long {
        val row =
            session
                .query(
                    """
                    SELECT r.conditions AS conditions FROM schedules s JOIN rules r ON r.id = s.rule
                    WHERE s.name = ? AND IFNULL(s.tombstone, 0) = 0 AND IFNULL(s.completed, 0) = 0
                    """.trimIndent(),
                    listOf(template.name),
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
        val amount =
            when (amountValue) {
                is JsonPrimitive -> abs(amountValue.content.toDoubleOrNull()?.roundToLong() ?: 0L)
                is JsonObject -> {
                    val a = (amountValue["num1"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0
                    val b = (amountValue["num2"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0
                    abs(((a + b) / 2).roundToLong())
                }
                else -> 0L
            }
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

    private fun income(
        categoryId: String?,
        month: YearMonth,
    ): Long {
        val actual = month.toActualMonth()
        val filter = if (categoryId == null) "c.is_income = 1" else "t.category = ?"
        val args = listOfNotNull(categoryId) + listOf(actual * 100 + 1, actual * 100 + 31)
        return session
            .query(
                """
                SELECT COALESCE(SUM(t.amount), 0) AS total
                FROM transactions t
                LEFT JOIN accounts a ON a.id = t.acct
                LEFT JOIN categories c ON c.id = t.category
                WHERE $filter AND t.date >= ? AND t.date <= ?
                  AND IFNULL(a.offbudget, 1) = 0 AND IFNULL(t.tombstone, 0) = 0 AND IFNULL(t.isParent, 0) = 0
                """.trimIndent(),
                args,
            ).first()
            .long("total")
    }

    private fun spent(
        categoryId: String,
        month: YearMonth,
    ): Long {
        val actual = month.toActualMonth()
        return session
            .query(
                """
                SELECT COALESCE(SUM(t.amount), 0) AS total
                FROM transactions t
                LEFT JOIN accounts a ON a.id = t.acct
                WHERE t.category = ? AND t.date >= ? AND t.date <= ?
                  AND IFNULL(a.offbudget, 1) = 0 AND IFNULL(t.tombstone, 0) = 0 AND IFNULL(t.isParent, 0) = 0
                """.trimIndent(),
                listOf(categoryId, actual * 100 + 1, actual * 100 + 31),
            ).first()
            .long("total")
    }

    private fun categoryId(name: String): String? =
        session
            .query("SELECT id FROM categories WHERE LOWER(name) = LOWER(?) AND IFNULL(tombstone, 0) = 0", listOf(name))
            .firstOrNull()
            ?.str("id")

    private fun tracking(): Boolean =
        session
            .query("SELECT value FROM preferences WHERE id = 'budgetType'")
            .firstOrNull()
            ?.str("value") == "tracking"

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
}
