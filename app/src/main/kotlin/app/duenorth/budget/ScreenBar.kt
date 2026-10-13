package app.duenorth.budget

import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.MonthFillCopy
import app.duenorth.budget.core.MonthFillKind
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
                        ) + fills() + undo(undoLabel)
                    else ->
                        listOf(
                            spec("add", AppGlyph.Add, "add"),
                            spec("hold", AppGlyph.Hold, "hold"),
                            spec("categories", AppGlyph.Categories, "categories"),
                            spec("settings", AppGlyph.Settings, "settings"),
                            menu("breakdown", AppGlyph.Breakdown, MonthFillCopy.BREAKDOWN),
                        ) + fills() + menu("auto-hold", AppGlyph.Hold, MonthFillCopy.AUTO_HOLD) + undo(undoLabel)
                }
            is ShellRoute.Register ->
                listOf(
                    spec("add", AppGlyph.Add, "add"),
                    spec("transfer", AppGlyph.Transfer, "transfer"),
                    spec("reconcile", AppGlyph.Reconcile, "reconcile"),
                    spec("import", AppGlyph.Import, "import"),
                ) + undo(undoLabel)
            is ShellRoute.CategoryBudget ->
                if (mode == BudgetMode.TRACKING) {
                    saveCancel()
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
        return specsFor(state.route, hasBudget, mode, state.undoLabel).map { spec ->
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
    val onAutoHold: () -> Unit = {},
    val onToBudget: () -> Unit = {},
    val onUndo: () -> Unit = {},
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
            "auto-hold" -> onAutoHold
            "to-budget" -> onToBudget
            "undo" -> onUndo
            else -> ({})
        }
}
