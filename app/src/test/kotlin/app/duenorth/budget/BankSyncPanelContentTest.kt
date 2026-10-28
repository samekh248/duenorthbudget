package app.duenorth.budget

import androidx.compose.ui.test.assertTextEquals
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
        compose.onNodeWithTag("bank-sync-status").assertTextEquals(BankSyncCopy.FINISHED_WITH_ERRORS)
        compose.onNodeWithTag("bank-sync-error").assertTextEquals("Card: fetch did not finish")
        compose.onNodeWithTag("bank-sync-retry").performClick()
        compose.onNodeWithTag("bank-sync-dismiss").performClick()
        assertEquals(1, retried)
        assertEquals(1, dismissed)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(tag: String) =
        onAllNodes(
            androidx.compose.ui.test
                .hasTestTag(tag),
        ).fetchSemanticsNodes().size
}
