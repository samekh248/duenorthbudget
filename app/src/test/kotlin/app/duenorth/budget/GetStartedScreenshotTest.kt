package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.AccountRow
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.GetStarted
import app.duenorth.budget.core.GroupRow
import app.duenorth.budget.core.IncomeCategoryRow
import app.duenorth.budget.core.IncomeGroupRow
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.NewBudgetCopy
import app.duenorth.budget.design.MonthWindow
import app.duenorth.budget.design.components.AppBarButton
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.MetroAppBar
import app.duenorth.budget.design.components.ScreenAction
import app.duenorth.budget.design.components.asScreenActions
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class GetStartedScreenshotTest {
    // The samples are October 2026; pin "this month" to it so the month control doesn't change with the run date.
    @Before
    fun pinCalendarMonth() {
        CalendarMonth.pinned = YearMonth.of(2026, 10)
    }

    @After
    fun unpinCalendarMonth() {
        CalendarMonth.pinned = null
    }

    // A budget just made: the list stands where "nothing to budget" was.
    @Test
    fun freshLight() = home("get_started_fresh_light", dark = false, fresh())

    @Test
    fun freshDark() = home("get_started_fresh_dark", dark = true, fresh())

    // Accounts and categories done: those lines tick off, and the month fills in under the list.
    @Test
    fun progressLight() = home("get_started_progress_light", dark = false, halfway())

    @Test
    fun progressDark() = home("get_started_progress_dark", dark = true, halfway())

    @Test
    fun starterLight() = starter("starter_categories_light", dark = false)

    @Test
    fun starterDark() = starter("starter_categories_dark", dark = true)

    private fun home(
        name: String,
        dark: Boolean,
        shell: MonthShell,
    ) {
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark) {
                Column(Modifier.width(390.dp).height(780.dp)) {
                    Box(Modifier.weight(1f)) {
                        HomePivot(
                            shell = shell,
                            loading = false,
                            openMonth = shell.month,
                            monthWindow = MonthWindow.around(shell.month),
                            initialSection = ScreenBar.HOME_BUDGET,
                            onGesture = {},
                            modifier = Modifier.fillMaxSize(),
                            getStarted = GetStarted.lines(shell, emptyList()),
                        )
                    }
                    MetroAppBar(
                        listOf(
                            AppBarButton(AppGlyph.Add, "add", onClick = {}),
                            AppBarButton(AppGlyph.Edit, BudgetEditCopy.EDIT, onClick = {}),
                        ).asScreenActions(),
                        true,
                        {},
                        windowInsets = WindowInsets(bottom = 0.dp),
                    )
                }
            }
        }
    }

    private fun starter(
        name: String,
        dark: Boolean,
    ) {
        val actions =
            ScreenBar.specsFor(ShellRoute.StarterCategories, true, BudgetMode.ENVELOPE).filter { it.primary }.map {
                ScreenAction(it.id, it.glyph, it.label, primary = it.primary) {}
            }
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark) {
                Column(Modifier.width(390.dp).height(780.dp)) {
                    CompositionLocalProvider(LocalFormSaveHost provides rememberFormSaveHost()) {
                        Box(Modifier.weight(1f)) {
                            SectionTitled(HomeSections.BUDGET.uppercase()) { StarterCategoriesScreen(error = null, onAdd = {}) }
                        }
                    }
                    MetroAppBar(actions, true, {}, windowInsets = WindowInsets(bottom = 0.dp))
                }
            }
        }
    }

    private fun income(receivedMinor: Long) =
        listOf(
            IncomeGroupRow(
                "g-income",
                NewBudgetCopy.INCOME,
                0,
                receivedMinor,
                listOf(
                    IncomeCategoryRow("c-income", NewBudgetCopy.INCOME, 0, 0, autoHold = false),
                    IncomeCategoryRow("c-start", NewBudgetCopy.STARTING_BALANCES, 0, receivedMinor, autoHold = false),
                ),
            ),
        )

    private fun fresh() =
        MonthShell(
            budgetId = "home",
            budgetName = "Home",
            mode = BudgetMode.ENVELOPE,
            currency = Currencies.byCode("USD")!!,
            month = YearMonth.of(2026, 10),
            headerLabel = "to budget",
            headerMinor = 0,
            groups = emptyList(),
            accounts = emptyList(),
            inbox = emptyList(),
            incomeGroups = income(0),
        )

    private fun halfway() =
        fresh().copy(
            headerMinor = 294_000,
            accounts =
                listOf(
                    AccountRow("checking", "Checking", offBudget = false, closed = false, balanceMinor = 214_000),
                    AccountRow("savings", "Savings", offBudget = false, closed = false, balanceMinor = 80_000),
                ),
            groups =
                listOf(
                    GroupRow(
                        "g-bills",
                        "Bills",
                        0,
                        listOf(
                            CategoryRow("c-rent", "Rent or mortgage", 0, 0, 0, false),
                            CategoryRow("c-electric", "Electric", 0, 0, 0, false),
                        ),
                    ),
                    GroupRow(
                        "g-every",
                        "Everyday",
                        0,
                        listOf(
                            CategoryRow("c-groceries", "Groceries", 0, 0, 0, false),
                            CategoryRow("c-gas", "Gas", 0, 0, 0, false),
                        ),
                    ),
                ),
            incomeGroups = income(294_000),
        )
}
