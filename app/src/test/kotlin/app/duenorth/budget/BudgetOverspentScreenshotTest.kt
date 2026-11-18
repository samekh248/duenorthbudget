package app.duenorth.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.FormatPrefs
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

/** Overspent categories flagged: a red row with how far over, a count under each group, and the month's total. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w390dp-h860dp-xxhdpi")
class BudgetOverspentScreenshotTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun overspentLight() = snap(dark = false, name = "budget_overspent_light")

    @Test
    fun overspentDark() = snap(dark = true, name = "budget_overspent_dark")

    // With goals on, the goal bar sits inside the red panel, lined up with the text and the other rows' bars.
    @Test
    fun overspentGoalsLight() = snap(dark = false, name = "budget_overspent_goals_light", goals = true)

    @Test
    fun overspentGoalsDark() = snap(dark = true, name = "budget_overspent_goals_dark", goals = true)

    private fun snap(
        dark: Boolean,
        name: String,
        goals: Boolean = false,
    ) {
        val shell =
            MonthShell(
                budgetId = "home",
                budgetName = "Home",
                mode = BudgetMode.ENVELOPE,
                currency = Currencies.byCode("USD")!!,
                month = YearMonth.of(2026, 10),
                headerLabel = "to budget",
                headerMinor = 12_000,
                groups = overspentGroups(goals),
                accounts = emptyList(),
                inbox = emptyList(),
            )
        compose.setContent {
            CompositionLocalProvider(LocalFormatPrefs provides FormatPrefs(experimental = goals, goalTemplates = goals)) {
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
        }
        compose.waitForIdle()
        captureScreenRoboImage("src/test/snapshots/$name.png")
    }
}

private fun overspentGroups(goals: Boolean): List<GroupRow> {
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
                val spent = 3_100L + 900L * i + (OVER[cat] ?: 0L)
                // Every other category's goal is half again what was budgeted, so its bar shows two thirds funded.
                val goal =
                    when {
                        !goals -> null
                        i % 2 == 0 -> budgeted
                        else -> budgeted * 3 / 2
                    }
                CategoryRow("g$g-c$i", cat, budgeted, spent, budgeted - spent, false, goalMinor = goal)
            }
        GroupRow("g$g", group, rows.sumOf { it.availableMinor }, rows)
    }
}

/** Extra spending that puts three categories over: one in Bills, two in Everyday. */
private val OVER = mapOf("Electric" to 8_000L, "Dining" to 17_775L, "Fuel" to 18_150L)
