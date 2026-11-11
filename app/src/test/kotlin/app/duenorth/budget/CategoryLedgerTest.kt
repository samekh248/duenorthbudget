package app.duenorth.budget

import app.duenorth.budget.ParityTestSupport.await
import app.duenorth.budget.ParityTestSupport.drain
import app.duenorth.budget.ParityTestSupport.household
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A category page's "see all" opens the ledger filtered to that category, across every month, rather than the
 * month's report; the filter clears in place, and back returns to the category.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CategoryLedgerTest {
    @Test
    fun seeAllOpensTheLedgerFilteredToTheCategory() {
        val (model, _) = household("ledger-see-all")
        model.envelopes.openBudget("c-food")
        drain()
        model.openLedger("c-food")
        await { model.state.value.ledger != null }

        assertEquals(ShellRoute.Ledger("c-food"), model.state.value.route)
        val page = model.state.value.ledger!!
        assertEquals("Food", page.categoryName)
        // Last month's food shows though the page is on this month: the ledger is not one report period.
        assertEquals(listOf("t-food-l"), page.rows.map { it.row.id })
        assertEquals("Checking", page.rows.single().accountName)
    }

    @Test
    fun clearingTheFilterShowsEveryTransactionAndBackReturnsToTheCategory() {
        val (model, _) = household("ledger-clear")
        model.envelopes.openBudget("c-food")
        drain()
        model.openLedger("c-food")
        await { model.state.value.ledger != null }

        model.clearLedgerFilter()
        await { model.state.value.ledger != null }
        assertEquals(ShellRoute.Ledger(null), model.state.value.route)
        val ids =
            model.state.value.ledger!!
                .rows
                .map { it.row.id }
        assertTrue(ids.containsAll(listOf("t-food-l", "t-gas-l", "t-save", "t-cafe")))

        model.back()
        drain()
        assertEquals(ShellRoute.CategoryBudget("c-food"), model.state.value.route)
    }

    @Test
    fun aRowOpensItsRegisterAndBackReturnsToTheLedger() {
        val (model, _) = household("ledger-row")
        model.envelopes.openBudget("c-food")
        drain()
        model.openLedger("c-food")
        await { model.state.value.ledger != null }
        val entry =
            model.state.value.ledger!!
                .rows
                .single()

        model.register.openTransaction(entry.accountId, entry.registerId)
        drain()
        assertEquals(ShellRoute.Register("checking"), model.state.value.route)

        model.back()
        drain()
        assertEquals(ShellRoute.Ledger("c-food"), model.state.value.route)
        model.back()
        drain()
        assertEquals(ShellRoute.CategoryBudget("c-food"), model.state.value.route)
    }
}
