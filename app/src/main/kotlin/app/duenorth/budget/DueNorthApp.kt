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
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.ShellCopy
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
                        onGroup = model::showGroup,
                        onPreviousMonth = model::previousMonth,
                        onNextMonth = model::nextMonth,
                        onHold = model::showHold,
                        onNewGroup = model::showAddGroup,
                    )
                is ShellRoute.Group -> {
                    val route = state.route as ShellRoute.Group
                    val page = state.page
                    val categories = page?.categories?.filter { it.groupId == route.id }.orEmpty()
                    val name =
                        page
                            ?.shell
                            ?.groups
                            ?.find { it.id == route.id }
                            ?.name
                            ?: page?.categories?.find { it.groupId == route.id }?.let { "group" }
                            ?: ""
                    GroupScreen(
                        groupName = name,
                        categories = categories,
                        currency = page?.shell?.currency ?: Currencies.byCode("USD")!!,
                        notice = state.notice,
                        onListGesture = model::setGesture,
                        onCategory = model::showCategory,
                        onAssign = model::assign,
                        onRename = { model.renameGroup(route.id, it) },
                        onAdd = { model.addCategory(route.id, it) },
                        onUp = { model.moveGroup(route.id, -1) },
                        onDown = { model.moveGroup(route.id, 1) },
                        onDelete = { model.deleteGroup(route.id) },
                        onShow = { model.hideCategory(it, false) },
                        modifier = modifier,
                    )
                }
                is ShellRoute.Category -> {
                    val route = state.route as ShellRoute.Category
                    val page = state.page
                    val category = page?.categories?.find { it.id == route.id }
                    if (page == null || category == null) {
                        Placeholder()
                    } else {
                        CategoryScreen(
                            category = category,
                            others = page.categories.filter { !it.hidden && it.id != category.id },
                            currency = page.shell.currency,
                            notice = state.notice,
                            onAssign = { model.assign(category.id, it) },
                            onMove = { otherId, text ->
                                if (category.availableMinor < 0) {
                                    model.move(otherId, category.id, text)
                                } else {
                                    model.move(category.id, otherId, text)
                                }
                            },
                            onCarryover = { model.carryover(category.id, it) },
                            onHide = { model.hideCategory(category.id, true) },
                            onDelete = { model.deleteCategory(category.id) },
                            modifier = modifier,
                        )
                    }
                }
                ShellRoute.Hold ->
                    HoldScreen(
                        held = state.shell?.bufferedMinor ?: 0L,
                        currency = state.shell?.currency ?: Currencies.byCode("USD")!!,
                        notice = state.notice,
                        onHold = model::hold,
                        onRelease = model::releaseHold,
                        modifier = modifier,
                    )
                ShellRoute.AddGroup ->
                    NameScreen(
                        title = ShellCopy.NEW_GROUP,
                        notice = state.notice,
                        onSave = model::addGroup,
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
