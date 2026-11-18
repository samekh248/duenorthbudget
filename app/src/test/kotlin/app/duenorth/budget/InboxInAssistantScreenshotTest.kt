package app.duenorth.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToKey
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.design.MonthWindow
import app.duenorth.budget.design.components.MetroAppBar
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Home's pivot item that holds the inbox, with transactions waiting and findings to look at. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w390dp-h780dp-xxhdpi")
class InboxInAssistantScreenshotTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun assistantLight() = snap("inbox_in_assistant_light", HomeSections.ASSISTANT, dark = false)

    @Test
    fun assistantDark() = snap("inbox_in_assistant_dark", HomeSections.ASSISTANT, dark = true)

    // Scrolled past the inbox: the assistant's cards carry on under it, and the last date has gone off the top.
    @Test
    fun scrolledLight() = snap("inbox_in_assistant_scrolled_light", HomeSections.ASSISTANT, dark = false, scrolled = true)

    @Test
    fun scrolledDark() = snap("inbox_in_assistant_scrolled_dark", HomeSections.ASSISTANT, dark = true, scrolled = true)

    private fun snap(
        name: String,
        section: String,
        dark: Boolean,
        scrolled: Boolean = false,
    ) {
        val shell = shellSample()
        val index = HomeSections.headers.indexOf(section)
        val actions =
            ScreenBar
                .specsFor(ShellRoute.Home, hasBudget = true, mode = BudgetMode.ENVELOPE, homeSection = index)
                .map { it.action() }
        compose.setContent {
            MetroTheme(darkTheme = dark) {
                Column(Modifier.fillMaxSize()) {
                    HomePivot(
                        shell = shell,
                        loading = false,
                        openMonth = shell.month,
                        monthWindow = MonthWindow.around(shell.month),
                        initialSection = index,
                        onGesture = {},
                        modifier = Modifier.weight(1f),
                        assistant = AssistantUi(report = sampleAssistant()),
                    )
                    MetroAppBar(actions, false, {}, windowInsets = WindowInsets(0))
                }
            }
        }
        compose.waitForIdle()
        if (scrolled) {
            compose.onNodeWithTag("inbox-list").performScrollToKey("count")
            compose.waitForIdle()
            compose.onNodeWithTag("assistant-count").assertIsDisplayed()
        }
        captureScreenRoboImage("src/test/snapshots/$name.png")
    }
}
