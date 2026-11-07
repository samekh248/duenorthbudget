package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.GroupRow
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.design.MonthWindow
import app.duenorth.budget.design.theme.MetroStyle
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/**
 * The budget item right after a month is picked whose figures have not loaded yet: the month control already
 * names November while the shell still holds October.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class BudgetMonthLoadingScreenshotTest {
    @Test
    fun loadingLight() = snap("budget_month_loading_light", dark = false)

    @Test
    fun loadingDark() = snap("budget_month_loading_dark", dark = true)

    @Test
    fun loadedLight() = snap("budget_month_loaded_light", dark = false, open = NOVEMBER, shellMonth = NOVEMBER)

    @Test
    fun loadedDark() = snap("budget_month_loaded_dark", dark = true, open = NOVEMBER, shellMonth = NOVEMBER)

    // The relief styles put to budget on an accent tile; its placeholder sits on the tile.
    @Test
    fun styleLoading() =
        MetroStyle.entries.filter { it != MetroStyle.Metro }.forEach { style ->
            for (dark in listOf(false, true)) {
                snap("style_${style.id}_month_loading_${if (dark) "dark" else "light"}", dark, style = style)
            }
        }

    private fun snap(
        name: String,
        dark: Boolean,
        open: YearMonth = NOVEMBER,
        style: MetroStyle = MetroStyle.Metro,
        shellMonth: YearMonth = OCTOBER,
    ) {
        val shell = sample().copy(month = shellMonth)
        captureRoboImage("src/test/snapshots/$name.png") {
            // An endless pulse never lets the frame settle, so hold it still for the capture.
            CompositionLocalProvider(LocalFigurePulseAnimates provides false) {
                MetroTheme(darkTheme = dark, style = style) {
                    Box(Modifier.width(390.dp).height(780.dp)) {
                        HomePivot(
                            shell = shell,
                            loading = false,
                            openMonth = open,
                            monthWindow = MonthWindow.around(OCTOBER),
                            onGesture = {},
                            initialSection = HomeSections.headers.indexOf(HomeSections.BUDGET),
                        )
                    }
                }
            }
        }
    }

    private fun sample(): MonthShell =
        MonthShell(
            budgetId = "home",
            budgetName = "Home",
            mode = BudgetMode.ENVELOPE,
            currency = Currencies.byCode("USD")!!,
            month = OCTOBER,
            headerLabel = "to budget",
            headerMinor = 42_000,
            bufferedMinor = 25_000,
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
                        "g-every",
                        "Everyday",
                        18_640,
                        listOf(
                            CategoryRow("c-gas", "Gas", 12_000, 4_360, 7_640, false),
                            CategoryRow("c-phone", "Phone", 11_000, 0, 11_000, false),
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
                    GroupRow("g-save", "Savings", 80_000, listOf(CategoryRow("c-rainy", "Rainy day", 80_000, 0, 80_000, false))),
                ),
            accounts = emptyList(),
            inbox = emptyList(),
        )

    private companion object {
        val OCTOBER: YearMonth = YearMonth.of(2026, 10)
        val NOVEMBER: YearMonth = OCTOBER.plusMonths(1)
    }
}
