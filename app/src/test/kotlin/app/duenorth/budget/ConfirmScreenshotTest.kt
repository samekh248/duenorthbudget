package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.BankSyncFailure
import app.duenorth.budget.core.BankSyncFix
import app.duenorth.budget.core.MonthFillCopy
import app.duenorth.budget.core.MonthFillKind
import app.duenorth.budget.core.SimpleFinError
import app.duenorth.budget.design.components.MetroAppBar
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/** Questions as the app shows them: the page, and its two answers on the application bar. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class ConfirmScreenshotTest {
    @Test
    fun fillLight() = snap("confirm_fill_light", dark = false) { Fill() }

    @Test
    fun fillDark() = snap("confirm_fill_dark", dark = true) { Fill() }

    @Test
    fun replaceBudgetLight() = snap("confirm_replace_budget_light", dark = false) { ReplaceBudget() }

    @Test
    fun replaceBudgetDark() = snap("confirm_replace_budget_dark", dark = true) { ReplaceBudget() }

    @Test
    fun advancedLight() = snap("confirm_advanced_light", dark = false) { ConfirmAdvancedScreen(AdvancedConfirm.Rebuild, {}, {}) }

    @Test
    fun scheduleDuplicateDark() = snap("confirm_schedule_duplicate_dark", dark = true) { ScheduleDuplicateScreen({}, {}) }

    // Unlink on the failed bank sync's page asks first.
    @Test
    fun bankSyncUnlinkLight() = snap("confirm_bank_sync_unlink_light", dark = false) { Unlink() }

    @Test
    fun bankSyncUnlinkDark() = snap("confirm_bank_sync_unlink_dark", dark = true) { Unlink() }

    @Composable
    private fun Unlink() {
        BankSyncUnlinkScreen(BankSyncFailure("Savings", SimpleFinError.ACCOUNT_MISSING, "savings", BankSyncFix.RELINK), {}, {})
    }

    @Composable
    private fun Fill() {
        FillConfirmScreen(FillConfirm(MonthFillKind.COPY_LAST, MonthFillCopy.REPLACES), YearMonth.of(2026, 10), {}, {})
    }

    @Composable
    private fun ReplaceBudget() {
        ConfirmReplaceBudgetScreen("Household", listOf("Home"), {}, {})
    }

    private fun snap(
        name: String,
        dark: Boolean,
        content: @Composable () -> Unit,
    ) {
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark) {
                val host = rememberFormSaveHost()
                Column(Modifier.width(390.dp).height(780.dp)) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        CompositionLocalProvider(LocalFormSaveHost provides host) { content() }
                    }
                    MetroAppBar(host.bar.orEmpty(), true, {}, windowInsets = WindowInsets(bottom = 0.dp))
                }
            }
        }
    }
}
