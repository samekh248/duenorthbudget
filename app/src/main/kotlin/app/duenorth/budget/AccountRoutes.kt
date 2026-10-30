package app.duenorth.budget

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.duenorth.budget.core.BankSyncFix
import app.duenorth.budget.core.RegisterEntry
import app.duenorth.budget.core.SimpleFinError

// The pages of accounts, their registers and transactions, import and bank links, each wired to the view model. DueNorthApp picks one per route.

@Composable
internal fun BankSyncReportRoute(
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    val context = LocalContext.current
    BankSyncReportScreen(state.bankSync.panel, modifier) { failure ->
        // The bank's own sign-in lives at SimpleFIN Bridge; the rest is mended on the bank link.
        if (failure.fix == BankSyncFix.SIMPLEFIN) {
            openWebPage(context, SimpleFinError.BRIDGE_URL)
        } else {
            model.repairBankLink(failure)
        }
    }
}

@Composable
internal fun RegisterRoute(
    route: ShellRoute.Register,
    state: ShellUiState,
    model: ShellViewModel,
    onImportFile: (accountId: String) -> Unit,
    modifier: Modifier,
) {
    val page = state.register
    if (page == null) {
        if (state.loading) Placeholder()
    } else {
        RegisterScreen(
            page = page,
            onGesture = model::setRegisterGesture,
            onOpen = model::openEdit,
            selection = state.registerSelection,
            onSelect = model::startSelecting,
            onToggleSelected = model::toggleSelected,
            onAdd = model::openNewTransaction,
            onReconcile = model::openReconcile,
            onImportFile = {
                onImportFile(route.accountId)
            },
            modifier = modifier,
            onMatch = { model.matchTransactions(it, stayOnRegister = true) },
            onNotAMatch = { model.notAMatch(it, stayOnRegister = true) },
            search = state.registerSearch,
            onSearch = model::setRegisterSearch,
        )
    }
}

@Composable
internal fun ImportFilePreviewRoute(
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    val preview = state.import.preview
    if (preview == null) {
        Placeholder()
    } else {
        ImportPreviewScreen(
            preview = preview,
            error = state.import.error,
            onConfirm = model.import::confirm,
            onCancel = model.import::cancel,
            modifier = modifier,
        )
    }
}

@Composable
internal fun ImportedReviewRoute(
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    val review = state.import.review
    if (review == null) {
        Placeholder()
    } else {
        ImportReviewScreen(
            page = review,
            error = state.import.error,
            onCategory = model.import::setReviewCategory,
            onDone = model.import::finishReview,
            modifier = modifier,
        )
    }
}

@Composable
internal fun EditTransactionRoute(
    route: ShellRoute.EditTransaction,
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    val page = state.register
    if (page == null) {
        Placeholder()
    } else {
        val prefill = state.newTransactionPrefill.takeIf { route.transactionId == null }
        TransactionForm(
            page = page,
            row = page.rows.firstOrNull { it.id == route.transactionId },
            initialDate = prefill?.dateIso ?: state.today,
            initialAmountMinor = prefill?.amountMinor,
            chooseAccount = state.editFromHome,
            error = state.writeError,
            onSave = model::submitTransaction,
            modifier = modifier,
            dateFormat = state.formatPrefs.dateFormat,
            numberFormat = state.formatPrefs.numberFormat,
            hideFraction = state.formatPrefs.hideFraction,
            payees = PayeeTypeAhead.names(state.payees.rows),
        )
    }
}

@Composable
internal fun PickCategoryRoute(
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    val target = state.categoryTarget
    if (target == null) {
        if (state.loading) Placeholder()
    } else {
        CategoryPickerScreen(
            target = target,
            error = state.writeError,
            onPick = model::submitCategory,
            modifier = modifier,
        )
    }
}

@Composable
internal fun ReconcileRoute(
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    val register = state.register
    if (register == null) {
        Placeholder()
    } else {
        ReconcileRegisterScreen(
            register = register,
            page = state.reconcile,
            error = state.reconcileError,
            onGesture = model::setRegisterGesture,
            onStart = model::startReconcile,
            onToggle = model::toggleReconcileCleared,
            onSetCleared = model::setReconcileCleared,
            onOpen = model::openEdit,
            onAddMissing = model::addMissingForReconcile,
            onFinish = model::finishReconcile,
            onCancel = model::cancelReconcile,
            modifier = modifier,
            today = RegisterEntry.parseDate(state.today) ?: RegisterEntry.parseDate(RegisterEntry.todayIso())!!,
        )
    }
}

@Composable
internal fun EditAccountRoute(
    route: ShellRoute.EditAccount,
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    val detail = state.accountDetail
    if (detail == null || detail.id != route.accountId) {
        Placeholder()
    } else {
        EditAccountScreen(detail, state.editError, model::submitEditAccount, modifier)
    }
}

@Composable
internal fun CloseAccountRoute(
    route: ShellRoute.CloseAccount,
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    val detail = state.accountDetail
    val shell = state.shell
    val force = state.accountForceConfirm
    when {
        detail == null || shell == null || detail.id != route.accountId -> Placeholder()
        force != null ->
            ForceCloseConfirmScreen(force, model::confirmForceClose, model::keepAccount, modifier)
        else ->
            CloseAccountScreen(
                account = detail,
                others = state.accountsList,
                categories = state.categories,
                currency = shell.currency,
                error = state.editError,
                onClose = { destination, category -> model.submitCloseAccount(destination, category) },
                onForceClose = { model.submitCloseAccount(null, null, forced = true) },
                modifier = modifier,
            )
    }
}

@Composable
internal fun BankLinkRoute(
    route: ShellRoute.BankLink,
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val ui = state.bankLink
    val shell = state.shell
    if (ui == null || shell == null || ui.accountId != route.accountId) {
        Placeholder()
    } else {
        BankLinkScreen(
            ui = ui,
            currency = shell.currency,
            onProvider = model::pickBankProvider,
            onToken = model::sendSimpleFinToken,
            onCountry = model::findGoCardlessBanks,
            onInstitution = model::startGoCardless,
            onOpenBank = { url -> openWebPage(context, url) },
            onSignedIn = model::checkGoCardless,
            onLink = model::linkBank,
            onRemove = model::removeBankLink,
            modifier = modifier,
            onNewToken = model::replaceSimpleFinToken,
        )
    }
}
