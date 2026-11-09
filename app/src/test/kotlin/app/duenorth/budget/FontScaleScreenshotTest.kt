package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.GroupRow
import app.duenorth.budget.design.MonthWindow
import app.duenorth.budget.design.components.MetroAppBar
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/**
 * The screens a person lives in, at the system's largest font size (200%), so a change that clips a figure, a name
 * or an app bar caption at large type shows up as a baseline diff. The transaction form has its own in
 * [TransactionDetailsScreenshotTest].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class FontScaleScreenshotTest {
    @Before
    fun pinCalendarMonth() {
        CalendarMonth.pinned = YearMonth.of(2026, 10)
    }

    @After
    fun unpinCalendarMonth() {
        CalendarMonth.pinned = null
    }

    @Test
    fun budgetLight() = snap("font2x_budget_light", dark = false) { Home(HomeSections.BUDGET) }

    @Test
    fun budgetDark() = snap("font2x_budget_dark", dark = true) { Home(HomeSections.BUDGET) }

    @Test
    fun accountsLight() = snap("font2x_accounts_light", dark = false) { Home(HomeSections.ACCOUNTS) }

    @Test
    fun inboxLight() = snap("font2x_inbox_light", dark = false) { Home(HomeSections.INBOX) }

    @Test
    fun inboxDark() = snap("font2x_inbox_dark", dark = true) { Home(HomeSections.INBOX) }

    @Test
    fun registerLight() =
        snap("font2x_register_light", dark = false) {
            Column(Modifier.width(390.dp).height(780.dp)) {
                SectionTitled("ACCOUNTS", Modifier.weight(1f)) {
                    RegisterScreen(page = registerSample(), onGesture = {}, onOpen = {}, onAdd = {}, onReconcile = {})
                }
                MetroAppBar(
                    ScreenBar.specsFor(ShellRoute.Register("visa"), true, BudgetMode.ENVELOPE).map { it.action() },
                    false,
                    {},
                    windowInsets = WindowInsets(bottom = 0.dp),
                )
            }
        }

    @Test
    fun categoryLight() =
        snap("font2x_category_light", dark = false) {
            val shell =
                TemplateSamples.shell.copy(
                    groups =
                        listOf(
                            GroupRow("g-liv", "Living", 70_000, listOf(CategoryRow("c-food", "Food", 220_000, -160_000, 70_000, false))),
                        ),
                )
            CompositionLocalProvider(LocalFormSaveHost provides rememberFormSaveHost()) {
                Column(Modifier.width(390.dp).height(780.dp)) {
                    CategoryBudgetScreen(shell, "c-food", null, {}, {}, {}, Modifier.weight(1f))
                    MetroAppBar(
                        ScreenBar.specsFor(ShellRoute.CategoryBudget("c-food"), true, BudgetMode.ENVELOPE).map { it.action() },
                        false,
                        {},
                        windowInsets = WindowInsets(bottom = 0.dp),
                    )
                }
            }
        }

    @Test
    fun settingsLight() = snap("font2x_settings_light", dark = false) { SettingsSample(SettingsPage.BUDGET) }

    @Test
    fun appBarExpandedLight() =
        snap("font2x_app_bar_expanded_light", dark = false) {
            Column(Modifier.width(390.dp).height(780.dp)) {
                Box(Modifier.weight(1f))
                MetroAppBar(
                    ScreenBar
                        .specsFor(ShellRoute.Home, hasBudget = true, mode = BudgetMode.ENVELOPE, homeSection = ScreenBar.HOME_BUDGET)
                        .map { it.action() },
                    true,
                    {},
                    windowInsets = WindowInsets(bottom = 0.dp),
                )
            }
        }

    @Composable
    private fun Home(section: String) {
        val shell = shellSample()
        val index = HomeSections.headers.indexOf(section)
        Column(Modifier.width(390.dp).height(780.dp)) {
            Box(Modifier.weight(1f)) {
                HomePivot(
                    shell = shell,
                    loading = false,
                    openMonth = shell.month,
                    monthWindow = MonthWindow.around(shell.month),
                    initialSection = index,
                    onGesture = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
            MetroAppBar(
                ScreenBar.specsFor(ShellRoute.Home, hasBudget = true, mode = BudgetMode.ENVELOPE, homeSection = index).map { it.action() },
                false,
                {},
                windowInsets = WindowInsets(bottom = 0.dp),
            )
        }
    }

    private fun snap(
        name: String,
        dark: Boolean,
        content: @Composable () -> Unit,
    ) {
        captureRoboImage("src/test/snapshots/$name.png") {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, FONT_SCALE)) {
                MetroTheme(darkTheme = dark) { content() }
            }
        }
    }

    private companion object {
        /** Android's largest font size setting. */
        const val FONT_SCALE = 2f
    }
}
