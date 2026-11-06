package app.duenorth.budget

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The pull-down sync panel: a pull opens it without syncing, its buttons sync, and pushing back up shuts it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class PullSyncContentTest {
    @get:Rule
    val compose = createHostComposeRule()

    private var synced = 0
    private var bankSynced = 0
    private var ui by mutableStateOf(PullSyncUi(enabled = true, status = "synced 3:42 pm"))

    // As in the app, a tap on sync now marks the shell as syncing straight away.
    private var startsSyncing = false

    private fun show() {
        compose.setContent {
            MetroTheme {
                PullSyncPanel(ui, onSync = {
                    synced++
                    if (startsSyncing) ui = ui.copy(syncing = true, progress = 0.4f)
                }, onBankSync = { bankSynced++ }) { modifier ->
                    LazyColumn(modifier.testTag("page")) {
                        items(40) { MetroText("row $it", Metro.typography.body, Modifier.fillMaxWidth().padding(12.dp)) }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun pull() {
        compose.onNodeWithTag("page").performTouchInput { swipeDown(startY = top + 20f, endY = bottom - 20f, durationMillis = 400) }
        compose.waitForIdle()
    }

    @Test
    fun aLongPullOpensThePanelWithoutSyncing() {
        show()
        compose.onNodeWithTag("pull-sync-now").assertIsNotDisplayed()
        pull()
        compose.onNodeWithTag("pull-sync-now").assertIsDisplayed()
        compose.onNodeWithTag("pull-sync-status", useUnmergedTree = true).assertTextEquals("synced 3:42 pm")
        assertEquals(0, synced)
    }

    @Test
    fun aShortPullSpringsBack() {
        show()
        compose.onNodeWithTag("page").performTouchInput { swipeDown(startY = top + 20f, endY = top + 60f, durationMillis = 300) }
        compose.waitForIdle()
        compose.onNodeWithTag("pull-sync-now").assertIsNotDisplayed()
        assertEquals(0, synced)
    }

    @Test
    fun syncNowSyncs() {
        show()
        pull()
        compose.onNodeWithTag("pull-sync-now").performClick()
        assertEquals(1, synced)
        assertEquals(0, bankSynced)
    }

    @Test
    fun bankSyncShowsOnlyWithLinkedAccounts() {
        show()
        pull()
        compose.onNodeWithTag("pull-sync-banks").assertDoesNotExist()
        ui = ui.copy(bankSync = true)
        compose.waitForIdle()
        compose.onNodeWithTag("pull-sync-banks").performClick()
        assertEquals(1, bankSynced)
    }

    @Test
    fun pushingBackUpShutsIt() {
        show()
        pull()
        compose.onNodeWithTag("page").performTouchInput { swipeUp(startY = bottom - 20f, endY = top + 20f, durationMillis = 400) }
        compose.waitForIdle()
        compose.onNodeWithTag("pull-sync-now").assertIsNotDisplayed()
        assertEquals(0, synced)
    }

    @Test
    fun aTapOnThePageShutsIt() {
        show()
        pull()
        compose.onNodeWithTag("pull-sync-scrim").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("pull-sync-now").assertIsNotDisplayed()
    }

    @Test
    fun offItNeverOpens() {
        ui = ui.copy(enabled = false)
        show()
        pull()
        compose.onNodeWithTag("pull-sync-now").assertIsNotDisplayed()
    }

    @Test
    fun itShowsTheSyncRunningThenShutsWhenItEnds() {
        startsSyncing = true
        show()
        pull()
        compose.onNodeWithTag("pull-sync-now").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("pull-sync-now").assertIsDisplayed()
        compose.onNodeWithTag("pull-sync-status", useUnmergedTree = true).assertTextEquals(PullSyncCopy.SYNCING)
        ui = ui.copy(syncing = false, progress = null, status = "synced 3:50 pm")
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        compose.onNodeWithTag("pull-sync-now").assertIsNotDisplayed()
    }

    @Test
    fun theShellTurnsItOnOnlyForAServerBudgetOnHome() {
        val shell =
            ParityTestSupport
                .household("pull-sync")
                .first.state.value
        assertFalse("a budget kept on the phone has nothing to sync with", shell.pullSync().enabled)
        val server = shell.copy(signedIn = true, cloudFileId = "cloud")
        assertTrue(server.pullSync().enabled)
        assertFalse(server.copy(route = ShellRoute.Settings).pullSync().enabled)
        assertFalse(server.copy(accounts = server.accounts.copy(reorder = true)).pullSync().enabled)
        assertFalse(server.copy(locked = true).pullSync().enabled)
    }
}
