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
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.FormatOptions
import app.duenorth.budget.core.HttpActualTransport
import app.duenorth.budget.core.OnboardingOrigin
import app.duenorth.budget.core.RegisterEntry
import app.duenorth.budget.core.ServerAddress
import app.duenorth.budget.core.SyncCoordinator
import app.duenorth.budget.core.TemplateLines
import app.duenorth.budget.core.TemplateNote
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
import java.util.Locale

open class DueNorthApplication : Application() {
    lateinit var library: BudgetLibrary
    var developerMode: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
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
                model.completeOpenId(intent?.dataString)
            }
            DueNorthApp(model)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        shellModel?.completeOpenId(intent.dataString)
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
            model.previewImportFile(accountId, bytes, name)
        }
    LaunchedEffect(state.openIdRedirectUrl) {
        val url = state.openIdRedirectUrl ?: return@LaunchedEffect
        openWebPage(context, url)
        model.clearOpenIdRedirect()
    }
    LaunchedEffect(state.pendingExport) {
        val export = state.pendingExport ?: return@LaunchedEffect
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
        model.clearPendingExport()
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
            state.fillConfirm != null ||
            state.confirmAdvanced != null ||
            (
                state.route != ShellRoute.Home &&
                    state.route != ShellRoute.Welcome &&
                    !(state.route == ShellRoute.Create && state.shell == null && state.onboarding == null && !state.settingsReturn) &&
                    !(state.route == ShellRoute.BudgetPassword && state.locked)
            )
    BackHandler(enabled = canBack) {
        if (state.confirmAdvanced != null) {
            model.cancelAdvanced()
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
        val openName =
            state.budgets
                .find { it.id == state.settings.openBudgetId }
                ?.name
                .orEmpty()
        val handlers =
            remember(state.route, importAccountId) {
                ScreenBarHandlers(
                    onAdd = {
                        when (state.route) {
                            is ShellRoute.Register -> model.openNewTransaction()
                            else -> model.showAddTransaction()
                        }
                    },
                    onHold = model::showHoldMonth,
                    onCategories = model::showManageCategories,
                    onSettings = model::showSettings,
                    onReconcile = model::openReconcile,
                    onImport = {
                        val accountId =
                            (state.route as? ShellRoute.Register)?.accountId ?: return@ScreenBarHandlers
                        importAccountId = accountId
                        importLauncher.launch("*/*")
                    },
                    onBankSync = {
                        val accountId =
                            (state.route as? ShellRoute.Register)?.accountId ?: return@ScreenBarHandlers
                        model.fetchBankTransactions(accountId)
                    },
                    onSave = {
                        when (state.route) {
                            is ShellRoute.ImportPreviewRoute -> model.confirmImport()
                            is ShellRoute.Reconcile -> model.finishReconcile()
                            else -> formSaveHost.save()
                        }
                    },
                    onClearSelected = { model.setSelectedCleared(true) },
                    onUnclearSelected = { model.setSelectedCleared(false) },
                    onEndSelecting = model::endSelecting,
                    onSearch = model::toggleRegisterSearch,
                    onRemoveTemplate = model::removeTemplate,
                    onAddTemplate = { model.openTemplateKinds() },
                    onMoveCategory = model::openMoveCategory,
                    onRollover = model::toggleCategoryCarryover,
                    onAutoHold = model::chooseAutoHold,
                    onSelectInbox = model::startInboxSelecting,
                    onAcceptSelected = model::acceptSelectedInbox,
                    onCategorizeSelected = model::categorizeSelectedInbox,
                    onEndInboxSelecting = model::endInboxSelecting,
                    onSyncServer = model::syncInbox,
                    onCancel = {
                        when (state.route) {
                            is ShellRoute.ImportPreviewRoute -> model.cancelImport()
                            is ShellRoute.Reconcile -> model.cancelReconcile()
                            else -> model.back()
                        }
                    },
                    onFill = { kind -> model.fillMonth(kind) },
                    onToBudget = model::openToBudget,
                    onUndo = model::undo,
                    onAddAccount = model::openAddAccount,
                    onShowClosed = model::toggleShowClosedAccounts,
                    onShowHidden = model::toggleShowHiddenCategories,
                    onReorder = model::toggleReorderAccounts,
                    onEditAccount = model::openEditAccount,
                    onCloseAccount = model::openCloseAccount,
                    onReopenAccount = model::reopenOpenAccount,
                    onAddSchedule = model::openNewSchedule,
                    onRules = model::openRules,
                    onAddRule = model::openNewRule,
                    onDeleteSchedule = model::deleteOpenSchedule,
                    onDeleteRule = model::deleteOpenRule,
                    onSchedules = model::openSchedules,
                    onSchedulesView = model::toggleSchedulesCalendar,
                    onPostSchedule = model::postOpenSchedule,
                    onSkipSchedule = model::skipOpenSchedule,
                    onEditSchedule = model::editOpenSchedule,
                    onTemplates = model::openTemplates,
                    onBankLink = model::openBankLink,
                    onNext = model::onboardingCheck,
                    onRefresh = model::onboardingRefresh,
                    onSamples = model::showSampleBudgets,
                    onNewBudget = model::onboardingNewBudget,
                    onSettingsNew = model::showSettingsCreate,
                    onSettingsPayees = model::showSettingsPayees,
                    onAddServer = model::onboardingAddServer,
                    onServerBudgets = model::showServerBudgets,
                    onSignOut = model::signOutInstance,
                    onExport = model::exportBudget,
                    onAddSplit = { formSaveHost.run(FORM_ADD_SPLIT) },
                    onScheduleTransaction = {
                        (state.route as? ShellRoute.EditTransaction)?.transactionId?.let(model::createScheduleFromTransaction)
                    },
                    onDeleteTransaction = { (state.route as? ShellRoute.EditTransaction)?.transactionId?.let(model::deleteTransaction) },
                    onEditBudget = model::startBudgetEdit,
                    onEndBudgetEdit = model::endBudgetEdit,
                    onCancelBudgetEdit = model::cancelBudgetEdit,
                )
            }
        val actions = formSaveHost.bar ?: ScreenBar.forState(state, handlers)
        ShellChrome(
            actions,
            syncing = state.syncing || state.importFetchPending,
            progress = state.syncProgress,
            bar = state.route != ShellRoute.Home || state.budgetEdit?.categoryId == null,
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
                    when (val route = state.route) {
                        ShellRoute.Home ->
                            HomePivot(
                                shell = state.editingShell(),
                                loading = state.loading,
                                openMonth = state.openMonth,
                                monthWindow = model.monthWindow(),
                                initialSection = state.homeSection,
                                onGesture = model::setGesture,
                                onSection = model::selectHomeSection,
                                onAccount = model::openAccount,
                                accountsReorder = state.accountsReorder,
                                onMoveAccount = model::moveAccount,
                                onInbox = model::openInbox,
                                onAcceptInbox = model::acceptInbox,
                                inboxFiled = state.inboxFiled,
                                onInboxFiledShown = model::inboxFiledShown,
                                inboxSelection = state.inboxSelection,
                                onInboxToggle = model::toggleInboxSelected,
                                inboxLead = state.inboxLead,
                                inboxNotice = state.inboxSyncNotice,
                                inboxActions =
                                    remember(model) {
                                        InboxActions(
                                            onDetails = model::openInboxDetails,
                                            onSplit = model::openInboxSplit,
                                            onSchedule = model::createScheduleFromTransaction,
                                            onDelete = model::deleteInbox,
                                            onOpenAccount = model::openAccount,
                                            onMatch = { model.matchTransactions(it, stayOnRegister = false) },
                                            onNotAMatch = { model.notAMatch(it, stayOnRegister = false) },
                                        )
                                    },
                                netWorth = state.netWorth,
                                onNetWorth = model::openNetWorth,
                                onMonthReview = model::openMonthReview,
                                onReport = model::openReport,
                                modifier = fill,
                                onSelectMonth = model::selectOpenMonth,
                                onBudgetCategory = if (state.shell == null) null else model::openCategoryBudget,
                                onAddTransaction = if (state.shell == null) null else model::showAddTransaction,
                                // Tapping to budget opens edit mode; in edit mode it opens the breakdown.
                                onToBudget = if (state.shell == null) null else model::startBudgetEdit,
                                showAhead = state.freeAfterPlansOn,
                                budgetEdit = state.budgetEdit,
                                budgetEditActions =
                                    remember(model) {
                                        BudgetEditActions(
                                            onFocus = model::focusBudgetBox,
                                            onKey = model::typeBudgetKey,
                                            onCover = model::coverBudgetBox,
                                            onAll = model::allToBudgetBox,
                                            onDone = model::doneBudgetBox,
                                            onAvailable = { id ->
                                                if (model.doneBudgetBox()) model.openMoveFrom(id)
                                            },
                                            // The breakdown reads the saved month, not edit mode's changes.
                                            onToBudget = null,
                                        )
                                    },
                                editError = state.editError,
                                notice = state.monthNotice,
                                formatOptions =
                                    FormatOptions(
                                        hideFraction = state.formatPrefs.hideFraction,
                                        numberFormat = state.formatPrefs.numberFormat,
                                    ),
                            )
                        ShellRoute.Settings -> {
                            var launcherIcon by remember { mutableStateOf(LauncherIcons.current(context)) }
                            SettingsScreen(
                                page = state.settingsPage,
                                instances = state.instances,
                                activeInstance = state.activeInstance,
                                instanceReachable = state.instanceReachable,
                                signedIn = state.signedIn,
                                budgets = state.budgets,
                                openBudgetId = state.settings.openBudgetId,
                                themeMode = state.settings.themeMode,
                                accentId = state.settings.accent,
                                styleId = state.settings.style,
                                formatPrefs = state.formatPrefs,
                                cloudFileId = state.cloudFileId,
                                encrypted = state.budgetEncrypted,
                                onPage = model::selectSettingsPage,
                                onMakeActive = model::requestInstanceSwitch,
                                onRemoveInstance = model::requestRemoveInstance,
                                onOpenBudget = model::requestSwitch,
                                onTheme = model::setTheme,
                                onAccent = model::setAccent,
                                onStyle = model::setStyle,
                                launcherIcon = launcherIcon,
                                onLauncherIcon = {
                                    LauncherIcons.set(context, it)
                                    launcherIcon = it
                                },
                                onPayees = model::showSettingsPayees,
                                onDateFormat = model::setDateFormat,
                                onNumberFormat = model::setNumberFormat,
                                onFirstDay = model::setFirstDay,
                                onHideFraction = model::setHideFraction,
                                onExperimental = model::setExperimental,
                                onExperimentalFeature = model::setExperimentalFeature,
                                onLearning = model::setLearning,
                                onExport = model::exportBudget,
                                onEncryption = model::showEncryption,
                                onRequestAdvanced = model::requestAdvanced,
                                phoneSettings = state.settings,
                                onBackgroundSyncHours = model::setBackgroundSyncHours,
                                scroll = state.settingsScroll,
                                onScroll = model::saveSettingsScroll,
                                modifier = fill,
                            )
                        }
                        is ShellRoute.ExperimentalOffer ->
                            ExperimentalOfferScreen(
                                budgetName = state.budgets.find { it.id == route.budgetId }?.name ?: openName,
                                onTurnOn = model::acceptExperimentalOffer,
                                onKeepOff = model::declineExperimentalOffer,
                                current = route.current,
                                modifier = fill,
                            )
                        is ShellRoute.EncryptionPassword ->
                            EncryptionPasswordScreen(
                                mode = route.mode,
                                error = state.encryptionError,
                                onSet = model::setEncryptionPassword,
                                onChange = model::changeEncryptionPassword,
                                onRemove = model::removeEncryptionPassword,
                                modifier = fill,
                            )
                        is ShellRoute.ConfirmRemoveInstance -> {
                            val instance = state.instances.find { it.id == route.instanceId }
                            ConfirmRemoveInstanceScreen(
                                name = instance?.label ?: instance?.address.orEmpty(),
                                onRemove = model::confirmRemoveInstance,
                                onKeep = { model.back() },
                                modifier = fill,
                            )
                        }
                        ShellRoute.ConfirmInstanceSwitch ->
                            ConfirmInstanceSwitchScreen(
                                leaving =
                                    state.activeInstance?.label
                                        ?: state.activeInstance?.address.orEmpty(),
                                opening =
                                    state.switchInstanceTarget?.label
                                        ?: state.switchInstanceTarget?.address.orEmpty(),
                                onOpen = { model.confirmInstanceSwitch() },
                                onStay = model::cancelInstanceSwitch,
                                modifier = fill,
                            )
                        ShellRoute.Budgets ->
                            BudgetsScreen(
                                budgets = state.budgets,
                                openId = state.settings.openBudgetId,
                                onOpen = model::requestSwitch,
                                onCreate = model::showCreate,
                                onSamples = if (state.developerMode) model::showSampleBudgets else null,
                                modifier = fill,
                            )
                        ShellRoute.SampleBudgets ->
                            SampleBudgetsScreen(
                                samples = state.sampleBudgets,
                                error = state.createError,
                                onChoose = model::createSample,
                                modifier = fill,
                            )
                        ShellRoute.Create -> {
                            val draft = state.onboarding?.progress
                            CreateBudgetScreen(
                                error = state.createError,
                                onCreate = model::create,
                                modifier = fill,
                                defaultName = draft?.createName?.ifBlank { null } ?: state.defaultBudgetName,
                                defaultCurrency = draft?.createCurrency ?: Currencies.forLocale(Locale.getDefault()),
                                onDraft = if (draft == null) null else model::onboardingCreateDraft,
                            )
                        }
                        ShellRoute.Welcome ->
                            WelcomeScreen(
                                onServer = model::onboardingChooseServer,
                                onPhone = model::onboardingChoosePhone,
                                modifier = fill,
                            )
                        ShellRoute.OnboardingAddress -> {
                            val onboarding = state.onboarding
                            OnboardingAddressScreen(
                                address = onboarding?.progress?.addressText.orEmpty(),
                                checking = onboarding?.checking == true,
                                error = onboarding?.addressError,
                                onAddress = model::onboardingAddress,
                                onNext = model::onboardingCheck,
                                onPhone = model::onboardingChoosePhone,
                                modifier = fill,
                                offerPhone = onboarding?.progress?.origin != OnboardingOrigin.SETTINGS,
                            )
                        }
                        ShellRoute.OnboardingSignIn -> {
                            val onboarding = state.onboarding
                            val ready = onboarding?.progress?.readyAddress.orEmpty()
                            val host = ServerAddress.parse(ready)?.host ?: ready
                            OnboardingSignInScreen(
                                host = host,
                                methods = onboarding?.progress?.methods.orEmpty(),
                                label = onboarding?.progress?.instanceLabel.orEmpty(),
                                checking = onboarding?.checking == true || state.syncing,
                                error = onboarding?.signInError,
                                onLabel = model::onboardingLabel,
                                onSignIn = model::onboardingSignIn,
                                onBrowser = model::onboardingBrowser,
                                modifier = fill,
                                secure = ready.isEmpty() || ServerAddress.isSecure(ready),
                                replacing =
                                    state.activeInstance
                                        ?.takeIf { onboarding?.progress?.origin == OnboardingOrigin.SETTINGS }
                                        ?.let { it.label ?: it.address },
                            )
                        }
                        ShellRoute.OnboardingFiles ->
                            OnboardingFilesScreen(
                                label =
                                    state.activeInstance?.label
                                        ?: state.onboarding
                                            ?.progress
                                            ?.instanceLabel
                                            .orEmpty(),
                                files = state.remoteFiles,
                                error = state.serverError,
                                onOpen = model::onboardingOpen,
                                modifier = fill,
                                replacing = state.budgets.map { it.name },
                            )
                        ShellRoute.ConfirmReplaceBudget ->
                            ConfirmReplaceBudgetScreen(
                                opening =
                                    state.pendingFile
                                        ?.name
                                        .orEmpty(),
                                clearing = state.replaceBudgets.map { it.name },
                                onUse = model::confirmReplaceBudget,
                                onKeep = model::cancelReplaceBudget,
                                modifier = fill,
                            )
                        ShellRoute.ConfirmSwitch ->
                            ConfirmSwitchScreen(
                                opening = state.switchTarget?.name.orEmpty(),
                                leaving = openName,
                                onOpen = model::confirmSwitch,
                                onStay = model::cancelSwitch,
                                modifier = fill,
                            )
                        ShellRoute.Payees ->
                            PayeesScreen(
                                payees = state.payees,
                                error = state.writeError,
                                mergeTarget = state.payeeMergeTarget,
                                onRename = model::renamePayee,
                                onConfirmMerge = model::confirmPayeeMerge,
                                onCancelMerge = model::cancelPayeeMerge,
                                modifier = fill,
                            )
                        ShellRoute.Server ->
                            ServerScreen(
                                address = state.settings.serverAddress.orEmpty(),
                                signedIn = state.signedIn,
                                files = state.remoteFiles,
                                status = state.syncStatus,
                                error = state.serverError,
                                conflictCount = state.conflicts.size,
                                onConflicts = model::showConflicts,
                                onConnect = model::connect,
                                onOpen = model::requestRemote,
                                onReplace = model::requestReplace,
                                onUpload = model::uploadNew,
                                onSync = model::syncNow,
                                onSignOut = model::signOut,
                                modifier = fill,
                            )
                        ShellRoute.BudgetPassword ->
                            BudgetPasswordScreen(
                                error = state.passwordError,
                                askEachTime = state.askEachTime,
                                onAskEachTime = model::setAskEachTime,
                                onSubmit = { password -> model.submitPassword(password, state.askEachTime) },
                                modifier = fill,
                            )
                        ShellRoute.Assign ->
                            AssignScreen(
                                categories = state.categories,
                                error = state.editError,
                                onAssign = model::assign,
                                modifier = fill,
                            )
                        ShellRoute.Conflicts ->
                            ConflictScreen(
                                conflicts = state.conflicts,
                                modifier = fill,
                            )
                        ShellRoute.ConfirmReplace ->
                            ConfirmReplaceScreen(
                                serverName = state.replaceFile?.name.orEmpty(),
                                phoneName = openName,
                                onReplace = model::confirmReplace,
                                onKeep = model::cancelReplace,
                                modifier = fill,
                            )
                        is ShellRoute.Register -> {
                            val page = state.register
                            if (page == null) {
                                if (state.loading) Placeholder()
                            } else {
                                RegisterScreen(
                                    page = page,
                                    onGesture = model::setRegisterGesture,
                                    onOpen = model::openEdit,
                                    selection = state.registerSelection,
                                    onSelect = model::startSelecting,
                                    onToggleSelected = model::toggleSelected,
                                    onAdd = model::openNewTransaction,
                                    onReconcile = model::openReconcile,
                                    onImportFile = {
                                        importAccountId = route.accountId
                                        importLauncher.launch("*/*")
                                    },
                                    modifier = fill,
                                    onMatch = { model.matchTransactions(it, stayOnRegister = true) },
                                    onNotAMatch = { model.notAMatch(it, stayOnRegister = true) },
                                    search = state.registerSearch,
                                    onSearch = model::setRegisterSearch,
                                )
                            }
                        }
                        is ShellRoute.ImportPreviewRoute -> {
                            val preview = state.importPreview
                            if (preview == null) {
                                Placeholder()
                            } else {
                                ImportPreviewScreen(
                                    preview = preview,
                                    error = state.importError,
                                    onConfirm = model::confirmImport,
                                    onCancel = model::cancelImport,
                                    modifier = fill,
                                )
                            }
                        }
                        is ShellRoute.ImportReviewRoute -> {
                            val review = state.importReview
                            if (review == null) {
                                Placeholder()
                            } else {
                                ImportReviewScreen(
                                    page = review,
                                    error = state.importError,
                                    onCategory = model::setImportReviewCategory,
                                    onDone = model::finishImportReview,
                                    modifier = fill,
                                )
                            }
                        }
                        is ShellRoute.EditTransaction -> {
                            val page = state.register
                            if (page == null) {
                                Placeholder()
                            } else {
                                val prefill = state.newTransactionPrefill.takeIf { route.transactionId == null }
                                TransactionForm(
                                    page = page,
                                    row = page.rows.firstOrNull { it.id == route.transactionId },
                                    initialDate = prefill?.dateIso ?: state.today,
                                    initialAmountMinor = prefill?.amountMinor,
                                    error = state.writeError,
                                    onSave = model::submitTransaction,
                                    modifier = fill,
                                    dateFormat = state.formatPrefs.dateFormat,
                                    numberFormat = state.formatPrefs.numberFormat,
                                    hideFraction = state.formatPrefs.hideFraction,
                                    payees = PayeeTypeAhead.names(state.payees),
                                )
                            }
                        }
                        is ShellRoute.SplitTransaction -> {
                            val page = state.register
                            val row = page?.rows?.firstOrNull { it.id == route.transactionId }
                            if (page == null || row == null) {
                                Placeholder()
                            } else {
                                SplitForm(
                                    page = page,
                                    row = row,
                                    error = state.writeError,
                                    onSave = model::submitSplit,
                                    onUnsplit = model::unsplit,
                                    modifier = fill,
                                )
                            }
                        }
                        is ShellRoute.PickCategory -> {
                            val target = state.categoryTarget
                            if (target == null) {
                                if (state.loading) Placeholder()
                            } else {
                                CategoryPickerScreen(
                                    target = target,
                                    error = state.writeError,
                                    onPick = model::submitCategory,
                                    modifier = fill,
                                )
                            }
                        }
                        is ShellRoute.Reconcile -> {
                            val register = state.register
                            if (register == null) {
                                Placeholder()
                            } else {
                                ReconcileRegisterScreen(
                                    register = register,
                                    page = state.reconcile,
                                    error = state.reconcileError,
                                    onGesture = model::setRegisterGesture,
                                    onStart = model::startReconcile,
                                    onToggle = model::toggleReconcileCleared,
                                    onSetCleared = model::setReconcileCleared,
                                    onOpen = model::openEdit,
                                    onAddMissing = model::addMissingForReconcile,
                                    onFinish = model::finishReconcile,
                                    onCancel = model::cancelReconcile,
                                    modifier = fill,
                                    today = RegisterEntry.parseDate(state.today) ?: RegisterEntry.parseDate(RegisterEntry.todayIso())!!,
                                )
                            }
                        }
                        is ShellRoute.ReviewCategory -> {
                            val page = state.categoryMonth
                            if (page == null) {
                                Placeholder()
                            } else {
                                CategoryReviewScreen(
                                    page = page,
                                    onGesture = model::setGesture,
                                    modifier = fill,
                                )
                            }
                        }
                        is ShellRoute.CategoryBudget -> {
                            val shell = state.shell
                            if (shell == null) {
                                Placeholder()
                            } else {
                                val income = shell.incomeCategory(route.categoryId)
                                val categories = shell.groups.flatMap { it.categories }
                                val name = income?.name ?: categories.firstOrNull { it.id == route.categoryId }?.name
                                EditorWithMonth(
                                    openMonth = state.openMonth,
                                    monthWindow = model.monthWindow(),
                                    onSelectMonth = model::selectOpenMonth,
                                    modifier = fill,
                                    title = name,
                                ) { inner ->
                                    if (income != null) {
                                        IncomeCategoryScreen(
                                            shell = shell,
                                            category = income,
                                            error = state.editError,
                                            onSave = model::saveCategoryBudget,
                                            onAutoHold = model::chooseAutoHold,
                                            modifier = inner,
                                            showName = false,
                                        )
                                        return@EditorWithMonth
                                    }
                                    CategoryBudgetScreen(
                                        shell = shell,
                                        categoryId = route.categoryId,
                                        error = state.editError,
                                        onSave = model::saveCategoryBudget,
                                        onMove = model::openMoveCategory,
                                        onToggleCarryover = model::toggleCategoryCarryover,
                                        modifier = inner,
                                        showName = false,
                                        note = state.categoryNote.takeIf { state.formatPrefs.templatesOn },
                                        onSaveNote = model::saveCategoryNoteText,
                                        templateAmounts = state.categoryTemplateAmounts,
                                        onOpenTemplate = model::openTemplate,
                                        onMoveTemplate = model::moveTemplate,
                                    )
                                    LaunchedEffect(state.categoryNote, shell.month) { model.refreshTemplateAmounts() }
                                }
                            }
                        }
                        is ShellRoute.MoveCategory -> {
                            val shell = state.editingShell()
                            if (shell == null) {
                                Placeholder()
                            } else if (state.budgetEdit != null) {
                                // From edit mode the move lands in its sandbox, on the month being edited.
                                MoveCategoryScreen(
                                    shell = shell,
                                    fromCategoryId = route.fromCategoryId,
                                    error = state.editError,
                                    onSave = model::submitMoveCategory,
                                    modifier = fill,
                                )
                            } else {
                                EditorWithMonth(
                                    openMonth = state.openMonth,
                                    monthWindow = model.monthWindow(),
                                    onSelectMonth = model::selectOpenMonth,
                                    modifier = fill,
                                ) { inner ->
                                    MoveCategoryScreen(
                                        shell = shell,
                                        fromCategoryId = route.fromCategoryId,
                                        error = state.editError,
                                        onSave = model::submitMoveCategory,
                                        modifier = inner,
                                    )
                                }
                            }
                        }
                        ShellRoute.HoldMonth -> {
                            val shell = state.shell
                            if (shell == null) {
                                Placeholder()
                            } else {
                                EditorWithMonth(
                                    openMonth = state.openMonth,
                                    monthWindow = model.monthWindow(),
                                    onSelectMonth = model::selectOpenMonth,
                                    modifier = fill,
                                ) { inner ->
                                    HoldMonthScreen(
                                        shell = shell,
                                        error = state.editError,
                                        onSave = model::submitHold,
                                        onRelease = model::releaseHold,
                                        modifier = inner,
                                    )
                                }
                            }
                        }
                        ShellRoute.Breakdown -> {
                            val shell = state.shell
                            val breakdown = state.breakdown
                            if (shell == null || breakdown == null) {
                                Placeholder()
                            } else {
                                BreakdownScreen(
                                    month = state.pinnedMonth ?: state.openMonth,
                                    breakdown = breakdown,
                                    currency = shell.currency,
                                    modifier = fill,
                                    showAhead = state.freeAfterPlansOn,
                                )
                            }
                        }
                        ShellRoute.AddAccount ->
                            AddAccountScreen(error = state.editError, onSave = model::submitAddAccount, modifier = fill)
                        is ShellRoute.EditAccount -> {
                            val detail = state.accountDetail
                            if (detail == null || detail.id != route.accountId) {
                                Placeholder()
                            } else {
                                EditAccountScreen(detail, state.editError, model::submitEditAccount, fill)
                            }
                        }
                        is ShellRoute.CloseAccount -> {
                            val detail = state.accountDetail
                            val shell = state.shell
                            val force = state.accountForceConfirm
                            when {
                                detail == null || shell == null || detail.id != route.accountId -> Placeholder()
                                force != null ->
                                    ForceCloseConfirmScreen(force, model::confirmForceClose, model::keepAccount, fill)
                                else ->
                                    CloseAccountScreen(
                                        account = detail,
                                        others = state.accountsList,
                                        categories = state.categories,
                                        currency = shell.currency,
                                        error = state.editError,
                                        onClose = { destination, category -> model.submitCloseAccount(destination, category) },
                                        onForceClose = { model.submitCloseAccount(null, null, forced = true) },
                                        modifier = fill,
                                    )
                            }
                        }
                        is ShellRoute.ScheduleEditor -> {
                            val draft = state.scheduleDraft
                            val shell = state.shell
                            if (draft == null || shell == null || draft.id != route.scheduleId) {
                                Placeholder()
                            } else {
                                ScheduleEditorScreen(
                                    draft = draft,
                                    accounts = shell.accounts,
                                    categories = state.categories.filter { !it.income || draft.amountMinor > 0 },
                                    decimals = shell.currency.decimals,
                                    error = state.editError,
                                    onSave = model::submitSchedule,
                                    modifier = fill,
                                    payees = PayeeTypeAhead.names(state.payees),
                                )
                            }
                        }
                        ShellRoute.Rules -> RulesScreen(state.rulesList, model::openRule, fill)
                        ShellRoute.Schedules -> {
                            val shell = state.shell
                            if (shell == null) {
                                Placeholder()
                            } else {
                                SchedulesScreen(
                                    schedules = state.schedules,
                                    currency = shell.currency,
                                    today = RegisterEntry.parseDate(state.today) ?: 0,
                                    section = state.schedulesSection,
                                    calendar = state.schedulesCalendar,
                                    onSection = model::setSchedulesSection,
                                    onOpen = model::openScheduleDetails,
                                    onPost = model::postSchedule,
                                    onSkip = model::skipSchedule,
                                    onGesture = model::setGesture,
                                    modifier = fill,
                                )
                            }
                        }
                        is ShellRoute.ScheduleDetails -> {
                            val details = state.scheduleDetails
                            val shell = state.shell
                            if (details == null || shell == null || details.summary.id != route.scheduleId) {
                                Placeholder()
                            } else {
                                ScheduleDetailsScreen(details, shell.currency, fill)
                            }
                        }
                        ShellRoute.MonthReview ->
                            MonthReviewSection(
                                review = state.monthReview,
                                loading = state.loading,
                                onListGesture = model::setGesture,
                                onCategory = model::openReviewCategory,
                                modifier = fill.background(Metro.colors.background),
                            )
                        ShellRoute.NetWorth ->
                            NetWorthSection(
                                page = state.netWorth,
                                loading = state.loading,
                                onIncludeOffBudget = model::setNetWorthIncludeOffBudget,
                                modifier = fill,
                            )
                        is ShellRoute.ReportPivot ->
                            ReportsScreen(
                                page = state.reports,
                                loading = state.reportsLoading,
                                preset = state.reportPreset,
                                groupBy = state.reportGroupBy,
                                includeOffBudget = state.settings.netWorthIncludeOffBudget,
                                onPreset = model::setReportPreset,
                                onCustom = model::setReportRange,
                                onGroupBy = model::setReportGroupBy,
                                onIncludeOffBudget = model::setReportIncludeOffBudget,
                                modifier = fill,
                                initialItem = route.item,
                            )
                        is ShellRoute.BankLink -> {
                            val ui = state.bankLink
                            val shell = state.shell
                            if (ui == null || shell == null || ui.accountId != route.accountId) {
                                Placeholder()
                            } else {
                                BankLinkScreen(
                                    ui = ui,
                                    currency = shell.currency,
                                    onProvider = model::pickBankProvider,
                                    onToken = model::sendSimpleFinToken,
                                    onCountry = model::findGoCardlessBanks,
                                    onInstitution = model::startGoCardless,
                                    onOpenBank = { url -> openWebPage(context, url) },
                                    onSignedIn = model::checkGoCardless,
                                    onLink = model::linkBank,
                                    onRemove = model::removeBankLink,
                                    modifier = fill,
                                )
                            }
                        }
                        is ShellRoute.TemplateKinds -> {
                            val shell = state.shell
                            val category = shell?.groups?.flatMap { it.categories }?.firstOrNull { it.id == route.categoryId }
                            if (category == null) {
                                Placeholder()
                            } else {
                                TemplateKindsScreen(
                                    categoryName = category.name,
                                    line =
                                        route.index?.let { index ->
                                            state.categoryNote?.let {
                                                TemplateNote
                                                    .parse(it)
                                                    .entries
                                                    .getOrNull(index)
                                                    ?.line
                                            }
                                        },
                                    onPick = model::pickTemplateKind,
                                    onRemove = model::removeTemplate,
                                    modifier = fill,
                                )
                            }
                        }
                        is ShellRoute.TemplateEditor -> {
                            val shell = state.shell
                            val category = shell?.groups?.flatMap { it.categories }?.firstOrNull { it.id == route.categoryId }
                            if (shell == null || category == null) {
                                Placeholder()
                            } else {
                                TemplateEditorScreen(
                                    categoryName = category.name,
                                    kind = route.kind,
                                    initial = state.templateDraftStart(route),
                                    isNew = route.index == null,
                                    month = shell.month,
                                    currency = shell.currency,
                                    schedules = state.templateSchedules,
                                    sources =
                                        listOf(
                                            PercentSource(TemplateLines.ALL_INCOME, TemplateLines.ALL_INCOME),
                                            PercentSource(TemplateLines.AVAILABLE_FUNDS, TemplateLines.AVAILABLE_FUNDS),
                                        ) + shell.incomeGroups.flatMap { it.categories }.map { PercentSource(it.name, it.name) },
                                    preview = state.templatePreview,
                                    onPreview = model::previewTemplate,
                                    onSave = model::saveTemplate,
                                    onRemove = model::removeTemplate,
                                    modifier = fill,
                                )
                            }
                        }
                        ShellRoute.Templates -> {
                            // A note saved from a chip's form (or a chip moved) changes the lanes.
                            LaunchedEffect(state.undoLabel) { model.refreshTemplatePlan() }
                            TemplatesScreen(
                                month = state.budgetEdit?.month ?: state.pinnedMonth ?: state.openMonth,
                                result = state.templateResult,
                                busy = state.templateBusy,
                                confirm = state.templateConfirm,
                                onCheck = {
                                    model.checkTemplates()
                                    model.refreshTemplatePlan()
                                },
                                onApply = { model.previewApplyTemplates(overwrite = false) },
                                onOverwrite = { model.previewApplyTemplates(overwrite = true) },
                                onConfirm = model::confirmTemplateOverwrite,
                                onKeep = model::keepTemplates,
                                modifier = fill,
                                plan = state.templatePlan,
                                currency = state.shell?.currency,
                                applyPreview = state.templateApplyPreview,
                                onOpenChip = model::openTemplateChip,
                                onMoveChip = model::moveTemplateToPriority,
                                onConfirmPreview = model::confirmApplyPreview,
                                onKeepPreview = model::keepApplyPreview,
                            )
                        }
                        is ShellRoute.RuleEditor -> {
                            val draft = state.ruleDraft
                            val shell = state.shell
                            if (draft == null || shell == null || draft.id != route.ruleId) {
                                Placeholder()
                            } else {
                                RuleEditorScreen(
                                    draft = draft,
                                    names = model.ruleNames(),
                                    decimals = shell.currency.decimals,
                                    error = state.editError,
                                    onSave = model::submitRule,
                                    modifier = fill,
                                )
                            }
                        }
                        is ShellRoute.ToBudget -> {
                            val shell = state.shell
                            if (shell == null) {
                                Placeholder()
                            } else {
                                ToBudgetScreen(
                                    shell = shell,
                                    categoryId = route.categoryId,
                                    error = state.editError,
                                    onSave = model::submitToBudget,
                                    modifier = fill,
                                )
                            }
                        }
                        ShellRoute.ManageCategories -> {
                            val manage = state.categoryManage
                            if (manage == null) {
                                Placeholder()
                            } else {
                                ManageCategoriesScreen(
                                    manage = manage,
                                    error = state.editError,
                                    onAddGroup = model::addCategoryGroup,
                                    onAddCategory = model::addEnvelopeCategory,
                                    onRenameGroup = model::renameCategoryGroup,
                                    onRenameCategory = model::renameEnvelopeCategory,
                                    onHideCategory = model::hideEnvelopeCategory,
                                    onDeleteCategory = model::deleteEnvelopeCategory,
                                    onDeleteGroup = model::deleteCategoryGroup,
                                    onMoveGroup = model::moveCategoryGroupOrder,
                                    onMoveCategory = model::moveEnvelopeCategoryOrder,
                                    modifier = fill,
                                )
                            }
                        }
                    }
                    if (state.confirm != null) {
                        ReconcileWarningScreen(
                            onChange = model::confirmWrite,
                            onKeep = model::dismissConfirm,
                            modifier = fill,
                        )
                    }
                    val fillConfirm = state.fillConfirm
                    if (fillConfirm != null) {
                        FillConfirmScreen(
                            confirm = fillConfirm,
                            month = state.pinnedMonth ?: state.openMonth,
                            onConfirm = model::confirmFill,
                            onKeep = model::keepFill,
                            modifier = fill,
                        )
                    }
                    if (state.scheduleDuplicateId != null) {
                        ScheduleDuplicateScreen(
                            onPostAnyway = model::confirmScheduleDuplicate,
                            onCancel = model::cancelScheduleDuplicate,
                            modifier = fill,
                        )
                    }
                    val advanced = state.confirmAdvanced
                    if (advanced != null) {
                        ConfirmAdvancedScreen(
                            kind = advanced,
                            onConfirm = {
                                when (advanced) {
                                    AdvancedConfirm.Rebuild -> model.confirmRebuild()
                                    AdvancedConfirm.Repair -> model.confirmRepair()
                                    AdvancedConfirm.ResetSync -> model.confirmResetSync()
                                }
                            },
                            onCancel = model::cancelAdvanced,
                            modifier = fill,
                        )
                    }
                }
            }
        }
    }
}

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
