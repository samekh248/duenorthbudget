package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import app.duenorth.budget.core.RegisterEntry
import app.duenorth.budget.core.ReportsCopy
import app.duenorth.budget.core.monthLabel
import app.duenorth.budget.design.theme.Metro

// The pages of schedules, rules and reports, each wired to the view model. DueNorthApp picks one per route.

@Composable
internal fun ReviewCategoryRoute(
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    val page = state.categoryMonth
    if (page == null) {
        Placeholder()
    } else {
        CategoryReviewScreen(
            page = page,
            onGesture = model::setGesture,
            modifier = modifier,
        )
    }
}

@Composable
internal fun ScheduleEditorRoute(
    route: ShellRoute.ScheduleEditor,
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    val draft = state.schedulesUi.draft
    val shell = state.shell
    if (draft == null || shell == null || draft.id != route.scheduleId) {
        Placeholder()
    } else {
        ScheduleEditorScreen(
            draft = draft,
            accounts = shell.accounts,
            categories = state.categories.filter { !it.income || draft.amountMinor > 0 },
            decimals = shell.currency.decimals,
            error = state.editError,
            onSave = model.schedules::submit,
            modifier = modifier,
            payees = PayeeTypeAhead.names(state.payees.rows),
        )
    }
}

@Composable
internal fun SchedulesRoute(
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    val shell = state.shell
    if (shell == null) {
        Placeholder()
    } else {
        SchedulesScreen(
            schedules = state.schedules,
            currency = shell.currency,
            today = RegisterEntry.parseDate(state.today) ?: 0,
            section = state.schedulesUi.section,
            calendar = state.schedulesUi.calendar,
            onSection = model.schedules::setSection,
            onOpen = model.schedules::openDetails,
            onPost = model.schedules::post,
            onSkip = model.schedules::skip,
            onGesture = model::setGesture,
            modifier = modifier,
            onMove = if (state.schedulesUi.canReorder) model.schedules::move else null,
        )
    }
}

@Composable
internal fun ScheduleDetailsRoute(
    route: ShellRoute.ScheduleDetails,
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    val details = state.schedulesUi.details
    val shell = state.shell
    if (details == null || shell == null || details.summary.id != route.scheduleId) {
        Placeholder()
    } else {
        ScheduleDetailsScreen(details, shell.currency, modifier)
    }
}

@Composable
internal fun MonthReviewRoute(
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    MonthReviewSection(
        review = state.monthReview,
        loading = state.loading,
        onListGesture = model::setGesture,
        onCategory = model::openReviewCategory,
        modifier = modifier.background(Metro.colors.background),
    )
}

@Composable
internal fun NetWorthRoute(
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    NetWorthGraphScreen(
        ui = state.netWorthGraph,
        includeOffBudget = state.settings.netWorthIncludeOffBudget,
        onSpan = model.netWorthGraph::setSpan,
        onIncludeOffBudget = model.netWorthGraph::setIncludeOffBudget,
        modifier = modifier,
    )
}

@Composable
internal fun ReportPivotRoute(
    route: ShellRoute.ReportPivot,
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    ReportsScreen(
        page = state.reports.page,
        loading = state.reports.loading,
        preset = state.reports.preset,
        groupBy = state.reports.groupBy,
        onPreset = model.reports::setPreset,
        onCustom = model.reports::setRange,
        onGroupBy = model.reports::setGroupBy,
        onRow = { model.reports.drill(it) },
        onMonth = model.reports::openMonth,
        modifier = modifier,
        // A pivot saved before net worth became its own graph may point past the last item.
        initialItem = route.item.coerceIn(0, ReportItems.headers.lastIndex),
    )
}

@Composable
internal fun ReportDrillRoute(
    route: ShellRoute.ReportDrill,
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    // Read again each time the page shows, so a transaction just fixed from here is counted where it now belongs.
    LaunchedEffect(route) { model.reports.loadDrill(route) }
    SpendingDrillScreen(
        path = route.path,
        drill = state.reports.drills[route],
        onRow = { row, groupBy -> model.reports.drill(route, row, groupBy) },
        onTransaction = model.reports::openTransaction,
        modifier = modifier,
        root = route.month?.let { listOf(ReportsCopy.CASH_FLOW, monthLabel(it)) } ?: listOf(ReportsCopy.SPENDING),
    )
}

@Composable
internal fun CashFlowMonthRoute(
    route: ShellRoute.CashFlowMonth,
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    LaunchedEffect(route) { model.reports.loadMonth(route.month) }
    CashFlowMonthScreen(
        month = route.month,
        page = state.reports.months[route.month],
        onIncome = { model.reports.drillMonth(route.month, it, income = true) },
        onExpense = { model.reports.drillMonth(route.month, it, income = false) },
        modifier = modifier,
    )
}

@Composable
internal fun RuleEditorRoute(
    route: ShellRoute.RuleEditor,
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    val draft = state.rules.draft
    val shell = state.shell
    if (draft == null || shell == null || draft.id != route.ruleId) {
        Placeholder()
    } else {
        RuleEditorScreen(
            draft = draft,
            names = model.rules.names(),
            decimals = shell.currency.decimals,
            error = state.editError,
            onSave = model.rules::submit,
            modifier = modifier,
        )
    }
}
