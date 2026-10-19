package app.duenorth.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.duenorth.budget.core.AccountDetail
import app.duenorth.budget.core.ActualMonthMath
import app.duenorth.budget.core.BankSyncTransport
import app.duenorth.budget.core.BudgetConflict
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.BudgetSummary
import app.duenorth.budget.core.CategoryChoice
import app.duenorth.budget.core.CategoryManagePage
import app.duenorth.budget.core.CategoryMonthPage
import app.duenorth.budget.core.CategoryTarget
import app.duenorth.budget.core.CreateResult
import app.duenorth.budget.core.EditResult
import app.duenorth.budget.core.ForcedWrite
import app.duenorth.budget.core.FormatPrefs
import app.duenorth.budget.core.HttpBankSyncTransport
import app.duenorth.budget.core.ImportPreview
import app.duenorth.budget.core.ImportReviewPage
import app.duenorth.budget.core.InboxRow
import app.duenorth.budget.core.MatchPair
import app.duenorth.budget.core.MockBudgetDataset
import app.duenorth.budget.core.MockBudgets
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.MonthReviewPage
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.NetWorthPage
import app.duenorth.budget.core.OnboardingPath
import app.duenorth.budget.core.OnboardingProgress
import app.duenorth.budget.core.OpenResult
import app.duenorth.budget.core.ParsedImportRow
import app.duenorth.budget.core.PayeeRow
import app.duenorth.budget.core.PayeeWriteResult
import app.duenorth.budget.core.PendingExport
import app.duenorth.budget.core.PhoneSettings
import app.duenorth.budget.core.PreviewRowStatus
import app.duenorth.budget.core.ReconcileFinishResult
import app.duenorth.budget.core.ReconcilePage
import app.duenorth.budget.core.ReconcileStartResult
import app.duenorth.budget.core.RefreshGate
import app.duenorth.budget.core.RegisterEntry
import app.duenorth.budget.core.RegisterPage
import app.duenorth.budget.core.RegisterSearch
import app.duenorth.budget.core.RemoteFile
import app.duenorth.budget.core.ReportPreset
import app.duenorth.budget.core.ReportRange
import app.duenorth.budget.core.ReportsPage
import app.duenorth.budget.core.RuleDraft
import app.duenorth.budget.core.RuleSummary
import app.duenorth.budget.core.SavedInstance
import app.duenorth.budget.core.ScheduleDetails
import app.duenorth.budget.core.ScheduleDraft
import app.duenorth.budget.core.ScheduleSummary
import app.duenorth.budget.core.ScheduleWriteResult
import app.duenorth.budget.core.ServerAddress
import app.duenorth.budget.core.ServerCheck
import app.duenorth.budget.core.ServerProbe
import app.duenorth.budget.core.SpendingGroupBy
import app.duenorth.budget.core.SplitPart
import app.duenorth.budget.core.SyncCoordinator
import app.duenorth.budget.core.SyncCopy
import app.duenorth.budget.core.TemplateResult
import app.duenorth.budget.core.TransactionDraft
import app.duenorth.budget.core.UnlockResult
import app.duenorth.budget.core.UploadResult
import app.duenorth.budget.core.WriteResult
import app.duenorth.budget.core.activeInstance
import app.duenorth.budget.core.syncFraction
import app.duenorth.budget.core.withBudgetedAssignment
import app.duenorth.budget.core.withHold
import app.duenorth.budget.core.withMoveAvailable
import app.duenorth.budget.core.withReleaseHold
import app.duenorth.budget.design.MonthWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth

sealed interface ShellRoute {
    data object Home : ShellRoute

    data object Budgets : ShellRoute

    data object Create : ShellRoute

    data object ConfirmSwitch : ShellRoute

    /** A server budget was picked while the phone holds others: names what gets cleared. */
    data object ConfirmReplaceBudget : ShellRoute

    data object ConfirmInstanceSwitch : ShellRoute

    data object Server : ShellRoute

    data object BudgetPassword : ShellRoute

    data object Assign : ShellRoute

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

    /** The category picker for [transactionId], plus [others] when several inbox rows are filed together. */
    data class PickCategory(
        val transactionId: String,
        val others: List<String> = emptyList(),
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

    data class CategoryBudget(
        val categoryId: String,
    ) : ShellRoute

    data class MoveCategory(
        val fromCategoryId: String,
    ) : ShellRoute

    data object HoldMonth : ShellRoute

    data object Breakdown : ShellRoute

    data class ToBudget(
        val categoryId: String,
    ) : ShellRoute

    data object ManageCategories : ShellRoute

    data object Payees : ShellRoute

    data object Settings : ShellRoute

    /** Settings → advanced → encryption: one page per password action (spec 012). */
    data class EncryptionPassword(
        val mode: EncryptionMode,
    ) : ShellRoute

    /** Settings → server → remove asks first (spec 012). */
    data class ConfirmRemoveInstance(
        val instanceId: String,
    ) : ShellRoute

    data object SampleBudgets : ShellRoute

    // Spec 011: first-run onboarding.
    data object Welcome : ShellRoute

    data object OnboardingAddress : ShellRoute

    data object OnboardingSignIn : ShellRoute

    data object OnboardingFiles : ShellRoute

    // Spec 010 US2: account management.
    data object Accounts : ShellRoute, StackRoute

    data object AddAccount : ShellRoute, StackRoute

    data class EditAccount(
        val accountId: String,
    ) : ShellRoute,
        StackRoute

    data class CloseAccount(
        val accountId: String,
    ) : ShellRoute,
        StackRoute

    // Spec 010 US3: schedules and rules.
    data class ScheduleEditor(
        val scheduleId: String?,
    ) : ShellRoute,
        StackRoute

    data object Rules : ShellRoute, StackRoute

    data class RuleEditor(
        val ruleId: String?,
    ) : ShellRoute,
        StackRoute

    /** Accounts → schedules: a pivot of what is coming up (a list or a calendar) and every schedule. */
    data object Schedules : ShellRoute, StackRoute

    /** Schedules → one schedule: its details, next dates and what it has posted. */
    data class ScheduleDetails(
        val scheduleId: String,
    ) : ShellRoute,
        StackRoute

    // Spec 010 US4. The home pivot's reports item is the reporting hub; each report opens from it.

    /** Reports → month review: the open month's income and spending by category. */
    data object MonthReview : ShellRoute, StackRoute

    /** Reports → net worth: today's on and off budget totals. */
    data object NetWorth : ShellRoute, StackRoute

    /** Reports → a report over a month range, opened at its pivot [item]. */
    data class ReportPivot(
        val item: Int,
    ) : ShellRoute,
        StackRoute

    // Spec 010 US5.
    data object Templates : ShellRoute, StackRoute

    // Spec 010 US6.
    data class BankLink(
        val accountId: String,
    ) : ShellRoute,
        StackRoute
}

/** A spec 010 screen that goes back to the screen it was opened from. */
interface StackRoute

/** An inbox row just filed under [categoryName], shown at [index] while its confirmation plays. */
data class InboxFiled(
    val row: InboxRow,
    val index: Int,
    val categoryName: String,
)

data class ShellUiState(
    val loading: Boolean = true,
    val route: ShellRoute = ShellRoute.Home,
    val settings: PhoneSettings = PhoneSettings(),
    val budgets: List<BudgetSummary> = emptyList(),
    val developerMode: Boolean = false,
    val sampleBudgets: List<MockBudgetDataset> = emptyList(),
    val defaultBudgetName: String = "",
    val shell: MonthShell? = null,
    val register: RegisterPage? = null,
    /** Transactions picked in the register's select mode; null when not selecting. */
    val registerSelection: Set<String>? = null,
    /** The register's search, open over the rows; null when closed. */
    val registerSearch: RegisterSearch? = null,
    val categoryTarget: CategoryTarget? = null,
    val createError: String? = null,
    val writeError: String? = null,
    val confirm: WriteResult.Confirm? = null,
    val switchTarget: BudgetSummary? = null,
    val today: String = RegisterEntry.todayIso(),
    val openMonth: YearMonth = YearMonth.now(),
    val pinnedMonth: YearMonth? = null,
    val monthReview: MonthReviewPage? = null,
    val netWorth: NetWorthPage? = null,
    val categoryMonth: CategoryMonthPage? = null,
    val reconcile: ReconcilePage? = null,
    val reconcileError: String? = null,
    /** Every schedule, ended ones included, by next date. */
    val schedules: List<ScheduleSummary> = emptyList(),
    /** The schedule [ShellRoute.ScheduleDetails] shows; null while it loads. */
    val scheduleDetails: ScheduleDetails? = null,
    /** The schedules pivot item showing: [SchedulesSection.UPCOMING] or [SchedulesSection.ALL]. */
    val schedulesSection: Int = 0,
    /** Upcoming shows a month calendar instead of the list. */
    val schedulesCalendar: Boolean = false,
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
    val instances: List<SavedInstance> = emptyList(),
    val activeInstance: SavedInstance? = null,
    val instanceReachable: Boolean? = null,
    val formatPrefs: FormatPrefs = FormatPrefs(),
    val budgetMethod: String = "envelope",
    val cloudFileId: String? = null,
    val pendingExport: PendingExport? = null,
    val confirmAdvanced: AdvancedConfirm? = null,
    val openIdRedirectUrl: String? = null,
    val openIdAvailable: Boolean = false,
    val openIdPendingAddress: String? = null,
    val switchInstanceTarget: SavedInstance? = null,
    val encryptionError: String? = null,
    val switchReturnRoute: ShellRoute = ShellRoute.Budgets,
    /** The phone's budgets that picking [pendingFile] clears. */
    val replaceBudgets: List<BudgetSummary> = emptyList(),
    val breakdown: ActualMonthMath.MonthBreakdown? = null,
    val fillConfirm: FillConfirm? = null,
    val monthNotice: String? = null,
    val undoLabel: String? = null,
    val accountsList: List<AccountDetail> = emptyList(),
    val accountDetail: AccountDetail? = null,
    val accountsReorder: Boolean = false,
    val accountForceConfirm: String? = null,
    val scheduleDraft: ScheduleDraft? = null,
    val rulesList: List<RuleSummary> = emptyList(),
    val ruleDraft: RuleDraft? = null,
    val reports: ReportsPage? = null,
    val reportsLoading: Boolean = false,
    val reportPreset: ReportPreset = ReportPreset.LAST_6,
    val reportCustom: ReportRange? = null,
    val reportGroupBy: SpendingGroupBy = SpendingGroupBy.CATEGORY,
    val templateResult: TemplateResult? = null,
    val templateBusy: Boolean = false,
    val templateConfirm: String? = null,
    /** The open category's note while templates are on; null until read. */
    val categoryNote: String? = null,
    val bankLink: BankLinkUi? = null,
    /** First-run progress while the phone has no budget (spec 011); null otherwise. */
    val onboarding: OnboardingUi? = null,
    /** The Home pivot item showing, kept while a page opened from Home is showing. */
    val homeSection: Int = 0,
    /** Inbox rows just filed, kept on screen while their confirmation plays. */
    val inboxFiled: List<InboxFiled> = emptyList(),
    /** Inbox rows checked in the inbox's select mode; null when not selecting. */
    val inboxSelection: Set<String>? = null,
    /** A transaction just left by a match, shown first in the inbox so it can be filed next. */
    val inboxLead: String? = null,
    /** The budget's edit mode, with the box being typed into; null when not editing. */
    val budgetEdit: BudgetEdit? = null,
    /** Set while a transaction form was opened from home (the inbox or its add), so saving it goes back home. */
    val editFromHome: Boolean = false,
    /** A new transaction opened from a reconcile's "add it" starts with the amount that balances it, on the statement date. */
    val newTransactionPrefill: NewTransactionPrefill? = null,
    /** The settings pivot item showing, kept while a settings page is open (spec 012). */
    val settingsPage: SettingsPage = SettingsPage.BUDGET,
    val settingsScroll: Map<SettingsPage, Int> = emptyMap(),
    /** True while a page opened from settings is showing; back and finish return to settings. */
    val settingsReturn: Boolean = false,
    val budgetEncrypted: Boolean = false,
)

class ShellViewModel(
    internal val library: BudgetLibrary,
    internal val sync: SyncCoordinator,
    private val bankSyncTransport: BankSyncTransport = HttpBankSyncTransport(),
    private val developerMode: Boolean = false,
    internal val probe: ServerProbe = ServerProbe(),
) : ViewModel() {
    internal val shellGate = RefreshGate<MonthShell>()
    private val registerGate = RefreshGate<RegisterPage>()
    internal val months = OpenMonthStore()
    private val history = ArrayDeque<ShellRoute>()
    private val _state =
        MutableStateFlow(
            ShellUiState(
                developerMode = developerMode,
                sampleBudgets = if (developerMode) MockBudgets.all else emptyList(),
                defaultBudgetName = if (developerMode) MockBudgets.WALKTHROUGH_NAME else "",
            ),
        )
    val state: StateFlow<ShellUiState> = _state.asStateFlow()

    /** For the view model's extension files, such as `ShellViewModelMonth.kt`. */
    internal fun mutate(transform: (ShellUiState) -> ShellUiState) = _state.update(transform)

    private var writing = false

    /** The running server check or sign-in, cancelled by back (spec 011 R11). */
    internal var onboardingJob: Job? = null

    /** `onboarding.json` writes, one at a time and in order. */
    internal val onboardingWrites = Dispatchers.IO.limitedParallelism(1)

    /** When the last sync the screen started without being asked began, in epoch millis; launch syncs at start. */
    private var lastScreenSync = System.currentTimeMillis()

    init {
        viewModelScope.launch {
            restoreOnboarding()
            reload()
            resumeOnboarding()
            val id = _state.value.settings.openBudgetId ?: return@launch
            if (_state.value.locked) return@launch
            withContext(Dispatchers.IO) { runCatching { sync.sync(id) } }
            reload()
        }
    }

    /**
     * The app came back on screen: pick up what other devices sent while it was away, quietly, at
     * most once a minute. Edit mode and a locked budget wait, since a sync would change what they show.
     */
    fun onScreenReturned() {
        val current = _state.value
        val id = current.settings.openBudgetId ?: return
        if (current.locked || current.syncing || current.budgetEdit != null) return
        val now = System.currentTimeMillis()
        if (now - lastScreenSync < RETURN_SYNC_GAP_MS) return
        lastScreenSync = now
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { sync.sync(id) } }
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

    fun showBudgets() {
        history.clear()
        _state.update { it.copy(route = ShellRoute.Budgets, settingsReturn = false, confirm = null, writeError = null) }
    }

    fun showCreate() {
        history.clear()
        _state.update { it.copy(route = ShellRoute.Create, settingsReturn = false, createError = null, confirm = null) }
    }

    fun showSampleBudgets() {
        if (!developerMode) return
        history.clear()
        _state.update { it.copy(route = ShellRoute.SampleBudgets, createError = null, confirm = null) }
    }

    fun showPayees() {
        history.clear()
        _state.update { it.copy(route = ShellRoute.Payees, writeError = null, payeeMergeTarget = null) }
        loadPayees()
    }

    /** Reads the saved payees, which the payees page lists and every payee box offers as you type. */
    internal fun loadPayees() {
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
        val previous = months.window.previous(months.openMonth) ?: return
        selectOpenMonth(previous)
    }

    fun nextBudgetMonth() {
        val next = months.window.next(months.openMonth) ?: return
        selectOpenMonth(next)
    }

    fun goToCurrentMonth() {
        selectOpenMonth(YearMonth.now())
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

    fun showSettings() {
        history.clear()
        _state.update {
            it.copy(
                route = ShellRoute.Settings,
                confirm = null,
                createError = null,
                encryptionError = null,
                settingsPage = SettingsPage.BUDGET,
                settingsScroll = emptyMap(),
                settingsReturn = false,
            )
        }
        refreshSettingsSections()
    }

    fun selectHomeSection(index: Int) {
        // Edit mode belongs to the budget item and holds the pivot still; leaving it anyway drops the changes.
        if (index != ScreenBar.HOME_BUDGET && _state.value.budgetEdit != null) cancelBudgetEdit()
        _state.update { it.copy(homeSection = index) }
    }

    fun selectSettingsPage(page: SettingsPage) {
        _state.update { it.copy(settingsPage = page) }
    }

    fun saveSettingsScroll(
        page: SettingsPage,
        offset: Int,
    ) {
        _state.update { it.copy(settingsScroll = it.settingsScroll + (page to offset)) }
    }

    /** Opens a page from settings; back or finishing it comes back to the same item (spec 012 FR-015). */
    internal fun openFromSettings(
        route: ShellRoute,
        page: SettingsPage = _state.value.settingsPage,
    ) {
        history.clear()
        _state.update {
            it.copy(
                route = route,
                settingsPage = page,
                settingsReturn = true,
                confirm = null,
                createError = null,
                encryptionError = null,
                serverError = null,
            )
        }
    }

    internal fun returnToSettings(page: SettingsPage? = null) {
        history.clear()
        _state.update {
            it.copy(
                route = ShellRoute.Settings,
                settingsPage = page ?: it.settingsPage,
                settingsReturn = false,
                createError = null,
                encryptionError = null,
                switchTarget = null,
                switchInstanceTarget = null,
                pendingFile = null,
            )
        }
        refreshSettingsSections()
    }

    fun showSettingsCreate() = openFromSettings(ShellRoute.Create, SettingsPage.BUDGET)

    fun showSettingsPayees() {
        showPayees()
        _state.update { it.copy(settingsReturn = true, settingsPage = SettingsPage.BUDGET) }
    }

    fun showEncryption(mode: EncryptionMode) = openFromSettings(ShellRoute.EncryptionPassword(mode), SettingsPage.ADVANCED)

    /** Settings → server → server budgets: the server page with a budget open, the pick-a-budget page without (research R5). */
    fun showServerBudgets() {
        if (_state.value.shell == null) {
            openFromSettings(ShellRoute.OnboardingFiles, SettingsPage.SERVER)
            onboardingRefresh()
        } else {
            showServer()
            _state.update { it.copy(settingsReturn = true, settingsPage = SettingsPage.SERVER) }
        }
    }

    fun requestRemoveInstance(instanceId: String) = openFromSettings(ShellRoute.ConfirmRemoveInstance(instanceId), SettingsPage.SERVER)

    fun confirmRemoveInstance() {
        val id = (_state.value.route as? ShellRoute.ConfirmRemoveInstance)?.instanceId ?: return
        removeInstance(id)
        returnToSettings(SettingsPage.SERVER)
    }

    fun connectInstance(
        address: String,
        password: String,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(serverError = null, syncing = true, syncProgress = 0f) }
            val result = withContext(Dispatchers.IO) { sync.connect(address, password) }
            val settings = withContext(Dispatchers.IO) { library.settings() }
            _state.update {
                it.copy(
                    syncing = false,
                    syncProgress = null,
                    serverError = result.error,
                    remoteFiles = if (result.error == null) result.files else it.remoteFiles,
                    settings = settings,
                    instances = settings.instances,
                    activeInstance = settings.activeInstance(),
                    instanceReachable = if (result.error == null) true else it.instanceReachable,
                    signedIn = sync.signedIn(),
                    openIdAvailable = false,
                )
            }
            if (result.error == null) {
                probeOpenId(address.ifBlank { settings.activeInstance()?.address.orEmpty() })
            }
        }
    }

    fun beginOpenId(address: String) {
        viewModelScope.launch {
            _state.update { it.copy(serverError = null) }
            val redirect =
                withContext(Dispatchers.IO) {
                    sync.beginOpenId(address, OPEN_ID_RETURN)
                }
            if (redirect.isNullOrBlank()) {
                _state.update { it.copy(serverError = SyncCopy.ADDRESS_FAILED, openIdRedirectUrl = null) }
            } else {
                _state.update {
                    it.copy(
                        openIdRedirectUrl = redirect,
                        openIdPendingAddress = SyncCoordinator.normalizeAddress(address) ?: address.trim().trimEnd('/'),
                    )
                }
            }
        }
    }

    fun clearOpenIdRedirect() {
        _state.update { it.copy(openIdRedirectUrl = null) }
    }

    fun completeOpenId(callbackUrl: String?) {
        if (callbackUrl.isNullOrBlank()) return
        val token =
            extractOAuthToken(callbackUrl) ?: run {
                _state.update { it.copy(serverError = SyncCopy.SIGN_IN_FAILED) }
                return
            }
        viewModelScope.launch {
            // After a restart the pending address lives only in onboarding.json (spec 011 R5).
            val saved =
                _state.value.onboarding?.progress
                    ?: withContext(Dispatchers.IO) { library.onboarding().read() }
            val onboarding = saved?.openIdPendingAddress != null
            val address = _state.value.openIdPendingAddress ?: saved?.openIdPendingAddress ?: return@launch
            val label = if (onboarding) saved?.instanceLabel else null
            _state.update { it.copy(syncing = true, syncProgress = 0f, serverError = null) }
            val result = withContext(Dispatchers.IO) { sync.completeOpenId(address, token, label) }
            val settings = withContext(Dispatchers.IO) { library.settings() }
            _state.update {
                it.copy(
                    syncing = false,
                    syncProgress = null,
                    serverError = if (onboarding) null else result.error,
                    remoteFiles = if (result.error == null) result.files else it.remoteFiles,
                    settings = settings,
                    instances = settings.instances,
                    activeInstance = settings.activeInstance(),
                    instanceReachable = if (result.error == null) true else false,
                    signedIn = sync.signedIn(),
                    openIdPendingAddress = null,
                    openIdRedirectUrl = null,
                    route = if (onboarding) it.route else ShellRoute.Settings,
                )
            }
            if (onboarding) finishBrowserSignIn(saved!!, result.error)
        }
    }

    fun requestInstanceSwitch(instanceId: String) {
        _state.update { it.copy(settingsPage = SettingsPage.SERVER) }
        val settings = _state.value.settings
        val target = settings.instances.firstOrNull { it.id == instanceId } ?: return
        val current = settings.activeInstance()
        if (current == null || current.id == target.id) {
            confirmInstanceSwitch(instanceId)
            return
        }
        _state.update {
            it.copy(
                route = ShellRoute.ConfirmInstanceSwitch,
                switchInstanceTarget = target,
            )
        }
    }

    fun confirmInstanceSwitch(instanceId: String? = null) {
        val id = instanceId ?: _state.value.switchInstanceTarget?.id ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { sync.makeActive(id) }
            val settings = withContext(Dispatchers.IO) { library.settings() }
            _state.update {
                it.copy(
                    settings = settings,
                    instances = settings.instances,
                    activeInstance = settings.activeInstance(),
                    instanceReachable = null,
                    remoteFiles = emptyList(),
                    switchInstanceTarget = null,
                    route = ShellRoute.Settings,
                    settingsReturn = false,
                    signedIn = sync.signedIn(),
                )
            }
            probeReachability()
            // A signed-in server goes straight to picking which of its budgets to use.
            if (sync.signedIn()) {
                openFromSettings(ShellRoute.OnboardingFiles, SettingsPage.SERVER)
                onboardingRefresh()
            }
        }
    }

    fun cancelInstanceSwitch() {
        _state.update {
            it.copy(route = ShellRoute.Settings, switchInstanceTarget = null, settingsReturn = false)
        }
    }

    fun removeInstance(instanceId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { sync.removeInstance(instanceId) }
            val settings = withContext(Dispatchers.IO) { library.settings() }
            _state.update {
                it.copy(
                    settings = settings,
                    instances = settings.instances,
                    activeInstance = settings.activeInstance(),
                    signedIn = sync.signedIn(),
                    remoteFiles = if (settings.activeInstanceId == null) emptyList() else it.remoteFiles,
                )
            }
        }
    }

    fun signOutInstance() {
        signOut(preferSettings = true)
    }

    fun setDateFormat(value: String) = setOpenBudgetPreference("dateFormat", value)

    fun setNumberFormat(value: String) = setOpenBudgetPreference("numberFormat", value)

    fun setFirstDay(index: Int) = setOpenBudgetPreference("firstDayOfWeekIdx", index.coerceIn(0, 6).toString())

    fun setHideFraction(hide: Boolean) = setOpenBudgetPreference("hideFraction", if (hide) "true" else "false")

    fun setExperimental(enabled: Boolean) = setOpenBudgetPreference("flags.experimental", if (enabled) "true" else "false")

    /** Settings → learn categories (spec 010 US3). */
    fun setLearning(enabled: Boolean) = setOpenBudgetPreference("learn-categories", if (enabled) "true" else "false")

    fun setGoalTemplates(enabled: Boolean) = setOpenBudgetPreference("flags.goalTemplatesEnabled", if (enabled) "true" else "false")

    fun exportBudget() {
        val budgetId = _state.value.settings.openBudgetId ?: return
        val name =
            _state.value.budgets
                .find { it.id == budgetId }
                ?.name
                .orEmpty()
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) { library.exportZip(budgetId) } ?: return@launch
            val fileName = library.exportFileName(name)
            _state.update { it.copy(pendingExport = PendingExport(fileName, bytes)) }
        }
    }

    fun clearPendingExport() {
        _state.update { it.copy(pendingExport = null) }
    }

    fun requestAdvanced(kind: AdvancedConfirm) {
        _state.update { it.copy(confirmAdvanced = kind) }
    }

    fun cancelAdvanced() {
        _state.update { it.copy(confirmAdvanced = null) }
    }

    fun confirmRebuild() = confirmAdvancedAction(AdvancedConfirm.Rebuild) { id -> library.rebuildTotals(id) }

    fun confirmRepair() = confirmAdvancedAction(AdvancedConfirm.Repair) { id -> library.repairSplits(id) }

    fun confirmResetSync() = confirmAdvancedAction(AdvancedConfirm.ResetSync) { id -> sync.resetSync(id) }

    fun setEncryptionPassword(password: String) {
        val budgetId = _state.value.settings.openBudgetId ?: return
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { sync.setEncryptionPassword(budgetId, password) }) {
                is UnlockResult.Failed -> _state.update { it.copy(encryptionError = result.reason) }
                UnlockResult.Unlocked -> encryptionSaved()
            }
        }
    }

    fun changeEncryptionPassword(
        current: String,
        next: String,
    ) {
        val budgetId = _state.value.settings.openBudgetId ?: return
        viewModelScope.launch {
            when (
                val result =
                    withContext(Dispatchers.IO) {
                        sync.changeEncryptionPassword(budgetId, current, next)
                    }
            ) {
                is UnlockResult.Failed -> _state.update { it.copy(encryptionError = result.reason) }
                UnlockResult.Unlocked -> encryptionSaved()
            }
        }
    }

    fun removeEncryptionPassword(password: String) {
        val budgetId = _state.value.settings.openBudgetId ?: return
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { sync.removeEncryptionPassword(budgetId, password) }) {
                is UnlockResult.Failed -> _state.update { it.copy(encryptionError = result.reason) }
                UnlockResult.Unlocked -> encryptionSaved()
            }
        }
    }

    private fun encryptionSaved() {
        _state.update { it.copy(encryptionError = null) }
        if (_state.value.route is ShellRoute.EncryptionPassword) returnToSettings(SettingsPage.ADVANCED) else refreshSettingsSections()
    }

    fun createFromSettings(
        name: String,
        currency: String,
    ) {
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { createBudget(name, currency) }) {
                is CreateResult.Created -> {
                    _state.update { it.copy(createError = null, route = ShellRoute.Home) }
                    reload()
                }
                is CreateResult.Rejected -> _state.update { it.copy(createError = result.reason) }
            }
        }
    }

    fun monthWindow(): MonthWindow = months.window

    internal fun editorMonth(): YearMonth = months.editorMonth(_state.value.pinnedMonth)

    private fun loadOpenMonth(month: YearMonth) {
        val budgetId = _state.value.settings.openBudgetId ?: return
        if (_state.value.locked) return
        viewModelScope.launch {
            val snapshot =
                withContext(Dispatchers.IO) {
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
        viewModelScope.launch(Dispatchers.IO) {
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

    fun openCategoryBudget(categoryId: String) {
        push(ShellRoute.CategoryBudget(categoryId))
        _state.update { it.copy(editError = null, categoryNote = null) }
        loadCategoryNote(categoryId)
    }

    fun openMoveCategory() {
        val from = (_state.value.route as? ShellRoute.CategoryBudget)?.categoryId ?: return
        push(ShellRoute.MoveCategory(from))
        _state.update { it.copy(editError = null) }
    }

    /** Opens the move screen for [categoryId] from wherever the person is, as edit mode's available figures do. */
    fun openMoveFrom(categoryId: String) {
        push(ShellRoute.MoveCategory(categoryId))
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
        val categoryId = (_state.value.route as? ShellRoute.CategoryBudget)?.categoryId ?: return
        assignBudget(categoryId, amountText)
    }

    /** Sets [categoryId]'s budgeted amount for the month being edited; [amountText] reads like "1234.56". */
    fun assignBudget(
        categoryId: String,
        amountText: String,
    ) {
        val shell = _state.value.shell ?: return
        val budgeted = MoneyFormat.parse(amountText, shell.currency.decimals)
        if (budgeted == null) {
            _state.update { it.copy(editError = SyncCopy.ENTER_AMOUNT) }
            return
        }
        applyEnvelopeEdit(optimistic = { it.withBudgetedAssignment(categoryId, budgeted) }) {
            sync.assign(shell.budgetId, categoryId, editorMonth(), amountText)
        }
    }

    /** Edit mode's done: saves each changed budgeted amount for [month] as one undo step. */
    fun saveBudgets(
        month: YearMonth,
        amounts: Map<String, Long>,
        templates: Boolean? = null,
    ) {
        val shell = _state.value.shell ?: return
        applyEnvelopeEdit(optimistic = { amounts.entries.fold(it) { s, (id, minor) -> s.withBudgetedAssignment(id, minor) } }) {
            sync.assignBudgets(shell.budgetId, month, amounts, templates)
        }
    }

    fun toggleCategoryCarryover(enabled: Boolean) {
        val shell = _state.value.shell ?: return
        val categoryId = (_state.value.route as? ShellRoute.CategoryBudget)?.categoryId ?: return
        applyEnvelopeEdit {
            sync.setCategoryCarryover(shell.budgetId, categoryId, editorMonth(), enabled)
        }
    }

    fun submitMoveCategory(
        toCategoryId: String,
        amountText: String,
    ) {
        val shell = _state.value.shell ?: return
        val from = (_state.value.route as? ShellRoute.MoveCategory)?.fromCategoryId ?: return
        if (_state.value.budgetEdit != null) {
            moveInBudgetEdit(from, toCategoryId, amountText)
            return
        }
        if (toCategoryId == MOVE_TO_BUDGET) {
            transferToBudget(from, amountText)
            return
        }
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
            sync.moveCategory(shell.budgetId, from, toCategoryId, editorMonth(), amountText)
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
            sync.holdForNextMonth(shell.budgetId, editorMonth(), amountText)
        }
    }

    fun releaseHold() {
        val shell = _state.value.shell ?: return
        applyEnvelopeEdit(optimistic = { it.withReleaseHold() }) {
            sync.releaseMonthHold(shell.budgetId, editorMonth())
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

    /**
     * Home's add: the full transaction form, starting on the first open budget account (or any open
     * account); saving comes back home. With no account yet, adding one comes first.
     */
    fun showAddTransaction() {
        val open =
            _state.value.shell
                ?.accounts
                .orEmpty()
                .filter { !it.closed }
        val accountId = (open.firstOrNull { !it.offBudget } ?: open.firstOrNull())?.id
        if (accountId == null) {
            openAddAccount()
            return
        }
        push(ShellRoute.EditTransaction(accountId, null))
        // The form waits for this account's page rather than drawing over another account's.
        _state.update { it.copy(editFromHome = true, registerSelection = null, register = null) }
        loadPayees()
        viewModelScope.launch { apply(load(accountId), accountId) }
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
        if (current.inboxSelection != null && current.route == ShellRoute.Home && current.confirm == null) {
            endInboxSelecting()
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
        _state.update { it.copy(registerSelection = null, registerSearch = null, editFromHome = false) }
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

    /** Accept: files the row under the category its payee last went to, or opens the picker when there is none. */
    fun acceptInbox(transactionId: String) {
        val inbox = _state.value.shell?.inbox ?: return
        val index = inbox.indexOfFirst { it.id == transactionId }
        val row = inbox.getOrNull(index) ?: return
        val categoryId = row.suggestionId
        if (categoryId == null) {
            openInbox(transactionId)
            return
        }
        fileInbox(row, index, categoryId, row.suggestionName, fromPicker = false)
    }

    /** A tap on an inbox row opens its transaction, as the register does. */
    fun openInboxDetails(transactionId: String) {
        openFromInbox(transactionId) { accountId -> ShellRoute.EditTransaction(accountId, transactionId) }
    }

    fun openInboxSplit(transactionId: String) {
        openFromInbox(transactionId) { accountId -> ShellRoute.SplitTransaction(accountId, transactionId) }
    }

    fun deleteInbox(transactionId: String) {
        val row =
            _state.value.shell
                ?.inbox
                ?.firstOrNull { it.id == transactionId } ?: return
        launchWrite { id ->
            val result = withContext(Dispatchers.IO) { library.deleteTransaction(id, transactionId, false) }
            publish(result, row.accountId, stayOnRegister = false)
        }
    }

    /**
     * Match: keeps the bank's transaction with the entry's payee, category and note, and deletes the
     * entry. When neither side had a category the one left leads the inbox, ready to file.
     */
    fun matchTransactions(
        pair: MatchPair,
        stayOnRegister: Boolean,
    ) {
        launchWrite { id ->
            val result = withContext(Dispatchers.IO) { library.matchTransactions(id, pair.bank.id, pair.entered.id) }
            if (result is WriteResult.Saved) _state.update { it.copy(inboxLead = pair.bank.id) }
            publish(result, pair.bank.accountId, stayOnRegister)
        }
    }

    fun notAMatch(
        pair: MatchPair,
        stayOnRegister: Boolean,
    ) {
        launchWrite { id ->
            val result = withContext(Dispatchers.IO) { library.notAMatch(id, pair.bank.id, pair.entered.id) }
            publish(result, pair.bank.accountId, stayOnRegister)
        }
    }

    private fun openFromInbox(
        transactionId: String,
        route: (String) -> ShellRoute,
    ) {
        val row =
            _state.value.shell
                ?.inbox
                ?.firstOrNull { it.id == transactionId } ?: return
        push(route(row.accountId))
        _state.update { it.copy(editFromHome = true, registerSelection = null) }
        loadPayees()
        viewModelScope.launch { apply(load(row.accountId), row.accountId) }
    }

    fun inboxFiledShown(transactionId: String) {
        _state.update { state -> state.copy(inboxFiled = state.inboxFiled.filterNot { it.row.id == transactionId }) }
    }

    /** The inbox bar's select: every row gets a check box, and the bar acts on the checked ones. */
    fun startInboxSelecting() {
        _state.update { it.copy(inboxSelection = emptySet()) }
    }

    fun toggleInboxSelected(transactionId: String) {
        _state.update {
            val picked = it.inboxSelection ?: return@update it
            it.copy(inboxSelection = if (transactionId in picked) picked - transactionId else picked + transactionId)
        }
    }

    fun endInboxSelecting() {
        _state.update { it.copy(inboxSelection = null) }
    }

    /**
     * Files every checked row that has a suggestion under it, as one undo step. Rows without a
     * suggestion stay checked, so categorize can file them next.
     */
    fun acceptSelectedInbox() {
        val picked = _state.value.inboxSelection ?: return
        val rows =
            _state.value.shell
                ?.inbox
                .orEmpty()
                .withIndex()
                .filter { it.value.id in picked && it.value.suggestionId != null }
        if (rows.isEmpty() || writing) return
        val left = picked - rows.map { it.value.id }.toSet()
        _state.update { it.copy(inboxSelection = left.ifEmpty { null }) }
        fileInboxRows(
            rows.associate {
                it.value.id to it.value.suggestionId
            },
            rows.map { InboxFiled(it.value, it.index, it.value.suggestionName) },
            fromPicker = false,
        )
    }

    /** Opens the picker for every checked row; the category picked files them all. */
    fun categorizeSelectedInbox() {
        val picked = _state.value.inboxSelection ?: return
        val ids =
            _state.value.shell
                ?.inbox
                .orEmpty()
                .filter { it.id in picked }
                .map { it.id }
        if (ids.isEmpty()) return
        openInbox(ids.first(), ids.drop(1))
    }

    private fun fileInbox(
        row: InboxRow,
        index: Int,
        categoryId: String,
        categoryName: String,
        fromPicker: Boolean,
    ) = fileInboxRows(mapOf(row.id to categoryId), listOf(InboxFiled(row, index, categoryName)), fromPicker)

    /** Writes [assignments] as one undo step while [filed] play their confirmations. */
    private fun fileInboxRows(
        assignments: Map<String, String?>,
        filed: List<InboxFiled>,
        fromPicker: Boolean,
    ) {
        if (writing || filed.isEmpty()) return
        _state.update { it.copy(inboxFiled = it.inboxFiled + filed) }
        val accountId = filed.first().row.accountId
        launchWrite { id ->
            val result =
                withContext(Dispatchers.IO) {
                    if (assignments.size == 1) {
                        val (txn, category) = assignments.entries.single()
                        library.setTransactionCategory(id, txn, category, false)
                    } else {
                        library.setTransactionsCategory(id, assignments, false)
                    }
                }
            if (result !is WriteResult.Saved) filed.forEach { inboxFiledShown(it.row.id) }
            if (result is WriteResult.Saved) {
                _state.update {
                    it.copy(
                        inboxSelection = it.inboxSelection?.minus(assignments.keys)?.ifEmpty { null },
                    )
                }
            }
            if (fromPicker || result is WriteResult.Saved) {
                publish(result, accountId, stayOnRegister = false)
            } else {
                when (result) {
                    is WriteResult.Rejected -> _state.update { it.copy(writeError = result.reason) }
                    is WriteResult.Confirm -> _state.update { it.copy(confirm = result, writeError = null) }
                    else -> Unit
                }
            }
        }
    }

    fun openInbox(transactionId: String) = openInbox(transactionId, emptyList())

    /** The picker for one inbox row, or for it and [others] together, headed by their count and total. */
    private fun openInbox(
        transactionId: String,
        others: List<String>,
    ) {
        push(ShellRoute.PickCategory(transactionId, others))
        val budgetId = _state.value.settings.openBudgetId ?: return
        val together =
            _state.value.shell
                ?.inbox
                .orEmpty()
                .filter { it.id == transactionId || it.id in others }
        viewModelScope.launch {
            val target = withContext(Dispatchers.IO) { library.readCategoryTarget(budgetId, transactionId) }
            val shown =
                if (others.isEmpty()) {
                    target
                } else {
                    target?.copy(payee = "${together.size} ${InboxCopy.TRANSACTIONS}", amountMinor = together.sumOf { it.amountMinor })
                }
            _state.update { it.copy(categoryTarget = shown, loading = false) }
        }
    }

    fun openNewTransaction() {
        val accountId = openAccountId() ?: return
        _state.update { it.copy(editFromHome = false, newTransactionPrefill = null) }
        push(ShellRoute.EditTransaction(accountId, null))
        loadPayees()
    }

    /** A reconcile's "add it": a new transaction for the difference, dated on the statement, so saving it balances. */
    fun addMissingForReconcile() {
        val page = _state.value.reconcile ?: return openNewTransaction()
        openNewTransaction()
        _state.update {
            it.copy(newTransactionPrefill = NewTransactionPrefill(page.differenceMinor, RegisterEntry.formatIso(page.statementDate)))
        }
    }

    fun openEdit(transactionId: String) {
        val accountId = openAccountId() ?: return
        push(ShellRoute.EditTransaction(accountId, transactionId))
        loadPayees()
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

    /** A hint's "clear both" or "unclear it": several rows' cleared marks set in one go. */
    fun setReconcileCleared(
        transactionIds: List<String>,
        cleared: Boolean,
    ) {
        val accountId = reconcileAccountId() ?: return
        val budgetId = _state.value.settings.openBudgetId ?: return
        viewModelScope.launch {
            val page =
                withContext(Dispatchers.IO) {
                    library.setReconcileCleared(budgetId, accountId, transactionIds, cleared)
                }
            if (page != null) _state.update { it.copy(reconcile = page) }
        }
    }

    fun finishReconcile(adjust: Boolean = false) {
        val accountId = reconcileAccountId() ?: return
        val budgetId = _state.value.settings.openBudgetId ?: return
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { library.finishReconcile(budgetId, accountId, adjust) }) {
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
        previousBudgetMonth()
    }

    fun openReviewCategory(categoryId: String) {
        push(ShellRoute.ReviewCategory(categoryId))
        val budgetId = _state.value.settings.openBudgetId ?: return
        val month = _state.value.openMonth
        viewModelScope.launch {
            val page = withContext(Dispatchers.IO) { library.readCategoryMonth(budgetId, categoryId, month) }
            _state.update { it.copy(categoryMonth = page) }
        }
    }

    fun setNetWorthIncludeOffBudget(include: Boolean) {
        saveSettings(_state.value.settings.copy(netWorthIncludeOffBudget = include))
        refreshNetWorth()
    }

    fun submitTransaction(draft: TransactionDraft) {
        launchWrite { id ->
            val result = withContext(Dispatchers.IO) { library.saveTransaction(id, draft) }
            publish(result, draft.accountId, stayOnRegister = true)
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
        val inbox =
            _state.value.shell
                ?.inbox
                .orEmpty()
        val index = inbox.indexOfFirst { it.id == target.transactionId }
        val others = (_state.value.route as? ShellRoute.PickCategory)?.others.orEmpty()
        if (categoryId != null && others.isNotEmpty()) {
            val name =
                target.categories
                    .firstOrNull { it.id == categoryId }
                    ?.name
                    .orEmpty()
            val ids = setOf(target.transactionId) + others
            val rows = inbox.withIndex().filter { it.value.id in ids }
            fileInboxRows(
                rows.associate { it.value.id to categoryId },
                rows.map { InboxFiled(it.value, it.index, name) },
                fromPicker = true,
            )
            return
        }
        if (categoryId != null && index >= 0) {
            val name =
                target.categories
                    .firstOrNull { it.id == categoryId }
                    ?.name
                    .orEmpty()
            fileInbox(inbox[index], index, categoryId, name, fromPicker = true)
            return
        }
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

    /** A long press starts select mode with that transaction picked. */
    fun startSelecting(transactionId: String) {
        _state.update { it.copy(registerSelection = setOf(transactionId)) }
    }

    /** Picks or unpicks a transaction; unpicking the last one leaves select mode. */
    fun toggleSelected(transactionId: String) {
        _state.update {
            val picked = it.registerSelection ?: return@update it
            val next = if (transactionId in picked) picked - transactionId else picked + transactionId
            it.copy(registerSelection = next.ifEmpty { null })
        }
    }

    fun endSelecting() {
        _state.update { it.copy(registerSelection = null) }
    }

    /** The register's search button: opens the search over the rows, or closes it when open. */
    fun toggleRegisterSearch() {
        _state.update { it.copy(registerSearch = if (it.registerSearch == null) RegisterSearch() else null) }
    }

    fun setRegisterSearch(search: RegisterSearch) {
        _state.update { if (it.registerSearch == null) it else it.copy(registerSearch = search) }
    }

    fun closeRegisterSearch() {
        _state.update { it.copy(registerSearch = null) }
    }

    /** Clears or unclears every picked transaction as one undo step, then leaves select mode. */
    fun setSelectedCleared(cleared: Boolean) {
        val accountId = openAccountId() ?: return
        val picked = _state.value.registerSelection?.toList() ?: return
        launchWrite { id ->
            val result = withContext(Dispatchers.IO) { library.setTransactionsCleared(id, picked, cleared, false) }
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
        val inboxAccount =
            when (val retry = pending.retry) {
                is ForcedWrite.Category -> retry.transactionId
                is ForcedWrite.Categories -> retry.assignments.keys.firstOrNull()
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
                withContext(Dispatchers.IO) {
                    when (val retry = pending.retry) {
                        is ForcedWrite.Save -> library.saveTransaction(id, retry.draft)
                        is ForcedWrite.Delete -> library.deleteTransaction(id, retry.transactionId, true)
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

    fun create(
        name: String,
        currency: String,
    ) {
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { createBudget(name, currency) }) {
                is CreateResult.Rejected -> _state.update { it.copy(createError = result.reason) }
                is CreateResult.Created -> {
                    // A budget made on the server path is sent to that server in the background (spec 011 US5).
                    val send =
                        _state.value.onboarding
                            ?.progress
                            ?.path == OnboardingPath.SERVER && sync.signedIn()
                    finishOnboarding()
                    val toSettings = _state.value.settingsReturn
                    _state.update {
                        it.copy(
                            createError = null,
                            route = if (toSettings) ShellRoute.Settings else ShellRoute.Home,
                            settingsPage = if (toSettings) SettingsPage.BUDGET else it.settingsPage,
                            settingsReturn = false,
                        )
                    }
                    reload()
                    if (toSettings) refreshSettingsSections()
                    if (send) uploadNew()
                }
            }
        }
    }

    /** Debug builds seed every new budget with the full walkthrough data; release builds start empty. */
    private fun createBudget(
        name: String,
        currency: String,
    ): CreateResult =
        if (developerMode) {
            library.createFromMock(MockBudgets.WALKTHROUGH, name.ifBlank { MockBudgets.WALKTHROUGH_NAME })
        } else {
            library.create(name, currency)
        }

    fun createSample(datasetId: String) {
        if (!developerMode) return
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { library.createFromMock(datasetId) }) {
                is CreateResult.Rejected -> _state.update { it.copy(createError = result.reason) }
                is CreateResult.Created -> {
                    finishOnboarding()
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
        _state.update {
            it.copy(
                switchTarget = target,
                pendingFile = null,
                route = ShellRoute.ConfirmSwitch,
                switchReturnRoute = it.route,
            )
        }
    }

    fun cancelSwitch() {
        _state.update {
            it.copy(
                route = it.switchReturnRoute,
                switchTarget = null,
                pendingFile = null,
            )
        }
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
        connectInstance(address, password)
    }

    fun signOut(preferSettings: Boolean = false) {
        // Non-blocking: detach token on IO while UI stays responsive even if sync is running.
        viewModelScope.launch {
            withContext(Dispatchers.IO) { sync.signOut() }
            val settings = withContext(Dispatchers.IO) { library.settings() }
            _state.update { current ->
                current.copy(
                    remoteFiles = emptyList(),
                    serverError = null,
                    signedIn = false,
                    settings = settings,
                    instances = settings.instances,
                    activeInstance = settings.activeInstance(),
                    instanceReachable = null,
                    route =
                        when {
                            preferSettings || current.route == ShellRoute.Settings -> ShellRoute.Settings
                            else -> ShellRoute.Server
                        },
                )
            }
        }
    }

    fun requestRemote(file: RemoteFile) {
        // Keep and failures come back to the list the budget was picked from.
        _state.update { it.copy(switchReturnRoute = it.route.takeIf(::isServerList) ?: it.switchReturnRoute) }
        if (file.encryptKeyId != null && !sync.isUnlocked(file.fileId)) {
            _state.update {
                it.copy(route = ShellRoute.BudgetPassword, pendingFile = file, passwordError = null, askEachTime = true)
            }
            return
        }
        confirmOrOpen(file)
    }

    /** `use` on the replace confirmation: download the server budget, then clear the others. */
    fun confirmReplaceBudget() {
        val current = _state.value
        val file = current.pendingFile ?: return
        if (current.syncing) return
        // The download's progress shows over the list it was picked from.
        _state.update { it.copy(route = it.switchReturnRoute.takeIf(::isServerList) ?: ShellRoute.Server) }
        beginOpen(file)
    }

    /** `keep` (or back) on the replace confirmation: the phone's budget stays, the list shows again. */
    fun cancelReplaceBudget() {
        _state.update {
            it.copy(
                route = it.switchReturnRoute.takeIf(::isServerList) ?: ShellRoute.Server,
                pendingFile = null,
                replaceBudgets = emptyList(),
                passwordError = null,
            )
        }
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

    fun setTheme(mode: String) {
        saveSettings(_state.value.settings.copy(themeMode = mode))
    }

    fun setAccent(id: String) {
        saveSettings(_state.value.settings.copy(accent = id))
    }

    /**
     * A server budget replaces whatever is on the phone. When other budgets are there, the
     * person sees which ones get cleared first; otherwise it opens straight away.
     */
    private fun confirmOrOpen(file: RemoteFile) {
        viewModelScope.launch {
            val cleared = withContext(Dispatchers.IO) { library.list() }.filter { it.id != file.fileId }
            if (cleared.isEmpty()) {
                beginOpen(file)
                return@launch
            }
            _state.update {
                it.copy(
                    route = ShellRoute.ConfirmReplaceBudget,
                    pendingFile = file,
                    replaceBudgets = cleared,
                    passwordError = null,
                )
            }
        }
    }

    private fun isServerList(route: ShellRoute): Boolean = route == ShellRoute.OnboardingFiles || route == ShellRoute.Server

    private fun beginOpen(file: RemoteFile) {
        viewModelScope.launch {
            _state.update { it.copy(syncing = true, syncProgress = 0f, serverError = null) }
            val result =
                withContext(Dispatchers.IO) {
                    sync.open(file, replace = true) { received, total ->
                        _state.update { it.copy(syncing = true, syncProgress = syncFraction(received, total)) }
                    }
                }
            when (result) {
                OpenResult.Opened -> {
                    finishOnboarding()
                    _state.update {
                        it.copy(
                            syncing = false,
                            syncProgress = null,
                            pendingFile = null,
                            replaceBudgets = emptyList(),
                            locked = false,
                            route = ShellRoute.Home,
                            settingsReturn = false,
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
                        it.copy(
                            syncing = false,
                            syncProgress = null,
                            serverError = result.reason,
                            replaceBudgets = emptyList(),
                            route =
                                when {
                                    it.onboarding != null -> ShellRoute.OnboardingFiles
                                    isServerList(it.switchReturnRoute) -> it.switchReturnRoute
                                    else -> ShellRoute.Server
                                },
                        )
                    }
            }
        }
    }

    private fun saveSettings(settings: PhoneSettings) {
        _state.update { it.copy(settings = settings) }
        viewModelScope.launch(Dispatchers.IO) { library.save(settings) }
    }

    private suspend fun reload() {
        val calendar = YearMonth.now()
        months.reset(calendar)
        _state.update { it.copy(openMonth = calendar, pinnedMonth = null) }
        val accountId = (_state.value.route as? ShellRoute.Register)?.accountId
        apply(load(accountId), accountId)
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

    private fun openAccountId(): String? =
        when (val route = _state.value.route) {
            is ShellRoute.Register -> route.accountId
            is ShellRoute.EditTransaction -> route.accountId
            is ShellRoute.SplitTransaction -> route.accountId
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
        val month = _state.value.openMonth
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
    }

    private fun ShellRoute.isRegisterFlow(): Boolean =
        this is ShellRoute.Register ||
            this is ShellRoute.EditTransaction ||
            this is ShellRoute.SplitTransaction ||
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
            this is ShellRoute.Breakdown ||
            this is ShellRoute.ToBudget

    private fun refreshBudgetShell() {
        val budgetId = _state.value.settings.openBudgetId ?: return
        if (_state.value.locked) return
        viewModelScope.launch {
            val month = editorMonth()
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
            val result = withContext(Dispatchers.IO) { block() }
            _state.update { it.copy(undoLabel = library.undoLabel(budgetId)) }
            when (result) {
                is EditResult.Rejected -> {
                    val month = editorMonth()
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
                    val month = editorMonth()
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

    private fun refreshSettingsSections() {
        viewModelScope.launch {
            val settings = withContext(Dispatchers.IO) { library.settings() }
            val budgetId = settings.openBudgetId
            val prefs =
                if (budgetId == null) {
                    FormatPrefs()
                } else {
                    withContext(Dispatchers.IO) { library.readFormatPrefs(budgetId) }
                }
            val cloud =
                if (budgetId == null) {
                    null
                } else {
                    withContext(Dispatchers.IO) {
                        library.useDatabase(budgetId) { session ->
                            app.duenorth.budget.core.SyncSchema
                                .state(session, "cloudFileId")
                        }
                    }
                }
            val encrypted = budgetId != null && withContext(Dispatchers.IO) { sync.isEncrypted(budgetId) }
            _state.update {
                it.copy(
                    budgetEncrypted = encrypted,
                    settings = settings,
                    instances = settings.instances,
                    activeInstance = settings.activeInstance(),
                    formatPrefs = prefs,
                    budgetMethod = prefs.budgetType,
                    cloudFileId = cloud?.takeIf { id -> id.isNotBlank() },
                    signedIn = sync.signedIn(),
                )
            }
            val address = settings.activeInstance()?.address
            if (!address.isNullOrBlank()) {
                probeOpenId(address)
                probeReachability()
            } else {
                _state.update { it.copy(openIdAvailable = false, instanceReachable = null) }
            }
        }
    }

    private fun setOpenBudgetPreference(
        id: String,
        value: String,
    ) {
        val budgetId = _state.value.settings.openBudgetId ?: return
        val optimistic =
            when (id) {
                "dateFormat" -> _state.value.formatPrefs.copy(dateFormat = value)
                "numberFormat" -> _state.value.formatPrefs.copy(numberFormat = value)
                "firstDayOfWeekIdx" ->
                    _state.value.formatPrefs.copy(firstDayOfWeekIdx = value.toIntOrNull() ?: 0)
                "hideFraction" -> _state.value.formatPrefs.copy(hideFraction = value == "true")
                "flags.experimental" -> _state.value.formatPrefs.copy(experimental = value == "true")
                "learn-categories" -> _state.value.formatPrefs.copy(learnCategories = value == "true")
                "flags.goalTemplatesEnabled" -> _state.value.formatPrefs.copy(goalTemplates = value == "true")
                else -> _state.value.formatPrefs
            }
        _state.update {
            it.copy(
                formatPrefs = optimistic,
                budgetMethod = optimistic.budgetType,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            library.setPreference(budgetId, id, value)
        }
    }

    private fun confirmAdvancedAction(
        kind: AdvancedConfirm,
        block: (String) -> Boolean,
    ) {
        if (_state.value.confirmAdvanced != kind) return
        val budgetId = _state.value.settings.openBudgetId ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { block(budgetId) }
            _state.update { it.copy(confirmAdvanced = null) }
            reload()
            refreshSettingsSections()
        }
    }

    private fun probeOpenId(address: String) {
        if (address.isBlank()) return
        viewModelScope.launch {
            val methods = withContext(Dispatchers.IO) { sync.loginMethods(address) }
            _state.update { it.copy(openIdAvailable = methods.any { method -> method.equals("openid", ignoreCase = true) }) }
        }
    }

    private fun probeReachability() {
        val address = _state.value.activeInstance?.address ?: return
        viewModelScope.launch {
            val reachable =
                withContext(Dispatchers.IO) {
                    val parsed = ServerAddress.parse(address) ?: return@withContext false
                    runCatching { probe.check(parsed) is ServerCheck.Ready }.getOrElse { false }
                }
            _state.update { it.copy(instanceReachable = reachable) }
        }
    }

    private fun extractOAuthToken(callbackUrl: String): String? {
        val uri = android.net.Uri.parse(callbackUrl)
        val fromQuery = uri.getQueryParameter("token") ?: uri.getQueryParameter("access_token")
        if (!fromQuery.isNullOrBlank()) return fromQuery
        val fragment = uri.fragment.orEmpty()
        if (fragment.isBlank()) return null
        return fragment
            .split('&')
            .mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx <= 0) null else part.substring(0, idx) to part.substring(idx + 1)
            }.firstOrNull { (key, _) -> key == "token" || key == "access_token" }
            ?.second
            ?.takeIf { it.isNotBlank() }
    }

    private data class Loaded(
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

data class NewTransactionPrefill(
    val amountMinor: Long,
    val dateIso: String,
)

/** The shortest gap between two syncs the screen starts on its own, as when the app comes back. */
private const val RETURN_SYNC_GAP_MS = 60_000L
