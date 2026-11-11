package app.duenorth.budget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import app.duenorth.budget.core.TemplateKind
import app.duenorth.budget.core.TemplateLines
import app.duenorth.budget.core.TemplateNote

// The pages of the budget month: a category, moves, holds, templates and categories, each wired to the view model. DueNorthApp picks one per route.

@Composable
internal fun CategoryBudgetRoute(
    route: ShellRoute.CategoryBudget,
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    val shell = state.shell
    if (shell == null) {
        Placeholder()
    } else {
        val income = shell.incomeCategory(route.categoryId)
        val categories = shell.groups.flatMap { it.categories }
        val name = income?.name ?: categories.firstOrNull { it.id == route.categoryId }?.name
        EditorWithMonth(
            openMonth = state.openMonth,
            monthWindow = model.monthWindow(),
            onSelectMonth = model::selectOpenMonth,
            modifier = modifier,
            title = name,
            titleField =
                if (name != null && state.envelopes.renaming == route.categoryId) {
                    { field ->
                        CategoryRenameField(
                            name = name,
                            onSave = { model.envelopes.renameCategory(route.categoryId, it) },
                            onCancel = model.envelopes::cancelRenameCategory,
                            modifier = field,
                        )
                    }
                } else {
                    null
                },
        ) { inner ->
            if (income != null) {
                IncomeCategoryScreen(
                    shell = shell,
                    category = income,
                    error = state.editError,
                    onSave = model.envelopes::saveBudget,
                    onAutoHold = model.month::chooseAutoHold,
                    modifier = inner,
                    showName = false,
                )
                return@EditorWithMonth
            }
            CategoryBudgetScreen(
                shell = shell,
                categoryId = route.categoryId,
                error = state.editError,
                onSave = model.envelopes::saveBudget,
                onMove = model.envelopes::openMove,
                onToggleCarryover = model.envelopes::toggleCarryover,
                modifier = inner,
                showName = false,
                note = state.templates.note.takeIf { state.formatPrefs.templatesOn },
                onSaveNote = model.templates::saveNoteText,
                automations = state.templates.automations.takeIf { state.formatPrefs.templatesOn },
                automationsEditable = state.formatPrefs.automationsOn,
                templateAmounts = state.templates.amounts,
                onOpenTemplate = model.templates::openTemplate,
                onMoveTemplate = model.templates::move,
                recent = state.categoryMonth,
                onSeeAll = { model.openLedger(route.categoryId) },
                onOpenTransaction = { row -> model.register.openTransaction(row.accountId, row.registerId) },
            )
            LaunchedEffect(state.templates.note, state.templates.automations, shell.month) { model.templates.refreshAmounts() }
            // Read again whenever the month's figures change, so a saved transaction shows.
            LaunchedEffect(route.categoryId, shell) { model.loadCategoryMonth(route.categoryId) }
        }
    }
}

/** A category's "see all": the ledger, filtered to it until the chip clears the filter. */
@Composable
internal fun LedgerRoute(
    route: ShellRoute.Ledger,
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    val page = state.ledger?.takeIf { it.categoryId == route.categoryId }
    if (page == null) {
        Placeholder()
    } else {
        LedgerScreen(
            page = page,
            onGesture = model::setGesture,
            onOpen = { entry -> model.register.openTransaction(entry.accountId, entry.registerId) },
            onClearFilter = model::clearLedgerFilter,
            modifier = modifier,
        )
    }
    // Read again whenever the budget's figures change, so a transaction edited from here shows as saved.
    LaunchedEffect(route.categoryId, state.shell) { model.loadLedger(route.categoryId) }
}

@Composable
internal fun MoveCategoryRoute(
    route: ShellRoute.MoveCategory,
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    val shell = state.editingShell()
    if (shell == null) {
        Placeholder()
    } else if (state.budgetEdit != null) {
        // From edit mode the move lands in its sandbox, on the month being edited.
        MoveCategoryScreen(
            shell = shell,
            fromCategoryId = route.fromCategoryId,
            error = state.editError,
            onSave = model.envelopes::submitMove,
            modifier = modifier,
        )
    } else {
        EditorWithMonth(
            openMonth = state.openMonth,
            monthWindow = model.monthWindow(),
            onSelectMonth = model::selectOpenMonth,
            modifier = modifier,
        ) { inner ->
            MoveCategoryScreen(
                shell = shell,
                fromCategoryId = route.fromCategoryId,
                error = state.editError,
                onSave = model.envelopes::submitMove,
                modifier = inner,
            )
        }
    }
}

@Composable
internal fun CoverCategoryRoute(
    route: ShellRoute.CoverCategory,
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    val shell = state.editingShell()
    if (shell == null || shell.groups.none { group -> group.categories.any { it.id == route.categoryId } }) {
        Placeholder()
    } else {
        EditorWithMonth(
            openMonth = state.openMonth,
            monthWindow = model.monthWindow(),
            onSelectMonth = model::selectOpenMonth,
            modifier = modifier,
        ) { inner ->
            CoverCategoryScreen(
                shell = shell,
                categoryId = route.categoryId,
                error = state.editError,
                onSave = model.envelopes::submitCover,
                modifier = inner,
            )
        }
    }
}

@Composable
internal fun HoldMonthRoute(
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    val shell = state.shell
    if (shell == null) {
        Placeholder()
    } else {
        EditorWithMonth(
            openMonth = state.openMonth,
            monthWindow = model.monthWindow(),
            onSelectMonth = model::selectOpenMonth,
            modifier = modifier,
        ) { inner ->
            HoldMonthScreen(
                shell = shell,
                error = state.editError,
                onSave = model.envelopes::submitHold,
                onRelease = model.envelopes::releaseHold,
                modifier = inner,
            )
        }
    }
}

@Composable
internal fun TemplateKindsRoute(
    route: ShellRoute.TemplateKinds,
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    val shell = state.shell
    val category = shell?.groups?.flatMap { it.categories }?.firstOrNull { it.id == route.categoryId }
    if (category == null) {
        Placeholder()
    } else {
        TemplateKindsScreen(
            categoryName = category.name,
            line =
                route.index?.let { index ->
                    state.templates.note?.let {
                        TemplateNote
                            .parse(it)
                            .entries
                            .getOrNull(index)
                            ?.line
                    }
                },
            kinds = TemplateKind.offered(automations = state.templates.automations != null),
            onPick = model.templates::pickKind,
            onRemove = model.templates::remove,
            modifier = modifier,
        )
    }
}

@Composable
internal fun TemplateEditorRoute(
    route: ShellRoute.TemplateEditor,
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    val shell = state.shell
    val category = shell?.groups?.flatMap { it.categories }?.firstOrNull { it.id == route.categoryId }
    if (shell == null || category == null) {
        Placeholder()
    } else {
        TemplateEditorScreen(
            categoryName = category.name,
            kind = route.kind,
            initial = state.templateDraftStart(route),
            isNew = route.index == null,
            automations = state.templates.automations != null,
            month = shell.month,
            currency = shell.currency,
            schedules = state.templates.schedules,
            scheduleInfo = state.templates.scheduleInfo,
            sources =
                listOf(
                    PercentSource(TemplateLines.ALL_INCOME, TemplateLines.ALL_INCOME),
                    PercentSource(TemplateLines.AVAILABLE_FUNDS, TemplateLines.AVAILABLE_FUNDS),
                ) + shell.incomeGroups.flatMap { it.categories }.map { PercentSource(it.name, it.name) },
            preview = state.templates.preview,
            onPreview = model.templates::preview,
            onSave = model.templates::save,
            onRemove = model.templates::remove,
            modifier = modifier,
        )
    }
}

@Composable
internal fun TemplatesRoute(
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    // A note saved from a chip's form (or a chip moved) changes the lanes.
    LaunchedEffect(state.undoLabel) { model.templates.refreshPlan() }
    TemplatesScreen(
        month = state.budgetEdit?.month ?: state.pinnedMonth ?: state.openMonth,
        result = state.templates.result,
        busy = state.templates.busy,
        confirm = state.templates.confirm,
        onCheck = {
            model.templates.check()
            model.templates.refreshPlan()
        },
        onApply = { model.templates.previewApply(overwrite = false) },
        onOverwrite = { model.templates.previewApply(overwrite = true) },
        onConfirm = model.templates::confirmOverwrite,
        onKeep = model.templates::keep,
        modifier = modifier,
        plan = state.templates.plan,
        currency = state.shell?.currency,
        applyPreview = state.templates.applyPreview,
        onOpenChip = model.templates::openChip,
        onMoveChip = model.templates::moveToPriority,
        onConfirmPreview = model.templates::confirmApplyPreview,
        onKeepPreview = model.templates::keepApplyPreview,
        cleanup = state.templates.cleanup,
        // Edit mode's boxes are not saved yet; cleanup works on the saved month.
        onCleanup = if (state.budgetEdit == null) model.templates::cleanup else null,
    )
}

@Composable
internal fun ToBudgetRoute(
    route: ShellRoute.ToBudget,
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    val shell = state.shell
    if (shell == null) {
        Placeholder()
    } else {
        ToBudgetScreen(
            shell = shell,
            categoryId = route.categoryId,
            error = state.editError,
            onSave = model.month::submitToBudget,
            modifier = modifier,
        )
    }
}

@Composable
internal fun ManageCategoriesRoute(
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    val manage = state.envelopes.manage
    if (manage == null) {
        Placeholder()
    } else {
        ManageCategoriesScreen(
            manage = manage,
            error = state.editError,
            onAddGroup = model.envelopes::addGroup,
            onAddCategory = model.envelopes::addCategory,
            onRenameGroup = model.envelopes::renameGroup,
            onRenameCategory = model.envelopes::renameCategory,
            onHideCategory = model.envelopes::hideCategory,
            onHideGroup = model.envelopes::hideGroup,
            onMoveToGroup = model.envelopes::moveToGroup,
            onDeleteCategory = model.envelopes::deleteCategory,
            onDeleteGroup = model.envelopes::deleteGroup,
            onPlaceGroup = model.envelopes::placeGroup,
            onPlaceCategory = model.envelopes::placeCategory,
            modifier = modifier,
            onOpenCategory = model.envelopes::openBudget,
        )
    }
}

@Composable
internal fun CloseOutRoute(
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    val shell = state.shell
    if (shell == null) {
        Placeholder()
        return
    }
    val closeOut = model.closeOut
    CloseOutScreen(
        ui = state.closeOut,
        currency = shell.currency,
        onFix = closeOut::startFix,
        onReview = closeOut::showReview,
        onSkip = closeOut::skip,
        onCover = closeOut::cover,
        onRollOver = closeOut::rollOver,
        onReturn = closeOut::returnToBudget,
        onTakeFromNext = closeOut::takeFromNext,
        onShown = closeOut::markShown,
        onDone = { model.back() },
        modifier = modifier,
    )
}
