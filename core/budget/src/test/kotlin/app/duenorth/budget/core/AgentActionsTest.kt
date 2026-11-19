package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class AgentActionsTest {
    private val today = LocalDate.of(2026, 10, 7)

    @Test
    fun categoryBalanceForgivesCaseAndPartialNames() {
        val agent = sample()
        val food = agent.categoryBalance("food")
        assertEquals("Food", food.category)
        assertEquals("Living", food.group)
        assertEquals("2200.00", food.budgeted)
        assertEquals("$2,700.00", food.display)
        assertFalse(food.overspent)
        assertEquals("Utilities", agent.categoryBalance("util").category)
    }

    @Test
    fun unknownCategoryNamesTheOnesThatExist() {
        val problem = assertThrows(AgentProblem.NotFound::class.java) { sample().categoryBalance("Groceries") }
        assertTrue(problem.message!!, problem.message!!.contains("Food"))
    }

    @Test
    fun inboxListsUncategorizedNewestFirst() {
        val inbox = sample().inbox()
        assertEquals(3, inbox.count)
        assertEquals("Bus", inbox.items.first().payee)
        assertEquals("-10.00", inbox.items.first().amount)
        assertEquals("2026-10-04", inbox.items.first().date)
    }

    @Test
    fun addingWithACategorySpendsFromIt() {
        val agent = sample()
        val added = agent.addTransaction(payee = "Market", amount = 42.5, categoryName = "food", date = "yesterday")
        assertEquals("Checking", added.account)
        assertEquals("Food", added.category)
        assertEquals("-42.50", added.amount)
        assertEquals("2026-10-06", added.date)
        assertFalse(added.inInbox)
        assertEquals("2657.50", agent.categoryBalance("Food").available)
    }

    @Test
    fun addingWithoutACategoryLandsInTheInbox() {
        val agent = sample()
        val added = agent.addTransaction(payee = "Hardware store", amount = 19.99)
        assertNull(added.category)
        assertTrue(added.inInbox)
        assertEquals(4, agent.inbox().count)
    }

    @Test
    fun incomeGoesToAnIncomeCategory() {
        val added = sample().addTransaction(payee = "Acme Payroll", amount = 100.0, income = true, categoryName = "salary")
        assertEquals("100.00", added.amount)
        assertEquals("Salary", added.category)
    }

    @Test
    fun badAsksAreExplained() {
        val agent = sample()
        assertThrows(AgentProblem.Invalid::class.java) { agent.addTransaction(payee = "Cafe", amount = 0.0) }
        assertThrows(AgentProblem.Invalid::class.java) { agent.addTransaction(payee = " ", amount = 3.0) }
        assertThrows(AgentProblem.Invalid::class.java) { agent.addTransaction(payee = "Cafe", amount = 3.0, date = "next Tuesday") }
        assertThrows(AgentProblem.NotFound::class.java) { agent.addTransaction(payee = "Cafe", amount = 3.0, accountName = "Visa") }
    }

    @Test
    fun overviewSummarisesTheMonth() {
        val overview = sample().overview()
        assertEquals("2026-10", overview.month)
        assertEquals(3, overview.inboxCount)
        assertTrue(overview.accounts.containsAll(listOf("Checking", "Savings")))
        assertTrue(overview.headline, overview.headline.contains("$"))
    }

    @Test
    fun noOpenBudgetSaysSo() {
        val library = BudgetLibrary(tempRoot(), JdbcSessionOpener(), BudgetClock { today })
        val problem = assertThrows(AgentProblem.NotFound::class.java) { AgentActions(library, BudgetClock { today }).overview() }
        assertEquals(AgentActions.NO_BUDGET, problem.message)
    }

    private fun sample(): AgentActions {
        val clock = BudgetClock { today }
        val library = BudgetLibrary(tempRoot(), JdbcSessionOpener(), clock)
        library.createSample()
        return AgentActions(library, clock)
    }

    private fun tempRoot(): File =
        File.createTempFile("budgets", "").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }
}
