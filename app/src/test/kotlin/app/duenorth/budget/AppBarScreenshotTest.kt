package app.duenorth.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.MetroAppBar
import app.duenorth.budget.design.components.ScreenAction
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class AppBarScreenshotTest {
    @Test
    fun homeBarLight() = snap("app_bar_home_light", home, dark = false, expanded = false)

    @Test
    fun homeBarExpandedLight() = snap("app_bar_home_expanded_light", home, dark = false, expanded = true)

    @Test
    fun registerBarDark() = snap("app_bar_register_dark", register, dark = true, expanded = false)

    @Test
    fun registerBarExpandedDark() = snap("app_bar_register_expanded_dark", register, dark = true, expanded = true)

    // A phone with three-button navigation drawn edge to edge: the bar pads above the buttons.
    @Test
    fun homeBarOverNavigationLight() = snap("app_bar_home_nav_light", home, dark = false, expanded = false, navigation = 48.dp)

    @Test
    fun registerBarOverNavigationDark() = snap("app_bar_register_nav_dark", register, dark = true, expanded = false, navigation = 48.dp)

    private fun snap(
        name: String,
        actions: List<ScreenAction>,
        dark: Boolean,
        expanded: Boolean,
        navigation: Dp = 0.dp,
    ) {
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark) {
                Column(Modifier.width(390.dp)) {
                    MetroAppBar(actions, expanded, {}, windowInsets = WindowInsets(bottom = navigation))
                }
            }
        }
    }

    private val home =
        listOf(
            ScreenAction("add", AppGlyph.Add, "add") {},
            ScreenAction("hold", AppGlyph.Hold, "hold") {},
            ScreenAction("categories", AppGlyph.Categories, "categories") {},
            ScreenAction("settings", AppGlyph.Settings, "settings") {},
        )

    private val register =
        listOf(
            ScreenAction("add", AppGlyph.Add, "add") {},
            ScreenAction("transfer", AppGlyph.Transfer, "transfer") {},
            ScreenAction("reconcile", AppGlyph.Reconcile, "reconcile") {},
            ScreenAction("import", AppGlyph.Import, "import") {},
        )
}
