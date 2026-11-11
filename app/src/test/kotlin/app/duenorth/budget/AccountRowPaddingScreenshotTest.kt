package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.duenorth.budget.ParityTestSupport.USD
import app.duenorth.budget.core.AccountRow
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.design.theme.Metro
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
 * Screenshots for [AccountRowPaddingTest]: a wrapped account name between one-line names, in every style, light
 * and dark, and at the largest font size. An account whose name wraps to a second line keeps the same air above and below its name as one that fits on a
 * line, in every style and at the largest font size, rather than filling the touch target edge to edge.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class AccountRowPaddingScreenshotTest {
    private val rows =
        listOf(
            AccountRow("short", "Checking", false, false, 248_000),
            AccountRow("wrapped", "Joint household checking at the credit union", false, false, 1_250_000),
            AccountRow("savings", "Savings", false, false, 100_000),
            AccountRow("card", "Rewards Visa Signature travel card ending 4412", false, false, -2_500),
        )

    private val shell =
        MonthShell(
            "b",
            "Home",
            BudgetMode.ENVELOPE,
            USD,
            YearMonth.of(2026, 10),
            "to budget",
            0,
            groups = emptyList(),
            accounts = rows,
            inbox = emptyList(),
        )

    @Test
    fun light() = snap("accounts_wrapped_rows_light", dark = false)

    @Test
    fun dark() = snap("accounts_wrapped_rows_dark", dark = true)

    @Test
    fun font2xLight() = snap("font2x_accounts_wrapped_rows_light", dark = false, fontScale = 2f)

    @Test
    fun font2xDark() = snap("font2x_accounts_wrapped_rows_dark", dark = true, fontScale = 2f)

    @Test
    fun styles() {
        MetroStyle.entries.filter { it != MetroStyle.Default }.forEach { style ->
            listOf(false, true).forEach { dark ->
                snap("style_${style.id}_accounts_wrapped_rows_${if (dark) "dark" else "light"}", dark, style = style)
            }
        }
    }

    @Composable
    private fun Section() {
        Box(Modifier.width(390.dp).height(780.dp).background(Metro.colors.background)) {
            AccountsSection(rows, shell, onListGesture = {})
        }
    }

    private fun snap(
        name: String,
        dark: Boolean,
        fontScale: Float = 1f,
        style: MetroStyle = MetroStyle.Default,
    ) {
        captureRoboImage("src/test/snapshots/$name.png") {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MetroTheme(darkTheme = dark, style = style) { Section() }
            }
        }
    }
}
