package app.duenorth.budget.core.parity

import app.duenorth.budget.core.ReportRange
import app.duenorth.budget.core.ReportsCopy
import app.duenorth.budget.core.ReportsReader
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
import org.junit.Assert.assertTrue
import org.junit.Test
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
