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
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.duenorth.budget.core.BudgetClock
import app.duenorth.budget.core.BudgetLibrary
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
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val library = (application as DueNorthApplication).library
        setContent {
            val model: ShellViewModel = viewModel(factory = ShellViewModel.factory(library))
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
    val canBack = state.route != ShellRoute.Home && !(state.route == ShellRoute.Create && state.shell == null)
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
                AppBarButton(AppGlyph.Appearance, "appearance", model::showAppearance),
            )
        ShellChrome(buttons) { modifier ->
            when (state.route) {
                ShellRoute.Home ->
                    HomePanorama(
                        shell = state.shell,
                        loading = state.loading,
                        onGesture = model::setGesture,
                        modifier = modifier,
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
            }
        }
    }
}
