package app.duenorth.budget

import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.GroupRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The category widget shows more as it grows: what each launcher size gets. */
class CategoryWidgetTest {
    @Test
    fun oneCellTallAndNarrowShowsThreeLinesWithNoTitle() {
        val plan = CategoryWidgetPlan.of(widthDp = 164, heightDp = 92)
        assertEquals(CategoryWidgetPlan.Shape.COMPACT, plan.shape)
        assertEquals(3, plan.slots)
        assertFalse(plan.cents)
    }

    @Test
    fun oneCellTallAndWideLaysCategoriesSideBySide() {
        val plan = CategoryWidgetPlan.of(widthDp = 336, heightDp = 92)
        assertEquals(CategoryWidgetPlan.Shape.STRIP, plan.shape)
        assertEquals(3, plan.slots)
        assertTrue(plan.cents)
    }

    @Test
    fun twoByTwoIsAListOfWholeAmounts() {
        val plan = CategoryWidgetPlan.of(widthDp = 164, heightDp = 192)
        assertEquals(CategoryWidgetPlan.Shape.LIST, plan.shape)
        assertEquals(4, plan.slots)
        assertFalse(plan.columns)
        assertFalse(plan.cents)
        assertFalse(plan.bars)
    }

    @Test
    fun fourByTwoAddsBudgetedAndSpentColumns() {
        val plan = CategoryWidgetPlan.of(widthDp = 336, heightDp = 192)
        assertTrue(plan.columns)
        assertTrue(plan.cents)
        assertFalse(plan.total)
        assertEquals(4, plan.slots)
    }

    @Test
    fun fourByFourAddsBarsAndATotal() {
        val plan = CategoryWidgetPlan.of(widthDp = 336, heightDp = 392)
        assertTrue(plan.bars)
        assertTrue(plan.total)
        assertEquals(7, plan.slots)
    }

    @Test
    fun aTallerWidgetFitsMoreRows() {
        val short = CategoryWidgetPlan.of(widthDp = 336, heightDp = 300).slots
        val tall = CategoryWidgetPlan.of(widthDp = 336, heightDp = 600).slots
        assertTrue(tall > short)
    }

    @Test
    fun daysLeftCountsToday() {
        assertEquals(12, CategoryWidgetPlan.daysLeft(LocalDate.of(2026, 11, 19)))
        assertEquals(1, CategoryWidgetPlan.daysLeft(LocalDate.of(2026, 11, 30)))
        assertEquals("last day", CategoryWidgetCopy.daysLeft(LocalDate.of(2026, 11, 30), short = false))
        assertEquals("12 days left", CategoryWidgetCopy.daysLeft(LocalDate.of(2026, 11, 19), short = false))
        assertEquals("12d", CategoryWidgetCopy.daysLeft(LocalDate.of(2026, 11, 19), short = true))
    }

    @Test
    fun chosenCategoriesKeepTheirOrderAndDeletedOnesDropOut() {
        val groups =
            listOf(
                GroupRow("g1", "Everyday", 0, listOf(row("a"), row("b"))),
                GroupRow("g2", "Bills", 0, listOf(row("c"))),
            )
        assertEquals(listOf("c", "a"), CategoryWidgetPlan.resolve(listOf("c", "gone", "a"), groups).map { it.id })
    }

    @Test
    fun theBarIsHowMuchOfTheMoneyIsSpent() {
        // $388 of $600 spent.
        assertEquals(64, CategoryWidgetPlan.spentPercent(row("g", budgeted = 60_000, spent = -38_760)))
        // Overspent fills the bar; nothing budgeted and nothing spent leaves it empty.
        assertEquals(100, CategoryWidgetPlan.spentPercent(row("d", budgeted = 15_000, spent = -16_825)))
        assertEquals(0, CategoryWidgetPlan.spentPercent(row("z", budgeted = 0, spent = 0)))
    }

    private fun row(
        id: String,
        budgeted: Long = 10_000,
        spent: Long = 0,
    ) = CategoryRow(id, id, budgeted, spent, budgeted + spent, carryover = false)
}
