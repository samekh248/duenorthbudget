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
import app.duenorth.budget.design.components.ScreenAction
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A category's page with templates on, and its bar's `•••` menu open: add template lives there. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class CategoryMenuScreenshotTest {
    @Test
    fun menuLight() = snap("category_menu_light", dark = false)

    @Test
    fun menuDark() = snap("category_menu_dark", dark = true)

    private fun snap(
        name: String,
        dark: Boolean,
    ) {
        val actions =
            ScreenBar.specsFor(ShellRoute.CategoryBudget("c-food"), true, BudgetMode.ENVELOPE, templates = true).map {
                ScreenAction(it.id, it.glyph, it.label, primary = it.primary) {}
            }
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark) {
                Column(Modifier.width(390.dp)) {
                    CompositionLocalProvider(LocalFormSaveHost provides rememberFormSaveHost()) {
                        CategoryBudgetScreen(
                            TemplateSamples.shell,
                            "c-food",
                            null,
                            {},
                            {},
                            {},
                            Modifier.height(560.dp),
                            note = TemplateSamples.NOTE,
                            templateAmounts = listOf(220_000),
                        )
                    }
                    MetroAppBar(actions, true, {}, windowInsets = WindowInsets(bottom = 0.dp))
                }
            }
        }
    }
}
