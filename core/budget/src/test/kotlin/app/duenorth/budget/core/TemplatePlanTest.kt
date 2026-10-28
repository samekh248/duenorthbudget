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
    fun budgetRowsMeasureTemplatesBeforeTheyAreApplied() {
        fixture.open().use { opened ->
            fun goals() =
                opened.library
                    .readShell(opened.budgetId, october)!!
                    .groups
                    .flatMap { it.categories }
                    .associate { it.id to (it.goalMinor to it.longGoal) }
            assertTrue(goals().values.all { it.first == null })
            opened.library.setPreference(opened.budgetId, "flags.experimental", "true")
            val live = goals().filterValues { it.first != null }
            assertTrue(live.isNotEmpty())
            opened.library.applyTemplates(opened.budgetId, october, overwrite = true, confirmed = true)
            val stored =
                opened.db { session ->
                    session
                        .query("SELECT category, goal, long_goal FROM zero_budgets WHERE month = ?", listOf(october.toActualMonth()))
                        .filter { it.values["goal"] != null }
                        .associate { it.str("category").orEmpty() to (it.long("goal") to it.bool("long_goal")) }
                }
            live.forEach { (category, goal) -> assertEquals(category, stored[category], goal.first to goal.second) }
        }
    }

    @Test
    fun aMissedOneOffScheduleDoesNotTakeTheBudgetDown() {
        fixture.open().use { opened ->
            opened.library.setPreference(opened.budgetId, "flags.experimental", "true")
            val rent = fixture.id("categories", "Rent")
            // Rent's schedule turned into a one-off bill due last month and not yet paid, with its 1,500 saved in
            // September and carried into October. Counting months to a date already past divided 0 by 0.
            opened.db { session ->
                val rule = session.query("SELECT rule FROM schedules WHERE name = 'Rent'").single().str("rule")
                val conditions = session.query("SELECT conditions FROM rules WHERE id = ?", listOf(rule)).single().str("conditions")!!
                val oneOff = conditions.replace(Regex(""""value":\{"start".*?\}"""), "\"value\":\"2026-09-28\"")
                check(oneOff != conditions)
                session.exec("UPDATE rules SET conditions = ? WHERE id = ?", listOf(oneOff, rule))
                session.exec(
                    "INSERT INTO zero_budgets (id, month, category, amount) VALUES (?, ?, ?, ?)",
                    listOf("202609-$rent", 202609, rent, 150_000L),
                )
            }
            val row =
                opened.library
                    .readShell(opened.budgetId, october)!!
                    .groups
                    .flatMap { it.categories }
                    .single { it.id == rent }
            // Due now, and already covered by what carried over.
            assertEquals(0L, row.goalMinor)
            assertTrue(
                opened.library
                    .templatePlan(opened.budgetId, october)!!
                    .lanes
                    .isNotEmpty(),
            )
        }
    }

    @Test
    fun remainderLinesWithNoWeightShareNothing() {
        fixture.open().use { opened ->
            opened.library.setPreference(opened.budgetId, "flags.experimental", "true")
            opened.library.saveCategoryNote(opened.budgetId, fixture.id("categories", "Remainder A"), "#template remainder 0")
            opened.library.saveCategoryNote(opened.budgetId, fixture.id("categories", "Remainder B"), "#template remainder 0")
            val remainder =
                opened.library
                    .templatePlan(opened.budgetId, october)!!
                    .lanes
                    .single { it.kind == TemplateLane.Kind.REMAINDER }
            assertTrue(remainder.chips.all { it.given == 0L })
            val result = opened.library.applyTemplates(opened.budgetId, october, overwrite = true, confirmed = true)
            assertTrue("$result", result is TemplateResult.Applied)
        }
    }

    @Test
    fun noPlanWhileTemplatesAreOff() {
        fixture.open().use { opened -> assertNull(opened.library.templatePlan(opened.budgetId, october)) }
    }
}
