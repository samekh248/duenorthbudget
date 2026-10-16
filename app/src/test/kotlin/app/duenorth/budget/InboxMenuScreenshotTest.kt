package app.duenorth.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.design.MonthWindow
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

/** The inbox's long-press menu, which opens in its own window over the home pivot. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w390dp-h780dp-xxhdpi")
class InboxMenuScreenshotTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun menuLight() = snap("inbox_menu_light", dark = false)

    @Test
    fun menuDark() = snap("inbox_menu_dark", dark = true)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun snap(
        name: String,
        dark: Boolean,
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
        compose.setContent {
            MetroTheme(darkTheme = dark) {
                Column(Modifier.fillMaxSize()) {
                    HomePivot(
                        shell = shell,
                        review = null,
                        netWorth = null,
                        upcoming = emptyList(),
                        loading = false,
                        openMonth = shell.month,
                        monthWindow = MonthWindow.around(shell.month),
                        initialSection = HomeSections.headers.indexOf(HomeSections.INBOX),
                        onGesture = {},
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        compose.onNodeWithTag("inbox-row-shell").performTouchInput { longClick() }
        compose.waitForIdle()
        captureScreenRoboImage("src/test/snapshots/$name.png")
    }
}
