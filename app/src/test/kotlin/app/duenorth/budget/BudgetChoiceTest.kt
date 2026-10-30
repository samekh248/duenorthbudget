package app.duenorth.budget

import app.duenorth.budget.core.BudgetSummary
import app.duenorth.budget.core.RemoteFile
import org.junit.Assert.assertEquals
import org.junit.Test

/** The open-budget box's entries: the phone's budgets, then the server's the phone does not hold. */
class BudgetChoiceTest {
    private val home = BudgetSummary("home", "Home", "USD")
    private val trip = BudgetSummary("trip", "Trip", "USD")
    private val files =
        listOf(
            RemoteFile("trip", "g1", "Trip", null),
            RemoteFile("cloud-home", "g2", "Home", null),
            RemoteFile("household", "g3", "Household", "key"),
        )

    @Test
    fun serverBudgetsThePhoneHoldsAreListedOnce() {
        val keys = BudgetChoice.of(listOf(home, trip), files, "home server", cloudFileId = "cloud-home").map { it.key }
        assertEquals(listOf("phone-home", "phone-trip", "server-household"), keys)
    }

    @Test
    fun aServerBudgetNamesItsServer() {
        val server = BudgetChoice.of(emptyList(), files.takeLast(1), "home server", null).single()
        assertEquals("Household · home server", server.label)
    }

    @Test
    fun signedOutListsOnlyThePhone() {
        assertEquals(listOf("phone-home"), BudgetChoice.of(listOf(home), files, null, null).map { it.key })
    }
}
