package app.duenorth.budget

import app.duenorth.budget.ParityTestSupport.await
import app.duenorth.budget.ParityTestSupport.drain
import app.duenorth.budget.ParityTestSupport.household
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The budget's placeholders follow a real month read: picking a month mid-gesture starts no read, so nothing says
 * loading, and once a read lands, loading ends with the month's figures on screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MonthLoadingStateTest {
    @Test
    fun aMonthPickedMidGestureIsNotLoadingUntilItsReadStarts() {
        val (model, _) = household("month-loading-gesture")
        val shown = model.state.value.openMonth
        val last = shown.minusMonths(1)

        model.setGesture(true)
        model.selectOpenMonth(last)
        drain()

        assertEquals(last, model.state.value.openMonth)
        assertEquals(
            shown,
            model.state.value.shell!!
                .month,
        )
        assertNull(model.state.value.monthLoading)

        model.setGesture(false)
        await {
            model.state.value.shell
                ?.month == last
        }
        drain()

        assertNull(model.state.value.monthLoading)
    }

    @Test
    fun loadingEndsWithTheMonthOnScreen() {
        val (model, _) = household("month-loading-done")
        val last =
            model.state.value.openMonth
                .minusMonths(1)

        model.selectOpenMonth(last)
        await {
            model.state.value.shell
                ?.month == last
        }
        drain()

        assertNull(model.state.value.monthLoading)
        assertNotEquals(null, model.state.value.shell)
    }
}
