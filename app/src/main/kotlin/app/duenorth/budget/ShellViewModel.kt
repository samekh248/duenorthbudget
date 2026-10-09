package app.duenorth.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.duenorth.budget.core.BudgetConflict
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.BudgetSummary
import app.duenorth.budget.core.CategoryChoice
import app.duenorth.budget.core.CategoryTarget
import app.duenorth.budget.core.CreateResult
import app.duenorth.budget.core.EditResult
import app.duenorth.budget.core.ForcedWrite
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.OpenResult
import app.duenorth.budget.core.PhoneSettings
import app.duenorth.budget.core.RefreshGate
import app.duenorth.budget.core.RegisterEntry
import app.duenorth.budget.core.RegisterPage
import app.duenorth.budget.core.RemoteFile
import app.duenorth.budget.core.SplitPart
import app.duenorth.budget.core.SyncCoordinator
import app.duenorth.budget.core.TransactionDraft
import app.duenorth.budget.core.TransferDraft
import app.duenorth.budget.core.UnlockResult
import app.duenorth.budget.core.UploadResult
import app.duenorth.budget.core.WriteResult
import app.duenorth.budget.core.syncFraction
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
}

data class ShellUiState(
    val loading: Boolean = true,
    val route: ShellRoute = ShellRoute.Home,
    val settings: PhoneSettings = PhoneSettings(),
    val budgets: List<BudgetSummary> = emptyList(),
    val shell: MonthShell? = null,
    val register: RegisterPage? = null,
    val registerFilter: String = "",
    val categoryTarget: CategoryTarget? = null,
    val createError: String? = null,
    val writeError: String? = null,
    val confirm: WriteResult.Confirm? = null,
    val switchTarget: BudgetSummary? = null,
    val today: String = RegisterEntry.todayIso(),
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
)

class ShellViewModel(
    private val library: BudgetLibrary,
    private val sync: SyncCoordinator,
) : ViewModel() {
    private val shellGate = RefreshGate<MonthShell>()
    private val registerGate = RefreshGate<RegisterPage>()
    private val history = ArrayDeque<ShellRoute>()
    private val _state = MutableStateFlow(ShellUiState())
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

    fun showAppearance() {
        history.clear()
        _state.update { it.copy(route = ShellRoute.Appearance, confirm = null) }
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
        if (current.route.isRegisterFlow()) {
            val previous = if (history.isEmpty()) ShellRoute.Home else history.removeLast()
            _state.update { it.copy(route = previous, writeError = null) }
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
            else ->
                _state.value.register
                    ?.account
                    ?.id
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

    private suspend fun load(accountId: String?): Loaded =
        withContext(Dispatchers.IO) {
            val settings = library.settings()
            val budgets = library.list()
            val budgetId = settings.openBudgetId
            val locked = budgetId != null && sync.needsPassword(budgetId)
            val shell = if (budgetId == null || locked) null else library.readShell(budgetId)
            val register =
                if (budgetId != null && accountId != null && !locked) {
                    library.readRegister(budgetId, accountId)
                } else {
                    null
                }
            val status = if (budgetId == null) "" else sync.status(budgetId)
            val conflicts = if (budgetId == null || locked) emptyList() else sync.conflicts(budgetId)
            Loaded(settings, budgets, shell, register, locked, status, conflicts)
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
            current.copy(
                loading = false,
                settings = loaded.settings,
                budgets = loaded.budgets,
                shell = visibleShell,
                register = register,
                route = route,
                locked = loaded.locked,
                syncStatus = loaded.status,
                conflicts = loaded.conflicts,
                remoteFiles = sync.remoteFiles,
                signedIn = sync.signedIn(),
            )
        }
    }

    private fun ShellRoute.isRegisterFlow(): Boolean =
        this is ShellRoute.Register ||
            this is ShellRoute.EditTransaction ||
            this is ShellRoute.SplitTransaction ||
            this is ShellRoute.TransferMoney ||
            this is ShellRoute.PickCategory

    private data class Loaded(
        val settings: PhoneSettings,
        val budgets: List<BudgetSummary>,
        val shell: MonthShell?,
        val register: RegisterPage?,
        val locked: Boolean,
        val status: String,
        val conflicts: List<BudgetConflict>,
    )

    companion object {
        fun factory(
            library: BudgetLibrary,
            sync: SyncCoordinator,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return ShellViewModel(library, sync) as T
                }
            }
    }
}
