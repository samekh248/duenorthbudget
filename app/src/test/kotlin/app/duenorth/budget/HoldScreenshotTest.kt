package app.duenorth.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.design.components.MetroAppBar
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Hold for next month: $552 to budget, $200 already held. The form asks how much more to hold. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class HoldScreenshotTest {
    @Test
    fun holdLight() = snap("hold_month_light", dark = false)

    @Test
    fun holdDark() = snap("hold_month_dark", dark = true)

    private fun snap(
        name: String,
        dark: Boolean,
    ) {
        val actions = ScreenBar.specsFor(ShellRoute.HoldMonth, true, BudgetMode.ENVELOPE).map { it.action() }
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark) {
                Column(Modifier.width(390.dp)) {
                    CompositionLocalProvider(LocalFormSaveHost provides rememberFormSaveHost()) {
                        HoldMonthScreen(shell, null, {}, {}, Modifier.height(560.dp))
                    }
                    MetroAppBar(actions, false, {}, windowInsets = WindowInsets(bottom = 0.dp))
                }
            }
        }
    }

    private val shell = TemplateSamples.shell.copy(bufferedMinor = 20_000)
}
