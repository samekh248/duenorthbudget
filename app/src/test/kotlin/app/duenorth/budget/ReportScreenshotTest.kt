package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.NetWorthPage
import app.duenorth.budget.core.ReportPreset
import app.duenorth.budget.core.ReportsCopy
import app.duenorth.budget.core.SpendingGroupBy
import app.duenorth.budget.core.UpcomingScheduleRow
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
    fun cashFlowLight() = snap("report_cashflow_light", dark = false, item = ReportItems.CASH_FLOW)

    @Test
    fun cashFlowDark() = snap("report_cashflow_dark", dark = true, item = ReportItems.CASH_FLOW)

    @Test
    fun netWorthLight() = snap("report_networth_light", dark = false, item = ReportItems.NET_WORTH)

    @Test
    fun netWorthDark() = snap("report_networth_dark", dark = true, item = ReportItems.NET_WORTH)

    @Test
    fun spendingLight() = snap("report_spending_light", dark = false, item = ReportItems.SPENDING)

    @Test
    fun spendingDark() = snap("report_spending_dark", dark = true, item = ReportItems.SPENDING)

    @Test
    fun netWorthTodayLight() = capture("reports_networth_today_light", dark = false) { NetWorthToday() }

    @Test
    fun netWorthTodayDark() = capture("reports_networth_today_dark", dark = true) { NetWorthToday() }

    // Home `•••` → due: what the due pivot item used to list, now its own view.
    @Test
    fun dueLight() = capture("due_light", dark = false) { Due() }

    @Test
    fun dueDark() = capture("due_dark", dark = true) { Due() }

    @Composable
    private fun Due() {
        val usd = Currencies.byCode("USD")!!
        val rows =
            listOf(
                UpcomingScheduleRow("s1", "rent", "a1", "Checking", "Landlord", -145_000, "c1", "Rent", 20261101, false, "upcoming"),
                UpcomingScheduleRow("s2", "phone", "a2", "Credit card", "Phone Co", -6_500, "c2", "Phone", 20261016, true, "due"),
                UpcomingScheduleRow("s3", "pay", "a1", "Checking", "Employer", 310_000, null, "Income", 20261030, false, "upcoming"),
            )
        DueScreen(rows, usd, {}, {}, {}, {})
    }

    private val today =
        NetWorthPage(
            currency = Currencies.byCode("USD")!!,
            onBudgetMinor = 1_284_250,
            offBudgetMinor = 21_830_000,
            includeOffBudget = true,
        )

    @Composable
    private fun NetWorthToday() {
        SectionTitled(ReportsCopy.TITLE.uppercase()) {
            NetWorthSection(today, loading = false, onIncludeOffBudget = {})
        }
    }

    @Composable
    private fun Reports(item: Int) {
        ReportsScreen(ReportSamples.page(), false, ReportPreset.LAST_6, SpendingGroupBy.CATEGORY, true, {}, {}, {}, {}, initialItem = item)
    }

    private fun snap(
        name: String,
        dark: Boolean,
        item: Int,
    ) = capture(name, dark) { Reports(item) }

    private fun capture(
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
