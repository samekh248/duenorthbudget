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
import app.duenorth.budget.core.BankSyncFix
import app.duenorth.budget.core.SimpleFinError
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

    // Every SimpleFIN failure offers SimpleFIN Bridge, whatever the error; one the app can also mend carries that button too.
    @Test
    fun reportOffersEachFailuresFix() {
        val attention = BankSyncFailure("Card", SimpleFinError.NEEDS_ATTENTION, "card", BankSyncFix.SIMPLEFIN)
        val token = BankSyncFailure("Joint", SimpleFinError.TOKEN_REFUSED, "joint", BankSyncFix.TOKEN)
        val down = BankSyncFailure("Savings", SimpleFinError.SERVER_DOWN, "savings")
        val signIn = BankSyncFailure("Loan", "sign in again", "loan", BankSyncFix.SIGN_IN)
        val fixed = mutableListOf<BankSyncFailure>()
        val bridged = mutableListOf<BankSyncFailure>()
        val ui = BankSyncUi(running = false, failures = listOf(attention, token, down, signIn))
        compose.setContent { MetroTheme { BankSyncReportScreen(ui, onSimpleFin = { bridged += it }, onFix = { fixed += it }) } }
        listOf("Card", "Joint", "Savings").forEach {
            compose.onNodeWithTag("bank-sync-simplefin-$it").assert(hasText(BankSyncCopy.FIX_SIMPLEFIN)).performClick()
        }
        assertEquals(0, compose.onAllNodesWithTagCount("bank-sync-simplefin-Loan"))
        compose.onNodeWithTag("bank-sync-fix-Joint").assert(hasText(BankSyncCopy.FIX_TOKEN)).performClick()
        listOf("Card", "Savings", "Loan").forEach { assertEquals(0, compose.onAllNodesWithTagCount("bank-sync-fix-$it")) }
        assertEquals(listOf(attention, token, down), bridged)
        assertEquals(listOf(token), fixed)
    }

    // Every failed account the page can name by id offers unlink beside its fix, after a single failed sync.
    @Test
    fun reportOffersUnlinkForEachLinkedFailure() {
        val missing = BankSyncFailure("Savings", SimpleFinError.ACCOUNT_MISSING, "savings", BankSyncFix.RELINK)
        val down = BankSyncFailure("Card", SimpleFinError.SERVER_DOWN, "card")
        val unnamed = BankSyncFailure("Mystery", "fetch did not finish")
        val asked = mutableListOf<BankSyncFailure>()
        val ui = BankSyncUi(running = false, failures = listOf(missing, down, unnamed))
        compose.setContent { MetroTheme { BankSyncReportScreen(ui, onUnlink = { asked += it }) } }
        compose.onNodeWithTag("bank-sync-unlink-Savings").assert(hasText(BankSyncCopy.UNLINK)).performClick()
        compose.onNodeWithTag("bank-sync-unlink-Card").performClick()
        assertEquals(0, compose.onAllNodesWithTagCount("bank-sync-unlink-Mystery"))
        assertEquals(listOf(missing, down), asked)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(tag: String) =
        onAllNodes(
            androidx.compose.ui.test
                .hasTestTag(tag),
        ).fetchSemanticsNodes().size
}
