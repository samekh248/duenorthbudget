package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncClockTest {
    @Test
    fun stampsSortInTimeOrderAndStayFortySixCharacters() {
        var millis = 1_444_000_000_000L
        val clock = SyncClock("phone") { millis }
        val first = clock.send()
        millis += 5
        val second = clock.send()
        val sameMillis = clock.send()
        assertEquals(46, first.length)
        assertTrue(first < second)
        assertTrue(second < sameMillis)
        assertTrue(first.endsWith("phone"))
        assertEquals(SyncClock.ZERO, "1970-01-01T00:00:00.000Z-0000-0000000000000000")
    }

    @Test
    fun observingARemoteStampMovesTheNextSendPastIt() {
        val clock = SyncClock("phone") { 1_000L }
        clock.observe("1970-01-01T00:00:02.000Z-0000-0000000000000001")
        val next = clock.send()
        assertTrue(next > "1970-01-01T00:00:02.000Z-0000-0000000000000001")
    }

    @Test
    fun restoreIfBehindDoesNotMoveBackwards() {
        val clock = SyncClock("phone") { 5_000L }
        val sent = clock.send()
        clock.restoreIfBehind("1970-01-01T00:00:01.000Z-0000-0000000000000001")
        val again = clock.send()
        assertTrue(again > sent)
    }
}
