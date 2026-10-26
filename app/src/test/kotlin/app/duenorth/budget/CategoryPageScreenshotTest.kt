package app.duenorth.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryMonthPage
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.GroupRow
import app.duenorth.budget.core.ReviewTransactionRow
import app.duenorth.budget.design.components.MetroAppBar
import app.duenorth.budget.design.components.ScreenAction
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A category's whole page with templates on: description, available, recent, the budget and templates, rollover. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h1100dp-xxhdpi")
class CategoryPageScreenshotTest {
    @Test
    fun pageLight() = snap("category_page_light", dark = false)

    @Test
    fun pageDark() = snap("category_page_dark", dark = true)

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
                            shell,
                            "c-food",
                            null,
                            {},
                            {},
                            {},
                            Modifier.height(1000.dp),
                            note = TemplateSamples.NOTE,
                            templateAmounts = listOf(220_000),
                            recent = recent,
                        )
                    }
                    MetroAppBar(actions, false, {}, windowInsets = WindowInsets(bottom = 0.dp))
                }
            }
        }
    }

    // $100 carried in from september, $2,200 budgeted, $1,600 spent: $700 left.
    private val shell =
        TemplateSamples.shell.copy(
            groups = listOf(GroupRow("g-liv", "Living", 70_000, listOf(CategoryRow("c-food", "Food", 220_000, -160_000, 70_000, false)))),
        )

    private val recent =
        CategoryMonthPage(
            categoryId = "c-food",
            categoryName = "Food",
            month = TemplateSamples.october,
            currency = shell.currency,
            totalMinor = -160_000,
            rows =
                listOf(
                    ReviewTransactionRow("t1", 20261024, "Trader Joe's", -8_412),
                    ReviewTransactionRow("t2", 20261022, "Thai Basil", -3_850),
                    ReviewTransactionRow("t3", 20261019, "Costco", -21_240),
                    ReviewTransactionRow("t4", 20261015, "Safeway", -9_603),
                ),
        )
}
