package app.duenorth.budget.core

import app.duenorth.budget.core.parity.ParityFixture
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneOffset

/** Spec 010 T019: transfer back to to-budget, and month actions as undo steps. */
class MonthFillBookTest {
    private val october = YearMonth.of(2026, 10)

    @Test
    fun transferLowersBudgetedAndRaisesToBudget() {
        val fixture = ParityFixture.load("month", "breakdown")
        val food = fixture.id("categories", "Food")
        fixture.open().use { opened ->
            val before = opened.library.readShell(opened.budgetId, october)!!.headerMinor
            opened.db { session ->
                assertEquals(MonthFillResult.Filled, MonthFillBook(session, fixture.today).transferToBudget(october, food, 4_000L))
                assertEquals(
                    46_000L,
                    session
                        .query("SELECT amount FROM zero_budgets WHERE month = 202610 AND category = ?", listOf(food))
                        .single()
                        .long("amount"),
                )
            }
            assertEquals(before + 4_000L, opened.library.readShell(opened.budgetId, october)!!.headerMinor)
        }
    }

    @Test
    fun transferRefusesMoreThanAvailable() {
        val fixture = ParityFixture.load("month", "breakdown")
        val food = fixture.id("categories", "Food")
        fixture.open().use { opened ->
            opened.db { session ->
                val book = MonthFillBook(session, fixture.today)
                val available =
                    ShellReader
                        .loadDetailed(session, october)
                        .categories
                        .getValue(food)
                        .availableMinor
                assertEquals(MonthFillResult.Rejected(EnvelopeCopy.NOT_ENOUGH), book.transferToBudget(october, food, available + 1))
                assertEquals(MonthFillResult.Rejected(EnvelopeCopy.ENTER_AMOUNT), book.transferToBudget(october, food, 0L))
                fixture.assertTable(session, "zero_budgets")
                assertEquals(0, session.query("SELECT timestamp FROM messages").size)
            }
        }
    }

    @Test
    fun transferAndAutoHoldAreAbsentOnTracking() {
        val fixture = ParityFixture.load("month", "tracking-set-zero")
        fixture.open().use { opened ->
            opened.db { session ->
                val book = MonthFillBook(session, fixture.today)
                val food = fixture.id("categories", "Food")
                val salary = fixture.id("categories", "Salary")
                assertEquals(MonthFillResult.Rejected(MonthFillCopy.ENVELOPE_ONLY), book.transferToBudget(october, food, 100L))
                assertEquals(MonthFillResult.Rejected(MonthFillCopy.ENVELOPE_ONLY), book.setAutoHold(october, salary, AutoHoldChoice.ON))
            }
            assertEquals(null, opened.library.readBreakdown(opened.budgetId, october))
        }
    }

    @Test
    fun eachMonthActionIsOneUndoStep() {
        val fixture = ParityFixture.load("month", "breakdown")
        fixture.open().use { opened ->
            val sync =
                SyncCoordinator(opened.library, HttpActualTransport(), MemorySecretStore(), BudgetClock { fixture.today }, ZoneOffset.UTC)
            val before = opened.library.readShell(opened.budgetId, october)!!.headerMinor
            assertEquals(MonthFillResult.Filled, sync.fillMonth(opened.budgetId, october, MonthFillKind.ZERO, confirmed = true))
            // A refused action leaves no step.
            assertEquals(
                MonthFillResult.Rejected(EnvelopeCopy.ENTER_AMOUNT),
                sync.transferToBudget(opened.budgetId, fixture.id("categories", "Pets"), october, "0"),
            )
            assertEquals(1, opened.library.journal.size(opened.budgetId))
            assertEquals(MonthFillCopy.ZERO, opened.library.undoLabel(opened.budgetId))
            assertEquals(UndoResult.Undone(MonthFillCopy.ZERO), sync.undo(opened.budgetId))
            assertEquals(before, opened.library.readShell(opened.budgetId, october)!!.headerMinor)
            opened.db { session ->
                val food = fixture.id("categories", "Food")
                val amounts =
                    session
                        .query("SELECT category, amount FROM zero_budgets WHERE month = 202610")
                        .associate { it.str("category") to it.long("amount") }
                assertEquals(50_000L, amounts[food])
                assertEquals(setOf(50_000L, 0L), amounts.values.toSet())
            }
        }
    }
}
