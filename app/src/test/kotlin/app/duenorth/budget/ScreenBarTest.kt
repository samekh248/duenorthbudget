package app.duenorth.budget

import app.duenorth.budget.core.BudgetMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenBarTest {
    @Test
    fun homeEnvelopeActions() {
        val ids = ScreenBar.specsFor(ShellRoute.Home, hasBudget = true, mode = BudgetMode.ENVELOPE).map { it.id }
        assertEquals(listOf("add", "hold", "categories", "settings"), ids)
    }

    @Test
    fun homeTrackingOmitsHold() {
        val ids = ScreenBar.specsFor(ShellRoute.Home, hasBudget = true, mode = BudgetMode.TRACKING).map { it.id }
        assertEquals(listOf("add", "categories", "settings"), ids)
        assertFalse(ids.contains("hold"))
    }

    @Test
    fun homeWithoutBudgetIsSettingsOnly() {
        val ids = ScreenBar.specsFor(ShellRoute.Home, hasBudget = false, mode = null).map { it.id }
        assertEquals(listOf("settings"), ids)
    }

    @Test
    fun registerActions() {
        val ids = ScreenBar.specsFor(ShellRoute.Register("a"), hasBudget = true, mode = BudgetMode.ENVELOPE).map { it.id }
        assertEquals(listOf("add", "transfer", "reconcile", "import"), ids)
    }

    @Test
    fun writeFormsAreSaveCancel() {
        val routes =
            listOf(
                ShellRoute.Create,
                ShellRoute.Assign,
                ShellRoute.AddTransaction,
                ShellRoute.EditTransaction("a", null),
                ShellRoute.HoldMonth,
                ShellRoute.CategoryBudget("c"),
                ShellRoute.ImportPreviewRoute("a"),
                ShellRoute.Reconcile("a"),
                ShellRoute.BudgetPassword,
            )
        routes.forEach { route ->
            val ids = ScreenBar.specsFor(route, hasBudget = true, mode = BudgetMode.ENVELOPE).map { it.id }
            assertEquals(route.toString(), listOf("save", "cancel"), ids)
            assertFalse(ids.contains("hold"))
            assertFalse(ids.contains("settings"))
        }
    }

    @Test
    fun settingsRouteHasNoBarActions() {
        assertTrue(ScreenBar.specsFor(ShellRoute.Settings, hasBudget = true, mode = BudgetMode.ENVELOPE).isEmpty())
    }
}
