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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
    val canBack =
        state.confirm != null ||
            (state.route != ShellRoute.Home && !(state.route == ShellRoute.Create && state.shell == null))
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
            Box(modifier) {
                val fill = Modifier.fillMaxSize()
                when (val route = state.route) {
                    ShellRoute.Home ->
                        HomePanorama(
                            shell = state.shell,
                            review = state.monthReview,
                            netWorth = state.netWorth,
                            loading = state.loading,
                            onGesture = model::setGesture,
                            onAccount = model::openAccount,
                            onInbox = model::openInbox,
                            onPreviousReviewMonth = model::previousReviewMonth,
                            onReviewCategory = model::openReviewCategory,
                            onNetWorthIncludeOffBudget = model::setNetWorthIncludeOffBudget,
                            modifier = fill,
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
                                onReconcile = model::openReconcile,
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
                    is ShellRoute.Reconcile -> {
                        val page = state.reconcile
                        val register = state.register
                        if (page == null) {
                            ReconcileStartScreen(
                                accountName = register?.account?.name.orEmpty(),
                                error = state.reconcileError,
                                onStart = model::startReconcile,
                                modifier = fill,
                            )
                        } else {
                            ReconcileScreen(
                                page = page,
                                error = state.reconcileError,
                                onGesture = model::setRegisterGesture,
                                onToggle = model::toggleReconcileCleared,
                                onFinish = model::finishReconcile,
                                onCancel = model::cancelReconcile,
                                modifier = fill,
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
                }
                if (state.confirm != null) {
                    ReconcileWarningScreen(
                        onChange = model::confirmWrite,
                        onKeep = model::dismissConfirm,
                        modifier = fill,
                    )
                }
            }
        }
    }
}
