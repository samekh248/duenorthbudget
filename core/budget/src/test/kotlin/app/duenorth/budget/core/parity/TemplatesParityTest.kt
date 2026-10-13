package app.duenorth.budget.core.parity

import app.duenorth.budget.core.CategoryNotes
import app.duenorth.budget.core.SqlSession
import app.duenorth.budget.core.TemplateEngine
import app.duenorth.budget.core.TemplateError
import app.duenorth.budget.core.TemplateResult
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** Spec 010 T057: goal templates against desktop Actual 26.8.1 (`parity/templates/`). */
class TemplatesParityTest {
    private val october = YearMonth.of(2026, 10)
    private val october202610: (Map<String, Any?>) -> Boolean = { it["month"] == 202610L }

    @Test
    fun applyFillsZeroCategoriesLikeDesktop() {
        val fixture = ParityFixture.load("templates", "apply")
        on(fixture) { session ->
            val result = TemplateEngine(session, fixture.today).run(october, overwrite = false)
            assertTrue("$result", result is TemplateResult.Applied)
            fixture.assertTable(session, "zero_budgets", where = october202610)
            fixture.assertTable(session, "categories")
            fixture.assertTable(session, "notes")
        }
    }

    @Test
    fun overwriteNeedsConfirmThenRefillsEveryTemplatedCategory() {
        val fixture = ParityFixture.load("templates", "overwrite")
        on(fixture) { session ->
            val engine = TemplateEngine(session, fixture.today)
            val before = session.query("SELECT * FROM zero_budgets").map { it.values }
            assertTrue(engine.run(october, overwrite = true) is TemplateResult.NeedsConfirm)
            assertEquals(before, session.query("SELECT * FROM zero_budgets").map { it.values })
            assertTrue(engine.run(october, overwrite = true, confirmed = true) is TemplateResult.Applied)
            fixture.assertTable(session, "zero_budgets", where = october202610)
            fixture.assertTable(session, "categories")
        }
    }

    @Test
    fun checkPassesAndWritesNothing() {
        val fixture = ParityFixture.load("templates", "check-valid")
        on(fixture) { session ->
            assertEquals(TemplateResult.Valid, TemplateEngine(session, fixture.today).check())
            fixture.assertTable(session, "zero_budgets")
            fixture.assertTable(session, "notes")
        }
    }

    @Test
    fun checkNamesTheBadLineAndApplyRefusesToWrite() {
        val fixture = ParityFixture.load("templates", "check-invalid")
        on(fixture) { session ->
            CategoryNotes.write(session, fixture.id("categories", "Fixed"), "#template fifty dollars")
            val engine = TemplateEngine(session, fixture.today)
            val result = engine.check() as TemplateResult.Invalid
            assertEquals(fixture.value("check", "pre").jsonPrimitive.content, result.errors.joinToString("\n"))
            assertEquals(listOf(TemplateError("Fixed", "#template fifty dollars")), result.errors)
            assertTrue(engine.run(october, overwrite = false) is TemplateResult.Invalid)
            fixture.assertTable(session, "zero_budgets")
            fixture.assertTable(session, "notes")
        }
    }

    private fun on(
        fixture: ParityFixture,
        block: (SqlSession) -> Unit,
    ) {
        fixture.open().use { opened -> opened.db(block) }
    }
}
