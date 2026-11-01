package app.duenorth.budget.core

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Words for spec 010's reports (`contracts/reports.md`). */
object ReportsCopy {
    const val TITLE = "reports"
    const val CASH_FLOW = "cash flow"
    const val NET_WORTH = "net worth"
    const val SPENDING = "spending"
    const val INCOME = "income"
    const val EXPENSES = "expenses"
    const val NET = "net"
    const val TOTAL = "total"
    const val ON_BUDGET = "on budget"
    const val OFF_BUDGET = "off budget"
    const val UNCATEGORIZED = "uncategorized"
    const val NO_PAYEE = "no payee"
    const val NOTHING = "nothing in this range"
    const val BY = "by"
    const val TRANSACTIONS = "transactions"

    // The reports hub: one line per report, under what it shows.
    const val NET_WORTH_TODAY = "on and off budget, as of today"
    const val NET_WORTH_SINCE = "since"
    const val MONTH_REVIEW = "month review"
    const val MONTH_REVIEW_NOTE = "income and spending for"
    const val CASH_FLOW_NOTE = "income and expenses by month"
    const val SPENDING_NOTE = "by category, group or payee"
}

/** Inclusive month range. */
data class ReportRange(
    val start: YearMonth,
    val end: YearMonth,
) {
    val months: List<YearMonth>
        get() = generateSequence(start) { it.plusMonths(1) }.takeWhile { it <= end }.toList()
}

enum class ReportPreset(
    val label: String,
) {
    THIS_MONTH("this month"),
    LAST_3("last 3 months"),
    LAST_6("last 6 months"),
    LAST_12("last 12 months"),
    YEAR_TO_DATE("year to date"),
    CUSTOM("custom"),
    ;

    /** The range ending this month; [CUSTOM] keeps [custom]. */
    fun range(
        today: LocalDate,
        custom: ReportRange? = null,
    ): ReportRange {
        val now = YearMonth.from(today)
        return when (this) {
            THIS_MONTH -> ReportRange(now, now)
            LAST_3 -> ReportRange(now.minusMonths(2), now)
            LAST_6 -> ReportRange(now.minusMonths(5), now)
            LAST_12 -> ReportRange(now.minusMonths(11), now)
            YEAR_TO_DATE -> ReportRange(YearMonth.of(now.year, 1), now)
            CUSTOM -> custom ?: ReportRange(now.minusMonths(5), now)
        }
    }
}

data class CashFlowMonth(
    val month: YearMonth,
    val incomeMinor: Long,
    val expenseMinor: Long,
) {
    val netMinor: Long get() = incomeMinor + expenseMinor
}

data class CashFlowReport(
    val months: List<CashFlowMonth>,
) {
    val incomeMinor: Long get() = months.sumOf { it.incomeMinor }
    val expenseMinor: Long get() = months.sumOf { it.expenseMinor }
    val netMinor: Long get() = incomeMinor + expenseMinor
}

data class NetWorthMonth(
    val month: YearMonth,
    val onBudgetMinor: Long,
    val offBudgetMinor: Long,
) {
    fun total(includeOffBudget: Boolean): Long = onBudgetMinor + if (includeOffBudget) offBudgetMinor else 0L
}

/** The net worth graph's time spans, each ending today. */
enum class NetWorthSpan(
    val label: String,
) {
    ONE_MONTH("1m"),
    THREE_MONTHS("3m"),
    SIX_MONTHS("6m"),
    YEAR_TO_DATE("ytd"),
    ONE_YEAR("1y"),
    ALL("all"),
    ;

    /** The first day shown; [ALL] starts at [earliest], the budget's first transaction. */
    fun start(
        today: LocalDate,
        earliest: LocalDate,
    ): LocalDate =
        when (this) {
            ONE_MONTH -> today.minusMonths(1)
            THREE_MONTHS -> today.minusMonths(3)
            SIX_MONTHS -> today.minusMonths(6)
            YEAR_TO_DATE -> today.withDayOfYear(1)
            ONE_YEAR -> today.minusYears(1)
            ALL -> minOf(earliest, today)
        }
}

/** Both sides of the budget at the end of [date]. */
data class NetWorthPoint(
    val date: LocalDate,
    val onBudgetMinor: Long,
    val offBudgetMinor: Long,
) {
    fun total(includeOffBudget: Boolean): Long = onBudgetMinor + if (includeOffBudget) offBudgetMinor else 0L
}

/** Net worth from [span]'s start through today, oldest first; the last point is today. */
data class NetWorthHistory(
    val span: NetWorthSpan,
    val currency: CurrencySpec,
    val points: List<NetWorthPoint>,
) {
    val first: NetWorthPoint? get() = points.firstOrNull()
    val last: NetWorthPoint? get() = points.lastOrNull()

    /** How far the total moved over the span. */
    fun change(includeOffBudget: Boolean): Long = (last?.total(includeOffBudget) ?: 0L) - (first?.total(includeOffBudget) ?: 0L)
}

data class NetWorthReport(
    val months: List<NetWorthMonth>,
    val includeOffBudget: Boolean,
)

enum class SpendingGroupBy(
    val label: String,
) {
    CATEGORY("category"),
    GROUP("group"),
    PAYEE("payee"),
}

data class SpendingRow(
    /** Category, group, or payee id; null for the uncategorized row. */
    val id: String?,
    val label: String,
    /** Negative, as stored. */
    val amountMinor: Long,
)

data class SpendingReport(
    val groupBy: SpendingGroupBy,
    val rows: List<SpendingRow>,
) {
    val totalMinor: Long get() = rows.sumOf { it.amountMinor }
}

/** One step down the spending report: only the expenses whose [groupBy] key is [id] (null: uncategorized, no payee). */
data class SpendingFilter(
    val groupBy: SpendingGroupBy,
    val id: String?,
    val label: String,
)

/** One expense under a drilled spending row. */
data class SpendingTransaction(
    val id: String,
    val accountId: String,
    /** What the transaction form opens: the split parent for a split line, else [id]. */
    val openId: String,
    /** yyyymmdd. */
    val date: Int,
    val payee: String,
    val category: String,
    /** Negative, as stored. */
    val amountMinor: Long,
)

/**
 * Spending report → a row → ...: the expenses under [path], either broken down one level further
 * ([breakdown]) or, at the bottom, listed one by one ([transactions]).
 */
data class SpendingDrill(
    val range: ReportRange,
    val currency: CurrencySpec,
    val path: List<SpendingFilter>,
    val breakdown: SpendingReport?,
    val transactions: List<SpendingTransaction>,
    val totalMinor: Long,
)

/** How the spending report drills: group → categories, category → payees, payee → categories, then transactions. */
object SpendingDrillLevels {
    /** The grouping one level under [path], or null when [path] lists its transactions. */
    fun next(path: List<SpendingFilter>): SpendingGroupBy? {
        if (path.size != 1) return null
        val top = path.single()
        return when (top.groupBy) {
            SpendingGroupBy.GROUP -> if (top.id == null) null else SpendingGroupBy.CATEGORY
            SpendingGroupBy.CATEGORY -> SpendingGroupBy.PAYEE
            SpendingGroupBy.PAYEE -> SpendingGroupBy.CATEGORY
        }
    }
}

data class ReportsPage(
    val range: ReportRange,
    val currency: CurrencySpec,
    val cashFlow: CashFlowReport,
    val netWorth: NetWorthReport,
    val spending: SpendingReport,
) {
    val empty: Boolean
        get() = cashFlow.months.all { it.incomeMinor == 0L && it.expenseMinor == 0L } && spending.rows.isEmpty()
}

/**
 * Read-only reports over living transactions (R11, R16 #10): deleted rows, rows in deleted
 * accounts, and split parents that have children are left out, as `v_transactions_internal_alive`.
 */
class ReportsReader(
    private val session: SqlSession,
    private val today: LocalDate,
) {
    private data class Line(
        val id: String,
        val parentId: String?,
        val date: Int,
        val amount: Long,
        val offBudget: Boolean,
        val accountId: String,
        val transfer: Boolean,
        val categoryId: String?,
        val payeeId: String?,
    )

    private val lines: List<Line> by lazy { load() }

    /** On-budget, transfers left out, nothing after today. */
    fun cashFlow(range: ReportRange): CashFlowReport {
        val todayInt = today.toInt()
        val relevant = lines.filter { !it.offBudget && !it.transfer && it.date <= todayInt }
        return CashFlowReport(
            range.months.map { month ->
                val inMonth = relevant.filter { YearMonth.of(it.date / 10_000, it.date / 100 % 100) == month }
                CashFlowMonth(
                    month = month,
                    incomeMinor = inMonth.filter { it.amount > 0 }.sumOf { it.amount },
                    expenseMinor = inMonth.filter { it.amount < 0 }.sumOf { it.amount },
                )
            },
        )
    }

    /** Each account's running balance through every month end in [range], summed by side of the budget. */
    fun netWorth(
        range: ReportRange,
        includeOffBudget: Boolean,
    ): NetWorthReport =
        NetWorthReport(
            range.months.map { month ->
                val end = month.atEndOfMonth().toInt()
                val through = lines.filter { it.date <= end }
                NetWorthMonth(
                    month = month,
                    onBudgetMinor = through.filter { !it.offBudget }.sumOf { it.amount },
                    offBudgetMinor = through.filter { it.offBudget }.sumOf { it.amount },
                )
            },
            includeOffBudget,
        )

    /**
     * Each side's running balance at the end of evenly spaced days from [span]'s start through today, at most
     * [maxPoints] of them so a long span still draws as one smooth line. Later-dated transactions are left out.
     */
    fun netWorthHistory(
        span: NetWorthSpan,
        currency: CurrencySpec,
        maxPoints: Int = NET_WORTH_POINTS,
    ): NetWorthHistory {
        val sorted = lines.sortedBy { it.date }
        val earliest = sorted.firstOrNull()?.date?.let { LocalDate.of(it / 10_000, it / 100 % 100, it % 100) } ?: today
        val start = span.start(today, earliest)
        val days = ChronoUnit.DAYS.between(start, today)
        val steps = minOf(days, (maxPoints - 1).toLong()).coerceAtLeast(1)
        val dates =
            (0..steps)
                .map { start.plusDays(days * it / steps) }
                .distinct()
        var index = 0
        var on = 0L
        var off = 0L
        val points =
            dates.map { date ->
                val through = date.toInt()
                while (index < sorted.size && sorted[index].date <= through) {
                    val line = sorted[index++]
                    if (line.offBudget) off += line.amount else on += line.amount
                }
                NetWorthPoint(date, on, off)
            }
        return NetWorthHistory(span, currency, points)
    }

    /** Expenses only: negative, on-budget, not a transfer; largest first. */
    fun spending(
        range: ReportRange,
        groupBy: SpendingGroupBy,
    ): SpendingReport = rowsOf(expenses(range), groupBy)

    /**
     * The expenses under [path] (see [SpendingDrillLevels]). A level that would show a single row
     * lists that row's transactions instead, newest first.
     */
    fun spendingDrill(
        range: ReportRange,
        path: List<SpendingFilter>,
        currency: CurrencySpec,
    ): SpendingDrill {
        val keys = keys()
        val under = expenses(range).filter { line -> path.all { keys.getValue(it.groupBy)(line) == it.id } }
        val breakdown = SpendingDrillLevels.next(path)?.let { rowsOf(under, it) }?.takeIf { it.rows.size > 1 }
        val transactions =
            if (breakdown != null) {
                emptyList()
            } else {
                under
                    .sortedWith(compareByDescending<Line> { it.date }.thenBy { it.amount })
                    .map { line ->
                        SpendingTransaction(
                            id = line.id,
                            accountId = line.accountId,
                            openId = line.parentId ?: line.id,
                            date = line.date,
                            payee = label(line.payeeId, SpendingGroupBy.PAYEE, names),
                            category = label(line.categoryId, SpendingGroupBy.CATEGORY, names),
                            amountMinor = line.amount,
                        )
                    }
            }
        return SpendingDrill(range, currency, path, breakdown, transactions, under.sumOf { it.amount })
    }

    private fun expenses(range: ReportRange): List<Line> {
        val low = range.start.atDay(1).toInt()
        val high = range.end.atEndOfMonth().toInt()
        return lines.filter { it.amount < 0 && !it.offBudget && !it.transfer && it.date in low..high }
    }

    private fun keys(): Map<SpendingGroupBy, (Line) -> String?> =
        mapOf(
            SpendingGroupBy.CATEGORY to { line -> line.categoryId },
            SpendingGroupBy.GROUP to { line -> line.categoryId?.let { groupOf[it] } },
            SpendingGroupBy.PAYEE to { line -> line.payeeId },
        )

    private fun rowsOf(
        expenses: List<Line>,
        groupBy: SpendingGroupBy,
    ): SpendingReport {
        val rows =
            expenses
                .groupBy(keys().getValue(groupBy))
                .map { (id, group) -> SpendingRow(id, label(id, groupBy, names), group.sumOf { it.amount }) }
                .sortedWith(compareByDescending<SpendingRow> { abs(it.amountMinor) }.thenBy { it.label })
        return SpendingReport(groupBy, rows)
    }

    private fun label(
        id: String?,
        groupBy: SpendingGroupBy,
        names: Map<String, String>,
    ): String =
        when {
            id == null && groupBy == SpendingGroupBy.PAYEE -> ReportsCopy.NO_PAYEE
            id == null -> ReportsCopy.UNCATEGORIZED
            else -> names[id] ?: id
        }

    fun page(
        range: ReportRange,
        includeOffBudget: Boolean,
        groupBy: SpendingGroupBy,
        currency: CurrencySpec,
    ): ReportsPage =
        ReportsPage(
            range = range,
            currency = currency,
            cashFlow = cashFlow(range),
            netWorth = netWorth(range, includeOffBudget),
            spending = spending(range, groupBy),
        )

    private fun load(): List<Line> =
        session
            .query(
                """
                SELECT t.id, t.parent_id, t.isChild, t.date, t.amount, a.offbudget, a.id AS account, p.transfer_acct, t.category, t.description
                FROM transactions t
                JOIN accounts a ON a.id = t.acct AND IFNULL(a.tombstone, 0) = 0
                LEFT JOIN payees p ON p.id = t.description
                WHERE IFNULL(t.tombstone, 0) = 0
                  AND t.date IS NOT NULL
                  AND (
                    IFNULL(t.isParent, 0) = 0
                    OR NOT EXISTS (
                      SELECT 1 FROM transactions c WHERE c.parent_id = t.id AND IFNULL(c.tombstone, 0) = 0
                    )
                  )
                  AND (
                    IFNULL(t.isChild, 0) = 0
                    OR EXISTS (
                      SELECT 1 FROM transactions parent WHERE parent.id = t.parent_id AND IFNULL(parent.tombstone, 0) = 0
                    )
                  )
                """.trimIndent(),
            ).map { row ->
                Line(
                    id = row.str("id").orEmpty(),
                    parentId = row.str("parent_id")?.takeIf { it.isNotEmpty() && row.values["isChild"] != null && row.bool("isChild") },
                    date = row.long("date").toInt(),
                    amount = row.long("amount"),
                    offBudget = row.values["offbudget"] != null && row.bool("offbudget"),
                    accountId = row.str("account").orEmpty(),
                    transfer = !row.str("transfer_acct").isNullOrEmpty(),
                    categoryId = row.str("category")?.takeIf { it.isNotEmpty() },
                    payeeId = row.str("description")?.takeIf { it.isNotEmpty() },
                )
            }

    private val names: Map<String, String> by lazy { readNames() }

    private val groupOf: Map<String, String> by lazy { categoryGroups() }

    private fun readNames(): Map<String, String> {
        val out = HashMap<String, String>()
        for (table in listOf("categories", "category_groups", "payees")) {
            session.query("SELECT id, name FROM $table").forEach { row ->
                val name = row.str("name").orEmpty()
                if (name.isNotEmpty()) out[row.str("id").orEmpty()] = name
            }
        }
        return out
    }

    private fun categoryGroups(): Map<String, String> =
        session
            .query("SELECT id, cat_group FROM categories")
            .mapNotNull { row -> row.str("cat_group")?.let { row.str("id").orEmpty() to it } }
            .toMap()

    private fun LocalDate.toInt(): Int = year * 10_000 + monthValue * 100 + dayOfMonth
}

/** Enough points for a smooth line on a phone without drawing every day of a long span. */
private const val NET_WORTH_POINTS = 120
