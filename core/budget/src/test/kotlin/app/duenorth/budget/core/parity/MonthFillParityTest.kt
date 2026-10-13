package app.duenorth.budget.core.parity

import app.duenorth.budget.core.MonthFillBook
import app.duenorth.budget.core.MonthFillCopy
import app.duenorth.budget.core.MonthFillKind
import app.duenorth.budget.core.MonthFillResult
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

/** Spec 010 T017: copy last month, three-month average and set to zero against desktop Actual 26.8.1. */
class MonthFillParityTest {
    @Test
    fun copyLastMonthSkipsHiddenAndIncome() = fill("copy-previous-month", MonthFillKind.COPY_LAST, "zero_budgets")

    @Test
    fun copyLastMonthOnTrackingIncludesIncome() = fill("tracking-copy-previous-month", MonthFillKind.COPY_LAST, "reflect_budgets")

    @Test
    fun threeMonthAverageUsesSpendingSinceFirstActivity() = fill("set-3month-avg", MonthFillKind.AVERAGE, "zero_budgets")

    @Test
    fun setToZeroIncludesHiddenAndSkipsIncome() = fill("set-zero", MonthFillKind.ZERO, "zero_budgets")

    @Test
    fun setToZeroOnTracking() = fill("tracking-set-zero", MonthFillKind.ZERO, "reflect_budgets")

    @Test
    fun nothingToCopyLeavesTheMonthAlone() {
        val fixture = ParityFixture.load("month", "copy-nothing")
        fixture.open().use { opened ->
            opened.db { session ->
                val result = MonthFillBook(session, fixture.today).fill(MonthFillKind.COPY_LAST, YearMonth.of(2026, 7), confirmed = true)
                assertEquals(MonthFillResult.NothingToCopy, result)
                fixture.assertTable(session, "zero_budgets")
                assertEquals(0, session.query("SELECT timestamp FROM messages").size)
            }
        }
    }

    @Test
    fun fillsAskBeforeReplacingBudgetedAmounts() {
        val fixture = ParityFixture.load("month", "breakdown")
        fixture.open().use { opened ->
            opened.db { session ->
                val book = MonthFillBook(session, fixture.today)
                val october = YearMonth.of(2026, 10)
                assertEquals(
                    MonthFillResult.NeedsConfirm(MonthFillCopy.REPLACES),
                    book.fill(MonthFillKind.COPY_LAST, october, confirmed = false),
                )
                assertEquals(
                    MonthFillResult.NeedsConfirm(MonthFillCopy.REPLACES),
                    book.fill(MonthFillKind.AVERAGE, october, confirmed = false),
                )
                assertEquals(
                    MonthFillResult.NeedsConfirm(MonthFillCopy.ZERO_CONFIRM),
                    book.fill(MonthFillKind.ZERO, october, confirmed = false),
                )
                // November has nothing budgeted, so copying into it needs no confirm.
                assertEquals(MonthFillResult.Filled, book.fill(MonthFillKind.COPY_LAST, october.plusMonths(1), confirmed = false))
                fixture.assertTable(session, "zero_budgets", where = { it["month"] != 202611L })
            }
        }
    }

    @Test
    fun transferToToBudgetMatchesDesktopAndNotesTheMonth() {
        val fixture = ParityFixture.load("month", "transfer-to-budget")
        fixture.open().use { opened ->
            opened.db { session ->
                val result =
                    MonthFillBook(session, fixture.today).transferToBudget(
                        YearMonth.of(2026, 10),
                        fixture.id("categories", "Food"),
                        4_000L,
                    )
                assertEquals(MonthFillResult.Filled, result)
                fixture.assertTable(session, "zero_budgets")
                fixture.assertTable(session, "notes")
            }
            assertToBudget(fixture, opened)
        }
    }

    @Test
    fun breakdownMatchesDesktopsSheet() {
        val fixture = ParityFixture.load("month", "breakdown")
        fixture.open().use { opened ->
            for (month in listOf("2026-09", "2026-10", "2026-11")) {
                val parts = opened.library.readBreakdown(opened.budgetId, YearMonth.parse(month))!!
                assertEquals("$month income", fixture.long(month, "totalIncome"), parts.incomeMinor)
                assertEquals("$month carried", fixture.long(month, "fromLastMonth"), parts.carriedMinor)
                assertEquals("$month overspent", fixture.long(month, "lastMonthOverspent"), parts.overspentMinor)
                assertEquals("$month budgeted", fixture.long(month, "totalBudgeted"), parts.budgetedMinor)
                assertEquals("$month held", -fixture.long(month, "bufferedSelected"), parts.heldMinor)
                assertEquals("$month total", fixture.long(month, "toBudget"), parts.totalMinor)
            }
        }
    }

    private fun fill(
        scenario: String,
        kind: MonthFillKind,
        table: String,
    ) {
        val fixture = ParityFixture.load("month", scenario)
        fixture.open().use { opened ->
            opened.db { session ->
                val result = MonthFillBook(session, fixture.today).fill(kind, YearMonth.of(2026, 10), confirmed = true)
                assertEquals(MonthFillResult.Filled, result)
                fixture.assertTable(session, table)
            }
            // Tracking budgets have no to-budget.
            if (table == "zero_budgets") assertToBudget(fixture, opened)
        }
    }

    private fun assertToBudget(
        fixture: ParityFixture,
        opened: ParityFixture.Opened,
    ) {
        for (month in listOf("2026-09", "2026-10", "2026-11")) {
            val shell = opened.library.readShell(opened.budgetId, YearMonth.parse(month))!!
            assertEquals("${fixture.scenario} $month", fixture.long(month, "toBudget"), shell.headerMinor)
        }
    }
}
