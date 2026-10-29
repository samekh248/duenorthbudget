package app.duenorth.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.design.MonthWindow
import app.duenorth.budget.design.theme.MetroStyle
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

/** The featured inbox transaction held down, before its menu opens: the press band covers the whole panel. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w390dp-h780dp-xxhdpi")
class InboxPressScreenshotTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun metroLight() = snap(MetroStyle.Metro, dark = false)

    @Test
    fun metroDark() = snap(MetroStyle.Metro, dark = true)

    @Test
    fun tileReliefLight() = snap(MetroStyle.TileRelief, dark = false)

    @Test
    fun tileReliefDark() = snap(MetroStyle.TileRelief, dark = true)

    @Test
    fun micaLight() = snap(MetroStyle.Mica, dark = false)

    @Test
    fun micaDark() = snap(MetroStyle.Mica, dark = true)

    @Test
    fun mobile10Light() = snap(MetroStyle.Mobile10, dark = false)

    @Test
    fun mobile10Dark() = snap(MetroStyle.Mobile10, dark = true)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun snap(
        style: MetroStyle,
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
            MetroTheme(darkTheme = dark, style = style) {
                Column(Modifier.fillMaxSize()) {
                    HomePivot(
                        shell = shell,
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
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        // Held on the payee, short of the long-press timeout, so the band shows and the menu has not opened.
        compose.onNodeWithTag("inbox-featured-coffee").performTouchInput { down(Offset(width / 3f, height / 5f)) }
        compose.mainClock.advanceTimeBy(250)
        val theme = if (dark) "dark" else "light"
        val name = if (style == MetroStyle.Metro) "inbox_featured_press_$theme" else "style_${style.id}_inbox_featured_press_$theme"
        captureScreenRoboImage("src/test/snapshots/$name.png")
        compose.onNodeWithTag("inbox-featured-coffee").performTouchInput { cancel() }
        compose.mainClock.autoAdvance = true
    }
}
