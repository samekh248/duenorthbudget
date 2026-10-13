package app.duenorth.budget

import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.MonthFillCopy
import app.duenorth.budget.core.MonthFillKind
import app.duenorth.budget.core.ReportsCopy
import app.duenorth.budget.core.TemplateCopy
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.ScreenAction

data class ScreenBarSpec(
    val id: String,
    val glyph: AppGlyph,
    val label: String,
    val primary: Boolean = true,
)

object ScreenBar {
    fun specsFor(
        route: ShellRoute,
        hasBudget: Boolean,
        mode: BudgetMode?,
        undoLabel: String? = null,
        incomeCategory: Boolean = false,
        showClosed: Boolean = false,
        templates: Boolean = false,
    ): List<ScreenBarSpec> =
        when (route) {
            ShellRoute.Home ->
                when {
                    !hasBudget -> listOf(spec("settings", AppGlyph.Settings, "settings"))
                    mode == BudgetMode.TRACKING ->
                        listOf(
                            spec("add", AppGlyph.Add, "add"),
                            spec("categories", AppGlyph.Categories, "categories"),
                            spec("settings", AppGlyph.Settings, "settings"),
                        ) + fills() + accounts() + schedules() + templates(templates) + undo(undoLabel)
                    else ->
                        listOf(
                            spec("add", AppGlyph.Add, "add"),
                            spec("hold", AppGlyph.Hold, "hold"),
                            spec("categories", AppGlyph.Categories, "categories"),
                            spec("settings", AppGlyph.Settings, "settings"),
                            menu("breakdown", AppGlyph.Breakdown, MonthFillCopy.BREAKDOWN),
                        ) + fills() + accounts() + schedules() + templates(templates) + undo(undoLabel)
                }
            is ShellRoute.Register ->
                listOf(
                    spec("add", AppGlyph.Add, "add"),
                    spec("transfer", AppGlyph.Transfer, "transfer"),
                    spec("reconcile", AppGlyph.Reconcile, "reconcile"),
                    spec("import", AppGlyph.Import, "import"),
                    menu("edit-account", AppGlyph.Settings, AccountScreenCopy.EDIT),
                    menu("close", AppGlyph.Cancel, AccountScreenCopy.CLOSE),
                ) + undo(undoLabel)
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
            ShellRoute.AddTransaction,
            is ShellRoute.EditTransaction,
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

    private fun saveCancel() =
        listOf(
            spec("save", AppGlyph.Save, "save"),
            spec("cancel", AppGlyph.Cancel, "cancel"),
        )

    /** Desktop's month menu: copy last month, 3 month average, set to zero. */
    private fun fills() =
        listOf(
            menu("copy-last", AppGlyph.Copy, MonthFillCopy.COPY_LAST),
            menu("average", AppGlyph.Average, MonthFillCopy.AVERAGE),
            menu("zero", AppGlyph.Zero, MonthFillCopy.ZERO),
        )

    /** Home `•••` → the account list (spec 010 US2). */
    private fun accounts() = listOf(menu("accounts", AppGlyph.Budgets, AccountScreenCopy.TITLE))

    /** Home `•••` → new schedule and rules (spec 010 US3). */
    private fun schedules() =
        listOf(
            menu("add-schedule", AppGlyph.Add, ScheduleScreenCopy.ADD),
            menu("rules", AppGlyph.Categories, RuleScreenCopy.TITLE),
            menu("reports", AppGlyph.Breakdown, ReportsCopy.TITLE),
        )

    /** Home `•••` → templates, only while experimental features and budget templates are both on (spec 010 US5). */
    private fun templates(on: Boolean) = if (on) listOf(menu("templates", AppGlyph.Average, TemplateCopy.TITLE)) else emptyList()

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
    val onSave: () -> Unit,
    val onCancel: () -> Unit,
    val onBreakdown: () -> Unit = {},
    val onFill: (MonthFillKind) -> Unit = {},
    val onToBudget: () -> Unit = {},
    val onUndo: () -> Unit = {},
    val onAccounts: () -> Unit = {},
    val onAddAccount: () -> Unit = {},
    val onShowClosed: () -> Unit = {},
    val onReorder: () -> Unit = {},
    val onEditAccount: () -> Unit = {},
    val onCloseAccount: () -> Unit = {},
    val onAddSchedule: () -> Unit = {},
    val onRules: () -> Unit = {},
    val onAddRule: () -> Unit = {},
    val onDeleteSchedule: () -> Unit = {},
    val onDeleteRule: () -> Unit = {},
    val onReports: () -> Unit = {},
    val onTemplates: () -> Unit = {},
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
            "save" -> onSave
            "cancel" -> onCancel
            "breakdown" -> onBreakdown
            "copy-last" -> ({ onFill(MonthFillKind.COPY_LAST) })
            "average" -> ({ onFill(MonthFillKind.AVERAGE) })
            "zero" -> ({ onFill(MonthFillKind.ZERO) })
            "to-budget" -> onToBudget
            "undo" -> onUndo
            "accounts" -> onAccounts
            "add-account" -> onAddAccount
            "show-closed" -> onShowClosed
            "reorder" -> onReorder
            "edit-account" -> onEditAccount
            "close" -> onCloseAccount
            "add-schedule" -> onAddSchedule
            "rules" -> onRules
            "add-rule" -> onAddRule
            "delete-schedule" -> onDeleteSchedule
            "delete-rule" -> onDeleteRule
            "reports" -> onReports
            "templates" -> onTemplates
            else -> ({})
        }
}
