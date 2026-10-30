package app.duenorth.budget.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/**
 * Reads `categories.goal_def` back into [Template]s, for categories desktop's budget automations own
 * (`flags.goalTemplatesUIEnabled`, `template_settings.source = ui`). Their `goal_def` is written by the automations
 * editor, not parsed from a note, so it is the only place their templates live.
 */
object TemplateJson {
    /** True when [templateSettings] hands the category to the budget automations rather than its note. */
    fun managed(templateSettings: String?): Boolean {
        val settings = templateSettings?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() } as? JsonObject
        return (settings?.get("source") as? JsonPrimitive)?.contentOrNull == "ui"
    }

    /**
     * Each stored template in order; null for one this port cannot run yet (a type or an option it does not model),
     * so the caller can leave the whole category alone rather than run part of it. [scheduleName] finds a schedule's
     * name from the `scheduleId` newer automations carry, which survives the schedule being renamed.
     */
    fun parse(
        goalDef: String?,
        scheduleName: (String) -> String? = { null },
    ): List<Template?> = read(goalDef, scheduleName).map { it.template }

    /** As [parse], keeping each stored automation's own `type`, so one the phone cannot run can still be named. */
    fun read(
        goalDef: String?,
        scheduleName: (String) -> String? = { null },
    ): List<Stored> {
        if (goalDef.isNullOrBlank()) return emptyList()
        val array = runCatching { Json.parseToJsonElement(goalDef) }.getOrNull() as? JsonArray ?: return listOf(Stored(null, null))
        return array.map { element ->
            val obj = element as? JsonObject
            Stored(obj?.text("type"), obj?.let { runCatching { template(it, scheduleName) }.getOrNull() }, obj)
        }
    }

    /** One stored automation: its `type` as desktop wrote it, and the [Template] it runs as, null when this port cannot. */
    data class Stored(
        val type: String?,
        val template: Template?,
        /** The object as stored, written back unchanged while the phone has not edited it (it may hold more than [template]). */
        val raw: JsonObject? = null,
    ) {
        fun toJson(): JsonObject = raw ?: template?.toJson() ?: JsonObject(emptyMap())

        companion object {
            fun of(template: Template) = Stored(template.toJson().text("type"), template)
        }
    }

    private fun template(
        obj: JsonObject,
        scheduleName: (String) -> String?,
    ): Template? {
        if (obj.text("directive") == "error") return null
        val priority = obj.int("priority") ?: 0
        return when (obj.text("type")) {
            "simple" -> {
                val monthly = obj.number("monthly")
                val limit = limit(obj["limit"])
                if (monthly == null && limit == null) return null
                Template.Simple(monthly, limit, priority)
            }
            "periodic" -> {
                val period = obj["period"] as? JsonObject ?: return null
                Template.Periodic(
                    amount = obj.number("amount") ?: return null,
                    every = period.int("amount") ?: return null,
                    unit = RepeatUnit.of(period.text("period")) ?: return null,
                    starting = LocalDate.parse(obj.text("starting") ?: return null),
                    limit = limit(obj["limit"]),
                    priority = priority,
                )
            }
            "by", "spend" -> {
                val amount = obj.number("amount") ?: return null
                val month = YearMonth.parse(obj.text("month") ?: return null)
                val annual = obj.bool("annual")
                val repeat = obj.int("repeat")
                val from = obj.text("from")?.let { YearMonth.parse(it) }
                if (from != null) {
                    Template.Spend(amount, month, from, annual, repeat, priority)
                } else {
                    Template.By(amount, month, annual, repeat, priority)
                }
            }
            "schedule" -> {
                val id = obj.text("scheduleId")
                val name = id?.let(scheduleName) ?: obj.text("name") ?: return null
                Template.Schedule(name, full = obj.bool("full") == true, priority = priority, scheduleId = id, adjustment = adjustment(obj))
            }
            "percentage" ->
                Template.Percentage(
                    percent = obj.number("percent") ?: return null,
                    previous = obj.bool("previous") == true,
                    category = obj.text("category") ?: return null,
                    priority = priority,
                )
            "average" -> Template.Average(obj.int("numMonths")?.takeIf { it > 0 } ?: return null, priority, adjustment(obj))
            "copy" -> Template.Copy(obj.int("lookBack") ?: return null, priority, limit(obj["limit"]))
            "remainder" -> Template.Remainder(obj.number("weight") ?: BigDecimal.ONE, limit(obj["limit"]))
            "refill" -> Template.Refill(priority)
            "limit" -> Template.Limit(limit(obj) ?: return null)
            "goal" -> Template.Goal(obj.number("amount") ?: return null)
            else -> null
        }
    }

    /** A nested `limit` (or a limit automation itself); null when there is none. An unknown period does not read. */
    private fun limit(element: kotlinx.serialization.json.JsonElement?): TemplateLimit? {
        val obj = element as? JsonObject ?: return null
        val amount = obj.number("amount") ?: return null
        val period = obj.text("period")?.let { LimitPeriod.of(it) ?: throw IllegalArgumentException(it) } ?: LimitPeriod.MONTHLY
        return TemplateLimit(amount, obj.bool("hold") == true, period, obj.text("start")?.let { LocalDate.parse(it) })
    }

    private fun adjustment(obj: JsonObject): TemplateAdjustment? {
        val amount = obj.number("adjustment") ?: return null
        return when (obj.text("adjustmentType")) {
            "percent" -> TemplateAdjustment(amount, percent = true)
            "fixed" -> TemplateAdjustment(amount, percent = false)
            else -> null
        }
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull

    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

    private fun JsonObject.number(key: String): BigDecimal? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toBigDecimalOrNull()
}

/** A category's automations as desktop's budget automations store them, for the category page (read-only for now). */
data class CategoryAutomations(
    val entries: List<TemplateJson.Stored>,
) {
    /** True when every automation is one the phone can run; otherwise runs leave the category alone. */
    val runnable: Boolean get() = entries.all { it.template != null }

    val templates: List<Template> get() = entries.mapNotNull { it.template }

    fun add(template: Template) = CategoryAutomations(entries + TemplateJson.Stored.of(template))

    fun replace(
        index: Int,
        template: Template,
    ) = CategoryAutomations(entries.toMutableList().also { it[index] = TemplateJson.Stored.of(template) })

    fun remove(index: Int) = CategoryAutomations(entries.filterIndexed { i, _ -> i != index })

    /** As [TemplateNote.move]: the category's priorities stay the same numbers, handed out again in the new order. */
    fun move(
        from: Int,
        to: Int,
    ): CategoryAutomations {
        if (from == to) return this
        val moved = entries.toMutableList().apply { add(to.coerceIn(0, size - 1), removeAt(from)) }
        val priorities = moved.mapNotNull { it.template?.priority }.sorted()
        var next = 0
        return CategoryAutomations(
            moved.map { entry ->
                val template = entry.template
                if (template?.priority == null) return@map entry
                val priority = priorities[next++]
                if (priority == template.priority) entry else TemplateJson.Stored.of(template.withPriority(priority))
            },
        )
    }

    companion object {
        /**
         * `migrateTemplatesToAutomations`, desktop's switch to automations: a fixed amount becomes a monthly
         * repeating one from the start of this month, a cap becomes a limit automation (with a refill when nothing
         * else funds it), a repeating amount or remainder hands its cap to a limit of its own, and a schedule gains
         * its id. [schedules] are (id, name) of the budget's live schedules.
         */
        fun fromNote(
            templates: List<Template>,
            schedules: List<Pair<String, String?>>,
            today: LocalDate,
        ): CategoryAutomations {
            val out = mutableListOf<Template>()
            for (template in templates) {
                when (template) {
                    is Template.Schedule -> {
                        val schedule =
                            schedules.firstOrNull { (id, _) -> id == template.scheduleId }
                                ?: schedules.firstOrNull { (_, name) -> name?.trim() == template.name.trim() }
                        out +=
                            if (schedule ==
                                null
                            ) {
                                template
                            } else {
                                template.copy(scheduleId = schedule.first, name = schedule.second ?: template.name)
                            }
                    }
                    is Template.Simple -> {
                        val monthly = template.monthly
                        val hasMonthly = monthly != null && monthly.signum() != 0
                        template.limit?.let { limit ->
                            out += Template.Limit(limit)
                            if (monthly == null) out += Template.Refill(template.priority)
                        }
                        val contribution = if (hasMonthly || (monthly != null && template.limit == null)) monthly else null
                        if (contribution != null) {
                            out += Template.Periodic(contribution, 1, RepeatUnit.MONTH, today.withDayOfMonth(1), null, template.priority)
                        }
                    }
                    is Template.Periodic ->
                        if (template.limit != null) {
                            out += template.copy(limit = null)
                            out += Template.Limit(template.limit)
                        } else {
                            out += template
                        }
                    is Template.Remainder ->
                        if (template.limit != null) {
                            out += template.copy(limit = null)
                            out += Template.Limit(template.limit)
                        } else {
                            out += template
                        }
                    else -> out += template
                }
            }
            return CategoryAutomations(out.map { TemplateJson.Stored.of(it) })
        }
    }

    /** `goal_def` as desktop stores it; null with no automations left. */
    fun goalDef(): String? = if (entries.isEmpty()) null else JsonArray(entries.map { it.toJson() }).toString()
}
