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
 * Coming back to a month the budget already holds, then scrolling, keeps that month on screen. The scroll's end used
 * to put back the last month repainted from disk (next month), so the targets vanished on the first scroll.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MonthReturnScrollTest {
    @Test
    fun scrollingAfterReturningToACachedMonthKeepsThatMonth() {
        val (model, _) = household("month-return-scroll")
        val current = model.state.value.openMonth
        val next = current.plusMonths(1)

        model.selectOpenMonth(next)
        await {
            model.state.value.shell
                ?.month == next
        }
        drain()
        // Next month is repainted from disk (a sync, an edit, coming back to the app).
        model.refresh()
        drain()

        // Back to the current month: a cache hit, so it shows at once.
        model.selectOpenMonth(current)
        assertEquals(
            current,
            model.state.value.shell!!
                .month,
        )

        // A scroll down the budget.
        model.setGesture(true)
        model.setGesture(false)
        drain()

        assertEquals(current, model.state.value.openMonth)
        assertEquals(
            current,
            model.state.value.shell!!
                .month,
        )
    }

    @Test
    fun scrollingAfterAMonthPickedMidScrollKeepsThatMonth() {
        val (model, _) = household("month-return-scroll-gesture")
        val current = model.state.value.openMonth
        val next = current.plusMonths(1)

        model.selectOpenMonth(next)
        await {
            model.state.value.shell
                ?.month == next
        }
        drain()
        // Next month is repainted from disk (a sync, an edit, coming back to the app).
        model.refresh()
        drain()

        // Back to the current month while a scroll is under way: it lands when the scroll ends.
        model.setGesture(true)
        model.selectOpenMonth(current)
        model.setGesture(false)
        assertEquals(
            current,
            model.state.value.shell!!
                .month,
        )

        model.setGesture(true)
        model.setGesture(false)
        drain()

        assertEquals(
            current,
            model.state.value.shell!!
                .month,
        )
    }
}
