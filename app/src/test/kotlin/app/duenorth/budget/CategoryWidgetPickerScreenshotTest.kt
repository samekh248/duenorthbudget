package app.duenorth.budget

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.FormatOptions
import app.duenorth.budget.core.GroupRow
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The page that chooses a category widget's categories: every category under its group, ticked ones in the accent. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class CategoryWidgetPickerScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun pickerLight() = snap("category_widget_picker_light", dark = false)

    @Test
    fun pickerDark() = snap("category_widget_picker_dark", dark = true)

    @Test
    fun tappingARowTicksIt() {
        var toggled: String? = null
        compose.setContent {
            MetroTheme(darkTheme = false) { screen(setOf("groceries")) { toggled = it } }
        }
        compose.onNodeWithTag("widget-pick-gas").performClick()
        assertEquals("gas", toggled)
    }

    private fun snap(
        name: String,
        dark: Boolean,
    ) {
        compose.setContent {
            MetroTheme(darkTheme = dark) { screen(setOf("groceries", "dining", "gas")) {} }
        }
        compose.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    @androidx.compose.runtime.Composable
    private fun screen(
        chosen: Set<String>,
        onToggle: (String) -> Unit,
    ) = CategoryWidgetPickerScreen(
        groups = groups,
        chosen = chosen,
        currency = Currencies.byCode("USD"),
        options = FormatOptions(),
        loading = false,
        onToggle = onToggle,
        onCancel = {},
        onDone = {},
        modifier = Modifier.width(411.dp).height(891.dp),
    )

    private val groups =
        listOf(
            GroupRow(
                "everyday",
                "Everyday",
                0,
                listOf(row("groceries", "Groceries", 21_240), row("dining", "Dining Out", -1_825), row("gas", "Gas", 9_600)),
            ),
            GroupRow("home", "Home", 0, listOf(row("household", "Household", 13_010), row("kids", "Kids", 7_500))),
            GroupRow("fun", "Fun", 0, listOf(row("fun", "Fun Money", 4_500), row("coffee", "Coffee", 850))),
        )

    private fun row(
        id: String,
        name: String,
        available: Long,
    ) = CategoryRow(id, name, 10_000, available - 10_000, available, carryover = false)
}
