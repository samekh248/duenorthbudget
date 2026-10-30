package app.duenorth.budget.core

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/** How often an `up to` cap counts: once a month, every day, or every week from a start day. */
enum class LimitPeriod(
    val json: String,
) {
    DAILY("daily"),
    WEEKLY("weekly"),
    MONTHLY("monthly"),
    ;

    companion object {
        fun of(json: String?): LimitPeriod? = entries.firstOrNull { it.json == json }
    }
}

/** An `up to` cap on what a category holds; [hold] keeps a balance already over it instead of taking the rest back. */
data class TemplateLimit(
    val amount: BigDecimal,
    val hold: Boolean = false,
    val period: LimitPeriod = LimitPeriod.MONTHLY,
    /** The first day of the first week, for a weekly cap. */
    val start: LocalDate? = null,
) {
    /** As the note grammar stores it, nested in a template: an absent `hold` is null. */
    internal fun toJson(): JsonObject =
        Template.obj(
            "amount" to amount,
            "hold" to if (hold) true else null,
            "period" to period.json,
            "start" to start?.toString(),
        )
}

/** The unit a periodic template repeats in. */
enum class RepeatUnit(
    val json: String,
) {
    DAY("day"),
    WEEK("week"),
    MONTH("month"),
    YEAR("year"),
    ;

    companion object {
        fun of(json: String?): RepeatUnit? = entries.firstOrNull { it.json == json }
    }
}

/** `[increase 10%]` or `[decrease 20]` on a schedule or average template; [amount] is negative for a decrease. */
data class TemplateAdjustment(
    val amount: BigDecimal,
    val percent: Boolean,
)

/** One parsed line of a category note, or one budget automation, with Actual's stored (`goal_def`) form. */
sealed interface Template {
    /** Null for remainder, limit and goal lines, which run outside the priority order. */
    val priority: Int?

    fun toJson(): JsonObject

    data class Simple(
        val monthly: BigDecimal?,
        val limit: TemplateLimit?,
        override val priority: Int,
    ) : Template {
        override fun toJson() =
            obj(
                "type" to "simple",
                "monthly" to monthly,
                "limit" to limit?.toJson(),
                "priority" to priority,
                "directive" to "template",
            )
    }

    /** `N repeat every K units starting DAY`: [amount] for each time it comes round in the month. */
    data class Periodic(
        val amount: BigDecimal,
        val every: Int,
        val unit: RepeatUnit,
        val starting: LocalDate,
        val limit: TemplateLimit?,
        override val priority: Int,
    ) : Template {
        override fun toJson() =
            obj(
                "type" to "periodic",
                "amount" to amount,
                "period" to obj("period" to unit.json, "amount" to every),
                "starting" to starting.toString(),
                "limit" to limit?.toJson(),
                "priority" to priority,
                "directive" to "template",
            )
    }

    data class By(
        val amount: BigDecimal,
        val month: YearMonth,
        /** Null without a `repeat every` clause; false for months, true for years. */
        val annual: Boolean?,
        /** How many months or years apart; null for one. */
        val repeat: Int?,
        override val priority: Int,
    ) : Template {
        /** Months between targets when it repeats, as Actual counts them; null when it does not. */
        val repeatMonths: Int? get() = repeatMonths(annual, repeat)

        override fun toJson() =
            obj(
                "type" to "by",
                "amount" to amount,
                "month" to month.toString(),
                *repeatFields(annual, repeat),
                "from" to null,
                "priority" to priority,
                "directive" to "template",
            )
    }

    /** `N by MONTH spend from MONTH`: saves toward [amount], counting what was budgeted since [from] even if spent. */
    data class Spend(
        val amount: BigDecimal,
        val month: YearMonth,
        val from: YearMonth,
        val annual: Boolean?,
        val repeat: Int?,
        override val priority: Int,
    ) : Template {
        val repeatMonths: Int? get() = repeatMonths(annual, repeat)

        override fun toJson() =
            obj(
                "type" to "spend",
                "amount" to amount,
                "month" to month.toString(),
                *repeatFields(annual, repeat),
                "from" to from.toString(),
                "priority" to priority,
                "directive" to "template",
            )
    }

    data class Schedule(
        val name: String,
        val full: Boolean,
        override val priority: Int,
        /** The schedule's id, which newer automations carry so a rename does not lose it. */
        val scheduleId: String? = null,
        val adjustment: TemplateAdjustment? = null,
    ) : Template {
        override fun toJson() =
            obj(
                "type" to "schedule",
                "name" to name,
                "priority" to priority,
                "directive" to "template",
                "full" to if (full) true else null,
                *adjustmentFields(adjustment),
                *(if (scheduleId != null) arrayOf("scheduleId" to scheduleId) else emptyArray()),
            )
    }

    data class Percentage(
        val percent: BigDecimal,
        val previous: Boolean,
        /** A category name or id, `all income`, or `available funds`. */
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
        val adjustment: TemplateAdjustment? = null,
    ) : Template {
        override fun toJson() =
            obj(
                "type" to "average",
                "numMonths" to months,
                "priority" to priority,
                "directive" to "template",
                *adjustmentFields(adjustment),
            )
    }

    /** `copy from N months ago`: what was budgeted then. A cap can be written but Actual does not apply it. */
    data class Copy(
        val lookBack: Int,
        override val priority: Int,
        val limit: TemplateLimit? = null,
    ) : Template {
        override fun toJson() =
            obj("type" to "copy", "priority" to priority, "directive" to "template", "lookBack" to lookBack, "limit" to limit?.toJson())
    }

    data class Remainder(
        val weight: BigDecimal,
        val limit: TemplateLimit? = null,
    ) : Template {
        override val priority: Int? = null

        override fun toJson() =
            obj("type" to "remainder", "priority" to null, "directive" to "template", "weight" to weight, "limit" to limit?.toJson())
    }

    /** Tops the category up to its [Limit] (an automation only; a note writes it as `up to N`). */
    data class Refill(
        override val priority: Int,
    ) : Template {
        override fun toJson() = obj("type" to "refill", "directive" to "template", "priority" to priority)
    }

    /** The category's cap as an automation of its own, with no priority. */
    data class Limit(
        val limit: TemplateLimit,
    ) : Template {
        override val priority: Int? = null

        override fun toJson() =
            obj(
                "type" to "limit",
                "amount" to limit.amount,
                "hold" to limit.hold,
                "period" to limit.period.json,
                *(if (limit.start != null) arrayOf("start" to limit.start.toString()) else emptyArray()),
                "directive" to "template",
                "priority" to null,
            )
    }

    data class Goal(
        val amount: BigDecimal,
    ) : Template {
        override val priority: Int? = null

        override fun toJson() = obj("type" to "goal", "amount" to amount, "priority" to null, "directive" to "goal")
    }

    companion object {
        /** `annual ? (repeat || 1) * 12 : repeat`, Actual's months between repeating targets. */
        fun repeatMonths(
            annual: Boolean?,
            repeat: Int?,
        ): Int? = if (annual == true) (repeat ?: 1) * 12 else repeat

        private fun repeatFields(
            annual: Boolean?,
            repeat: Int?,
        ): Array<Pair<String, Any?>> =
            when {
                annual == null -> emptyArray()
                repeat == null -> arrayOf("annual" to annual)
                else -> arrayOf("annual" to annual, "repeat" to repeat)
            }

        private fun adjustmentFields(adjustment: TemplateAdjustment?): Array<Pair<String, Any?>> =
            if (adjustment == null) {
                emptyArray()
            } else {
                arrayOf("adjustment" to adjustment.amount, "adjustmentType" to if (adjustment.percent) "percent" else "fixed")
            }

        internal fun obj(vararg pairs: Pair<String, Any?>): JsonObject =
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
