package app.duenorth.budget.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.max

/**
 * Port of Actual 26.8.1's `schedule-template.ts` (`createScheduleList` and `runSchedule`), which 26.8 through 26.10
 * share. A schedule is paid in the month it falls due when it comes at least monthly (or is `full`, or the budget is
 * a tracking one), and otherwise saved for a little each month until it does.
 */
internal class ScheduleTemplates(
    private val session: SqlSession,
    private val sheet: TemplateSheet,
) {
    /** One schedule template's place in the month: what it costs, when it next falls, and how often it comes. */
    data class Entry(
        /** The template's place in the category's list. */
        val index: Int,
        /** What it costs: positive for spending; for a schedule that repeats, every time it falls up to its month. */
        val target: Long,
        val next: LocalDate,
        val interval: Int,
        /** `daily`, `weekly`, `monthly` or `yearly`; null for a one-off date. */
        val frequency: String?,
        /** Months from this one to the month it next falls in. */
        val numMonths: Int,
        val full: Boolean,
    )

    data class Result(
        /** The priority's running total once the schedules have been added. */
        val toBudget: Long,
        /** Each schedule template's share of the month, by its place in the list, for splitting the batch total. */
        val perSchedule: Map<Int, Double>,
    )

    /** `createScheduleList`: the schedules still to come; one already past, or marked done, is left out. */
    fun list(
        templates: List<Pair<Int, Template.Schedule>>,
        month: YearMonth,
        categoryId: String,
        income: Boolean,
    ): List<Entry> {
        val out = mutableListOf<Entry>()
        for ((index, template) in templates) {
            val id = template.scheduleId
            val row =
                session
                    .query(
                        """
                        SELECT s.completed AS completed, r.conditions AS conditions, r.actions AS actions
                        FROM schedules s LEFT JOIN rules r ON r.id = s.rule
                        WHERE ${if (id != null) "s.id = ?" else "TRIM(s.name) = ?"} AND IFNULL(s.tombstone, 0) = 0
                        """.trimIndent(),
                        listOf(id ?: template.name),
                    ).firstOrNull() ?: continue
            val conditions = array(row.str("conditions")).mapNotNull { it as? JsonObject }
            val amountCond =
                conditions.firstOrNull { it.str("field") == "amount" && it.str("op") in setOf("is", "isapprox", "isbetween") } ?: continue
            val dateCond = conditions.firstOrNull { it.str("field") == "date" && it.str("op") in setOf("is", "isapprox") } ?: continue
            var scheduled =
                if (amountCond.str("op") == "isbetween") {
                    val range = amountCond["value"] as? JsonObject ?: continue
                    jsRound(range.num("num1") + range.num("num2")) / 2.0
                } else {
                    (amountCond["value"] as? JsonPrimitive)?.doubleOrNull ?: continue
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
            val amount = jsRound(scheduled)
            val next = nextDate(dateCond, month.atDay(1), skipWeekend = true) ?: continue
            val target = (if (income) 1 else -1) * ruleAmount(array(row.str("actions")), amount, categoryId)
            val config = dateCond["value"] as? JsonObject
            val numMonths = months(month, YearMonth.from(next))
            // A schedule in the past asks for nothing; desktop notes it and moves on.
            if (numMonths < 0) continue
            if (row.bool("completed")) continue
            var monthly = target
            if (config != null && config["frequency"] != null) {
                // Every time it falls, from the start of this month to the end of the month it next falls in.
                val end = month.plusMonths(numMonths + 1L).atDay(1)
                val skip = config.str("skipWeekend") == "true"
                val before = config.str("weekendSolveMode") == "before"
                var total = 0L
                var base = nextDate(dateCond, month.atDay(1), skipWeekend = false)
                var date = base?.let { if (skip) ScheduleRecurrence.offWeekend(it, before) else it }
                while (base != null && date != null && date < end) {
                    total += target
                    val current = base
                    base = nextDate(dateCond, current.plusDays(1), skipWeekend = false)
                    date = base?.let { if (skip) ScheduleRecurrence.offWeekend(it, before) else it }
                    if (base == current) break
                }
                monthly = total
            }
            out +=
                Entry(
                    index = index,
                    target = monthly,
                    next = next,
                    interval =
                        config
                            ?.get("interval")
                            ?.jsonPrimitive
                            ?.intOrNull
                            ?.takeIf { it != 0 } ?: 1,
                    frequency = config?.str("frequency"),
                    numMonths = numMonths,
                    full = template.full,
                )
        }
        return out
    }

    /** `runSchedule`: what the schedules add to [toBudget], with [balance] what the category holds so far. */
    fun run(
        entries: List<Entry>,
        month: YearMonth,
        categoryId: String,
        balance: Long,
        lastMonthBalance: Long,
        toBudget: Long,
    ): Result {
        fun payMonthOf(entry: Entry) =
            entry.full ||
                ((entry.frequency == "monthly" || entry.frequency == null) && entry.interval == 1 && entry.numMonths == 0) ||
                (entry.frequency == "weekly" && entry.interval <= 4) ||
                (entry.frequency == "daily" && entry.interval <= 31) ||
                sheet.tracking
        val payMonthOf = entries.filter(::payMonthOf)
        val sinking = entries.filterNot(::payMonthOf).sortedBy { it.next }
        val subMonthly = entries.count { it.frequency == "weekly" || it.frequency == "daily" }
        val totalPayMonthOf = payMonthOf.filter { it.numMonths == 0 }.sumOf { it.target }
        val totalSinking = sinking.sumOf { it.target }
        val totalBase = sinking.sumOf { base(it) }
        val lastMonthGoal = sheet.goal(month.minusMonths(1), categoryId)
        val per = LinkedHashMap<Int, Double>()

        fun add(
            index: Int,
            amount: Double,
        ) {
            per[index] = (per[index] ?: 0.0) + amount
        }
        val due = totalSinking + totalPayMonthOf
        var result = toBudget
        if (balance >= due || (lastMonthGoal < due && lastMonthGoal != 0L && balance >= lastMonthGoal && subMonthly > 0)) {
            result += jsRound(totalPayMonthOf + totalBase)
            for (entry in payMonthOf) if (entry.numMonths == 0) add(entry.index, entry.target.toDouble())
            for (entry in sinking) add(entry.index, base(entry))
        } else {
            // What last month carried in pays the soonest first; each then saves what is still missing over the months left.
            var remainder = 0L
            var total = 0.0
            val sinkingPer = LinkedHashMap<Int, Double>()
            sinking.forEachIndexed { n, entry ->
                remainder = if (n == 0) entry.target - lastMonthBalance else entry.target - remainder
                val need: Long
                if (remainder >= 0) {
                    need = remainder
                    remainder = 0
                } else {
                    need = 0
                    remainder = abs(remainder)
                }
                val contribution = need.toDouble() / (entry.numMonths + 1)
                total += contribution
                sinkingPer[entry.index] = (sinkingPer[entry.index] ?: 0.0) + contribution
            }
            result += jsRound(totalPayMonthOf + total) - if (sinking.isEmpty()) lastMonthBalance else 0L
            for (entry in payMonthOf) if (entry.numMonths == 0) add(entry.index, entry.target.toDouble())
            sinkingPer.forEach { (index, amount) -> add(index, amount) }
        }
        return Result(result, per)
    }

    /** `getMonthlyBaseContribution`: a schedule that comes less often than monthly, spread over its months. */
    private fun base(entry: Entry): Double {
        val target = entry.target.toDouble()
        return when (entry.frequency) {
            "yearly" -> target / entry.interval / 12
            "monthly" -> target / entry.interval
            "weekly" -> target / max(1, months(YearMonth.from(entry.next.minusWeeks(entry.interval.toLong())), YearMonth.from(entry.next)))
            "daily" -> target / max(1, months(YearMonth.from(entry.next.minusDays(entry.interval.toLong())), YearMonth.from(entry.next)))
            else -> target / entry.interval
        }
    }

    /**
     * The schedule's amount once its rule's actions have run on it, as `rule.execActions` does: a set amount
     * replaces it, and a split counts only the parts that land in [categoryId].
     */
    private fun ruleAmount(
        actions: JsonArray,
        amount: Long,
        categoryId: String,
    ): Long {
        val all = actions.mapNotNull { it as? JsonObject }

        fun splitIndex(action: JsonObject) = (action["options"] as? JsonObject)?.get("splitIndex")?.jsonPrimitive?.intOrNull ?: 0
        var parentAmount = amount
        var parentCategory: String? = categoryId
        for (action in all.filter { splitIndex(it) == 0 }) {
            if (action.str("op") != "set" || formula(action)) continue
            when (action.str("field")) {
                "amount" -> (action["value"] as? JsonPrimitive)?.doubleOrNull?.let { parentAmount = it.toLong() }
                "category" -> parentCategory = (action["value"] as? JsonPrimitive)?.contentOrNull
            }
        }
        val splits = all.maxOfOrNull { splitIndex(it) } ?: 0
        if (splits == 0) return parentAmount
        // Children start at nothing in the parent's category; the first, empty one is dropped at the end.
        val amounts = LongArray(splits + 1)
        val categories = Array(splits + 1) { parentCategory }
        val childActions = all.filter { splitIndex(it) > 0 }
        for (action in childActions) {
            val at = splitIndex(action)
            when (action.str("op")) {
                "set" ->
                    if (!formula(action)) {
                        when (action.str("field")) {
                            "amount" -> (action["value"] as? JsonPrimitive)?.doubleOrNull?.let { amounts[at] = it.toLong() }
                            "category" -> categories[at] = (action["value"] as? JsonPrimitive)?.contentOrNull
                        }
                    }
                "set-split-amount" ->
                    if ((action["options"] as? JsonObject)?.str("method") == "fixed-amount") {
                        (action["value"] as? JsonPrimitive)?.doubleOrNull?.let { amounts[at] = it.toLong() }
                    }
            }
        }

        fun left() = parentAmount - amounts.sum()
        val splitAmounts = childActions.filter { it.str("op") == "set-split-amount" }
        val afterFixed = left()
        for (action in splitAmounts.filter { (it["options"] as? JsonObject)?.str("method") == "fixed-percent" }) {
            val percent = ((action["value"] as? JsonPrimitive)?.doubleOrNull ?: 0.0) / 100
            amounts[splitIndex(action)] = jsRound(afterFixed * percent)
        }
        val remainders = splitAmounts.filter { (it["options"] as? JsonObject)?.str("method") == "remainder" }
        if (remainders.isNotEmpty()) {
            val each = jsRound(left().toDouble() / remainders.size)
            var last = -1
            for (action in remainders) {
                amounts[splitIndex(action)] = each
                last = max(last, splitIndex(action))
            }
            amounts[last] += left()
        }
        val mine = (1..splits).filter { categories[it] == categoryId }
        return if (mine.isEmpty()) parentAmount else mine.sumOf { amounts[it] }
    }

    private fun formula(action: JsonObject): Boolean {
        val options = action["options"] as? JsonObject ?: return false
        return options["formula"] != null || options["template"] != null
    }

    /** `getNextDate`: the first time it falls on or after [from], else the last time it ever fell. */
    private fun nextDate(
        dateCond: JsonObject,
        from: LocalDate,
        skipWeekend: Boolean,
    ): LocalDate? {
        val value = dateCond["value"] ?: return null
        if (value is JsonPrimitive) return value.contentOrNull?.let { ScheduleDates.parseIso(it) }
        val config = value.jsonObject
        return ScheduleRecurrence.occurrences(config, from, limit = 1, byRawDate = true, moveWeekends = skipWeekend).firstOrNull()
            ?: ScheduleRecurrence.lastOccurrence(config, moveWeekends = skipWeekend)
    }

    private fun months(
        from: YearMonth,
        to: YearMonth,
    ): Int = (to.year - from.year) * 12 + (to.monthValue - from.monthValue)

    private fun array(text: String?): JsonArray =
        text?.let { runCatching { Json.parseToJsonElement(it) as? JsonArray }.getOrNull() } ?: JsonArray(emptyList())

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.num(key: String): Double = (this[key] as? JsonPrimitive)?.doubleOrNull ?: 0.0
}
