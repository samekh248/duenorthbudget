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
            BY_DATE -> Template.By(BigDecimal.ZERO, month.plusMonths(11), null, annual = false, repeat = null, priority = priority)
            SCHEDULE -> Template.Schedule(schedule, full = false, priority = priority)
            PERCENT -> Template.Percentage(BigDecimal.TEN, previous = false, category = TemplateLines.ALL_INCOME, priority = priority)
            AVERAGE -> Template.Average(3, priority)
            REMAINDER -> Template.Remainder(BigDecimal.ONE)
            CAP -> Template.Simple(null, BigDecimal.ZERO, priority)
            GOAL -> Template.Goal(BigDecimal.ZERO)
        }

    companion object {
        fun of(template: Template): TemplateKind =
            when (template) {
                is Template.Simple -> if (template.monthly == null) CAP else MONTHLY
                is Template.By -> BY_DATE
                is Template.Schedule -> SCHEDULE
                is Template.Percentage -> PERCENT
                is Template.Average -> AVERAGE
                is Template.Remainder -> REMAINDER
                is Template.Goal -> GOAL
            }
    }
}

/** The same template at [priority]; remainder and goal lines have none and come back unchanged. */
fun Template.withPriority(priority: Int): Template =
    when (this) {
        is Template.Simple -> copy(priority = priority)
        is Template.By -> copy(priority = priority)
        is Template.Schedule -> copy(priority = priority)
        is Template.Percentage -> copy(priority = priority)
        is Template.Average -> copy(priority = priority)
        is Template.Remainder, is Template.Goal -> this
    }

/** Writes a [Template] back as the note line [TemplateParser] reads, in Actual's own words. */
object TemplateLines {
    const val ALL_INCOME = "all income"
    const val AVAILABLE_FUNDS = "available funds"

    fun format(template: Template): String {
        if (template is Template.Goal) return "#goal ${number(template.amount)}"
        val priority = template.priority
        val head = if (priority == null || priority == 0) "#template" else "#template-$priority"
        val body =
            when (template) {
                is Template.Simple ->
                    when {
                        template.monthly == null -> "up to ${number(template.limit ?: BigDecimal.ZERO)}"
                        template.limit == null -> number(template.monthly)
                        else -> "${number(template.monthly)} up to ${number(template.limit)}"
                    }
                is Template.By -> "${number(template.amount)} by ${template.month}${repeat(template)}"
                is Template.Schedule -> if (template.full) "schedule full ${template.name}" else "schedule ${template.name}"
                is Template.Percentage ->
                    "${number(template.percent)}% of ${if (template.previous) "previous " else ""}${template.category}"
                is Template.Average -> "average ${template.months} months"
                is Template.Remainder ->
                    if (template.weight.compareTo(BigDecimal.ONE) ==
                        0
                    ) {
                        "remainder"
                    } else {
                        "remainder ${number(template.weight)}"
                    }
                is Template.Goal -> error("handled above")
            }
        return "$head $body"
    }

    private fun repeat(template: Template.By): String {
        val step = template.repeatMonths ?: return ""
        return when {
            template.annual && step == 12 -> " repeat every year"
            template.annual -> " repeat every ${step / 12} years"
            step == 1 -> " repeat every month"
            else -> " repeat every $step months"
        }
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
