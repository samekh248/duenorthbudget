package app.duenorth.budget

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.duenorth.budget.core.BankSyncFailure
import app.duenorth.budget.design.theme.MetroTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class BankSyncPanelContentTest {
    @get:Rule
    val compose = createHostComposeRule()

    // While banks fetch the panel names the account and offers nothing to tap.
    @Test
    fun runningNamesTheAccountWithoutActions() {
        compose.mainClock.autoAdvance = false
        compose.setContent { MetroTheme { BankSyncPanel(BankSyncUi(accountName = "Checking"), {}, {}) } }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("bank-sync-status").assertTextEquals("Checking")
        assertEquals(0, compose.onAllNodesWithTagCount("bank-sync-retry"))
    }

    // Done with an error: each failed account shows with its reason, and retry and dismiss answer.
    @Test
    fun finishedWithErrorsOffersRetryAndDismiss() {
        var retried = 0
        var dismissed = 0
        val ui = BankSyncUi(running = false, failures = listOf(BankSyncFailure("Card", "fetch did not finish")))
        compose.setContent { MetroTheme { BankSyncPanel(ui, { retried++ }, { dismissed++ }) } }
        compose.onNodeWithTag("bank-sync-title", useUnmergedTree = true).assertTextEquals(BankSyncCopy.FINISHED_WITH_ERRORS)
        compose.onNodeWithTag("bank-sync-error", useUnmergedTree = true).assertTextEquals("Card: fetch did not finish")
        compose.onNodeWithTag("bank-sync-retry").performClick()
        compose.onNodeWithTag("bank-sync-dismiss").performClick()
        assertEquals(1, retried)
        assertEquals(1, dismissed)
    }

    // A failed sync's tile opens its page on a tap; a running one does not.
    @Test
    fun failedTileOpensItsPage() {
        var opened = 0
        var ui by androidx.compose.runtime.mutableStateOf(BankSyncUi(accountName = "Checking"))
        compose.mainClock.autoAdvance = false
        compose.setContent { MetroTheme { BankSyncPanel(ui, {}, {}, onOpen = { opened++ }) } }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("bank-sync-panel").performClick()
        assertEquals(0, opened)
        ui = BankSyncUi(running = false, failures = listOf(BankSyncFailure("Card", "fetch did not finish")))
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("bank-sync-panel").performClick()
        assertEquals(1, opened)
    }

    // The page lists each account the sync reached: synced, or failed with its reason.
    @Test
    fun reportListsEachAccount() {
        val ui =
            BankSyncUi(
                running = false,
                started = listOf("Checking", "Card"),
                failures = listOf(BankSyncFailure("Card", "fetch did not finish")),
            )
        compose.setContent { MetroTheme { BankSyncReportScreen(ui) } }
        compose.onNodeWithTag("bank-sync-report-Checking").assertExists()
        compose.onNodeWithTag("bank-sync-report-Card").assert(hasAnyDescendant(hasText("fetch did not finish")))
        compose.onNodeWithTag("bank-sync-report-synced", useUnmergedTree = true).assertTextEquals(BankSyncCopy.syncedOf(1, 2))
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(tag: String) =
        onAllNodes(
            androidx.compose.ui.test
                .hasTestTag(tag),
        ).fetchSemanticsNodes().size
}
