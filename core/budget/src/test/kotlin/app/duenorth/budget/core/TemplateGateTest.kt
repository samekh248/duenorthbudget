package app.duenorth.budget.core

import app.duenorth.budget.core.parity.ParityFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** Spec 010 T058: templates change nothing unless both flags are on, and a bad line stops a run. */
class TemplateGateTest {
    private val fixture = ParityFixture.load("templates", "apply")
    private val october = YearMonth.of(2026, 10)

    @Test
    fun experimentalOffMeansNoBudgetChange() {
        fixture.open().use { opened ->
            // The base file has goal templates on but experimental features off.
            val before = budgets(opened)
            val result = opened.library.applyTemplates(opened.budgetId, october, overwrite = false)
            assertEquals(TemplateResult.Rejected(TemplateCopy.OFF), result)
            assertEquals(TemplateResult.Rejected(TemplateCopy.OFF), opened.library.checkTemplates(opened.budgetId))
            assertEquals(before, budgets(opened))
        }
    }

    @Test
    fun templatesFlagOffMeansNoBudgetChange() {
        fixture.open().use { opened ->
            opened.library.setPreference(opened.budgetId, "flags.experimental", "true")
            opened.library.setPreference(opened.budgetId, "flags.goalTemplatesEnabled", "false")
            val before = budgets(opened)
            assertEquals(
                TemplateResult.Rejected(TemplateCopy.OFF),
                opened.library.applyTemplates(opened.budgetId, october, overwrite = true, confirmed = true),
            )
            assertEquals(before, budgets(opened))
        }
    }

    @Test
    fun bothFlagsOnAppliesAsOneUndoStep() {
        fixture.open().use { opened ->
            opened.library.setPreference(opened.budgetId, "flags.experimental", "true")
            assertTrue(opened.library.readFormatPrefs(opened.budgetId).templatesOn)
            val before = budgets(opened)
            val result = opened.library.applyTemplates(opened.budgetId, october, overwrite = false)
            assertTrue("$result", result is TemplateResult.Applied)
            assertTrue(budgets(opened) != before)
            assertEquals(UndoCopy.TEMPLATES, opened.library.journal.newestLabel(opened.budgetId))
        }
    }

    @Test
    fun invalidLineWritesNothing() {
        fixture.open().use { opened ->
            opened.library.setPreference(opened.budgetId, "flags.experimental", "true")
            val fixed = fixture.id("categories", "Fixed")
            assertTrue(opened.library.saveCategoryNote(opened.budgetId, fixed, "#template fifty dollars"))
            assertEquals("#template fifty dollars", opened.library.readCategoryNote(opened.budgetId, fixed))
            val before = budgets(opened)
            val goals = definitions(opened)
            val check = opened.library.checkTemplates(opened.budgetId) as TemplateResult.Invalid
            assertEquals("Fixed: #template fifty dollars", check.errors.single().toString())
            assertTrue(opened.library.applyTemplates(opened.budgetId, october, overwrite = false) is TemplateResult.Invalid)
            assertEquals(before, budgets(opened))
            assertEquals(goals, definitions(opened))
        }
    }

    private fun budgets(opened: ParityFixture.Opened) =
        opened.db { session -> session.query("SELECT * FROM zero_budgets ORDER BY id").map { it.values } }

    private fun definitions(opened: ParityFixture.Opened) =
        opened.db { session -> session.query("SELECT id, goal_def, template_settings FROM categories ORDER BY id").map { it.values } }
}
