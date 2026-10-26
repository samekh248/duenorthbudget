package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.ActualMonthMath
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.GroupRow
import app.duenorth.budget.core.IncomeCategoryRow
import app.duenorth.budget.core.IncomeGroupRow
import app.duenorth.budget.core.MonthFillCopy
import app.duenorth.budget.core.MonthFillKind
import app.duenorth.budget.core.MonthShell
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
    fun breakdownLight() = snap("breakdown_light", dark = false) { SectionTitled("BUDGET") { BreakdownScreen(october, parts, usd) } }

    @Test
    fun breakdownDark() = snap("breakdown_dark", dark = true) { SectionTitled("BUDGET") { BreakdownScreen(october, parts, usd) } }

    private val withPlans = parts.copy(ahead = ActualMonthMath.FuturePlans(15_200, 202612))

    @Test
    fun breakdownPlansLight() =
        snap(
            "breakdown_plans_light",
            dark = false,
        ) { SectionTitled("BUDGET") { BreakdownScreen(october, withPlans, usd, showAhead = true) } }

    @Test
    fun breakdownPlansDark() =
        snap("breakdown_plans_dark", dark = true) { SectionTitled("BUDGET") { BreakdownScreen(october, withPlans, usd, showAhead = true) } }

    @Test
    fun fillConfirmLight() = snap("fill_confirm_light", dark = false) { FillConfirm() }

    @Test
    fun fillConfirmDark() = snap("fill_confirm_dark", dark = true) { FillConfirm() }

    @Test
    fun incomeOnHomeLight() = snap("income_home_light", dark = false) { BudgetSection(shell, onListGesture = {}) }

    @Test
    fun incomeCategoryDark() =
        snap("income_category_dark", dark = true) {
            IncomeCategoryScreen(shell, shell.incomeGroups[0].categories[0], error = null, onSave = {}, onAutoHold = {})
        }

    private val shell =
        MonthShell(
            budgetId = "b",
            budgetName = "Home",
            mode = BudgetMode.ENVELOPE,
            currency = usd,
            month = october,
            headerLabel = "to budget",
            headerMinor = 55_200,
            groups =
                listOf(
                    GroupRow("g-liv", "Living", 70_000, listOf(CategoryRow("c-food", "Food", 220_000, -150_000, 70_000, false))),
                    GroupRow("g-bills", "Bills", 25_000, listOf(CategoryRow("c-rent", "Rent", 1_200_000, -1_200_000, 0, false))),
                ),
            accounts = emptyList(),
            inbox = emptyList(),
            incomeGroups =
                listOf(
                    IncomeGroupRow(
                        "g-inc",
                        "Income",
                        0,
                        455_000,
                        listOf(
                            IncomeCategoryRow("c-sal", "Salary", 0, 420_000, true),
                            IncomeCategoryRow("c-other", "Other income", 0, 35_000, false),
                        ),
                    ),
                ),
        )

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
