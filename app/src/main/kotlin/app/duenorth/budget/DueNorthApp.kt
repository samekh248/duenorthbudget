package app.duenorth.budget

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.os.StrictMode
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.duenorth.budget.core.BudgetClock
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.HttpActualTransport
import app.duenorth.budget.core.SyncCoordinator
import app.duenorth.budget.core.ThemeMode
import app.duenorth.budget.design.MotionPolicy
import app.duenorth.budget.design.components.LocalFieldSubmit
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.theme.Accent
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import app.duenorth.budget.design.theme.MetroStyle
import app.duenorth.budget.design.theme.MetroTheme
import app.duenorth.budget.design.turnstileIn
import java.io.File
import java.time.YearMonth

open class DueNorthApplication : Application() {
    lateinit var library: BudgetLibrary
    var developerMode: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        developerMode = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (developerMode) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy
                    .Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build(),
            )
        }
        library = BudgetLibrary(File(filesDir, "budgets"), AndroidSessionOpener(), BudgetClock.System)
        sync =
            SyncCoordinator(
                library,
                HttpActualTransport(),
                AndroidSecretStore(this),
                BudgetClock.System,
            )
        // Robolectric hosts have no work manager; the app still runs without the background sync.
        runCatching { BackgroundSyncWorker.schedule(this, library.settings().backgroundSyncHours) }
    }

    lateinit var sync: SyncCoordinator

    /** The app is on screen, so its own syncs keep it current and the background job skips its turn. */
    @Volatile
    var onScreen: Boolean = false
}

class MainActivity : ComponentActivity() {
    private var shellModel: ShellViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val application = application as DueNorthApplication
        if (savedInstanceState == null) CrashLog.offer(this)
        setContent {
            val model: ShellViewModel =
                viewModel(
                    factory =
                        ShellViewModel.factory(
                            application.library,
                            application.sync,
                            developerMode = application.developerMode,
                            onBackgroundSyncHours = { hours -> runCatching { BackgroundSyncWorker.schedule(application, hours) } },
                        ),
                )
            shellModel = model
            LaunchedEffect(intent?.data) {
                model.servers.completeOpenId(intent?.dataString)
            }
            DueNorthApp(model)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        shellModel?.servers?.completeOpenId(intent.dataString)
    }
}

private const val EXPORTS_DIR = "exports"

/** Opens a server-supplied link in a browser tab, but only a web page: never another app's scheme. */
internal fun openWebPage(
    context: android.content.Context,
    url: String,
): Boolean {
    if (!isWebUrl(url)) return false
    CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
    return true
}

internal fun isWebUrl(url: String): Boolean {
    val scheme = Uri.parse(url.trim()).scheme?.lowercase()
    return scheme == "https" || scheme == "http"
}

@Composable
fun DueNorthApp(model: ShellViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var importAccountId by remember { mutableStateOf<String?>(null) }
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            val accountId = importAccountId ?: return@rememberLauncherForActivityResult
            if (uri == null) return@rememberLauncherForActivityResult
            val bytes =
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@rememberLauncherForActivityResult
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "import.qif"
            model.import.previewFile(accountId, bytes, name)
        }
    LaunchedEffect(state.servers.openIdRedirectUrl) {
        val url = state.servers.openIdRedirectUrl ?: return@LaunchedEffect
        openWebPage(context, url)
        model.servers.clearOpenIdRedirect()
    }
    LaunchedEffect(state.settingsUi.pendingExport) {
        val export = state.settingsUi.pendingExport ?: return@LaunchedEffect
        // Only this folder is shared through the FileProvider; an earlier export is not kept around.
        val folder = File(context.cacheDir, EXPORTS_DIR).apply { deleteRecursively() }
        folder.mkdirs()
        val file = File(folder, export.fileName)
        file.writeBytes(export.bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val share =
            Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        context.startActivity(Intent.createChooser(share, export.fileName))
        model.settingsUi.clearPendingExport()
    }
    val systemDark = isSystemInDarkTheme()
    val dark =
        when (ThemeMode.fromStored(state.settings.themeMode)) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    val scale =
        Settings.Global.getFloat(
            LocalContext.current.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        (context.applicationContext as? DueNorthApplication)?.onScreen = true
                        model.onScreenReturned()
                    }
                    Lifecycle.Event.ON_STOP -> {
                        (context.applicationContext as? DueNorthApplication)?.onScreen = false
                        model.onScreenLocked()
                        runCatching { BackgroundSyncWorker.sendUnsent(context) }
                    }
                    else -> Unit
                }
            }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val canBack =
        state.confirm != null ||
            state.monthUi.fillConfirm != null ||
            state.settingsUi.confirmAdvanced != null ||
            (
                state.route != ShellRoute.Home &&
                    state.route != ShellRoute.Welcome &&
                    !(state.route == ShellRoute.Create && state.shell == null && state.onboarding == null && !state.settingsReturn) &&
                    !(state.route == ShellRoute.BudgetPassword && state.locked)
            )
    BackHandler(enabled = canBack) {
        if (state.settingsUi.confirmAdvanced != null) {
            model.settingsUi.cancelAdvanced()
        } else {
            model.back()
        }
    }
    val formSaveHost = rememberFormSaveHost()
    MetroTheme(
        darkTheme = dark,
        accent = Accent.fromId(state.settings.accent),
        style = MetroStyle.fromId(state.settings.style),
        animationsEnabled = MotionPolicy.enabled(scale),
    ) {
        val handlers =
            remember(state.route, importAccountId) {
                ScreenBarHandlers(
                    onAdd = {
                        when (state.route) {
                            is ShellRoute.Register -> model.register.openNew()
                            else -> model.register.showAdd()
                        }
                    },
                    onHold = model.envelopes::showHold,
                    onCategories = model.envelopes::showManage,
                    onSettings = model.settingsUi::show,
                    onReconcile = model.reconcile::open,
                    onImport = {
                        val accountId =
                            (state.route as? ShellRoute.Register)?.accountId ?: return@ScreenBarHandlers
                        importAccountId = accountId
                        importLauncher.launch("*/*")
                    },
                    onBankSync = {
                        val accountId =
                            (state.route as? ShellRoute.Register)?.accountId ?: return@ScreenBarHandlers
                        model.import.fetchBank(accountId)
                    },
                    onBankSyncAll = model.bankSync::syncAll,
                    onDismissBankSync = model.bankSync::dismiss,
                    onSave = {
                        when (state.route) {
                            is ShellRoute.ImportPreviewRoute -> model.import.confirm()
                            is ShellRoute.Reconcile -> model.reconcile.finish()
                            else -> formSaveHost.save()
                        }
                    },
                    onClearSelected = { model.register.setSelectedCleared(true) },
                    onUnclearSelected = { model.register.setSelectedCleared(false) },
                    onEndSelecting = model.register::endSelecting,
                    onSearch = model.register::toggleSearch,
                    onRemoveTemplate = model.templates::remove,
                    onAddTemplate = { model.templates.openKinds() },
                    onToAutomations = model.templates::switchToAutomations,
                    onToNotes = model.templates::switchToNotes,
                    onMoveCategory = model.envelopes::openMove,
                    onAutoHold = model.month::chooseAutoHold,
                    onSelectInbox = model.inbox::startSelecting,
                    onAcceptSelected = model.inbox::acceptSelected,
                    onCategorizeSelected = model.inbox::categorizeSelected,
                    onEndInboxSelecting = model.inbox::endSelecting,
                    onDeleteSelected = model.inbox::deleteSelected,
                    onMergeSelected = model.register::mergeSelected,
                    onMergeInboxSelected = model.inbox::mergeSelected,
                    onSyncServer = model.budgetFiles::syncInbox,
                    onCancel = {
                        when (state.route) {
                            is ShellRoute.ImportPreviewRoute -> model.import.cancel()
                            is ShellRoute.Reconcile -> model.reconcile.cancel()
                            else -> model.back()
                        }
                    },
                    onFill = { kind -> model.month.fill(kind) },
                    onToBudget = model.month::openToBudget,
                    onUndo = model.month::undo,
                    onAddAccount = model.accounts::openAdd,
                    onShowClosed = model.accounts::toggleShowClosed,
                    onShowHidden = model.month::toggleShowHidden,
                    onReorder = model.accounts::toggleReorder,
                    onEditAccount = model.accounts::openEdit,
                    onCloseAccount = model.accounts::openClose,
                    onReopenAccount = model.accounts::reopenOpen,
                    onAddSchedule = model.schedules::openNew,
                    onRules = model.rules::open,
                    onAddRule = model.rules::openNew,
                    onDeleteSchedule = model.schedules::deleteOpen,
                    onDeleteRule = model.rules::deleteOpen,
                    onSchedules = model.schedules::open,
                    onSchedulesView = model.schedules::toggleCalendar,
                    onPostSchedule = model.schedules::postOpen,
                    onSkipSchedule = model.schedules::skipOpen,
                    onEditSchedule = model.schedules::editOpen,
                    onTemplates = model.templates::open,
                    onBankLink = model.bankLink::open,
                    onNext = model.onboarding::check,
                    onRefresh = model.onboarding::refreshFiles,
                    onSamples = model.budgetFiles::showSamples,
                    onNewBudget = model.onboarding::newBudget,
                    onSettingsNew = model.settingsUi::showCreate,
                    onSettingsPayees = model.settingsUi::showPayees,
                    onAddServer = model.onboarding::addServer,
                    onServerBudgets = model.servers::showBudgets,
                    onSignOut = model.servers::signOut,
                    onExport = model.settingsUi::exportBudget,
                    onScheduleTransaction = {
                        (state.route as? ShellRoute.EditTransaction)?.transactionId?.let(model.schedules::createFromTransaction)
                    },
                    onDeleteTransaction = { (state.route as? ShellRoute.EditTransaction)?.transactionId?.let(model.register::delete) },
                    onEditBudget = model.budgetEdit::start,
                    onEndBudgetEdit = model.budgetEdit::end,
                    onCancelBudgetEdit = model.budgetEdit::cancel,
                )
            }
        val actions = formSaveHost.bar ?: ScreenBar.forState(state, handlers)
        // The bank sync tile stays over every page while banks fetch or a failure waits.
        ShellChrome(
            actions,
            syncing = state.syncing || state.import.fetchPending,
            progress = state.syncProgress,
            bar = state.route != ShellRoute.Home || state.budgetEdit?.categoryId == null,
            overBar =
                state.bankSync.panel
                    ?.takeIf { state.route != ShellRoute.BankSyncReport }
                    ?.let { ui ->
                        { modifier ->
                            BankSyncPanel(
                                ui,
                                model.bankSync::syncAll,
                                model.bankSync::dismiss,
                                modifier,
                                onOpen = model.bankSync::openReport,
                            )
                        }
                    },
        ) { modifier ->
            CompositionLocalProvider(
                LocalFormSaveHost provides formSaveHost,
                // Enter in a one-line box saves the page, unless a picker drawn over the form owns the bar.
                LocalFieldSubmit provides { if (formSaveHost.bar == null) formSaveHost.save() },
                LocalFormatPrefs provides state.formatPrefs,
            ) {
                // Each new page turns in; a route that only updates in place keeps its class and stays put.
                SectionTitled(SectionTitle.of(state), modifier.turnstileIn(state.route::class)) {
                    val fill = Modifier.fillMaxSize()
                    val onImportFile: (String) -> Unit = { accountId ->
                        importAccountId = accountId
                        importLauncher.launch("*/*")
                    }
                    when (val route = state.route) {
                        ShellRoute.Home -> HomeRoute(state, model, fill)
                        ShellRoute.Settings -> SettingsRoute(state, model, fill)
                        is ShellRoute.ExperimentalOffer -> ExperimentalOfferRoute(route, state, model, fill)
                        is ShellRoute.EncryptionPassword -> EncryptionPasswordRoute(route, state, model, fill)
                        is ShellRoute.ConfirmRemoveInstance -> ConfirmRemoveInstanceRoute(route, state, model, fill)
                        ShellRoute.ConfirmInstanceSwitch -> ConfirmInstanceSwitchRoute(state, model, fill)
                        ShellRoute.SampleBudgets -> SampleBudgetsRoute(state, model, fill)
                        ShellRoute.Create -> CreateRoute(state, model, fill)
                        ShellRoute.Welcome -> WelcomeRoute(state, model, fill)
                        ShellRoute.OnboardingAddress -> OnboardingAddressRoute(state, model, fill)
                        ShellRoute.OnboardingSignIn -> OnboardingSignInRoute(state, model, fill)
                        ShellRoute.OnboardingFiles -> OnboardingFilesRoute(state, model, fill)
                        ShellRoute.ConfirmReplaceBudget -> ConfirmReplaceBudgetRoute(state, model, fill)
                        ShellRoute.ConfirmSwitch -> ConfirmSwitchRoute(state, model, fill)
                        ShellRoute.BankSyncReport -> BankSyncReportRoute(state, model, fill)
                        ShellRoute.Payees -> PayeesRoute(state, model, fill)
                        ShellRoute.Server -> ServerRoute(state, model, fill)
                        ShellRoute.BudgetPassword -> BudgetPasswordRoute(state, model, fill)
                        ShellRoute.Conflicts -> ConflictsRoute(state, model, fill)
                        ShellRoute.ConfirmReplace -> ConfirmReplaceRoute(state, model, fill)
                        is ShellRoute.Register -> RegisterRoute(route, state, model, onImportFile, fill)
                        is ShellRoute.ImportPreviewRoute -> ImportFilePreviewRoute(state, model, fill)
                        is ShellRoute.ImportReviewRoute -> ImportedReviewRoute(state, model, fill)
                        is ShellRoute.EditTransaction -> EditTransactionRoute(route, state, model, fill)
                        is ShellRoute.PickCategory -> PickCategoryRoute(state, model, fill)
                        is ShellRoute.Reconcile -> ReconcileRoute(state, model, fill)
                        is ShellRoute.ReviewCategory -> ReviewCategoryRoute(state, model, fill)
                        is ShellRoute.CategoryBudget -> CategoryBudgetRoute(route, state, model, fill)
                        is ShellRoute.MoveCategory -> MoveCategoryRoute(route, state, model, fill)
                        ShellRoute.HoldMonth -> HoldMonthRoute(state, model, fill)
                        ShellRoute.StarterCategories ->
                            StarterCategoriesScreen(
                                error = state.editError,
                                onAdd = model.getStarted::addStarterCategories,
                                modifier = fill,
                            )
                        ShellRoute.AddAccount ->
                            AddAccountScreen(error = state.editError, onSave = model.accounts::submitAdd, modifier = fill)
                        is ShellRoute.EditAccount -> EditAccountRoute(route, state, model, fill)
                        is ShellRoute.CloseAccount -> CloseAccountRoute(route, state, model, fill)
                        is ShellRoute.ScheduleEditor -> ScheduleEditorRoute(route, state, model, fill)
                        ShellRoute.Rules -> RulesScreen(state.rules.list, model.rules::openEditor, fill)
                        ShellRoute.Schedules -> SchedulesRoute(state, model, fill)
                        is ShellRoute.ScheduleDetails -> ScheduleDetailsRoute(route, state, model, fill)
                        ShellRoute.MonthReview -> MonthReviewRoute(state, model, fill)
                        ShellRoute.NetWorth -> NetWorthRoute(state, model, fill)
                        is ShellRoute.ReportPivot -> ReportPivotRoute(route, state, model, fill)
                        is ShellRoute.BankLink -> BankLinkRoute(route, state, model, fill)
                        is ShellRoute.TemplateKinds -> TemplateKindsRoute(route, state, model, fill)
                        is ShellRoute.TemplateEditor -> TemplateEditorRoute(route, state, model, fill)
                        ShellRoute.Templates -> TemplatesRoute(state, model, fill)
                        is ShellRoute.RuleEditor -> RuleEditorRoute(route, state, model, fill)
                        is ShellRoute.ToBudget -> ToBudgetRoute(route, state, model, fill)
                        ShellRoute.ManageCategories -> ManageCategoriesRoute(state, model, fill)
                    }
                    if (state.confirm != null) {
                        ReconcileWarningScreen(
                            onChange = model::confirmWrite,
                            onKeep = model::dismissConfirm,
                            modifier = fill,
                        )
                    }
                    val fillConfirm = state.monthUi.fillConfirm
                    if (fillConfirm != null) {
                        FillConfirmScreen(
                            confirm = fillConfirm,
                            month = state.pinnedMonth ?: state.openMonth,
                            onConfirm = model.month::confirmFill,
                            onKeep = model.month::keepFill,
                            modifier = fill,
                        )
                    }
                    if (state.schedulesUi.duplicateId != null) {
                        ScheduleDuplicateScreen(
                            onPostAnyway = model.schedules::confirmDuplicate,
                            onCancel = model.schedules::cancelDuplicate,
                            modifier = fill,
                        )
                    }
                    val advanced = state.settingsUi.confirmAdvanced
                    if (advanced != null) {
                        ConfirmAdvancedScreen(
                            kind = advanced,
                            onConfirm = {
                                when (advanced) {
                                    AdvancedConfirm.Rebuild -> model.settingsUi.confirmRebuild()
                                    AdvancedConfirm.Repair -> model.settingsUi.confirmRepair()
                                    AdvancedConfirm.ResetSync -> model.settingsUi.confirmResetSync()
                                }
                            },
                            onCancel = model.settingsUi::cancelAdvanced,
                            modifier = fill,
                        )
                    }
                }
            }
        }
    }
}

/** The name of the budget open on the phone, or empty when none is. */
internal fun ShellUiState.openBudgetName(): String =
    budgets
        .find { it.id == settings.openBudgetId }
        ?.name
        .orEmpty()

/** An editor under the month control; a [title] (the category being edited) heads the page above it. */
@Composable
internal fun EditorWithMonth(
    openMonth: YearMonth,
    monthWindow: app.duenorth.budget.design.MonthWindow,
    onSelectMonth: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable (Modifier) -> Unit,
) {
    Column(modifier.fillMaxSize().background(Metro.colors.background)) {
        if (title != null) {
            MetroText(
                title,
                Metro.typography.header,
                Modifier
                    .padding(start = MetroDimens.Gutter, end = MetroDimens.Gutter, top = MetroDimens.Gutter)
                    .testTag("editor-title"),
            )
        }
        MonthControl(
            month = openMonth,
            window = monthWindow,
            onSelect = onSelectMonth,
            modifier =
                Modifier.padding(
                    start = MetroDimens.Gutter,
                    end = MetroDimens.Gutter,
                    top = if (title != null) MetroDimens.Grid else MetroDimens.Gutter,
                    bottom = MetroDimens.Grid,
                ),
        )
        Box(Modifier.weight(1f).fillMaxSize()) {
            content(Modifier.fillMaxSize())
        }
    }
}
