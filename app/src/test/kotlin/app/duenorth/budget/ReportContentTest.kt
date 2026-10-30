package app.duenorth.budget

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
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
    fun reportsIsAHomePivotItemNotAMenuLink() {
        assertEquals(listOf("inbox", "budget", "accounts", "reports"), HomeSections.headers)
        for (mode in listOf(BudgetMode.ENVELOPE, BudgetMode.TRACKING)) {
            val ids = ScreenBar.specsFor(ShellRoute.Home, true, mode).map { it.id }
            assertTrue("reports" !in ids)
            assertTrue("due" !in ids)
        }
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
    fun aCustomRangeIsTwoMonthPickersThatApplyAtOnce() {
        val picked = mutableListOf<ReportRange>()
        compose.setContent {
            MetroTheme {
                ReportsScreen(
                    ReportSamples.page(),
                    false,
                    ReportPreset.CUSTOM,
                    SpendingGroupBy.CATEGORY,
                    {},
                    { picked += it },
                    {},
                )
            }
        }
        compose.onAllNodesWithTag("report-custom-apply").assertCountEquals(0)
        tapShown("report-custom-start-value")
        tapShown("report-custom-start-2026-08")
        // An end before the start moves the start with it.
        tapShown("report-custom-end-value")
        tapShown("report-custom-end-2026-05")
        assertEquals(
            listOf(
                ReportRange(YearMonth.of(2026, 8), YearMonth.of(2026, 9)),
                ReportRange(YearMonth.of(2026, 5), YearMonth.of(2026, 5)),
            ),
            picked,
        )
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
                    {},
                    {},
                    { groupBy.value = it },
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
            MetroTheme { ReportsScreen(null, true, ReportPreset.LAST_6, SpendingGroupBy.CATEGORY, {}, {}, {}) }
        }
        compose.onNodeWithTag("report-cashflow").assertExists()
        compose.onNodeWithTag("report-cashflow-totals").assertDoesNotExist()
    }

    @Test
    fun reportsThroughTheModel() {
        val (model, _) = household("reports")
        model.reports.open(ReportItems.CASH_FLOW)
        assertEquals(ShellRoute.ReportPivot(ReportItems.CASH_FLOW), model.state.value.route)
        await { model.state.value.reports.page != null && !model.state.value.reports.loading }
        assertEquals(
            6,
            model.state.value.reports.page!!
                .cashFlow.months.size,
        )
        model.reports.setPreset(ReportPreset.LAST_12)
        await {
            model.state.value.reports.page!!
                .cashFlow.months.size == 12
        }
        model.reports.setGroupBy(SpendingGroupBy.PAYEE)
        await {
            model.state.value.reports.page!!
                .spending.groupBy == SpendingGroupBy.PAYEE
        }
        model.reports.setRange(ReportRange(YearMonth.of(2000, 1), YearMonth.of(2000, 2)))
        await {
            model.state.value.reports.page!!
                .range.start == YearMonth.of(2000, 1)
        }
        val empty = model.state.value.reports.page!!
        assertEquals(0L, empty.cashFlow.incomeMinor)
        assertTrue(empty.spending.rows.isEmpty())
        model.back()
        assertEquals(ShellRoute.Home, model.state.value.route)
    }

    @Test
    fun hubListsEveryReportAndOpensIt() {
        val opened = mutableStateOf<Int?>(null)
        val netWorth = mutableStateOf(false)
        val review = mutableStateOf(false)
        val page =
            NetWorthPage(
                currency = Currencies.byCode("USD")!!,
                onBudgetMinor = 100_000,
                offBudgetMinor = 25_000,
                includeOffBudget = true,
            )
        compose.setContent {
            MetroTheme {
                ReportsHub(
                    netWorth = page,
                    month = YearMonth.of(2026, 10),
                    onNetWorth = { netWorth.value = true },
                    onMonthReview = { review.value = true },
                    onReport = { opened.value = it },
                )
            }
        }
        compose.onNodeWithText("${MoneyFormat.format(125_000, page.currency)} today").assertExists()
        compose.onNodeWithTag("reports-hub-networth").performClick()
        assertTrue(netWorth.value)
        compose.onNodeWithTag("reports-hub-review").performClick()
        assertTrue(review.value)
        compose.onNodeWithTag("reports-hub-cashflow").performClick()
        assertEquals(ReportItems.CASH_FLOW, opened.value)
        compose.onNodeWithTag("reports-hub-spending").performClick()
        assertEquals(ReportItems.SPENDING, opened.value)
    }

    @Test
    fun reportPagesGoBackToHome() {
        val (model, _) = household("networth")
        model.netWorthGraph.open()
        assertEquals(ShellRoute.NetWorth, model.state.value.route)
        model.back()
        assertEquals(ShellRoute.Home, model.state.value.route)
        model.reports.openMonthReview()
        assertEquals(ShellRoute.MonthReview, model.state.value.route)
        model.back()
        assertEquals(ShellRoute.Home, model.state.value.route)
    }

    /** The pivot lays out its neighbours too: tap the copy that is on screen. */
    private fun tapShown(tag: String) {
        val nodes = compose.onAllNodesWithTag(tag)
        val shown =
            nodes.fetchSemanticsNodes().indexOfFirst { node ->
                node.boundsInRoot.width > 0f && node.boundsInRoot.left in 0f..ON_SCREEN
            }
        nodes[shown].performClick()
        compose.waitForIdle()
    }
}

private const val ON_SCREEN = 1000f
