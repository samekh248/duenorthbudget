package app.duenorth.budget.core

import app.duenorth.budget.core.parity.ParityFixture
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** The templates page's apply: tops up every short target from what is to budget, and never lowers one. */
class TemplateFillTest {
    private val fixture = ParityFixture.load("schedule-templates", "overwrite")
    private val today = LocalDate.of(2026, 11, 18)
    private val november = YearMonth.of(2026, 11)
    private val december = YearMonth.of(2026, 12)

    /** Water's schedule made $100 due on the 11th each month, November's paid, with $10 of its budget left over. */
    private fun SqlSession.waterDueTheEleventh(): String {
        val rule = query("SELECT rule FROM schedules WHERE id = ?", listOf(fixture.id("schedules", "Water"))).single().str("rule")
        val date =
            """{"start":"2026-01-11","frequency":"monthly","interval":1,"endMode":"never",""" +
                """"patterns":[],"skipWeekend":false,"weekendSolveMode":"after"}"""
        val conditions =
            """[{"op":"is","field":"acct","value":"${fixture.id("accounts", "Checking")}"},""" +
                """{"op":"is","field":"amount","value":-10000},{"op":"isapprox","field":"date","value":$date}]"""
        exec("UPDATE rules SET conditions = ? WHERE id = ?", listOf(conditions, rule))
        val water = fixture.id("categories", "Water")
        exec("INSERT INTO zero_budgets (id, month, category, amount) VALUES (?, ?, ?, ?)", listOf("202611-$water", 202611, water, 11_000L))
        exec(
            "INSERT INTO transactions (id, isParent, isChild, acct, category, amount, date, tombstone, sort_order) " +
                "VALUES ('tx-water', 0, 0, ?, ?, -10000, 20261111, 0, 1)",
            listOf(fixture.id("accounts", "Checking"), water),
        )
        return water
    }

    private fun SqlSession.budget(
        month: YearMonth,
        category: String,
    ): Long = ShellReader.loadDetailed(this, month).categories[category]?.budgetedMinor ?: 0L

    @Test
    fun aTargetWithSomeMoneyAlreadyIsToppedUpInAFutureMonth() {
        fixture.open().use { opened ->
            opened.db { session ->
                val water = session.waterDueTheEleventh()
                // $10 already budgeted in December: Actual's apply skips the category, so nothing was budgeted.
                session.exec(
                    "INSERT INTO zero_budgets (id, month, category, amount) VALUES (?, ?, ?, ?)",
                    listOf("202612-$water", 202612, water, 1_000L),
                )
                val engine = TemplateEngine(session, today)
                engine.run(december, overwrite = false)
                assertEquals(1_000L, session.budget(december, water))

                val result = engine.fill(december)
                assertEquals(true, result is TemplateResult.Applied)
                // $100 due, $10 carried in from November: the template asks $90 of December.
                assertEquals(9_000L, session.budget(december, water))
            }
        }
    }

    @Test
    fun nothingIsLoweredAndAMetTargetSaysSo() {
        fixture.open().use { opened ->
            opened.db { session ->
                val water = session.waterDueTheEleventh()
                val engine = TemplateEngine(session, today)
                engine.run(december, overwrite = true, confirmed = true)
                val rent = fixture.id("categories", "Rent")
                // Rent given more than its template asks keeps it all.
                val over = session.budget(december, rent) + 5_000L
                session.exec("UPDATE zero_budgets SET amount = ? WHERE month = 202612 AND category = ?", listOf(over, rent))
                assertEquals(TemplateResult.Rejected(TemplateCopy.ALL_MET), engine.fill(december))
                assertEquals(over, session.budget(december, rent))
                assertEquals(9_000L, session.budget(december, water))
                assertEquals(11_000L, session.budget(november, water))
            }
        }
    }

    @Test
    fun withNothingToBudgetItSaysSo() {
        fixture.open().use { opened ->
            opened.db { session ->
                val savings = fixture.id("categories", "Savings")
                val header = ShellReader.loadDetailed(session, december).figures.headerMinor
                session.exec(
                    "INSERT INTO zero_budgets (id, month, category, amount) VALUES (?, ?, ?, ?)",
                    listOf("202612-$savings", 202612, savings, header),
                )
                assertEquals(TemplateResult.Rejected(TemplateCopy.NOTHING_TO_BUDGET), TemplateEngine(session, today).fill(december))
            }
        }
    }
}
