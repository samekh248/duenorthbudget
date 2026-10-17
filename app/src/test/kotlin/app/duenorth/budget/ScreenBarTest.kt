package app.duenorth.budget

import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.ShellCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenBarTest {
    @Test
    fun homeEnvelopeActions() {
        val specs = ScreenBar.specsFor(ShellRoute.Home, hasBudget = true, mode = BudgetMode.ENVELOPE)
        assertEquals(listOf("add", "hold", "categories"), specs.filter { it.primary }.map { it.id })
        assertEquals(
            listOf("show-hidden", "copy-last", "average", "zero", "due", "add-schedule", "rules", "settings"),
            specs.filter { !it.primary }.map { it.id },
        )
    }

    @Test
    fun showHiddenLabelFollowsTheSetting() {
        fun label(shown: Boolean) =
            ScreenBar
                .specsFor(ShellRoute.Home, hasBudget = true, mode = BudgetMode.ENVELOPE, showHidden = shown)
                .single { it.id == "show-hidden" }
                .label
        assertEquals(ShellCopy.SHOW_HIDDEN, label(false))
        assertEquals(ShellCopy.HIDE_HIDDEN, label(true))
    }

    @Test
    fun homeAccountsSectionCarriesAccountActions() {
        val specs =
            ScreenBar.specsFor(
                ShellRoute.Home,
                hasBudget = true,
                mode = BudgetMode.ENVELOPE,
                homeSection = ScreenBar.HOME_ACCOUNTS,
            )
        assertEquals(listOf("add-account", "accounts"), specs.filter { it.primary }.map { it.id })
        assertEquals(listOf("settings"), specs.filter { !it.primary }.map { it.id })
    }

    @Test
    fun homeTrackingOmitsHold() {
        val specs = ScreenBar.specsFor(ShellRoute.Home, hasBudget = true, mode = BudgetMode.TRACKING)
        val ids = specs.map { it.id }
        assertEquals(listOf("add", "categories"), specs.filter { it.primary }.map { it.id })
        assertFalse(ids.contains("hold"))
    }

    @Test
    fun homeWithoutBudgetIsSettingsOnly() {
        val specs = ScreenBar.specsFor(ShellRoute.Home, hasBudget = false, mode = null)
        assertEquals(listOf("settings"), specs.map { it.id })
        assertFalse(specs.single().primary)
    }

    @Test
    fun settingsIsTheLastMenuItemOnHome() {
        listOf(BudgetMode.ENVELOPE, BudgetMode.TRACKING).forEach { mode ->
            listOf(ScreenBar.HOME_BUDGET, ScreenBar.HOME_ACCOUNTS).forEach { section ->
                val specs = ScreenBar.specsFor(ShellRoute.Home, true, mode, undoLabel = "assign", homeSection = section)
                assertEquals("settings", specs.last().id)
                assertFalse(specs.last().primary)
            }
        }
    }

    @Test
    fun registerActions() {
        val ids = ScreenBar.specsFor(ShellRoute.Register("a"), hasBudget = true, mode = BudgetMode.ENVELOPE).map { it.id }
        assertEquals(listOf("add", "reconcile", "import", "edit-account", "bank-link", "close"), ids)
    }

    @Test
    fun registerImportIsInTheMenu() {
        val specs = ScreenBar.specsFor(ShellRoute.Register("a"), hasBudget = true, mode = BudgetMode.ENVELOPE)
        assertEquals(listOf("add", "reconcile"), specs.filter { it.primary }.map { it.id })
    }

    @Test
    fun bankLinkedRegisterSyncsFromTheBar() {
        val specs = ScreenBar.specsFor(ShellRoute.Register("a"), hasBudget = true, mode = BudgetMode.ENVELOPE, bankLinked = true)
        assertEquals(listOf("add", "reconcile", "sync"), specs.filter { it.primary }.map { it.id })
        assertEquals(listOf("import", "edit-account", "bank-link", "close"), specs.filterNot { it.primary }.map { it.id })
    }

    @Test
    fun registerSelectModeSwapsTheBarForClearAndUnclear() {
        val ids =
            ScreenBar
                .specsFor(ShellRoute.Register("a"), hasBudget = true, mode = BudgetMode.ENVELOPE, undoLabel = "assign", selecting = true)
                .map { it.id }
        assertEquals(listOf("clear-selected", "unclear-selected", "end-selecting"), ids)
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
    fun settingsBarFollowsThePivotItem() {
        fun ids(
            page: SettingsPage,
            hasBudget: Boolean = true,
            signedIn: Boolean = false,
            serverActive: Boolean = false,
        ) = ScreenBar
            .specsFor(
                ShellRoute.Settings,
                hasBudget = hasBudget,
                mode = BudgetMode.ENVELOPE,
                settings = SettingsBar(page, signedIn, serverActive),
            ).map { it.id }
        assertEquals(listOf("settings-new", "settings-payees"), ids(SettingsPage.BUDGET))
        assertEquals(listOf("settings-new"), ids(SettingsPage.BUDGET, hasBudget = false))
        assertEquals(listOf("add-server"), ids(SettingsPage.SERVER))
        assertEquals(listOf("add-server", "server-budgets", "sign-out"), ids(SettingsPage.SERVER, signedIn = true, serverActive = true))
        assertTrue(ids(SettingsPage.FORMAT).isEmpty())
        assertTrue(ids(SettingsPage.APPEARANCE).isEmpty())
        assertEquals(listOf("export"), ids(SettingsPage.ADVANCED))
        assertEquals(
            listOf("save", "cancel"),
            ScreenBar
                .specsFor(
                    ShellRoute.EncryptionPassword(EncryptionMode.SET),
                    hasBudget = true,
                    mode = BudgetMode.ENVELOPE,
                ).map { it.id },
        )
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

    @Test
    fun transactionCommandsFollowTheTransactionKind() {
        fun menu(kind: TransactionMenu) =
            ScreenBar
                .specsFor(ShellRoute.EditTransaction("a", "t"), hasBudget = true, mode = BudgetMode.ENVELOPE, transaction = kind)
        assertEquals(listOf("save", "cancel"), menu(TransactionMenu.ORDINARY).filter { it.primary }.map { it.id })
        assertEquals(
            listOf("split-transaction", "schedule-transaction", "delete-transaction"),
            menu(TransactionMenu.ORDINARY).filter { !it.primary }.map { it.id },
        )
        assertEquals(listOf("delete-transaction"), menu(TransactionMenu.TRANSFER).filter { !it.primary }.map { it.id })
        assertTrue(menu(TransactionMenu.NEW).none { !it.primary })
    }
}
