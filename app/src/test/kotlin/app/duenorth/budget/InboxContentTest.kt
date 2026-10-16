package app.duenorth.budget

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.InboxRow
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.design.theme.MetroTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class InboxContentTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun rowsShowDayCountSuggestionAccountAndNote() {
        show(sampleInbox())
        compose.onNodeWithTag("inbox-count").assertTextEquals("3")
        compose.onNodeWithTag("inbox-day-20261015").assertTextEquals("oct 15")
        compose.onNodeWithTag("inbox-detail-coffee", useUnmergedTree = true).assertTextEquals("Dining · Visa")
        compose.onNodeWithTag("inbox-detail-atm", useUnmergedTree = true).assertTextEquals("no category · Checking · ATM withdrawal")
        compose.onNodeWithTag("inbox-amount-coffee", useUnmergedTree = true).assertTextEquals("-$5.75")
    }

    @Test
    fun swipeRightAcceptsSwipeLeftCategorizesAndTapOpensDetails() {
        val accepted = mutableListOf<String>()
        val opened = mutableListOf<String>()
        val details = mutableListOf<String>()
        show(
            sampleInbox(),
            onAccept = { accepted += it },
            onInbox = { opened += it },
            actions = InboxActions(onDetails = { details += it }),
        )
        compose.onNodeWithTag("inbox-row-coffee").performTouchInput { swipeRight(startX = width * 0.2f, endX = right) }
        compose.waitForIdle()
        assertEquals(listOf("coffee"), accepted)
        compose.onNodeWithTag("inbox-row-shell").performTouchInput { swipeLeft(startX = width * 0.8f, endX = left) }
        compose.waitForIdle()
        assertEquals(listOf("shell"), opened)
        compose.onNodeWithTag("inbox-row-atm").performClick()
        assertEquals(listOf("shell"), opened)
        assertEquals(listOf("atm"), details)
    }

    @Test
    fun longPressOpensAMenuOfEveryAction() {
        val ran = mutableListOf<String>()
        show(
            sampleInbox(),
            onAccept = { ran += "accept $it" },
            onInbox = { ran += "categorize $it" },
            actions =
                InboxActions(
                    onDetails = { ran += "edit $it" },
                    onSplit = { ran += "split $it" },
                    onSchedule = { ran += "schedule $it" },
                    onDelete = { ran += "delete $it" },
                    onOpenAccount = { ran += "account $it" },
                ),
        )
        compose.onNodeWithTag("inbox-row-coffee").performTouchInput { longClick() }
        compose.onNodeWithTag("inbox-menu").assertIsDisplayed()
        listOf("accept dining", "categorize", "edit", "split", "schedule", "delete", "go to visa").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        compose.onNodeWithText("split").performClick()
        compose.onNodeWithTag("inbox-menu").assertDoesNotExist()
        assertEquals(listOf("split coffee"), ran)
        // No suggestion, no accept.
        val atm = inboxMenuItems(sampleInbox().last(), {}, {}, InboxActions()).map { it.first }
        assertEquals(listOf("categorize", "edit", "split", "schedule", "delete", "go to checking"), atm)
    }

    @Test
    fun dragsFromTheEdgesAreLeftToThePivot() {
        val accepted = mutableListOf<String>()
        val opened = mutableListOf<String>()
        var pivotDragged = 0f
        show(sampleInbox(), onAccept = { accepted += it }, onInbox = { opened += it }, onPivotDrag = { pivotDragged += abs(it) })
        compose.onNodeWithTag("inbox-row-coffee").performTouchInput { swipeRight(startX = width * 0.05f, endX = right) }
        compose.onNodeWithTag("inbox-row-shell").performTouchInput { swipeLeft(startX = width * 0.95f, endX = left) }
        compose.waitForIdle()
        assertEquals(emptyList<String>(), accepted)
        assertEquals(emptyList<String>(), opened)
        assertTrue(pivotDragged > 0f)
    }

    @Test
    fun aFiledRowShowsItsConfirmationWhereItWas() {
        val rows = sampleInbox()
        val shown = mutableListOf<String>()
        compose.mainClock.autoAdvance = false
        show(rows.drop(1), filed = listOf(InboxFiled(rows.first(), 0, "Dining")), onFiledShown = { shown += it })
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("filed to dining").assertIsDisplayed()
        compose.onNodeWithTag("inbox-count").assertTextEquals("2")
        compose.mainClock.advanceTimeBy(FILED_HOLD_MS + 1_000)
        assertEquals(listOf("coffee"), shown)
    }

    @Test
    fun filedRowsGoBackToTheirPlace() {
        val rows = sampleInbox()
        val entries = inboxEntries(rows.drop(1), listOf(InboxFiled(rows.first(), 0, "Dining")))
        assertEquals(listOf("coffee", "shell", "atm"), entries.map { it.row.id })
        assertEquals(listOf("Dining", null, null), entries.map { it.filedTo })
        assertEquals(listOf(2, 1), inboxDays(entries).map { it.size })
    }

    private fun show(
        inbox: List<InboxRow>,
        onAccept: (String) -> Unit = {},
        onInbox: (String) -> Unit = {},
        filed: List<InboxFiled> = emptyList(),
        onFiledShown: (String) -> Unit = {},
        onPivotDrag: (Float) -> Unit = {},
        actions: InboxActions = InboxActions(),
    ) {
        compose.setContent {
            MetroTheme {
                // Stands in for the pivot, which takes horizontal drags the rows leave alone.
                val pivot = rememberDraggableState(onPivotDrag)
                Box(Modifier.width(390.dp).height(780.dp).draggable(pivot, Orientation.Horizontal)) {
                    InboxSection(
                        shell = shell(inbox),
                        onListGesture = {},
                        onInbox = onInbox,
                        onAccept = onAccept,
                        filed = filed,
                        onFiledShown = onFiledShown,
                        actions = actions,
                    )
                }
            }
        }
    }
}

internal fun sampleInbox() =
    listOf(
        InboxRow("coffee", "Corner Coffee", -575, 20261015, "visa", "Visa", "", "c-dining", "Dining"),
        InboxRow("shell", "Shell", -4_210, 20261015, "checking", "Checking", "", "c-gas", "Gas"),
        InboxRow("atm", "(no payee)", -2_000, 20261011, "checking", "Checking", "ATM withdrawal"),
    )

private fun shell(inbox: List<InboxRow>) =
    MonthShell(
        budgetId = "b",
        budgetName = "Home",
        mode = BudgetMode.ENVELOPE,
        currency = Currencies.byCode("USD")!!,
        month = YearMonth.of(2026, 10),
        headerLabel = "to budget",
        headerMinor = 0,
        groups = emptyList(),
        accounts = emptyList(),
        inbox = inbox,
    )
