package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.LocalDate

class HouseholdSampleTest {
    @Test
    fun sampleIsAFilledInBudgetNamedAsASample() {
        val today = LocalDate.of(2026, 11, 9)
        val root = Files.createTempDirectory("household-sample").toFile()
        val library = BudgetLibrary(root, JdbcSessionOpener(), BudgetClock { today })
        val created = library.createSample() as CreateResult.Created
        val shell = library.readShell(created.id)!!
        assertEquals(HouseholdSample.NAME, shell.budgetName)
        assertEquals(BudgetMode.ENVELOPE, shell.mode)
        assertEquals(listOf("Checking", "Savings"), shell.accounts.map { it.name })
        assertTrue(shell.headerMinor > 0)
        assertTrue(shell.inbox.isNotEmpty())
        assertEquals(created.id, library.settings().openBudgetId)
        root.deleteRecursively()
    }

    @Test
    fun aSecondSampleIsItsOwnFile() {
        val root: File = Files.createTempDirectory("household-sample-twice").toFile()
        val library = BudgetLibrary(root, JdbcSessionOpener(), BudgetClock { LocalDate.of(2026, 11, 1) })
        val first = library.createSample() as CreateResult.Created
        val second = library.createSample() as CreateResult.Created
        assertTrue(first.id != second.id)
        assertEquals(2, library.list().size)
        root.deleteRecursively()
    }
}
