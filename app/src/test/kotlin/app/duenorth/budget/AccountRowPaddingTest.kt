package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.duenorth.budget.ParityTestSupport.USD
import app.duenorth.budget.core.AccountRow
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.design.theme.MetroDimens
import app.duenorth.budget.design.theme.MetroStyle
import app.duenorth.budget.design.theme.MetroTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/**
 * An account whose name wraps to a second line keeps the same air above and below its name as one that fits on a
 * line, in every style and at the largest font size, rather than filling the touch target edge to edge.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class AccountRowPaddingTest {
    @get:Rule
    val compose = createHostComposeRule()

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
    fun wrappedAndSingleLineRowsHaveTheSameAir() {
        val style = mutableStateOf(MetroStyle.Default)
        val scale = mutableStateOf(1f)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale.value)) {
                MetroTheme(darkTheme = false, style = style.value) { Section() }
            }
        }
        MetroStyle.entries.forEach { s ->
            listOf(1f, 2f).forEach { fontScale ->
                style.value = s
                scale.value = fontScale
                compose.waitForIdle()
                val label = "${s.id} at ${fontScale}x"
                val single = air("short")
                val wrapped = air("wrapped")
                // The long name really does take two lines, or the check proves nothing.
                assertTrue("$label: the long name should wrap", nameHeight("wrapped") > nameHeight("short") * 1.5f)
                assertEquals("$label: air above the name", single.first.value, wrapped.first.value, 0.5f)
                assertEquals("$label: air below the name", single.second.value, wrapped.second.value, 0.5f)
                val height = compose.onNodeWithTag("account-short", useUnmergedTree = true).getBoundsInRoot().let { it.bottom - it.top }
                assertTrue("$label: a one-line row is still a touch target", height >= MetroDimens.TouchTarget - 0.5.dp)
                assertTrue("$label: wrapped row keeps a full row's air", wrapped.first >= MetroDimens.Row - 0.5.dp)
                assertTrue("$label: wrapped row keeps a full row's air below", wrapped.second >= MetroDimens.Row - 0.5.dp)
            }
        }
    }

    /** Space between the row's top and the name's first line, and between the name's last line and the row's bottom. */
    private fun air(id: String): Pair<Dp, Dp> {
        val row = compose.onNodeWithTag("account-$id", useUnmergedTree = true).getBoundsInRoot()
        val name = compose.onNodeWithTag("account-name-$id", useUnmergedTree = true).getBoundsInRoot()
        // The tag sits inside the name's padding, so its bounds are the text's own.
        return (name.top - row.top) to (row.bottom - name.bottom)
    }

    private fun nameHeight(id: String): Dp {
        val name = compose.onNodeWithTag("account-name-$id", useUnmergedTree = true).getBoundsInRoot()
        return name.bottom - name.top
    }

    @Composable
    private fun Section() {
        Box(Modifier.width(390.dp).height(780.dp)) {
            AccountsSection(rows, shell, onListGesture = {})
        }
    }
}
