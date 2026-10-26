package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.BudgetSummary
import app.duenorth.budget.core.FormatPrefs
import app.duenorth.budget.core.SavedInstance
import app.duenorth.budget.design.components.ScreenAction
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

    // Format has no buttons and no menu, so the whole screen shows no app bar.
    @Test
    fun formatScreenLight() = screen("settings_format_screen_light", SettingsPage.FORMAT, dark = false)

    @Test
    fun formatScreenDark() = screen("settings_format_screen_dark", SettingsPage.FORMAT, dark = true)

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

    @Test
    fun experimentalLight() =
        snap("experimental_light", SettingsPage.EXPERIMENTAL, dark = false, prefs = FormatPrefs(experimental = true, goalTemplates = true))

    @Test
    fun experimentalDark() =
        snap("experimental_dark", SettingsPage.EXPERIMENTAL, dark = true, prefs = FormatPrefs(experimental = true, goalTemplates = true))

    @Test
    fun experimentalOffLight() = snap("experimental_off_light", SettingsPage.EXPERIMENTAL, dark = false)

    @Test
    fun experimentalOffDark() = snap("experimental_off_dark", SettingsPage.EXPERIMENTAL, dark = true)

    @Test
    fun experimentalOfferLight() = page("experimental_offer_light", dark = false) { ExperimentalOfferScreen("Home", {}, {}) }

    @Test
    fun experimentalOfferChosenLight() =
        page("experimental_offer_chosen_light", dark = false) { ExperimentalOfferScreen("Home", {}, {}, current = true) }

    @Test
    fun experimentalOfferDark() = page("experimental_offer_dark", dark = true) { ExperimentalOfferScreen("Home", {}, {}) }

    private val home = SavedInstance("i1", "http://192.168.1.20:5006", "home server")
    private val office = SavedInstance("i2", "https://budget.example.com", "office")

    private fun snap(
        name: String,
        settingsPage: SettingsPage,
        dark: Boolean,
        budgetOpen: Boolean = true,
        prefs: FormatPrefs = FormatPrefs(),
    ) = page(name, dark) { settingsScreen(settingsPage, dark, budgetOpen, prefs) }

    @androidx.compose.runtime.Composable
    private fun settingsScreen(
        settingsPage: SettingsPage,
        dark: Boolean,
        budgetOpen: Boolean = true,
        prefs: FormatPrefs = FormatPrefs(),
    ) {
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
            formatPrefs = prefs,
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

    private fun screen(
        name: String,
        settingsPage: SettingsPage,
        dark: Boolean,
    ) {
        val actions = ScreenBar.specsFor(ShellRoute.Settings, hasBudget = true, mode = null, settings = SettingsBar(page = settingsPage))
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark) {
                Box(Modifier.width(390.dp).height(780.dp)) {
                    ShellChrome(
                        actions.map { ScreenAction(it.id, it.glyph, it.label, it.primary) {} },
                        statusBar = WindowInsets(0),
                    ) { modifier -> Box(modifier) { settingsScreen(settingsPage, dark) } }
                }
            }
        }
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
