package app.duenorth.budget.core

import app.duenorth.budget.core.parity.ParityFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** The templates screen's lanes give each category what an overwrite would budget it. */
class TemplatePlanTest {
    private val fixture = ParityFixture.load("templates", "apply")
    private val october = YearMonth.of(2026, 10)

    @Test
    fun lanesAddUpToWhatOverwriteBudgets() {
        fixture.open().use { opened ->
            opened.library.setPreference(opened.budgetId, "flags.experimental", "true")
            val plan = opened.library.templatePlan(opened.budgetId, october)!!
            assertTrue(plan.lanes.isNotEmpty())
            val priorities = plan.lanes.filter { it.kind == TemplateLane.Kind.PRIORITY }.map { it.priority!! }
            assertEquals(priorities.sorted(), priorities)
            val planned =
                plan.lanes
                    .flatMap { it.chips }
                    .filter { it.given != null }
                    .groupBy { it.categoryId }
                    .mapValues { (_, chips) -> chips.sumOf { it.given!! } }
            val result = opened.library.applyTemplates(opened.budgetId, october, overwrite = true, confirmed = true)
            assertTrue("$result", result is TemplateResult.Applied)
            val budgeted =
                opened.db { session ->
                    session
                        .query("SELECT category, amount FROM zero_budgets WHERE month = ?", listOf(october.toActualMonth()))
                        .associate { it.str("category").orEmpty() to it.long("amount") }
                }
            planned.forEach { (category, amount) -> assertEquals(category, budgeted[category] ?: 0L, amount) }
        }
    }

    @Test
    fun aLineThatDoesNotReadIsListedWithItsCategory() {
        fixture.open().use { opened ->
            opened.library.setPreference(opened.budgetId, "flags.experimental", "true")
            val fixed = fixture.id("categories", "Fixed")
            opened.library.saveCategoryNote(opened.budgetId, fixed, "rent\n#template fifty dollars")
            val chip =
                opened.library
                    .templatePlan(opened.budgetId, october)!!
                    .unreadable
                    .single()
            assertEquals(fixed to 0, chip.categoryId to chip.index)
            assertEquals("Fixed", chip.categoryName)
            assertNull(chip.template)
        }
    }

    @Test
    fun noPlanWhileTemplatesAreOff() {
        fixture.open().use { opened -> assertNull(opened.library.templatePlan(opened.budgetId, october)) }
    }
}
