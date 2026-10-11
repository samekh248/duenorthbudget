package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

class MockBudgetsTest {
    @Test
    fun catalogListsTheThreeDatasets() {
        assertEquals(
            listOf(MockBudgets.HOUSEHOLD, MockBudgets.FULL_TOUR, MockBudgets.WIDE),
            MockBudgets.all.map { it.id },
        )
    }

    @Test
    fun householdSeedsInboxAccountsAndLeftover() {
        val today = LocalDate.of(2026, 10, 7)
        val library = library(today)
        val created = library.createFromMock(MockBudgets.HOUSEHOLD) as CreateResult.Created
        val shell = library.readShell(created.id)!!
        assertEquals("household", shell.budgetName)
        assertEquals(BudgetMode.ENVELOPE, shell.mode)
        assertTrue(shell.headerMinor > 0)
        assertEquals(listOf("Checking", "Savings"), shell.accounts.map { it.name })
        assertTrue(shell.accounts.any { it.offBudget })
        assertTrue(shell.inbox.isNotEmpty())
        assertTrue(shell.groups.any { it.name == "Living" })
        assertEquals(created.id, library.settings().openBudgetId)
    }

    @Test
    fun fullTourExposesSchedulesBankLinkAndHold() {
        val today = LocalDate.of(2026, 10, 7)
        val library = library(today)
        val created = library.createFromMock(MockBudgets.FULL_TOUR) as CreateResult.Created
        val shell = library.readShell(created.id)!!
        assertEquals("full tour", shell.budgetName)
        assertEquals(50_000L, shell.bufferedMinor)
        assertTrue(shell.inbox.size >= 2)
        assertTrue(library.bankLinked(created.id, "checking"))
        val upcoming = library.readUpcomingSchedules(created.id)
        assertTrue(upcoming.any { it.dueToday })
        assertTrue(upcoming.any { !it.dueToday })
        val uncleared =
            library.useDatabase(created.id) { session ->
                session
                    .query(
                        """
                        SELECT COUNT(*) AS n FROM transactions
                        WHERE acct = 'checking' AND IFNULL(cleared, 0) = 0 AND IFNULL(tombstone, 0) = 0
                        """.trimIndent(),
                    ).first()
                    .long("n")
            }
        assertTrue((uncleared ?: 0L) >= 2L)
        val register = library.readRegister(created.id, "checking")!!
        assertTrue(register.rows.any { it.parts.isNotEmpty() })
    }

    @Test
    fun wideMonthBuildsManyGroupsAndALongRegister() {
        val today = LocalDate.of(2026, 10, 7)
        val library = library(today)
        val created = library.createFromMock(MockBudgets.WIDE) as CreateResult.Created
        val shell = library.readShell(created.id)!!
        assertTrue(shell.groups.size >= 20)
        val register = library.readRegister(created.id, "checking")!!
        assertTrue(register.rows.size >= 80)
        assertEquals(YearMonth.of(2026, 10), shell.month)
    }

    @Test
    fun unknownDatasetIsRejected() {
        val result = library(LocalDate.of(2026, 10, 7)).createFromMock("nope")
        assertTrue(result is CreateResult.Rejected)
        assertEquals(ShellCopy.UNKNOWN_SAMPLE, (result as CreateResult.Rejected).reason)
    }

    @Test
    fun samplesStayAnchoredWhenTheClockMoves() {
        val november = library(LocalDate.of(2026, 11, 3))
        val created = november.createFromMock(MockBudgets.HOUSEHOLD) as CreateResult.Created
        val shell = november.readShell(created.id)!!
        assertEquals(YearMonth.of(2026, 11), shell.month)
        assertFalse(shell.inbox.isEmpty())
    }

    private fun library(today: LocalDate): BudgetLibrary {
        val root = File.createTempFile("mock-lib", "dir")
        root.delete()
        root.mkdirs()
        return BudgetLibrary(root, JdbcSessionOpener(), BudgetClock { today })
    }
}
