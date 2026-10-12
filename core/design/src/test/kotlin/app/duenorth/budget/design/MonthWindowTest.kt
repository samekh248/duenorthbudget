package app.duenorth.budget.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class MonthWindowTest {
    @Test
    fun fortyNineMonthsAroundAnchor() {
        val anchor = YearMonth.of(2026, 10)
        val window = MonthWindow(anchor)
        assertEquals(49, window.months.size)
        assertEquals(YearMonth.of(2024, 10), window.oldest)
        assertEquals(YearMonth.of(2028, 10), window.newest)
        assertEquals(anchor, window.months[24])
    }

    @Test
    fun previousAndNextRespectBounds() {
        val window = MonthWindow(YearMonth.of(2026, 6))
        assertNull(window.previous(window.oldest))
        assertEquals(window.months[1], window.next(window.oldest))
        assertNull(window.next(window.newest))
        assertEquals(window.months[window.months.lastIndex - 1], window.previous(window.newest))
        assertTrue(window.contains(YearMonth.of(2026, 6)))
        assertFalse(window.contains(YearMonth.of(2024, 5)))
    }
}
