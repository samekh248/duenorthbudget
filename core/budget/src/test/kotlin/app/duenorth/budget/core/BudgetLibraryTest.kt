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

    @Test
    fun syncInsertDuringScrollKeepsTheRowUnderTheFinger() {
        val gate = RefreshGate<List<String>>()
        val rows = (0 until 500).map { "row-$it" }
        assertTrue(gate.offer(rows))
        gate.beginGesture()
        assertFalse(gate.offer(listOf("rent inserted above") + rows))
        assertEquals("row-0", gate.visible!!.first())
        assertEquals(500, gate.visible!!.size)
        gate.endGesture()
        assertEquals("rent inserted above", gate.visible!!.first())
        assertEquals(501, gate.visible!!.size)
    }

    /** Spec 010 T071: a transaction save with 100 rules stays inside the spec 001 budget and its repaint waits for the gesture. */
    @Test
    fun aHundredRuleSaveIsQuickAndHeldDuringAGesture() {
        val library = library(tempRoot())
        val id = (library.createFromMock(MockBudgets.HOUSEHOLD) as CreateResult.Created).id
        val account = library.readShell(id)!!.accounts.first().id
        repeat(100) {
            val condition = RulePart.condition("notes", "contains", kotlinx.serialization.json.JsonPrimitive("tag-$it"))
            val action = RulePart.action("category", "set", kotlinx.serialization.json.JsonPrimitive("c-food"))
            assertTrue(library.saveRule(id, RuleDraft(null, null, "and", listOf(condition), listOf(action))) is RuleSaveResult.Saved)
        }
        val gate = RefreshGate<MonthShell>()
        gate.offer(library.readShell(id)!!)
        val before = gate.visible
        library.saveTransaction(id, TransactionDraft("warm", account, 20261007, "Cafe", -100L, null, "tag-1"))
        gate.beginGesture()
        val runs = 10
        val started = System.nanoTime()
        repeat(runs) { library.saveTransaction(id, TransactionDraft("t$it", account, 20261007, "Cafe", -100L, null, "tag-99")) }
        val perSave = (System.nanoTime() - started) / runs / 1_000_000.0
        assertTrue("save with 100 rules took $perSave ms", perSave < 50.0)
        assertFalse(gate.offer(library.readShell(id)!!))
        assertEquals(before, gate.visible)
        gate.endGesture()
        assertTrue(gate.visible != before)
    }

    /** Spec 010 T071: undo on the panorama is under 100 ms and does not move rows under the finger. */
    @Test
    fun undoOnThePanoramaIsQuickAndHeldDuringAGesture() {
        val library = library(tempRoot())
        val id = (library.createFromMock(MockBudgets.HOUSEHOLD) as CreateResult.Created).id
        val account = library.readShell(id)!!.accounts.first().id
        repeat(11) { library.saveTransaction(id, TransactionDraft("u$it", account, 20261007, "Cafe", -1_000L * (it + 1), "c-food", null)) }
        library.undo(id)
        val gate = RefreshGate<MonthShell>()
        gate.offer(library.readShell(id)!!)
        val before = gate.visible
        gate.beginGesture()
        val worst =
            (0 until 10).maxOf {
                val started = System.nanoTime()
                assertTrue(library.undo(id) is UndoResult.Undone)
                (System.nanoTime() - started) / 1_000_000
            }
        assertTrue("slowest undo took $worst ms", worst < 100)
        assertFalse(gate.offer(library.readShell(id)!!))
        assertEquals(before, gate.visible)
        gate.endGesture()
        assertTrue(gate.visible != before)
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
