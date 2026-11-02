package app.duenorth.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.duenorth.budget.core.ActualMonthMath
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

/** The unassigned figure tapped: Actual's apply and overwrite open under it, each saying what it would budget. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w390dp-h780dp-xxhdpi")
class DistributeMenuScreenshotTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun menuLight() = snap("distribute_menu_light", dark = false)

    @Test
    fun menuDark() = snap("distribute_menu_dark", dark = true)

    @Test
    fun closedLight() = snap("distribute_closed_light", dark = false, open = false)

    @Test
    fun closedDark() = snap("distribute_closed_dark", dark = true, open = false)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun snap(
        name: String,
        dark: Boolean,
        open: Boolean = true,
    ) {
        val shell = TemplateSamples.shell.copy(ahead = ActualMonthMath.FuturePlans(TemplateSamples.shell.headerMinor - 40_000, 202612))
        val menu =
            DistributeMenu(
                shell.month,
                listOf(
                    DistributeOption(false, 3, 31_000L, names = listOf("Power", "Gas", "Groceries")),
                    DistributeOption(true, 5, 48_500L, names = listOf("Rent", "Power", "Gas", "Groceries", "Rainy day")),
                ),
            )
        var shown by mutableStateOf(false)
        compose.setContent {
            MetroTheme(darkTheme = dark) {
                Column(Modifier.fillMaxSize()) {
                    HomePivot(
                        shell = shell,
                        loading = false,
                        openMonth = shell.month,
                        monthWindow = MonthWindow.around(shell.month),
                        initialSection = HomeSections.headers.indexOf(HomeSections.BUDGET),
                        onGesture = {},
                        showAhead = true,
                        distribute = DistributeActions(menu = menu.takeIf { shown }, onOpen = { shown = true }),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (open) compose.onNodeWithTag("free-after-plans-figure").performClick()
        compose.waitForIdle()
        captureScreenRoboImage("src/test/snapshots/$name.png")
    }
}
