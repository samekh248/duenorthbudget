package app.duenorth.budget

import app.duenorth.budget.core.BudgetClock
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CreateResult
import app.duenorth.budget.core.EnvelopeCopy
import app.duenorth.budget.core.HttpActualTransport
import app.duenorth.budget.core.MemorySecretStore
import app.duenorth.budget.core.MockBudgets
import app.duenorth.budget.core.SyncCoordinator
import app.duenorth.budget.core.SyncCopy
import app.duenorth.budget.core.createFromMock
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** Cover overspending: an overspent category takes money from another category, or from to budget. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CoverOverspendingTest {
    @Test
    fun coverFromAnotherCategoryMovesTheMoneyIn() {
        val model = overspentHousehold("from-category")
        assertEquals(-20_000L, model.available("c-gas"))
        val food = model.available("c-food")
        model.envelopes.openBudget("c-gas")
        model.envelopes.openCover()
        assertEquals(ShellRoute.CoverCategory("c-gas"), model.state.value.route)
        model.envelopes.submitCover("c-food", "200.00")
        settleMain()
        assertEquals(0L, model.available("c-gas"))
        assertEquals(food - 20_000L, model.available("c-food"))
        // Back on the page that opened cover.
        assertEquals(ShellRoute.CategoryBudget("c-gas"), model.state.value.route)
        assertEquals(null, model.state.value.editError)
    }

    @Test
    fun coverFromToBudgetBudgetsMoreInTheCategory() {
        val model = overspentHousehold("from-to-budget")
        val header =
            model.state.value.shell!!
                .headerMinor
        model.envelopes.openCover("c-gas")
        model.envelopes.submitCover(MOVE_TO_BUDGET, "200")
        settleMain()
        assertEquals(0L, model.available("c-gas"))
        assertEquals(
            header - 20_000L,
            model.state.value.shell!!
                .headerMinor,
        )
        assertEquals(ShellRoute.Home, model.state.value.route)
    }

    @Test
    fun coverAsksForASourceAndAnAmount() {
        val model = overspentHousehold("refused")
        model.envelopes.openCover("c-gas")
        model.envelopes.submitCover("", "200")
        assertEquals(EnvelopeCopy.PICK_SOURCE, model.state.value.editError)
        model.envelopes.submitCover("c-food", "")
        assertEquals(SyncCopy.ENTER_AMOUNT, model.state.value.editError)
        model.envelopes.submitCover("c-food", "100000")
        assertEquals(EnvelopeCopy.NOT_ENOUGH, model.state.value.editError)
        assertEquals(ShellRoute.CoverCategory("c-gas"), model.state.value.route)
        assertEquals(-20_000L, model.available("c-gas"))
    }

    @Test
    fun theRowMenuOffersCoverOnlyWhileTheCategoryIsOverspent() {
        val model = overspentHousehold("row-menu")
        val menus = AppRowMenus(model)
        assertTrue(menus.category("c-gas").any { it.tag == "category-menu-cover" })
        assertFalse(menus.category("c-food").any { it.tag == "category-menu-cover" })
        menus.category("c-gas").first { it.tag == "category-menu-cover" }.run()
        assertEquals(ShellRoute.CoverCategory("c-gas"), model.state.value.route)
    }

    @Test
    fun theCategoryBarOffersCoverOnlyWhileTheCategoryIsOverspent() {
        fun ids(
            overspent: Boolean,
            mode: BudgetMode = BudgetMode.ENVELOPE,
        ) = ScreenBar.specsFor(ShellRoute.CategoryBudget("c-gas"), true, mode, overspent = overspent).map { it.id }
        assertTrue("cover" in ids(overspent = true))
        assertFalse("cover" in ids(overspent = false))
        assertFalse("cover" in ids(overspent = true, mode = BudgetMode.TRACKING))
        val model = overspentHousehold("bar")
        model.envelopes.openBudget("c-gas")
        assertTrue(ScreenBar.overspent(model.state.value))
        model.back()
        model.envelopes.openBudget("c-food")
        assertFalse(ScreenBar.overspent(model.state.value))
    }

    private fun ShellViewModel.available(id: String) =
        state.value.shell!!
            .groups
            .flatMap { it.categories }
            .first { it.id == id }
            .availableMinor
}

/** The household sample with this month's market run filed under gas: gas is $200 over, food has $700 left. */
internal fun overspentHousehold(name: String): ShellViewModel {
    val context = RuntimeEnvironment.getApplication()
    val root =
        File(context.filesDir, "cover-$name").apply {
            deleteRecursively()
            mkdirs()
        }
    val library = BudgetLibrary(root, AndroidSessionOpener(), BudgetClock.System)
    val created = library.createFromMock(MockBudgets.HOUSEHOLD) as CreateResult.Created
    library.useDatabase(created.id) { session ->
        session.exec("UPDATE transactions SET category = 'c-gas', amount = -120000 WHERE id = 't-market'")
    }
    val sync = SyncCoordinator(library, HttpActualTransport(), MemorySecretStore(), BudgetClock.System)
    val model = ShellViewModel(library, sync, io = Dispatchers.Main)
    awaitMain { !model.state.value.loading && model.state.value.shell != null }
    return model
}
