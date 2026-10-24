package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.Template
import app.duenorth.budget.core.TemplateKind
import app.duenorth.budget.core.TemplateResult
import app.duenorth.budget.design.theme.MetroStyle
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.math.BigDecimal
import java.time.YearMonth

/** The template editor (option A, template tiles: tiles, the grid of kinds and a form; option D, the lanes), in every style, light and dark. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class TemplateEditorScreenshotTest {
    @Test
    fun tiles() = every("template_tiles") { Tiles() }

    @Test
    fun kinds() = every("template_kinds") { TemplateKindsScreen("Food", null, {}, {}) }

    @Test
    fun byDateForm() = every("template_form_by") { Form() }

    @Test
    fun lanes() = every("template_lanes") { Lanes(null) }

    @Test
    fun applyList() = every("template_apply_list") { Lanes(TemplateSamples.applyPreview) }

    @Composable
    private fun Lanes(preview: TemplateApplyPreview?) {
        TemplatesScreen(
            TemplateSamples.october,
            TemplateResult.Invalid(emptyList()),
            false,
            null,
            {},
            {},
            {},
            {},
            {},
            plan = TemplateSamples.plan,
            currency = TemplateSamples.shell.currency,
            applyPreview = preview,
        )
    }

    @Composable
    private fun Tiles() {
        CategoryBudgetScreen(
            TemplateSamples.shell,
            "c-food",
            null,
            {},
            {},
            {},
            note = TemplateSamples.TILES,
            templateAmounts = TemplateSamples.tileAmounts,
        )
    }

    @Composable
    private fun Form() {
        val by = Template.By(BigDecimal("1200"), YearMonth.of(2026, 12), 12, annual = true, repeat = null, priority = 2)
        TemplateEditorScreen(
            categoryName = "Food",
            kind = TemplateKind.BY_DATE,
            initial = by,
            isNew = false,
            month = TemplateSamples.october,
            currency = TemplateSamples.shell.currency,
            schedules = emptyList(),
            sources = emptyList(),
            preview = by to 40_000L,
            onPreview = {},
            onSave = {},
            onRemove = {},
        )
    }

    private fun every(
        name: String,
        content: @Composable () -> Unit,
    ) {
        for (style in MetroStyle.entries) {
            for (dark in listOf(false, true)) {
                val prefix = if (style == MetroStyle.Metro) "" else "style_${style.id}_"
                captureRoboImage("src/test/snapshots/$prefix${name}_${if (dark) "dark" else "light"}.png") {
                    MetroTheme(darkTheme = dark, style = style) {
                        Box(Modifier.width(390.dp).height(780.dp)) {
                            SectionTitled(HomeSections.BUDGET.uppercase()) { content() }
                        }
                    }
                }
            }
        }
    }
}
