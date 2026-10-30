package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.duenorth.budget.design.components.LayerCorner
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.PillCorner
import app.duenorth.budget.design.components.controlFrame
import app.duenorth.budget.design.components.layerClip
import app.duenorth.budget.design.components.layerShape
import app.duenorth.budget.design.components.styleCard
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

/**
 * The style surfaces of core:design side by side under each style, light and dark: a card, a chosen card, a small
 * card, a badge, a bar, a control frame and a field. A change to one style's surfaces shows here first.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class StyleSurfacesScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun metroLight() = snap(MetroStyle.Metro, dark = false)

    @Test
    fun metroDark() = snap(MetroStyle.Metro, dark = true)

    @Test
    fun tileReliefLight() = snap(MetroStyle.TileRelief, dark = false)

    @Test
    fun tileReliefDark() = snap(MetroStyle.TileRelief, dark = true)

    @Test
    fun micaLight() = snap(MetroStyle.Mica, dark = false)

    @Test
    fun micaDark() = snap(MetroStyle.Mica, dark = true)

    @Test
    fun mobile10Light() = snap(MetroStyle.Mobile10, dark = false)

    @Test
    fun mobile10Dark() = snap(MetroStyle.Mobile10, dark = true)

    private fun snap(
        style: MetroStyle,
        dark: Boolean,
    ) {
        compose.setContent {
            MetroTheme(darkTheme = dark, style = style) {
                Column(
                    Modifier.width(390.dp).background(Metro.colors.background).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f).styleCard().padding(12.dp)) { MetroText("card", Metro.typography.body) }
                        Box(Modifier.weight(1f).styleCard(selected = true).padding(12.dp)) {
                            MetroText("chosen", Metro.typography.body)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.styleCard(small = true).padding(horizontal = 10.dp, vertical = 6.dp)) {
                            MetroText("small", Metro.typography.caption)
                        }
                        Box(Modifier.styleCard(selected = true, small = true).padding(horizontal = 10.dp, vertical = 6.dp)) {
                            MetroText("small, chosen", Metro.typography.caption)
                        }
                        Box(Modifier.background(Metro.accent.fill, layerShape(LayerCorner.Badge)).padding(horizontal = 7.dp)) {
                            MetroText("1", Metro.typography.caption, color = Metro.accent.onFill)
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .layerClip(PillCorner)
                            .background(Metro.colors.secondary.copy(alpha = 0.25f)),
                    ) {
                        Box(Modifier.weight(0.6f).height(6.dp).background(Metro.accent.fill))
                        Box(Modifier.weight(0.4f))
                    }
                    Box(Modifier.fillMaxWidth().controlFrame().padding(12.dp)) { MetroText("control frame", Metro.typography.body) }
                    MetroField("Corner Coffee", {}, "payee")
                }
            }
        }
        compose.waitForIdle()
        val name = "style_surfaces_${style.id.replace('-', '_')}_${if (dark) "dark" else "light"}"
        compose.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }
}
