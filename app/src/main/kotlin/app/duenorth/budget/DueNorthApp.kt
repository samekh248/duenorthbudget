package app.duenorth.budget

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.StrictMode
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import app.duenorth.budget.design.components.AppBarButton
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.theme.Accent
import app.duenorth.budget.design.theme.MetroTheme
import java.io.File

class DueNorthApplication : Application() {
    lateinit var library: BudgetLibrary

    override fun onCreate() {
        super.onCreate()
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (debuggable) {
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
    }

    lateinit var sync: SyncCoordinator
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val application = application as DueNorthApplication
        setContent {
            val model: ShellViewModel =
                viewModel(factory = ShellViewModel.factory(application.library, application.sync))
            DueNorthApp(model)
        }
    }
}

@Composable
fun DueNorthApp(model: ShellViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
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
                if (event == Lifecycle.Event.ON_STOP) model.onScreenLocked()
            }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val canBack =
        state.confirm != null ||
            (
                state.route != ShellRoute.Home &&
                    !(state.route == ShellRoute.Create && state.shell == null) &&
                    !(state.route == ShellRoute.BudgetPassword && state.locked)
            )
    BackHandler(enabled = canBack) { model.back() }
    MetroTheme(
        darkTheme = dark,
        accent = Accent.fromId(state.settings.accent),
        animationsEnabled = MotionPolicy.enabled(scale),
    ) {
        val openName =
            state.budgets
                .find { it.id == state.settings.openBudgetId }
                ?.name
                .orEmpty()
        val buttons =
            listOf(
                AppBarButton(AppGlyph.Budgets, "budgets", model::showBudgets),
                AppBarButton(AppGlyph.Sync, "sync", model::showServer),
                AppBarButton(AppGlyph.More, "payees", model::showPayees),
                AppBarButton(AppGlyph.Appearance, "appearance", model::showAppearance),
            )
        ShellChrome(buttons, syncing = state.syncing, progress = state.syncProgress) { modifier ->
            Box(modifier) {
                val fill = Modifier.fillMaxSize()
                when (val route = state.route) {
                    ShellRoute.Home ->
                        HomePanorama(
                            shell = state.shell,
                            upcoming = state.upcoming,
                            loading = state.loading,
                            onGesture = model::setGesture,
                            onAccount = model::openAccount,
                            onInbox = model::openInbox,
                            onPostSchedule = model::postSchedule,
                            onSkipSchedule = model::skipSchedule,
                            modifier = fill,
                            onAssign = if (state.shell == null) null else model::showAssign,
                            onAddTransaction = if (state.shell == null) null else model::showAddTransaction,
                        )
                    ShellRoute.Budgets ->
                        BudgetsScreen(
                            budgets = state.budgets,
                            openId = state.settings.openBudgetId,
                            onOpen = model::requestSwitch,
                            onCreate = model::showCreate,
                            modifier = fill,
                        )
                    ShellRoute.Create ->
                        CreateBudgetScreen(
                            error = state.createError,
                            onCreate = model::create,
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
                    ShellRoute.Appearance ->
                        AppearanceScreen(
                            themeMode = state.settings.themeMode,
                            accentId = state.settings.accent,
                            onTheme = model::setTheme,
                            onAccent = model::setAccent,
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
                    ShellRoute.AddTransaction ->
                        AddTransactionScreen(
                            error = state.editError,
                            onAdd = model::addTransaction,
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
                                filter = state.registerFilter,
                                onFilter = model::setRegisterFilter,
                                onGesture = model::setRegisterGesture,
                                onOpen = model::openEdit,
                                onAdd = model::openNewTransaction,
                                onTransfer = model::openTransfer,
                                modifier = fill,
                            )
                        }
                    }
                    is ShellRoute.EditTransaction -> {
                        val page = state.register
                        if (page == null) {
                            Placeholder()
                        } else {
                            TransactionForm(
                                page = page,
                                row = page.rows.firstOrNull { it.id == route.transactionId },
                                initialDate = state.today,
                                error = state.writeError,
                                onSave = model::submitTransaction,
                                onDelete = model::deleteTransaction,
                                onSplit = model::openSplit,
                                onSchedule = model::createScheduleFromTransaction,
                                modifier = fill,
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
                    is ShellRoute.TransferMoney -> {
                        val page = state.register
                        if (page == null) {
                            Placeholder()
                        } else {
                            TransferForm(
                                page = page,
                                initialDate = state.today,
                                error = state.writeError,
                                onSave = model::submitTransfer,
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
                }
                if (state.confirm != null) {
                    ReconcileWarningScreen(
                        onChange = model::confirmWrite,
                        onKeep = model::dismissConfirm,
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
            }
        }
    }
}
