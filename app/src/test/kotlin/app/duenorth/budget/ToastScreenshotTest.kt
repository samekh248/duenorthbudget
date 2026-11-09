package app.duenorth.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.ScreenAction
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import app.duenorth.budget.design.theme.MetroStyle
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The global toast in each style, light and dark: a success with undo over budget, a failure over a form. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class ToastScreenshotTest {
    private val success = Toast(ToastKind.Success, ToastCopy.CATEGORY_DELETED, "Dining", ToastAction.Undo)
    private val failure = Toast(ToastKind.Failure, ToastCopy.TRANSACTION_SAVE_FAILED, "Server unreachable")

    @Test fun metroSuccessLight() = snap("toast_metro_success_light", MetroStyle.Metro, false, success)

    @Test fun metroSuccessDark() = snap("toast_metro_success_dark", MetroStyle.Metro, true, success)

    @Test fun metroFailureLight() = snap("toast_metro_failure_light", MetroStyle.Metro, false, failure)

    @Test fun metroFailureDark() = snap("toast_metro_failure_dark", MetroStyle.Metro, true, failure)

    @Test fun tileSuccessLight() = snap("toast_tile_success_light", MetroStyle.TileRelief, false, success)

    @Test fun tileSuccessDark() = snap("toast_tile_success_dark", MetroStyle.TileRelief, true, success)

    @Test fun tileFailureLight() = snap("toast_tile_failure_light", MetroStyle.TileRelief, false, failure)

    @Test fun tileFailureDark() = snap("toast_tile_failure_dark", MetroStyle.TileRelief, true, failure)

    @Test fun micaSuccessLight() = snap("toast_mica_success_light", MetroStyle.Mica, false, success)

    @Test fun micaSuccessDark() = snap("toast_mica_success_dark", MetroStyle.Mica, true, success)

    @Test fun micaFailureLight() = snap("toast_mica_failure_light", MetroStyle.Mica, false, failure)

    @Test fun micaFailureDark() = snap("toast_mica_failure_dark", MetroStyle.Mica, true, failure)

    @Test fun mobile10SuccessLight() = snap("toast_w10m_success_light", MetroStyle.Mobile10, false, success)

    @Test fun mobile10SuccessDark() = snap("toast_w10m_success_dark", MetroStyle.Mobile10, true, success)

    @Test fun mobile10FailureLight() = snap("toast_w10m_failure_light", MetroStyle.Mobile10, false, failure)

    @Test fun mobile10FailureDark() = snap("toast_w10m_failure_dark", MetroStyle.Mobile10, true, failure)

    private fun snap(
        name: String,
        style: MetroStyle,
        dark: Boolean,
        toast: Toast,
    ) {
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark, style = style) {
                Column(Modifier.width(390.dp).height(520.dp)) {
                    ShellChrome(
                        actions =
                            listOf(
                                ScreenAction("add", AppGlyph.Add, "add", run = {}),
                                ScreenAction("edit", AppGlyph.Edit, "edit", run = {}),
                                ScreenAction("sync", AppGlyph.Sync, "sync", run = {}),
                            ),
                        statusBar = WindowInsets(top = 0.dp),
                        toast = { at -> ToastHost(toast, {}, {}, at) },
                    ) { modifier ->
                        Column(modifier.padding(MetroDimens.Gutter)) {
                            MetroText("budget", Metro.typography.sectionHeader)
                            listOf("Rent" to "$1,100.00", "Power" to "$140.00", "Groceries" to "$300.00", "Coffee" to "$12.00")
                                .forEach { (n, v) -> MetroText("$n   $v", Metro.typography.body, Modifier.padding(top = 12.dp)) }
                        }
                    }
                }
            }
        }
    }
}
