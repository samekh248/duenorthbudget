package app.duenorth.budget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.duenorth.budget.core.FormatOptions

// The pages of the home pivot, each wired to the view model. DueNorthApp picks one per route.

@Composable
internal fun HomeRoute(
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    HomePivot(
        shell = state.editingShell(),
        loading = state.loading,
        openMonth = state.openMonth,
        monthWindow = model.monthWindow(),
        initialSection = state.homeSection,
        onGesture = model::setGesture,
        onSection = model::selectHomeSection,
        onAccount = model::openAccount,
        accountsReorder = state.accountsReorder,
        onMoveAccount = model::moveAccount,
        getStarted = state.getStarted(),
        onGetStarted = model::openGetStarted,
        onHideGetStarted = model::hideGetStarted,
        onInbox = model.inbox::openPicker,
        onAcceptInbox = model.inbox::accept,
        inboxFiled = state.inbox.filed,
        onInboxFiledShown = model.inbox::filedShown,
        inboxSelection = state.inbox.selection,
        onInboxToggle = model.inbox::toggleSelected,
        inboxLead = state.inbox.lead,
        inboxNotice = state.inbox.syncNotice,
        inboxActions =
            remember(model) {
                InboxActions(
                    onDetails = model.inbox::openDetails,
                    onSchedule = model::createScheduleFromTransaction,
                    onDelete = model.inbox::delete,
                    onOpenAccount = model::openAccount,
                    onMatch = { model.matchTransactions(it, stayOnRegister = false) },
                    onNotAMatch = { model.notAMatch(it, stayOnRegister = false) },
                )
            },
        netWorth = state.netWorth,
        onNetWorth = model::openNetWorth,
        onMonthReview = model::openMonthReview,
        onReport = model::openReport,
        modifier = modifier,
        onSelectMonth = model::selectOpenMonth,
        onBudgetCategory = if (state.shell == null) null else model.envelopes::openBudget,
        // Tapping to budget opens edit mode; in edit mode it opens the breakdown.
        onToBudget = if (state.shell == null) null else model::startBudgetEdit,
        showAhead = state.freeAfterPlansOn,
        budgetEdit = state.budgetEdit,
        budgetEditActions =
            remember(model) {
                BudgetEditActions(
                    onFocus = model::focusBudgetBox,
                    onKey = model::typeBudgetKey,
                    onCover = model::coverBudgetBox,
                    onAll = model::allToBudgetBox,
                    onDone = model::doneBudgetBox,
                    onAvailable = { id ->
                        if (model.doneBudgetBox()) model.envelopes.openMoveFrom(id)
                    },
                    // The breakdown reads the saved month, not edit mode's changes.
                    onToBudget = null,
                )
            },
        editError = state.editError,
        notice = state.monthNotice,
        formatOptions =
            FormatOptions(
                hideFraction = state.formatPrefs.hideFraction,
                numberFormat = state.formatPrefs.numberFormat,
            ),
    )
}
