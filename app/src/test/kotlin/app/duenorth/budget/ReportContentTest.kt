package app.duenorth.budget

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.duenorth.budget.ParityTestSupport.await
import app.duenorth.budget.ParityTestSupport.household
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.NetWorthPage
import app.duenorth.budget.core.ReportPreset
import app.duenorth.budget.core.ReportRange
import app.duenorth.budget.core.ReportsCopy
import app.duenorth.budget.core.SpendingGroupBy
import app.duenorth.budget.design.theme.MetroTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth

/** Spec 010 T055: the reports pivot, its empty range, and the group-by switch. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class ReportContentTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun homeMenuOpensReports() {
        assertTrue("reports" in ScreenBar.specsFor(ShellRoute.Home, true, BudgetMode.ENVELOPE).map { it.id })
        assertTrue("reports" in ScreenBar.specsFor(ShellRoute.Home, true, BudgetMode.TRACKING).map { it.id })
    }

    @Test
    fun anEmptyRangeShowsZerosAndNoError() {
        compose.setContent {
            MetroTheme {
                ReportsScreen(
                    ReportSamples.empty(),
                    false,
                    ReportPreset.CUSTOM,
                    SpendingGroupBy.CATEGORY,
                    true,
                    {},
                    {},
                    {},
                    {},
                    initialItem = ReportItems.SPENDING,
                )
            }
        }
        compose.onNodeWithTag("report-spending-empty", useUnmergedTree = true).assertExists()
        compose.onNodeWithText(ReportsCopy.NOTHING).assertExists()
    }

    @Test
    fun groupBySwitchAsksForTheOtherGrouping() {
        val groupBy = mutableStateOf(SpendingGroupBy.CATEGORY)
        compose.setContent {
            MetroTheme {
                ReportsScreen(
                    ReportSamples.page(groupBy.value),
                    false,
                    ReportPreset.LAST_6,
                    groupBy.value,
                    true,
                    {},
                    {},
                    { groupBy.value = it },
                    {},
                    initialItem = ReportItems.SPENDING,
                )
            }
        }
        compose.onNodeWithTag("report-spending-row-none", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("report-spending-by-payee", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(SpendingGroupBy.PAYEE, groupBy.value)
        compose.onNodeWithTag("report-spending-row-landlord", useUnmergedTree = true).assertExists()
    }

    @Test
    fun loadingShowsPlaceholdersNotASpinner() {
        compose.setContent {
            MetroTheme { ReportsScreen(null, true, ReportPreset.LAST_6, SpendingGroupBy.CATEGORY, true, {}, {}, {}, {}) }
        }
        compose.onNodeWithTag("report-cashflow").assertExists()
        compose.onNodeWithTag("report-cashflow-totals").assertDoesNotExist()
    }

    @Test
    fun reportsThroughTheModel() {
        val (model, _) = household("reports")
        model.openReports()
        assertEquals(ShellRoute.Reports, model.state.value.route)
        model.openReport(ReportItems.CASH_FLOW)
        assertEquals(ShellRoute.ReportPivot(ReportItems.CASH_FLOW), model.state.value.route)
        await { model.state.value.reports != null && !model.state.value.reportsLoading }
        assertEquals(
            6,
            model.state.value.reports!!
                .cashFlow.months.size,
        )
        model.setReportPreset(ReportPreset.LAST_12)
        await {
            model.state.value.reports!!
                .cashFlow.months.size == 12
        }
        model.setReportGroupBy(SpendingGroupBy.PAYEE)
        await {
            model.state.value.reports!!
                .spending.groupBy == SpendingGroupBy.PAYEE
        }
        model.setReportRange(ReportRange(YearMonth.of(2000, 1), YearMonth.of(2000, 2)))
        await {
            model.state.value.reports!!
                .range.start == YearMonth.of(2000, 1)
        }
        val empty = model.state.value.reports!!
        assertEquals(0L, empty.cashFlow.incomeMinor)
        assertTrue(empty.spending.rows.isEmpty())
        model.back()
        assertEquals(ShellRoute.Reports, model.state.value.route)
        model.back()
        assertEquals(ShellRoute.Home, model.state.value.route)
    }

    @Test
    fun hubListsEveryReportAndOpensIt() {
        val opened = mutableStateOf<Int?>(null)
        val netWorth = mutableStateOf(false)
        val page =
            NetWorthPage(
                currency = Currencies.byCode("USD")!!,
                onBudgetMinor = 100_000,
                offBudgetMinor = 25_000,
                includeOffBudget = true,
            )
        compose.setContent {
            MetroTheme {
                ReportsHubScreen(
                    netWorth = page,
                    onNetWorth = { netWorth.value = true },
                    onReport = { opened.value = it },
                )
            }
        }
        compose.onNodeWithText("${MoneyFormat.format(125_000, page.currency)} today").assertExists()
        compose.onNodeWithTag("reports-hub-networth").performClick()
        assertTrue(netWorth.value)
        compose.onNodeWithTag("reports-hub-cashflow").performClick()
        assertEquals(ReportItems.CASH_FLOW, opened.value)
        compose.onNodeWithTag("reports-hub-networth-months").performClick()
        assertEquals(ReportItems.NET_WORTH, opened.value)
        compose.onNodeWithTag("reports-hub-spending").performClick()
        assertEquals(ReportItems.SPENDING, opened.value)
    }

    @Test
    fun netWorthOpensFromReportsNotHome() {
        assertTrue(ReportsCopy.NET_WORTH !in HomeSections.headers)
        val (model, _) = household("networth")
        model.openReports()
        model.openNetWorth()
        assertEquals(ShellRoute.NetWorth, model.state.value.route)
        model.back()
        assertEquals(ShellRoute.Reports, model.state.value.route)
    }
}
