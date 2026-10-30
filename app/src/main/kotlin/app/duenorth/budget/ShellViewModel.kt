package app.duenorth.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.duenorth.budget.core.BankSyncTransport
import app.duenorth.budget.core.BudgetConflict
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.BudgetSummary
import app.duenorth.budget.core.ForcedWrite
import app.duenorth.budget.core.FormatPrefs
import app.duenorth.budget.core.HttpBankSyncTransport
import app.duenorth.budget.core.MonthReviewPage
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.NetWorthPage
import app.duenorth.budget.core.OnboardingProgress
import app.duenorth.budget.core.PhoneSettings
import app.duenorth.budget.core.RefreshGate
import app.duenorth.budget.core.RegisterPage
import app.duenorth.budget.core.SampleBudgets
import app.duenorth.budget.core.ScheduleDetails
import app.duenorth.budget.core.ScheduleSummary
import app.duenorth.budget.core.ServerProbe
import app.duenorth.budget.core.SyncCoordinator
import app.duenorth.budget.core.WriteResult
import app.duenorth.budget.core.activeInstance
import app.duenorth.budget.design.MonthWindow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.time.YearMonth

class ShellViewModel(
    internal val library: BudgetLibrary,
    internal val sync: SyncCoordinator,
    internal val bankSyncTransport: BankSyncTransport = HttpBankSyncTransport(),
    internal val developerMode: Boolean = false,
    internal val probe: ServerProbe = ServerProbe(),
    /** Reschedules the background job when its interval changes in settings. */
    internal val onBackgroundSyncHours: (Int) -> Unit = {},
    /** Where file and network work runs. Tests pass the main thread, so idling its looper runs that work to the end. */
    internal val io: CoroutineDispatcher = Dispatchers.IO,
    /** The sample budgets of this build: [buildSamples], which is null in release builds. */
    sampleSource: SampleBudgets? = buildSamples,
) : ViewModel() {
    /** The samples developer mode offers; none outside it, or in a build without them. */
    internal val samples: SampleBudgets? = sampleSource?.takeIf { developerMode }
    internal val shellGate = RefreshGate<MonthShell>()
    private val registerGate = RefreshGate<RegisterPage>()
    internal val months = OpenMonthStore()
    internal val history = ArrayDeque<ShellRoute>()

    /** Category note saves, written in the order they were made. */
    internal val noteWrites = Mutex()

    /** Budget preference saves, in the order they were made, so two flipped together both land. */
    internal val preferenceWrites = Mutex()
    private val _state =
        MutableStateFlow(
            ShellUiState(
                developerMode = developerMode,
                sampleBudgets = samples?.all.orEmpty(),
                defaultBudgetName = samples?.walkthroughName.orEmpty(),
            ),
        )
    val state: StateFlow<ShellUiState> = _state.asStateFlow()

    /** For the view model's extension files, such as `ShellViewModelMonth.kt`. */
    internal fun mutate(transform: (ShellUiState) -> ShellUiState) = _state.update(transform)

    internal var writing = false

    /** What the feature holders reach the rest of the shell through. */
    private val host: ShellHost =
        object : ShellHost {
            override val state get() = this@ShellViewModel.state
            override val scope get() = viewModelScope
            override val library get() = this@ShellViewModel.library
            override val sync get() = this@ShellViewModel.sync
            override val bankSyncTransport get() = this@ShellViewModel.bankSyncTransport
            override val io get() = this@ShellViewModel.io
            override val writing get() = this@ShellViewModel.writing

            override fun mutate(transform: (ShellUiState) -> ShellUiState) = this@ShellViewModel.mutate(transform)

            override fun push(route: ShellRoute) = this@ShellViewModel.push(route)

            override fun back() {
                this@ShellViewModel.back()
            }

            override fun launchWrite(block: suspend (String) -> Unit) = this@ShellViewModel.launchWrite(block)

            override suspend fun publish(
                result: WriteResult,
                accountId: String,
                stayOnRegister: Boolean,
            ) = this@ShellViewModel.publish(result, accountId, stayOnRegister)

            override suspend fun reload() = this@ShellViewModel.reload()

            override fun loadPayees() = payees.load()

            override fun clearHistory() = history.clear()

            override suspend fun reloadAccount(accountId: String) = apply(load(accountId), accountId)
        }

    /** The inbox: filing, deleting and opening what came in. */
    val inbox: InboxHolder = InboxHolder(host)

    /** Bank sync: every SimpleFIN account fetches, then the budget syncs. */
    val bankSync: BankSyncHolder = BankSyncHolder(host)

    /** Payees: the payees page, renaming and merging, and the names payee boxes offer. */
    val payees: PayeesHolder = PayeesHolder(host)

    /** Importing: a file preview, a bank fetch and the review of what came in. */
    val import: ImportHolder = ImportHolder(host)

    /** The running server check or sign-in, cancelled by back (spec 011 R11). */
    internal var onboardingJob: Job? = null

    /** `onboarding.json` writes, one at a time and in order. */
    internal val onboardingWrites = io.limitedParallelism(1)

    /** When the last sync the screen started without being asked began, in epoch millis; launch syncs at start. */
    private var lastScreenSync = System.currentTimeMillis()

    /** Counts returns to the screen, so a send started on leaving can tell the person came back. */
    private var screenVisits = 0

    private var sendingWrites = false
    private var sendAgain = false

    /** Outlives this view model, so a send started as the app closes still finishes. */
    internal val leaving = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val localChange: (String) -> Unit = { id ->
        viewModelScope.launch { if (_state.value.settings.openBudgetId == id) sendWrites(id) }
    }

    init {
        library.onLocalChange = localChange
        viewModelScope.launch {
            restoreOnboarding()
            reload()
            resumeOnboarding()
            val id = _state.value.settings.openBudgetId ?: return@launch
            if (_state.value.locked) return@launch
            // Launch fetches the banks first, so the sync after sends what they brought to the server.
            // The panel only shows once a linked account starts, so a budget with none never sees it.
            bankSync.run(id, quiet = true)
        }
    }

    /**
     * The app came back on screen: pick up what other devices sent while it was away, quietly, at
     * most once a minute. Edit mode and a locked budget wait, since a sync would change what they show.
     */
    fun onScreenReturned() {
        screenVisits++
        val current = _state.value
        val id = current.settings.openBudgetId ?: return
        if (current.locked || current.syncing || current.budgetEdit != null) return
        val now = System.currentTimeMillis()
        if (now - lastScreenSync < RETURN_SYNC_GAP_MS) return
        lastScreenSync = now
        viewModelScope.launch {
            withContext(io) { runCatching { sync.sync(id) } }
            if (_state.value.settings.openBudgetId == id) reload()
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
        val hit = months.flushPending(gestureActive = false)
        _state.update {
            it.copy(
                shell = if (hit) months.shell ?: shellGate.visible ?: it.shell else shellGate.visible ?: it.shell,
                monthReview = if (hit) months.review ?: it.monthReview else it.monthReview,
                openMonth = months.openMonth,
            )
        }
        if (!hit && months.pendingMonth == null && months.cached(months.openMonth) == null) {
            loadOpenMonth(months.openMonth)
        }
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

    fun selectOpenMonth(month: YearMonth) {
        // Edit mode's changes belong to the month it opened on.
        if (_state.value.budgetEdit != null) return
        val gesture = shellGate.gestureActive
        val pinned = _state.value.pinnedMonth
        val hit = months.select(month, gesture)
        _state.update {
            it.copy(
                openMonth = months.openMonth,
                shell = if (hit && pinned == null) months.shell else it.shell,
                monthReview = if (hit && pinned == null) months.review else it.monthReview,
            )
        }
        if (!hit && !gesture && pinned == null) {
            loadOpenMonth(month)
        }
    }

    fun selectHomeSection(index: Int) {
        // Edit mode belongs to the budget item and holds the pivot still; leaving it anyway drops the changes.
        if (index != ScreenBar.HOME_BUDGET && _state.value.budgetEdit != null) cancelBudgetEdit()
        // Reorder mode belongs to the accounts item, so leaving it ends reorder too.
        _state.update {
            it.copy(
                homeSection = index,
                accountsReorder = it.accountsReorder && index == ScreenBar.HOME_ACCOUNTS,
                inbox = it.inbox.copy(syncNotice = it.inbox.syncNotice.takeIf { index == ScreenBar.HOME_INBOX }),
            )
        }
    }

    fun monthWindow(): MonthWindow = months.window

    internal fun editorMonth(): YearMonth = months.editorMonth(_state.value.pinnedMonth)

    private fun loadOpenMonth(month: YearMonth) {
        val budgetId = _state.value.settings.openBudgetId ?: return
        if (_state.value.locked) return
        viewModelScope.launch {
            val snapshot =
                withContext(io) {
                    val shell = library.readShell(budgetId, month) ?: return@withContext null
                    val review = library.readMonthReview(budgetId, month)
                    MonthSnapshot(month, shell, review)
                } ?: return@launch
            if (month != months.openMonth) return@launch
            months.applyLoaded(snapshot, shellGate.gestureActive)
            if (!shellGate.gestureActive && months.openMonth == month) {
                shellGate.offer(snapshot.shell)
                _state.update {
                    it.copy(
                        shell = months.shell,
                        monthReview = months.review,
                        openMonth = months.openMonth,
                    )
                }
            }
        }
    }

    internal fun warmMonthWindow() {
        val budgetId = _state.value.settings.openBudgetId ?: return
        if (_state.value.locked) return
        months.rebuildWindow(YearMonth.now())
        val windowMonths = months.window.months
        viewModelScope.launch(io) {
            for (month in windowMonths) {
                if (_state.value.settings.openBudgetId != budgetId) return@launch
                if (months.cached(month) != null) continue
                // Warming is best effort: a month that fails here loads when it is opened.
                val snapshot =
                    runCatching {
                        val shell = library.readShell(budgetId, month) ?: return@runCatching null
                        MonthSnapshot(month, shell, library.readMonthReview(budgetId, month))
                    }.getOrElse { return@launch } ?: continue
                months.put(snapshot)
            }
        }
    }

    fun back(): Boolean {
        val current = _state.value
        if (current.registerSelection != null && current.confirm == null) {
            endSelecting()
            return true
        }
        // Back closes the register's search before it leaves the account.
        if (current.registerSearch != null && current.route is ShellRoute.Register && current.confirm == null) {
            closeRegisterSearch()
            return true
        }
        if (current.inbox.selection != null && current.route == ShellRoute.Home && current.confirm == null) {
            inbox.endSelecting()
            return true
        }
        if (current.accountsReorder && current.route == ShellRoute.Home && current.confirm == null) {
            _state.update { it.copy(accountsReorder = false) }
            return true
        }
        // Back is edit mode's cancel: it leaves without saving anything changed there.
        if (current.budgetEdit != null && current.route == ShellRoute.Home && current.confirm == null) {
            cancelBudgetEdit()
            return true
        }
        if (current.confirm != null) {
            _state.update { it.copy(confirm = null) }
            return true
        }
        if (current.fillConfirm != null) {
            _state.update { it.copy(fillConfirm = null) }
            return true
        }
        if (current.templateConfirm != null) {
            _state.update { it.copy(templateConfirm = null) }
            return true
        }
        if (current.templateApplyPreview != null && current.route == ShellRoute.Templates) {
            _state.update { it.copy(templateApplyPreview = null) }
            return true
        }
        if (current.accountForceConfirm != null) {
            _state.update { it.copy(accountForceConfirm = null) }
            return true
        }
        if (current.route == ShellRoute.Home) return false
        if (current.route == ShellRoute.BudgetPassword && current.locked) return false
        if (current.route == ShellRoute.ConfirmReplaceBudget) {
            cancelReplaceBudget()
            return true
        }
        onboardingBack()?.let { return it }
        if (current.settingsReturn && current.route != ShellRoute.Settings) {
            returnToSettings()
            return true
        }
        if (current.route == ShellRoute.Create && current.shell == null) return false
        if (current.route.isRegisterFlow() || current.route.isHomeFlow()) {
            val previous = if (history.isEmpty()) ShellRoute.Home else history.removeLast()
            val leavePin =
                previous == ShellRoute.Home ||
                    !previous.pinsMonth()
            _state.update {
                it.copy(
                    route = previous,
                    writeError = null,
                    categoryMonth = if (previous == ShellRoute.Home) null else it.categoryMonth,
                    pinnedMonth = if (leavePin) null else it.pinnedMonth,
                )
            }
            if (leavePin && current.pinnedMonth != null && current.pinnedMonth != current.openMonth) {
                selectOpenMonth(current.openMonth)
            }
            return true
        }
        history.clear()
        _state.update {
            it.copy(
                // With no budget yet, every other page leads back to the welcome page,
                // unless a download is opening one.
                route = if (it.shell == null && it.onboarding != null && !it.syncing) ShellRoute.Welcome else ShellRoute.Home,
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

    /**
     * The app left the screen: send what the server has not stored, then forget an ask-each-time
     * key. The screen locks at once; the key stays until the send is done, unless the person is
     * back by then and has the password screen in front of them anyway.
     */
    fun onScreenLocked() {
        val id = _state.value.settings.openBudgetId ?: return
        val visit = screenVisits
        if (sync.locksWhenAway(id) || sync.needsPassword(id)) {
            history.clear()
            _state.update {
                it.copy(locked = true, shell = null, register = null, route = ShellRoute.BudgetPassword, passwordError = null)
            }
        }
        leaving.launch {
            withContext(io) { runCatching { sync.sendUnsent(id) } }
            if (screenVisits == visit) sync.lock(id)
        }
    }

    /**
     * Sends a write as soon as it lands. Writes made while a send is out go together in one more
     * send when it returns, so a quick run of edits does not queue a sync each.
     */
    private fun sendWrites(budgetId: String) {
        if (sendingWrites) {
            sendAgain = true
            return
        }
        sendingWrites = true
        viewModelScope.launch {
            try {
                do {
                    sendAgain = false
                    val sent = withContext(io) { runCatching { sync.sendUnsent(budgetId) }.getOrNull() }
                    // A send also brings back what other devices changed; budget edit mode repaints itself.
                    val current = _state.value
                    if (sent != null && current.settings.openBudgetId == budgetId && !current.locked && current.budgetEdit == null) {
                        months.invalidate()
                        val accountId = (current.route as? ShellRoute.Register)?.accountId
                        apply(load(accountId), accountId)
                    }
                } while (sendAgain)
            } finally {
                sendingWrites = false
            }
        }
    }

    override fun onCleared() {
        if (library.onLocalChange === localChange) library.onLocalChange = null
    }

    fun openReviewCategory(categoryId: String) {
        push(ShellRoute.ReviewCategory(categoryId))
        loadCategoryMonth(categoryId)
    }

    /** Reads [categoryId]'s transactions in the open month: the review list, and the category page's recent. */
    fun loadCategoryMonth(categoryId: String) {
        val budgetId = _state.value.settings.openBudgetId ?: return
        val month = _state.value.openMonth
        viewModelScope.launch {
            val page = withContext(io) { library.readCategoryMonth(budgetId, categoryId, month) }
            _state.update { it.copy(categoryMonth = page) }
        }
    }

    fun setNetWorthIncludeOffBudget(include: Boolean) {
        saveSettings(_state.value.settings.copy(netWorthIncludeOffBudget = include))
        refreshNetWorth()
    }

    fun confirmWrite() {
        val pending = _state.value.confirm ?: return
        val inboxAccount =
            when (val retry = pending.retry) {
                is ForcedWrite.Category -> retry.transactionId
                is ForcedWrite.Categories -> retry.assignments.keys.firstOrNull()
                is ForcedWrite.Deletes -> retry.transactionIds.firstOrNull()
                else -> null
            }?.let { id ->
                _state.value.shell
                    ?.inbox
                    ?.firstOrNull { it.id == id }
                    ?.accountId
            }
        val accountId = openAccountId() ?: _state.value.categoryTarget?.accountId ?: inboxAccount ?: return
        // A write confirmed from the picker or the inbox itself goes back home, not to a register.
        val stay = _state.value.route !is ShellRoute.PickCategory && _state.value.route !is ShellRoute.Home
        launchWrite { id ->
            val result =
                withContext(io) {
                    when (val retry = pending.retry) {
                        is ForcedWrite.Save -> library.saveTransaction(id, retry.draft)
                        is ForcedWrite.Delete -> library.deleteTransaction(id, retry.transactionId, true)
                        is ForcedWrite.Deletes -> library.deleteTransactions(id, retry.transactionIds, true)
                        is ForcedWrite.Category ->
                            library.setTransactionCategory(id, retry.transactionId, retry.categoryId, true)
                        is ForcedWrite.Categories -> library.setTransactionsCategory(id, retry.assignments, true)
                        is ForcedWrite.Split -> library.splitTransaction(id, retry.transactionId, retry.parts, true)
                        is ForcedWrite.Unsplit -> library.unsplitTransaction(id, retry.transactionId, true)
                        is ForcedWrite.Cleared ->
                            library.setTransactionsCleared(id, retry.transactionIds, retry.cleared, true)
                    }
                }
            publish(result, accountId, stay)
        }
    }

    fun dismissConfirm() {
        _state.update { it.copy(confirm = null) }
    }

    internal suspend fun reload() {
        val calendar = YearMonth.now()
        months.reset(calendar)
        _state.update { it.copy(openMonth = calendar, pinnedMonth = null) }
        val accountId = (_state.value.route as? ShellRoute.Register)?.accountId
        apply(load(accountId), accountId)
    }

    /** Shows [route] in place of the current page, so back skips the page it replaces. */
    internal fun replaceRoute(route: ShellRoute) {
        _state.update { it.copy(route = route, writeError = null, confirm = null) }
    }

    internal fun push(route: ShellRoute) {
        val pins = route.pinsMonth()
        _state.update {
            history.addLast(it.route)
            it.copy(
                route = route,
                writeError = null,
                confirm = null,
                pinnedMonth = if (pins) it.pinnedMonth ?: it.openMonth else it.pinnedMonth,
            )
        }
    }

    private fun refreshNetWorth() {
        val budgetId = _state.value.settings.openBudgetId ?: return
        val include = _state.value.settings.netWorthIncludeOffBudget
        viewModelScope.launch {
            val page = withContext(io) { library.readNetWorth(budgetId, include) }
            _state.update { it.copy(netWorth = page) }
        }
    }

    internal fun launchWrite(block: suspend (String) -> Unit) {
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

    internal suspend fun publish(
        result: WriteResult,
        accountId: String,
        stayOnRegister: Boolean,
    ) {
        when (result) {
            is WriteResult.Saved -> {
                // Edits started from the inbox go back to it, not to the account's register.
                val toRegister = stayOnRegister && !_state.value.editFromHome
                // A transaction added or fixed from a reconcile goes back to that reconcile.
                val toReconcile = toRegister && history.lastOrNull() == ShellRoute.Reconcile(accountId)
                _state.update { it.copy(editFromHome = false, newTransactionPrefill = null) }
                if (toReconcile) {
                    history.clear()
                    history.addLast(ShellRoute.Home)
                    history.addLast(ShellRoute.Register(accountId))
                    _state.update {
                        it.copy(
                            route = ShellRoute.Reconcile(accountId),
                            writeError = null,
                            confirm = null,
                            registerSelection = null,
                        )
                    }
                    apply(load(accountId), accountId)
                    reloadReconcile(accountId)
                    return
                }
                if (toRegister) {
                    history.clear()
                    history.addLast(ShellRoute.Home)
                    _state.update {
                        it.copy(
                            route = ShellRoute.Register(accountId),
                            writeError = null,
                            confirm = null,
                            registerSelection = null,
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

    internal suspend fun load(accountId: String?): Loaded =
        withContext(io) {
            val settings = library.settings()
            val budgets = library.list()
            val budgetId = settings.openBudgetId
            val locked = budgetId != null && sync.needsPassword(budgetId)
            val viewMonth = editorMonth()
            val shell = if (budgetId == null || locked) null else library.readShell(budgetId, viewMonth)
            val schedules =
                if (budgetId == null || locked) {
                    emptyList()
                } else {
                    library.readSchedules(budgetId)
                }
            val scheduleDetails =
                (_state.value.route as? ShellRoute.ScheduleDetails)?.let { route ->
                    if (budgetId == null || locked) null else library.readScheduleDetails(budgetId, route.scheduleId)
                }
            val register =
                if (budgetId != null && accountId != null && !locked) {
                    library.readRegister(budgetId, accountId)
                } else {
                    null
                }
            val reviewMonth = _state.value.openMonth
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
            val formatPrefs =
                if (budgetId == null || locked) {
                    FormatPrefs()
                } else {
                    library.readFormatPrefs(budgetId)
                }
            val cloudFileId =
                if (budgetId == null || locked) {
                    null
                } else {
                    library
                        .useDatabase(budgetId) { session ->
                            app.duenorth.budget.core.SyncSchema
                                .state(session, "cloudFileId")
                        }?.takeIf { it.isNotBlank() }
                }
            Loaded(
                settings,
                budgets,
                shell,
                register,
                schedules,
                scheduleDetails,
                monthReview,
                netWorth,
                locked,
                status,
                conflicts,
                bankLinked,
                formatPrefs,
                cloudFileId,
                if (budgetId == null || locked) null else library.undoLabel(budgetId),
            )
        }

    internal fun apply(
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
                    visibleShell == null && current.route == ShellRoute.Home ->
                        if (loaded.budgets.isEmpty()) onboardingRoute(current.onboarding?.progress) else ShellRoute.Create
                    else -> current.route
                }
            val reviewMonth =
                if (current.monthReview == null && loaded.shell != null) {
                    loaded.shell.month
                } else {
                    current.openMonth
                }
            if (visibleShell != null && !shellGate.gestureActive) {
                months.applyLoaded(
                    MonthSnapshot(visibleShell.month, visibleShell, loaded.monthReview),
                    gestureActive = false,
                )
            }
            current.copy(
                loading = false,
                onboarding =
                    when {
                        loaded.budgets.isNotEmpty() -> current.onboarding.takeIf { route.isOnboarding() }
                        else -> current.onboarding ?: OnboardingUi(OnboardingProgress())
                    },
                settings = loaded.settings,
                budgets = loaded.budgets,
                shell = visibleShell,
                register = register,
                schedules = loaded.schedules,
                scheduleDetails =
                    (route as? ShellRoute.ScheduleDetails)?.let { open ->
                        (loaded.scheduleDetails ?: current.scheduleDetails)?.takeIf { it.summary.id == open.scheduleId }
                    },
                route = route,
                settingsReturn = current.settingsReturn && route != ShellRoute.Home,
                openMonth = reviewMonth,
                monthReview = loaded.monthReview,
                netWorth = loaded.netWorth,
                locked = loaded.locked,
                syncStatus = loaded.status,
                conflicts = loaded.conflicts,
                remoteFiles = sync.remoteFiles,
                signedIn = sync.signedIn(),
                registerBankLinked = loaded.bankLinked,
                instances = loaded.settings.instances,
                activeInstance = loaded.settings.activeInstance(),
                formatPrefs = loaded.formatPrefs,
                budgetMethod = loaded.formatPrefs.budgetType,
                cloudFileId = loaded.cloudFileId,
                undoLabel = loaded.undoLabel,
            )
        }
        if (!loaded.locked && loaded.shell != null) {
            warmMonthWindow()
        }
        retireFinishedGetStarted()
    }

    private fun ShellRoute.isRegisterFlow(): Boolean =
        this is ShellRoute.Register ||
            this is ShellRoute.EditTransaction ||
            this is ShellRoute.PickCategory ||
            this is ShellRoute.Reconcile ||
            this is ShellRoute.ImportPreviewRoute ||
            this is ShellRoute.ImportReviewRoute

    private fun ShellRoute.isHomeFlow(): Boolean =
        this is StackRoute ||
            this is ShellRoute.ReviewCategory ||
            pinsMonth() ||
            this is ShellRoute.ManageCategories

    /** Editors of one month keep that month while the person swipes elsewhere. */
    private fun ShellRoute.pinsMonth(): Boolean =
        this is ShellRoute.CategoryBudget ||
            this is ShellRoute.MoveCategory ||
            this is ShellRoute.HoldMonth ||
            this is ShellRoute.ToBudget

    internal data class Loaded(
        val settings: PhoneSettings,
        val budgets: List<BudgetSummary>,
        val shell: MonthShell?,
        val register: RegisterPage?,
        val schedules: List<ScheduleSummary>,
        val scheduleDetails: ScheduleDetails?,
        val monthReview: MonthReviewPage?,
        val netWorth: NetWorthPage?,
        val locked: Boolean,
        val status: String,
        val conflicts: List<BudgetConflict>,
        val bankLinked: Boolean = false,
        val formatPrefs: FormatPrefs = FormatPrefs(),
        val cloudFileId: String? = null,
        val undoLabel: String? = null,
    )

    companion object {
        const val OPEN_ID_RETURN = "duenorth://oauth"

        internal fun isOpenIdReturn(callbackUrl: String): Boolean {
            val uri = android.net.Uri.parse(callbackUrl)
            val expected = android.net.Uri.parse(OPEN_ID_RETURN)
            return uri.scheme.equals(expected.scheme, ignoreCase = true) && uri.host.equals(expected.host, ignoreCase = true)
        }

        fun factory(
            library: BudgetLibrary,
            sync: SyncCoordinator,
            developerMode: Boolean = false,
            onBackgroundSyncHours: (Int) -> Unit = {},
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return ShellViewModel(
                        library,
                        sync,
                        developerMode = developerMode,
                        onBackgroundSyncHours = onBackgroundSyncHours,
                    ) as T
                }
            }
    }
}

/** The shortest gap between two syncs the screen starts on its own, as when the app comes back. */
private const val RETURN_SYNC_GAP_MS = 60_000L
