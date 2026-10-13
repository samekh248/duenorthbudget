package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.ActualMonthMath
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.MonthFillCopy
import app.duenorth.budget.core.MonthFillKind
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/** Spec 010 T027: the breakdown and the fill confirm in light and dark. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class MonthActionScreenshotTest {
    private val october = YearMonth.of(2026, 10)
    private val usd = Currencies.byCode("USD")!!
    private val parts = ActualMonthMath.MonthBreakdown(420_000, 35_000, -4_800, -375_000, -20_000, 55_200)

    @Test
    fun breakdownLight() = snap("breakdown_light", dark = false) { BreakdownScreen(october, parts, usd) }

    @Test
    fun breakdownDark() = snap("breakdown_dark", dark = true) { BreakdownScreen(october, parts, usd) }

    @Test
    fun fillConfirmLight() = snap("fill_confirm_light", dark = false) { FillConfirm() }

    @Test
    fun fillConfirmDark() = snap("fill_confirm_dark", dark = true) { FillConfirm() }

    @Composable
    private fun FillConfirm() {
        FillConfirmScreen(FillConfirm(MonthFillKind.COPY_LAST, MonthFillCopy.REPLACES), october, onConfirm = {}, onKeep = {})
    }

    private fun snap(
        name: String,
        dark: Boolean,
        content: @Composable () -> Unit,
    ) {
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark) {
                Box(Modifier.width(390.dp).height(780.dp)) { content() }
            }
        }
    }
}
