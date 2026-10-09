package app.duenorth.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.BudgetSummary
import app.duenorth.budget.core.CategoryTarget
import app.duenorth.budget.core.CreateResult
import app.duenorth.budget.core.ForcedWrite
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.PhoneSettings
import app.duenorth.budget.core.RefreshGate
import app.duenorth.budget.core.RegisterEntry
import app.duenorth.budget.core.RegisterPage
import app.duenorth.budget.core.SplitPart
import app.duenorth.budget.core.TransactionDraft
import app.duenorth.budget.core.TransferDraft
import app.duenorth.budget.core.CategoryMonthPage
import app.duenorth.budget.core.MonthReviewPage
import app.duenorth.budget.core.NetWorthPage
import app.duenorth.budget.core.ReconcileFinishResult
import app.duenorth.budget.core.ReconcilePage
import app.duenorth.budget.core.ReconcileStartResult
import app.duenorth.budget.core.WriteResult
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

    data class Reconcile(
        val accountId: String,
    ) : ShellRoute

    data class ReviewCategory(
        val categoryId: String,
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
    val reviewMonth: YearMonth = YearMonth.now(),
    val monthReview: MonthReviewPage? = null,
    val netWorth: NetWorthPage? = null,
    val categoryMonth: CategoryMonthPage? = null,
    val reconcile: ReconcilePage? = null,
    val reconcileError: String? = null,
)

class ShellViewModel(
    private val library: BudgetLibrary,
) : ViewModel() {
    private val shellGate = RefreshGate<MonthShell>()
    private val registerGate = RefreshGate<RegisterPage>()
    private val history = ArrayDeque<ShellRoute>()
    private val _state = MutableStateFlow(ShellUiState())
    val state: StateFlow<ShellUiState> = _state.asStateFlow()
    private var writing = false

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val accountId = (_state.value.route as? ShellRoute.Register)?.accountId
            apply(load(accountId), accountId)
        }
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

    fun back(): Boolean {
        val current = _state.value
        if (current.confirm != null) {
            _state.update { it.copy(confirm = null) }
            return true
        }
        if (current.route == ShellRoute.Home) return false
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
        _state.update { it.copy(route = ShellRoute.Home, createError = null, switchTarget = null, writeError = null) }
        return true
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
                    refresh()
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
        _state.update { it.copy(switchTarget = target, route = ShellRoute.ConfirmSwitch) }
    }

    fun cancelSwitch() {
        _state.update { it.copy(route = ShellRoute.Budgets, switchTarget = null) }
    }

    fun confirmSwitch() {
        val target = _state.value.switchTarget ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { library.switchTo(target.id) }
            _state.update { it.copy(route = ShellRoute.Home, switchTarget = null) }
            refresh()
        }
    }

    fun setTheme(mode: String) {
        saveSettings(_state.value.settings.copy(themeMode = mode))
    }

    fun setAccent(id: String) {
        saveSettings(_state.value.settings.copy(accent = id))
    }

    private fun saveSettings(settings: PhoneSettings) {
        _state.update { it.copy(settings = settings) }
        viewModelScope.launch(Dispatchers.IO) { library.save(settings) }
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

    private suspend fun load(accountId: String?): Loaded {
        val settings = withContext(Dispatchers.IO) { library.settings() }
        val budgets = withContext(Dispatchers.IO) { library.list() }
        val budgetId = settings.openBudgetId
        val shell = withContext(Dispatchers.IO) { budgetId?.let { library.readShell(it) } }
        val register =
            withContext(Dispatchers.IO) {
                if (budgetId != null && accountId != null) library.readRegister(budgetId, accountId) else null
            }
        val reviewMonth = _state.value.reviewMonth
        val monthReview =
            withContext(Dispatchers.IO) {
                budgetId?.let { library.readMonthReview(it, reviewMonth) }
            }
        val netWorth =
            withContext(Dispatchers.IO) {
                budgetId?.let { library.readNetWorth(it, settings.netWorthIncludeOffBudget) }
            }
        return Loaded(settings, budgets, shell, register, monthReview, netWorth)
    }

    private fun apply(
        loaded: Loaded,
        accountId: String?,
    ) {
        if (loaded.shell != null) shellGate.offer(loaded.shell)
        if (loaded.register != null) registerGate.offer(loaded.register)
        _state.update { current ->
            val shell = if (shellGate.gestureActive) current.shell else loaded.shell
            val register =
                when {
                    accountId == null -> current.register
                    registerGate.gestureActive -> current.register
                    else -> loaded.register
                }
            val route =
                if (shell == null && current.route == ShellRoute.Home) {
                    ShellRoute.Create
                } else {
                    current.route
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
                shell = shell,
                register = register,
                route = route,
                reviewMonth = reviewMonth,
                monthReview = loaded.monthReview,
                netWorth = loaded.netWorth,
            )
        }
    }

    private fun ShellRoute.isRegisterFlow(): Boolean =
        this is ShellRoute.Register ||
            this is ShellRoute.EditTransaction ||
            this is ShellRoute.SplitTransaction ||
            this is ShellRoute.TransferMoney ||
            this is ShellRoute.Reconcile ||
            this is ShellRoute.PickCategory

    private fun ShellRoute.isHomeFlow(): Boolean = this is ShellRoute.ReviewCategory

    private data class Loaded(
        val settings: PhoneSettings,
        val budgets: List<BudgetSummary>,
        val shell: MonthShell?,
        val register: RegisterPage?,
        val monthReview: MonthReviewPage?,
        val netWorth: NetWorthPage?,
    )

    companion object {
        fun factory(library: BudgetLibrary): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return ShellViewModel(library) as T
                }
            }
    }
}
