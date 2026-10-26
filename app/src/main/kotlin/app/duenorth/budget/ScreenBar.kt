package app.duenorth.budget

import app.duenorth.budget.core.AutoHoldChoice
import app.duenorth.budget.core.BankLinkCopy
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.EnvelopeCopy
import app.duenorth.budget.core.MonthFillCopy
import app.duenorth.budget.core.MonthFillKind
import app.duenorth.budget.core.RegisterCopy
import app.duenorth.budget.core.ShellCopy
import app.duenorth.budget.core.SignInMethod
import app.duenorth.budget.core.TemplateCopy
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.ScreenAction

data class ScreenBarSpec(
    val id: String,
    val glyph: AppGlyph,
    val label: String,
    val primary: Boolean = true,
)

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
        /** The inbox holds no transactions, so there is nothing to select. */
        inboxEmpty: Boolean = false,
        /** The open budget lives on a server this phone is signed in to, so the inbox menu can sync it. */
        inboxSync: Boolean = false,
        bankLinked: Boolean = false,
        transaction: TransactionMenu = TransactionMenu.NEW,
        budgetEditing: Boolean = false,
        schedules: SchedulesBar = SchedulesBar(),
        accountsReorder: Boolean = false,
        accountClosed: Boolean = false,
    ): List<ScreenBarSpec> =
        when (route) {
            // Spec 012: each settings pivot item has its own bar.
            ShellRoute.Settings ->
                when (settings.page) {
                    SettingsPage.BUDGET ->
                        listOf(spec("settings-new", AppGlyph.Add, SettingsCopy.NEW)) +
                            if (hasBudget) listOf(menu("settings-payees", AppGlyph.Payees, SettingsCopy.PAYEES)) else emptyList()
                    SettingsPage.SERVER ->
                        buildList {
                            add(spec("add-server", AppGlyph.Add, SettingsCopy.ADD_SERVER))
                            if (settings.signedIn) add(spec("server-budgets", AppGlyph.Budgets, SettingsCopy.SERVER_BUDGETS))
                            if (settings.serverActive) add(menu("sign-out", AppGlyph.Cancel, SettingsCopy.SIGN_OUT))
                        }
                    SettingsPage.ADVANCED -> listOf(spec("export", AppGlyph.Save, SettingsCopy.EXPORT))
                    SettingsPage.FORMAT, SettingsPage.APPEARANCE, SettingsPage.EXPERIMENTAL -> emptyList()
                }
            is ShellRoute.EncryptionPassword -> saveCancel()
            // Spec 011: one primary action per onboarding page, in the bar where the thumb is.
            ShellRoute.Welcome ->
                if (developerMode) listOf(menu("sample-budgets", AppGlyph.Budgets, ShellCopy.SAMPLE_BUDGETS)) else emptyList()
            ShellRoute.OnboardingAddress -> listOf(spec("next", AppGlyph.ChevronRight, OnboardingScreenCopy.NEXT))
            ShellRoute.OnboardingSignIn ->
                if (passwordSignIn) listOf(spec("sign-in", AppGlyph.ChevronRight, OnboardingScreenCopy.SIGN_IN)) else emptyList()
            ShellRoute.OnboardingFiles ->
                listOf(
                    spec("new-budget", AppGlyph.Add, OnboardingScreenCopy.NEW_BUDGET),
                    spec("refresh", AppGlyph.Sync, OnboardingScreenCopy.REFRESH),
                )
            ShellRoute.Home ->
                when {
                    !hasBudget -> settings()
                    // Edit mode is a sandbox: done saves what changed, cancel drops it. The month fills and
                    // templates live here and fill its boxes; undo would write straight away, so it waits.
                    homeSection == HOME_BUDGET && budgetEditing ->
                        listOf(
                            spec("end-budget-edit", AppGlyph.Save, BudgetEditCopy.DONE),
                            spec("cancel-budget-edit", AppGlyph.Cancel, BudgetEditCopy.CANCEL),
                        ) + fills() + templates(templates)
                    // The accounts pivot item carries account management in its menu, so the month menu does not.
                    // Select mode swaps the inbox's bar for what can be done to the checked rows.
                    homeSection == HOME_INBOX && inboxSelecting ->
                        listOf(
                            spec("accept-selected", AppGlyph.Save, InboxCopy.ACCEPT),
                            spec("categorize-selected", AppGlyph.Categories, InboxCopy.CATEGORIZE),
                            spec("end-inbox-selecting", AppGlyph.Cancel, InboxCopy.CANCEL),
                        )
                    // Add a transaction is always on the inbox bar; select only shows while there is something to check.
                    homeSection == HOME_INBOX ->
                        buildList {
                            if (!inboxEmpty) add(spec("select-inbox", AppGlyph.Select, InboxCopy.SELECT))
                            add(addTransaction())
                        } + serverSync(inboxSync) + undo(undoLabel) + settings()
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
                        ) + undo(undoLabel) + settings()
                    // Add a transaction is the budget's one button; hold and categories follow edit budget in the menu.
                    mode == BudgetMode.TRACKING ->
                        listOf(addTransaction()) + editBudget() + categories() + hidden(showHidden) + rules() +
                            undo(undoLabel) + settings()
                    else ->
                        listOf(addTransaction()) + editBudget() + hold() + categories() + hidden(showHidden) + rules() +
                            undo(undoLabel) + settings()
                }
            // Select mode swaps the register's bar for what can be done to the picked transactions.
            is ShellRoute.Register ->
                if (selecting) {
                    listOf(
                        spec("clear-selected", AppGlyph.Reconcile, RegisterCopy.CLEAR),
                        spec("unclear-selected", AppGlyph.Undo, RegisterCopy.UNCLEAR),
                        spec("end-selecting", AppGlyph.Cancel, RegisterCopy.DONE),
                    )
                } else {
                    // A bank-linked account syncs from the server; import lives in the menu.
                    buildList {
                        add(spec("search", AppGlyph.Search, RegisterCopy.SEARCH))
                        add(addTransaction())
                        add(spec("reconcile", AppGlyph.Reconcile, "reconcile"))
                        if (bankLinked) add(spec("sync", AppGlyph.Sync, "sync"))
                        add(menu("import", AppGlyph.Import, "import"))
                        add(menu("edit-account", AppGlyph.Settings, AccountScreenCopy.EDIT))
                        add(menu("bank-link", AppGlyph.Sync, BankLinkCopy.TITLE))
                        if (accountClosed) {
                            add(menu("reopen", AppGlyph.Undo, AccountScreenCopy.REOPEN))
                        } else {
                            add(menu("close", AppGlyph.Cancel, AccountScreenCopy.CLOSE))
                        }
                    } + undo(undoLabel)
                }
            ShellRoute.AddAccount,
            is ShellRoute.EditAccount,
            -> saveCancel()
            ShellRoute.Rules -> listOf(spec("add-rule", AppGlyph.Add, RuleScreenCopy.ADD)) + undo(undoLabel)
            // Upcoming switches between its list and its calendar; all has nothing to switch.
            ShellRoute.Schedules ->
                listOf(spec("add-schedule", AppGlyph.Add, ScheduleScreenCopy.NEW)) +
                    if (schedules.section == SchedulesSection.UPCOMING) {
                        listOf(
                            if (schedules.calendar) {
                                spec("schedules-view", AppGlyph.Categories, ScheduleScreenCopy.LIST)
                            } else {
                                spec("schedules-view", AppGlyph.Calendar, ScheduleScreenCopy.CALENDAR)
                            },
                        )
                    } else {
                        emptyList()
                    } + undo(undoLabel)
            is ShellRoute.ScheduleDetails ->
                buildList {
                    if (schedules.open) {
                        add(spec("post-schedule", AppGlyph.Save, ScheduleScreenCopy.POST))
                        add(spec("skip-schedule", AppGlyph.ChevronRight, ScheduleScreenCopy.SKIP))
                    }
                    add(spec("edit-schedule", AppGlyph.Settings, ScheduleScreenCopy.EDIT_ACTION))
                    add(menu("delete-schedule", AppGlyph.Cancel, ScheduleScreenCopy.DELETE))
                } + undo(undoLabel)
            is ShellRoute.ScheduleEditor ->
                saveCancel() +
                    if (route.scheduleId ==
                        null
                    ) {
                        emptyList()
                    } else {
                        listOf(menu("delete-schedule", AppGlyph.Cancel, ScheduleScreenCopy.DELETE))
                    }
            // A template's form saves from the bar; one already in the note can also be removed there.
            is ShellRoute.TemplateEditor ->
                saveCancel() +
                    if (route.index == null) emptyList() else listOf(menu("remove-template", AppGlyph.Cancel, TemplateEditorCopy.REMOVE))
            is ShellRoute.TemplateKinds -> listOf(spec("cancel", AppGlyph.Cancel, "cancel"))
            is ShellRoute.RuleEditor ->
                saveCancel() +
                    if (route.ruleId == null) emptyList() else listOf(menu("delete-rule", AppGlyph.Cancel, RuleScreenCopy.DELETE))
            // Move sits with save, the rest in the menu, add template first; rollover is a switch on the page.
            is ShellRoute.CategoryBudget ->
                if (incomeCategory) {
                    if (mode == BudgetMode.TRACKING) saveCancel() else autoHold()
                } else {
                    buildList {
                        addAll(saveCancel())
                        add(spec("move", AppGlyph.Transfer, EnvelopeCopy.MOVE))
                        if (templates) add(menu("add-template", AppGlyph.Add, TemplateEditorCopy.ADD))
                        if (mode != BudgetMode.TRACKING) add(menu("to-budget", AppGlyph.Transfer, MonthActionCopy.TRANSFER))
                    }
                }
            ShellRoute.Create,
            ShellRoute.Assign,
            -> saveCancel()
            is ShellRoute.EditTransaction ->
                when (transaction) {
                    // Add split sits between save and cancel; each tap adds one split line to the form.
                    TransactionMenu.NEW, TransactionMenu.ORDINARY ->
                        listOf(
                            spec("save", AppGlyph.Save, "save"),
                            spec("add-split", AppGlyph.Categories, RegisterCopy.ADD_SPLIT),
                            spec("cancel", AppGlyph.Cancel, "cancel"),
                        )
                    TransactionMenu.TRANSFER -> saveCancel()
                } +
                    when (transaction) {
                        TransactionMenu.NEW -> emptyList()
                        TransactionMenu.ORDINARY ->
                            listOf(
                                menu("schedule-transaction", AppGlyph.Calendar, RegisterCopy.SCHEDULE),
                                menu("delete-transaction", AppGlyph.Cancel, RegisterCopy.DELETE),
                            )
                        TransactionMenu.TRANSFER -> listOf(menu("delete-transaction", AppGlyph.Cancel, RegisterCopy.DELETE))
                    }
            is ShellRoute.SplitTransaction,
            ShellRoute.HoldMonth,
            is ShellRoute.ToBudget,
            is ShellRoute.MoveCategory,
            is ShellRoute.ImportPreviewRoute,
            is ShellRoute.Reconcile,
            ShellRoute.BudgetPassword,
            -> saveCancel()
            else -> emptyList()
        }.let { specs -> settingsLast(route, specs) }

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
            state.registerSelection != null,
            state.inboxSelection != null,
            state.shell?.inbox.isNullOrEmpty(),
            state.signedIn && !state.cloudFileId.isNullOrBlank(),
            state.registerBankLinked,
            transactionMenu(state),
            state.budgetEdit != null,
            SchedulesBar(
                section = state.schedulesSection,
                calendar = state.schedulesCalendar,
                open = state.scheduleDetails?.summary?.completed != true,
            ),
            state.accountsReorder,
            state.register
                ?.account
                ?.closed == true,
        ).map { spec ->
            ScreenAction(
                id = spec.id,
                glyph = spec.glyph,
                label = spec.label,
                primary = spec.primary,
                run = handlers.bind(spec.id, state),
            )
        }
    }

    private fun transactionMenu(state: ShellUiState): TransactionMenu {
        val route = state.route as? ShellRoute.EditTransaction ?: return TransactionMenu.NEW
        val id = route.transactionId ?: return TransactionMenu.NEW
        val row = state.register?.rows?.firstOrNull { it.id == id } ?: return TransactionMenu.NEW
        return if (row.transferId != null) TransactionMenu.TRANSFER else TransactionMenu.ORDINARY
    }

    /** Add a transaction: the full form, on the register's account or, from home, the first one. */
    private fun addTransaction() = spec("add", AppGlyph.Add, "add")

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

    /** Home `•••` → edit budget heads the budget's menu. */
    private fun editBudget() = listOf(menu("edit-budget", AppGlyph.Categories, BudgetEditCopy.EDIT))

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

    /** Home `•••` → rules (spec 010 US3); schedules live on the accounts pivot item. */
    private fun rules() = listOf(menu("rules", AppGlyph.Categories, RuleScreenCopy.TITLE))

    /** Home `•••` → templates, only while experimental features and budget templates are both on (spec 010 US5). */
    private fun templates(on: Boolean) = if (on) listOf(menu("templates", AppGlyph.Average, TemplateCopy.TITLE)) else emptyList()

    /** Inbox `•••` → sync: pulls in what other devices sent without waiting for the background job. */
    private fun serverSync(on: Boolean) = if (on) listOf(menu("sync-server", AppGlyph.Sync, InboxCopy.SYNC)) else emptyList()

    /** Every home `•••` ends with settings, as WP8.1 apps put it last in the menu. */
    private fun settings() = listOf(menu("settings", AppGlyph.Settings, "settings"))

    /** Every other page with a `•••` menu ends it with settings too; settings' own pages need no way to themselves. */
    private fun settingsLast(
        route: ShellRoute,
        specs: List<ScreenBarSpec>,
    ): List<ScreenBarSpec> =
        if (route == ShellRoute.Settings || specs.all { it.primary }) {
            specs
        } else {
            specs.filter { it.id != "settings" } + settings()
        }

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
    ) = ScreenBarSpec(id, glyph, label, primary = true)
}

class ScreenBarHandlers(
    val onAdd: () -> Unit,
    val onHold: () -> Unit,
    val onCategories: () -> Unit,
    val onSettings: () -> Unit,
    val onReconcile: () -> Unit,
    val onImport: () -> Unit,
    val onBankSync: () -> Unit = {},
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
    val onRules: () -> Unit = {},
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
    val onSyncServer: () -> Unit = {},
    val onAddSplit: () -> Unit = {},
    val onScheduleTransaction: () -> Unit = {},
    val onDeleteTransaction: () -> Unit = {},
    val onEditBudget: () -> Unit = {},
    val onEndBudgetEdit: () -> Unit = {},
    val onCancelBudgetEdit: () -> Unit = {},
    val onSearch: () -> Unit = {},
    val onRemoveTemplate: () -> Unit = {},
    val onAddTemplate: () -> Unit = {},
    val onMoveCategory: () -> Unit = {},
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
            "rules" -> onRules
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
            "accept-selected" -> onAcceptSelected
            "categorize-selected" -> onCategorizeSelected
            "end-inbox-selecting" -> onEndInboxSelecting
            "add-split" -> onAddSplit
            "schedule-transaction" -> onScheduleTransaction
            "delete-transaction" -> onDeleteTransaction
            "edit-budget" -> onEditBudget
            "end-budget-edit" -> onEndBudgetEdit
            "cancel-budget-edit" -> onCancelBudgetEdit
            "remove-template" -> onRemoveTemplate
            "add-template" -> onAddTemplate
            "move" -> onMoveCategory
            "auto-hold-on" -> ({ onAutoHold(AutoHoldChoice.ON) })
            "auto-hold-off-month" -> ({ onAutoHold(AutoHoldChoice.OFF_THIS_MONTH) })
            "auto-hold-off" -> ({ onAutoHold(AutoHoldChoice.OFF) })
            else -> ({})
        }
}
