package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.BudgetSummary
import app.duenorth.budget.core.FormatPrefs
import app.duenorth.budget.core.SavedInstance
import app.duenorth.budget.core.SyncCopy
import app.duenorth.budget.core.ThemeMode
import app.duenorth.budget.design.theme.MetroTheme
import org.junit.Assert.assertEquals
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

    private val home = SavedInstance("i1", "http://192.168.1.10:5006", "home")
    private val work = SavedInstance("i2", "https://work.example.com", null)
    private val budgets = listOf(BudgetSummary("a", "Alpha", "USD"), BudgetSummary("b", "Beta", "USD"))

    @Test
    fun headerRowHasSixItemsWithABudgetOpen() {
        show(SettingsPage.BUDGET)
        listOf("budget", "server", "format", "appearance", "advanced", "experimental").forEach {
            compose.onNodeWithText(it).assertExists()
        }
        compose.onNodeWithTag("settings-item-budget").assertIsDisplayed()
    }

    @Test
    fun formatAndAdvancedLeaveTheRowWithNoBudgetOpen() {
        show(SettingsPage.ADVANCED, openBudgetId = null)
        compose.onNodeWithText("format").assertDoesNotExist()
        compose.onNodeWithText("advanced").assertDoesNotExist()
        compose.onNodeWithText("experimental").assertDoesNotExist()
        // The stored advanced item is not shown, so settings falls back to budget.
        compose.onNodeWithTag("settings-item-budget").assertIsDisplayed()
        compose.onNodeWithTag("settings-open-budget").assertTextEquals("no budget open")
        compose.onNodeWithTag("settings-budget-a").assertIsDisplayed()
    }

    @Test
    fun budgetItemShowsOpenBudgetMethodAndOthers() {
        var opened: String? = null
        show(SettingsPage.BUDGET, onOpenBudget = { opened = it })
        compose.onNodeWithTag("settings-open-budget").assertTextEquals("Beta")
        compose.onNodeWithTag("settings-method-value").assertTextEquals("envelope budget · syncs with home")
        compose.onNodeWithTag("settings-budget-b").assertDoesNotExist()
        compose.onNodeWithTag("settings-budget-a").performClick()
        assertEquals("a", opened)
    }

    @Test
    fun budgetItemOpensTheServersBudgetsOnlyWhenSignedIn() {
        var picker = 0
        var signedIn by mutableStateOf(false)
        compose.setContent {
            MetroTheme {
                androidx.compose.foundation.layout.Column {
                    BudgetItem(
                        budgets = budgets,
                        openBudgetId = "b",
                        budgetType = "envelope",
                        learning = false,
                        onOpen = {},
                        onPayees = {},
                        onLearning = {},
                        serverName = "home",
                        signedIn = signedIn,
                        synced = true,
                        onServerBudgets = { picker++ },
                    )
                }
            }
        }
        compose.onNodeWithTag("settings-server-budgets").assertDoesNotExist()
        compose.onNodeWithTag("settings-server-budgets-signed-out").assertIsDisplayed()
        signedIn = true
        compose.onNodeWithTag("settings-server-budgets").performClick()
        assertEquals(1, picker)
    }

    @Test
    fun serverItemPicksTheBackgroundSyncInterval() {
        var picked: Int? = null
        compose.setContent {
            MetroTheme {
                androidx.compose.foundation.layout.Column {
                    ServerItem(
                        instances = listOf(home),
                        activeInstance = home,
                        instanceReachable = true,
                        signedIn = true,
                        onMakeActive = {},
                        onRemove = {},
                        onBackgroundSyncHours = { picked = it },
                    )
                }
            }
        }
        compose.onNodeWithText("every 3 hours").assertIsDisplayed()
        compose.onNodeWithTag("settings-background-sync-picker").performClick()
        compose.onNodeWithTag("settings-background-sync-picker-6").performClick()
        assertEquals(6, picked)
    }

    @Test
    fun serverItemHidesBackgroundSyncWithNoServer() {
        compose.setContent {
            MetroTheme {
                androidx.compose.foundation.layout.Column {
                    ServerItem(emptyList(), null, null, signedIn = false, onMakeActive = {}, onRemove = {})
                }
            }
        }
        compose.onNodeWithTag("settings-background-sync").assertDoesNotExist()
    }

    @Test
    fun headerTapOpensItsItemAndReportsIt() {
        var page: SettingsPage? = null
        show(SettingsPage.BUDGET, onPage = { page = it })
        compose.onNodeWithText("appearance").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("settings-item-appearance").assertIsDisplayed()
        assertEquals(SettingsPage.APPEARANCE, page)
    }

    @Test
    fun serverItemNamesActiveServerAndListsSaved() {
        var removed: String? = null
        show(SettingsPage.SERVER, instanceReachable = false, onRemove = { removed = it })
        compose.onNodeWithTag("instance-status").assertTextEquals("home")
        compose.onNodeWithTag("instance-address-line").assertTextEquals(home.address)
        compose.onNodeWithTag("instance-state").assertTextEquals(SyncCopy.NOT_REACHED)
        compose.onNodeWithTag("instance-i2").assertTextEquals(work.address)
        // A server's commands open from its row.
        compose.onNodeWithTag("instance-activate-i2").assertDoesNotExist()
        compose.onNodeWithTag("instance-i2").performClick()
        compose.onNodeWithTag("instance-activate-i2").assertIsDisplayed()
        compose.onNodeWithTag("instance-remove-i2").performClick()
        assertEquals("i2", removed)
        compose.onNodeWithTag("instance-menu").assertDoesNotExist()
        compose.onNodeWithTag("instance-status").performClick()
        compose.onNodeWithTag("instance-remove-i1").performClick()
        assertEquals("i1", removed)
    }

    @Test
    fun serverItemWithNoServerSaysNotConnected() {
        show(SettingsPage.SERVER, instances = emptyList(), active = null)
        compose.onNodeWithTag("instance-status").assertTextEquals("not connected")
        compose.onNodeWithTag("instance-state").assertDoesNotExist()
    }

    @Test
    fun noItemHoldsATextField() {
        var current by mutableStateOf(SettingsPage.BUDGET)
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(780.dp)) {
                    key(current) { Screen(current) }
                }
            }
        }
        SettingsPage.entries.forEach { page ->
            current = page
            compose.waitForIdle()
            compose.onNodeWithTag("settings-item-${page.name.lowercase()}").assertIsDisplayed()
            compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        }
    }

    @Test
    fun riskyActionsOnlyLiveOnAdvanced() {
        var current by mutableStateOf(SettingsPage.ADVANCED)
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(780.dp)) {
                    key(current) { Screen(current) }
                }
            }
        }
        compose.onNodeWithTag("settings-reset-sync").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("encryption-set").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("encryption-change").assertDoesNotExist()
        val risky = listOf("settings-rebuild", "settings-repair", "settings-reset-sync", "encryption-set", "settings-export")
        listOf(SettingsPage.BUDGET, SettingsPage.SERVER, SettingsPage.FORMAT, SettingsPage.APPEARANCE).forEach { page ->
            current = page
            compose.waitForIdle()
            val item = hasAnyAncestor(hasTestTag("settings-item-${page.name.lowercase()}"))
            risky.forEach { tag -> compose.onAllNodes(hasTestTag(tag) and item).assertCountEquals(0) }
        }
    }

    @Test
    fun encryptedBudgetOffersChangeAndRemove() {
        show(SettingsPage.ADVANCED, encrypted = true)
        compose.onNodeWithTag("settings-encryption-state").performScrollTo().assertTextEquals("encrypted")
        compose.onNodeWithTag("encryption-change").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("encryption-remove").assertExists()
        compose.onNodeWithTag("encryption-set").assertDoesNotExist()
    }

    @Test
    fun encryptionPageAsksOnlyForWhatTheActionNeeds() {
        var mode by mutableStateOf(EncryptionMode.SET)
        var changed: Pair<String, String>? = null
        compose.setContent {
            MetroTheme {
                EncryptionPasswordScreen(
                    mode = mode,
                    error = null,
                    onSet = {},
                    onChange = { a, b -> changed = a to b },
                    onRemove = {},
                )
            }
        }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        compose.onNodeWithTag("encryption-password").assertExists()
        mode = EncryptionMode.CHANGE
        compose.waitForIdle()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(2)
        compose.onNodeWithTag("encryption-save").performClick()
        assertEquals("" to "", changed)
        mode = EncryptionMode.REMOVE
        compose.waitForIdle()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        compose.onNodeWithTag("encryption-current").assertExists()
    }

    @Test
    fun themeChangeSurvivesLocalState() {
        var theme = ThemeMode.LIGHT.stored()
        compose.setContent {
            var mode by remember { mutableStateOf(theme) }
            MetroTheme(darkTheme = mode == ThemeMode.DARK.stored()) {
                Box(Modifier.width(390.dp).height(780.dp)) {
                    Screen(
                        SettingsPage.APPEARANCE,
                        themeMode = mode,
                        onTheme = {
                            mode = it
                            theme = it
                        },
                    )
                }
            }
        }
        compose.onNodeWithTag("settings-theme-dark").performScrollTo().performClick()
        assertEquals(ThemeMode.DARK.stored(), theme)
    }

    private fun show(
        page: SettingsPage,
        openBudgetId: String? = "b",
        instances: List<SavedInstance> = listOf(home, work),
        active: SavedInstance? = home,
        instanceReachable: Boolean? = true,
        encrypted: Boolean = false,
        onPage: (SettingsPage) -> Unit = {},
        onOpenBudget: (String) -> Unit = {},
        onRemove: (String) -> Unit = {},
    ) {
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(780.dp)) {
                    Screen(
                        page,
                        openBudgetId = openBudgetId,
                        instances = instances,
                        active = active,
                        instanceReachable = instanceReachable,
                        encrypted = encrypted,
                        onPage = onPage,
                        onOpenBudget = onOpenBudget,
                        onRemove = onRemove,
                    )
                }
            }
        }
    }

    @Composable
    private fun Screen(
        page: SettingsPage,
        openBudgetId: String? = "b",
        instances: List<SavedInstance> = listOf(home, work),
        active: SavedInstance? = home,
        instanceReachable: Boolean? = true,
        encrypted: Boolean = false,
        themeMode: String = "light",
        onPage: (SettingsPage) -> Unit = {},
        onOpenBudget: (String) -> Unit = {},
        onRemove: (String) -> Unit = {},
        onTheme: (String) -> Unit = {},
    ) {
        SettingsScreen(
            page = page,
            instances = instances,
            activeInstance = active,
            instanceReachable = instanceReachable,
            signedIn = active != null,
            budgets = budgets,
            openBudgetId = openBudgetId,
            themeMode = themeMode,
            accentId = "magenta",
            formatPrefs = FormatPrefs(),
            cloudFileId = "cloud-1",
            encrypted = encrypted,
            onPage = onPage,
            onMakeActive = {},
            onRemoveInstance = onRemove,
            onOpenBudget = onOpenBudget,
            onTheme = onTheme,
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
}
