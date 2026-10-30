package app.duenorth.budget

import app.duenorth.budget.ParityTestSupport.await
import app.duenorth.budget.ParityTestSupport.household
import app.duenorth.budget.core.SyncCopy
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Saving a category's budget goes back to the page that opened it; a save that cannot go through stays put. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CategorySaveReturnsTest {
    @Test
    fun aSavedBudgetGoesBackHome() {
        val (model, _) = household("category-save-home")
        model.envelopes.openBudget("c-food")
        assertEquals(ShellRoute.CategoryBudget("c-food"), model.state.value.route)
        model.envelopes.saveBudget("250")
        await { model.state.value.route == ShellRoute.Home }
        assertEquals(null, model.state.value.editError)
        await {
            model.state.value.shell
                ?.groups
                ?.flatMap { it.categories }
                ?.firstOrNull { it.id == "c-food" }
                ?.budgetedMinor == 25_000L
        }
    }

    @Test
    fun aSavedIncomeBudgetGoesBackHome() {
        val (model, _) = household("category-save-income")
        model.envelopes.openBudget("c-sal")
        model.envelopes.saveBudget("5000")
        await { model.state.value.route == ShellRoute.Home }
    }

    @Test
    fun anAmountThatDoesNotReadStaysOnTheCategory() {
        val (model, _) = household("category-save-bad")
        model.envelopes.openBudget("c-food")
        model.envelopes.saveBudget("lots")
        assertEquals(SyncCopy.ENTER_AMOUNT, model.state.value.editError)
        assertEquals(ShellRoute.CategoryBudget("c-food"), model.state.value.route)
    }
}
