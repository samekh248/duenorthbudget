package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.AccountChoice
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryChoice
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.InboxRow
import app.duenorth.budget.core.MatchPair
import app.duenorth.budget.core.MatchSide
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.RegisterAccount
import app.duenorth.budget.core.RegisterPage
import app.duenorth.budget.core.RegisterRow
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

/** A possible match in the inbox's panel and on the register, and what its buttons do. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class MatchContentTest {
    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun aPairTakesTheInboxPanelAndLeavesTheQueue() {
        showInbox()
        compose.onNodeWithTag("match-panel-bank|mine").assertIsDisplayed()
        compose.onNodeWithTag("inbox-to-match").assertTextEquals("1")
        compose.onNodeWithTag("inbox-count").assertTextEquals("1")
        compose.onNodeWithTag("inbox-row-bank").assertDoesNotExist()
        compose.onNodeWithTag("inbox-row-shell").assertIsDisplayed()
    }

    @Test
    fun matchAndNotAMatchActOnThePair() {
        val matched = mutableListOf<String>()
        val dismissed = mutableListOf<String>()
        showInbox(InboxActions(onMatch = { matched += it.key }, onNotAMatch = { dismissed += it.key }))
        compose.onNodeWithTag("match-bank|mine").performClick()
        compose.onNodeWithTag("not-a-match-bank|mine").performClick()
        assertEquals(listOf("bank|mine"), matched)
        assertEquals(listOf("bank|mine"), dismissed)
    }

    @Test
    fun theTransactionAMatchLeftLeadsTheInbox() {
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(780.dp)) {
                    InboxSection(shell(inbox, emptyList()), onListGesture = {}, lead = "shell")
                }
            }
        }
        compose.onNodeWithTag("inbox-featured-shell").assertIsDisplayed()
    }

    @Test
    fun theRegisterHoldsThePairTogetherAboveItsRows() {
        val matched = mutableListOf<MatchPair>()
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(780.dp)) {
                    RegisterScreen(
                        page = page(),
                        onGesture = {},
                        onOpen = {},
                        onAdd = {},
                        onTransfer = {},
                        onReconcile = {},
                        onMatch = { matched += it },
                    )
                }
            }
        }
        compose.onNodeWithTag("register-match-bank|mine").assertIsDisplayed()
        compose.onNodeWithTag("register-detail-bank", useUnmergedTree = true).assertTextEquals("oct 15 · from the bank")
        compose.onNodeWithTag("register-day-20261015", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("match-bank|mine").performClick()
        assertEquals(listOf(pair), matched)
    }

    @Test
    fun selectModeHidesThePair() {
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(780.dp)) {
                    RegisterScreen(page(), {}, {}, {}, {}, {}, selection = emptySet())
                }
            }
        }
        compose.onNodeWithTag("register-match-bank|mine").assertDoesNotExist()
        compose.onNodeWithTag("register-row-mine").assertIsDisplayed()
    }

    private fun showInbox(actions: InboxActions = InboxActions()) {
        compose.setContent {
            MetroTheme {
                Box(Modifier.width(390.dp).height(780.dp)) {
                    InboxSection(shell(inbox, listOf(pair)), onListGesture = {}, actions = actions)
                }
            }
        }
    }
}

/** The pair panel and the register's pair, in both themes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class MatchScreenshotTest {
    @Test fun inboxLight() = snapInbox("match_inbox_light", dark = false)

    @Test fun inboxDark() = snapInbox("match_inbox_dark", dark = true)

    @Test fun registerLight() = snapRegister("match_register_light", dark = false)

    @Test fun registerDark() = snapRegister("match_register_dark", dark = true)

    private fun snapInbox(
        name: String,
        dark: Boolean,
    ) = captureRoboImage("src/test/snapshots/$name.png") {
        MetroTheme(darkTheme = dark) {
            Column(Modifier.width(390.dp).height(780.dp)) {
                HomePivot(
                    shell = shell(inbox, listOf(pair)),
                    loading = false,
                    openMonth = YearMonth.of(2026, 10),
                    monthWindow = MonthWindow.around(YearMonth.of(2026, 10)),
                    initialSection = HomeSections.headers.indexOf(HomeSections.INBOX),
                    onGesture = {},
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    private fun snapRegister(
        name: String,
        dark: Boolean,
    ) = captureRoboImage("src/test/snapshots/$name.png") {
        MetroTheme(darkTheme = dark) {
            Column(Modifier.width(390.dp).height(780.dp)) {
                SectionTitled("ACCOUNTS", Modifier.weight(1f)) {
                    RegisterScreen(page(), {}, {}, {}, {}, {})
                }
            }
        }
    }
}

private val bankSide = MatchSide("bank", "CORNER COFFEE #42", 20261015, -575, "visa", "Visa", null, "", "")

private val enteredSide = MatchSide("mine", "Corner Coffee", 20261014, -575, "visa", "Visa", "c-dining", "Dining", "with Sam")

private val pair = MatchPair(bankSide, enteredSide)

private val inbox =
    listOf(
        InboxRow("bank", "CORNER COFFEE #42", -575, 20261015, "visa", "Visa"),
        InboxRow("shell", "Shell", -4_210, 20261014, "checking", "Checking", "", "c-gas", "Gas"),
    )

private fun shell(
    inbox: List<InboxRow>,
    matches: List<MatchPair>,
) = MonthShell(
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
    matches = matches,
)

private fun row(
    id: String,
    date: Int,
    payee: String,
    category: String,
    amount: Long,
    note: String = "",
    cleared: Boolean = true,
) = RegisterRow(id, date, payee, null, category, amount, note, false, null, null, null, false, emptyList(), cleared)

private fun page() =
    RegisterPage(
        account = RegisterAccount("visa", "Visa", offBudget = false, type = "credit", closed = false),
        balanceMinor = -9_390,
        currency = Currencies.byCode("USD")!!,
        rows =
            listOf(
                row("bank", 20261015, "CORNER COFFEE #42", "no category", -575),
                row("mine", 20261014, "Corner Coffee", "Dining", -575, note = "with Sam", cleared = false),
                row("tj", 20261012, "Trader Joe's", "Groceries", -8_420),
            ),
        categories = listOf(CategoryChoice("c-dining", "Dining", "Fun", income = false)),
        accounts = listOf(AccountChoice("checking", "Checking", offBudget = false, type = "checking", closed = false)),
        matches = listOf(pair),
    )
