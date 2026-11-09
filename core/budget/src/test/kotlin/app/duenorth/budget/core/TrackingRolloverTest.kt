package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

/** Actual's `tracking.ts`: rollover carries the whole balance; hidden categories and groups stay out of totals. */
class TrackingRolloverTest {
    private val income = ActualMonthMath.GroupFact("g-inc", isIncome = true, sortOrder = 0.0)
    private val living = ActualMonthMath.GroupFact("g-liv", isIncome = false, sortOrder = 1.0)
    private val stashed = ActualMonthMath.GroupFact("g-old", isIncome = false, sortOrder = 2.0, hidden = true)
    private val salary = ActualMonthMath.CategoryFact("c-sal", "g-inc", isIncome = true)
    private val food = ActualMonthMath.CategoryFact("c-food", "g-liv", isIncome = false)
    private val gifts = ActualMonthMath.CategoryFact("c-gift", "g-liv", isIncome = false)
    private val retired = ActualMonthMath.CategoryFact("c-ret", "g-liv", isIncome = false, hidden = true)
    private val old = ActualMonthMath.CategoryFact("c-old", "g-old", isIncome = false)

    private fun october(
        assignments: List<ActualMonthMath.AssignmentFact>,
        spent: Map<Pair<Int, String>, Long>,
    ) = ActualMonthMath.projectDetailed(
        mode = BudgetMode.TRACKING,
        month = YearMonth.of(2026, 10),
        groups = listOf(income, living, stashed),
        categories = listOf(salary, food, gifts, retired, old),
        spentByCategoryMonth = spent,
        assignments = assignments,
        buffers = emptyMap(),
    )

    @Test
    fun rolloverCarriesWhatIsLeftEitherWay() {
        val detailed =
            october(
                assignments =
                    listOf(
                        ActualMonthMath.AssignmentFact(202609, "c-food", 10_000L, true),
                        ActualMonthMath.AssignmentFact(202609, "c-gift", 5_000L, true),
                        ActualMonthMath.AssignmentFact(202610, "c-food", 10_000L, false),
                        ActualMonthMath.AssignmentFact(202610, "c-gift", 5_000L, false),
                    ),
                spent =
                    mapOf(
                        (202609 to "c-food") to -12_000L,
                        (202609 to "c-gift") to -1_000L,
                        (202610 to "c-food") to -3_000L,
                    ),
            )
        // September's −20.00 and +40.00 carry in.
        assertEquals(5_000L, detailed.categories["c-food"]?.availableMinor)
        assertEquals(9_000L, detailed.categories["c-gift"]?.availableMinor)
        assertEquals(14_000L, detailed.figures.headerMinor)
    }

    @Test
    fun withoutRolloverEachMonthStartsFresh() {
        val detailed =
            october(
                assignments = listOf(ActualMonthMath.AssignmentFact(202610, "c-food", 10_000L, true)),
                spent = mapOf((202609 to "c-food") to -12_000L, (202610 to "c-food") to -3_000L),
            )
        assertEquals(7_000L, detailed.categories["c-food"]?.availableMinor)
        assertEquals(true, detailed.categories["c-food"]?.carryover)
    }

    @Test
    fun hiddenCategoriesAndGroupsStayOutOfTotals() {
        val detailed =
            october(
                assignments =
                    listOf(
                        ActualMonthMath.AssignmentFact(202610, "c-food", 10_000L, false),
                        ActualMonthMath.AssignmentFact(202610, "c-ret", 2_000L, false),
                        ActualMonthMath.AssignmentFact(202610, "c-old", 3_000L, false),
                    ),
                spent = emptyMap(),
            )
        assertEquals(10_000L, detailed.figures.groupAvailable["g-liv"])
        assertEquals(10_000L, detailed.figures.headerMinor)
        // The hidden category still has its own balance.
        assertEquals(2_000L, detailed.categories["c-ret"]?.availableMinor)
    }
}
