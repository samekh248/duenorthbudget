package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/** Hold for next month adds what is typed to what the month already holds, up to what is left to budget. */
class HoldForNextMonthTest {
    private val today = LocalDate.of(2026, 10, 7)
    private val october = YearMonth.of(2026, 10)

    @Test
    fun holdAddsToWhatIsAlreadyHeld() {
        val (library, sync, id) = open(MockBudgets.FULL_TOUR)
        val before = library.readShell(id, october)!!
        assertEquals(50_000L, before.bufferedMinor)

        assertEquals(EditResult.Saved, sync.holdForNextMonth(id, october, "25"))

        val after = library.readShell(id, october)!!
        assertEquals(52_500L, after.bufferedMinor)
        assertEquals(before.headerMinor - 2_500L, after.headerMinor)
    }

    @Test
    fun holdTwiceKeepsAdding() {
        val (library, sync, id) = open(MockBudgets.HOUSEHOLD)
        val start = library.readShell(id, october)!!
        assertEquals(0L, start.bufferedMinor)

        assertEquals(EditResult.Saved, sync.holdForNextMonth(id, october, "10"))
        assertEquals(EditResult.Saved, sync.holdForNextMonth(id, october, "15"))

        val after = library.readShell(id, october)!!
        assertEquals(2_500L, after.bufferedMinor)
        assertEquals(start.headerMinor - 2_500L, after.headerMinor)
    }

    @Test
    fun holdCanTakeEverythingLeftToBudget() {
        val (library, sync, id) = open(MockBudgets.FULL_TOUR)
        val before = library.readShell(id, october)!!
        val all = plain(before.headerMinor)

        assertEquals(EditResult.Saved, sync.holdForNextMonth(id, october, all))

        val after = library.readShell(id, october)!!
        assertEquals(0L, after.headerMinor)
        assertEquals(before.bufferedMinor + before.headerMinor, after.bufferedMinor)
    }

    @Test
    fun holdRejectsMoreThanIsLeftToBudget() {
        val (library, sync, id) = open(MockBudgets.FULL_TOUR)
        val before = library.readShell(id, october)!!
        val tooMuch = plain(before.headerMinor + 1)

        assertEquals(EditResult.Rejected(EnvelopeCopy.HOLD_TOO_MUCH), sync.holdForNextMonth(id, october, tooMuch))
        assertEquals(before.bufferedMinor, library.readShell(id, october)!!.bufferedMinor)
    }

    @Test
    fun holdRejectsNothingOrLess() {
        val (library, sync, id) = open(MockBudgets.FULL_TOUR)
        assertEquals(EditResult.Rejected(SyncCopy.ENTER_AMOUNT), sync.holdForNextMonth(id, october, "0"))
        assertEquals(EditResult.Rejected(SyncCopy.ENTER_AMOUNT), sync.holdForNextMonth(id, october, "-5"))
        assertEquals(50_000L, library.readShell(id, october)!!.bufferedMinor)
    }

    @Test
    fun releaseStillClearsTheWholeHold() {
        val (library, sync, id) = open(MockBudgets.FULL_TOUR)
        sync.holdForNextMonth(id, october, "25")
        assertEquals(EditResult.Saved, sync.releaseMonthHold(id, october))
        assertEquals(0L, library.readShell(id, october)!!.bufferedMinor)
    }

    private fun plain(minor: Long): String = "%d.%02d".format(minor / 100, minor % 100)

    private fun open(mock: String): Triple<BudgetLibrary, SyncCoordinator, String> {
        val root =
            File.createTempFile("hold", "").apply {
                delete()
                mkdirs()
            }
        val library = BudgetLibrary(root, JdbcSessionOpener(), BudgetClock { today })
        val sync = SyncCoordinator(library, HttpActualTransport(), MemorySecretStore(), BudgetClock { today }, ZoneOffset.UTC)
        val id = (library.createFromMock(mock) as CreateResult.Created).id
        return Triple(library, sync, id)
    }
}
