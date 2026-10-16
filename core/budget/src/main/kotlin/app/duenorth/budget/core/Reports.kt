package app.duenorth.budget.core

import java.time.LocalDate
import java.time.YearMonth
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

    // The reports hub: one line per report, under what it shows.
    const val NET_WORTH_TODAY = "on and off budget, as of today"
    const val CASH_FLOW_NOTE = "income and expenses by month"
    const val NET_WORTH_BY_MONTH = "net worth by month"
    const val NET_WORTH_BY_MONTH_NOTE = "how the total moved over time"
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

    /** Expenses only: negative, on-budget, not a transfer; largest first. */
    fun spending(
        range: ReportRange,
        groupBy: SpendingGroupBy,
    ): SpendingReport {
        val low = range.start.atDay(1).toInt()
        val high = range.end.atEndOfMonth().toInt()
        val expenses = lines.filter { it.amount < 0 && !it.offBudget && !it.transfer && it.date in low..high }
        val names = names()
        val groupOf = categoryGroups()
        val key: (Line) -> String? =
            when (groupBy) {
                SpendingGroupBy.CATEGORY -> { line -> line.categoryId }
                SpendingGroupBy.GROUP -> { line -> line.categoryId?.let { groupOf[it] } }
                SpendingGroupBy.PAYEE -> { line -> line.payeeId }
            }
        val rows =
            expenses
                .groupBy(key)
                .map { (id, group) ->
                    val label =
                        when {
                            id == null && groupBy == SpendingGroupBy.PAYEE -> ReportsCopy.NO_PAYEE
                            id == null -> ReportsCopy.UNCATEGORIZED
                            else -> names[id] ?: id
                        }
                    SpendingRow(id, label, group.sumOf { it.amount })
                }.sortedWith(compareByDescending<SpendingRow> { abs(it.amountMinor) }.thenBy { it.label })
        return SpendingReport(groupBy, rows)
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
                SELECT t.date, t.amount, a.offbudget, a.id AS account, p.transfer_acct, t.category, t.description
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
                    date = row.long("date").toInt(),
                    amount = row.long("amount"),
                    offBudget = row.values["offbudget"] != null && row.bool("offbudget"),
                    accountId = row.str("account").orEmpty(),
                    transfer = !row.str("transfer_acct").isNullOrEmpty(),
                    categoryId = row.str("category")?.takeIf { it.isNotEmpty() },
                    payeeId = row.str("description")?.takeIf { it.isNotEmpty() },
                )
            }

    private fun names(): Map<String, String> {
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
