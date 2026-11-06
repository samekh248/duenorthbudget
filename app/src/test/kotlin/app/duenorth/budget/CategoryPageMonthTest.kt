package app.duenorth.budget

import app.duenorth.budget.ParityTestSupport.await
import app.duenorth.budget.ParityTestSupport.drain
import app.duenorth.budget.ParityTestSupport.household
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A category page's own month control moves the whole page: the figures, the recent list and where a save lands all
 * follow the month it names, so a later month never shows this month's spending.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CategoryPageMonthTest {
    @Test
    fun pickingALaterMonthShowsThatMonthsFigures() {
        val (model, _) = household("page-month-later")
        val now = model.state.value.openMonth
        val last = now.minusMonths(1)
        model.selectOpenMonth(last)
        await {
            model.state.value.shell
                ?.month == last
        }
        model.envelopes.openBudget("c-food")
        drain()
        assertEquals(-150_000L, model.foodSpent())

        // On the category page, the month control steps to a month with no food spending.
        model.selectOpenMonth(now)
        await {
            model.state.value.shell
                ?.month == now
        }

        // The label, the figures and the month the page edits all agree.
        assertEquals(now, model.state.value.openMonth)
        assertEquals(now, model.editorMonth())
        assertEquals(0L, model.foodSpent())

        model.loadCategoryMonth("c-food")
        await {
            model.state.value.categoryMonth
                ?.month == now
        }
        assertEquals(
            model.foodSpent(),
            model.state.value.categoryMonth!!
                .totalMinor,
        )
    }

    @Test
    fun spentOnThePageMatchesItsTransactions() {
        val (model, _) = household("page-month-match")
        val last =
            model.state.value.openMonth
                .minusMonths(1)
        model.envelopes.openBudget("c-food")
        drain()
        model.selectOpenMonth(last)
        await {
            model.state.value.shell
                ?.month == last
        }
        model.loadCategoryMonth("c-food")
        await {
            model.state.value.categoryMonth
                ?.month == last
        }
        assertEquals(
            model.state.value.categoryMonth!!
                .totalMinor,
            model.foodSpent(),
        )
    }

    @Test
    fun backFromAnotherMonthKeepsTheHomeOnIt() {
        val (model, _) = household("page-month-back")
        val next =
            model.state.value.openMonth
                .plusMonths(1)
        model.envelopes.openBudget("c-food")
        drain()
        model.selectOpenMonth(next)
        await {
            model.state.value.shell
                ?.month == next
        }
        model.back()
        drain()
        assertEquals(ShellRoute.Home, model.state.value.route)
        assertEquals(null, model.state.value.pinnedMonth)
        assertEquals(next, model.state.value.openMonth)
        assertEquals(
            next,
            model.state.value.shell!!
                .month,
        )
    }

    private fun ShellViewModel.foodSpent(): Long =
        state.value.shell!!
            .groups
            .flatMap { it.categories }
            .first { it.id == "c-food" }
            .spentMinor
}
