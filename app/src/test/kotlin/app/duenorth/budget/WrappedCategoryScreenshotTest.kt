package app.duenorth.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.GroupRow
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.design.MonthWindow
import app.duenorth.budget.design.theme.MetroStyle
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/** A category name that wraps keeps the same air above and below it as a one-line name, in every style. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w390dp-h780dp-xxhdpi")
class WrappedCategoryScreenshotTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun metroLight() = snap(MetroStyle.Metro, dark = false)

    @Test
    fun metroDark() = snap(MetroStyle.Metro, dark = true)

    @Test
    fun tileReliefLight() = snap(MetroStyle.TileRelief, dark = false)

    @Test
    fun tileReliefDark() = snap(MetroStyle.TileRelief, dark = true)

    @Test
    fun micaLight() = snap(MetroStyle.Mica, dark = false)

    @Test
    fun micaDark() = snap(MetroStyle.Mica, dark = true)

    @Test
    fun mobile10Light() = snap(MetroStyle.Mobile10, dark = false)

    @Test
    fun mobile10Dark() = snap(MetroStyle.Mobile10, dark = true)

    private fun snap(
        style: MetroStyle,
        dark: Boolean,
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
                groups = groups(),
                accounts = emptyList(),
                inbox = emptyList(),
            )
        compose.setContent {
            MetroTheme(darkTheme = dark, style = style) {
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
        val name = "budget_wrapped_category_${style.id}_${if (dark) "dark" else "light"}"
        captureScreenRoboImage("src/test/snapshots/$name.png")
        val (oneAbove, oneBelow) = air(ONE_LINE)
        val (wrapAbove, wrapBelow) = air(WRAPPED)
        val wrapHeight =
            compose
                .onNodeWithTag(
                    "category-name-$WRAPPED",
                    useUnmergedTree = true,
                ).getBoundsInRoot()
                .let { it.bottom - it.top }
        val oneHeight =
            compose
                .onNodeWithTag(
                    "category-name-$ONE_LINE",
                    useUnmergedTree = true,
                ).getBoundsInRoot()
                .let { it.bottom - it.top }
        assertTrue("$style: the long name should wrap", wrapHeight > oneHeight * 1.5f)
        assertEquals("$style: one-line name centered", oneAbove.value, oneBelow.value, 0.5f)
        assertEquals("$style: wrapped name keeps the air above", oneAbove.value, wrapAbove.value, 0.5f)
        assertEquals("$style: wrapped name keeps the air below", oneBelow.value, wrapBelow.value, 0.5f)
    }

    /** Space between the category row's top and the name, and between the name and the row's text area bottom. */
    private fun air(id: String): Pair<Dp, Dp> {
        val row = compose.onNodeWithTag("category-line-$id", useUnmergedTree = true).getBoundsInRoot()
        val name = compose.onNodeWithTag("category-name-$id", useUnmergedTree = true).getBoundsInRoot()
        return (name.top - row.top) to (row.bottom - name.bottom)
    }
}

private const val ONE_LINE = "g0-c0"
private const val WRAPPED = "g0-c1"

private fun groups(): List<GroupRow> {
    val names =
        listOf(
            "Bills" to listOf("Rent", "Car insurance and registration renewal", "Electric", "Internet"),
            "Everyday" to listOf("Groceries", "Kids' school lunches and field trips", "Coffee"),
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
