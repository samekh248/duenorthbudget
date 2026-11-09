package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CloseOutTest {
    private val october = YearMonth.of(2026, 10)

    private fun item(
        id: String,
        available: Long,
        kept: Boolean = false,
    ) = CloseOutItem(id, id, 0L, 0L, available, kept)

    @Test
    fun windowOpensForTheLastDaysAndNeverWhenOff() {
        assertEquals(october, CloseOutWindow.monthFor(LocalDate.of(2026, 10, 29), 3))
        assertEquals(october, CloseOutWindow.monthFor(LocalDate.of(2026, 10, 31), 1))
        assertNull(CloseOutWindow.monthFor(LocalDate.of(2026, 10, 28), 3))
        assertNull(CloseOutWindow.monthFor(LocalDate.of(2026, 10, 30), 1))
        assertNull(CloseOutWindow.monthFor(LocalDate.of(2026, 10, 31), 0))
        assertEquals(october, CloseOutWindow.monthFor(LocalDate.of(2026, 10, 25), 7))
    }

    @Test
    fun sortsCategoriesAndPutsOverspentFirst() {
        val review = CloseOutReview(october, listOf(item("rent", 0), item("water", 2_600), item("dining", -9_275)), 12_000)
        assertEquals(listOf("dining"), review.over.map { it.categoryId })
        assertEquals(listOf("water"), review.left.map { it.categoryId })
        assertEquals(listOf("rent"), review.even.map { it.categoryId })
        assertEquals(listOf("dining", "water"), review.todo.map { it.categoryId })
        assertFalse(review.allSquare)
    }

    @Test
    fun keptCategoriesAreSettled() {
        val review = CloseOutReview(october, listOf(item("water", 2_600, kept = true), item("fuel", -100, kept = true)), 0)
        assertTrue(review.allSquare)
        assertFalse(review.rough)
    }

    @Test
    fun moreThanFiveOverIsRough() {
        val five = CloseOutReview(october, (1..5).map { item("c$it", -100) }, 0)
        val six = CloseOutReview(october, (1..6).map { item("c$it", -100) }, 0)
        assertFalse(five.rough)
        assertTrue(six.rough)
    }

    @Test
    fun sourcesStartWithToBudgetThenMostLeftOver() {
        val dining = item("dining", -9_275)
        val review = CloseOutReview(october, listOf(dining, item("water", 2_600), item("coffee", 8_850), item("rent", 0)), 12_000)
        assertEquals(listOf(null, "coffee", "water"), review.sources(dining).map { it.categoryId })
        val broke = review.copy(toBudgetMinor = 0)
        assertEquals(listOf("coffee", "water"), broke.sources(dining).map { it.categoryId })
    }

    @Test
    fun marksKeepOnlyRecentMonths() {
        var marks = CloseOutMarks.keep(emptyList(), "b", october, "water")
        marks = CloseOutMarks.keep(marks, "b", october, "water")
        assertEquals(setOf("water"), CloseOutMarks.kept(marks, "b", october))
        assertEquals(emptySet<String>(), CloseOutMarks.kept(marks, "other", october))
        val later = CloseOutMarks.prune(marks + CloseOutMarks.month("b", october), october.plusMonths(2))
        assertTrue(later.isEmpty())
    }

    @Test
    fun reviewSkipsHiddenCategoriesAndUsesToBudget() {
        val shell =
            MonthShell(
                budgetId = "b",
                budgetName = "b",
                mode = BudgetMode.ENVELOPE,
                currency = CurrencySpec("USD", "$", 2),
                month = october,
                headerLabel = "to budget",
                headerMinor = 12_000,
                groups =
                    listOf(
                        GroupRow(
                            "g",
                            "Bills",
                            0,
                            listOf(
                                CategoryRow("rent", "Rent", 100, -100, 0, false),
                                CategoryRow("old", "Old", 0, 0, 500, false, hidden = true),
                            ),
                        ),
                        GroupRow("h", "Hidden", 0, listOf(CategoryRow("x", "X", 0, 0, 5, false)), hidden = true),
                    ),
                accounts = emptyList(),
                inbox = emptyList(),
            )
        val review = CloseOutReview.of(shell, emptySet())
        assertEquals(listOf("rent"), review.items.map { it.categoryId })
        assertEquals(12_000, review.toBudgetMinor)
    }
}
