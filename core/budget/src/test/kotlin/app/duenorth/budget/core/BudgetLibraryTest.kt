package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class BudgetLibraryTest {
    @Test
    fun blankNameCreatesNothing() {
        val root = tempRoot()
        val library = library(root)
        val result = library.create("   ", "USD")
        assertTrue(result is CreateResult.Rejected)
        assertEquals(ShellCopy.ENTER_NAME, (result as CreateResult.Rejected).reason)
        assertTrue(root.listFiles().isNullOrEmpty() || root.listFiles()!!.none { it.isDirectory })
    }

    @Test
    fun emptyBudgetOpensOnZeroAndReopensAfterANewLibrary() {
        val root = tempRoot()
        val library = library(root, LocalDate.of(2026, 10, 7))
        val created = library.create("Home", "USD") as CreateResult.Created
        val shell = library.readShell(created.id)!!
        assertEquals("Home", shell.budgetName)
        assertEquals(BudgetMode.ENVELOPE, shell.mode)
        assertEquals(0L, shell.headerMinor)
        assertEquals(ShellCopy.TO_BUDGET, shell.headerLabel)
        assertTrue(shell.groups.isEmpty())
        assertTrue(shell.accounts.isEmpty())
        assertEquals(2026, shell.month.year)
        assertEquals(10, shell.month.monthValue)

        val reopened = library(root, LocalDate.of(2026, 10, 8))
        assertEquals(created.id, reopened.settings().openBudgetId)
        assertEquals("Home", reopened.readShell(created.id)!!.budgetName)
    }

    @Test
    fun switchingLeavesTheOtherFileUntouched() {
        val root = tempRoot()
        val library = library(root)
        val home = (library.create("Home", "USD") as CreateResult.Created).id
        val trip = (library.create("Trip", "JPY") as CreateResult.Created).id
        val homeDatabase = File(root, "$home/db.sqlite")
        val before = homeDatabase.readBytes()
        assertTrue(library.switchTo(home))
        assertEquals(home, library.settings().openBudgetId)
        library.switchTo(trip)
        assertEquals(trip, library.settings().openBudgetId)
        assertTrue(homeDatabase.readBytes().contentEquals(before))
        assertEquals("JPY", library.readShell(trip)!!.currency.code)
        assertEquals("USD", library.readShell(home)!!.currency.code)
    }

    @Test
    fun unknownCurrencyIsRejected() {
        val root = tempRoot()
        val result = library(root).create("Home", "NOPE")
        assertTrue(result is CreateResult.Rejected)
        assertFalse(File(root, "phone.json").exists())
    }

    @Test
    fun themeAndAccentRoundTrip() {
        val root = tempRoot()
        val library = library(root)
        library.save(PhoneSettings(themeMode = "light", accent = "coral"))
        val again = library(root).settings()
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromStored(again.themeMode))
        assertEquals("coral", again.accent)
        assertNull(again.openBudgetId)
    }

    @Test
    fun refreshDuringGestureKeepsTheVisibleShell() {
        val gate = RefreshGate<String>()
        assertTrue(gate.offer("living"))
        gate.beginGesture()
        assertFalse(gate.offer("rent inserted above"))
        assertEquals("living", gate.visible)
        gate.endGesture()
        assertEquals("rent inserted above", gate.visible)
    }

    private fun library(
        root: File,
        today: LocalDate = LocalDate.of(2026, 10, 7),
    ) = BudgetLibrary(root, JdbcSessionOpener(), BudgetClock { today })

    private fun tempRoot(): File =
        File.createTempFile("budgets", "").apply {
            delete()
            mkdirs()
        }
}
