package app.duenorth.budget.core.parity

import app.duenorth.budget.core.AutoHoldChoice
import app.duenorth.budget.core.MonthFillBook
import app.duenorth.budget.core.MonthFillCopy
import app.duenorth.budget.core.MonthFillResult
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

/** Spec 010 T018: income auto-hold against desktop Actual 26.8.1. */
class AutoHoldParityTest {
    private val october = YearMonth.of(2026, 10)

    @Test
    fun onWritesCarryoverThroughTheLastCreatedMonth() = run("auto-hold-on", listOf(october to AutoHoldChoice.ON))

    @Test
    fun offClearsEveryMonthFromHere() = run("auto-hold-off", listOf(october to AutoHoldChoice.ON, october to AutoHoldChoice.OFF))

    @Test
    fun offThisMonthClearsEveryIncomeCategoryForOneMonth() =
        run(
            "auto-hold-off-this-month",
            listOf(october to AutoHoldChoice.ON, october.plusMonths(1) to AutoHoldChoice.OFF_THIS_MONTH),
        )

    @Test
    fun autoHoldIsOnlyForIncomeCategories() {
        val fixture = ParityFixture.load("month", "breakdown")
        fixture.open().use { opened ->
            opened.db { session ->
                val result = MonthFillBook(session, fixture.today).setAutoHold(october, fixture.id("categories", "Food"), AutoHoldChoice.ON)
                assertEquals(MonthFillResult.Rejected(MonthFillCopy.INCOME_ONLY), result)
                fixture.assertTable(session, "zero_budgets")
            }
        }
    }

    @Test
    fun theShellShowsIncomeGroupsWithTheMonthFlag() {
        val fixture = ParityFixture.load("month", "auto-hold-on")
        val salary = fixture.id("categories", "Salary")
        fixture.open().use { opened ->
            fun salaryRow() =
                opened.library
                    .readShell(opened.budgetId, october)!!
                    .incomeGroups
                    .flatMap { it.categories }
                    .single { it.id == salary }
            assertEquals(false, salaryRow().autoHold)
            opened.db { session -> MonthFillBook(session, fixture.today).setAutoHold(october, salary, AutoHoldChoice.ON) }
            assertEquals(true, salaryRow().autoHold)
            val shell = opened.library.readShell(opened.budgetId, october)!!
            val income =
                opened.db { session ->
                    session
                        .query("SELECT id FROM categories WHERE is_income = 1 AND tombstone = 0 AND hidden = 0")
                        .map { it.str("id").orEmpty() }
                        .toSet()
                }
            val incomeShown = shell.incomeGroups.flatMap { it.categories }.map { it.id }
            val expenseShown = shell.groups.flatMap { it.categories }.map { it.id }
            assertEquals(income, incomeShown.toSet())
            assertEquals(emptySet<String>(), expenseShown.toSet() intersect income)
        }
    }

    private fun run(
        scenario: String,
        steps: List<Pair<YearMonth, AutoHoldChoice>>,
    ) {
        val fixture = ParityFixture.load("month", scenario)
        val salary = fixture.id("categories", "Salary")
        fixture.open().use { opened ->
            opened.db { session ->
                val book = MonthFillBook(session, fixture.today)
                steps.forEach { (month, choice) -> assertEquals(MonthFillResult.Filled, book.setAutoHold(month, salary, choice)) }
                fixture.assertTable(session, "zero_budgets")
            }
            for (month in listOf("2026-09", "2026-10", "2026-11")) {
                val parts = opened.library.readBreakdown(opened.budgetId, YearMonth.parse(month))!!
                assertEquals("$scenario $month held", -fixture.long(month, "bufferedSelected"), parts.heldMinor)
                assertEquals("$scenario $month", fixture.long(month, "toBudget"), parts.totalMinor)
            }
        }
    }
}
