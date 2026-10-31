package app.duenorth.budget.core

import java.math.BigDecimal
import java.time.YearMonth

/** The kinds the template editor offers, each one of Actual's forms (option A of the template editor design). */
enum class TemplateKind(
    val label: String,
) {
    MONTHLY("same each month"),
    BY_DATE("save by a date"),
    SCHEDULE("pay a schedule"),
    PERCENT("share of income"),
    AVERAGE("like before"),
    REMAINDER("what's left"),
    CAP("cap a balance"),
    GOAL("long goal"),
    ;

    /** A filled-in starting point for a new template of this kind at [priority]. */
    fun blank(
        priority: Int,
        month: YearMonth,
        schedule: String,
    ): Template =
        when (this) {
            MONTHLY -> Template.Simple(BigDecimal.ZERO, null, priority)
            BY_DATE -> Template.By(BigDecimal.ZERO, month.plusMonths(11), annual = null, repeat = null, priority = priority)
            SCHEDULE -> Template.Schedule(schedule, full = false, priority = priority)
            PERCENT -> Template.Percentage(BigDecimal.TEN, previous = false, category = TemplateLines.ALL_INCOME, priority = priority)
            AVERAGE -> Template.Average(3, priority)
            REMAINDER -> Template.Remainder(BigDecimal.ONE)
            CAP -> Template.Simple(null, TemplateLimit(BigDecimal.ZERO), priority)
            GOAL -> Template.Goal(BigDecimal.ZERO)
        }

    companion object {
        /**
         * The form that edits [template]; null for one the phone has no form for yet (a repeating amount, spend,
         * copy, refill or limit automation, a held or per-day cap, an adjustment), which stays read-only.
         */
        fun of(template: Template): TemplateKind? =
            when (template) {
                is Template.Simple ->
                    when {
                        template.limit != null && !template.limit.plain -> null
                        template.monthly == null -> CAP
                        else -> MONTHLY
                    }
                is Template.By -> BY_DATE
                is Template.Schedule -> if (template.adjustment == null) SCHEDULE else null
                is Template.Percentage -> PERCENT
                is Template.Average -> if (template.adjustment == null) AVERAGE else null
                is Template.Remainder -> if (template.limit == null) REMAINDER else null
                is Template.Goal -> GOAL
                // Desktop's switch to automations turns a fixed amount into this; it is the same each month.
                is Template.Periodic -> if (template.monthlyFixed) MONTHLY else null
                is Template.Spend, is Template.Copy, is Template.Refill, is Template.Limit -> null
            }
    }
}

/** The kind [this] looks like, for its glyph, even when no form edits it yet. */
val Template.look: TemplateKind
    get() =
        TemplateKind.of(this) ?: when (this) {
            is Template.Simple, is Template.Refill, is Template.Limit -> TemplateKind.CAP
            is Template.Periodic -> TemplateKind.MONTHLY
            is Template.By, is Template.Spend -> TemplateKind.BY_DATE
            is Template.Schedule -> TemplateKind.SCHEDULE
            is Template.Percentage -> TemplateKind.PERCENT
            is Template.Average, is Template.Copy -> TemplateKind.AVERAGE
            is Template.Remainder -> TemplateKind.REMAINDER
            is Template.Goal -> TemplateKind.GOAL
        }

/** Once a month with no cap of its own: the same each month, as desktop's automations write a fixed amount. */
val Template.Periodic.monthlyFixed: Boolean get() = every == 1 && unit == RepeatUnit.MONTH && limit == null

/** A plain `up to N`: monthly and not held, the only cap the forms write. */
val TemplateLimit.plain: Boolean get() = !hold && period == LimitPeriod.MONTHLY

/** The same template at [priority]; remainder, limit and goal lines have none and come back unchanged. */
fun Template.withPriority(priority: Int): Template =
    when (this) {
        is Template.Simple -> copy(priority = priority)
        is Template.Periodic -> copy(priority = priority)
        is Template.By -> copy(priority = priority)
        is Template.Spend -> copy(priority = priority)
        is Template.Schedule -> copy(priority = priority)
        is Template.Percentage -> copy(priority = priority)
        is Template.Average -> copy(priority = priority)
        is Template.Copy -> copy(priority = priority)
        is Template.Refill -> copy(priority = priority)
        is Template.Remainder, is Template.Limit, is Template.Goal -> this
    }

/** Writes a [Template] back as the note line [TemplateParser] reads, in Actual's own words. */
object TemplateLines {
    const val ALL_INCOME = "all income"
    const val AVAILABLE_FUNDS = "available funds"

    /** The note line for [template], as `budget/render-note-templates` writes it. A refill alone has none. */
    fun format(template: Template): String = line(template, refill = null, exact = false) ?: "#template refill"

    /**
     * `unparse`: the note lines for a category's automations when it goes back to notes. A refill folds into its
     * limit as `up to N` at the refill's priority; a limit without one is `0 up to N`.
     */
    fun unparse(templates: List<Template>): List<String> {
        val refill = templates.filterIsInstance<Template.Refill>().firstOrNull()
        return templates.filter { it !is Template.Refill }.mapNotNull { line(it, refill, exact = true) }
    }

    /** True when [templates] can all go back into a note: a refill needs a limit to fold into. */
    fun writable(templates: List<Template>): Boolean = templates.none { it is Template.Refill } || templates.any { it is Template.Limit }

    /**
     * [exact] writes a repeat of one month or year as desktop's un-migrate does (`repeat every month`), which
     * desktop then reads as not repeating; otherwise as `repeat every 1 months`, which reads back as written.
     */
    private fun line(
        template: Template,
        refill: Template.Refill?,
        exact: Boolean,
    ): String? {
        if (template is Template.Goal) return "#goal ${number(template.amount)}"
        val head = head(template.priority)
        val body =
            when (template) {
                is Template.Simple ->
                    listOfNotNull(template.monthly?.let { number(it) }, template.limit?.let { limit(it) }).joinToString(" ")
                is Template.Periodic ->
                    "${number(template.amount)} repeat every ${template.every} ${template.unit.json}s starting ${template.starting}" +
                        (template.limit?.let { " ${limit(it)}" } ?: "")
                is Template.By -> "${number(template.amount)} by ${template.month}${repeat(template.annual, template.repeat, exact)}"
                is Template.Spend ->
                    "${number(
                        template.amount,
                    )} by ${template.month} spend from ${template.from}${repeat(template.annual, template.repeat, exact)}"
                is Template.Schedule ->
                    (if (template.full) "schedule full ${template.name}" else "schedule ${template.name}") + adjustment(template.adjustment)
                is Template.Percentage ->
                    "${number(template.percent)}% of ${if (template.previous) "previous " else ""}${template.category}"
                is Template.Average -> "average ${template.months} months${adjustment(template.adjustment)}"
                is Template.Copy -> "copy from ${template.lookBack} months ago"
                is Template.Remainder ->
                    "remainder" + (if (template.weight.compareTo(BigDecimal.ONE) == 0) "" else " ${number(template.weight)}") +
                        (template.limit?.let { " ${limit(it)}" } ?: "")
                is Template.Limit ->
                    return if (refill == null) "$head 0 ${limit(template.limit)}" else "${head(refill.priority)} ${limit(template.limit)}"
                is Template.Refill -> return null
                is Template.Goal -> error("handled above")
            }
        return "$head $body"
    }

    private fun head(priority: Int?): String = if (priority == null || priority == 0) "#template" else "#template-$priority"

    private fun limit(limit: TemplateLimit): String {
        val base =
            when (limit.period) {
                LimitPeriod.WEEKLY -> "up to ${number(limit.amount)} per week starting ${limit.start}"
                LimitPeriod.DAILY -> "up to ${number(limit.amount)} per day"
                LimitPeriod.MONTHLY -> "up to ${number(limit.amount)}"
            }
        return if (limit.hold) "$base hold" else base
    }

    private fun adjustment(adjustment: TemplateAdjustment?): String {
        if (adjustment == null) return ""
        val op = if (adjustment.amount.signum() >= 0) "increase" else "decrease"
        return " [$op ${number(adjustment.amount.abs())}${if (adjustment.percent) "%" else ""}]"
    }

    /** `repeatToString`: nothing without a repeat clause, else `month`, `N months`, `year` or `N years`. */
    private fun repeat(
        annual: Boolean?,
        repeat: Int?,
        exact: Boolean,
    ): String =
        when {
            annual == null -> ""
            !exact && repeat == 1 -> if (annual) " repeat every 1 years" else " repeat every 1 months"
            annual -> if (repeat == null || repeat == 1) " repeat every year" else " repeat every $repeat years"
            else -> if (repeat == null || repeat == 1) " repeat every month" else " repeat every $repeat months"
        }

    private fun number(value: BigDecimal): String =
        value
            .stripTrailingZeros()
            .let {
                if (it.scale() <
                    0
                ) {
                    it.setScale(0)
                } else {
                    it
                }
            }.toPlainString()
}

/**
 * A category note split for the template editor: each template line (one that parses, or one that does not) in note
 * order, and the rest of the note. Saving puts the template lines back where the first one stood, so the person's
 * own text keeps its place.
 */
data class TemplateNote(
    val entries: List<Entry>,
    /** The note without its template lines. */
    val text: String,
    private val lines: List<String>,
    private val at: Int?,
) {
    /** One template line: [template] is null when the line does not parse. */
    data class Entry(
        val line: String,
        val template: Template?,
    ) {
        companion object {
            fun of(template: Template) = Entry(TemplateLines.format(template), template)
        }
    }

    /** The note with [entries] in place of the template lines it was read with. */
    fun compose(entries: List<Entry>): String {
        val templateLines = entries.map { it.line }
        val out =
            if (at == null) {
                val body = lines.dropLastWhile { it.isBlank() }
                if (templateLines.isEmpty()) body else body + templateLines
            } else {
                lines.take(at) + templateLines + lines.drop(at)
            }
        return out.joinToString("\n")
    }

    /** The note with its free text replaced by [text], template lines kept at the end. */
    fun withText(text: String): String {
        val body = text.lines().dropLastWhile { it.isBlank() }.filterNot { TemplateParser.isTemplateLine(it) }
        return (body + entries.map { it.line }).joinToString("\n").trim('\n')
    }

    fun replace(
        index: Int,
        template: Template,
    ): String = compose(entries.toMutableList().also { it[index] = Entry.of(template) })

    fun add(template: Template): String = compose(entries + Entry.of(template))

    fun remove(index: Int): String = compose(entries.filterIndexed { i, _ -> i != index })

    /**
     * Moves the entry at [from] to [to]. The category's priorities stay the same set of numbers, handed out again in
     * the new order, so dragging a template above another gives it the earlier priority. Lines whose priority does
     * not change keep their own wording.
     */
    fun move(
        from: Int,
        to: Int,
    ): String {
        if (from == to) return compose(entries)
        val moved = entries.toMutableList().apply { add(to.coerceIn(0, size - 1), removeAt(from)) }
        val ranked = moved.filter { it.template?.priority != null }
        val priorities = ranked.mapNotNull { it.template?.priority }.sorted()
        var next = 0
        val renumbered =
            moved.map { entry ->
                val template = entry.template
                if (template?.priority == null) return@map entry
                val priority = priorities[next++]
                if (priority == template.priority) entry else Entry.of(template.withPriority(priority))
            }
        return compose(renumbered)
    }

    companion object {
        fun parse(note: String): TemplateNote {
            val all = note.lines()
            val entries = mutableListOf<Entry>()
            val rest = mutableListOf<String>()
            var at: Int? = null
            for (raw in all) {
                if (TemplateParser.isTemplateLine(raw)) {
                    if (at == null) at = rest.size
                    val line = raw.trim()
                    entries += Entry(line, TemplateParser.parseLine(line))
                } else {
                    rest += raw
                }
            }
            if (note.isEmpty()) rest.clear()
            return TemplateNote(entries, rest.joinToString("\n").trim('\n'), rest, at)
        }
    }
}
