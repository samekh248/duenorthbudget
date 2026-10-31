package app.duenorth.budget.core.parity

import app.duenorth.budget.core.SqlSession
import app.duenorth.budget.core.TemplateEngine
import app.duenorth.budget.core.TemplateParser
import app.duenorth.budget.core.TemplateResult
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** Schedule templates against desktop Actual 26.8.1's `runSchedule`, which 26.8 through 26.10 share (`parity/schedule-templates/`). */
class ScheduleTemplatesParityTest {
    private val september = YearMonth.of(2026, 9)
    private val october = YearMonth.of(2026, 10)

    @Test
    fun everyKindOfScheduleBudgetsLikeDesktop() {
        val fixture = ParityFixture.load("schedule-templates", "overwrite")
        on(fixture) { session ->
            val result = TemplateEngine(session, fixture.today).run(october, overwrite = true, confirmed = true)
            assertEquals(TemplateResult.Applied(fixture.long("result", "count").toInt()), result)
            fixture.assertTable(session, "zero_budgets", where = { it["month"] == 202610L })
            assertEquals(fixture.long("2026-10", "toBudget"), toBudget(session))
        }
    }

    @Test
    fun lastMonthsSavingsAndGoalCarryIntoTheNext() {
        val fixture = ParityFixture.load("schedule-templates", "two-months")
        on(fixture) { session ->
            val engine = TemplateEngine(session, fixture.today)
            assertTrue(engine.run(september, overwrite = true, confirmed = true) is TemplateResult.Applied)
            val result = engine.run(october, overwrite = true, confirmed = true)
            assertEquals(TemplateResult.Applied(fixture.long("result", "count").toInt()), result)
            fixture.assertTable(session, "zero_budgets", where = { it["month"] == 202609L || it["month"] == 202610L })
            assertEquals(fixture.long("2026-10", "toBudget"), toBudget(session))
        }
    }

    @Test
    fun eachSchedulesShareMatchesDesktopsDryRun() {
        val fixture = ParityFixture.load("schedule-templates", "dry-run")
        on(fixture) { session ->
            val engine = TemplateEngine(session, fixture.today)
            val problems = mutableListOf<String>()
            for ((name, run) in fixture.value("dryRun").jsonObject) {
                val id = fixture.id("categories", name)
                val templates =
                    TemplateParser
                        .parse(
                            app.duenorth.budget.core.CategoryNotes
                                .read(session, id),
                        ).map { it.getOrThrow() }
                val want =
                    run.jsonObject
                        .getValue("perTemplate")
                        .jsonArray
                        .map { it.jsonPrimitive.longOrNull }
                val got = engine.preview(id, october, templates)
                if (got != want) problems += "$name: desktop $want, phone $got"
            }
            assertTrue(problems.joinToString("\n"), problems.isEmpty())
        }
    }

    private fun toBudget(session: SqlSession): Long =
        app.duenorth.budget.core.ShellReader
            .loadDetailed(session, october)
            .figures.headerMinor

    private fun on(
        fixture: ParityFixture,
        block: (SqlSession) -> Unit,
    ) {
        fixture.open().use { opened -> opened.db(block) }
    }
}
