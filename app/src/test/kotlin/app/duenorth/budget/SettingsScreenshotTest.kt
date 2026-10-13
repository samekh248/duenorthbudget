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
    fun settingsLight() = snap("settings_light", dark = false)

    @Test
    fun settingsDark() = snap("settings_dark", dark = true)

    private fun snap(
        name: String,
        dark: Boolean,
    ) {
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark) {
                Box(Modifier.width(390.dp).height(780.dp)) {
                    SettingsScreen(
                        instances =
                            listOf(
                                SavedInstance("i1", "http://192.168.1.20:5006", "home server"),
                            ),
                        activeInstance = SavedInstance("i1", "http://192.168.1.20:5006", "home server"),
                        instanceReachable = true,
                        signedIn = true,
                        serverError = null,
                        budgets =
                            listOf(
                                BudgetSummary("home", "Home", "USD"),
                                BudgetSummary("trip", "Trip", "USD"),
                            ),
                        openBudgetId = "home",
                        themeMode = if (dark) "dark" else "light",
                        accentId = "magenta",
                        formatPrefs = FormatPrefs(),
                        cloudFileId = "cloud-1",
                        createError = null,
                        encryptionError = null,
                        openIdAvailable = true,
                        onConnect = { _, _ -> },
                        onBeginOpenId = {},
                        onMakeActive = {},
                        onRemoveInstance = {},
                        onSignOut = {},
                        onOpenBudget = {},
                        onCreateBudget = { _, _ -> },
                        onTheme = {},
                        onAccent = {},
                        onPayees = {},
                        onDateFormat = {},
                        onNumberFormat = {},
                        onFirstDay = {},
                        onHideFraction = {},
                        onExperimental = {},
                        onExport = {},
                        onSetEncryptionPassword = {},
                        onChangeEncryptionPassword = { _, _ -> },
                        onRemoveEncryptionPassword = {},
                        onRequestAdvanced = {},
                    )
                }
            }
        }
    }
}
