package app.duenorth.budget

import app.duenorth.budget.core.CashFlowMonth
import app.duenorth.budget.core.CashFlowReport
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.NetWorthHistory
import app.duenorth.budget.core.NetWorthMonth
import app.duenorth.budget.core.NetWorthPoint
import app.duenorth.budget.core.NetWorthReport
import app.duenorth.budget.core.NetWorthSpan
import app.duenorth.budget.core.ReportRange
import app.duenorth.budget.core.ReportsPage
import app.duenorth.budget.core.SpendingDrill
import app.duenorth.budget.core.SpendingFilter
import app.duenorth.budget.core.SpendingGroupBy
import app.duenorth.budget.core.SpendingReport
import app.duenorth.budget.core.SpendingRow
import app.duenorth.budget.core.SpendingTransaction
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** A four-month report like the `parity/reports` fixture, for the report screen tests. */
internal object ReportSamples {
    private val usd = Currencies.byCode("USD")!!
    private val range = ReportRange(YearMonth.of(2026, 6), YearMonth.of(2026, 9))

    fun page(groupBy: SpendingGroupBy = SpendingGroupBy.CATEGORY): ReportsPage =
        ReportsPage(
            range = range,
            currency = usd,
            cashFlow =
                CashFlowReport(
                    listOf(
                        CashFlowMonth(YearMonth.of(2026, 6), 400_000, -205_000),
                        CashFlowMonth(YearMonth.of(2026, 7), 400_000, -205_000),
                        CashFlowMonth(YearMonth.of(2026, 8), 400_000, -215_000),
                        CashFlowMonth(YearMonth.of(2026, 9), 400_000, -205_000),
                    ),
                ),
            netWorth =
                NetWorthReport(
                    listOf(
                        NetWorthMonth(YearMonth.of(2026, 6), 195_000, 0),
                        NetWorthMonth(YearMonth.of(2026, 7), 390_000, 0),
                        NetWorthMonth(YearMonth.of(2026, 8), 505_000, 70_000),
                        NetWorthMonth(YearMonth.of(2026, 9), 700_000, 82_500),
                    ),
                    includeOffBudget = true,
                ),
            spending =
                SpendingReport(
                    groupBy,
                    when (groupBy) {
                        SpendingGroupBy.PAYEE ->
                            listOf(
                                SpendingRow("landlord", "Landlord", -600_000),
                                SpendingRow("grocer", "Grocer", -168_000),
                                SpendingRow("cafe", "Cafe", -32_000),
                                SpendingRow("shop", "Shop", -20_000),
                                SpendingRow("market", "Market", -10_000),
                            )
                        else ->
                            listOf(
                                SpendingRow("bills", "Bills", -600_000),
                                SpendingRow("food", "Food", -206_000),
                                SpendingRow(null, "uncategorized", -20_000),
                                SpendingRow("general", "General", -4_000),
                            )
                    },
                ),
        )

    private val food = SpendingFilter(SpendingGroupBy.GROUP, "food", "Food")
    private val groceries = SpendingFilter(SpendingGroupBy.CATEGORY, "groceries", "Groceries")

    /** Spending by group → food: its categories. */
    val groupPath = listOf(food)

    /** ... → food → groceries: its transactions. */
    val categoryPath = listOf(food, groceries)

    fun groupDrill(): SpendingDrill =
        SpendingDrill(
            range = range,
            currency = usd,
            path = groupPath,
            breakdown =
                SpendingReport(
                    SpendingGroupBy.CATEGORY,
                    listOf(
                        SpendingRow("groceries", "Groceries", -168_000),
                        SpendingRow("dining", "Dining out", -32_000),
                        SpendingRow("coffee", "Coffee", -6_000),
                    ),
                ),
            transactions = emptyList(),
            totalMinor = -206_000,
        )

    fun categoryDrill(): SpendingDrill {
        val lines =
            listOf(
                Triple(20260927, "Grocer", -21_400L),
                Triple(20260920, "Market", -8_650L),
                Triple(20260913, "Grocer", -19_875L),
                Triple(20260906, "Grocer", -22_310L),
                Triple(20260830, "Market", -6_120L),
                Triple(20260823, "Grocer", -24_045L),
                Triple(20260816, "Grocer", -18_900L),
                Triple(20260809, "Market", -9_300L),
                Triple(20260802, "Grocer", -37_400L),
            )
        return SpendingDrill(
            range = range,
            currency = usd,
            path = categoryPath,
            breakdown = null,
            transactions =
                lines.mapIndexed { index, (date, payee, amount) ->
                    SpendingTransaction("t$index", "checking", "t$index", date, payee, "Groceries", amount)
                },
            totalMinor = lines.sumOf { it.third },
        )
    }

    fun empty(): ReportsPage {
        val months = ReportRange(YearMonth.of(2025, 1), YearMonth.of(2025, 3)).months
        return ReportsPage(
            range = ReportRange(months.first(), months.last()),
            currency = usd,
            cashFlow = CashFlowReport(months.map { CashFlowMonth(it, 0, 0) }),
            netWorth = NetWorthReport(months.map { NetWorthMonth(it, 0, 0) }, includeOffBudget = true),
            spending = SpendingReport(SpendingGroupBy.CATEGORY, emptyList()),
        )
    }

    /**
     * Six months to 2026-10-07, a point every day and a half: a paycheck every two weeks against daily spending,
     * a car repair in late june, and an off budget savings account opened in july.
     */
    fun history(): NetWorthHistory {
        val today = LocalDate.of(2026, 10, 7)
        val start = today.minusMonths(6)
        val days = ChronoUnit.DAYS.between(start, today)
        val points =
            (0L..119L).map { step ->
                val day = days * step / 119
                val paychecks = day / 14 + 1
                val repair = if (day >= 80) 95_000L else 0L
                val on = 180_000 + paychecks * 140_000 - day * 8_500 - repair
                val off = if (day >= 95) 70_000 + (day - 95) * 150 else 0L
                NetWorthPoint(start.plusDays(day), on, off)
            }
        return NetWorthHistory(NetWorthSpan.SIX_MONTHS, usd, points)
    }
}
