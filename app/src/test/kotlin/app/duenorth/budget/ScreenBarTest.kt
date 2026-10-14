package app.duenorth.budget

import app.duenorth.budget.core.BudgetMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenBarTest {
    @Test
    fun homeEnvelopeActions() {
        val specs = ScreenBar.specsFor(ShellRoute.Home, hasBudget = true, mode = BudgetMode.ENVELOPE)
        assertEquals(listOf("add", "hold", "categories", "settings"), specs.filter { it.primary }.map { it.id })
        assertEquals(
            listOf("breakdown", "copy-last", "average", "zero", "accounts", "add-schedule", "rules", "reports"),
            specs.filter { !it.primary }.map { it.id },
        )
    }

    @Test
    fun homeTrackingOmitsHold() {
        val specs = ScreenBar.specsFor(ShellRoute.Home, hasBudget = true, mode = BudgetMode.TRACKING)
        val ids = specs.map { it.id }
        assertEquals(listOf("add", "categories", "settings"), specs.filter { it.primary }.map { it.id })
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
        assertEquals(listOf("add", "transfer", "reconcile", "import", "edit-account", "bank-link", "close"), ids)
    }

    @Test
    fun envelopeIncomeCategoryHasNoSave() {
        val income = ScreenBar.specsFor(ShellRoute.CategoryBudget("c"), true, BudgetMode.ENVELOPE, incomeCategory = true)
        assertTrue(income.isEmpty())
        val tracking = ScreenBar.specsFor(ShellRoute.CategoryBudget("c"), true, BudgetMode.TRACKING, incomeCategory = true)
        assertEquals(listOf("save", "cancel"), tracking.map { it.id })
    }

    @Test
    fun undoAppearsOnHomeAndRegisterOnlyWithAStep() {
        listOf(ShellRoute.Home, ShellRoute.Register("a")).forEach { route ->
            val none = ScreenBar.specsFor(route, hasBudget = true, mode = BudgetMode.ENVELOPE, undoLabel = null)
            assertFalse(none.any { it.id == "undo" })
            val some = ScreenBar.specsFor(route, hasBudget = true, mode = BudgetMode.ENVELOPE, undoLabel = "assign")
            assertEquals("undo assign", some.single { it.id == "undo" }.label)
        }
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
            assertEquals(route.toString(), listOf("save", "cancel"), ids.take(2))
            assertTrue(route.toString(), ids.drop(2).all { it == "to-budget" })
            assertFalse(ids.contains("hold"))
            assertFalse(ids.contains("settings"))
        }
    }

    @Test
    fun settingsRouteHasNoBarActions() {
        assertTrue(ScreenBar.specsFor(ShellRoute.Settings, hasBudget = true, mode = BudgetMode.ENVELOPE).isEmpty())
    }

    @Test
    fun onboardingPagesKeepOnePrimaryActionInTheBar() {
        fun ids(
            route: ShellRoute,
            developerMode: Boolean = false,
            password: Boolean = true,
        ) = ScreenBar
            .specsFor(route, hasBudget = false, mode = null, developerMode = developerMode, passwordSignIn = password)
            .map { it.id }
        assertTrue(ids(ShellRoute.Welcome).isEmpty())
        assertEquals(listOf("sample-budgets"), ids(ShellRoute.Welcome, developerMode = true))
        assertEquals(listOf("next"), ids(ShellRoute.OnboardingAddress))
        assertEquals(listOf("sign-in"), ids(ShellRoute.OnboardingSignIn))
        assertTrue(ids(ShellRoute.OnboardingSignIn, password = false).isEmpty())
        assertEquals(listOf("new-budget", "refresh"), ids(ShellRoute.OnboardingFiles))
    }
}
