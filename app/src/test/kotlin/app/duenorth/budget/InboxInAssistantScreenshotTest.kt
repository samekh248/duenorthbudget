package app.duenorth.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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

    // The inbox card folded shut, like the assistant's other cards: its count in the circle.
    @Test
    fun collapsedLight() = snap("inbox_in_assistant_collapsed_light", HomeSections.ASSISTANT, dark = false, collapse = true)

    @Test
    fun collapsedDark() = snap("inbox_in_assistant_collapsed_dark", HomeSections.ASSISTANT, dark = true, collapse = true)

    private fun snap(
        name: String,
        section: String,
        dark: Boolean,
        collapse: Boolean = false,
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
        compose.onNodeWithTag("assistant-kind-count-INBOX", useUnmergedTree = true).onChild().assertTextEquals("6")
        if (collapse) {
            compose.onNodeWithTag("assistant-kind-INBOX").performClick()
            compose.waitForIdle()
        }
        captureScreenRoboImage("src/test/snapshots/$name.png")
    }
}
