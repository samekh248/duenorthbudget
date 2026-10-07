package app.duenorth.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.duenorth.budget.core.BudgetBook
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.BudgetSummary
import app.duenorth.budget.core.CreateResult
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.MonthEdits
import app.duenorth.budget.core.MonthPage
import app.duenorth.budget.core.MonthResult
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.PhoneSettings
import app.duenorth.budget.core.RefreshGate
import app.duenorth.budget.core.ShellCopy
import app.duenorth.budget.core.month
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.YearMonth
import java.util.UUID

sealed interface ShellRoute {
    data object Home : ShellRoute

    data object Budgets : ShellRoute

    data object Create : ShellRoute

    data object ConfirmSwitch : ShellRoute

    data object Appearance : ShellRoute

    data class Group(
        val id: String,
    ) : ShellRoute

    data class Category(
        val id: String,
        val groupId: String,
    ) : ShellRoute

    data object Hold : ShellRoute

    data object AddGroup : ShellRoute
}

data class ShellUiState(
    val loading: Boolean = true,
    val route: ShellRoute = ShellRoute.Home,
    val settings: PhoneSettings = PhoneSettings(),
    val budgets: List<BudgetSummary> = emptyList(),
    val shell: MonthShell? = null,
    val book: BudgetBook? = null,
    val page: MonthPage? = null,
    val createError: String? = null,
    val notice: String? = null,
    val switchTarget: BudgetSummary? = null,
)

class ShellViewModel(
    private val library: BudgetLibrary,
) : ViewModel() {
    private val gate = RefreshGate<MonthShell>()
    private val io = Mutex()
    private var epoch = 0
    private var viewing: YearMonth? = null
    private var monthPinned = false
    private val _state = MutableStateFlow(ShellUiState())
    val state: StateFlow<ShellUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val seen = epoch
        viewModelScope.launch {
            val loaded = io.withLock { withContext(Dispatchers.IO) { snapshot() } }
            if (seen != epoch) return@launch
            if (!monthPinned) viewing = loaded.today
            val page = loaded.book?.month(viewing ?: loaded.today)
            if (page != null) gate.offer(page.shell)
            _state.update { current ->
                val shell = if (gate.gestureActive) current.shell else page?.shell
                val route =
                    if (shell == null && current.route == ShellRoute.Home) {
                        ShellRoute.Create
                    } else {
                        current.route
                    }
                current.copy(
                    loading = false,
                    settings = loaded.settings,
                    budgets = loaded.budgets,
                    book = loaded.book,
                    page = page,
                    shell = shell,
                    route = route,
                )
            }
        }
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

    fun back(): Boolean {
        val route = _state.value.route
        if (route == ShellRoute.Home) return false
        if (route == ShellRoute.Create && _state.value.shell == null) return false
        if (route is ShellRoute.Category) {
            _state.update { it.copy(route = ShellRoute.Group(route.groupId), notice = null) }
            return true
        }
        _state.update { it.copy(route = ShellRoute.Home, createError = null, switchTarget = null, notice = null) }
        return true
    }

    fun previousMonth() = shiftMonth(-1)

    fun nextMonth() = shiftMonth(1)

    fun showGroup(id: String) {
        _state.update { it.copy(route = ShellRoute.Group(id), notice = null) }
    }

    fun showCategory(id: String) {
        val category =
            _state.value.page
                ?.categories
                ?.find { it.id == id } ?: return
        _state.update { it.copy(route = ShellRoute.Category(id, category.groupId), notice = null) }
    }

    fun showHold() {
        if (_state.value.book?.mode != BudgetMode.ENVELOPE) return
        _state.update { it.copy(route = ShellRoute.Hold, notice = null) }
    }

    fun showAddGroup() {
        _state.update { it.copy(route = ShellRoute.AddGroup, notice = null) }
    }

    fun assign(
        categoryId: String,
        text: String,
    ) {
        val book = _state.value.book ?: return
        val month = viewing ?: return
        val amount = MoneyFormat.parse(text, book.currency)
        if (amount == null) {
            _state.update { it.copy(notice = ShellCopy.ENTER_AMOUNT) }
            return
        }
        edit { MonthEdits.assign(it, month, categoryId, amount) }
    }

    fun move(
        fromId: String,
        toId: String,
        text: String,
    ) {
        val book = _state.value.book ?: return
        val month = viewing ?: return
        val amount = MoneyFormat.parse(text, book.currency)
        if (amount == null || amount <= 0L) {
            _state.update { it.copy(notice = ShellCopy.ENTER_AMOUNT) }
            return
        }
        edit { MonthEdits.move(it, month, fromId, toId, amount) }
    }

    fun carryover(
        categoryId: String,
        enabled: Boolean,
    ) {
        val month = viewing ?: return
        edit { MonthEdits.carryover(it, month, categoryId, enabled) }
    }

    fun hold(text: String) {
        val book = _state.value.book ?: return
        val month = viewing ?: return
        val amount = MoneyFormat.parse(text, book.currency)
        if (amount == null) {
            _state.update { it.copy(notice = ShellCopy.ENTER_AMOUNT) }
            return
        }
        edit { MonthEdits.hold(it, month, amount) }
    }

    fun releaseHold() {
        val month = viewing ?: return
        edit { MonthEdits.hold(it, month, 0) }
    }

    fun addGroup(name: String) {
        if (edit { MonthEdits.addGroup(it, name, UUID.randomUUID().toString()) }) {
            _state.update { it.copy(route = ShellRoute.Home) }
        }
    }

    fun renameGroup(
        id: String,
        name: String,
    ) {
        edit { MonthEdits.renameGroup(it, id, name) }
    }

    fun moveGroup(
        id: String,
        direction: Int,
    ) {
        edit { MonthEdits.moveGroup(it, id, direction) }
    }

    fun deleteGroup(id: String) {
        if (edit { MonthEdits.deleteGroup(it, id) }) {
            _state.update { it.copy(route = ShellRoute.Home) }
        }
    }

    fun addCategory(
        groupId: String,
        name: String,
    ) {
        edit { MonthEdits.addCategory(it, groupId, name, UUID.randomUUID().toString()) }
    }

    fun hideCategory(
        id: String,
        hidden: Boolean,
    ) {
        edit { MonthEdits.hidden(it, id, hidden) }
        if (hidden) {
            val groupId = (_state.value.route as? ShellRoute.Category)?.groupId
            if (groupId != null) _state.update { it.copy(route = ShellRoute.Group(groupId)) }
        }
    }

    fun deleteCategory(id: String) {
        val groupId = (_state.value.route as? ShellRoute.Category)?.groupId
        if (edit { MonthEdits.deleteCategory(it, id) } && groupId != null) {
            _state.update { it.copy(route = ShellRoute.Group(groupId)) }
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
                    monthPinned = false
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
            monthPinned = false
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

    private fun shiftMonth(delta: Long) {
        val book = _state.value.book ?: return
        val current = viewing ?: return
        viewing = current.plusMonths(delta)
        monthPinned = true
        publish(book, notice = null)
    }

    private fun edit(block: (BudgetBook) -> MonthResult): Boolean {
        val book = _state.value.book ?: return false
        return when (val result = block(book)) {
            is MonthResult.Refused -> {
                _state.update { it.copy(notice = result.reason) }
                false
            }
            is MonthResult.Applied -> {
                epoch += 1
                publish(result.book, notice = null)
                viewModelScope.launch {
                    io.withLock {
                        val latest = _state.value.book ?: return@withLock
                        withContext(Dispatchers.IO) { library.saveBook(latest) }
                    }
                }
                true
            }
        }
    }

    private fun publish(
        book: BudgetBook,
        notice: String?,
    ) {
        val month = viewing ?: library.currentMonth()
        viewing = month
        val page = book.month(month)
        gate.offer(page.shell)
        _state.update { it.copy(book = book, page = page, shell = page.shell, notice = notice) }
    }

    private fun snapshot(): Loaded {
        val settings = library.settings()
        val budgets = library.list()
        val book = settings.openBudgetId?.let { library.readBook(it) }
        return Loaded(settings, budgets, book, library.currentMonth())
    }

    private data class Loaded(
        val settings: PhoneSettings,
        val budgets: List<BudgetSummary>,
        val book: BudgetBook?,
        val today: YearMonth,
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
