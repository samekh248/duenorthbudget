package app.duenorth.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.BudgetSummary
import app.duenorth.budget.core.CreateResult
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.PhoneSettings
import app.duenorth.budget.core.RefreshGate
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
}

data class ShellUiState(
    val loading: Boolean = true,
    val route: ShellRoute = ShellRoute.Home,
    val settings: PhoneSettings = PhoneSettings(),
    val budgets: List<BudgetSummary> = emptyList(),
    val shell: MonthShell? = null,
    val createError: String? = null,
    val switchTarget: BudgetSummary? = null,
)

class ShellViewModel(
    private val library: BudgetLibrary,
) : ViewModel() {
    private val gate = RefreshGate<MonthShell>()
    private val _state = MutableStateFlow(ShellUiState())
    val state: StateFlow<ShellUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { snapshot() }
            if (loaded.shell != null) gate.offer(loaded.shell)
            _state.update { current ->
                val shell = if (gate.gestureActive) current.shell else loaded.shell
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
        _state.update { it.copy(route = ShellRoute.Home, createError = null, switchTarget = null) }
        return true
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

    private fun snapshot(): Loaded {
        val settings = library.settings()
        val budgets = library.list()
        val shell = settings.openBudgetId?.let { library.readShell(it) }
        return Loaded(settings, budgets, shell)
    }

    private data class Loaded(
        val settings: PhoneSettings,
        val budgets: List<BudgetSummary>,
        val shell: MonthShell?,
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
