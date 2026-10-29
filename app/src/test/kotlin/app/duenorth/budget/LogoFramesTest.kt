package app.duenorth.budget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The logo's motion as a strip of frames, light and dark, for review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w1200dp-h260dp-xxhdpi")
class LogoFramesTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun framesLight() = strip(dark = false)

    @Test
    fun framesDark() = strip(dark = true)

    private fun strip(dark: Boolean) {
        compose.setContent {
            MetroTheme(darkTheme = dark) {
                val color = Metro.accent.fill
                Row(
                    Modifier.background(Metro.colors.background).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    FRAMES_MS.forEach { ms ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Canvas(Modifier.height(100.dp).width(100.dp * LogoMotion.ASPECT)) {
                                drawLogo(ms / LogoMotion.DURATION_MS.toFloat(), color)
                            }
                            MetroText("$ms ms", Metro.typography.caption, Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(
            "src/test/snapshots/logo_frames_${if (dark) "dark" else "light"}.png",
        )
    }

    private companion object {
        val FRAMES_MS = listOf(0, 80, 160, 240, 320, 400, 480, 560, 640, 700)
    }
}
