package app.duenorth.budget.core.parity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ParityFixtureTest {
    @Test
    fun everyScenarioLoadsAndItsBaseOpensOnThePhone() {
        val areas = ParityFixture.areas()
        assertEquals(
            listOf("accounts", "automations", "cleanup", "month", "reports", "schedule-templates", "schedules-rules", "templates", "undo"),
            areas,
        )
        for (area in areas) {
            val scenarios = ParityFixture.scenarios(area)
            assertTrue("$area has no scenarios", scenarios.isNotEmpty())
            for (scenario in scenarios) {
                val fixture = ParityFixture.load(area, scenario)
                assertEquals(LocalDate.of(2026, 10, 7), fixture.today)
                assertEquals("26.8.1", fixture.actualVersion)
                fixture.open().use { opened ->
                    assertNotNull("$area/$scenario shell", opened.library.readShell(opened.budgetId))
                }
            }
        }
    }

    @Test
    fun unchangedBudgetMatchesItsOwnFixture() {
        val fixture = ParityFixture.load("month", "breakdown")
        fixture.open().use { opened ->
            opened.db { session ->
                fixture.assertTable(session, "zero_budgets")
                fixture.assertTable(session, "zero_budget_months")
            }
        }
    }

    @Test
    fun aChangedRowIsReportedWithItsKeyAndColumn() {
        val fixture = ParityFixture.load("month", "breakdown")
        val food = fixture.id("categories", "Food")
        fixture.open().use { opened ->
            opened.db { session ->
                session.exec(
                    "UPDATE zero_budgets SET amount = 1 WHERE month = 202610 AND category = ?",
                    listOf(food),
                )
                try {
                    fixture.assertTable(session, "zero_budgets", where = { it["month"] == 202610L })
                    fail("expected a difference")
                } catch (error: AssertionError) {
                    val message = error.message.orEmpty()
                    assertTrue(message, message.contains("column amount: expected <50000> but was <1>"))
                }
            }
        }
    }

    @Test
    fun phoneToBudgetMatchesDesktopForTheFixtureMonth() {
        val fixture = ParityFixture.load("month", "breakdown")
        fixture.open().use { opened ->
            for (month in listOf("2026-09", "2026-10", "2026-11")) {
                val shell = opened.library.readShell(opened.budgetId, YearMonth.parse(month))!!
                assertEquals("to budget $month", fixture.long(month, "toBudget"), shell.headerMinor)
            }
        }
    }
}
