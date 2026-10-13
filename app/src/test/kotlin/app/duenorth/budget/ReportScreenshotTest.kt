package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.ReportPreset
import app.duenorth.budget.core.SpendingGroupBy
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Spec 010 T055: each report in light and dark. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class ReportScreenshotTest {
    @Test
    fun cashFlowLight() = snap("report_cashflow_light", dark = false, item = 0)

    @Test
    fun cashFlowDark() = snap("report_cashflow_dark", dark = true, item = 0)

    @Test
    fun netWorthLight() = snap("report_networth_light", dark = false, item = 1)

    @Test
    fun netWorthDark() = snap("report_networth_dark", dark = true, item = 1)

    @Test
    fun spendingLight() = snap("report_spending_light", dark = false, item = 2)

    @Test
    fun spendingDark() = snap("report_spending_dark", dark = true, item = 2)

    @Composable
    private fun Reports(item: Int) {
        ReportsScreen(ReportSamples.page(), false, ReportPreset.LAST_6, SpendingGroupBy.CATEGORY, true, {}, {}, {}, {}, initialItem = item)
    }

    private fun snap(
        name: String,
        dark: Boolean,
        item: Int,
    ) {
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark) {
                Box(Modifier.width(390.dp).height(780.dp)) { Reports(item) }
            }
        }
    }
}
