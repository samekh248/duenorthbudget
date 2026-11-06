package app.duenorth.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.GroupRow
import app.duenorth.budget.design.components.MetroAppBar
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Covering an overspent category: gas is $200 over. Its page has cover on the bar; cover picks where money comes from. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class CoverScreenshotTest {
    @Test
    fun categoryPageLight() = pageSnap("cover_category_page_light", dark = false)

    @Test
    fun categoryPageDark() = pageSnap("cover_category_page_dark", dark = true)

    @Test
    fun coverLight() = coverSnap("cover_screen_light", dark = false)

    @Test
    fun coverDark() = coverSnap("cover_screen_dark", dark = true)

    private fun pageSnap(
        name: String,
        dark: Boolean,
    ) {
        val actions =
            ScreenBar.specsFor(ShellRoute.CategoryBudget("c-gas"), true, BudgetMode.ENVELOPE, overspent = true).map { it.action() }
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark) {
                Column(Modifier.width(390.dp)) {
                    CompositionLocalProvider(LocalFormSaveHost provides rememberFormSaveHost()) {
                        CategoryBudgetScreen(shell, "c-gas", null, {}, {}, {}, Modifier.height(640.dp))
                    }
                    MetroAppBar(actions, false, {}, windowInsets = WindowInsets(bottom = 0.dp))
                }
            }
        }
    }

    private fun coverSnap(
        name: String,
        dark: Boolean,
    ) {
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark) {
                HostedPage { CoverCategoryScreen(shell, "c-gas", null, { _, _ -> }, initialFrom = "c-gas-less") }
            }
        }
    }

    // Gas: $200 carried in, $800 budgeted, $1,200 spent: $200 over. Coffee has $50, less than gas needs.
    private val shell =
        TemplateSamples.shell.copy(
            groups =
                listOf(
                    GroupRow(
                        "g-liv",
                        "Living",
                        250_000,
                        listOf(
                            CategoryRow("c-food", "Food", 220_000, -20_000, 270_000, false),
                            CategoryRow("c-gas", "Gas", 80_000, -120_000, -20_000, false),
                            CategoryRow("c-gas-less", "Coffee", 5_000, 0, 5_000, false),
                        ),
                    ),
                    GroupRow(
                        "g-bills",
                        "Bills",
                        0,
                        listOf(
                            CategoryRow("c-rent", "Rent", 120_000, -120_000, 0, false),
                            CategoryRow("c-util", "Utilities", 12_000, -9_500, 2_500, false),
                        ),
                    ),
                ),
        )
}
