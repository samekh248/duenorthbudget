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
                val limit = obj["limit"] as? JsonObject
                if (limit != null && !plainLimit(limit)) return null
                val monthly = obj.number("monthly")
                val cap = limit?.number("amount")
                if (monthly == null && cap == null) return null
                Template.Simple(monthly, cap, priority)
            }
            "by" -> {
                if (obj.text("from") != null) return null
                val annual = obj.bool("annual") == true
                val repeat = obj.int("repeat")
                val repeatMonths = if (annual) (repeat ?: 1) * 12 else repeat
                Template.By(
                    amount = obj.number("amount") ?: return null,
                    month = YearMonth.parse(obj.text("month") ?: return null),
                    repeatMonths = repeatMonths,
                    annual = annual,
                    repeat = repeat,
                    priority = priority,
                )
            }
            "schedule" -> {
                if (obj.number("adjustment") != null) return null
                val name = obj.text("scheduleId")?.let(scheduleName) ?: obj.text("name") ?: return null
                Template.Schedule(name, full = obj.bool("full") == true, priority = priority)
            }
            "percentage" ->
                Template.Percentage(
                    percent = obj.number("percent") ?: return null,
                    previous = obj.bool("previous") == true,
                    category = obj.text("category") ?: return null,
                    priority = priority,
                )
            "average" -> {
                if (obj.number("adjustment") != null) return null
                Template.Average(obj.int("numMonths")?.takeIf { it > 0 } ?: return null, priority)
            }
            "remainder" -> {
                if (obj["limit"].let { it != null && it !is JsonNull }) return null
                Template.Remainder(obj.number("weight") ?: BigDecimal.ONE)
            }
            "goal" -> Template.Goal(obj.number("amount") ?: return null)
            else -> null
        }
    }

    /** A cap as note templates write it (`up to N`): monthly and not held over; anything else is newer. */
    private fun plainLimit(limit: JsonObject): Boolean =
        limit.bool("hold") != true && (limit.text("period") ?: "monthly") == "monthly" && limit.text("start") == null

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

    /** `goal_def` as desktop stores it; null with no automations left. */
    fun goalDef(): String? = if (entries.isEmpty()) null else JsonArray(entries.map { it.toJson() }).toString()
}
