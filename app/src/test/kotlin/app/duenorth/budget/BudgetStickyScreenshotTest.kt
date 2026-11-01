package app.duenorth.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performSemanticsAction
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.GroupRow
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.design.MonthWindow
import app.duenorth.budget.design.theme.MetroStyle
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/** The budget scrolled: the budgeted / spent / available headings hold at the top of the list. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w390dp-h780dp-xxhdpi")
class BudgetStickyScreenshotTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun stuckLight() = snap(dark = false, name = "budget_sticky_headings_light")

    @Test
    fun stuckDark() = snap(dark = true, name = "budget_sticky_headings_dark")

    private fun snap(
        dark: Boolean,
        name: String,
    ) {
        val shell =
            MonthShell(
                budgetId = "home",
                budgetName = "Home",
                mode = BudgetMode.ENVELOPE,
                currency = Currencies.byCode("USD")!!,
                month = YearMonth.of(2026, 10),
                headerLabel = "to budget",
                headerMinor = 0,
                groups = longGroups(),
                accounts = emptyList(),
                inbox = emptyList(),
            )
        compose.setContent {
            MetroTheme(darkTheme = dark, style = MetroStyle.Metro) {
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
        compose.waitForIdle()
        val px = SCROLL_DP * compose.density.density
        compose
            .onAllNodes(hasScrollAction() and hasAnyDescendant(hasTestTag("category-budgeted-g0-c0")))
            .onLast()
            .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, px) }
        compose.waitForIdle()
        captureScreenRoboImage("src/test/snapshots/$name.png")
    }
}

private const val SCROLL_DP = 260f

private fun longGroups(): List<GroupRow> {
    val names =
        listOf(
            "Bills" to listOf("Rent", "Electric", "Water", "Internet", "Phone"),
            "Everyday" to listOf("Groceries", "Dining", "Coffee", "Household", "Fuel"),
            "Fun" to listOf("Movies", "Books", "Games", "Travel"),
        )
    return names.mapIndexed { g, (group, cats) ->
        val rows =
            cats.mapIndexed { i, cat ->
                val budgeted = 5_000L + 1_250L * (g * 5 + i)
                val spent = 3_100L + 900L * i
                CategoryRow("g$g-c$i", cat, budgeted, spent, budgeted - spent, false)
            }
        GroupRow("g$g", group, rows.sumOf { it.availableMinor }, rows)
    }
}
