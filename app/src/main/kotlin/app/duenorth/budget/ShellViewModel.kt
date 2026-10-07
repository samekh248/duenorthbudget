package app.duenorth.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.duenorth.budget.core.BudgetConflict
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.BudgetSummary
import app.duenorth.budget.core.CategoryChoice
import app.duenorth.budget.core.CreateResult
import app.duenorth.budget.core.EditResult
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.OpenResult
import app.duenorth.budget.core.PhoneSettings
import app.duenorth.budget.core.RefreshGate
import app.duenorth.budget.core.RemoteFile
import app.duenorth.budget.core.SyncCoordinator
import app.duenorth.budget.core.UnlockResult
import app.duenorth.budget.core.UploadResult
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
}

data class ShellUiState(
    val loading: Boolean = true,
    val route: ShellRoute = ShellRoute.Home,
    val settings: PhoneSettings = PhoneSettings(),
    val budgets: List<BudgetSummary> = emptyList(),
    val shell: MonthShell? = null,
    val createError: String? = null,
    val switchTarget: BudgetSummary? = null,
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
    private val gate = RefreshGate<MonthShell>()
    private val _state = MutableStateFlow(ShellUiState())
    val state: StateFlow<ShellUiState> = _state.asStateFlow()

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
            gate.beginGesture()
            return
        }
        if (!gate.gestureActive) return
        gate.endGesture()
        _state.update { it.copy(shell = gate.visible ?: it.shell) }
    }

    fun showBudgets() {
        _state.update { it.copy(route = ShellRoute.Budgets) }
    }

    fun showCreate() {
        _state.update { it.copy(route = ShellRoute.Create, createError = null) }
    }

    fun showAppearance() {
        _state.update { it.copy(route = ShellRoute.Appearance) }
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
        val route = _state.value.route
        if (route == ShellRoute.Home) return false
        if (route == ShellRoute.BudgetPassword && _state.value.locked) return false
        if (route == ShellRoute.Create && _state.value.shell == null) return false
        _state.update {
            it.copy(
                route = ShellRoute.Home,
                createError = null,
                switchTarget = null,
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
            _state.update {
                it.copy(locked = true, shell = null, route = ShellRoute.BudgetPassword, passwordError = null)
            }
        }
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
        val loaded =
            withContext(Dispatchers.IO) {
                val settings = library.settings()
                val budgets = library.list()
                val id = settings.openBudgetId
                val locked = id != null && sync.needsPassword(id)
                val shell = if (id == null || locked) null else library.readShell(id)
                val status = if (id == null) "" else sync.status(id)
                val conflicts = if (id == null || locked) emptyList() else sync.conflicts(id)
                Loaded(settings, budgets, shell, locked, status, conflicts)
            }
        if (loaded.shell != null) gate.offer(loaded.shell)
        _state.update { current ->
            val shell = if (gate.gestureActive) current.shell else loaded.shell
            val route =
                when {
                    loaded.locked -> ShellRoute.BudgetPassword
                    shell == null && current.route == ShellRoute.Home -> ShellRoute.Create
                    else -> current.route
                }
            current.copy(
                loading = false,
                settings = loaded.settings,
                budgets = loaded.budgets,
                shell = shell,
                route = route,
                locked = loaded.locked,
                syncStatus = loaded.status,
                conflicts = loaded.conflicts,
                remoteFiles = sync.remoteFiles,
                signedIn = sync.signedIn(),
            )
        }
    }

    private data class Loaded(
        val settings: PhoneSettings,
        val budgets: List<BudgetSummary>,
        val shell: MonthShell?,
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
