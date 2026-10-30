package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.duenorth.budget.core.RegisterEntry
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
    val draft = state.scheduleDraft
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
            onSave = model::submitSchedule,
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
            section = state.schedulesSection,
            calendar = state.schedulesCalendar,
            onSection = model::setSchedulesSection,
            onOpen = model::openScheduleDetails,
            onPost = model::postSchedule,
            onSkip = model::skipSchedule,
            onGesture = model::setGesture,
            modifier = modifier,
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
    val details = state.scheduleDetails
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
    NetWorthSection(
        page = state.netWorth,
        loading = state.loading,
        onIncludeOffBudget = model::setNetWorthIncludeOffBudget,
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
        includeOffBudget = state.settings.netWorthIncludeOffBudget,
        onPreset = model.reports::setPreset,
        onCustom = model.reports::setRange,
        onGroupBy = model.reports::setGroupBy,
        onIncludeOffBudget = model.reports::setIncludeOffBudget,
        modifier = modifier,
        initialItem = route.item,
    )
}

@Composable
internal fun RuleEditorRoute(
    route: ShellRoute.RuleEditor,
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    val draft = state.ruleDraft
    val shell = state.shell
    if (draft == null || shell == null || draft.id != route.ruleId) {
        Placeholder()
    } else {
        RuleEditorScreen(
            draft = draft,
            names = model.ruleNames(),
            decimals = shell.currency.decimals,
            error = state.editError,
            onSave = model::submitRule,
            modifier = modifier,
        )
    }
}
