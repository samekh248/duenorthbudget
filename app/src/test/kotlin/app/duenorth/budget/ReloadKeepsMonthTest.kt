package app.duenorth.budget

import app.duenorth.budget.ParityTestSupport.await
import app.duenorth.budget.ParityTestSupport.drain
import app.duenorth.budget.ParityTestSupport.household
import app.duenorth.budget.core.CreateResult
import app.duenorth.budget.core.MockBudgets
import app.duenorth.budget.core.createFromMock
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth

/**
 * Reading the budget again (a sync, coming back to the app, the inbox's sync) keeps the month on screen. Only a
 * different budget starts again on the calendar month.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReloadKeepsMonthTest {
    @Test
    fun refreshKeepsAnEarlierMonthAndItsFigures() {
        val (model, _) = household("reload-keeps-month")
        val last = model.openLastMonth()
        val lastSpent = model.foodSpent()

        model.refresh()
        drain()

        assertEquals(last, model.state.value.openMonth)
        assertEquals(
            last,
            model.state.value.shell!!
                .month,
        )
        assertEquals(lastSpent, model.foodSpent())
    }

    @Test
    fun comingBackToTheAppKeepsTheMonth() {
        val (model, _) = household("reload-return")
        val last = model.openLastMonth()

        // Coming back (as after the notification shade or another app) syncs and reads the budget again, once the
        // last such sync is over a minute old.
        model.lastScreenSync = 0L
        model.onScreenReturned()
        drain()

        assertEquals(last, model.state.value.openMonth)
        assertEquals(
            last,
            model.state.value.shell!!
                .month,
        )
    }

    @Test
    fun syncNowKeepsTheMonth() {
        val (model, _) = household("reload-sync-now")
        val last = model.openLastMonth()

        model.budgetFiles.syncNow()
        drain()

        assertEquals(last, model.state.value.openMonth)
        assertEquals(
            last,
            model.state.value.shell!!
                .month,
        )
    }

    @Test
    fun aCategoryPageKeepsItsMonthThroughARefresh() {
        val (model, _) = household("reload-category-page")
        val last = model.openLastMonth()
        model.envelopes.openBudget("c-food")
        drain()

        model.refresh()
        drain()

        assertEquals(ShellRoute.CategoryBudget("c-food"), model.state.value.route)
        assertEquals(last, model.state.value.pinnedMonth)
        assertEquals(last, model.editorMonth())
        assertEquals(
            last,
            model.state.value.shell!!
                .month,
        )
    }

    @Test
    fun anotherBudgetStartsOnTheCalendarMonth() {
        val (model, library) = household("reload-switch")
        model.openLastMonth()
        val other = (library.createFromMock(MockBudgets.HOUSEHOLD) as CreateResult.Created).id
        library.switchTo(other)

        model.refresh()
        await { model.state.value.settings.openBudgetId == other }
        drain()

        assertEquals(YearMonth.now(), model.state.value.openMonth)
        assertEquals(
            YearMonth.now(),
            model.state.value.shell!!
                .month,
        )
    }

    private fun ShellViewModel.openLastMonth(): YearMonth {
        val last = state.value.openMonth.minusMonths(1)
        selectOpenMonth(last)
        await { state.value.shell?.month == last }
        assertEquals(-150_000L, foodSpent())
        return last
    }

    private fun ShellViewModel.foodSpent(): Long =
        state.value.shell!!
            .groups
            .flatMap { it.categories }
            .first { it.id == "c-food" }
            .spentMinor
}
