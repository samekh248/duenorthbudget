package app.duenorth.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.AccountRow
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.GroupRow
import app.duenorth.budget.core.InboxRow
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.design.components.AppBarButton
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.MetroAppBar
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class ShellScreenshotTest {
    @Test
    fun homeLight() = snap("home_light", dark = false, shell = sample())

    @Test
    fun homeDark() = snap("home_dark", dark = true, shell = sample())

    @Test
    fun emptyLight() =
        snap(
            "empty_light",
            dark = false,
            shell = sample().copy(headerMinor = 0, groups = emptyList(), accounts = emptyList(), inbox = emptyList()),
        )

    @Test
    fun accountsLight() = snap("accounts_light", dark = false, shell = sample(), section = 1)

    @Test
    fun inboxDark() = snap("inbox_dark", dark = true, shell = sample(), section = 2)

    private fun snap(
        name: String,
        dark: Boolean,
        shell: MonthShell,
        section: Int = 0,
    ) {
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark) {
                Column(Modifier.width(390.dp).height(780.dp)) {
                    HomePanorama(
                        shell = shell,
                        loading = false,
                        initialSection = section,
                        onGesture = {},
                        modifier = Modifier.weight(1f),
                    )
                    MetroAppBar(
                        buttons =
                            listOf(
                                AppBarButton(AppGlyph.Budgets, "budgets") {},
                                AppBarButton(AppGlyph.Appearance, "appearance") {},
                            ),
                        expanded = false,
                        onExpandedChange = {},
                    )
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
            month = YearMonth.of(2026, 10),
            headerLabel = "to budget",
            headerMinor = 180_000,
            groups =
                listOf(
                    GroupRow("g-liv", "Living", 50_000),
                    GroupRow("g-bills", "Bills", 12_000),
                ),
            accounts =
                listOf(
                    AccountRow("checking", "Checking", offBudget = false, closed = false, balanceMinor = 226_000),
                    AccountRow("savings", "Savings", offBudget = true, closed = false, balanceMinor = 10_000),
                ),
            inbox = listOf(InboxRow("cafe", "Cafe", -2_500), InboxRow("bus", "Bus", -1_000)),
        )
}
