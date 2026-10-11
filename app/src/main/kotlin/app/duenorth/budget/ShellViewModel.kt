package app.duenorth.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.duenorth.budget.core.BudgetConflict
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.BudgetSummary
import app.duenorth.budget.core.CategoryChoice
import app.duenorth.budget.core.CategoryManagePage
import app.duenorth.budget.core.CategoryTarget
import app.duenorth.budget.core.CreateResult
import app.duenorth.budget.core.EditResult
import app.duenorth.budget.core.BankSyncTransport
import app.duenorth.budget.core.ForcedWrite
import app.duenorth.budget.core.HttpBankSyncTransport
import app.duenorth.budget.core.ImportPreview
import app.duenorth.budget.core.ImportReviewPage
import app.duenorth.budget.core.MockBudgetDataset
import app.duenorth.budget.core.MockBudgets
import app.duenorth.budget.core.ParsedImportRow
import app.duenorth.budget.core.PreviewRowStatus
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.withBudgetedAssignment
import app.duenorth.budget.core.withHold
import app.duenorth.budget.core.withMoveAvailable
import app.duenorth.budget.core.withReleaseHold
import app.duenorth.budget.core.OpenResult
import app.duenorth.budget.core.PhoneSettings
import app.duenorth.budget.core.RefreshGate
import app.duenorth.budget.core.RegisterEntry
import app.duenorth.budget.core.RegisterPage
import app.duenorth.budget.core.RemoteFile
import app.duenorth.budget.core.PayeeRow
import app.duenorth.budget.core.PayeeWriteResult
import app.duenorth.budget.core.ScheduleWriteResult
import app.duenorth.budget.core.SplitPart
import app.duenorth.budget.core.SyncCopy
import app.duenorth.budget.core.SyncCoordinator
import app.duenorth.budget.core.TransactionDraft
import app.duenorth.budget.core.TransferDraft
import app.duenorth.budget.core.CategoryMonthPage
import app.duenorth.budget.core.MonthReviewPage
import app.duenorth.budget.core.NetWorthPage
import app.duenorth.budget.core.ReconcileFinishResult
import app.duenorth.budget.core.ReconcilePage
import app.duenorth.budget.core.ReconcileStartResult
import app.duenorth.budget.core.UnlockResult
import app.duenorth.budget.core.UploadResult
import app.duenorth.budget.core.UpcomingScheduleRow
import app.duenorth.budget.core.WriteResult
import app.duenorth.budget.core.syncFraction
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ShellRoute {
    data object Home : ShellRoute

    data object Budgets : ShellRoute

    data object Create : ShellRoute

    data object ConfirmSwitch : ShellRoute

    data object Appearance : ShellRoute

    data object Server : ShellRoute

    data object BudgetPassword : ShellRoute

    data object Assign : ShellRoute

    data object AddTransaction : ShellRoute

    data object Conflicts : ShellRoute

    data object ConfirmReplace : ShellRoute

    data class Register(
        val accountId: String,
    ) : ShellRoute

    data class EditTransaction(
        val accountId: String,
        val transactionId: String?,
    ) : ShellRoute

    data class SplitTransaction(
        val accountId: String,
        val transactionId: String,
    ) : ShellRoute

    data class TransferMoney(
        val accountId: String,
    ) : ShellRoute

    data class PickCategory(
        val transactionId: String,
    ) : ShellRoute

    data class ImportPreviewRoute(
        val accountId: String,
    ) : ShellRoute

    data class ImportReviewRoute(
        val accountId: String,
    ) : ShellRoute

    data class Reconcile(
        val accountId: String,
    ) : ShellRoute

    data class ReviewCategory(
        val categoryId: String,
    ) : ShellRoute

    data class GroupCategories(
        val groupId: String,
    ) : ShellRoute

    data class CategoryBudget(
        val categoryId: String,
    ) : ShellRoute

    data class MoveCategory(
        val fromCategoryId: String,
    ) : ShellRoute

    data object HoldMonth : ShellRoute

    data object ManageCategories : ShellRoute

    data object Payees : ShellRoute

    data object SampleBudgets : ShellRoute
}

data class ShellUiState(
    val loading: Boolean = true,
    val route: ShellRoute = ShellRoute.Home,
    val settings: PhoneSettings = PhoneSettings(),
    val budgets: List<BudgetSummary> = emptyList(),
    val developerMode: Boolean = false,
    val sampleBudgets: List<MockBudgetDataset> = emptyList(),
    val shell: MonthShell? = null,
    val register: RegisterPage? = null,
    val registerFilter: String = "",
    val categoryTarget: CategoryTarget? = null,
    val createError: String? = null,
    val writeError: String? = null,
    val confirm: WriteResult.Confirm? = null,
    val switchTarget: BudgetSummary? = null,
    val today: String = RegisterEntry.todayIso(),
    val reviewMonth: YearMonth = YearMonth.now(),
    val budgetMonth: YearMonth = YearMonth.now(),
    val monthReview: MonthReviewPage? = null,
    val netWorth: NetWorthPage? = null,
    val categoryMonth: CategoryMonthPage? = null,
    val reconcile: ReconcilePage? = null,
    val reconcileError: String? = null,
    val upcoming: List<UpcomingScheduleRow> = emptyList(),
    val payees: List<PayeeRow> = emptyList(),
    val payeeMergeTarget: String? = null,
    val scheduleDuplicateId: String? = null,
    val serverError: String? = null,
    val remoteFiles: List<RemoteFile> = emptyList(),
    val syncing: Boolean = false,
    val syncProgress: Float? = null,
    val syncStatus: String = "",
    val conflicts: List<BudgetConflict> = emptyList(),
    val categories: List<CategoryChoice> = emptyList(),
    val locked: Boolean = false,
    val passwordError: String? = null,
    val pendingFile: RemoteFile? = null,
    val replaceFile: RemoteFile? = null,
    val editError: String? = null,
    val askEachTime: Boolean = true,
    val signedIn: Boolean = false,
    val importPreview: ImportPreview? = null,
    val importCandidates: List<ParsedImportRow> = emptyList(),
    val importReview: ImportReviewPage? = null,
    val importError: String? = null,
    val importFetchPending: Boolean = false,
    val registerBankLinked: Boolean = false,
    val categoryManage: CategoryManagePage? = null,
)

class ShellViewModel(
    private val library: BudgetLibrary,
    private val sync: SyncCoordinator,
    private val bankSyncTransport: BankSyncTransport = HttpBankSyncTransport(),
    private val developerMode: Boolean = false,
) : ViewModel() {
    private val shellGate = RefreshGate<MonthShell>()
    private val registerGate = RefreshGate<RegisterPage>()
    private val history = ArrayDeque<ShellRoute>()
    private val _state =
        MutableStateFlow(
            ShellUiState(
                developerMode = developerMode,
                sampleBudgets = if (developerMode) MockBudgets.all else emptyList(),
            ),
        )
    val state: StateFlow<ShellUiState> = _state.asStateFlow()
    private var writing = false

    init {
        viewModelScope.launch {
            reload()
            val id = _state.value.settings.openBudgetId ?: return@launch
            if (_state.value.locked) return@launch
            withContext(Dispatchers.IO) { runCatching { sync.sync(id) } }
            reload()
        }
    }

    fun refresh() {
        viewModelScope.launch { reload() }
    }

    fun setGesture(active: Boolean) {
        if (active) {
            shellGate.beginGesture()
            return
        }
        if (!shellGate.gestureActive) return
        shellGate.endGesture()
        _state.update { it.copy(shell = shellGate.visible ?: it.shell) }
    }

    fun setRegisterGesture(active: Boolean) {
        if (active) {
            registerGate.beginGesture()
            return
        }
        if (!registerGate.gestureActive) return
        registerGate.endGesture()
        _state.update { it.copy(register = registerGate.visible ?: it.register) }
    }

    fun showBudgets() {
        history.clear()
        _state.update { it.copy(route = ShellRoute.Budgets, confirm = null, writeError = null) }
    }

    fun showCreate() {
        history.clear()
        _state.update { it.copy(route = ShellRoute.Create, createError = null, confirm = null) }
    }

    fun showSampleBudgets() {
        if (!developerMode) return
        history.clear()
        _state.update { it.copy(route = ShellRoute.SampleBudgets, createError = null, confirm = null) }
    }

    fun showAppearance() {
        history.clear()
        _state.update { it.copy(route = ShellRoute.Appearance, confirm = null) }
    }

    fun showPayees() {
        history.clear()
        _state.update { it.copy(route = ShellRoute.Payees, writeError = null, payeeMergeTarget = null) }
        viewModelScope.launch {
            val budgetId = _state.value.settings.openBudgetId ?: return@launch
            val rows = withContext(Dispatchers.IO) { library.listPayees(budgetId) }
            _state.update { it.copy(payees = rows) }
        }
    }

    fun postSchedule(scheduleId: String) {
        launchSchedule(scheduleId, forceDuplicate = false)
    }

    fun confirmScheduleDuplicate() {
        val id = _state.value.scheduleDuplicateId ?: return
        _state.update { it.copy(scheduleDuplicateId = null) }
        launchSchedule(id, forceDuplicate = true)
    }

    fun cancelScheduleDuplicate() {
        _state.update { it.copy(scheduleDuplicateId = null) }
    }

    fun skipSchedule(scheduleId: String) {
        val budgetId = _state.value.settings.openBudgetId ?: return
        viewModelScope.launch {
            when (withContext(Dispatchers.IO) { library.skipSchedule(budgetId, scheduleId) }) {
                is ScheduleWriteResult.Skipped,
                is ScheduleWriteResult.Posted,
                -> refresh()
                is ScheduleWriteResult.Rejected -> Unit
                is ScheduleWriteResult.ConfirmDuplicate -> Unit
            }
        }
    }

    fun createScheduleFromTransaction(transactionId: String) {
        val budgetId = _state.value.settings.openBudgetId ?: return
        val next = _state.value.today
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                library.createScheduleFromTransaction(budgetId, transactionId, "monthly", next)
            }
            refresh()
        }
    }

    fun renamePayee(
        payeeId: String,
        name: String,
        confirmMerge: Boolean,
        rememberRule: Boolean,
    ) {
        val budgetId = _state.value.settings.openBudgetId ?: return
        viewModelScope.launch {
            when (
                val result =
                    withContext(Dispatchers.IO) {
                        library.renamePayee(budgetId, payeeId, name, confirmMerge, rememberRule)
                    }
            ) {
                is PayeeWriteResult.Saved -> {
                    val rows = withContext(Dispatchers.IO) { library.listPayees(budgetId) }
                    _state.update { it.copy(payees = rows, writeError = null, payeeMergeTarget = null) }
                }
                is PayeeWriteResult.Rejected ->
                    _state.update { it.copy(writeError = result.reason) }
                is PayeeWriteResult.ConfirmMerge ->
                    _state.update { it.copy(payeeMergeTarget = result.targetId, writeError = null) }
            }
        }
    }

    fun confirmPayeeMerge(
        payeeId: String,
        name: String,
    ) {
        renamePayee(payeeId, name, confirmMerge = true, rememberRule = false)
    }

    fun cancelPayeeMerge() {
        _state.update { it.copy(payeeMergeTarget = null) }
    }

    fun showServer() {
        viewModelScope.launch {
            val result =
                if (sync.signedIn()) {
                    withContext(Dispatchers.IO) { sync.refreshRemote() }
                } else {
                    null
                }
            _state.update { current ->
                val status = current.settings.openBudgetId?.let { sync.status(it) } ?: current.syncStatus
                current.copy(
                    route = ShellRoute.Server,
                    remoteFiles = result?.files ?: sync.remoteFiles,
                    serverError = result?.error,
                    syncStatus = status,
                    signedIn = sync.signedIn(),
                )
            }
        }
    }

    fun showConflicts() {
        val id = _state.value.settings.openBudgetId
        viewModelScope.launch {
            val conflicts = if (id == null) emptyList() else withContext(Dispatchers.IO) { sync.conflicts(id) }
            if (id != null) withContext(Dispatchers.IO) { sync.markConflictsSeen(id) }
            _state.update { it.copy(route = ShellRoute.Conflicts, conflicts = conflicts) }
        }
    }

    fun showAssign() {
        val id = _state.value.shell?.budgetId
        viewModelScope.launch {
            val categories = if (id == null) emptyList() else withContext(Dispatchers.IO) { sync.categories(id) }
            _state.update { it.copy(route = ShellRoute.Assign, categories = categories, editError = null) }
        }
    }

    fun previousBudgetMonth() {
        _state.update { it.copy(budgetMonth = it.budgetMonth.minusMonths(1)) }
        refreshBudgetShell()
    }

    fun nextBudgetMonth() {
        _state.update { it.copy(budgetMonth = it.budgetMonth.plusMonths(1)) }
        refreshBudgetShell()
    }

    fun openGroupCategories(groupId: String) {
        push(ShellRoute.GroupCategories(groupId))
        _state.update { it.copy(editError = null) }
    }

    fun openCategoryBudget(categoryId: String) {
        push(ShellRoute.CategoryBudget(categoryId))
        _state.update { it.copy(editError = null) }
    }

    fun openMoveCategory() {
        val from = (_state.value.route as? ShellRoute.CategoryBudget)?.categoryId ?: return
        push(ShellRoute.MoveCategory(from))
        _state.update { it.copy(editError = null) }
    }

    fun showHoldMonth() {
        push(ShellRoute.HoldMonth)
        _state.update { it.copy(editError = null) }
    }

    fun showManageCategories() {
        push(ShellRoute.ManageCategories)
        val budgetId = _state.value.shell?.budgetId ?: _state.value.settings.openBudgetId
        if (budgetId == null) return
        viewModelScope.launch {
            val page = withContext(Dispatchers.IO) { sync.readCategoryManage(budgetId) }
            _state.update { it.copy(editError = null, categoryManage = page) }
        }
    }

    fun saveCategoryBudget(amountText: String) {
        val shell = _state.value.shell ?: return
        val categoryId = (_state.value.route as? ShellRoute.CategoryBudget)?.categoryId ?: return
        val budgeted = MoneyFormat.parse(amountText, shell.currency.decimals)
        if (budgeted == null) {
            _state.update { it.copy(editError = SyncCopy.ENTER_AMOUNT) }
            return
        }
        applyEnvelopeEdit(optimistic = { it.withBudgetedAssignment(categoryId, budgeted) }) {
            sync.assign(shell.budgetId, categoryId, _state.value.budgetMonth, amountText)
        }
    }

    fun toggleCategoryCarryover(enabled: Boolean) {
        val shell = _state.value.shell ?: return
        val categoryId = (_state.value.route as? ShellRoute.CategoryBudget)?.categoryId ?: return
        applyEnvelopeEdit {
            sync.setCategoryCarryover(shell.budgetId, categoryId, _state.value.budgetMonth, enabled)
        }
    }

    fun submitMoveCategory(
        toCategoryId: String,
        amountText: String,
    ) {
        val shell = _state.value.shell ?: return
        val from = (_state.value.route as? ShellRoute.MoveCategory)?.fromCategoryId ?: return
        if (toCategoryId.isBlank()) {
            _state.update { it.copy(editError = SyncCopy.ENTER_AMOUNT) }
            return
        }
        val amount = MoneyFormat.parse(amountText, shell.currency.decimals)
        if (amount == null || amount <= 0L) {
            _state.update { it.copy(editError = SyncCopy.ENTER_AMOUNT) }
            return
        }
        applyEnvelopeEdit(
            stayOnRoute = false,
            optimistic = { it.withMoveAvailable(from, toCategoryId, amount) },
        ) {
            sync.moveCategory(shell.budgetId, from, toCategoryId, _state.value.budgetMonth, amountText)
        }
    }

    fun submitHold(amountText: String) {
        val shell = _state.value.shell ?: return
        val amount = MoneyFormat.parse(amountText, shell.currency.decimals)
        if (amount == null) {
            _state.update { it.copy(editError = SyncCopy.ENTER_AMOUNT) }
            return
        }
        applyEnvelopeEdit(optimistic = { it.withHold(amount) }) {
            sync.holdForNextMonth(shell.budgetId, _state.value.budgetMonth, amountText)
        }
    }

    fun releaseHold() {
        val shell = _state.value.shell ?: return
        applyEnvelopeEdit(optimistic = { it.withReleaseHold() }) {
            sync.releaseMonthHold(shell.budgetId, _state.value.budgetMonth)
        }
    }

    fun addCategoryGroup(name: String) {
        val budgetId = _state.value.settings.openBudgetId ?: return
        if (name.isBlank()) {
            _state.update { it.copy(editError = SyncCopy.ENTER_AMOUNT) }
            return
        }
        applyEnvelopeEdit {
            sync.addCategoryGroup(budgetId, name)
        }
    }

    fun addEnvelopeCategory(
        groupId: String,
        name: String,
    ) {
        val budgetId = _state.value.settings.openBudgetId ?: return
        if (groupId.isBlank() || name.isBlank()) {
            _state.update { it.copy(editError = SyncCopy.ENTER_AMOUNT) }
            return
        }
        applyEnvelopeEdit {
            sync.addEnvelopeCategory(budgetId, groupId, name)
        }
    }

    fun renameCategoryGroup(
        groupId: String,
        name: String,
    ) {
        val budgetId = _state.value.settings.openBudgetId ?: return
        applyEnvelopeEdit { sync.renameCategoryGroup(budgetId, groupId, name) }
    }

    fun renameEnvelopeCategory(
        categoryId: String,
        name: String,
    ) {
        val budgetId = _state.value.settings.openBudgetId ?: return
        applyEnvelopeEdit { sync.renameEnvelopeCategory(budgetId, categoryId, name) }
    }

    fun hideEnvelopeCategory(
        categoryId: String,
        hidden: Boolean,
    ) {
        val budgetId = _state.value.settings.openBudgetId ?: return
        applyEnvelopeEdit { sync.hideEnvelopeCategory(budgetId, categoryId, hidden) }
    }

    fun deleteEnvelopeCategory(categoryId: String) {
        val budgetId = _state.value.settings.openBudgetId ?: return
        applyEnvelopeEdit { sync.deleteEnvelopeCategory(budgetId, categoryId) }
    }

    fun deleteCategoryGroup(groupId: String) {
        val budgetId = _state.value.settings.openBudgetId ?: return
        applyEnvelopeEdit { sync.deleteCategoryGroup(budgetId, groupId) }
    }

    fun moveCategoryGroupOrder(
        groupId: String,
        earlier: Boolean,
    ) {
        val budgetId = _state.value.settings.openBudgetId ?: return
        applyEnvelopeEdit { sync.moveCategoryGroupOrder(budgetId, groupId, earlier) }
    }

    fun moveEnvelopeCategoryOrder(
        categoryId: String,
        earlier: Boolean,
    ) {
        val budgetId = _state.value.settings.openBudgetId ?: return
        applyEnvelopeEdit { sync.moveEnvelopeCategoryOrder(budgetId, categoryId, earlier) }
    }

    fun showAddTransaction() {
        _state.update { it.copy(route = ShellRoute.AddTransaction, editError = null) }
    }

    fun back(): Boolean {
        val current = _state.value
        if (current.confirm != null) {
            _state.update { it.copy(confirm = null) }
            return true
        }
        if (current.route == ShellRoute.Home) return false
        if (current.route == ShellRoute.BudgetPassword && current.locked) return false
        if (current.route == ShellRoute.Create && current.shell == null) return false
        if (current.route.isRegisterFlow() || current.route.isHomeFlow()) {
            val previous = if (history.isEmpty()) ShellRoute.Home else history.removeLast()
            _state.update {
                it.copy(
                    route = previous,
                    writeError = null,
                    categoryMonth = if (previous == ShellRoute.Home) null else it.categoryMonth,
                )
            }
            return true
        }
        history.clear()
        _state.update {
            it.copy(
                route = ShellRoute.Home,
                createError = null,
                switchTarget = null,
                writeError = null,
                passwordError = null,
                editError = null,
                serverError = null,
            )
        }
        return true
    }

    fun onScreenLocked() {
        val id = _state.value.settings.openBudgetId ?: return
        sync.lock(id)
        if (sync.needsPassword(id)) {
            history.clear()
            _state.update {
                it.copy(locked = true, shell = null, register = null, route = ShellRoute.BudgetPassword, passwordError = null)
            }
        }
    }

    fun openAccount(accountId: String) {
        push(ShellRoute.Register(accountId))
        _state.update { it.copy(registerFilter = "") }
        viewModelScope.launch { apply(load(accountId), accountId) }
    }

    fun previewImportFile(
        accountId: String,
        bytes: ByteArray,
        fileName: String,
    ) {
        val budgetId = _state.value.settings.openBudgetId ?: return
        viewModelScope.launch {
            val (bundle, error) =
                withContext(Dispatchers.IO) {
                    library.previewImportFile(budgetId, accountId, bytes, fileName)
                }
            if (bundle == null) {
                _state.update { it.copy(importError = error) }
                return@launch
            }
            _state.update {
                it.copy(
                    route = ShellRoute.ImportPreviewRoute(accountId),
                    importPreview = bundle.preview,
                    importCandidates = bundle.candidates,
                    importError = error,
                )
            }
        }
    }

    fun confirmImport() {
        val budgetId = _state.value.settings.openBudgetId ?: return
        val preview = _state.value.importPreview ?: return
        val candidates = _state.value.importCandidates
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val book = library
                val toAdd =
                    preview.rows
                        .filter { it.status == PreviewRowStatus.New }
                        .map { candidates[it.index] }
                book.confirmImport(budgetId, preview.accountId, toAdd)
            }
            push(ShellRoute.ImportReviewRoute(preview.accountId))
            reloadReview(preview.accountId)
            apply(load(preview.accountId), preview.accountId)
        }
    }

    fun cancelImport() {
        val accountId =
            (_state.value.route as? ShellRoute.ImportPreviewRoute)?.accountId
                ?: openAccountId()
                ?: return
        _state.update {
            it.copy(
                importPreview = null,
                importCandidates = emptyList(),
                importError = null,
                route = ShellRoute.Register(accountId),
            )
        }
    }

    fun fetchBankTransactions(accountId: String) {
        val budgetId = _state.value.settings.openBudgetId ?: return
        val settings = _state.value.settings
        _state.update { it.copy(importFetchPending = true, importError = null, syncProgress = 0.1f) }
        viewModelScope.launch {
            val (bundle, error) =
                withContext(Dispatchers.IO) {
                    library.previewBankFetch(
                        budgetId,
                        accountId,
                        settings.serverAddress,
                        sync.syncToken(),
                        bankSyncTransport,
                        java.time.LocalDate.now(),
                    )
                }
            _state.update { it.copy(importFetchPending = false, syncProgress = null) }
            if (bundle == null) {
                _state.update { it.copy(importError = error) }
                return@launch
            }
            _state.update {
                it.copy(
                    route = ShellRoute.ImportPreviewRoute(accountId),
                    importPreview = bundle.preview,
                    importCandidates = bundle.candidates,
                    importError = null,
                )
            }
        }
    }

    fun setImportReviewCategory(
        transactionId: String,
        categoryId: String?,
    ) {
        val budgetId = _state.value.settings.openBudgetId ?: return
        val accountId = _state.value.importReview?.accountId ?: return
        viewModelScope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    library.setImportReviewCategory(budgetId, transactionId, categoryId)
                }
            when (result) {
                is WriteResult.Rejected -> _state.update { it.copy(importError = result.reason) }
                else -> reloadReview(accountId)
            }
            apply(load(accountId), accountId)
        }
    }

    fun finishImportReview() {
        val budgetId = _state.value.settings.openBudgetId ?: return
        val accountId = _state.value.importReview?.accountId ?: openAccountId() ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { library.finishImportReview(budgetId) }
            _state.update {
                it.copy(
                    importReview = null,
                    importPreview = null,
                    importCandidates = emptyList(),
                    importError = null,
                    route = ShellRoute.Register(accountId),
                )
            }
            apply(load(accountId), accountId)
        }
    }

    private suspend fun reloadReview(accountId: String) {
        val budgetId = _state.value.settings.openBudgetId ?: return
        val review = withContext(Dispatchers.IO) { library.readImportReview(budgetId) }
        _state.update { it.copy(importReview = review, importError = null) }
    }

    fun openInbox(transactionId: String) {
        push(ShellRoute.PickCategory(transactionId))
        val budgetId = _state.value.settings.openBudgetId ?: return
        viewModelScope.launch {
            val target = withContext(Dispatchers.IO) { library.readCategoryTarget(budgetId, transactionId) }
            _state.update { it.copy(categoryTarget = target, loading = false) }
        }
    }

    fun openNewTransaction() {
        val accountId = openAccountId() ?: return
        push(ShellRoute.EditTransaction(accountId, null))
    }

    fun openEdit(transactionId: String) {
        val accountId = openAccountId() ?: return
        push(ShellRoute.EditTransaction(accountId, transactionId))
    }

    fun openSplit(transactionId: String) {
        val accountId = openAccountId() ?: return
        push(ShellRoute.SplitTransaction(accountId, transactionId))
    }

    fun openTransfer() {
        val accountId = openAccountId() ?: return
        push(ShellRoute.TransferMoney(accountId))
    }

    fun openReconcile() {
        val accountId = openAccountId() ?: return
        push(ShellRoute.Reconcile(accountId))
        reloadReconcile(accountId)
    }

    fun startReconcile(
        balanceText: String,
        dateText: String,
    ) {
        val accountId = reconcileAccountId() ?: return
        val budgetId = _state.value.settings.openBudgetId ?: return
        viewModelScope.launch {
            when (
                val result =
                    withContext(Dispatchers.IO) {
                        library.startReconcile(budgetId, accountId, balanceText, dateText)
                    }
            ) {
                is ReconcileStartResult.Started ->
                    _state.update { it.copy(reconcile = result.page, reconcileError = null) }
                is ReconcileStartResult.Rejected ->
                    _state.update { it.copy(reconcileError = result.reason) }
            }
        }
    }

    fun toggleReconcileCleared(transactionId: String) {
        val accountId = reconcileAccountId() ?: return
        val budgetId = _state.value.settings.openBudgetId ?: return
        viewModelScope.launch {
            val page =
                withContext(Dispatchers.IO) {
                    library.toggleReconcileCleared(budgetId, accountId, transactionId)
                }
            if (page != null) _state.update { it.copy(reconcile = page) }
        }
    }

    fun finishReconcile() {
        val accountId = reconcileAccountId() ?: return
        val budgetId = _state.value.settings.openBudgetId ?: return
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { library.finishReconcile(budgetId, accountId) }) {
                ReconcileFinishResult.Finished -> {
                    history.clear()
                    history.addLast(ShellRoute.Home)
                    _state.update {
                        it.copy(
                            route = ShellRoute.Register(accountId),
                            reconcile = null,
                            reconcileError = null,
                        )
                    }
                    apply(load(accountId), accountId)
                }
                is ReconcileFinishResult.Rejected ->
                    _state.update { state -> state.copy(reconcileError = result.reason) }
            }
        }
    }

    fun cancelReconcile() {
        val accountId = reconcileAccountId() ?: return
        val budgetId = _state.value.settings.openBudgetId ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { library.cancelReconcile(budgetId, accountId) }
            history.removeLastOrNull()
            _state.update {
                it.copy(
                    route = ShellRoute.Register(accountId),
                    reconcile = null,
                    reconcileError = null,
                )
            }
        }
    }

    fun previousReviewMonth() {
        _state.update { it.copy(reviewMonth = it.reviewMonth.minusMonths(1)) }
        refreshReview()
    }

    fun openReviewCategory(categoryId: String) {
        push(ShellRoute.ReviewCategory(categoryId))
        val budgetId = _state.value.settings.openBudgetId ?: return
        val month = _state.value.reviewMonth
        viewModelScope.launch {
            val page = withContext(Dispatchers.IO) { library.readCategoryMonth(budgetId, categoryId, month) }
            _state.update { it.copy(categoryMonth = page) }
        }
    }

    fun setNetWorthIncludeOffBudget(include: Boolean) {
        saveSettings(_state.value.settings.copy(netWorthIncludeOffBudget = include))
        refreshNetWorth()
    }

    fun setRegisterFilter(text: String) {
        _state.update { it.copy(registerFilter = text) }
    }

    fun submitTransaction(draft: TransactionDraft) {
        launchWrite { id ->
            val result = withContext(Dispatchers.IO) { library.saveTransaction(id, draft) }
            publish(result, draft.accountId, stayOnRegister = true)
        }
    }

    fun submitTransfer(draft: TransferDraft) {
        launchWrite { id ->
            val result = withContext(Dispatchers.IO) { library.transfer(id, draft) }
            publish(result, draft.fromAccountId, stayOnRegister = true)
        }
    }

    fun submitSplit(
        transactionId: String,
        parts: List<SplitPart>,
    ) {
        val accountId = openAccountId() ?: return
        launchWrite { id ->
            val result = withContext(Dispatchers.IO) { library.splitTransaction(id, transactionId, parts, false) }
            publish(result, accountId, stayOnRegister = true)
        }
    }

    fun submitCategory(categoryId: String?) {
        val target = _state.value.categoryTarget ?: return
        launchWrite { id ->
            val result =
                withContext(Dispatchers.IO) {
                    library.setTransactionCategory(id, target.transactionId, categoryId, false)
                }
            publish(result, target.accountId, stayOnRegister = false)
        }
    }

    fun unsplit(transactionId: String) {
        val accountId = openAccountId() ?: return
        launchWrite { id ->
            val result = withContext(Dispatchers.IO) { library.unsplitTransaction(id, transactionId, false) }
            publish(result, accountId, stayOnRegister = true)
        }
    }

    fun deleteTransaction(transactionId: String) {
        val accountId = openAccountId() ?: return
        launchWrite { id ->
            val result = withContext(Dispatchers.IO) { library.deleteTransaction(id, transactionId, false) }
            publish(result, accountId, stayOnRegister = true)
        }
    }

    fun confirmWrite() {
        val pending = _state.value.confirm ?: return
        val accountId = openAccountId() ?: _state.value.categoryTarget?.accountId ?: return
        val stay = _state.value.route !is ShellRoute.PickCategory
        launchWrite { id ->
            val result =
                withContext(Dispatchers.IO) {
                    when (val retry = pending.retry) {
                        is ForcedWrite.Save -> library.saveTransaction(id, retry.draft)
                        is ForcedWrite.Delete -> library.deleteTransaction(id, retry.transactionId, true)
                        is ForcedWrite.Category ->
                            library.setTransactionCategory(id, retry.transactionId, retry.categoryId, true)
                        is ForcedWrite.Split -> library.splitTransaction(id, retry.transactionId, retry.parts, true)
                        is ForcedWrite.Unsplit -> library.unsplitTransaction(id, retry.transactionId, true)
                        is ForcedWrite.Transfer -> library.transfer(id, retry.draft)
                    }
                }
            publish(result, accountId, stay)
        }
    }

    fun dismissConfirm() {
        _state.update { it.copy(confirm = null) }
    }

    fun create(
        name: String,
        currency: String,
    ) {
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { library.create(name, currency) }) {
                is CreateResult.Rejected -> _state.update { it.copy(createError = result.reason) }
                is CreateResult.Created -> {
                    _state.update { it.copy(createError = null, route = ShellRoute.Home) }
                    reload()
                }
            }
        }
    }

    fun createSample(datasetId: String) {
        if (!developerMode) return
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { library.createFromMock(datasetId) }) {
                is CreateResult.Rejected -> _state.update { it.copy(createError = result.reason) }
                is CreateResult.Created -> {
                    _state.update { it.copy(createError = null, route = ShellRoute.Home) }
                    reload()
                }
            }
        }
    }

    fun requestSwitch(id: String) {
        val target = _state.value.budgets.find { it.id == id } ?: return
        if (target.id == _state.value.settings.openBudgetId) {
            _state.update { it.copy(route = ShellRoute.Home) }
            return
        }
        _state.update { it.copy(switchTarget = target, pendingFile = null, route = ShellRoute.ConfirmSwitch) }
    }

    fun cancelSwitch() {
        _state.update { it.copy(route = ShellRoute.Budgets, switchTarget = null, pendingFile = null) }
    }

    fun confirmSwitch() {
        val pending = _state.value.pendingFile
        if (pending != null) {
            beginOpen(pending)
            return
        }
        val target = _state.value.switchTarget ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { library.switchTo(target.id) }
            _state.update { it.copy(route = ShellRoute.Home, switchTarget = null) }
            reload()
        }
    }

    fun connect(
        address: String,
        password: String,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(serverError = null, syncing = true, syncProgress = 0f) }
            val result = withContext(Dispatchers.IO) { sync.connect(address, password) }
            _state.update {
                it.copy(
                    syncing = false,
                    syncProgress = null,
                    serverError = result.error,
                    remoteFiles = if (result.error == null) result.files else it.remoteFiles,
                    settings = library.settings(),
                    signedIn = sync.signedIn(),
                )
            }
        }
    }

    fun signOut() {
        sync.signOut()
        _state.update { it.copy(remoteFiles = emptyList(), serverError = null, route = ShellRoute.Server, signedIn = false) }
    }

    fun requestRemote(file: RemoteFile) {
        if (file.encryptKeyId != null && !sync.isUnlocked(file.fileId)) {
            _state.update {
                it.copy(route = ShellRoute.BudgetPassword, pendingFile = file, passwordError = null, askEachTime = true)
            }
            return
        }
        confirmOrOpen(file)
    }

    fun submitPassword(
        password: String,
        askEachTime: Boolean,
    ) {
        val file = _state.value.pendingFile
        val openId = _state.value.settings.openBudgetId
        viewModelScope.launch {
            if (file != null) {
                when (val result = withContext(Dispatchers.IO) { sync.unlock(file, password, askEachTime) }) {
                    is UnlockResult.Failed -> _state.update { it.copy(passwordError = result.reason) }
                    UnlockResult.Unlocked -> confirmOrOpen(file)
                }
            } else if (openId != null) {
                val remote =
                    RemoteFile(
                        fileId = openId,
                        groupId = "",
                        name =
                            _state.value.budgets
                                .find { it.id == openId }
                                ?.name
                                .orEmpty(),
                        encryptKeyId = "local",
                    )
                when (val result = withContext(Dispatchers.IO) { sync.unlock(remote, password, askEachTime) }) {
                    is UnlockResult.Failed -> _state.update { it.copy(passwordError = result.reason) }
                    UnlockResult.Unlocked -> {
                        _state.update { it.copy(locked = false, passwordError = null, route = ShellRoute.Home) }
                        reload()
                    }
                }
            }
        }
    }

    fun setAskEachTime(value: Boolean) {
        _state.update { it.copy(askEachTime = value) }
    }

    fun syncNow() {
        val id = _state.value.settings.openBudgetId ?: return
        viewModelScope.launch {
            _state.update { it.copy(syncing = true, syncProgress = 0.08f) }
            withContext(Dispatchers.IO) { runCatching { sync.sync(id) } }
            _state.update { it.copy(syncing = false, syncProgress = null) }
            reload()
        }
    }

    fun uploadNew() {
        val id = _state.value.settings.openBudgetId ?: return
        viewModelScope.launch {
            _state.update { it.copy(syncing = true, syncProgress = 0.08f, serverError = null) }
            val result = withContext(Dispatchers.IO) { sync.uploadNew(id) }
            _state.update {
                it.copy(
                    syncing = false,
                    syncProgress = null,
                    serverError = (result as? UploadResult.Failed)?.reason,
                    remoteFiles = sync.remoteFiles,
                    signedIn = sync.signedIn(),
                )
            }
        }
    }

    fun requestReplace(file: RemoteFile) {
        _state.update { it.copy(route = ShellRoute.ConfirmReplace, replaceFile = file) }
    }

    fun cancelReplace() {
        _state.update { it.copy(route = ShellRoute.Server, replaceFile = null) }
    }

    fun confirmReplace() {
        val file = _state.value.replaceFile ?: return
        val id = _state.value.settings.openBudgetId ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { sync.uploadOnto(id, file) }
            _state.update {
                it.copy(
                    route = ShellRoute.Server,
                    replaceFile = null,
                    serverError = (result as? UploadResult.Failed)?.reason,
                    remoteFiles = sync.remoteFiles,
                    signedIn = sync.signedIn(),
                )
            }
        }
    }

    fun assign(
        categoryId: String,
        amountText: String,
    ) {
        val shell = _state.value.shell ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { sync.assign(shell.budgetId, categoryId, shell.month, amountText) }
            when (result) {
                is EditResult.Rejected -> _state.update { it.copy(editError = result.reason) }
                EditResult.Saved -> {
                    _state.update { it.copy(editError = null, route = ShellRoute.Home) }
                    reload()
                    withContext(Dispatchers.IO) { runCatching { sync.sync(shell.budgetId) } }
                    reload()
                }
            }
        }
    }

    fun addTransaction(
        payee: String,
        amountText: String,
    ) {
        val shell = _state.value.shell ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { sync.addTransaction(shell.budgetId, payee, amountText) }
            when (result) {
                is EditResult.Rejected -> _state.update { it.copy(editError = result.reason) }
                EditResult.Saved -> {
                    _state.update { it.copy(editError = null, route = ShellRoute.Home) }
                    reload()
                    withContext(Dispatchers.IO) { runCatching { sync.sync(shell.budgetId) } }
                    reload()
                }
            }
        }
    }

    fun setTheme(mode: String) {
        saveSettings(_state.value.settings.copy(themeMode = mode))
    }

    fun setAccent(id: String) {
        saveSettings(_state.value.settings.copy(accent = id))
    }

    private fun confirmOrOpen(file: RemoteFile) {
        val current = _state.value.settings.openBudgetId
        if (current != null && current != file.fileId) {
            _state.update {
                it.copy(
                    route = ShellRoute.ConfirmSwitch,
                    pendingFile = file,
                    switchTarget = BudgetSummary(file.fileId, file.name, ""),
                )
            }
        } else {
            beginOpen(file)
        }
    }

    private fun beginOpen(file: RemoteFile) {
        viewModelScope.launch {
            _state.update { it.copy(syncing = true, syncProgress = 0f, serverError = null) }
            val result =
                withContext(Dispatchers.IO) {
                    sync.open(file) { received, total ->
                        _state.update { it.copy(syncing = true, syncProgress = syncFraction(received, total)) }
                    }
                }
            when (result) {
                OpenResult.Opened -> {
                    _state.update {
                        it.copy(
                            syncing = false,
                            syncProgress = null,
                            pendingFile = null,
                            locked = false,
                            route = ShellRoute.Home,
                        )
                    }
                    reload()
                }
                OpenResult.NeedsPassword ->
                    _state.update {
                        it.copy(syncing = false, syncProgress = null, route = ShellRoute.BudgetPassword, pendingFile = file)
                    }
                is OpenResult.Failed ->
                    _state.update {
                        it.copy(syncing = false, syncProgress = null, serverError = result.reason, route = ShellRoute.Server)
                    }
            }
        }
    }

    private fun saveSettings(settings: PhoneSettings) {
        _state.update { it.copy(settings = settings) }
        viewModelScope.launch(Dispatchers.IO) { library.save(settings) }
    }

    private suspend fun reload() {
        val accountId = (_state.value.route as? ShellRoute.Register)?.accountId
        apply(load(accountId), accountId)
    }

    private fun push(route: ShellRoute) {
        _state.update {
            history.addLast(it.route)
            it.copy(route = route, writeError = null, confirm = null)
        }
    }

    private fun openAccountId(): String? =
        when (val route = _state.value.route) {
            is ShellRoute.Register -> route.accountId
            is ShellRoute.EditTransaction -> route.accountId
            is ShellRoute.SplitTransaction -> route.accountId
            is ShellRoute.TransferMoney -> route.accountId
            is ShellRoute.Reconcile -> route.accountId
            else ->
                _state.value.register
                    ?.account
                    ?.id
        }

    private fun reconcileAccountId(): String? =
        when (val route = _state.value.route) {
            is ShellRoute.Reconcile -> route.accountId
            else -> openAccountId()
        }

    private fun reloadReconcile(accountId: String) {
        val budgetId = _state.value.settings.openBudgetId ?: return
        viewModelScope.launch {
            val page = withContext(Dispatchers.IO) { library.readReconcile(budgetId, accountId) }
            _state.update { it.copy(reconcile = page, reconcileError = null) }
        }
    }

    private fun refreshReview() {
        val budgetId = _state.value.settings.openBudgetId ?: return
        val month = _state.value.reviewMonth
        viewModelScope.launch {
            val review = withContext(Dispatchers.IO) { library.readMonthReview(budgetId, month) }
            _state.update { it.copy(monthReview = review) }
        }
    }

    private fun refreshNetWorth() {
        val budgetId = _state.value.settings.openBudgetId ?: return
        val include = _state.value.settings.netWorthIncludeOffBudget
        viewModelScope.launch {
            val page = withContext(Dispatchers.IO) { library.readNetWorth(budgetId, include) }
            _state.update { it.copy(netWorth = page) }
        }
    }

    private fun launchWrite(block: suspend (String) -> Unit) {
        if (writing) return
        val budgetId = _state.value.settings.openBudgetId ?: return
        writing = true
        viewModelScope.launch {
            try {
                block(budgetId)
            } finally {
                writing = false
            }
        }
    }

    private suspend fun publish(
        result: WriteResult,
        accountId: String,
        stayOnRegister: Boolean,
    ) {
        when (result) {
            is WriteResult.Saved -> {
                if (stayOnRegister) {
                    history.clear()
                    history.addLast(ShellRoute.Home)
                    _state.update {
                        it.copy(
                            route = ShellRoute.Register(accountId),
                            writeError = null,
                            confirm = null,
                            registerFilter = "",
                        )
                    }
                } else {
                    history.clear()
                    _state.update {
                        it.copy(route = ShellRoute.Home, writeError = null, confirm = null, categoryTarget = null)
                    }
                }
                apply(load(accountId), accountId)
            }
            is WriteResult.Rejected -> _state.update { it.copy(writeError = result.reason, confirm = null) }
            is WriteResult.Confirm -> _state.update { it.copy(confirm = result, writeError = null) }
        }
    }

    private fun launchSchedule(
        scheduleId: String,
        forceDuplicate: Boolean,
    ) {
        val budgetId = _state.value.settings.openBudgetId ?: return
        viewModelScope.launch {
            when (
                withContext(Dispatchers.IO) {
                    library.postSchedule(budgetId, scheduleId, forceDuplicate)
                }
            ) {
                is ScheduleWriteResult.Posted -> refresh()
                is ScheduleWriteResult.ConfirmDuplicate ->
                    _state.update { it.copy(scheduleDuplicateId = scheduleId) }
                is ScheduleWriteResult.Rejected -> Unit
                is ScheduleWriteResult.Skipped -> refresh()
            }
        }
    }

    private suspend fun load(accountId: String?): Loaded =
        withContext(Dispatchers.IO) {
            val settings = library.settings()
            val budgets = library.list()
            val budgetId = settings.openBudgetId
            val locked = budgetId != null && sync.needsPassword(budgetId)
            val viewMonth = _state.value.budgetMonth
            val shell = if (budgetId == null || locked) null else library.readShell(budgetId, viewMonth)
            val upcoming =
                if (budgetId == null || locked) {
                    emptyList()
                } else {
                    library.readUpcomingSchedules(budgetId)
                }
            val register =
                if (budgetId != null && accountId != null && !locked) {
                    library.readRegister(budgetId, accountId)
                } else {
                    null
                }
            val reviewMonth = _state.value.reviewMonth
            val monthReview =
                if (budgetId == null || locked) {
                    null
                } else {
                    library.readMonthReview(budgetId, reviewMonth)
                }
            val netWorth =
                if (budgetId == null || locked) {
                    null
                } else {
                    library.readNetWorth(budgetId, settings.netWorthIncludeOffBudget)
                }
            val status = if (budgetId == null) "" else sync.status(budgetId)
            val conflicts = if (budgetId == null || locked) emptyList() else sync.conflicts(budgetId)
            val bankLinked =
                budgetId != null &&
                    accountId != null &&
                    !locked &&
                    library.bankLinked(budgetId, accountId)
            Loaded(
                settings,
                budgets,
                shell,
                register,
                upcoming,
                monthReview,
                netWorth,
                locked,
                status,
                conflicts,
                bankLinked,
            )
        }

    private fun apply(
        loaded: Loaded,
        accountId: String?,
    ) {
        if (loaded.shell != null) shellGate.offer(loaded.shell)
        if (loaded.register != null) registerGate.offer(loaded.register)
        _state.update { current ->
            val shell = if (loaded.locked || shellGate.gestureActive) current.shell else loaded.shell
            val visibleShell = if (loaded.locked) null else shell
            val register =
                when {
                    loaded.locked -> null
                    accountId == null -> current.register
                    registerGate.gestureActive -> current.register
                    else -> loaded.register
                }
            val route =
                when {
                    loaded.locked -> ShellRoute.BudgetPassword
                    visibleShell == null && current.route == ShellRoute.Home -> ShellRoute.Create
                    else -> current.route
                }
            val reviewMonth =
                if (current.monthReview == null && loaded.shell != null) {
                    loaded.shell.month
                } else {
                    current.reviewMonth
                }
            current.copy(
                loading = false,
                settings = loaded.settings,
                budgets = loaded.budgets,
                shell = visibleShell,
                register = register,
                upcoming = loaded.upcoming,
                route = route,
                reviewMonth = reviewMonth,
                monthReview = loaded.monthReview,
                netWorth = loaded.netWorth,
                locked = loaded.locked,
                syncStatus = loaded.status,
                conflicts = loaded.conflicts,
                remoteFiles = sync.remoteFiles,
                signedIn = sync.signedIn(),
                registerBankLinked = loaded.bankLinked,
            )
        }
    }

    private fun ShellRoute.isRegisterFlow(): Boolean =
        this is ShellRoute.Register ||
            this is ShellRoute.EditTransaction ||
            this is ShellRoute.SplitTransaction ||
            this is ShellRoute.TransferMoney ||
            this is ShellRoute.PickCategory ||
            this is ShellRoute.Reconcile ||
            this is ShellRoute.ImportPreviewRoute ||
            this is ShellRoute.ImportReviewRoute

    private fun ShellRoute.isHomeFlow(): Boolean =
        this is ShellRoute.ReviewCategory ||
            this is ShellRoute.GroupCategories ||
            this is ShellRoute.CategoryBudget ||
            this is ShellRoute.MoveCategory ||
            this is ShellRoute.HoldMonth ||
            this is ShellRoute.ManageCategories

    private fun refreshBudgetShell() {
        val budgetId = _state.value.settings.openBudgetId ?: return
        if (_state.value.locked) return
        viewModelScope.launch {
            val month = _state.value.budgetMonth
            val shell = withContext(Dispatchers.IO) { library.readShell(budgetId, month) } ?: return@launch
            shellGate.offer(shell)
            _state.update { current ->
                current.copy(shell = if (shellGate.gestureActive) current.shell else shell)
            }
        }
    }

    private fun applyEnvelopeEdit(
        stayOnRoute: Boolean = true,
        optimistic: ((MonthShell) -> MonthShell)? = null,
        block: suspend () -> EditResult,
    ) {
        val budgetId = _state.value.shell?.budgetId ?: _state.value.settings.openBudgetId ?: return
        val previousShell = _state.value.shell
        if (optimistic != null && previousShell != null) {
            _state.update { it.copy(shell = optimistic(previousShell), editError = null) }
        }
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { block() }) {
                is EditResult.Rejected -> {
                    val month = _state.value.budgetMonth
                    val shell = withContext(Dispatchers.IO) { library.readShell(budgetId, month) }
                    _state.update { current ->
                        current.copy(
                            editError = result.reason,
                            shell =
                                if (shell != null && !shellGate.gestureActive) {
                                    shell
                                } else {
                                    current.shell
                                },
                        )
                    }
                }
                EditResult.Saved -> {
                    _state.update { it.copy(editError = null) }
                    val month = _state.value.budgetMonth
                    val shell = withContext(Dispatchers.IO) { library.readShell(budgetId, month) }
                    if (shell != null) {
                        shellGate.offer(shell)
                        _state.update { current ->
                            current.copy(shell = if (shellGate.gestureActive) current.shell else shell)
                        }
                    }
                    if (_state.value.route is ShellRoute.ManageCategories) {
                        val manage = withContext(Dispatchers.IO) { sync.readCategoryManage(budgetId) }
                        _state.update { it.copy(categoryManage = manage) }
                    }
                    if (!stayOnRoute) back()
                    withContext(Dispatchers.IO) { runCatching { sync.sync(budgetId) } }
                    val refreshed = withContext(Dispatchers.IO) { library.readShell(budgetId, month) }
                    if (refreshed != null) {
                        shellGate.offer(refreshed)
                        _state.update { current ->
                            current.copy(shell = if (shellGate.gestureActive) current.shell else refreshed)
                        }
                    }
                }
            }
        }
    }

    private data class Loaded(
        val settings: PhoneSettings,
        val budgets: List<BudgetSummary>,
        val shell: MonthShell?,
        val register: RegisterPage?,
        val upcoming: List<UpcomingScheduleRow>,
        val monthReview: MonthReviewPage?,
        val netWorth: NetWorthPage?,
        val locked: Boolean,
        val status: String,
        val conflicts: List<BudgetConflict>,
        val bankLinked: Boolean = false,
    )

    companion object {
        fun factory(
            library: BudgetLibrary,
            sync: SyncCoordinator,
            developerMode: Boolean = false,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return ShellViewModel(library, sync, developerMode = developerMode) as T
                }
            }
    }
}
