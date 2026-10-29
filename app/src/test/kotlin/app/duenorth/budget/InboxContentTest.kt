package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class InboxContentTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun theTopRowIsFeaturedAndTheRestWaitWithTheirDetails() {
        show(sampleInbox())
        compose.onNodeWithTag("inbox-count").assertTextEquals("3")
        compose.onNodeWithTag("inbox-featured-coffee").assertIsDisplayed()
        compose.onNodeWithTag("inbox-featured-amount", useUnmergedTree = true).assertTextEquals("-$5.75")
        compose.onNodeWithText("Dining", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("inbox-row-coffee").assertDoesNotExist()
        compose.onNodeWithTag("inbox-detail-shell", useUnmergedTree = true).assertTextEquals("Gas · Checking")
        compose
            .onNodeWithTag(
                "inbox-detail-atm",
                useUnmergedTree = true,
            ).assertTextEquals("no category · Checking · ATM withdrawal")
        compose.onNodeWithTag("inbox-amount-atm", useUnmergedTree = true).assertTextEquals("-$20.00")
    }

    @Test
    fun theQueueIsGroupedUnderDateHeadings() {
        show(sampleInbox())
        compose.onNodeWithTag("inbox-day-20261015", useUnmergedTree = true).assertTextEquals("oct 15")
        compose.onNodeWithTag("inbox-day-20261011", useUnmergedTree = true).assertTextEquals("oct 11")
        val shellTop =
            compose
                .onNodeWithTag("inbox-row-shell")
                .fetchSemanticsNode()
                .boundsInRoot.top
        val atmTop =
            compose
                .onNodeWithTag("inbox-row-atm")
                .fetchSemanticsNode()
                .boundsInRoot.top
        val oct11 = compose.onNodeWithTag("inbox-day-20261011", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("oct 11 heads the atm row, below shell", oct11.top > shellTop && oct11.bottom <= atmTop)
        assertEquals(
            listOf(listOf("coffee", "shell"), listOf("atm")),
            inboxDays(inboxEntries(sampleInbox(), emptyList())).map { day -> day.map { it.row.id } },
        )
    }

    @Test
    fun selectModeGroupsEveryRowUnderDateHeadings() {
        show(sampleInbox(), selection = emptySet())
        compose.onNodeWithTag("inbox-day-20261015", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("inbox-day-20261011", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun theFeaturedButtonsAcceptAndCategorizeAndTapsOpenDetails() {
        val accepted = mutableListOf<String>()
        val opened = mutableListOf<String>()
        val details = mutableListOf<String>()
        show(
            sampleInbox(),
            onAccept = { accepted += it },
            onInbox = { opened += it },
            actions = InboxActions(onDetails = { details += it }),
        )
        compose.onNodeWithTag("inbox-featured-accept").performClick()
        compose.onNodeWithTag("inbox-featured-categorize").performClick()
        compose.onNodeWithTag("inbox-row-atm").performClick()
        assertEquals(listOf("coffee"), accepted)
        assertEquals(listOf("coffee"), opened)
        assertEquals(listOf("atm"), details)
    }

    @Test
    fun aFeaturedRowWithoutASuggestionOnlyOffersCategorize() {
        show(sampleInbox().takeLast(1))
        compose.onNodeWithTag("inbox-featured-atm").assertIsDisplayed()
        compose.onNodeWithTag("inbox-featured-accept").assertDoesNotExist()
        compose.onNodeWithTag("inbox-featured-categorize").assertIsDisplayed()
        compose.onNodeWithText(InboxCopy.NO_SUGGESTION, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aRowCanNoLongerBeSwiped() {
        val accepted = mutableListOf<String>()
        val opened = mutableListOf<String>()
        show(sampleInbox(), onAccept = { accepted += it }, onInbox = { opened += it })
        compose.onNodeWithTag("inbox-row-shell").performTouchInput { swipeRight(startX = width * 0.3f, endX = right) }
        compose.onNodeWithTag("inbox-row-atm").performTouchInput { swipeLeft(startX = width * 0.7f, endX = left) }
        compose.waitForIdle()
        assertEquals(emptyList<String>(), accepted)
        assertEquals(emptyList<String>(), opened)
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
                    onSchedule = { ran += "schedule $it" },
                    onDelete = { ran += "delete $it" },
                    onOpenAccount = { ran += "account $it" },
                ),
        )
        compose.onNodeWithTag("inbox-row-shell").performTouchInput { longClick() }
        compose.onNodeWithTag("inbox-menu").assertIsDisplayed()
        listOf("accept", "categorize", "edit", "schedule", "delete", "go to checking").forEach {
            compose.onNode(hasText(it) and hasAnyAncestor(hasTestTag("inbox-menu"))).assertIsDisplayed()
        }
        // Splitting is in the transaction's own form, which edit opens, so the menu has no split of its own.
        compose.onNode(hasText("split") and hasAnyAncestor(hasTestTag("inbox-menu"))).assertDoesNotExist()
        compose.onNodeWithText("schedule").performClick()
        compose.onNodeWithTag("inbox-menu").assertDoesNotExist()
        assertEquals(listOf("schedule shell"), ran)
        // The featured row has the same menu.
        compose.onNodeWithTag("inbox-featured-coffee").performTouchInput { longClick(center.copy(y = 10f)) }
        compose.onNodeWithText("go to visa").performClick()
        assertEquals(listOf("schedule shell", "account visa"), ran)
        // No suggestion, no accept.
        val atm = inboxMenuItems(sampleInbox().last(), {}, {}, InboxActions()).map { it.first }
        assertEquals(listOf("categorize", "edit", "schedule", "delete", "go to checking"), atm)
    }

    @Test
    fun selectModeChecksRowsInsteadOfOpeningThem() {
        val toggled = mutableListOf<String>()
        val details = mutableListOf<String>()
        show(sampleInbox(), selection = setOf("shell"), onToggle = { toggled += it }, actions = InboxActions(onDetails = { details += it }))
        compose.onNodeWithTag("inbox-featured").assertDoesNotExist()
        compose.onNodeWithTag("inbox-selected").assertTextEquals("1")
        compose.onNodeWithTag("inbox-checked-shell", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("inbox-unchecked-coffee", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("inbox-row-atm").performClick()
        // A long press checks the row too; there is no menu in select mode.
        compose.onNodeWithTag("inbox-row-coffee").performTouchInput { longClick() }
        compose.onNodeWithTag("inbox-menu").assertDoesNotExist()
        assertEquals(listOf("atm", "coffee"), toggled)
        assertEquals(emptyList<String>(), details)
    }

    @Test
    fun aFiledTopRowConfirmsInThePanelThenTheNextRisesIntoIt() {
        val rows = sampleInbox()
        val shown = mutableListOf<String>()
        compose.mainClock.autoAdvance = false
        show(rows.drop(1), filed = listOf(InboxFiled(rows.first(), 0, "Dining")), onFiledShown = { shown += it })
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("filed to dining", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("inbox-count").assertTextEquals("2")
        // The next row waits in the queue until it rises.
        compose.onNodeWithTag("inbox-row-shell").assertExists()
        compose.mainClock.advanceTimeBy(FILED_HOLD_MS + 1_000)
        assertEquals(listOf("coffee"), shown)
    }

    @Test
    fun aRowFiledFromTheQueueConfirmsInItsOwnSlot() {
        val rows = sampleInbox()
        val shown = mutableListOf<String>()
        compose.mainClock.autoAdvance = false
        show(rows - rows[1], filed = listOf(InboxFiled(rows[1], 1, "Gas")), onFiledShown = { shown += it })
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("inbox-featured-coffee").assertExists()
        compose.onNodeWithTag("inbox-filed-shell").assertExists()
        compose.mainClock.advanceTimeBy(FILED_HOLD_MS + 1_000)
        assertEquals(listOf("shell"), shown)
    }

    @Test
    fun theMenuStaysClearOfTheNavigationBar() {
        val items = inboxMenuItems(sampleInbox().first(), {}, {}, InboxActions())
        var screen = 0
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(780.dp).onSizeChanged { screen = it.height }) {
                    // A row near the bottom: the menu cannot fit below it above a 48px navigation bar.
                    InboxMenuPanel(Rect(0f, 1_900f, 1_000f, 2_000f), items, onDismiss = {}, safeBottom = 48)
                }
            }
        }
        val menu = compose.onNodeWithTag("inbox-menu").fetchSemanticsNode().boundsInRoot
        assertTrue(menu.bottom <= screen - 48)
        assertTrue(menu.bottom <= 1_900f)
    }

    @Test
    fun filedRowsGoBackToTheirPlace() {
        val rows = sampleInbox()
        val entries = inboxEntries(rows.drop(1), listOf(InboxFiled(rows.first(), 0, "Dining")))
        assertEquals(listOf("coffee", "shell", "atm"), entries.map { it.row.id })
        assertEquals(listOf("Dining", null, null), entries.map { it.filedTo })
    }

    private fun show(
        inbox: List<InboxRow>,
        onAccept: (String) -> Unit = {},
        onInbox: (String) -> Unit = {},
        filed: List<InboxFiled> = emptyList(),
        onFiledShown: (String) -> Unit = {},
        actions: InboxActions = InboxActions(),
        selection: Set<String>? = null,
        onToggle: (String) -> Unit = {},
    ) {
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(780.dp)) {
                    InboxSection(
                        shell = shell(inbox),
                        onListGesture = {},
                        onInbox = onInbox,
                        onAccept = onAccept,
                        filed = filed,
                        onFiledShown = onFiledShown,
                        actions = actions,
                        selection = selection,
                        onToggle = onToggle,
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
