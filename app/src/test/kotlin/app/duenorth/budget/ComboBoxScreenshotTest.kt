package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.duenorth.budget.design.components.MetroComboBox
import app.duenorth.budget.design.components.MetroComboValue
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.controlFrame
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroStyle
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class ComboBoxScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun micaLight() = snap("style_mica_combo_light", dark = false)

    @Test
    fun micaDark() = snap("style_mica_combo_dark", dark = true)

    /** Mica's combo boxes beside a text box: one closed, one open on its choices, and the date box. */
    private fun snap(
        name: String,
        dark: Boolean,
    ) {
        val choices = listOf("Groceries", "Dining out", "Coffee", "Household")
        compose.setContent {
            MetroTheme(darkTheme = dark, style = MetroStyle.Mica) {
                Column(
                    Modifier.width(390.dp).background(Metro.colors.background).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetroText("Payee", Metro.typography.caption, color = Metro.colors.secondary)
                    MetroField("Corner Coffee", {}, "payee")
                    MetroText("Date", Metro.typography.caption, color = Metro.colors.secondary)
                    MetroComboValue("Wed 10/7/2026", "date", {}, Modifier.fillMaxWidth().controlFrame())
                    MetroText("Category", Metro.typography.caption, color = Metro.colors.secondary)
                    MetroComboBox(choices, "Coffee", { it }, {}, "closed")
                    MetroText("Category, open", Metro.typography.caption, color = Metro.colors.secondary)
                    MetroComboBox(choices, "Coffee", { it }, {}, "open")
                }
            }
        }
        compose.onNodeWithTag("open-value", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }
}
