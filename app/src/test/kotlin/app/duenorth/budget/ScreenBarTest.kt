package app.duenorth.budget

import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.InboxRow
import app.duenorth.budget.core.ShellCopy
import app.duenorth.budget.core.TemplateKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenBarTest {
    @Test
    fun homeEnvelopeActions() {
        val specs = ScreenBar.specsFor(ShellRoute.Home, hasBudget = true, mode = BudgetMode.ENVELOPE)
        assertEquals(listOf("add", "edit-budget"), specs.filter { it.primary }.map { it.id })
        assertEquals(
            listOf("hold", "categories", "show-hidden", "rules", "settings"),
            specs.filter { !it.primary }.map { it.id },
        )
    }

    // Schedules live on the accounts pivot item: its bar opens them, and the budget's menu no longer adds or lists them.
    @Test
    fun accountsBarOpensSchedules() {
        val specs = ScreenBar.specsFor(ShellRoute.Home, hasBudget = true, mode = BudgetMode.ENVELOPE, homeSection = ScreenBar.HOME_ACCOUNTS)
        assertEquals(listOf("add", "schedules"), specs.filter { it.primary }.map { it.id })
        assertEquals("schedules", specs.single { it.id == "schedules" }.label)
    }

    // A SimpleFIN-linked budget on a server gets a sync button beside add on the accounts bar.
    @Test
    fun accountsBarSyncsBanksWhenLinked() {
        val specs =
            ScreenBar.specsFor(
                ShellRoute.Home,
                hasBudget = true,
                mode = BudgetMode.ENVELOPE,
                homeSection = ScreenBar.HOME_ACCOUNTS,
                accountsBankSync = true,
            )
        assertEquals(listOf("add", "bank-sync", "schedules"), specs.filter { it.primary }.map { it.id })
        assertEquals("sync", specs.single { it.id == "bank-sync" }.label)
    }

    // The failed sync's page retries every linked account, and dismiss clears the tile.
    @Test
    fun bankSyncReportBarRetriesAndDismisses() {
        val specs = ScreenBar.specsFor(ShellRoute.BankSyncReport, hasBudget = true, mode = BudgetMode.ENVELOPE)
        assertEquals(listOf("bank-sync", "bank-sync-dismiss"), specs.map { it.id })
        assertEquals(listOf("retry", "dismiss"), specs.map { it.label })
    }

    // A sync button turns only while its own sync is running.
    @Test
    fun syncButtonsSpinOnlyWhileSyncing() {
        val idle = ShellUiState()
        listOf("sync", "bank-sync", "refresh").forEach { assertFalse(ScreenBar.syncRunning(it, idle)) }
        assertTrue(ScreenBar.syncRunning("sync", idle.copy(importFetchPending = true)))
        assertTrue(ScreenBar.syncRunning("bank-sync", idle.copy(bankSyncing = true)))
        assertFalse(ScreenBar.syncRunning("bank-sync", idle.copy(importFetchPending = true)))
        assertTrue(ScreenBar.syncRunning("refresh", idle.copy(remoteRefreshing = true)))
        assertFalse(ScreenBar.syncRunning("add", idle.copy(bankSyncing = true)))
    }

    // Upcoming switches between its list and calendar; all has nothing to switch.
    @Test
    fun schedulesBarSwitchesUpcomingView() {
        val list = ScreenBar.specsFor(ShellRoute.Schedules, hasBudget = true, mode = BudgetMode.ENVELOPE)
        assertEquals(listOf("add-schedule", "schedules-view"), list.map { it.id })
        assertEquals("calendar", list.last().label)
        val calendar =
            ScreenBar.specsFor(
                ShellRoute.Schedules,
                hasBudget = true,
                mode = BudgetMode.ENVELOPE,
                schedules = SchedulesBar(calendar = true),
            )
        assertEquals("list", calendar.last().label)
        val all =
            ScreenBar.specsFor(
                ShellRoute.Schedules,
                hasBudget = true,
                mode = BudgetMode.ENVELOPE,
                schedules = SchedulesBar(section = SchedulesSection.ALL),
            )
        assertEquals(listOf("add-schedule"), all.map { it.id })
    }

    // A schedule's page: post, skip and edit on the bar, delete in the menu; an ended one only edits.
    @Test
    fun scheduleDetailsBar() {
        val open = ScreenBar.specsFor(ShellRoute.ScheduleDetails("s1"), hasBudget = true, mode = BudgetMode.ENVELOPE)
        assertEquals(listOf("post-schedule", "skip-schedule", "edit-schedule"), open.filter { it.primary }.map { it.id })
        assertEquals(listOf("delete-schedule", "settings"), open.filter { !it.primary }.map { it.id })
        val ended =
            ScreenBar.specsFor(
                ShellRoute.ScheduleDetails("s1"),
                hasBudget = true,
                mode = BudgetMode.ENVELOPE,
                schedules = SchedulesBar(open = false),
            )
        assertEquals(listOf("edit-schedule"), ended.filter { it.primary }.map { it.id })
    }

    // Edit mode: done and cancel on the bar, the month fills in the menu, and settings last like every menu.
    @Test
    fun budgetEditModeBar() {
        val specs =
            ScreenBar.specsFor(
                ShellRoute.Home,
                hasBudget = true,
                mode = BudgetMode.ENVELOPE,
                undoLabel = "assign",
                budgetEditing = true,
                templates = true,
            )
        // A sandbox: done and cancel, with the fills and templates in its menu; undo would write straight away.
        assertEquals(listOf("end-budget-edit", "cancel-budget-edit"), specs.filter { it.primary }.map { it.id })
        assertEquals(listOf("copy-last", "average", "zero", "templates", "settings"), specs.filter { !it.primary }.map { it.id })
        // The budget's own menu no longer carries them.
        val home = ScreenBar.specsFor(ShellRoute.Home, hasBudget = true, mode = BudgetMode.ENVELOPE, templates = true).map { it.id }
        assertTrue(home.none { it in setOf("copy-last", "average", "zero", "templates") })
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
        assertEquals(listOf("add", "schedules"), specs.filter { it.primary }.map { it.id })
        assertEquals(listOf("add-account", "reorder", "show-closed", "settings"), specs.filter { !it.primary }.map { it.id })
    }

    @Test
    fun homeInboxSectionOffersSelectAndSelectModeActsOnTheCheckedRows() {
        val inbox = ScreenBar.specsFor(ShellRoute.Home, hasBudget = true, mode = BudgetMode.ENVELOPE, homeSection = ScreenBar.HOME_INBOX)
        assertEquals(listOf("select-inbox", "add"), inbox.filter { it.primary }.map { it.id })
        assertEquals(listOf("settings"), inbox.filter { !it.primary }.map { it.id })
        val empty =
            ScreenBar.specsFor(
                ShellRoute.Home,
                hasBudget = true,
                mode = BudgetMode.ENVELOPE,
                homeSection = ScreenBar.HOME_INBOX,
                inboxEmpty = true,
            )
        assertEquals(listOf("add"), empty.filter { it.primary }.map { it.id })
        val selecting =
            ScreenBar.specsFor(
                ShellRoute.Home,
                hasBudget = true,
                mode = BudgetMode.ENVELOPE,
                homeSection = ScreenBar.HOME_INBOX,
                inboxSelecting = true,
            )
        assertEquals(listOf("end-inbox-selecting"), selecting.map { it.id })
    }

    @Test
    fun inboxSelectBarFollowsTheCheckedRows() {
        fun ids(pick: InboxPick) =
            ScreenBar
                .specsFor(
                    ShellRoute.Home,
                    hasBudget = true,
                    mode = BudgetMode.ENVELOPE,
                    homeSection = ScreenBar.HOME_INBOX,
                    inboxSelecting = true,
                    inboxPick = pick,
                ).map { it.id }
        assertEquals(listOf("end-inbox-selecting"), ids(InboxPick.NONE))
        assertEquals(listOf("categorize-selected", "end-inbox-selecting", "delete-selected"), ids(InboxPick.NEEDS_CATEGORY))
        assertEquals(
            listOf("accept-selected", "categorize-selected", "end-inbox-selecting", "delete-selected"),
            ids(InboxPick.READY),
        )
        val delete =
            ScreenBar
                .specsFor(
                    ShellRoute.Home,
                    hasBudget = true,
                    mode = BudgetMode.ENVELOPE,
                    homeSection = ScreenBar.HOME_INBOX,
                    inboxSelecting = true,
                    inboxPick = InboxPick.READY,
                ).single { it.id == "delete-selected" }
        assertTrue(delete.apart && delete.primary)
    }

    @Test
    fun inboxPickReadsTheCheckedRowsCategories() {
        val ready = InboxRow("a", "A", -100, 20261015, "visa", "Visa", "", "c-1", "Dining")
        val bare = InboxRow("b", "B", -100, 20261015, "visa", "Visa", "")
        assertEquals(InboxPick.NONE, InboxPick.of(emptySet(), listOf(ready, bare)))
        assertEquals(InboxPick.READY, InboxPick.of(setOf("a"), listOf(ready, bare)))
        assertEquals(InboxPick.NEEDS_CATEGORY, InboxPick.of(setOf("a", "b"), listOf(ready, bare)))
    }

    @Test
    fun inboxMenuSyncsOnlyABudgetOnTheServerAndKeepsSettingsLast() {
        val local = ScreenBar.specsFor(ShellRoute.Home, hasBudget = true, mode = BudgetMode.ENVELOPE, homeSection = ScreenBar.HOME_INBOX)
        assertFalse(local.any { it.id == "sync-server" })
        val synced =
            ScreenBar.specsFor(
                ShellRoute.Home,
                hasBudget = true,
                mode = BudgetMode.ENVELOPE,
                undoLabel = "accept",
                homeSection = ScreenBar.HOME_INBOX,
                inboxSync = true,
            )
        assertEquals(listOf("sync-server", "undo", "settings"), synced.filter { !it.primary }.map { it.id })
        assertEquals(InboxCopy.SYNC, synced.first { it.id == "sync-server" }.label)
        val selecting =
            ScreenBar.specsFor(
                ShellRoute.Home,
                hasBudget = true,
                mode = BudgetMode.ENVELOPE,
                homeSection = ScreenBar.HOME_INBOX,
                inboxSelecting = true,
                inboxSync = true,
            )
        assertFalse(selecting.any { it.id == "sync-server" })
    }

    @Test
    fun homeTrackingOmitsHold() {
        val specs = ScreenBar.specsFor(ShellRoute.Home, hasBudget = true, mode = BudgetMode.TRACKING)
        val ids = specs.map { it.id }
        assertEquals(listOf("add", "edit-budget"), specs.filter { it.primary }.map { it.id })
        assertTrue(ids.contains("categories"))
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
            listOf(ScreenBar.HOME_BUDGET, ScreenBar.HOME_ACCOUNTS, ScreenBar.HOME_INBOX).forEach { section ->
                val specs = ScreenBar.specsFor(ShellRoute.Home, true, mode, undoLabel = "assign", homeSection = section)
                assertEquals("settings", specs.last().id)
                assertFalse(specs.last().primary)
            }
        }
    }

    @Test
    fun registerActions() {
        val ids = ScreenBar.specsFor(ShellRoute.Register("a"), hasBudget = true, mode = BudgetMode.ENVELOPE).map { it.id }
        assertEquals(listOf("search", "add", "reconcile", "import", "edit-account", "bank-link", "close", "settings"), ids)
    }

    @Test
    fun registerImportIsInTheMenu() {
        val specs = ScreenBar.specsFor(ShellRoute.Register("a"), hasBudget = true, mode = BudgetMode.ENVELOPE)
        assertEquals(listOf("search", "add", "reconcile"), specs.filter { it.primary }.map { it.id })
    }

    @Test
    fun bankLinkedRegisterSyncsFromTheBar() {
        val specs = ScreenBar.specsFor(ShellRoute.Register("a"), hasBudget = true, mode = BudgetMode.ENVELOPE, bankLinked = true)
        assertEquals(listOf("search", "add", "reconcile", "sync"), specs.filter { it.primary }.map { it.id })
        assertEquals(listOf("import", "edit-account", "bank-link", "close", "settings"), specs.filterNot { it.primary }.map { it.id })
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
        assertEquals(listOf("auto-hold-on", "auto-hold-off-month", "auto-hold-off", "settings"), income.map { it.id })
        assertTrue(income.none { it.primary })
        val tracking = ScreenBar.specsFor(ShellRoute.CategoryBudget("c"), true, BudgetMode.TRACKING, incomeCategory = true)
        assertEquals(listOf("save", "cancel"), tracking.map { it.id })
    }

    @Test
    fun categoryBudgetPutsItsButtonsOnTheBar() {
        val specs = ScreenBar.specsFor(ShellRoute.CategoryBudget("c"), true, BudgetMode.ENVELOPE)
        assertEquals(listOf("save", "cancel", "move"), specs.filter { it.primary }.map { it.id })
        // Rollover is a switch on the page now, not a menu item.
        assertEquals(listOf("to-budget", "settings"), specs.filterNot { it.primary }.map { it.id })
        // With templates on, add template heads the menu; the page itself has no add button.
        val templates = ScreenBar.specsFor(ShellRoute.CategoryBudget("c"), true, BudgetMode.ENVELOPE, templates = true)
        assertEquals(listOf("add-template", "to-budget", "settings"), templates.filterNot { it.primary }.map { it.id })
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
                ShellRoute.HoldMonth,
                ShellRoute.CategoryBudget("c"),
                ShellRoute.ImportPreviewRoute("a"),
                ShellRoute.Reconcile("a"),
                ShellRoute.BudgetPassword,
            )
        routes.forEach { route ->
            val ids = ScreenBar.specsFor(route, hasBudget = true, mode = BudgetMode.ENVELOPE).map { it.id }
            assertEquals(route.toString(), listOf("save", "cancel"), ids.take(2))
            assertTrue(route.toString(), ids.drop(2).all { it in setOf("to-budget", "move", "settings") })
            assertFalse(ids.contains("hold"))
            // A form with a menu ends it with settings, like every menu; one without a menu gains none.
            assertEquals(route.toString(), ids.size > 3, ids.contains("settings"))
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
        assertTrue(ids(SettingsPage.EXPERIMENTAL).isEmpty())
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
        assertEquals(listOf("sample-budgets", "settings"), ids(ShellRoute.Welcome, developerMode = true))
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
        assertEquals(listOf("save", "add-split", "cancel"), menu(TransactionMenu.ORDINARY).filter { it.primary }.map { it.id })
        assertEquals(listOf("save", "add-split", "cancel"), menu(TransactionMenu.NEW).filter { it.primary }.map { it.id })
        assertEquals(listOf("save", "cancel"), menu(TransactionMenu.TRANSFER).filter { it.primary }.map { it.id })
        assertEquals(
            listOf("schedule-transaction", "delete-transaction", "settings"),
            menu(TransactionMenu.ORDINARY).filter { !it.primary }.map { it.id },
        )
        assertEquals(listOf("delete-transaction", "settings"), menu(TransactionMenu.TRANSFER).filter { !it.primary }.map { it.id })
        assertTrue(menu(TransactionMenu.NEW).none { !it.primary })
    }

    // Every `•••` menu outside settings ends with settings, once, whatever else it carries.
    @Test
    fun everyMenuEndsWithSettings() {
        val routes =
            listOf(
                ShellRoute.Welcome,
                ShellRoute.Register("a"),
                ShellRoute.Rules,
                ShellRoute.Schedules,
                ShellRoute.ScheduleDetails("s1"),
                ShellRoute.ScheduleEditor("s1"),
                ShellRoute.RuleEditor("r1"),
                ShellRoute.TemplateEditor("c", 0, TemplateKind.entries.first()),
                ShellRoute.CategoryBudget("c"),
                ShellRoute.EditTransaction("a", "t"),
            )
        routes.forEach { route ->
            val specs =
                ScreenBar.specsFor(
                    route,
                    hasBudget = true,
                    mode = BudgetMode.ENVELOPE,
                    undoLabel = "assign",
                    developerMode = true,
                    transaction = TransactionMenu.ORDINARY,
                )
            assertEquals(route.toString(), "settings", specs.last().id)
            assertFalse(route.toString(), specs.last().primary)
            assertEquals(route.toString(), 1, specs.count { it.id == "settings" })
        }
        SettingsPage.entries.forEach { page ->
            val specs =
                ScreenBar.specsFor(
                    ShellRoute.Settings,
                    hasBudget = true,
                    mode = BudgetMode.ENVELOPE,
                    settings = SettingsBar(page, signedIn = true, serverActive = true),
                )
            assertFalse(page.toString(), specs.any { it.id == "settings" })
        }
    }
}
