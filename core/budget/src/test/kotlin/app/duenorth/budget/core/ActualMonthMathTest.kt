package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class ActualMonthMathTest {
    private val incomeGroup = ActualMonthMath.GroupFact("g-inc", isIncome = true, sortOrder = 0.0)
    private val living = ActualMonthMath.GroupFact("g-liv", isIncome = false, sortOrder = 1.0)
    private val salary = ActualMonthMath.CategoryFact("c-sal", "g-inc", isIncome = true)
    private val food = ActualMonthMath.CategoryFact("c-food", "g-liv", isIncome = false)
    private val rent = ActualMonthMath.CategoryFact("c-rent", "g-liv", isIncome = false)

    @Test
    fun emptyMonthIsZero() {
        val figures =
            ActualMonthMath.project(
                mode = BudgetMode.ENVELOPE,
                month = YearMonth.of(2026, 10),
                groups = emptyList(),
                categories = emptyList(),
                spentByCategoryMonth = emptyMap(),
                assignments = emptyList(),
                buffers = emptyMap(),
            )
        assertEquals(ShellCopy.TO_BUDGET, figures.headerLabel)
        assertEquals(0L, figures.headerMinor)
        assertTrue(figures.groupAvailable.isEmpty())
    }

    @Test
    fun octoberToBudgetCarriesLeftoverAndUncoveredOverspend() {
        val figures = october()
        assertEquals(180_000L, figures.headerMinor)
        assertEquals(50_000L, figures.groupAvailable["g-liv"])
    }

    @Test
    fun holdForNextMonthLowersThisMonthOnly() {
        val september =
            ActualMonthMath.project(
                mode = BudgetMode.ENVELOPE,
                month = YearMonth.of(2026, 9),
                groups = listOf(incomeGroup, living),
                categories = listOf(salary, food, rent),
                spentByCategoryMonth = septemberSpent(),
                assignments = septemberAssignments(),
                buffers = mapOf(202609 to 50_000L),
            )
        assertEquals(150_000L, september.headerMinor)
        assertEquals(180_000L, october(buffers = mapOf(202609 to 50_000L)).headerMinor)
    }

    @Test
    fun carryoverKeepsOverspendInTheCategory() {
        val assignments =
            septemberAssignments().map {
                if (it.categoryId == "c-rent") it.copy(carryover = true) else it
            }
        val figures = october(assignments = assignments)
        assertEquals(200_000L, figures.headerMinor)
        assertEquals(30_000L, figures.groupAvailable["g-liv"])
    }

    @Test
    fun trackingCarryoverRollsThePreviousLeftover() {
        val figures =
            ActualMonthMath.project(
                mode = BudgetMode.TRACKING,
                month = YearMonth.of(2026, 10),
                groups = listOf(incomeGroup, living),
                categories = listOf(salary, food, rent),
                spentByCategoryMonth = mapOf((202609 to "c-food") to -4_000L),
                assignments = listOf(ActualMonthMath.AssignmentFact(202609, "c-food", 10_000L, true)),
                buffers = emptyMap(),
            )
        assertEquals(6_000L, figures.categoryAvailable["c-food"])
        assertEquals(6_000L, figures.headerMinor)
    }

    @Test
    fun trackingDoesNotRollForward() {
        val figures =
            ActualMonthMath.project(
                mode = BudgetMode.TRACKING,
                month = YearMonth.of(2026, 10),
                groups = listOf(incomeGroup, living),
                categories = listOf(salary, food, rent),
                spentByCategoryMonth = mapOf((202610 to "c-food") to -4_000L),
                assignments =
                    listOf(
                        ActualMonthMath.AssignmentFact(202610, "c-food", 10_000L, false),
                    ),
                buffers = emptyMap(),
            )
        assertEquals(ShellCopy.BALANCE, figures.headerLabel)
        assertEquals(6_000L, figures.headerMinor)
        assertEquals(6_000L, figures.groupAvailable["g-liv"])
    }

    @Test
    fun threeHundredGroupsStayUnderTheBudget() {
        val groups = (0 until 300).map { ActualMonthMath.GroupFact("g$it", false, it.toDouble()) }
        val categories = groups.map { ActualMonthMath.CategoryFact("c${it.id}", it.id, false) }
        val assignments =
            categories.map {
                ActualMonthMath.AssignmentFact(202610, it.id, 100L, false)
            }
        val started = System.nanoTime()
        val figures =
            ActualMonthMath.project(
                mode = BudgetMode.ENVELOPE,
                month = YearMonth.of(2026, 10),
                groups = groups,
                categories = categories,
                spentByCategoryMonth = emptyMap(),
                assignments = assignments,
                buffers = emptyMap(),
            )
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertEquals(300, figures.groupAvailable.size)
        assertEquals(-30_000L, figures.headerMinor)
        assertTrue("300 groups took ${elapsedMs}ms", elapsedMs < 100)
    }

    private fun october(
        assignments: List<ActualMonthMath.AssignmentFact> = septemberAssignments(),
        buffers: Map<Int, Long> = emptyMap(),
    ) = ActualMonthMath.project(
        mode = BudgetMode.ENVELOPE,
        month = YearMonth.of(2026, 10),
        groups = listOf(incomeGroup, living),
        categories = listOf(salary, food, rent),
        spentByCategoryMonth = septemberSpent(),
        assignments = assignments,
        buffers = buffers,
    )

    private fun septemberSpent() =
        mapOf(
            (202609 to "c-sal") to 500_000L,
            (202609 to "c-food") to -150_000L,
            (202609 to "c-rent") to -120_000L,
        )

    private fun septemberAssignments() =
        listOf(
            ActualMonthMath.AssignmentFact(202609, "c-food", 200_000L, false),
            ActualMonthMath.AssignmentFact(202609, "c-rent", 100_000L, false),
        )
}
