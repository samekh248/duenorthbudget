package app.duenorth.budget

import app.duenorth.budget.core.BudgetMode
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
                        )
                    else ->
                        listOf(
                            spec("add", AppGlyph.Add, "add"),
                            spec("hold", AppGlyph.Hold, "hold"),
                            spec("categories", AppGlyph.Categories, "categories"),
                            spec("settings", AppGlyph.Settings, "settings"),
                        )
                }
            is ShellRoute.Register ->
                listOf(
                    spec("add", AppGlyph.Add, "add"),
                    spec("transfer", AppGlyph.Transfer, "transfer"),
                    spec("reconcile", AppGlyph.Reconcile, "reconcile"),
                    spec("import", AppGlyph.Import, "import"),
                )
            ShellRoute.Create,
            ShellRoute.Assign,
            ShellRoute.AddTransaction,
            is ShellRoute.EditTransaction,
            is ShellRoute.SplitTransaction,
            is ShellRoute.TransferMoney,
            ShellRoute.HoldMonth,
            is ShellRoute.CategoryBudget,
            is ShellRoute.MoveCategory,
            is ShellRoute.ImportPreviewRoute,
            is ShellRoute.Reconcile,
            ShellRoute.BudgetPassword,
            ->
                listOf(
                    spec("save", AppGlyph.Save, "save"),
                    spec("cancel", AppGlyph.Cancel, "cancel"),
                )
            else -> emptyList()
        }

    fun forState(
        state: ShellUiState,
        handlers: ScreenBarHandlers,
    ): List<ScreenAction> {
        val mode = state.shell?.mode
        val hasBudget = state.shell != null && !state.locked
        return specsFor(state.route, hasBudget, mode).map { spec ->
            ScreenAction(
                id = spec.id,
                glyph = spec.glyph,
                label = spec.label,
                primary = spec.primary,
                run = handlers.bind(spec.id, state),
            )
        }
    }

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
            else -> ({})
        }
}
