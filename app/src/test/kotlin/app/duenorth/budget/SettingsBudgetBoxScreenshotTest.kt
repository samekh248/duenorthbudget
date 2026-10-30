package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.BudgetSummary
import app.duenorth.budget.core.FormatPrefs
import app.duenorth.budget.core.RemoteFile
import app.duenorth.budget.core.SavedInstance
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings → budget with the open-budget box unfolded: the phone's budgets, then the server's. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class SettingsBudgetBoxScreenshotTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun openLight() = snap("settings_budget_box_light", dark = false)

    @Test
    fun openDark() = snap("settings_budget_box_dark", dark = true)

    private fun snap(
        name: String,
        dark: Boolean,
    ) {
        val home = SavedInstance("i1", "http://192.168.1.20:5006", "home server")
        compose.setContent {
            MetroTheme(darkTheme = dark, animationsEnabled = false) {
                Box(Modifier.width(390.dp).height(780.dp).background(Metro.colors.background)) {
                    SettingsScreen(
                        page = SettingsPage.BUDGET,
                        instances = listOf(home),
                        activeInstance = home,
                        instanceReachable = true,
                        signedIn = true,
                        budgets = listOf(BudgetSummary("home", "Home", "USD"), BudgetSummary("trip", "Trip", "USD")),
                        openBudgetId = "home",
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
                        remoteFiles =
                            listOf(
                                RemoteFile("cloud-1", "g1", "Home", null),
                                RemoteFile("household", "g2", "Household", "key-1"),
                                RemoteFile("travel", "g3", "Travel 2026", null),
                            ),
                    )
                }
            }
        }
        compose.onNodeWithTag("settings-open-budget-value", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }
}
