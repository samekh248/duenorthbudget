package app.duenorth.budget

import app.duenorth.budget.core.AssistantCopy
import app.duenorth.budget.core.AutoHoldChoice
import app.duenorth.budget.core.BankLinkCopy
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.EnvelopeCopy
import app.duenorth.budget.core.InboxRow
import app.duenorth.budget.core.MergeCopy
import app.duenorth.budget.core.MonthFillCopy
import app.duenorth.budget.core.MonthFillKind
import app.duenorth.budget.core.RegisterCopy
import app.duenorth.budget.core.ShellCopy
import app.duenorth.budget.core.SignInMethod
import app.duenorth.budget.core.StarterCategoriesCopy
import app.duenorth.budget.core.TemplateCopy
import app.duenorth.budget.core.TransactionMerge
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.ScreenAction

data class ScreenBarSpec(
    val id: String,
    val glyph: AppGlyph,
    val label: String,
    val primary: Boolean = true,
    /** A destructive command: last in the menu, in the accent, under a rule (or set apart on the bar). */
    val apart: Boolean = false,
    /** A menu line that starts a new group under a rule: the shared undo, settings tail. */
    val rule: Boolean = false,
    /** The one line under a bar button: the noun alone, since the glyph is the verb. The menu shows [label]. */
    val caption: String = label,
)

/** The spec as a bar button or menu line that runs [run], keeping its rule and destructive marks. */
fun ScreenBarSpec.action(run: () -> Unit = {}) =
    ScreenAction(id, glyph, label, primary, apart = apart, rule = rule, caption = caption, run = run)

/** The bar's words that name what a button does, so a glyph never has to be guessed at (+ adds, ✓ commits). */
object ScreenBarCopy {
    const val ADD_TRANSACTION = "add transaction"
    const val ADD_BUDGET = "add budget"
    const val ADD_SERVER = "add server"
    const val ADD_RULE = "add rule"
    const val ADD_SCHEDULE = "add schedule"
    const val SAVE = "save"
    const val USE_THESE = "use these"
    const val RECONCILE = "reconcile"
    const val SYNC = "sync"
    const val SYNC_BANK = "sync bank"
    const val IMPORT = "import"

    /**
     * Captions under bar buttons. The glyph is the verb (+ adds, ✓ commits, the pencil edits), so the caption is only
     * the noun, on one line, abbreviated if the word is too long for its slot.
     */
    const val TRANSACTION = "transaction"
    const val BUDGET = "budget"
    const val SERVER = "server"
    const val RULE = "rule"
    const val SCHEDULE = "schedule"
    const val BANK = "bank"
    const val CATEGORIES = "categories"
    const val CLOSE = "close"
}

/** What the inbox's checked rows allow: nothing, categorize and delete, or accept as well when every one has a category. */
enum class InboxPick {
    NONE,
    NEEDS_CATEGORY,
    READY,
    ;

    companion object {
        fun of(
            picked: Set<String>,
            inbox: List<InboxRow>,
        ): InboxPick {
            val rows = inbox.filter { it.id in picked }
            return when {
                rows.isEmpty() -> NONE
                rows.all { it.suggestionId != null } -> READY
                else -> NEEDS_CATEGORY
            }
        }
    }
}

/** Which commands a transaction's details page offers in its `•••` menu (spec 013). */
enum class TransactionMenu {
    NEW,
    ORDINARY,
    TRANSFER,
}

/** What the schedules pivot and a schedule's page need beyond the route. */
data class SchedulesBar(
    val section: Int = SchedulesSection.UPCOMING,
    val calendar: Boolean = false,
    /** False for an ended schedule: nothing left to post or skip. */
    val open: Boolean = true,
)

/** What the settings bar needs beyond the route (spec 012). */
data class SettingsBar(
    val page: SettingsPage = SettingsPage.BUDGET,
    val signedIn: Boolean = false,
    val serverActive: Boolean = false,
)

object ScreenBar {
    /** Home pivot item indexes the bar changes for. */
    val HOME_BUDGET = HomeSections.headers.indexOf(HomeSections.BUDGET)
    val HOME_ACCOUNTS = HomeSections.headers.indexOf(HomeSections.ACCOUNTS)
    val HOME_INBOX = HomeSections.headers.indexOf(HomeSections.INBOX)
    val HOME_ASSISTANT = HomeSections.headers.indexOf(HomeSections.ASSISTANT)
    val HOME_REPORTS = HomeSections.headers.indexOf(HomeSections.REPORTS)

    fun specsFor(
        route: ShellRoute,
        hasBudget: Boolean,
        mode: BudgetMode?,
        undoLabel: String? = null,
        incomeCategory: Boolean = false,
        showClosed: Boolean = false,
        templates: Boolean = false,
        developerMode: Boolean = false,
        passwordSignIn: Boolean = true,
        settings: SettingsBar = SettingsBar(),
        homeSection: Int = HOME_BUDGET,
        showHidden: Boolean = false,
        selecting: Boolean = false,
        inboxSelecting: Boolean = false,
        /** What the checked inbox rows allow, which decides the select bar's buttons. */
        inboxPick: InboxPick = InboxPick.NONE,
        /** The inbox holds no transactions, so there is nothing to select. */
        inboxEmpty: Boolean = false,
        bankLinked: Boolean = false,
        transaction: TransactionMenu = TransactionMenu.NEW,
        budgetEditing: Boolean = false,
        schedules: SchedulesBar = SchedulesBar(),
        accountsReorder: Boolean = false,
        accountClosed: Boolean = false,
        /** The close page is asking again before a force close; the question puts its own answers on the bar. */
        accountForceConfirm: Boolean = false,
        /** The open category's templates are its budget automations (`goal_def`), not lines in its note. */
        categoryAutomations: Boolean = false,
        /** Budget automations are on for the budget, so a category can be switched to them and back, and edited. */
        automationsOn: Boolean = false,
        /** The picked register transactions can be merged: two or more, by Actual's merge rule. */
        registerMerge: Boolean = false,
        /** The checked inbox rows can be merged: two or more of one account and amount. */
        inboxMerge: Boolean = false,
        /** The open category's available is below zero, so its page offers to cover it. */
        overspent: Boolean = false,
    ): List<ScreenBarSpec> =
        when (route) {
            // Spec 012: each settings pivot item has its own bar. Payees has its link on the budget page's manage list.
            ShellRoute.Settings ->
                when (settings.page) {
                    SettingsPage.BUDGET -> listOf(spec("settings-new", AppGlyph.Add, ScreenBarCopy.ADD_BUDGET, ScreenBarCopy.BUDGET))
                    SettingsPage.SERVER ->
                        buildList {
                            add(spec("add-server", AppGlyph.Add, ScreenBarCopy.ADD_SERVER, ScreenBarCopy.SERVER))
                            if (settings.signedIn) {
                                add(
                                    spec("server-budgets", AppGlyph.Budgets, SettingsCopy.SERVER_BUDGETS, SettingsCopy.BUDGETS_CAPTION),
                                )
                            }
                            if (settings.serverActive) add(destructive("sign-out", AppGlyph.Cancel, SettingsCopy.SIGN_OUT))
                        }
                    SettingsPage.ADVANCED -> listOf(spec("export", AppGlyph.Export, SettingsCopy.EXPORT))
                    SettingsPage.FORMAT, SettingsPage.APPEARANCE, SettingsPage.EXPERIMENTAL -> emptyList()
                }
            is ShellRoute.EncryptionPassword -> saveCancel()
            // Spec 011: one primary action per onboarding page, in the bar where the thumb is.
            ShellRoute.Welcome ->
                if (developerMode) listOf(menu("sample-budgets", AppGlyph.Budgets, ShellCopy.SAMPLE_BUDGETS)) + settings() else emptyList()
            ShellRoute.OnboardingAddress -> listOf(spec("next", AppGlyph.ChevronRight, OnboardingScreenCopy.NEXT))
            ShellRoute.OnboardingSignIn ->
                if (passwordSignIn) listOf(spec("sign-in", AppGlyph.ChevronRight, OnboardingScreenCopy.SIGN_IN)) else emptyList()
            ShellRoute.OnboardingFiles ->
                listOf(
                    spec("new-budget", AppGlyph.Add, ScreenBarCopy.ADD_BUDGET, ScreenBarCopy.BUDGET),
                    spec("refresh", AppGlyph.Sync, OnboardingScreenCopy.REFRESH),
                )
            // Every home pivot is a hub: add transaction first, then its own buttons; its menu holds its own
            // tools, then the same undo, settings tail under a rule. Syncing lives in the panel a pull down opens.
            ShellRoute.Home -> {
                val tail = hubTail(undoLabel)
                when {
                    !hasBudget -> settings()
                    // Edit mode is a sandbox: save keeps what changed, cancel drops it. The month fills and
                    // templates live here and fill its boxes; undo would write straight away, so it waits.
                    homeSection == HOME_BUDGET && budgetEditing ->
                        listOf(
                            spec("end-budget-edit", AppGlyph.Save, ScreenBarCopy.SAVE, ScreenBarCopy.BUDGET),
                            spec("cancel-budget-edit", AppGlyph.Cancel, BudgetEditCopy.CANCEL),
                        ) + fills() + templates(templates)
                    // Select mode swaps the inbox's bar for what can be done to the checked rows: accept only while
                    // every checked row has a category, cancel last, and delete always last in the menu.
                    homeSection == HOME_INBOX && inboxSelecting ->
                        buildList {
                            if (inboxPick == InboxPick.READY) add(spec("accept-selected", AppGlyph.Save, InboxCopy.ACCEPT))
                            if (inboxPick != InboxPick.NONE) add(spec("categorize-selected", AppGlyph.Categories, InboxCopy.CATEGORIZE))
                            if (inboxMerge) add(spec("merge-inbox-selected", AppGlyph.Merge, MergeCopy.MERGE))
                            add(spec("end-inbox-selecting", AppGlyph.Cancel, InboxCopy.CANCEL))
                            if (inboxPick != InboxPick.NONE) add(destructive("delete-selected", AppGlyph.Delete, InboxCopy.DELETE))
                        }
                    // Rules and payees live in settings › budget › manage, with the budget's other lists.
                    homeSection == HOME_INBOX ->
                        buildList {
                            add(addTransaction())
                            if (!inboxEmpty) add(spec("select-inbox", AppGlyph.Select, InboxCopy.SELECT, InboxCopy.BATCH))
                        } + tail
                    // Reorder happens in place on the accounts list; done is the only way on.
                    homeSection == HOME_ACCOUNTS && accountsReorder ->
                        listOf(spec("reorder", AppGlyph.Save, AccountScreenCopy.DONE))
                    homeSection == HOME_ACCOUNTS ->
                        listOf(
                            addTransaction(),
                            spec("schedules", AppGlyph.Calendar, ScheduleScreenCopy.SCHEDULES),
                            menu("add-account", AppGlyph.Add, AccountScreenCopy.ADD),
                            menu("reorder", AppGlyph.Categories, AccountScreenCopy.REORDER),
                            menu(
                                "show-closed",
                                AppGlyph.Categories,
                                if (showClosed) AccountScreenCopy.HIDE_CLOSED else AccountScreenCopy.SHOW_CLOSED,
                            ),
                        ) + tail
                    // Reports has no tools of its own on the bar; it keeps add and the tail.
                    homeSection == HOME_REPORTS -> listOf(addTransaction()) + tail
                    // The assistant fixes things from its own panels; its bar refreshes the checks.
                    homeSection == HOME_ASSISTANT ->
                        listOf(addTransaction(), spec("refresh-assistant", AppGlyph.Sync, AssistantCopy.REFRESH)) + tail
                    // Add a transaction and edit budget are the budget's buttons; hold heads the menu.
                    else ->
                        listOf(addTransaction()) + editBudget() + (if (mode == BudgetMode.TRACKING) emptyList() else hold()) +
                            hidden(showHidden) + categories() + templates(templates) + tail
                }
            }
            // Select mode swaps the register's bar for what can be done to the picked transactions.
            is ShellRoute.Register ->
                if (selecting) {
                    listOfNotNull(
                        spec("clear-selected", AppGlyph.Save, RegisterCopy.CLEAR),
                        spec("unclear-selected", AppGlyph.Undo, RegisterCopy.UNCLEAR),
                        spec("merge-selected", AppGlyph.Merge, MergeCopy.MERGE).takeIf { registerMerge },
                        spec("end-selecting", AppGlyph.Cancel, InboxCopy.CANCEL),
                    )
                } else {
                    // An object page: its verbs on the bar, the rest in the menu, then undo and settings, close last.
                    buildList {
                        add(addTransaction())
                        add(spec("search", AppGlyph.Search, RegisterCopy.SEARCH))
                        add(spec("reconcile", AppGlyph.Reconcile, ScreenBarCopy.RECONCILE))
                        if (bankLinked) add(spec("sync", AppGlyph.Sync, ScreenBarCopy.SYNC_BANK, ScreenBarCopy.BANK))
                        add(menu("import", AppGlyph.Import, ScreenBarCopy.IMPORT))
                        add(menu("bank-link", AppGlyph.Sync, BankLinkCopy.TITLE))
                        add(menu("edit-account", AppGlyph.Settings, AccountScreenCopy.EDIT))
                        if (accountClosed) add(menu("reopen", AppGlyph.Undo, AccountScreenCopy.REOPEN))
                        addAll(objectTail(undoLabel))
                        if (!accountClosed) add(destructive("close", AppGlyph.Cancel, AccountScreenCopy.CLOSE))
                    }
                }
            ShellRoute.AddAccount,
            is ShellRoute.EditAccount,
            -> saveCancel()
            // Use these takes the checked groups; making them by hand is the categories page, in the menu.
            ShellRoute.StarterCategories ->
                listOf(
                    spec("save", AppGlyph.Save, ScreenBarCopy.USE_THESE, ScreenBarCopy.CATEGORIES),
                    spec("cancel", AppGlyph.Cancel, "cancel"),
                    menu("categories", AppGlyph.Categories, StarterCategoriesCopy.OWN),
                )
            // Close account is the page's save; force close stays on the page, under its warning.
            is ShellRoute.CloseAccount ->
                if (accountForceConfirm) {
                    emptyList()
                } else {
                    listOf(
                        spec("save", AppGlyph.Save, AccountScreenCopy.CLOSE, ScreenBarCopy.CLOSE),
                        spec("cancel", AppGlyph.Cancel, "cancel"),
                    )
                }
            ShellRoute.Rules -> listOf(spec("add-rule", AppGlyph.Add, ScreenBarCopy.ADD_RULE, ScreenBarCopy.RULE)) + objectTail(undoLabel)
            // The failed sync's page: retry runs every linked account again, dismiss clears the tile.
            ShellRoute.BankSyncReport ->
                listOf(
                    spec("bank-sync", AppGlyph.Sync, BankSyncCopy.RETRY),
                    spec("bank-sync-dismiss", AppGlyph.Cancel, BankSyncCopy.DISMISS),
                )
            // Upcoming switches between its list and its calendar; all has nothing to switch.
            ShellRoute.Schedules ->
                listOf(spec("add-schedule", AppGlyph.Add, ScreenBarCopy.ADD_SCHEDULE, ScreenBarCopy.SCHEDULE)) +
                    if (schedules.section == SchedulesSection.UPCOMING) {
                        listOf(
                            if (schedules.calendar) {
                                spec("schedules-view", AppGlyph.Breakdown, ScheduleScreenCopy.LIST)
                            } else {
                                spec("schedules-view", AppGlyph.Calendar, ScheduleScreenCopy.CALENDAR)
                            },
                        )
                    } else {
                        emptyList()
                    } + objectTail(undoLabel)
            is ShellRoute.ScheduleDetails ->
                buildList {
                    if (schedules.open) {
                        add(spec("post-schedule", AppGlyph.Save, ScheduleScreenCopy.POST))
                        add(spec("skip-schedule", AppGlyph.ChevronRight, ScheduleScreenCopy.SKIP))
                    }
                    add(spec("edit-schedule", AppGlyph.Edit, ScheduleScreenCopy.EDIT_ACTION))
                    addAll(objectTail(undoLabel))
                    add(destructive("delete-schedule", AppGlyph.Delete, ScheduleScreenCopy.DELETE))
                }
            // Forms: save and cancel on the bar, delete alone in the menu, and no way off to settings.
            is ShellRoute.ScheduleEditor ->
                saveCancel() +
                    if (route.scheduleId ==
                        null
                    ) {
                        emptyList()
                    } else {
                        listOf(destructive("delete-schedule", AppGlyph.Delete, ScheduleScreenCopy.DELETE))
                    }
            // A template's form saves from the bar; one already in the note can also be removed there.
            is ShellRoute.TemplateEditor ->
                saveCancel() +
                    if (route.index == null) {
                        emptyList()
                    } else {
                        listOf(destructive("remove-template", AppGlyph.Delete, TemplateEditorCopy.REMOVE))
                    }
            // Picking a new kind for a line already in the note can also remove that line.
            is ShellRoute.TemplateKinds ->
                listOf(spec("cancel", AppGlyph.Cancel, "cancel")) +
                    if (route.index == null) {
                        emptyList()
                    } else {
                        listOf(destructive("remove-template", AppGlyph.Delete, TemplateEditorCopy.REMOVE_LINE))
                    }
            is ShellRoute.RuleEditor ->
                saveCancel() +
                    if (route.ruleId == null) emptyList() else listOf(destructive("delete-rule", AppGlyph.Delete, RuleScreenCopy.DELETE))
            // Move sits with save, and cover beside it while the category is overspent; the rest in the menu, add
            // template first; rollover is a switch on the page.
            // Rename is last in the menu on both kinds of category page.
            is ShellRoute.CategoryBudget ->
                if (incomeCategory) {
                    (if (mode == BudgetMode.TRACKING) saveCancel() else autoHold()) + renameCategory()
                } else {
                    buildList {
                        addAll(saveCancel())
                        add(spec("move", AppGlyph.Transfer, EnvelopeCopy.MOVE))
                        if (overspent && mode != BudgetMode.TRACKING) {
                            add(spec("cover", AppGlyph.Cover, EnvelopeCopy.COVER, EnvelopeCopy.COVER_CAPTION))
                        }
                        if (templates &&
                            (!categoryAutomations || automationsOn)
                        ) {
                            add(menu("add-template", AppGlyph.Add, TemplateEditorCopy.ADD))
                        }
                        if (templates && automationsOn) {
                            if (categoryAutomations) {
                                add(menu("to-notes", AppGlyph.Cancel, AutomationCopy.TO_NOTES))
                            } else {
                                add(menu("to-automations", AppGlyph.Average, AutomationCopy.TO_AUTOMATIONS))
                            }
                        }
                        if (mode != BudgetMode.TRACKING) add(menu("to-budget", AppGlyph.Transfer, MonthActionCopy.TRANSFER))
                        addAll(renameCategory())
                    }
                }
            ShellRoute.Create -> saveCancel()
            // Splitting starts in the form, under the category box, so the bar keeps to save and cancel.
            is ShellRoute.EditTransaction ->
                saveCancel() +
                    when (transaction) {
                        TransactionMenu.NEW -> emptyList()
                        TransactionMenu.ORDINARY ->
                            listOf(
                                menu("schedule-transaction", AppGlyph.Calendar, RegisterCopy.SCHEDULE),
                                destructive("delete-transaction", AppGlyph.Delete, RegisterCopy.DELETE),
                            )
                        TransactionMenu.TRANSFER -> listOf(destructive("delete-transaction", AppGlyph.Delete, RegisterCopy.DELETE))
                    }
            ShellRoute.HoldMonth,
            is ShellRoute.ToBudget,
            is ShellRoute.MoveCategory,
            is ShellRoute.CoverCategory,
            is ShellRoute.ImportPreviewRoute,
            is ShellRoute.Reconcile,
            ShellRoute.BudgetPassword,
            -> saveCancel()
            else -> emptyList()
        }

    fun forState(
        state: ShellUiState,
        handlers: ScreenBarHandlers,
    ): List<ScreenAction> {
        val mode = state.shell?.mode
        val hasBudget = state.shell != null && !state.locked
        val income =
            (state.route as? ShellRoute.CategoryBudget)?.let { route -> state.shell?.incomeCategory(route.categoryId) != null } == true
        return specsFor(
            state.route,
            hasBudget,
            mode,
            state.undoLabel,
            income,
            state.settings.showClosedAccounts,
            state.formatPrefs.templatesOn,
            state.developerMode,
            state.onboarding
                ?.progress
                ?.methods
                ?.contains(SignInMethod.PASSWORD) != false,
            SettingsBar(
                page = SettingsPage.visible(state.settings.openBudgetId != null, state.settingsPage),
                signedIn = state.signedIn,
                serverActive = state.activeInstance != null,
            ),
            state.homeSection,
            state.settings.showHiddenCategories,
            state.registerUi.selection != null,
            state.inbox.selection != null,
            state.inbox.selection?.let { InboxPick.of(it, state.shell?.inbox.orEmpty()) } ?: InboxPick.NONE,
            state.shell?.inbox.isNullOrEmpty(),
            state.registerBankLinked,
            transactionMenu(state),
            state.budgetEdit != null,
            SchedulesBar(
                section = state.schedulesUi.section,
                calendar = state.schedulesUi.calendar,
                open =
                    state.schedulesUi.details
                        ?.summary
                        ?.completed != true,
            ),
            state.accounts.reorder,
            state.register
                ?.account
                ?.closed == true,
            state.accounts.forceConfirm != null,
            categoryAutomations = state.templates.automations != null,
            automationsOn = state.formatPrefs.automationsOn,
            registerMerge = registerMerge(state),
            inboxMerge = inboxMerge(state),
            overspent = overspent(state),
        ).map { spec ->
            ScreenAction(
                id = spec.id,
                glyph = spec.glyph,
                label = spec.label,
                primary = spec.primary,
                apart = spec.apart,
                rule = spec.rule,
                caption = spec.caption,
                spinning = spec.glyph == AppGlyph.Sync && spec.primary && syncRunning(spec.id, state),
                run = handlers.bind(spec.id, state),
            )
        }
    }

    /** Whether the open category page's category has spent more than it has, so cover belongs on its bar. */
    internal fun overspent(state: ShellUiState): Boolean {
        val route = state.route as? ShellRoute.CategoryBudget ?: return false
        val category =
            state
                .editingShell()
                ?.groups
                ?.flatMap { it.categories }
                ?.firstOrNull { it.id == route.categoryId }
        return (category?.availableMinor ?: 0L) < 0L
    }

    /** Whether the register's picked transactions can be merged, by desktop Actual's rule. */
    internal fun registerMerge(state: ShellUiState): Boolean {
        val picked = state.registerUi.selection ?: return false
        val rows =
            state.register
                ?.rows
                .orEmpty()
                .filter { it.id in picked }
        return TransactionMerge.canMerge(TransactionMerge.ofRegister(rows))
    }

    /** Whether the inbox's checked rows can be merged: two or more of one account and amount. */
    internal fun inboxMerge(state: ShellUiState): Boolean {
        val picked = state.inbox.selection ?: return false
        val rows =
            state.shell
                ?.inbox
                .orEmpty()
                .filter { it.id in picked }
        return TransactionMerge.canMerge(TransactionMerge.ofInbox(rows))
    }

    /** Whether the work a sync button starts is running now: its glyph turns only while it is. */
    internal fun syncRunning(
        id: String,
        state: ShellUiState,
    ): Boolean =
        when (id) {
            "sync" -> state.import.fetchPending || state.bankSync.syncing
            "bank-sync" -> state.bankSync.syncing
            "refresh" -> state.remoteRefreshing
            "refresh-assistant" -> state.assistant.refreshing || state.syncing
            else -> false
        }

    private fun transactionMenu(state: ShellUiState): TransactionMenu {
        val route = state.route as? ShellRoute.EditTransaction ?: return TransactionMenu.NEW
        val id = route.transactionId ?: return TransactionMenu.NEW
        val row = state.register?.rows?.firstOrNull { it.id == id } ?: return TransactionMenu.NEW
        return if (row.transferId != null) TransactionMenu.TRANSFER else TransactionMenu.ORDINARY
    }

    /** Add a transaction: the full form, on the register's account or, from home, the first one. First on every hub. */
    private fun addTransaction() = spec("add", AppGlyph.Add, ScreenBarCopy.ADD_TRANSACTION, ScreenBarCopy.TRANSACTION)

    private fun saveCancel() =
        listOf(
            spec("save", AppGlyph.Save, "save"),
            spec("cancel", AppGlyph.Cancel, "cancel"),
        )

    /** An envelope income category's auto-hold choices, which apply at once; there is nothing to save. */
    private fun autoHold() =
        listOf(
            menu("auto-hold-on", AppGlyph.Hold, "${MonthFillCopy.AUTO_HOLD} ${IncomeCopy.HOLD_ON}"),
            menu("auto-hold-off-month", AppGlyph.Hold, "${MonthFillCopy.AUTO_HOLD} ${IncomeCopy.HOLD_OFF_MONTH}"),
            menu("auto-hold-off", AppGlyph.Hold, "${MonthFillCopy.AUTO_HOLD} ${IncomeCopy.HOLD_OFF}"),
        )

    /** A category's page `•••` → rename, which opens the name over the month for typing. */
    private fun renameCategory() = listOf(menu("rename-category", AppGlyph.Edit, EnvelopeCopy.RENAME))

    /** Edit budget sits on the budget's bar, right of add. */
    private fun editBudget() = listOf(spec("edit-budget", AppGlyph.Edit, BudgetEditCopy.EDIT, ScreenBarCopy.BUDGET))

    /** Home `•••` → hold money for next month, envelope budgets only. */
    private fun hold() = listOf(menu("hold", AppGlyph.Hold, "hold"))

    /** Home `•••` → the categories page. */
    private fun categories() = listOf(menu("categories", AppGlyph.Categories, "categories"))

    /** Home `•••` → bring hidden groups and categories back into the month, or leave them out again. */
    private fun hidden(shown: Boolean) =
        listOf(menu("show-hidden", AppGlyph.Categories, if (shown) ShellCopy.HIDE_HIDDEN else ShellCopy.SHOW_HIDDEN))

    /** Desktop's month menu: copy last month, 3 month average, set to zero. */
    private fun fills() =
        listOf(
            menu("copy-last", AppGlyph.Copy, MonthFillCopy.COPY_LAST),
            menu("average", AppGlyph.Average, MonthFillCopy.AVERAGE),
            menu("zero", AppGlyph.Zero, MonthFillCopy.ZERO),
        )

    /** Home `•••` → templates, only while experimental features and budget templates are both on (spec 010 US5). */
    private fun templates(on: Boolean) = if (on) listOf(menu("templates", AppGlyph.Average, TemplateCopy.TITLE)) else emptyList()

    /** Every home `•••` ends with settings, as WP8.1 apps put it last in the menu. */
    private fun settings() = listOf(menu("settings", AppGlyph.Settings, "settings"))

    /**
     * The tail every hub's menu ends with, in this order under a rule: undo while there is a step to undo, settings.
     * Sync is not here: on home a pull down opens the sync panel, with sync now and bank sync.
     */
    private fun hubTail(undoLabel: String?): List<ScreenBarSpec> {
        val lines = undo(undoLabel) + settings()
        return lines.mapIndexed { i, line -> if (i == 0) line.copy(rule = true) else line }
    }

    /** An object page's menu ends with undo and settings under a rule; its destructive line, if any, follows. */
    private fun objectTail(undoLabel: String?): List<ScreenBarSpec> = hubTail(undoLabel)

    /** A destructive command: always the last line of the menu, in the accent and under a rule. */
    private fun destructive(
        id: String,
        glyph: AppGlyph,
        label: String,
    ) = ScreenBarSpec(id, glyph, label, primary = false, apart = true)

    /** Present only while there is a step to undo; the label names it. */
    private fun undo(label: String?) =
        if (label == null) emptyList() else listOf(menu("undo", AppGlyph.Undo, "${MonthActionCopy.UNDO} $label"))

    private fun menu(
        id: String,
        glyph: AppGlyph,
        label: String,
    ) = ScreenBarSpec(id, glyph, label, primary = false)

    private fun spec(
        id: String,
        glyph: AppGlyph,
        label: String,
        caption: String = label,
    ) = ScreenBarSpec(id, glyph, label, primary = true, caption = caption)
}

class ScreenBarHandlers(
    val onAdd: () -> Unit,
    val onHold: () -> Unit,
    val onCategories: () -> Unit,
    val onSettings: () -> Unit,
    val onReconcile: () -> Unit,
    val onImport: () -> Unit,
    val onBankSync: () -> Unit = {},
    val onBankSyncAll: () -> Unit = {},
    val onDismissBankSync: () -> Unit = {},
    val onSave: () -> Unit,
    val onCancel: () -> Unit,
    val onFill: (MonthFillKind) -> Unit = {},
    val onToBudget: () -> Unit = {},
    val onUndo: () -> Unit = {},
    val onAddAccount: () -> Unit = {},
    val onShowClosed: () -> Unit = {},
    val onShowHidden: () -> Unit = {},
    val onReorder: () -> Unit = {},
    val onEditAccount: () -> Unit = {},
    val onCloseAccount: () -> Unit = {},
    val onReopenAccount: () -> Unit = {},
    val onAddSchedule: () -> Unit = {},
    val onAddRule: () -> Unit = {},
    val onDeleteSchedule: () -> Unit = {},
    val onDeleteRule: () -> Unit = {},
    val onSchedules: () -> Unit = {},
    val onSchedulesView: () -> Unit = {},
    val onPostSchedule: () -> Unit = {},
    val onSkipSchedule: () -> Unit = {},
    val onEditSchedule: () -> Unit = {},
    val onTemplates: () -> Unit = {},
    val onBankLink: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onSamples: () -> Unit = {},
    val onNewBudget: () -> Unit = {},
    val onSettingsNew: () -> Unit = {},
    val onSettingsPayees: () -> Unit = {},
    val onAddServer: () -> Unit = {},
    val onServerBudgets: () -> Unit = {},
    val onSignOut: () -> Unit = {},
    val onExport: () -> Unit = {},
    val onClearSelected: () -> Unit = {},
    val onUnclearSelected: () -> Unit = {},
    val onEndSelecting: () -> Unit = {},
    val onSelectInbox: () -> Unit = {},
    val onAcceptSelected: () -> Unit = {},
    val onCategorizeSelected: () -> Unit = {},
    val onEndInboxSelecting: () -> Unit = {},
    val onDeleteSelected: () -> Unit = {},
    val onMergeSelected: () -> Unit = {},
    val onMergeInboxSelected: () -> Unit = {},
    val onSyncServer: () -> Unit = {},
    val onRefreshAssistant: () -> Unit = {},
    val onScheduleTransaction: () -> Unit = {},
    val onDeleteTransaction: () -> Unit = {},
    val onEditBudget: () -> Unit = {},
    val onEndBudgetEdit: () -> Unit = {},
    val onCancelBudgetEdit: () -> Unit = {},
    val onSearch: () -> Unit = {},
    val onRemoveTemplate: () -> Unit = {},
    val onAddTemplate: () -> Unit = {},
    val onToAutomations: () -> Unit = {},
    val onToNotes: () -> Unit = {},
    val onMoveCategory: () -> Unit = {},
    val onCoverCategory: () -> Unit = {},
    val onRenameCategory: () -> Unit = {},
    val onAutoHold: (AutoHoldChoice) -> Unit = {},
) {
    fun bind(
        id: String,
        state: ShellUiState,
    ): () -> Unit =
        when (id) {
            "add" -> onAdd
            "hold" -> onHold
            "categories" -> onCategories
            "settings" -> onSettings
            "reconcile" -> onReconcile
            "search" -> onSearch
            "import" -> onImport
            "sync" -> onBankSync
            "bank-sync" -> onBankSyncAll
            "bank-sync-dismiss" -> onDismissBankSync
            "save" -> onSave
            "cancel" -> onCancel
            "copy-last" -> ({ onFill(MonthFillKind.COPY_LAST) })
            "average" -> ({ onFill(MonthFillKind.AVERAGE) })
            "zero" -> ({ onFill(MonthFillKind.ZERO) })
            "to-budget" -> onToBudget
            "undo" -> onUndo
            "add-account" -> onAddAccount
            "show-closed" -> onShowClosed
            "show-hidden" -> onShowHidden
            "reorder" -> onReorder
            "edit-account" -> onEditAccount
            "close" -> onCloseAccount
            "reopen" -> onReopenAccount
            "add-schedule" -> onAddSchedule
            "add-rule" -> onAddRule
            "delete-schedule" -> onDeleteSchedule
            "delete-rule" -> onDeleteRule
            "schedules" -> onSchedules
            "schedules-view" -> onSchedulesView
            "post-schedule" -> onPostSchedule
            "skip-schedule" -> onSkipSchedule
            "edit-schedule" -> onEditSchedule
            "templates" -> onTemplates
            "bank-link" -> onBankLink
            "next" -> onNext
            "sign-in" -> onSave
            "refresh" -> onRefresh
            "sample-budgets" -> onSamples
            "new-budget" -> onNewBudget
            "settings-new" -> onSettingsNew
            "settings-payees" -> onSettingsPayees
            "add-server" -> onAddServer
            "server-budgets" -> onServerBudgets
            "sign-out" -> onSignOut
            "export" -> onExport
            "clear-selected" -> onClearSelected
            "unclear-selected" -> onUnclearSelected
            "end-selecting" -> onEndSelecting
            "select-inbox" -> onSelectInbox
            "sync-server" -> onSyncServer
            "refresh-assistant" -> onRefreshAssistant
            "accept-selected" -> onAcceptSelected
            "categorize-selected" -> onCategorizeSelected
            "end-inbox-selecting" -> onEndInboxSelecting
            "delete-selected" -> onDeleteSelected
            "merge-selected" -> onMergeSelected
            "merge-inbox-selected" -> onMergeInboxSelected
            "schedule-transaction" -> onScheduleTransaction
            "delete-transaction" -> onDeleteTransaction
            "edit-budget" -> onEditBudget
            "end-budget-edit" -> onEndBudgetEdit
            "cancel-budget-edit" -> onCancelBudgetEdit
            "remove-template" -> onRemoveTemplate
            "add-template" -> onAddTemplate
            "to-automations" -> onToAutomations
            "to-notes" -> onToNotes
            "move" -> onMoveCategory
            "cover" -> onCoverCategory
            "rename-category" -> onRenameCategory
            "auto-hold-on" -> ({ onAutoHold(AutoHoldChoice.ON) })
            "auto-hold-off-month" -> ({ onAutoHold(AutoHoldChoice.OFF_THIS_MONTH) })
            "auto-hold-off" -> ({ onAutoHold(AutoHoldChoice.OFF) })
            else -> ({})
        }
}
