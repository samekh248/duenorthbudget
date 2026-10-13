package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.BudgetSummary
import app.duenorth.budget.core.FormatPrefs
import app.duenorth.budget.core.SavedInstance
import app.duenorth.budget.core.ShellCopy
import app.duenorth.budget.core.ThemeMode
import app.duenorth.budget.design.theme.MetroTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsContentTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun listsBudgetsAndMarksOpen() {
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(780.dp)) {
                    SettingsScreen(
                        instances = emptyList(),
                        activeInstance = null,
                        instanceReachable = null,
                        signedIn = false,
                        serverError = null,
                        budgets =
                            listOf(
                                BudgetSummary("a", "Alpha", "USD"),
                                BudgetSummary("b", "Beta", "USD"),
                            ),
                        openBudgetId = "b",
                        themeMode = "system",
                        accentId = "magenta",
                        formatPrefs = FormatPrefs(),
                        cloudFileId = null,
                        createError = null,
                        encryptionError = null,
                        openIdAvailable = false,
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
        compose.onNodeWithTag("settings-budget-a").assertIsDisplayed()
        compose.onNodeWithTag("settings-budget-b").assertTextEquals("Beta  open")
        compose.onNodeWithTag("settings-formatting").assertIsDisplayed()
    }

    @Test
    fun blankCreateIsRejected() {
        var createdName: String? = "unset"
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(780.dp)) {
                    SettingsScreen(
                        instances = emptyList(),
                        activeInstance = null,
                        instanceReachable = null,
                        signedIn = false,
                        serverError = null,
                        budgets = emptyList(),
                        openBudgetId = null,
                        themeMode = "system",
                        accentId = "magenta",
                        formatPrefs = FormatPrefs(),
                        cloudFileId = null,
                        createError = ShellCopy.ENTER_NAME,
                        encryptionError = null,
                        openIdAvailable = false,
                        onConnect = { _, _ -> },
                        onBeginOpenId = {},
                        onMakeActive = {},
                        onRemoveInstance = {},
                        onSignOut = {},
                        onOpenBudget = {},
                        onCreateBudget = { name, _ -> createdName = name },
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
        compose.onNodeWithTag("settings-create-error").assertTextEquals(ShellCopy.ENTER_NAME)
        compose.onNodeWithTag("settings-create").performClick()
        assertEquals("", createdName)
        assertTrue(createdName!!.trim().isEmpty())
    }

    @Test
    fun formattingAbsentWhenNoBudgetOpen() {
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(780.dp)) {
                    SettingsScreen(
                        instances =
                            listOf(SavedInstance("i1", "http://192.168.1.10:5006", "home")),
                        activeInstance = SavedInstance("i1", "http://192.168.1.10:5006", "home"),
                        instanceReachable = true,
                        signedIn = true,
                        serverError = null,
                        budgets = listOf(BudgetSummary("a", "Alpha", "USD")),
                        openBudgetId = null,
                        themeMode = "light",
                        accentId = "magenta",
                        formatPrefs = FormatPrefs(),
                        cloudFileId = null,
                        createError = null,
                        encryptionError = null,
                        openIdAvailable = false,
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
        compose.onNodeWithTag("settings-screen").assertIsDisplayed()
        compose.onNodeWithTag("settings-formatting").assertDoesNotExist()
        compose.onNodeWithTag("settings-export").assertDoesNotExist()
        compose.onNodeWithTag("settings-encryption").assertDoesNotExist()
        compose.onNodeWithTag("settings-advanced").assertDoesNotExist()
        compose.onNodeWithText("appearance").assertIsDisplayed()
        compose.onNodeWithTag("instance-status").assertIsDisplayed()
    }

    @Test
    fun themeChangeSurvivesLocalState() {
        var theme = ThemeMode.LIGHT.stored()
        compose.setContent {
            var mode by remember { mutableStateOf(theme) }
            MetroTheme(darkTheme = mode == ThemeMode.DARK.stored()) {
                Box(Modifier.width(390.dp).height(780.dp)) {
                    SettingsScreen(
                        instances = emptyList(),
                        activeInstance = null,
                        instanceReachable = null,
                        signedIn = false,
                        serverError = null,
                        budgets = emptyList(),
                        openBudgetId = null,
                        themeMode = mode,
                        accentId = "magenta",
                        formatPrefs = FormatPrefs(),
                        cloudFileId = null,
                        createError = null,
                        encryptionError = null,
                        openIdAvailable = false,
                        onConnect = { _, _ -> },
                        onBeginOpenId = {},
                        onMakeActive = {},
                        onRemoveInstance = {},
                        onSignOut = {},
                        onOpenBudget = {},
                        onCreateBudget = { _, _ -> },
                        onTheme = {
                            mode = it
                            theme = it
                        },
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
        compose.onNodeWithTag("settings-theme-dark").performClick()
        assertEquals(ThemeMode.DARK.stored(), theme)
        assertTrue(theme == "dark")
    }
}
