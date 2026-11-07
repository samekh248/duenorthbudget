package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.InboxRow
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.RegisterAccount
import app.duenorth.budget.core.RegisterPage
import app.duenorth.budget.core.RegisterRow
import app.duenorth.budget.design.theme.MetroTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth

/** New transactions ease in; the list as it opens, a scroll, and reduced motion do not animate. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class ArrivalsContentTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun onlyIdsAfterTheFirstLookAreNew() {
        val arrivals = Arrivals()
        assertFalse(arrivals.note(listOf("a", "b")))
        assertFalse(arrivals.isNew("a"))
        assertTrue(arrivals.note(listOf("c", "a", "b")))
        assertTrue(arrivals.isNew("c"))
        assertFalse(arrivals.isNew("a"))
        // A row that leaves and comes back (a search, a fold) is not new.
        assertFalse(arrivals.note(listOf("a")))
        assertFalse(arrivals.note(listOf("a", "b", "c")))
        assertTrue(arrivals.allNew(listOf("c")))
        assertFalse(arrivals.allNew(listOf("c", "a")))
        arrivals.settle()
        assertFalse(arrivals.isNew("c"))
    }

    @Test
    fun theRegisterOpensWithoutAnimatingItsRows() {
        compose.mainClock.autoAdvance = false
        var page by mutableStateOf(registerPage(rows()))
        showRegister({ page })
        compose.mainClock.advanceTimeByFrame()
        val full = height("register-row-older")
        assertTrue(full > 0f)
        page = registerPage(rows())
        compose.mainClock.advanceTimeByFrame()
        assertEquals(full, height("register-row-older"), 0.5f)
    }

    @Test
    fun aNewRegisterRowOpensItsSlotThenSettles() {
        compose.mainClock.autoAdvance = false
        var page by mutableStateOf(registerPage(rows()))
        showRegister({ page })
        compose.mainClock.advanceTimeByFrame()
        val olderTop = top("register-row-older")
        page = registerPage(listOf(row("synced", 20261020, "Synced Store")) + rows())
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(ArrivalMotion.DURATION_MS / 3L)
        // Mid-way: the new row is on screen at the top, its slot part open, so the rows under it are part way down.
        compose.onNodeWithTag("register-row-synced").assertIsDisplayed()
        val partway = top("register-row-older")
        assertTrue("rows below ease down, $olderTop < $partway", partway > olderTop)
        compose.mainClock.advanceTimeBy(ArrivalMotion.DURATION_MS + 200L)
        val settled = top("register-row-older")
        assertTrue("and keep going until the slot is open, $partway < $settled", settled > partway)
    }

    @Test
    fun withAnimationsOffANewRowIsInPlaceAtOnce() {
        compose.mainClock.autoAdvance = false
        var page by mutableStateOf(registerPage(rows()))
        showRegister({ page }, animations = false)
        compose.mainClock.advanceTimeByFrame()
        page = registerPage(listOf(row("synced", 20261005, "Synced Store")) + rows())
        compose.mainClock.advanceTimeByFrame()
        val first = top("register-row-older")
        compose.mainClock.advanceTimeBy(ArrivalMotion.DURATION_MS + 200L)
        assertEquals(first, top("register-row-older"), 0.5f)
    }

    @Test
    fun aRowScrolledIntoViewJustAppears() {
        compose.mainClock.autoAdvance = false
        val long = (1..40).map { row("r$it", 20261001 - it, "Store $it") }
        showRegister({ registerPage(long) })
        compose.mainClock.advanceTimeByFrame()
        // One row a day: each row sits under its own date, a steady step apart.
        val step = top("register-row-r2") - top("register-row-r1")
        val first = composedRows()
        compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        compose.mainClock.advanceTimeByFrame()
        val shown = composedRows()
        val (a, b) = shown.takeLast(3).take(2)
        assertTrue("rows the first screen never had: $first then $shown", a !in first && b !in first)
        assertEquals(step, top("register-row-r$b") - top("register-row-r$a"), 0.5f)
    }

    @Test
    fun aNewInboxRowArrivesInTheQueue() {
        compose.mainClock.autoAdvance = false
        var inbox by mutableStateOf(sampleInbox())
        showInbox { inbox }
        compose.mainClock.advanceTimeByFrame()
        val before = top("inbox-row-shell")
        // A row landing under the featured one, on the featured row's day.
        inbox = listOf(inbox[0], InboxRow("bakery", "Bakery", -900, 20261015, "visa", "Visa", "")) + inbox.drop(1)
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(ArrivalMotion.DURATION_MS / 3L)
        val partway = top("inbox-row-shell")
        compose.mainClock.advanceTimeBy(ArrivalMotion.DURATION_MS + 200L)
        val settled = top("inbox-row-shell")
        assertTrue("shell eases down: $before < $partway < $settled", before < partway && partway < settled)
    }

    @Test
    fun aNewTopTransactionRisesIntoThePanelAndTheOldOneMovesIntoTheQueue() {
        compose.mainClock.autoAdvance = false
        var inbox by mutableStateOf(sampleInbox())
        showInbox { inbox }
        compose.mainClock.advanceTimeByFrame()
        val before = top("inbox-row-atm")
        inbox = listOf(InboxRow("rent", "Rent", -120_000, 20261015, "checking", "Checking", "")) + inbox
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(ArrivalMotion.DURATION_MS / 3L)
        compose.onNodeWithTag("inbox-featured-rent").assertExists()
        compose.onNodeWithTag("inbox-row-coffee").assertExists()
        val partway = top("inbox-row-atm")
        // The whole panel, fill and all, is still rising.
        val panelPartway = top("inbox-featured")
        compose.mainClock.advanceTimeBy(FEATURED_ARRIVE_MS + 200L)
        val settled = top("inbox-row-atm")
        val panelSettled = top("inbox-featured")
        assertTrue("the panel rises into place: $panelSettled < $panelPartway", panelSettled < panelPartway)
        assertTrue("coffee's slot opens in the queue: $before < $partway < $settled", before < partway && partway < settled)
    }

    private fun composedRows() = (1..40).filter { compose.onAllNodesWithTag("register-row-r$it").fetchSemanticsNodes().isNotEmpty() }

    private fun height(tag: String) =
        compose
            .onNodeWithTag(tag)
            .fetchSemanticsNode()
            .boundsInRoot.height

    private fun top(tag: String) =
        compose
            .onNodeWithTag(tag)
            .fetchSemanticsNode()
            .boundsInRoot.top

    private fun showRegister(
        page: () -> RegisterPage,
        animations: Boolean = true,
    ) {
        compose.setContent {
            MetroTheme(animationsEnabled = animations) {
                Box(Modifier.width(390.dp).height(780.dp)) {
                    RegisterScreen(page = page(), onGesture = {}, onOpen = {}, onAdd = {}, onReconcile = {})
                }
            }
        }
    }

    private fun showInbox(inbox: () -> List<InboxRow>) {
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(780.dp)) {
                    InboxSection(shell = inboxShell(inbox()), onListGesture = {})
                }
            }
        }
    }
}

internal fun rows() =
    listOf(
        row("newer", 20261007, "Corner Coffee"),
        row("older", 20261003, "Grocer"),
    )

internal fun row(
    id: String,
    date: Int,
    payee: String,
) = RegisterRow(
    id = id,
    date = date,
    payee = payee,
    categoryId = "c-food",
    categoryLabel = "Groceries",
    amountMinor = -1_250,
    note = "",
    reconciled = false,
    transferId = null,
    partnerOffBudget = null,
    partnerCategoryId = null,
    parent = false,
    parts = emptyList(),
)

internal fun registerPage(rows: List<RegisterRow>) =
    RegisterPage(
        account = RegisterAccount("visa", "visa", offBudget = false, type = "credit", closed = false),
        balanceMinor = rows.sumOf { it.amountMinor },
        currency = Currencies.byCode("USD")!!,
        rows = rows,
        categories = emptyList(),
        accounts = emptyList(),
    )

internal fun inboxShell(inbox: List<InboxRow>) =
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
