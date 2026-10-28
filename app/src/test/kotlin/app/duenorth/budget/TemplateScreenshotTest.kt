package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.TemplateCopy
import app.duenorth.budget.core.TemplateResult
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Spec 010 T063: templates and the category note in light and dark. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class TemplateScreenshotTest {
    @Test
    fun invalidLight() = snap("templates_invalid_light", dark = false, hosted = true) { Templates(TemplateSamples.invalid, null) }

    @Test
    fun invalidDark() = snap("templates_invalid_dark", dark = true, hosted = true) { Templates(TemplateSamples.invalid, null) }

    @Test
    fun overwriteConfirmLight() =
        snap("templates_confirm_light", dark = false) {
            Templates(TemplateResult.Valid, TemplateCopy.OVERWRITE_CONFIRM)
        }

    @Test
    fun overwriteConfirmDark() =
        snap("templates_confirm_dark", dark = true) { Templates(TemplateResult.Valid, TemplateCopy.OVERWRITE_CONFIRM) }

    @Test
    fun categoryNoteLight() = snap("category_note_light", dark = false) { Note() }

    @Test
    fun categoryNoteDark() = snap("category_note_dark", dark = true) { Note() }

    @Composable
    private fun Templates(
        result: TemplateResult,
        confirm: String?,
    ) {
        TemplatesScreen(TemplateSamples.october, result, false, confirm, {}, {}, {}, {}, {})
    }

    @Composable
    private fun Note() {
        CategoryBudgetScreen(TemplateSamples.shell, "c-food", null, {}, {}, {}, note = TemplateSamples.NOTE)
    }

    private fun snap(
        name: String,
        dark: Boolean,
        hosted: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark) {
                if (hosted) HostedPage(content) else Box(Modifier.width(390.dp).height(780.dp)) { content() }
            }
        }
    }
}
