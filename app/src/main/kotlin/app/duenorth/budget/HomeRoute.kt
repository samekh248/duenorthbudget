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
        onAccount = model.register::openAccount,
        accountsReorder = state.accounts.reorder,
        onMoveAccount = model.accounts::move,
        getStarted = state.getStarted(),
        onGetStarted = model.getStarted::open,
        onHideGetStarted = model.getStarted::hide,
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
                    onSchedule = model.schedules::createFromTransaction,
                    onDelete = model.inbox::delete,
                    onOpenAccount = model.register::openAccount,
                    onMatch = { model.register.match(it, stayOnRegister = false) },
                    onNotAMatch = { model.register.notAMatch(it, stayOnRegister = false) },
                )
            },
        netWorth = state.netWorth,
        onNetWorth = model.reports::openNetWorth,
        onMonthReview = model.reports::openMonthReview,
        onReport = model.reports::open,
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
        notice = state.monthUi.notice,
        formatOptions =
            FormatOptions(
                hideFraction = state.formatPrefs.hideFraction,
                numberFormat = state.formatPrefs.numberFormat,
            ),
    )
}
