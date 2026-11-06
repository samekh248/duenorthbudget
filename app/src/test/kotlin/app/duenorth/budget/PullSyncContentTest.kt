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
import app.duenorth.budget.core.BankSyncFailure
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
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
    private var details = 0
    private var dismissed = 0
    private val failed = BankSyncUi(running = false, failures = listOf(BankSyncFailure("Card", "fetch did not finish")))

    private fun show() {
        compose.setContent {
            MetroTheme {
                PullSyncPanel(ui, onSync = {
                    synced++
                    if (startsSyncing) ui = ui.copy(syncing = true)
                }, onBankSync = { bankSynced++ }, onDetails = { details++ }, onDismissFailure = {
                    dismissed++
                    ui = ui.copy(failure = null)
                }) { modifier ->
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

    // A tap folds the panel straight back up; its grip says syncing until the sync is done, then goes.
    @Test
    fun aTapFoldsThePanelIntoASyncingStripUntilItsDone() {
        startsSyncing = true
        show()
        pull()
        compose.onNodeWithTag("pull-sync-now").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("pull-sync-now").assertIsNotDisplayed()
        compose.onNodeWithTag("pull-sync-syncing").assertIsDisplayed()
        ui = ui.copy(syncing = false, status = "synced 3:50 pm")
        compose.waitForIdle()
        compose.onNodeWithTag("pull-sync-syncing").assertDoesNotExist()
        compose.onNodeWithTag("pull-sync-now").assertIsNotDisplayed()
    }

    // Bank sync folds the panel too, and a failure it ends in takes the strip's place.
    @Test
    fun aFailedSyncFromThePanelLeavesItsStrip() {
        show()
        pull()
        ui = ui.copy(bankSync = true)
        compose.waitForIdle()
        compose.onNodeWithTag("pull-sync-banks").performClick()
        ui = ui.copy(syncing = true)
        compose.waitForIdle()
        compose.onNodeWithTag("pull-sync-syncing").assertIsDisplayed()
        ui = ui.copy(syncing = false, failure = failed)
        compose.waitForIdle()
        compose.onNodeWithTag("pull-sync-syncing").assertDoesNotExist()
        compose.onNodeWithTag("pull-sync-strip").assertIsDisplayed()
    }

    // A failed bank sync waits as a quiet strip naming what failed; a tap on it opens the details, with no button for them.
    @Test
    fun aFailedSyncsStripOpensItsDetails() {
        ui = ui.copy(failure = failed)
        show()
        compose.onNodeWithTag("pull-sync-strip").assertIsDisplayed()
        compose.onNodeWithTag("pull-sync-strip-title", useUnmergedTree = true).assertTextEquals(BankSyncCopy.FINISHED_WITH_ERRORS)
        compose.onNodeWithTag("pull-sync-strip-line", useUnmergedTree = true).assertTextEquals("Card: fetch did not finish")
        compose.onNodeWithTag("pull-sync-details").assertDoesNotExist()
        compose.onNodeWithTag("pull-sync-strip").performClick()
        assertEquals(1, details)
        assertEquals(0, dismissed)
    }

    // The strip's × dismisses it.
    @Test
    fun theStripsCrossDismissesIt() {
        ui = ui.copy(failure = failed)
        show()
        compose.onNodeWithTag("pull-sync-strip-dismiss").performClick()
        compose.waitForIdle()
        assertEquals(1, dismissed)
        assertEquals(0, details)
        compose.onNodeWithTag("pull-sync-strip").assertDoesNotExist()
    }

    // In the open panel the failure's row opens the details on a tap too.
    @Test
    fun thePanelsFailureOpensItsDetails() {
        ui = ui.copy(failure = failed)
        show()
        pull()
        compose.onNodeWithTag("pull-sync-failure").performClick()
        assertEquals(1, details)
    }

    @Test
    fun dismissClearsTheFailure() {
        ui = ui.copy(failure = failed)
        show()
        pull()
        compose.onNodeWithTag("pull-sync-failure-dismiss").performClick()
        compose.waitForIdle()
        assertEquals(1, dismissed)
        compose.onNodeWithTag("pull-sync-failure").assertDoesNotExist()
        compose.onNodeWithTag("pull-sync-strip").assertDoesNotExist()
    }

    // The first-run peek drops the panel into view, then tucks it back, and says once that it played.
    @Test
    fun thePeekBouncesOnceAndTucksBack() {
        var peeked = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MetroTheme {
                PullSyncPanel(ui, onSync = {}, onBankSync = {}, peek = peeked == 0, onPeeked = { peeked++ }) { modifier ->
                    LazyColumn(modifier.testTag("page")) {
                        items(40) { MetroText("row $it", Metro.typography.body, Modifier.fillMaxWidth().padding(12.dp)) }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_600)
        compose.onNodeWithTag("pull-sync-now").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(4_000)
        compose.onNodeWithTag("pull-sync-now").assertIsNotDisplayed()
        assertEquals(1, peeked)
        assertEquals(0, synced)
    }

    // Only a fresh install owes the peek; once played it is never owed again.
    @Test
    fun aFreshInstallOwesThePeekOnce() {
        val context = RuntimeEnvironment.getApplication()
        assertTrue(PullSyncPeek.pending(context))
        assertTrue(PullSyncPeek.pending(context))
        PullSyncPeek.markDone(context)
        assertFalse(PullSyncPeek.pending(context))
    }

    // Someone updating from an older version has used the app before, so the peek is recorded as seen.
    @Test
    fun anUpdateNeverPeeks() {
        val context = RuntimeEnvironment.getApplication()
        Shadows.shadowOf(context.packageManager).getInternalMutablePackageInfo(context.packageName).apply {
            firstInstallTime = 1_000L
            lastUpdateTime = 2_000L
        }
        assertFalse(PullSyncPeek.pending(context))
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
        // A finished sync that failed shows; one still running does not.
        assertEquals(failed, server.copy(bankSync = BankSyncState(panel = failed)).pullSync().failure)
        assertEquals(null, server.copy(bankSync = BankSyncState(panel = BankSyncUi())).pullSync().failure)
    }
}
