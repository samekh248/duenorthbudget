package app.duenorth.budget

import app.duenorth.budget.core.AccountDetail
import app.duenorth.budget.core.BudgetConflict
import app.duenorth.budget.core.BudgetSummary
import app.duenorth.budget.core.CategoryChoice
import app.duenorth.budget.core.CategoryManagePage
import app.duenorth.budget.core.CategoryMonthPage
import app.duenorth.budget.core.CategoryTarget
import app.duenorth.budget.core.FormatPrefs
import app.duenorth.budget.core.ImportPreview
import app.duenorth.budget.core.ImportReviewPage
import app.duenorth.budget.core.InboxRow
import app.duenorth.budget.core.MockBudgetDataset
import app.duenorth.budget.core.MonthReviewPage
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.NetWorthPage
import app.duenorth.budget.core.ParsedImportRow
import app.duenorth.budget.core.PayeeRow
import app.duenorth.budget.core.PendingExport
import app.duenorth.budget.core.PhoneSettings
import app.duenorth.budget.core.ReconcilePage
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
import app.duenorth.budget.core.SpendingGroupBy
import app.duenorth.budget.core.Template
import app.duenorth.budget.core.TemplatePlan
import app.duenorth.budget.core.TemplateResult
import app.duenorth.budget.core.WriteResult
import app.duenorth.budget.core.activeInstance
import kotlinx.coroutines.launch
import java.time.YearMonth

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
    /** Bank sync's own state, kept by [BankSyncHolder]. */
    val bankSync: BankSyncState = BankSyncState(),
    /** The onboarding files list is asking the server for its budgets again. */
    val remoteRefreshing: Boolean = false,
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
    val switchReturnRoute: ShellRoute = ShellRoute.Home,
    /** The phone's budgets that picking [pendingFile] clears. */
    val replaceBudgets: List<BudgetSummary> = emptyList(),
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
    /** What each of the note's template lines asks for in the open month, in line order (null for none). */
    val categoryTemplateAmounts: List<Long?> = emptyList(),
    /** The template being edited and what it would give this month. */
    val templatePreview: Pair<Template, Long?>? = null,
    /** Schedule names a schedule template can pay. */
    val templateSchedules: List<String> = emptyList(),
    /** Every category's templates in priority lanes, for the templates screen; null until worked out. */
    val templatePlan: TemplatePlan? = null,
    /** What apply or overwrite would change, shown before it does. */
    val templateApplyPreview: TemplateApplyPreview? = null,
    val bankLink: BankLinkUi? = null,
    /** First-run progress while the phone has no budget (spec 011); null otherwise. */
    val onboarding: OnboardingUi? = null,
    /** The Home pivot item showing, kept while a page opened from Home is showing. */
    val homeSection: Int = 0,
    /** The inbox's own state, kept by [InboxHolder]. */
    val inbox: InboxUi = InboxUi(),
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

data class NewTransactionPrefill(
    val amountMinor: Long,
    val dateIso: String,
)
