package app.duenorth.budget

import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.MonthReviewPage
import app.duenorth.budget.core.MonthShell
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMonthTest {
    private val october = YearMonth.of(2026, 10)
    private val september = YearMonth.of(2026, 9)

    @Test
    fun cacheHitSwapsShellAndReviewTogether() {
        val store = OpenMonthStore(october)
        store.rebuildWindow(october)
        val oct = snapshot(october, 180_000L)
        val sep = snapshot(september, 90_000L)
        store.put(oct)
        store.put(sep)
        assertTrue(store.select(september, gestureActive = false))
        assertEquals(september, store.openMonth)
        assertEquals(90_000L, store.shell?.headerMinor)
        assertEquals(september, store.review?.month)
        assertNull(store.pendingMonth)
    }

    @Test
    fun gestureSetsPendingAndKeepsPaintedRows() {
        val store = OpenMonthStore(october)
        store.rebuildWindow(october)
        store.put(snapshot(october, 180_000L))
        store.put(snapshot(september, 90_000L))
        store.select(october, gestureActive = false)
        assertFalse(store.select(september, gestureActive = true))
        assertEquals(september, store.openMonth)
        assertEquals(september, store.pendingMonth)
        assertEquals(180_000L, store.shell?.headerMinor)
        assertTrue(store.flushPending(gestureActive = false))
        assertEquals(90_000L, store.shell?.headerMinor)
        assertNull(store.pendingMonth)
    }

    @Test
    fun cacheMissUpdatesMonthWithoutFigures() {
        val store = OpenMonthStore(october)
        store.rebuildWindow(october)
        store.put(snapshot(october, 180_000L))
        store.select(october, gestureActive = false)
        assertFalse(store.select(september, gestureActive = false))
        assertEquals(september, store.openMonth)
        assertEquals(180_000L, store.shell?.headerMinor)
        store.applyLoaded(snapshot(september, 0L), gestureActive = false)
        assertEquals(0L, store.shell?.headerMinor)
        assertEquals(0L, store.review?.incomeMinor)
    }

    @Test
    fun editorMonthUsesPinnedValue() {
        val store = OpenMonthStore(october)
        assertEquals(october, store.editorMonth(null))
        assertEquals(september, store.editorMonth(september))
    }

    private fun snapshot(
        month: YearMonth,
        header: Long,
    ) = MonthSnapshot(
        month = month,
        shell =
            MonthShell(
                budgetId = "b",
                budgetName = "Home",
                mode = BudgetMode.ENVELOPE,
                currency = Currencies.byCode("USD")!!,
                month = month,
                headerLabel = "to budget",
                headerMinor = header,
                groups = emptyList(),
                accounts = emptyList(),
                inbox = emptyList(),
            ),
        review =
            MonthReviewPage(
                month = month,
                currency = Currencies.byCode("USD")!!,
                incomeMinor = header,
                categories = emptyList(),
            ),
    )
}
