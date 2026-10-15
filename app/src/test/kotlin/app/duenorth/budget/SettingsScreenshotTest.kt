package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.BudgetSummary
import app.duenorth.budget.core.FormatPrefs
import app.duenorth.budget.core.SavedInstance
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class SettingsScreenshotTest {
    @Test
    fun settingsLight() = snap("settings_light", SettingsPage.BUDGET, dark = false)

    @Test
    fun settingsDark() = snap("settings_dark", SettingsPage.BUDGET, dark = true)

    @Test
    fun serverLight() = snap("settings_server_light", SettingsPage.SERVER, dark = false)

    @Test
    fun serverDark() = snap("settings_server_dark", SettingsPage.SERVER, dark = true)

    @Test
    fun formatLight() = snap("settings_format_light", SettingsPage.FORMAT, dark = false)

    @Test
    fun formatDark() = snap("settings_format_dark", SettingsPage.FORMAT, dark = true)

    @Test
    fun appearanceLight() = snap("settings_appearance_light", SettingsPage.APPEARANCE, dark = false)

    @Test
    fun appearanceDark() = snap("settings_appearance_dark", SettingsPage.APPEARANCE, dark = true)

    @Test
    fun advancedLight() = snap("settings_advanced_light", SettingsPage.ADVANCED, dark = false)

    @Test
    fun advancedDark() = snap("settings_advanced_dark", SettingsPage.ADVANCED, dark = true)

    @Test
    fun noBudgetLight() = snap("settings_no_budget_light", SettingsPage.BUDGET, dark = false, budgetOpen = false)

    @Test
    fun noBudgetDark() = snap("settings_no_budget_dark", SettingsPage.BUDGET, dark = true, budgetOpen = false)

    @Test
    fun encryptionChangeLight() = page("settings_encryption_light", dark = false) { EncryptionScreen(EncryptionMode.CHANGE) }

    @Test
    fun encryptionChangeDark() = page("settings_encryption_dark", dark = true) { EncryptionScreen(EncryptionMode.CHANGE) }

    @Test
    fun encryptionSetLight() = page("settings_encryption_set_light", dark = false) { EncryptionScreen(EncryptionMode.SET) }

    @Test
    fun encryptionRemoveDark() = page("settings_encryption_remove_dark", dark = true) { EncryptionScreen(EncryptionMode.REMOVE) }

    @Test
    fun removeServerLight() = page("settings_remove_server_light", dark = false) { RemoveScreen() }

    @Test
    fun removeServerDark() = page("settings_remove_server_dark", dark = true) { RemoveScreen() }

    private val home = SavedInstance("i1", "http://192.168.1.20:5006", "home server")
    private val office = SavedInstance("i2", "https://budget.example.com", "office")

    private fun snap(
        name: String,
        settingsPage: SettingsPage,
        dark: Boolean,
        budgetOpen: Boolean = true,
    ) = page(name, dark) {
        SettingsScreen(
            page = settingsPage,
            instances = listOf(home, office),
            activeInstance = home,
            instanceReachable = true,
            signedIn = true,
            budgets =
                listOf(
                    BudgetSummary("home", "Home", "USD"),
                    BudgetSummary("trip", "Trip", "USD"),
                ),
            openBudgetId = if (budgetOpen) "home" else null,
            themeMode = if (dark) "dark" else "light",
            accentId = "magenta",
            formatPrefs = FormatPrefs(),
            cloudFileId = "cloud-1",
            encrypted = false,
            onPage = {},
            onMakeActive = {},
            onRemoveInstance = {},
            onOpenBudget = {},
            onTheme = {},
            onAccent = {},
            onPayees = {},
            onDateFormat = {},
            onNumberFormat = {},
            onFirstDay = {},
            onHideFraction = {},
            onExperimental = {},
            onExport = {},
            onEncryption = {},
            onRequestAdvanced = {},
        )
    }

    @androidx.compose.runtime.Composable
    private fun EncryptionScreen(mode: EncryptionMode) =
        EncryptionPasswordScreen(mode = mode, error = null, onSet = {}, onChange = { _, _ -> }, onRemove = {})

    @androidx.compose.runtime.Composable
    private fun RemoveScreen() = ConfirmRemoveInstanceScreen(name = "home server", onRemove = {}, onKeep = {})

    private fun page(
        name: String,
        dark: Boolean,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark) {
                Box(Modifier.width(390.dp).height(780.dp)) { content() }
            }
        }
    }
}
