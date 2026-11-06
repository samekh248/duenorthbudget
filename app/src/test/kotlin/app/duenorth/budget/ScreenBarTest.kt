package app.duenorth.budget

import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.EnvelopeCopy
import app.duenorth.budget.core.InboxRow
import app.duenorth.budget.core.ShellCopy
import app.duenorth.budget.core.TemplateKind
import app.duenorth.budget.design.components.AppGlyph
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
            listOf("hold", "show-hidden", "categories", "settings"),
            specs.filter { !it.primary }.map { it.id },
        )
        // Add names what it adds, on every hub.
        assertEquals(ScreenBarCopy.ADD_TRANSACTION, specs.first().label)
    }

    // Every hub's first button is add transaction, and every hub's menu ends with the same tail.
    @Test
    fun everyHubStartsWithAddAndEndsWithTheSameTail() {
        val hubs =
            listOf(ScreenBar.HOME_INBOX, ScreenBar.HOME_BUDGET, ScreenBar.HOME_ACCOUNTS, ScreenBar.HOME_REPORTS, ScreenBar.HOME_ASSISTANT)
        hubs.forEach { section ->
            val specs =
                ScreenBar.specsFor(
                    ShellRoute.Home,
                    hasBudget = true,
                    mode = BudgetMode.ENVELOPE,
                    undoLabel = "assign",
                    homeSection = section,
                )
            assertEquals(section.toString(), "add", specs.first().id)
            val menu = specs.filterNot { it.primary }
            assertEquals(section.toString(), listOf("undo", "settings"), menu.takeLast(2).map { it.id })
            assertTrue(section.toString(), menu.takeLast(2).first().rule)
        }
    }

    // Reports has its own bar: add and the tail, none of the budget's tools.
    @Test
    fun reportsHasItsOwnBar() {
        val ids =
            ScreenBar
                .specsFor(ShellRoute.Home, hasBudget = true, mode = BudgetMode.ENVELOPE, homeSection = ScreenBar.HOME_REPORTS)
                .map { it.id }
        assertEquals(listOf("add", "settings"), ids)
    }

    // Schedules live on the accounts pivot item: its bar opens them, and the budget's menu no longer adds or lists them.
    @Test
    fun accountsBarOpensSchedules() {
        val specs = ScreenBar.specsFor(ShellRoute.Home, hasBudget = true, mode = BudgetMode.ENVELOPE, homeSection = ScreenBar.HOME_ACCOUNTS)
        assertEquals(listOf("add", "schedules"), specs.filter { it.primary }.map { it.id })
        assertEquals("schedules", specs.single { it.id == "schedules" }.label)
    }

    // No hub offers sync or bank sync on its bar or in its menu: a pull down opens the sync panel instead.
    @Test
    fun noHubOffersSync() {
        val hubs =
            listOf(ScreenBar.HOME_INBOX, ScreenBar.HOME_BUDGET, ScreenBar.HOME_ACCOUNTS, ScreenBar.HOME_REPORTS, ScreenBar.HOME_ASSISTANT)
        hubs.forEach { section ->
            val specs = ScreenBar.specsFor(ShellRoute.Home, hasBudget = true, mode = BudgetMode.ENVELOPE, homeSection = section)
            assertFalse(section.toString(), specs.any { it.id in setOf("sync-all", "sync", "bank-sync", "sync-server") })
        }
    }

    // The failed sync's page only dismisses: syncing again is the home pull-down panel's job.
    @Test
    fun bankSyncReportBarOnlyDismisses() {
        val specs = ScreenBar.specsFor(ShellRoute.BankSyncReport, hasBudget = true, mode = BudgetMode.ENVELOPE)
        assertEquals(listOf("bank-sync-dismiss"), specs.map { it.id })
        assertEquals(listOf("dismiss"), specs.map { it.label })
    }

    // A sync button turns only while its own sync is running.
    @Test
    fun syncButtonsSpinOnlyWhileSyncing() {
        val idle = ShellUiState()
        listOf("refresh", "refresh-assistant").forEach { assertFalse(ScreenBar.syncRunning(it, idle)) }
        assertTrue(ScreenBar.syncRunning("refresh-assistant", idle.copy(syncing = true)))
        assertTrue(ScreenBar.syncRunning("refresh", idle.copy(remoteRefreshing = true)))
        assertFalse(ScreenBar.syncRunning("add", idle.copy(bankSync = BankSyncState(syncing = true))))
    }

    // Upcoming switches between its list and calendar; all has nothing to switch.
    @Test
    fun schedulesBarSwitchesUpcomingView() {
        val list = ScreenBar.specsFor(ShellRoute.Schedules, hasBudget = true, mode = BudgetMode.ENVELOPE)
        assertEquals(listOf("add-schedule", "schedules-view", "settings"), list.map { it.id })
        assertEquals(ScreenBarCopy.ADD_SCHEDULE, list.first().label)
        assertEquals("calendar", list[1].label)
        val calendar =
            ScreenBar.specsFor(
                ShellRoute.Schedules,
                hasBudget = true,
                mode = BudgetMode.ENVELOPE,
                schedules = SchedulesBar(calendar = true),
            )
        assertEquals("list", calendar[1].label)
        val all =
            ScreenBar.specsFor(
                ShellRoute.Schedules,
                hasBudget = true,
                mode = BudgetMode.ENVELOPE,
                schedules = SchedulesBar(section = SchedulesSection.ALL),
            )
        assertEquals(listOf("add-schedule", "settings"), all.map { it.id })
    }

    // A schedule's page: post, skip and edit on the bar, delete in the menu; an ended one only edits.
    @Test
    fun scheduleDetailsBar() {
        val open = ScreenBar.specsFor(ShellRoute.ScheduleDetails("s1"), hasBudget = true, mode = BudgetMode.ENVELOPE)
        assertEquals(listOf("post-schedule", "skip-schedule", "edit-schedule"), open.filter { it.primary }.map { it.id })
        // Delete is last, after the tail, and set apart.
        assertEquals(listOf("settings", "delete-schedule"), open.filter { !it.primary }.map { it.id })
        assertTrue(open.last().apart)
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
        // A mode: no settings in its menu, and its commit says save.
        assertEquals(listOf("copy-last", "average", "zero", "templates"), specs.filter { !it.primary }.map { it.id })
        assertEquals(ScreenBarCopy.SAVE, specs.first().label)
        // The budget's own menu carries templates too, but not the fills.
        val home = ScreenBar.specsFor(ShellRoute.Home, hasBudget = true, mode = BudgetMode.ENVELOPE, templates = true).map { it.id }
        assertTrue(home.none { it in setOf("copy-last", "average", "zero") })
        assertTrue(home.contains("templates"))
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
        assertEquals(listOf("add", "select-inbox"), inbox.filter { it.primary }.map { it.id })
        // Rules and payees live in settings, so the inbox menu is just the hub tail.
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
        // Delete is always the menu's last line, set apart, never a button.
        assertTrue(delete.apart && !delete.primary)
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
    fun inboxMenuKeepsSettingsLast() {
        val specs =
            ScreenBar.specsFor(
                ShellRoute.Home,
                hasBudget = true,
                mode = BudgetMode.ENVELOPE,
                undoLabel = "accept",
                homeSection = ScreenBar.HOME_INBOX,
            )
        assertEquals(listOf("undo", "settings"), specs.filter { !it.primary }.map { it.id }.takeLast(2))
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
        assertEquals(listOf("add", "search", "reconcile", "import", "bank-link", "edit-account", "settings", "close"), ids)
    }

    @Test
    fun registerImportIsInTheMenu() {
        val specs = ScreenBar.specsFor(ShellRoute.Register("a"), hasBudget = true, mode = BudgetMode.ENVELOPE)
        assertEquals(listOf("add", "search", "reconcile"), specs.filter { it.primary }.map { it.id })
        // Reconcile has its own glyph, so it never reads as save's check.
        assertEquals(AppGlyph.Reconcile, specs.single { it.id == "reconcile" }.glyph)
        assertTrue(specs.single { it.id == "close" }.apart)
    }

    // A linked account's page has no bank sync of its own: that is the home pull-down panel's.
    @Test
    fun registerHasNoBankSync() {
        val specs = ScreenBar.specsFor(ShellRoute.Register("a"), hasBudget = true, mode = BudgetMode.ENVELOPE)
        assertEquals(listOf("add", "search", "reconcile"), specs.filter { it.primary }.map { it.id })
        assertEquals(listOf("import", "bank-link", "edit-account", "settings", "close"), specs.filterNot { it.primary }.map { it.id })
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
    fun registerSelectModeOffersMergeWhenThePickedRowsCanMerge() {
        val ids =
            ScreenBar
                .specsFor(ShellRoute.Register("a"), hasBudget = true, mode = BudgetMode.ENVELOPE, selecting = true, registerMerge = true)
                .map { it.id }
        assertEquals(listOf("clear-selected", "unclear-selected", "merge-selected", "end-selecting"), ids)
    }

    @Test
    fun inboxSelectModeOffersMergeWhenTheCheckedRowsCanMerge() {
        val ids =
            ScreenBar
                .specsFor(
                    ShellRoute.Home,
                    hasBudget = true,
                    mode = BudgetMode.ENVELOPE,
                    homeSection = ScreenBar.HOME_INBOX,
                    inboxSelecting = true,
                    inboxPick = InboxPick.READY,
                    inboxMerge = true,
                ).map { it.id }
        assertEquals(
            listOf("accept-selected", "categorize-selected", "merge-inbox-selected", "end-inbox-selecting", "delete-selected"),
            ids,
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
                    inboxMerge = true,
                ).single { it.id == "delete-selected" }
        assertFalse(delete.primary)
    }

    @Test
    fun envelopeIncomeCategoryHasNoSave() {
        val income = ScreenBar.specsFor(ShellRoute.CategoryBudget("c"), true, BudgetMode.ENVELOPE, incomeCategory = true)
        assertEquals(listOf("auto-hold-on", "auto-hold-off-month", "auto-hold-off", "rename-category"), income.map { it.id })
        assertTrue(income.none { it.primary })
        val tracking = ScreenBar.specsFor(ShellRoute.CategoryBudget("c"), true, BudgetMode.TRACKING, incomeCategory = true)
        assertEquals(listOf("save", "cancel", "rename-category"), tracking.map { it.id })
    }

    @Test
    fun categoryBudgetPutsItsButtonsOnTheBar() {
        val specs = ScreenBar.specsFor(ShellRoute.CategoryBudget("c"), true, BudgetMode.ENVELOPE)
        assertEquals(listOf("save", "cancel", "move"), specs.filter { it.primary }.map { it.id })
        // Rollover is a switch on the page now, not a menu item.
        assertEquals(listOf("to-budget", "rename-category"), specs.filterNot { it.primary }.map { it.id })
        // With templates on, add template heads the menu; the page itself has no add button.
        val templates = ScreenBar.specsFor(ShellRoute.CategoryBudget("c"), true, BudgetMode.ENVELOPE, templates = true)
        assertEquals(listOf("add-template", "to-budget", "rename-category"), templates.filterNot { it.primary }.map { it.id })
    }

    @Test
    fun categoryPageMenuEndsWithRename() {
        val rename = ScreenBar.specsFor(ShellRoute.CategoryBudget("c"), true, BudgetMode.ENVELOPE).single { it.id == "rename-category" }
        assertEquals(EnvelopeCopy.RENAME, rename.label)
        assertEquals(AppGlyph.Edit, rename.glyph)
        assertFalse(rename.primary)
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
                ShellRoute.HoldMonth,
                ShellRoute.CategoryBudget("c"),
                ShellRoute.ImportPreviewRoute("a"),
                ShellRoute.Reconcile("a"),
                ShellRoute.BudgetPassword,
            )
        routes.forEach { route ->
            val ids = ScreenBar.specsFor(route, hasBudget = true, mode = BudgetMode.ENVELOPE).map { it.id }
            assertEquals(route.toString(), listOf("save", "cancel"), ids.take(2))
            assertTrue(route.toString(), ids.drop(2).all { it in setOf("to-budget", "move", "rename-category") })
            assertFalse(ids.contains("hold"))
            // Forms never lead off to settings, where a half-filled form would be lost.
            assertFalse(route.toString(), ids.contains("settings"))
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
        // Payees has its link on the page's manage list, so the bar only adds a budget.
        assertEquals(listOf("settings-new"), ids(SettingsPage.BUDGET))
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
        assertEquals(listOf("next"), ids(ShellRoute.OnboardingAddress))
        assertEquals(listOf("sign-in"), ids(ShellRoute.OnboardingSignIn))
        assertTrue(ids(ShellRoute.OnboardingSignIn, password = false).isEmpty())
        assertEquals(listOf("new-budget", "refresh"), ids(ShellRoute.OnboardingFiles))
        assertEquals(listOf("sample-budgets", "settings"), ids(ShellRoute.Welcome, developerMode = true))
    }

    @Test
    fun transactionCommandsFollowTheTransactionKind() {
        fun menu(kind: TransactionMenu) =
            ScreenBar
                .specsFor(ShellRoute.EditTransaction("a", "t"), hasBudget = true, mode = BudgetMode.ENVELOPE, transaction = kind)
        // Splitting starts under the category box, so no transaction puts it on the bar.
        assertEquals(listOf("save", "cancel"), menu(TransactionMenu.ORDINARY).filter { it.primary }.map { it.id })
        assertEquals(listOf("save", "cancel"), menu(TransactionMenu.NEW).filter { it.primary }.map { it.id })
        assertEquals(listOf("save", "cancel"), menu(TransactionMenu.TRANSFER).filter { it.primary }.map { it.id })
        assertEquals(
            listOf("schedule-transaction", "delete-transaction"),
            menu(TransactionMenu.ORDINARY).filter { !it.primary }.map { it.id },
        )
        assertEquals(listOf("delete-transaction"), menu(TransactionMenu.TRANSFER).filter { !it.primary }.map { it.id })
        assertTrue(menu(TransactionMenu.ORDINARY).last().apart)
        assertTrue(menu(TransactionMenu.NEW).none { !it.primary })
    }

    // Hubs and object pages end their menu with settings (an object page's destructive line follows it); forms
    // and modes never offer it; settings' own pages need no way to themselves.
    @Test
    fun settingsEndsHubAndObjectMenusButNeverForms() {
        val objects =
            listOf(ShellRoute.Welcome, ShellRoute.Register("a"), ShellRoute.Rules, ShellRoute.Schedules, ShellRoute.ScheduleDetails("s1"))
        objects.forEach { route ->
            val specs =
                ScreenBar.specsFor(route, hasBudget = true, mode = BudgetMode.ENVELOPE, undoLabel = "assign", developerMode = true)
            val menu = specs.filterNot { it.primary }
            assertEquals(route.toString(), "settings", menu.lastOrNull { !it.apart }?.id)
            assertEquals(route.toString(), 1, specs.count { it.id == "settings" })
            assertTrue(route.toString(), menu.dropWhile { !it.apart }.all { it.apart })
        }
        val forms =
            listOf(
                ShellRoute.ScheduleEditor("s1"),
                ShellRoute.RuleEditor("r1"),
                ShellRoute.TemplateEditor("c", 0, TemplateKind.entries.first()),
                ShellRoute.CategoryBudget("c"),
                ShellRoute.EditTransaction("a", "t"),
            )
        forms.forEach { route ->
            val specs =
                ScreenBar.specsFor(
                    route,
                    hasBudget = true,
                    mode = BudgetMode.ENVELOPE,
                    undoLabel = "assign",
                    transaction = TransactionMenu.ORDINARY,
                )
            assertFalse(route.toString(), specs.any { it.id == "settings" })
            // Destructive lines are always last.
            assertTrue(route.toString(), specs.dropWhile { !it.apart }.all { it.apart })
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

    // + always starts something new and names it; ✓ always commits and never says add.
    @Test
    fun addAndSaveGlyphsKeepOneMeaning() {
        val routes =
            listOf(
                ShellRoute.Home,
                ShellRoute.Register("a"),
                ShellRoute.Rules,
                ShellRoute.Schedules,
                ShellRoute.StarterCategories,
                ShellRoute.OnboardingFiles,
                ShellRoute.Settings,
            )
        routes.forEach { route ->
            ScreenBar.specsFor(route, hasBudget = true, mode = BudgetMode.ENVELOPE).filter { it.primary }.forEach { spec ->
                if (spec.glyph == AppGlyph.Add) assertTrue(spec.label, spec.label.startsWith("add "))
                if (spec.glyph == AppGlyph.Save) assertFalse(spec.label, spec.label.startsWith("add"))
            }
        }
        val server = ScreenBar.specsFor(ShellRoute.Settings, true, BudgetMode.ENVELOPE, settings = SettingsBar(SettingsPage.SERVER))
        assertEquals(ScreenBarCopy.ADD_SERVER, server.single { it.id == "add-server" }.label)
    }

    @Test
    fun buttonCaptionsAreOneNounUnderTheGlyphsVerb() {
        val routes =
            listOf(
                ShellRoute.Home,
                ShellRoute.Register("a"),
                ShellRoute.Rules,
                ShellRoute.Schedules,
                ShellRoute.StarterCategories,
                ShellRoute.OnboardingFiles,
                ShellRoute.Settings,
            )
        val specs =
            routes.flatMap { ScreenBar.specsFor(it, hasBudget = true, mode = BudgetMode.ENVELOPE) } +
                ScreenBar.specsFor(ShellRoute.Settings, true, BudgetMode.ENVELOPE, settings = SettingsBar(SettingsPage.SERVER))
        specs.filter { it.primary }.forEach { spec ->
            // One word, so the caption never wraps; + is already "add", so the caption never says it again.
            assertFalse(spec.caption, ' ' in spec.caption)
            assertFalse(spec.caption, spec.caption.startsWith("add"))
        }
        assertEquals(ScreenBarCopy.TRANSACTION, specs.first { it.id == "add" }.caption)
        // The menu and the screen reader keep the whole command.
        assertEquals(ScreenBarCopy.ADD_TRANSACTION, specs.first { it.id == "add" }.label)
    }
}
