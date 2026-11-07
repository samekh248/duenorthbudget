package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.InboxRow
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.TransferGuess
import app.duenorth.budget.design.MonthWindow
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/** A guessed transfer in the inbox: the featured panel offers it, the queue names it, and the menu can make it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class InboxTransferContentTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun theFeaturedPanelOffersTheTransferAheadOfACategory() {
        val made = mutableListOf<String>()
        show(InboxActions(onTransfer = { made += it }))
        compose.onNodeWithText("transfer to Ally Savings 4821", useUnmergedTree = true).assertIsDisplayed()
        compose
            .onNodeWithTag("inbox-featured-reasons", useUnmergedTree = true)
            .assertTextEquals("says transfer · ends in 4821 · went there 3 times before")
        compose.onNodeWithTag("inbox-featured-accept").assertDoesNotExist()
        compose.onNodeWithTag("inbox-featured-transfer").performClick()
        assertEquals(listOf("xfer"), made)
    }

    @Test
    fun aQueuedRowSaysWhereItLooksLikeItWent() {
        show(rows = transferInbox.reversed())
        compose.onNodeWithTag("inbox-detail-xfer", useUnmergedTree = true).assertTextEquals("transfer to ally savings 4821? · Checking")
    }

    @Test
    fun theMenuCanMakeTheTransfer() {
        val made = mutableListOf<String>()
        val items = inboxMenuItems(transferInbox.first(), {}, {}, InboxActions(onTransfer = { made += it }))
        assertEquals("transfer to ally savings 4821", items.first().first)
        items.first().second()
        assertEquals(listOf("xfer"), made)
    }

    @Test
    fun aTransferMadeSaysSoInItsConfirmation() {
        assertEquals(
            "filed as transfer to ally savings 4821",
            filedLine(InboxEntry(transferInbox.first(), "transfer to Ally Savings 4821", transfer = true)),
        )
        assertEquals("filed to gas", filedLine(InboxEntry(transferInbox.last(), "Gas")))
    }

    private fun show(
        actions: InboxActions = InboxActions(),
        rows: List<InboxRow> = transferInbox,
    ) {
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(780.dp)) {
                    InboxSection(transferShell(rows), onListGesture = {}, actions = actions)
                }
            }
        }
    }
}

/** The inbox with a guessed transfer featured, in both themes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class InboxTransferScreenshotTest {
    @Test fun light() = snap("inbox_transfer_light", dark = false)

    @Test fun dark() = snap("inbox_transfer_dark", dark = true)

    private fun snap(
        name: String,
        dark: Boolean,
    ) = captureRoboImage("src/test/snapshots/$name.png") {
        MetroTheme(darkTheme = dark) {
            Column(Modifier.width(390.dp).height(780.dp)) {
                HomePivot(
                    shell = transferShell(transferInbox),
                    loading = false,
                    openMonth = YearMonth.of(2026, 11),
                    monthWindow = MonthWindow.around(YearMonth.of(2026, 11)),
                    initialSection = HomeSections.headers.indexOf(HomeSections.INBOX),
                    onGesture = {},
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private val guess =
    TransferGuess(
        accountId = "savings",
        accountName = "Ally Savings 4821",
        reasons = listOf("says transfer", "ends in 4821", "went there 3 times before"),
    )

internal val transferInbox =
    listOf(
        InboxRow(
            "xfer",
            "ONLINE TRANSFER TO SAV XXXXXX4821",
            -50_000,
            20261106,
            "checking",
            "Checking",
            suggestionId = "c-gas",
            suggestionName = "Gas",
            transfer = guess,
        ),
        InboxRow("tj", "TRADER JOE'S #552", -6_418, 20261105, "checking", "Checking", "", "c-groceries", "Groceries"),
        InboxRow("shell", "SHELL OIL 5741", -4_210, 20261104, "checking", "Checking", "", "c-gas", "Gas"),
    )

internal fun transferShell(inbox: List<InboxRow>) =
    MonthShell(
        budgetId = "b",
        budgetName = "Home",
        mode = BudgetMode.ENVELOPE,
        currency = Currencies.byCode("USD")!!,
        month = YearMonth.of(2026, 11),
        headerLabel = "to budget",
        headerMinor = 0,
        groups = emptyList(),
        accounts = emptyList(),
        inbox = inbox,
    )
