package app.duenorth.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.design.MonthWindow
import app.duenorth.budget.design.components.MetroAppBar
import app.duenorth.budget.design.components.ScreenAction
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/** The inbox with its `•••` menu open: sync above settings, and the line it leaves under the totals. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w390dp-h780dp-xxhdpi")
class InboxSyncScreenshotTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun menuLight() = snap("inbox_sync_menu_light", dark = false)

    @Test
    fun menuDark() = snap("inbox_sync_menu_dark", dark = true)

    @Test
    fun syncedLight() = snap("inbox_synced_light", dark = false, expanded = false, notice = "synced 12:05 pm")

    @Test
    fun syncedDark() = snap("inbox_synced_dark", dark = true, expanded = false, notice = "synced 12:05 pm")

    @OptIn(ExperimentalRoborazziApi::class)
    private fun snap(
        name: String,
        dark: Boolean,
        expanded: Boolean = true,
        notice: String? = null,
    ) {
        val shell =
            MonthShell(
                budgetId = "home",
                budgetName = "Home",
                mode = BudgetMode.ENVELOPE,
                currency = Currencies.byCode("USD")!!,
                month = YearMonth.of(2026, 10),
                headerLabel = "to budget",
                headerMinor = 0,
                groups = emptyList(),
                accounts = emptyList(),
                inbox = sampleInbox(),
            )
        val actions =
            ScreenBar
                .specsFor(
                    ShellRoute.Home,
                    hasBudget = true,
                    mode = BudgetMode.ENVELOPE,
                    homeSection = ScreenBar.HOME_INBOX,
                    inboxSync = true,
                ).map { ScreenAction(it.id, it.glyph, it.label, primary = it.primary) {} }
        compose.setContent {
            MetroTheme(darkTheme = dark) {
                Column(Modifier.fillMaxSize()) {
                    HomePivot(
                        shell = shell,
                        loading = false,
                        openMonth = shell.month,
                        monthWindow = MonthWindow.around(shell.month),
                        initialSection = HomeSections.headers.indexOf(HomeSections.INBOX),
                        onGesture = {},
                        modifier = Modifier.weight(1f),
                        inboxNotice = notice,
                    )
                    MetroAppBar(actions, expanded, {}, windowInsets = WindowInsets(0))
                }
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("src/test/snapshots/$name.png")
    }
}
