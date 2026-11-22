package app.duenorth.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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

/**
 * The budget's available column a step larger than the row's other figures, in Metro, Tonal and Windows 10 Mobile,
 * light and dark, and at the largest font size: the figures stay on their columns and a large balance still fits.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w390dp-h860dp-xxhdpi")
class CategoryAvailableScreenshotTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun metroLight() = snap(MetroStyle.Metro, dark = false)

    @Test
    fun metroDark() = snap(MetroStyle.Metro, dark = true)

    @Test
    fun tonalLight() = snap(MetroStyle.Tonal, dark = false)

    @Test
    fun tonalDark() = snap(MetroStyle.Tonal, dark = true)

    @Test
    fun mobile10Light() = snap(MetroStyle.Mobile10, dark = false)

    @Test
    fun mobile10Dark() = snap(MetroStyle.Mobile10, dark = true)

    @Test
    fun font2xMetroLight() = snap(MetroStyle.Metro, dark = false, fontScale = 2f)

    @Test
    fun font2xTonalDark() = snap(MetroStyle.Tonal, dark = true, fontScale = 2f)

    private fun snap(
        style: MetroStyle,
        dark: Boolean,
        fontScale: Float = 1f,
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
                groups = groups(),
                accounts = emptyList(),
                inbox = emptyList(),
            )
        val font = if (fontScale == 1f) "" else "font2x_"
        val name = "${font}style_${style.id}_budget_available_${if (dark) "dark" else "light"}"
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
                LocalFormatPrefs provides FormatPrefs(experimental = true, goalTemplates = true),
            ) {
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
        }
        compose.waitForIdle()
        captureScreenRoboImage("src/test/snapshots/$name.png")
    }
}

private fun groups(): List<GroupRow> {
    val bills =
        listOf(
            CategoryRow("rent", "Rent", 165_000, 165_000, 0, false, goalMinor = 165_000),
            CategoryRow("electric", "Electric", 9_000, 17_000, -8_000, false, goalMinor = 9_000),
            CategoryRow("car", "Car insurance savings", 12_500, 0, 1_187_500, false, goalMinor = 1_200_000),
        )
    val everyday =
        listOf(
            CategoryRow("groceries", "Groceries", 60_000, 41_275, 18_725, false, goalMinor = 60_000),
            CategoryRow("dining", "Dining out", 15_000, 9_840, 5_160, false, goalMinor = 20_000),
            CategoryRow("fuel", "Fuel", 18_000, 6_215, 11_785, false, goalMinor = 18_000),
        )
    return listOf(
        GroupRow("bills", "Bills", bills.sumOf { it.availableMinor }, bills),
        GroupRow("everyday", "Everyday", everyday.sumOf { it.availableMinor }, everyday),
    )
}
