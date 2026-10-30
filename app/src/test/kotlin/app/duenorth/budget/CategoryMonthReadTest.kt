package app.duenorth.budget

import app.duenorth.budget.ParityTestSupport.await
import app.duenorth.budget.ParityTestSupport.household
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The category page's recent, read through the phone's sqlite: a month filter worked out in SQL finds its rows. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CategoryMonthReadTest {
    @Test
    fun recentFindsTheMonthsTransactions() {
        val (model, _) = household("category-month")
        val last =
            model.state.value.openMonth
                .minusMonths(1)
        model.envelopes.openBudget("c-food")
        model.selectOpenMonth(last)
        model.loadCategoryMonth("c-food")
        await {
            model.state.value.categoryMonth
                ?.month == last
        }
        val page = model.state.value.categoryMonth!!
        assertEquals(listOf("t-food-l"), page.rows.map { it.id })
        assertEquals(-150_000L, page.totalMinor)
    }

    @Test
    fun monthReviewCountsTheMonthsSpending() {
        val (model, library) = household("month-review")
        val budgetId = model.state.value.settings.openBudgetId!!
        val last =
            model.state.value.openMonth
                .minusMonths(1)
        val review = library.readMonthReview(budgetId, last)!!
        assertEquals(-150_000L, review.categories.first { it.id == "c-food" }.spentMinor)
    }
}
