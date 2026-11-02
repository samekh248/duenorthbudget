package app.duenorth.budget.core

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** A category's templates cannot run as written; the message is desktop's own. */
internal class TemplateFailure(
    message: String,
) : Exception(message)

/** JavaScript's `Math.round`: halves go up, toward positive infinity. */
internal fun jsRound(value: Double): Long = floor(value + 0.5).toLong()

/** `amountToInteger`: a template's decimal amount in cents. */
internal fun cents(amount: BigDecimal): Long = jsRound(amount.toDouble() * 100)

/** What the templates read about the budget: Actual's sheet cells, one month at a time. */
internal class TemplateSheet(
    private val session: SqlSession,
    private val today: LocalDate,
) {
    val tracking: Boolean =
        session
            .query("SELECT value FROM preferences WHERE id = 'budgetType'")
            .firstOrNull()
            ?.str("value") == "tracking"

    val hideDecimal: Boolean =
        session
            .query("SELECT value FROM preferences WHERE id = 'hideFraction'")
            .firstOrNull()
            ?.str("value") == "true"

    private val loaded = HashMap<YearMonth, ShellReader.LoadedDetailed>()
    private val fill by lazy { MonthFillBook(session, today) }

    fun month(month: YearMonth): ShellReader.LoadedDetailed = loaded.getOrPut(month) { ShellReader.loadDetailed(session, month) }

    private fun stat(
        month: YearMonth,
        categoryId: String,
    ) = month(month).categories[categoryId]

    /** `budget-<category>`. */
    fun budget(
        month: YearMonth,
        categoryId: String,
    ): Long = stat(month, categoryId)?.budgetedMinor ?: 0L

    /** `leftover-<category>`: the balance at the month's end. */
    fun leftover(
        month: YearMonth,
        categoryId: String,
    ): Long = stat(month, categoryId)?.availableMinor ?: 0L

    /** `sum-amount-<category>`: the month's activity, negative for spending. */
    fun spent(
        month: YearMonth,
        categoryId: String,
    ): Long = stat(month, categoryId)?.spentMinor ?: 0L

    fun carryover(
        month: YearMonth,
        categoryId: String,
    ): Boolean = stat(month, categoryId)?.carryover ?: false

    fun average(
        month: YearMonth,
        categoryId: String,
        months: Int,
    ): Long = fill.average(month, categoryId, months)

    /** `total-income`, or one income category's activity when [categoryId] is set. */
    fun income(
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

    /** Income categories as (id, name), for percentage templates that name one either way. */
    val incomeCategories: List<Pair<String, String>> by lazy {
        session
            .query("SELECT id, name FROM categories WHERE IFNULL(is_income, 0) = 1 AND IFNULL(tombstone, 0) = 0")
            .map { it.str("id").orEmpty() to it.str("name").orEmpty() }
    }

    /** `goal-<category>`: the goal a run wrote for [month], or nothing. */
    fun goal(
        month: YearMonth,
        categoryId: String,
    ): Long =
        session
            .query(
                "SELECT goal FROM ${if (tracking) "reflect_budgets" else "zero_budgets"} WHERE month = ? AND category = ?",
                listOf(month.toActualMonth(), categoryId),
            ).firstOrNull()
            ?.long("goal") ?: 0L

    val schedules by lazy { ScheduleTemplates(session, this) }

    /** Schedules `getActiveSchedules` lists: named and not deleted, as (id, name). */
    val activeSchedules: List<Pair<String, String>> by lazy {
        session
            .query("SELECT id, name FROM schedules WHERE name IS NOT NULL AND IFNULL(tombstone, 0) = 0")
            .map { it.str("id").orEmpty() to it.str("name").orEmpty() }
    }
}

/**
 * Port of Actual 26.8.1's `CategoryTemplateContext`: one category's templates for one month. Build it, run each
 * priority the budget has with [runPriority], share what is left with [runRemainder], then read [values].
 * Templates are kept by their place in the category's list, so two identical lines stay apart.
 */
internal class TemplateContext private constructor(
    val categoryId: String,
    private val income: Boolean,
    private val all: List<Template>,
    private val month: YearMonth,
    private val fromLastMonth: Long,
    private val previouslyBudgeted: Long,
    private val sheet: TemplateSheet,
    private val skipAvailableClamp: Boolean,
) {
    /** Template indexes that run in a priority. */
    private val templates = all.indices.filter { all[it].priority != null && all[it] !is Template.Remainder && all[it] !is Template.Limit }
    private val remainders = all.indices.filter { all[it] is Template.Remainder }
    private val goals = all.indices.filter { all[it] is Template.Goal }
    private val priorities = templates.mapNotNull { all[it].priority }.toSet()
    private val remainderWeight = remainders.sumOf { (all[it] as Template.Remainder).weight.toDouble() }

    private var toBudgetAmount = 0L
    private var fullAmount: Long? = null
    private var limitMet = false
    private var limitExcess = 0L
    private var limitAmount = 0L
    private var limitCheck = false
    private var limitHold = false

    /** What each template asked for before limits and clamps, and what it ended up with. */
    val asked = HashMap<Int, Long>()
    val contribution = HashMap<Int, Long>()

    init {
        checkLimit()
        if (templates.count { all[it] is Template.Spend } > 1) throw TemplateFailure("Only one spend template is allowed per category")
        if (goals.size > 1) throw TemplateFailure("Only one #goal is allowed per category")
    }

    val goalOnly: Boolean get() = templates.isEmpty() && remainders.isEmpty() && goals.isNotEmpty()

    fun priorities(): Set<Int> = priorities

    fun hasRemainder(): Boolean = remainderWeight > 0 && !limitMet

    fun remainderWeight(): Double = remainderWeight

    fun limitExcess(): Long = limitExcess

    /** Budgets the templates at [priority]; returns what it took from to budget (negative for an income category). */
    fun runPriority(
        priority: Int,
        budgetAvail: Long,
        availStart: Long,
    ): Long {
        if (priority !in priorities || limitMet) return 0L
        val at = templates.filter { all[it].priority == priority }
        var available = budgetAvail
        var toBudget = 0L
        val local = LinkedHashMap<Int, Long>()
        var byDone = false
        var byNeed: Map<Int, Long> = emptyMap()
        var scheduleDone = false
        var scheduleShare: Map<Int, Double> = emptyMap()
        for (index in at) {
            val newBudget =
                when (val template = all[index]) {
                    is Template.Simple -> template.monthly?.let { cents(it) } ?: (limitAmount - fromLastMonth)
                    is Template.Refill -> limitAmount - fromLastMonth
                    is Template.Copy -> sheet.budget(month.minusMonths(template.lookBack.toLong()), categoryId)
                    is Template.Periodic -> periodic(template)
                    is Template.Spend -> spend(template)
                    is Template.Percentage -> percentage(template, availStart)
                    is Template.By ->
                        if (byDone) {
                            0L
                        } else {
                            byDone = true
                            val (total, need) = by(at)
                            byNeed = need
                            total
                        }
                    is Template.Schedule ->
                        if (scheduleDone) {
                            0L
                        } else {
                            scheduleDone = true
                            val schedules = at.mapNotNull { i -> (all[i] as? Template.Schedule)?.let { i to it } }
                            val entries = sheet.schedules.list(schedules, month, categoryId, income)
                            val result = sheet.schedules.run(entries, month, categoryId, fromLastMonth + toBudget, fromLastMonth, toBudget)
                            scheduleShare = result.perSchedule
                            result.toBudget - toBudget
                        }
                    is Template.Average -> average(template)
                    is Template.Remainder, is Template.Limit, is Template.Goal -> 0L
                }
            available -= newBudget
            toBudget += newBudget
            local[index] = (local[index] ?: 0L) + newBudget
        }
        redistribute(local, at.filter { all[it] is Template.By }) { (byNeed[it] ?: 0L).coerceAtLeast(0L).toDouble() }
        redistribute(local, at.filter { all[it] is Template.Schedule }) { (scheduleShare[it] ?: 0.0).coerceAtLeast(0.0) }
        local.forEach { (index, value) -> asked[index] = (asked[index] ?: 0L) + value }

        var scale = 1.0
        if (limitCheck && toBudget + toBudgetAmount + fromLastMonth >= limitAmount) {
            val orig = toBudget
            toBudget = limitAmount - toBudgetAmount - fromLastMonth
            limitMet = true
            available = available + orig - toBudget
            if (orig > 0) scale *= toBudget.toDouble() / orig
        }
        if (sheet.hideDecimal) {
            val preRound = toBudget
            toBudget = removeFraction(toBudget)
            if (preRound != 0L) scale *= toBudget.toDouble() / preRound
        }
        fullAmount = (fullAmount ?: 0L) + toBudget
        if (priority > 0 && available < 0 && !income && !skipAvailableClamp) {
            val adjusted = max(0L, toBudget + available)
            if (toBudget > 0) scale *= adjusted.toDouble() / toBudget
            toBudget = adjusted
        }
        toBudgetAmount += toBudget

        // Each row's share of what the priority ended with; the last takes what rounding leaves.
        val perRowScale = max(0.0, scale)
        var remaining = max(0L, toBudget)
        val items = local.entries.toList()
        items.forEachIndexed { n, (index, value) ->
            val share = if (n == items.lastIndex) remaining else max(0L, min(remaining, jsRound(value * perRowScale)))
            contribution[index] = (contribution[index] ?: 0L) + share
            remaining -= share
        }
        return if (income) -toBudget else toBudget
    }

    fun runRemainder(
        budgetAvail: Long,
        perWeight: Double,
    ): Long {
        if (remainders.isEmpty()) return 0L
        var toBudget = jsRound(remainderWeight * perWeight)
        var smallest = 1L
        if (sheet.hideDecimal) {
            toBudget = removeFraction(toBudget)
            smallest = 100L
        }
        // Rounding can leave a cent over or ask a cent too many: then it takes all that is left.
        if (toBudget > budgetAvail || budgetAvail - toBudget <= smallest) toBudget = budgetAvail
        if (limitCheck && toBudget + toBudgetAmount + fromLastMonth >= limitAmount) {
            toBudget = limitAmount - toBudgetAmount - fromLastMonth
            limitMet = true
        }
        if (toBudget > 0 && remainderWeight > 0) {
            var remaining = toBudget
            remainders.forEachIndexed { n, index ->
                val weight = (all[index] as Template.Remainder).weight.toDouble()
                val share = if (n == remainders.lastIndex) remaining else jsRound(toBudget * (weight / remainderWeight))
                val allocated = max(0L, min(share, remaining))
                contribution[index] = (contribution[index] ?: 0L) + allocated
                remaining -= allocated
            }
        }
        toBudgetAmount += toBudget
        return toBudget
    }

    data class Values(
        val budgeted: Long,
        val goal: Long?,
        val longGoal: Boolean,
    )

    fun values(): Values {
        if (goals.isNotEmpty()) {
            if (goalOnly) toBudgetAmount = previouslyBudgeted
            return Values(toBudgetAmount, cents((all[goals.first()] as Template.Goal).amount), longGoal = true)
        }
        return Values(toBudgetAmount, fullAmount, longGoal = false)
    }

    private fun removeFraction(amount: Long): Long = jsRound(amount / 100.0) * 100

    private fun checkLimit() {
        for (template in all) {
            val limit =
                when (template) {
                    is Template.Limit -> template.limit
                    is Template.Simple -> template.limit
                    is Template.Periodic -> template.limit
                    is Template.Remainder -> template.limit
                    else -> null
                } ?: continue
            if (limitCheck) throw TemplateFailure("Only one `up to` allowed per category")
            val base = cents(limit.amount)
            limitAmount =
                when (limit.period) {
                    LimitPeriod.DAILY -> limitAmount + base * month.lengthOfMonth()
                    LimitPeriod.WEEKLY -> {
                        val start = limit.start ?: throw TemplateFailure("Weekly limit requires a start date (YYYY-MM-DD)")
                        var week = start
                        var total = limitAmount
                        // Every week from the start that falls in this month (or none before it).
                        while (YearMonth.from(week) <= month) {
                            if (YearMonth.from(week) >= month) total += base
                            week = week.plusWeeks(1)
                        }
                        total
                    }
                    LimitPeriod.MONTHLY -> base
                }
            limitCheck = true
            limitHold = limit.hold
            if (fromLastMonth >= limitAmount) {
                limitMet = true
                if (limitHold) {
                    limitExcess = 0L
                    toBudgetAmount = 0L
                    fullAmount = 0L
                } else {
                    limitExcess = fromLastMonth - limitAmount
                    toBudgetAmount = -limitExcess
                    fullAmount = -limitExcess
                }
            }
        }
    }

    private fun periodic(template: Template.Periodic): Long {
        val amount = cents(template.amount)
        if (template.every < 1) throw TemplateFailure("Template repeat interval must be a whole number greater than 0")
        return when (template.unit) {
            // monthUtils.addMonths keeps only the month, so month and year repeats count whole months.
            RepeatUnit.MONTH, RepeatUnit.YEAR -> {
                val step = template.every.toLong() * if (template.unit == RepeatUnit.YEAR) 12 else 1
                var at = YearMonth.from(template.starting)
                while (at < month) at = at.plusMonths(step)
                if (at == month) amount else 0L
            }
            RepeatUnit.DAY, RepeatUnit.WEEK -> {
                val step = template.every.toLong() * if (template.unit == RepeatUnit.WEEK) 7 else 1
                var date = template.starting
                val first = month.atDay(1)
                while (date < first) date = date.plusDays(step)
                var total = 0L
                while (YearMonth.from(date) == month) {
                    total += amount
                    date = date.plusDays(step)
                }
                total
            }
        }
    }

    private fun spend(template: Template.Spend): Long {
        var from = template.from
        var to = template.month
        val repeat = template.repeatMonths
        if (repeat != null && repeat > 0) {
            while (to < month) {
                to = to.plusMonths(repeat.toLong())
                from = from.plusMonths(repeat.toLong())
            }
        }
        var alreadyBudgeted = fromLastMonth
        var first = true
        var m = from
        while (m < month) {
            if (first) {
                alreadyBudgeted = sheet.leftover(m, categoryId) - sheet.spent(m, categoryId)
                first = false
            } else {
                alreadyBudgeted += sheet.budget(m, categoryId)
            }
            m = m.plusMonths(1)
        }
        val numMonths = ChronoUnit.MONTHS.between(month, to)
        if (numMonths < 0) return 0L
        return jsRound((cents(template.amount) - alreadyBudgeted).toDouble() / (numMonths + 1))
    }

    private fun percentage(
        template: Template.Percentage,
        availableFunds: Long,
    ): Long {
        val from = if (template.previous) month.minusMonths(1) else month
        val base =
            when (val name = template.category.lowercase()) {
                "all income" -> sheet.income(null, from)
                "available funds" -> availableFunds
                else -> {
                    val id =
                        sheet.incomeCategories.firstOrNull { (id, n) -> id == template.category || n.lowercase() == name }?.first
                            ?: throw TemplateFailure("Income category \"${template.category}\" not found for percentage template")
                    sheet.income(id, from)
                }
            }
        return max(0L, jsRound(base * (template.percent.toDouble() / 100)))
    }

    private fun average(template: Template.Average): Long {
        var average = sheet.average(month, categoryId, template.months).toDouble()
        if (average < 0) average *= -1
        val adjustment = template.adjustment
        if (adjustment != null) {
            average =
                if (adjustment.percent) {
                    (1 + adjustment.amount.toDouble() / 100) * average
                } else {
                    average + cents(adjustment.amount)
                }
        }
        return jsRound(average)
    }

    /** `runBy`: every by template at this priority together, saving for the nearest target first. */
    private fun by(at: List<Int>): Pair<Long, Map<Int, Long>> {
        val bys = at.filter { all[it] is Template.By }
        val info =
            bys.map { index ->
                val template = all[index] as Template.By
                val period = template.repeatMonths
                var target = template.month
                var numMonths = ChronoUnit.MONTHS.between(month, target)
                while (numMonths < 0 && period != null && period > 0) {
                    target = target.plusMonths(period.toLong())
                    numMonths = ChronoUnit.MONTHS.between(month, target)
                }
                numMonths to period
            }
        val shortNumMonths = info.minOfOrNull { it.first } ?: 0L
        val need = LinkedHashMap<Int, Long>()
        var totalNeeded = 0L
        bys.forEachIndexed { n, index ->
            val template = all[index] as Template.By
            val (numMonths, period) = info[n]
            val target = cents(template.amount)
            val amount =
                when {
                    numMonths > shortNumMonths && period != null ->
                        jsRound(target.toDouble() / period * (period - numMonths + shortNumMonths))
                    numMonths > shortNumMonths -> jsRound(target.toDouble() / (numMonths + 1) * (shortNumMonths + 1))
                    else -> target
                }
            need[index] = amount
            totalNeeded += amount
        }
        return jsRound((totalNeeded - fromLastMonth).toDouble() / (shortNumMonths + 1)) to need
    }

    /** `redistributeBatch`: a batch total credited to its first template, split back across its siblings by weight. */
    private fun redistribute(
        local: MutableMap<Int, Long>,
        siblings: List<Int>,
        weightOf: (Int) -> Double,
    ) {
        if (siblings.size < 2) return
        var total = 0L
        for (sibling in siblings) {
            total += local[sibling] ?: 0L
            local[sibling] = 0L
        }
        if (total == 0L) return
        val totalWeight = siblings.sumOf(weightOf)
        var remaining = total
        siblings.forEachIndexed { n, sibling ->
            val share =
                when {
                    n == siblings.lastIndex -> remaining
                    totalWeight <= 0 -> jsRound(total.toDouble() / siblings.size)
                    else -> jsRound(total * weightOf(sibling) / totalWeight)
                }
            val allocated = max(0L, min(share, remaining))
            local[sibling] = (local[sibling] ?: 0L) + allocated
            remaining -= allocated
        }
    }

    companion object {
        /** `CategoryTemplateContext.init`: checks the templates, then works out what the category carries in. */
        fun init(
            categoryId: String,
            income: Boolean,
            templates: List<Template>,
            month: YearMonth,
            budgeted: Long,
            sheet: TemplateSheet,
            skipAvailableClamp: Boolean = false,
        ): TemplateContext {
            val last = month.minusMonths(1)
            var fromLastMonth = sheet.leftover(last, categoryId)
            val carryover = sheet.carryover(last, categoryId)
            if ((fromLastMonth < 0 && !carryover) || income || (sheet.tracking && !carryover)) fromLastMonth = 0L
            checkByAndSchedule(templates, month, sheet)
            checkPercentage(templates, sheet)
            return TemplateContext(
                categoryId,
                income,
                templates,
                month,
                fromLastMonth,
                budgeted,
                sheet,
                skipAvailableClamp,
            )
        }

        private fun checkByAndSchedule(
            templates: List<Template>,
            month: YearMonth,
            sheet: TemplateSheet,
        ) {
            val batched = templates.filter { it is Template.Schedule || it is Template.By }
            if (batched.isEmpty()) return
            val ids = sheet.activeSchedules.map { it.first }.toSet()
            val names =
                sheet.activeSchedules
                    .map { it.second.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet()
            for (template in templates.filterIsInstance<Template.Schedule>()) {
                val id = template.scheduleId
                if (id != null) {
                    if (id !in ids) throw TemplateFailure("Schedule ${template.name} does not exist")
                } else if (template.name.trim() !in names) {
                    throw TemplateFailure("Schedule ${template.name.trim()} does not exist")
                }
            }
            val lowest = batched.mapNotNull { it.priority }.min()
            if (batched.any { it.priority != lowest }) {
                throw TemplateFailure(
                    "Schedule and By templates must be the same priority level. Fix by setting all Schedule and By templates to priority level $lowest",
                )
            }
            for (template in templates) {
                val (target, repeats) =
                    when (template) {
                        is Template.By -> template.month to (template.repeat != null || template.annual == true)
                        is Template.Spend -> template.month to (template.repeat != null || template.annual == true)
                        else -> continue
                    }
                if (target < month && !repeats) throw TemplateFailure("Target month has passed, remove or update the target month")
            }
        }

        private fun checkPercentage(
            templates: List<Template>,
            sheet: TemplateSheet,
        ) {
            val percentages = templates.filterIsInstance<Template.Percentage>()
            if (percentages.isEmpty()) return
            val names = sheet.incomeCategories.map { it.second.lowercase() }.toSet()
            val ids = sheet.incomeCategories.map { it.first }.toSet()
            for (template in percentages) {
                val raw = template.category
                val lowered = raw.lowercase()
                if (lowered == "all income" || lowered == "available funds" || lowered in names || raw in ids) continue
                throw TemplateFailure("Category \"$raw\" is not found in available income categories")
            }
        }
    }
}

/** `distributeRemainder`: shares what is left by remainder weight, again and again while a cap sends some back. */
internal fun distributeRemainder(
    contexts: List<TemplateContext>,
    available: Long,
): Long {
    var avail = available
    var sharing = contexts.filter { it.hasRemainder() }
    while (avail > 0 && sharing.isNotEmpty()) {
        val weight = sharing.sumOf { it.remainderWeight() }
        val perWeight = avail.toDouble() / weight
        val before = avail
        sharing.forEach { avail -= it.runRemainder(avail, perWeight) }
        if (avail == before) break
        sharing = contexts.filter { it.hasRemainder() }
    }
    return avail
}
