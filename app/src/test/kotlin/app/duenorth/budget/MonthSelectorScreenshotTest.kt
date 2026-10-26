package app.duenorth.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.GroupRow
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.design.MonthWindow
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/** The month selector on the budget and on a category's budget page, shut and open on its month list. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w390dp-h780dp-xxhdpi")
class MonthSelectorScreenshotTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun budgetLight() = budget("month_budget_light", dark = false, open = false)

    @Test
    fun budgetOpenLight() = budget("month_budget_open_light", dark = false, open = true)

    @Test
    fun budgetOpenDark() = budget("month_budget_open_dark", dark = true, open = true)

    @Test
    fun categoryLight() = category("month_category_light", dark = false, open = false)

    @Test
    fun categoryOpenLight() = category("month_category_open_light", dark = false, open = true)

    @Test
    fun categoryOpenDark() = category("month_category_open_dark", dark = true, open = true)

    private fun budget(
        name: String,
        dark: Boolean,
        open: Boolean,
    ) {
        val shell = sample()
        compose.setContent {
            MetroTheme(darkTheme = dark) {
                Column(Modifier.fillMaxSize()) {
                    HomePivot(
                        shell = shell,
                        loading = false,
                        openMonth = shell.month,
                        monthWindow = MonthWindow.around(shell.month),
                        initialSection = HomeSections.headers.indexOf(HomeSections.BUDGET),
                        onGesture = {},
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        shoot(name, open)
    }

    private fun category(
        name: String,
        dark: Boolean,
        open: Boolean,
    ) {
        val shell = sample()
        compose.setContent {
            MetroTheme(darkTheme = dark) {
                EditorWithMonth(
                    openMonth = shell.month,
                    monthWindow = MonthWindow.around(shell.month),
                    onSelectMonth = {},
                    title = "Groceries",
                ) { inner ->
                    CategoryBudgetScreen(
                        shell = shell,
                        categoryId = "c-groceries",
                        error = null,
                        onSave = {},
                        onMove = {},
                        onToggleCarryover = {},
                        modifier = inner,
                        showName = false,
                    )
                }
            }
        }
        shoot(name, open)
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun shoot(
        name: String,
        open: Boolean,
    ) {
        if (open) compose.onNodeWithTag("month-label").performClick()
        compose.waitForIdle()
        captureScreenRoboImage("src/test/snapshots/$name.png")
    }

    private fun sample(): MonthShell =
        MonthShell(
            budgetId = "home",
            budgetName = "Home",
            mode = BudgetMode.ENVELOPE,
            currency = Currencies.byCode("USD")!!,
            month = YearMonth.of(2026, 10),
            headerLabel = "to budget",
            headerMinor = 42_000,
            groups =
                listOf(
                    GroupRow(
                        "g-bills",
                        "Bills",
                        124_000,
                        listOf(
                            CategoryRow("c-rent", "Rent", 110_000, 0, 110_000, false),
                            CategoryRow("c-power", "Power", 14_000, 0, 14_000, false),
                        ),
                    ),
                    GroupRow(
                        "g-food",
                        "Food",
                        -3_200,
                        listOf(
                            CategoryRow("c-groceries", "Groceries", 20_000, 28_000, -8_000, false),
                            CategoryRow("c-dining", "Dining", 8_000, 3_200, 4_800, false),
                        ),
                    ),
                ),
            accounts = emptyList(),
            inbox = emptyList(),
        )
}
