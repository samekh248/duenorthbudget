package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth

/** To budget less later months' budgeting: the lowest to-budget from the open month on. */
class FuturePlansTest {
    private val incomeGroup = ActualMonthMath.GroupFact("g-inc", isIncome = true, sortOrder = 0.0)
    private val living = ActualMonthMath.GroupFact("g-liv", isIncome = false, sortOrder = 1.0)
    private val salary = ActualMonthMath.CategoryFact("c-sal", "g-inc", isIncome = true)
    private val food = ActualMonthMath.CategoryFact("c-food", "g-liv", isIncome = false)
    private val rent = ActualMonthMath.CategoryFact("c-rent", "g-liv", isIncome = false)

    private fun assign(
        month: Int,
        category: String,
        amount: Long,
        carryover: Boolean = false,
    ) = ActualMonthMath.AssignmentFact(month, category, amount, carryover)

    private fun october(
        spent: Map<Pair<Int, String>, Long> = mapOf((202610 to "c-sal") to 100_000L),
        assignments: List<ActualMonthMath.AssignmentFact>,
        buffers: Map<Int, Long> = emptyMap(),
        mode: BudgetMode = BudgetMode.ENVELOPE,
    ) = ActualMonthMath.projectDetailed(
        mode = mode,
        month = YearMonth.of(2026, 10),
        groups = listOf(incomeGroup, living),
        categories = listOf(salary, food, rent),
        spentByCategoryMonth = spent,
        assignments = assignments,
        buffers = buffers,
    )

    @Test
    fun laterBudgetingComesOutOfTheOpenMonth() {
        val figures =
            october(
                assignments =
                    listOf(
                        assign(202610, "c-food", 50_000L),
                        assign(202611, "c-rent", 30_000L),
                        assign(202612, "c-rent", 10_000L),
                    ),
            )
        // Actual's own figure is untouched.
        assertEquals(50_000L, figures.figures.headerMinor)
        assertEquals(ActualMonthMath.FuturePlans(10_000L, 202612), figures.ahead)
        assertEquals(figures.ahead, figures.breakdown?.ahead)
        assertEquals(50_000L, figures.categories["c-food"]?.availableMinor)
    }

    @Test
    fun nothingLaterLeavesItEmpty() {
        val figures = october(assignments = listOf(assign(202610, "c-food", 50_000L)))
        assertNull(figures.ahead)
    }

    @Test
    fun laterIncomeCoversItsOwnMonth() {
        val figures =
            october(
                spent = mapOf((202610 to "c-sal") to 100_000L, (202611 to "c-sal") to 40_000L),
                assignments = listOf(assign(202610, "c-food", 50_000L), assign(202611, "c-rent", 30_000L)),
            )
        assertNull(figures.ahead)
    }

    @Test
    fun holdingForNextMonthIsNotCountedTwice() {
        val figures =
            october(
                assignments = listOf(assign(202610, "c-food", 50_000L), assign(202611, "c-rent", 20_000L)),
                buffers = mapOf(202610 to 20_000L),
            )
        assertEquals(30_000L, figures.figures.headerMinor)
        // November spends exactly what October held, so October has nothing more spoken for.
        assertNull(figures.ahead)
    }

    @Test
    fun aLaterDipThatRecoversStillCounts() {
        val figures =
            october(
                spent = mapOf((202610 to "c-sal") to 100_000L, (202612 to "c-sal") to 90_000L),
                assignments = listOf(assign(202610, "c-food", 50_000L), assign(202611, "c-rent", 70_000L)),
            )
        assertEquals(ActualMonthMath.FuturePlans(-20_000L, 202611), figures.ahead)
    }

    @Test
    fun trackingHasNone() {
        val figures =
            october(
                assignments = listOf(assign(202610, "c-food", 50_000L), assign(202611, "c-rent", 30_000L)),
                mode = BudgetMode.TRACKING,
            )
        assertNull(figures.ahead)
    }
}
