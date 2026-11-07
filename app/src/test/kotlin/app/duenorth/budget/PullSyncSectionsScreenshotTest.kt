package app.duenorth.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import app.duenorth.budget.core.AccountRow
import app.duenorth.budget.core.AssistantReport
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.GroupRow
import app.duenorth.budget.core.InboxRow
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.design.MonthWindow
import app.duenorth.budget.design.components.MetroAppBar
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/** Samples for the home sections pulled down: a few accounts, and inbox rows to file. */
internal object PullSyncSections {
    val accounts =
        listOf(
            AccountRow("checking", "Checking", false, false, 182_450),
            AccountRow("visa", "Visa", false, false, -24_310, bankSync = true),
        )

    val inbox =
        listOf(
            InboxRow("coffee", "CORNER COFFEE #42", -575, 20261015, "visa", "Visa"),
            InboxRow("fuel", "Shell", -4_210, 20261014, "checking", "Checking"),
        )

    fun shell(
        accounts: List<AccountRow>,
        inbox: List<InboxRow>,
    ) = MonthShell(
        budgetId = "home",
        budgetName = "Home",
        mode = BudgetMode.ENVELOPE,
        currency = Currencies.byCode("USD")!!,
        month = YearMonth.of(2026, 10),
        headerLabel = "to budget",
        headerMinor = 42_000,
        groups =
            listOf(
                GroupRow(
                    "g-bills",
                    "Bills",
                    124_000,
                    listOf(
                        CategoryRow("c-rent", "Rent", 110_000, 0, 110_000, false),
                        CategoryRow("c-power", "Power", 14_000, 0, 14_000, false),
                    ),
                ),
            ),
        accounts = accounts,
        inbox = inbox,
    )
}

/** Each home section pulled down: the sync panel opens over the inbox, accounts, reports and assistant, as on the budget. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w390dp-h780dp-xxhdpi")
class PullSyncSectionsScreenshotTest {
    @Before
    fun pinCalendarMonth() {
        CalendarMonth.pinned = YearMonth.of(2026, 10)
    }

    @After
    fun unpinCalendarMonth() {
        CalendarMonth.pinned = null
    }

    @get:Rule
    val compose = createHostComposeRule()

    @Test
    fun inboxEmptyLight() = snap("pull_sync_inbox_empty_light", HomeSections.INBOX, ScreenBar.HOME_INBOX, dark = false, inbox = emptyList())

    @Test
    fun inboxEmptyDark() = snap("pull_sync_inbox_empty_dark", HomeSections.INBOX, ScreenBar.HOME_INBOX, dark = true, inbox = emptyList())

    @Test
    fun inboxLight() = snap("pull_sync_inbox_light", HomeSections.INBOX, ScreenBar.HOME_INBOX, dark = false)

    @Test
    fun inboxDark() = snap("pull_sync_inbox_dark", HomeSections.INBOX, ScreenBar.HOME_INBOX, dark = true)

    @Test
    fun accountsLight() = snap("pull_sync_accounts_light", HomeSections.ACCOUNTS, ScreenBar.HOME_ACCOUNTS, dark = false)

    @Test
    fun accountsDark() = snap("pull_sync_accounts_dark", HomeSections.ACCOUNTS, ScreenBar.HOME_ACCOUNTS, dark = true)

    @Test
    fun reportsLight() = snap("pull_sync_reports_light", HomeSections.REPORTS, ScreenBar.HOME_REPORTS, dark = false)

    @Test
    fun reportsDark() = snap("pull_sync_reports_dark", HomeSections.REPORTS, ScreenBar.HOME_REPORTS, dark = true)

    @Test
    fun assistantLight() = snap("pull_sync_assistant_light", HomeSections.ASSISTANT, ScreenBar.HOME_ASSISTANT, dark = false)

    @Test
    fun assistantDark() = snap("pull_sync_assistant_dark", HomeSections.ASSISTANT, ScreenBar.HOME_ASSISTANT, dark = true)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun snap(
        name: String,
        section: String,
        homeSection: Int,
        dark: Boolean,
        inbox: List<InboxRow> = PullSyncSections.inbox.take(1),
    ) {
        val shell = PullSyncSections.shell(PullSyncSections.accounts, inbox)
        val actions =
            ScreenBar
                .specsFor(ShellRoute.Home, hasBudget = true, mode = BudgetMode.ENVELOPE, homeSection = homeSection)
                .map { it.action() }
        compose.setContent {
            MetroTheme(darkTheme = dark) {
                Column(Modifier.fillMaxSize()) {
                    PullSyncPanel(
                        PullSyncUi(enabled = true, status = "synced 3:42 pm", bankSync = true),
                        onSync = {},
                        onBankSync = {},
                        modifier = Modifier.weight(1f).testTag("home"),
                    ) { inner ->
                        HomePivot(
                            shell = shell,
                            loading = false,
                            openMonth = shell.month,
                            monthWindow = MonthWindow.around(shell.month),
                            initialSection = HomeSections.headers.indexOf(section),
                            onGesture = {},
                            assistant = AssistantUi(report = AssistantReport(emptyList())),
                            modifier = inner,
                        )
                    }
                    MetroAppBar(actions, false, {}, windowInsets = WindowInsets(0))
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("home").performTouchInput { swipeDown(startY = top + 400f, endY = bottom - 20f, durationMillis = 400) }
        compose.waitForIdle()
        captureScreenRoboImage("src/test/snapshots/$name.png")
    }
}
