package app.duenorth.budget

import app.duenorth.budget.core.CashFlowMonth
import app.duenorth.budget.core.CashFlowReport
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.NetWorthMonth
import app.duenorth.budget.core.NetWorthReport
import app.duenorth.budget.core.ReportRange
import app.duenorth.budget.core.ReportsPage
import app.duenorth.budget.core.SpendingGroupBy
import app.duenorth.budget.core.SpendingReport
import app.duenorth.budget.core.SpendingRow
import java.time.YearMonth

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
}
