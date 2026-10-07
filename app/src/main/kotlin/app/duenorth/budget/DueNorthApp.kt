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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
        state.route != ShellRoute.Home &&
            !(state.route == ShellRoute.Create && state.shell == null) &&
            !(state.route == ShellRoute.BudgetPassword && state.locked)
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
                AppBarButton(AppGlyph.Appearance, "appearance", model::showAppearance),
            )
        ShellChrome(buttons, syncing = state.syncing, progress = state.syncProgress) { modifier ->
            when (state.route) {
                ShellRoute.Home ->
                    HomePanorama(
                        shell = state.shell,
                        loading = state.loading,
                        onGesture = model::setGesture,
                        modifier = modifier,
                        onAssign = if (state.shell == null) null else model::showAssign,
                        onAddTransaction = if (state.shell == null) null else model::showAddTransaction,
                    )
                ShellRoute.Budgets ->
                    BudgetsScreen(
                        budgets = state.budgets,
                        openId = state.settings.openBudgetId,
                        onOpen = model::requestSwitch,
                        onCreate = model::showCreate,
                        modifier = modifier,
                    )
                ShellRoute.Create ->
                    CreateBudgetScreen(
                        error = state.createError,
                        onCreate = model::create,
                        modifier = modifier,
                    )
                ShellRoute.ConfirmSwitch ->
                    ConfirmSwitchScreen(
                        opening = state.switchTarget?.name.orEmpty(),
                        leaving = openName,
                        onOpen = model::confirmSwitch,
                        onStay = model::cancelSwitch,
                        modifier = modifier,
                    )
                ShellRoute.Appearance ->
                    AppearanceScreen(
                        themeMode = state.settings.themeMode,
                        accentId = state.settings.accent,
                        onTheme = model::setTheme,
                        onAccent = model::setAccent,
                        modifier = modifier,
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
                        modifier = modifier,
                    )
                ShellRoute.BudgetPassword ->
                    BudgetPasswordScreen(
                        error = state.passwordError,
                        askEachTime = state.askEachTime,
                        onAskEachTime = model::setAskEachTime,
                        onSubmit = { password -> model.submitPassword(password, state.askEachTime) },
                        modifier = modifier,
                    )
                ShellRoute.Assign ->
                    AssignScreen(
                        categories = state.categories,
                        error = state.editError,
                        onAssign = model::assign,
                        modifier = modifier,
                    )
                ShellRoute.AddTransaction ->
                    AddTransactionScreen(
                        error = state.editError,
                        onAdd = model::addTransaction,
                        modifier = modifier,
                    )
                ShellRoute.Conflicts ->
                    ConflictScreen(
                        conflicts = state.conflicts,
                        modifier = modifier,
                    )
                ShellRoute.ConfirmReplace ->
                    ConfirmReplaceScreen(
                        serverName = state.replaceFile?.name.orEmpty(),
                        phoneName = openName,
                        onReplace = model::confirmReplace,
                        onKeep = model::cancelReplace,
                        modifier = modifier,
                    )
            }
        }
    }
}
