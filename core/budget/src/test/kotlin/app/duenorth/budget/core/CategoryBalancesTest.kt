package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Every category picker shows what each category has available this month, as the budget page does. */
class CategoryBalancesTest {
    private val today = LocalDate.of(2026, 10, 7)

    private fun library(): BudgetLibrary {
        val root = File.createTempFile("balances-lib", "dir")
        root.delete()
        root.mkdirs()
        return BudgetLibrary(root, JdbcSessionOpener(), BudgetClock { today })
    }

    @Test
    fun pickerBalancesMatchTheBudgetPage() {
        val library = library()
        val created = library.createFromMock(MockBudgets.HOUSEHOLD) as CreateResult.Created
        val shell = library.readShell(created.id)!!
        val onPage = shell.groups.flatMap { it.categories }.associate { it.id to it.availableMinor }
        assertTrue(onPage.isNotEmpty())

        val register = library.readRegister(created.id, "checking")!!
        register.categories.filter { !it.income && it.id in onPage }.forEach { choice ->
            assertEquals(choice.name, onPage.getValue(choice.id), choice.availableMinor)
        }
        register.categories.filter { it.income }.forEach { assertNull(it.name, it.availableMinor) }

        val transaction = register.rows.first()
        val target = library.readCategoryTarget(created.id, transaction.id)!!
        target.categories.filter { !it.income && it.id in onPage }.forEach { choice ->
            assertEquals(choice.name, onPage.getValue(choice.id), choice.availableMinor)
        }
    }
}
