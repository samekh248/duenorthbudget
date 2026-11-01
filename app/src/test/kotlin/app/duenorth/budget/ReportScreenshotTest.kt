package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.ReportPreset
import app.duenorth.budget.core.ReportsCopy
import app.duenorth.budget.core.SpendingFilter
import app.duenorth.budget.core.SpendingGroupBy
import app.duenorth.budget.core.monthLabel
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
    fun spendingLight() = snap("report_spending_light", dark = false, item = ReportItems.SPENDING)

    @Test
    fun spendingDark() = snap("report_spending_dark", dark = true, item = ReportItems.SPENDING)

    @Test
    fun customRangeLight() = capture("report_custom_range_light", dark = false) { Reports(ReportItems.CASH_FLOW, ReportPreset.CUSTOM) }

    @Test
    fun customRangeDark() = capture("report_custom_range_dark", dark = true) { Reports(ReportItems.CASH_FLOW, ReportPreset.CUSTOM) }

    @Test
    fun netWorthLight() = capture("report_networth_light", dark = false) { NetWorthGraph() }

    @Test
    fun netWorthDark() = capture("report_networth_dark", dark = true) { NetWorthGraph() }

    @Test
    fun netWorthOnBudgetOnlyLight() = capture("report_networth_on_budget_light", dark = false) { NetWorthGraph(include = false) }

    @Test
    fun netWorthOnBudgetOnlyDark() = capture("report_networth_on_budget_dark", dark = true) { NetWorthGraph(include = false) }

    @Test
    fun drillBreakdownLight() = capture("report_drill_breakdown_light", dark = false) { Drill(ReportSamples.groupPath) }

    @Test
    fun drillBreakdownDark() = capture("report_drill_breakdown_dark", dark = true) { Drill(ReportSamples.groupPath) }

    @Test
    fun drillTransactionsLight() = capture("report_drill_transactions_light", dark = false) { Drill(ReportSamples.categoryPath) }

    @Test
    fun drillTransactionsDark() = capture("report_drill_transactions_dark", dark = true) { Drill(ReportSamples.categoryPath) }

    @Test
    fun cashFlowMonthLight() = capture("report_cashflow_month_light", dark = false) { Month() }

    @Test
    fun cashFlowMonthDark() = capture("report_cashflow_month_dark", dark = true) { Month() }

    @Test
    fun cashFlowIncomeDrillLight() = capture("report_cashflow_income_drill_light", dark = false) { IncomeDrill() }

    @Test
    fun cashFlowIncomeDrillDark() = capture("report_cashflow_income_drill_dark", dark = true) { IncomeDrill() }

    @Composable
    private fun Month() {
        SectionTitled(ReportsCopy.TITLE.uppercase()) {
            CashFlowMonthScreen(ReportSamples.month, ReportSamples.monthPage(), {}, {})
        }
    }

    @Composable
    private fun IncomeDrill() {
        SectionTitled(ReportsCopy.TITLE.uppercase()) {
            SpendingDrillScreen(
                ReportSamples.incomePath,
                ReportSamples.incomeDrill(),
                { _, _ -> },
                {},
                root = listOf(ReportsCopy.CASH_FLOW, monthLabel(ReportSamples.month)),
            )
        }
    }

    @Composable
    private fun Drill(path: List<SpendingFilter>) {
        val drill = if (path == ReportSamples.groupPath) ReportSamples.groupDrill() else ReportSamples.categoryDrill()
        SectionTitled(ReportsCopy.TITLE.uppercase()) {
            SpendingDrillScreen(path, drill, { _, _ -> }, {})
        }
    }

    @Composable
    private fun NetWorthGraph(include: Boolean = true) {
        SectionTitled(ReportsCopy.TITLE.uppercase()) {
            NetWorthGraphScreen(NetWorthUi(history = ReportSamples.history()), include, {}, {})
        }
    }

    @Composable
    private fun Reports(
        item: Int,
        preset: ReportPreset = ReportPreset.LAST_6,
    ) {
        ReportsScreen(ReportSamples.page(), false, preset, SpendingGroupBy.CATEGORY, {}, {}, {}, initialItem = item)
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
