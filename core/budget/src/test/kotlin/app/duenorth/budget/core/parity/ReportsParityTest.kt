package app.duenorth.budget.core.parity

import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.NetWorthSpan
import app.duenorth.budget.core.ReportRange
import app.duenorth.budget.core.ReportsCopy
import app.duenorth.budget.core.ReportsReader
import app.duenorth.budget.core.SpendingDrillLevels
import app.duenorth.budget.core.SpendingFilter
import app.duenorth.budget.core.SpendingGroupBy
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Spec 010 T051: reports against desktop Actual 26.8.1 (`parity/reports/`). */
class ReportsParityTest {
    private val fixture = ParityFixture.load("reports", "range-2026-06-to-09")
    private val range = ReportRange(YearMonth.of(2026, 6), YearMonth.of(2026, 9))

    @Test
    fun cashFlowLeavesOutTransfersAndOffBudget() {
        val report = read { it.cashFlow(range) }
        assertEquals(fixture.long("cashFlow", "income"), report.incomeMinor)
        assertEquals(fixture.long("cashFlow", "expense"), report.expenseMinor)
        val want = HashMap<YearMonth, Pair<Long, Long>>()
        for ((key, income) in listOf("monthlyIncome" to true, "monthlyExpense" to false)) {
            for (entry in fixture.value("cashFlow", key).jsonArray.map { it.jsonObject }) {
                if (entry["isTransfer"] != JsonNull) continue
                val month = YearMonth.parse(entry.getValue("date").jsonPrimitive.content)
                val amount = entry.getValue("amount").jsonPrimitive.long
                val (i, e) = want[month] ?: (0L to 0L)
                want[month] = if (income) (i + amount) to e else i to (e + amount)
            }
        }
        for (month in report.months) {
            assertEquals("${month.month}", want[month.month] ?: (0L to 0L), month.incomeMinor to month.expenseMinor)
        }
    }

    @Test
    fun netWorthIsTheRunningBalanceAtEachMonthEnd() {
        val report = read { it.netWorth(range, includeOffBudget = true) }
        val accounts = fixture.value("netWorth").jsonObject
        for (month in report.months) {
            var on = 0L
            var off = 0L
            for ((_, account) in accounts) {
                val account = account.jsonObject
                val through =
                    account.getValue("starting").jsonPrimitive.long +
                        account
                            .getValue("monthly")
                            .jsonArray
                            .map { it.jsonObject }
                            .filter { YearMonth.parse(it.getValue("date").jsonPrimitive.content) <= month.month }
                            .sumOf { it.getValue("amount").jsonPrimitive.long }
                if (account.getValue("offbudget").jsonPrimitive.boolean) off += through else on += through
            }
            assertEquals("${month.month} on budget", on, month.onBudgetMinor)
            assertEquals("${month.month} off budget", off, month.offBudgetMinor)
        }
        val september = report.months.last()
        assertEquals(700_000L, september.onBudgetMinor)
        assertEquals(82_500L, september.offBudgetMinor)
        assertEquals(700_000L, september.total(includeOffBudget = false))
    }

    @Test
    fun spendingByCategoryGroupAndPayeeMatchesTheCustomReportRows() {
        val rows =
            fixture
                .value("spendingRows")
                .jsonArray
                .map { it.jsonObject }
                .filter { it["transferAccount"] == JsonNull && !it.getValue("accountOffBudget").jsonPrimitive.boolean }
        for (groupBy in SpendingGroupBy.entries) {
            val field =
                when (groupBy) {
                    SpendingGroupBy.CATEGORY -> "category"
                    SpendingGroupBy.GROUP -> "categoryGroup"
                    SpendingGroupBy.PAYEE -> "payee"
                }
            val want =
                rows
                    .groupBy { (it[field] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
                    .mapValues { (_, group) -> group.sumOf { it.getValue("amount").jsonPrimitive.long } }
            val report = read { it.spending(range, groupBy) }
            assertEquals(groupBy.name, want, report.rows.associate { it.id to it.amountMinor })
            val sizes = report.rows.map { -it.amountMinor }
            assertEquals(sizes.sortedDescending(), sizes)
        }
        val byCategory = read { it.spending(range, SpendingGroupBy.CATEGORY) }
        assertEquals(listOf("Bills", "Food", ReportsCopy.UNCATEGORIZED, "General"), byCategory.rows.map { it.label })
        // The deleted "Mistake" and the split parent at Market are not counted.
        assertEquals(-830_000L, byCategory.totalMinor)
    }

    @Test
    fun eachSpendingRowDrillsDownToTheTransactionsItAdds() {
        val usd = Currencies.byCode("USD")!!
        read { reader ->
            for (groupBy in SpendingGroupBy.entries) {
                for (row in reader.spending(range, groupBy).rows) {
                    val path = listOf(SpendingFilter(groupBy, row.id, row.label))
                    val drill = reader.spendingDrill(range, path, usd)
                    assertEquals("$groupBy ${row.label}", row.amountMinor, drill.totalMinor)
                    val breakdown = drill.breakdown
                    if (breakdown == null) {
                        assertEquals(row.amountMinor, drill.transactions.sumOf { it.amountMinor })
                        assertEquals(drill.transactions.map { it.date }.sortedDescending(), drill.transactions.map { it.date })
                        continue
                    }
                    assertEquals(SpendingDrillLevels.next(path), breakdown.groupBy)
                    assertTrue("a single row lists its transactions instead", breakdown.rows.size > 1)
                    assertEquals(row.amountMinor, breakdown.totalMinor)
                    for (sub in breakdown.rows) {
                        val deeper = reader.spendingDrill(range, path + SpendingFilter(breakdown.groupBy, sub.id, sub.label), usd)
                        assertNull("two levels down lists transactions", deeper.breakdown)
                        assertEquals(sub.amountMinor, deeper.transactions.sumOf { it.amountMinor })
                    }
                }
            }
        }
    }

    @Test
    fun eachCashFlowMonthSplitsIntoTheRowsItDrillsTo() {
        val usd = Currencies.byCode("USD")!!
        read { reader ->
            for (month in reader.cashFlow(range).months) {
                val page = reader.cashFlowMonth(month.month, usd)
                assertEquals("${month.month}", month.incomeMinor to month.expenseMinor, page.incomeMinor to page.expenseMinor)
                assertEquals(SpendingGroupBy.CATEGORY, page.income.groupBy)
                assertEquals(SpendingGroupBy.GROUP, page.expenses.groupBy)
                val one = ReportRange(month.month, month.month)
                for ((income, rows) in listOf(true to page.income.rows, false to page.expenses.rows)) {
                    val groupBy = if (income) SpendingGroupBy.CATEGORY else SpendingGroupBy.GROUP
                    for (row in rows) {
                        val drill =
                            reader.spendingDrill(
                                one,
                                listOf(SpendingFilter(groupBy, row.id, row.label)),
                                usd,
                                income,
                                throughToday = true,
                            )
                        assertEquals("${month.month} ${row.label}", row.amountMinor, drill.totalMinor)
                        assertTrue(if (income) row.amountMinor > 0 else row.amountMinor < 0)
                    }
                }
            }
        }
    }

    @Test
    fun drillLevels() {
        fun at(
            groupBy: SpendingGroupBy,
            id: String? = "x",
        ) = listOf(SpendingFilter(groupBy, id, "x"))
        assertEquals(SpendingGroupBy.CATEGORY, SpendingDrillLevels.next(at(SpendingGroupBy.GROUP)))
        assertEquals(SpendingGroupBy.PAYEE, SpendingDrillLevels.next(at(SpendingGroupBy.CATEGORY)))
        assertEquals(SpendingGroupBy.CATEGORY, SpendingDrillLevels.next(at(SpendingGroupBy.PAYEE)))
        assertNull("uncategorized has no categories under it", SpendingDrillLevels.next(at(SpendingGroupBy.GROUP, null)))
        assertNull(SpendingDrillLevels.next(at(SpendingGroupBy.GROUP) + at(SpendingGroupBy.CATEGORY)))
    }

    @Test
    fun anEmptyRangeIsZeros() {
        val empty = ParityFixture.load("reports", "empty-range")
        val range = ReportRange(YearMonth.of(2025, 1), YearMonth.of(2025, 3))
        empty.open().use { opened ->
            opened.db { session ->
                val reader = ReportsReader(session, empty.today)
                assertTrue(empty.value("spendingRows").jsonArray.isEmpty())
                assertTrue(reader.spending(range, SpendingGroupBy.CATEGORY).rows.isEmpty())
                val cash = reader.cashFlow(range)
                assertEquals(3, cash.months.size)
                assertEquals(0L to 0L, cash.incomeMinor to cash.expenseMinor)
                assertTrue(reader.netWorth(range, true).months.all { it.onBudgetMinor == 0L && it.offBudgetMinor == 0L })
            }
        }
    }

    @Test
    fun theNetWorthGraphMatchesEachMonthEnd() {
        val usd = Currencies.byCode("USD")!!
        val (history, months) = read { it.netWorthHistory(NetWorthSpan.THREE_MONTHS, usd) to it.netWorth(range, true).months }
        assertEquals(fixture.today.minusMonths(3), history.first?.date)
        assertEquals("the line ends today", fixture.today, history.last?.date)
        assertTrue("three months draws a point a day", history.points.zipWithNext().all { (a, b) -> a.date.plusDays(1) == b.date })
        for (month in months.filter { it.month >= YearMonth.of(2026, 7) }) {
            val point = history.points.single { it.date == month.month.atEndOfMonth() }
            assertEquals("${month.month}", month.onBudgetMinor to month.offBudgetMinor, point.onBudgetMinor to point.offBudgetMinor)
        }
    }

    @Test
    fun longNetWorthSpansAreThinnedAndAllStartsAtTheFirstTransaction() {
        val usd = Currencies.byCode("USD")!!
        val (year, all) = read { it.netWorthHistory(NetWorthSpan.ONE_YEAR, usd) to it.netWorthHistory(NetWorthSpan.ALL, usd) }
        assertTrue(year.points.size <= 120)
        assertEquals(fixture.today.minusYears(1), year.first?.date)
        assertEquals(fixture.today, year.last?.date)
        assertTrue(all.first!!.date > LocalDate.of(2000, 1, 1))
        assertTrue("nothing before the first transaction", all.first!!.total(true) != 0L)
        assertEquals(year.last, all.last)
    }

    @Test
    fun cashFlowStopsAtToday() {
        val report = read { it.cashFlow(ReportRange(YearMonth.of(2026, 10), YearMonth.of(2026, 12))) }
        assertEquals(0L, report.incomeMinor)
    }

    private fun <T> read(block: (ReportsReader) -> T): T =
        fixture.open().use { opened ->
            opened.db {
                block(ReportsReader(it, fixture.today))
            }
        }

    @Suppress("unused")
    private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.contentOrNull
}
