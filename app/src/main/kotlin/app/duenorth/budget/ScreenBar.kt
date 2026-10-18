package app.duenorth.budget

import app.duenorth.budget.core.BankLinkCopy
import app.duenorth.budget.core.BudgetMode
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
        bankLinked: Boolean = false,
        transaction: TransactionMenu = TransactionMenu.NEW,
        budgetEditing: Boolean = false,
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
                    SettingsPage.FORMAT, SettingsPage.APPEARANCE -> emptyList()
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
                    // Edit mode is a sandbox: done saves what changed, cancel drops it. Undo and the month
                    // fills would write straight away, so they wait until it is over.
                    homeSection == HOME_BUDGET && budgetEditing ->
                        listOf(
                            spec("end-budget-edit", AppGlyph.Save, BudgetEditCopy.DONE),
                            spec("cancel-budget-edit", AppGlyph.Cancel, BudgetEditCopy.CANCEL),
                        )
                    // The accounts pivot item carries account management, so the month menu does not.
                    // Select mode swaps the inbox's bar for what can be done to the checked rows.
                    homeSection == HOME_INBOX && inboxSelecting ->
                        listOf(
                            spec("accept-selected", AppGlyph.Save, InboxCopy.ACCEPT),
                            spec("categorize-selected", AppGlyph.Categories, InboxCopy.CATEGORIZE),
                            spec("end-inbox-selecting", AppGlyph.Cancel, InboxCopy.CANCEL),
                        )
                    homeSection == HOME_INBOX ->
                        listOf(
                            spec("add", AppGlyph.Add, "add"),
                            spec("select-inbox", AppGlyph.Select, InboxCopy.SELECT),
                        ) + undo(undoLabel) + settings()
                    homeSection == HOME_ACCOUNTS ->
                        listOf(
                            spec("add-account", AppGlyph.Add, AccountScreenCopy.ADD),
                            spec("accounts", AppGlyph.Budgets, AccountScreenCopy.MANAGE),
                        ) + undo(undoLabel) + settings()
                    mode == BudgetMode.TRACKING ->
                        listOf(
                            spec("add", AppGlyph.Add, "add"),
                            spec("categories", AppGlyph.Categories, "categories"),
                        ) + editBudget() + hidden(showHidden) + fills() + schedules() + templates(templates) + undo(undoLabel) +
                            settings()
                    else ->
                        listOf(
                            spec("add", AppGlyph.Add, "add"),
                            spec("hold", AppGlyph.Hold, "hold"),
                            spec("categories", AppGlyph.Categories, "categories"),
                        ) + editBudget() + hidden(showHidden) + fills() + schedules() + templates(templates) + undo(undoLabel) +
                            settings()
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
                        add(spec("add", AppGlyph.Add, "add"))
                        add(spec("reconcile", AppGlyph.Reconcile, "reconcile"))
                        if (bankLinked) add(spec("sync", AppGlyph.Sync, "sync"))
                        add(menu("import", AppGlyph.Import, "import"))
                        add(menu("edit-account", AppGlyph.Settings, AccountScreenCopy.EDIT))
                        add(menu("bank-link", AppGlyph.Sync, BankLinkCopy.TITLE))
                        add(menu("close", AppGlyph.Cancel, AccountScreenCopy.CLOSE))
                    } + undo(undoLabel)
                }
            ShellRoute.Accounts ->
                listOf(
                    spec("add-account", AppGlyph.Add, AccountScreenCopy.ADD),
                    menu(
                        "show-closed",
                        AppGlyph.Categories,
                        if (showClosed) AccountScreenCopy.HIDE_CLOSED else AccountScreenCopy.SHOW_CLOSED,
                    ),
                    menu("reorder", AppGlyph.Categories, AccountScreenCopy.REORDER),
                ) + undo(undoLabel)
            ShellRoute.AddAccount,
            is ShellRoute.EditAccount,
            -> saveCancel()
            ShellRoute.Rules -> listOf(spec("add-rule", AppGlyph.Add, RuleScreenCopy.ADD)) + undo(undoLabel)
            is ShellRoute.ScheduleEditor ->
                saveCancel() +
                    if (route.scheduleId ==
                        null
                    ) {
                        emptyList()
                    } else {
                        listOf(menu("delete-schedule", AppGlyph.Cancel, ScheduleScreenCopy.DELETE))
                    }
            is ShellRoute.RuleEditor ->
                saveCancel() +
                    if (route.ruleId == null) emptyList() else listOf(menu("delete-rule", AppGlyph.Cancel, RuleScreenCopy.DELETE))
            is ShellRoute.CategoryBudget ->
                if (mode == BudgetMode.TRACKING) {
                    saveCancel()
                } else if (incomeCategory) {
                    // Auto-hold choices apply at once; there is nothing to save.
                    emptyList()
                } else {
                    saveCancel() + menu("to-budget", AppGlyph.Transfer, MonthActionCopy.TRANSFER)
                }
            ShellRoute.Create,
            ShellRoute.Assign,
            is ShellRoute.EditTransaction,
            ->
                saveCancel() +
                    when (transaction) {
                        TransactionMenu.NEW -> emptyList()
                        TransactionMenu.ORDINARY ->
                            listOf(
                                menu("split-transaction", AppGlyph.Categories, RegisterCopy.SPLIT),
                                menu("schedule-transaction", AppGlyph.Calendar, RegisterCopy.SCHEDULE),
                                menu("delete-transaction", AppGlyph.Cancel, RegisterCopy.DELETE),
                            )
                        TransactionMenu.TRANSFER -> listOf(menu("delete-transaction", AppGlyph.Cancel, RegisterCopy.DELETE))
                    }
            ShellRoute.AddTransaction,
            is ShellRoute.SplitTransaction,
            is ShellRoute.TransferMoney,
            ShellRoute.HoldMonth,
            is ShellRoute.ToBudget,
            is ShellRoute.MoveCategory,
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
            state.registerSelection != null,
            state.inboxSelection != null,
            state.registerBankLinked,
            transactionMenu(state),
            state.budgetEdit != null,
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

    private fun saveCancel() =
        listOf(
            spec("save", AppGlyph.Save, "save"),
            spec("cancel", AppGlyph.Cancel, "cancel"),
        )

    /** Home `•••` → edit budget heads the budget's menu. */
    private fun editBudget() = listOf(menu("edit-budget", AppGlyph.Categories, BudgetEditCopy.EDIT))

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

    /** Home `•••` → what is due, new schedule, and rules (spec 010 US3). */
    private fun schedules() =
        listOf(
            menu("due", AppGlyph.Calendar, ScheduleScreenCopy.DUE),
            menu("add-schedule", AppGlyph.Add, ScheduleScreenCopy.ADD),
            menu("rules", AppGlyph.Categories, RuleScreenCopy.TITLE),
        )

    /** Home `•••` → templates, only while experimental features and budget templates are both on (spec 010 US5). */
    private fun templates(on: Boolean) = if (on) listOf(menu("templates", AppGlyph.Average, TemplateCopy.TITLE)) else emptyList()

    /** Every home `•••` ends with settings, as WP8.1 apps put it last in the menu. */
    private fun settings() = listOf(menu("settings", AppGlyph.Settings, "settings"))

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
    val onTransfer: () -> Unit,
    val onReconcile: () -> Unit,
    val onImport: () -> Unit,
    val onBankSync: () -> Unit = {},
    val onSave: () -> Unit,
    val onCancel: () -> Unit,
    val onFill: (MonthFillKind) -> Unit = {},
    val onToBudget: () -> Unit = {},
    val onUndo: () -> Unit = {},
    val onAccounts: () -> Unit = {},
    val onAddAccount: () -> Unit = {},
    val onShowClosed: () -> Unit = {},
    val onShowHidden: () -> Unit = {},
    val onReorder: () -> Unit = {},
    val onEditAccount: () -> Unit = {},
    val onCloseAccount: () -> Unit = {},
    val onAddSchedule: () -> Unit = {},
    val onRules: () -> Unit = {},
    val onAddRule: () -> Unit = {},
    val onDeleteSchedule: () -> Unit = {},
    val onDeleteRule: () -> Unit = {},
    val onDue: () -> Unit = {},
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
    val onSplitTransaction: () -> Unit = {},
    val onScheduleTransaction: () -> Unit = {},
    val onDeleteTransaction: () -> Unit = {},
    val onEditBudget: () -> Unit = {},
    val onEndBudgetEdit: () -> Unit = {},
    val onCancelBudgetEdit: () -> Unit = {},
) {
    fun bind(
        id: String,
        state: ShellUiState,
    ): () -> Unit =
        when (id) {
            "add" ->
                when (state.route) {
                    is ShellRoute.Register -> onAdd
                    else -> onAdd
                }
            "hold" -> onHold
            "categories" -> onCategories
            "settings" -> onSettings
            "transfer" -> onTransfer
            "reconcile" -> onReconcile
            "import" -> onImport
            "sync" -> onBankSync
            "save" -> onSave
            "cancel" -> onCancel
            "copy-last" -> ({ onFill(MonthFillKind.COPY_LAST) })
            "average" -> ({ onFill(MonthFillKind.AVERAGE) })
            "zero" -> ({ onFill(MonthFillKind.ZERO) })
            "to-budget" -> onToBudget
            "undo" -> onUndo
            "accounts" -> onAccounts
            "add-account" -> onAddAccount
            "show-closed" -> onShowClosed
            "show-hidden" -> onShowHidden
            "reorder" -> onReorder
            "edit-account" -> onEditAccount
            "close" -> onCloseAccount
            "add-schedule" -> onAddSchedule
            "rules" -> onRules
            "add-rule" -> onAddRule
            "delete-schedule" -> onDeleteSchedule
            "delete-rule" -> onDeleteRule
            "due" -> onDue
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
            "accept-selected" -> onAcceptSelected
            "categorize-selected" -> onCategorizeSelected
            "end-inbox-selecting" -> onEndInboxSelecting
            "split-transaction" -> onSplitTransaction
            "schedule-transaction" -> onScheduleTransaction
            "delete-transaction" -> onDeleteTransaction
            "edit-budget" -> onEditBudget
            "end-budget-edit" -> onEndBudgetEdit
            "cancel-budget-edit" -> onCancelBudgetEdit
            else -> ({})
        }
}
