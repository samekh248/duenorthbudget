package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.AccountDetail
import app.duenorth.budget.core.AccountsCopy
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryChoice
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.design.components.MetroAppBar
import app.duenorth.budget.design.components.ScreenAction
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Spec 010 T036: account close and force close in light and dark. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class AccountScreenshotTest {
    private val usd = Currencies.byCode("USD")!!
    private val accounts =
        listOf(
            AccountDetail("a1", "Checking", false, false, 248_000, 12, "", false),
            AccountDetail("a2", "Savings", false, false, 100_000, 3, "Six months of expenses", false),
            AccountDetail("a3", "Visa", false, false, -2_500, 6, "", false),
            AccountDetail("a4", "Brokerage", true, false, 500_000, 2, "", false),
            AccountDetail("a5", "Old card", false, true, 0, 9, "", false),
        )

    @Test
    fun closeLight() = snapWithBar("account_close_light", dark = false)

    @Test
    fun closeDark() = snapWithBar("account_close_dark", dark = true)

    @Test
    fun forceCloseDark() = snap("account_force_close_dark", dark = true) { ForceCloseConfirmScreen(AccountsCopy.FORCE_CONFIRM, {}, {}) }

    @Composable
    private fun Close() {
        CloseAccountScreen(
            account = accounts[1],
            others = accounts,
            categories = listOf(CategoryChoice("food", "Food", "Living", false), CategoryChoice("gen", "General", "Living", false)),
            currency = usd,
            error = null,
            onClose = { _, _ -> },
            onForceClose = {},
        )
    }

    /** The close page under its bar, where close account lives. */
    private fun snapWithBar(
        name: String,
        dark: Boolean,
    ) {
        val actions =
            ScreenBar.specsFor(ShellRoute.CloseAccount("a2"), true, BudgetMode.ENVELOPE).map {
                ScreenAction(it.id, it.glyph, it.label, primary = it.primary) {}
            }
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark) {
                Column(Modifier.width(390.dp)) {
                    CompositionLocalProvider(LocalFormSaveHost provides rememberFormSaveHost()) {
                        Box(Modifier.height(700.dp)) { Close() }
                    }
                    MetroAppBar(actions, true, {}, windowInsets = WindowInsets(bottom = 0.dp))
                }
            }
        }
    }

    private fun snap(
        name: String,
        dark: Boolean,
        content: @Composable () -> Unit,
    ) {
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark) {
                Box(Modifier.width(390.dp).height(780.dp)) { content() }
            }
        }
    }
}
