package app.duenorth.budget

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import app.duenorth.budget.core.AccountRow
import app.duenorth.budget.core.AssistantReport
import app.duenorth.budget.core.InboxRow
import app.duenorth.budget.design.MonthWindow
import app.duenorth.budget.design.theme.MetroTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Every home section opens the sync panel on a pull, whether or not it has a list to scroll. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class PullSyncSectionsTest {
    @get:Rule
    val compose = createHostComposeRule()

    private var selected = -1

    private fun pullOn(
        section: String,
        accounts: List<AccountRow> = emptyList(),
        inbox: List<InboxRow> = emptyList(),
    ) {
        val shell = PullSyncSections.shell(accounts, inbox)
        compose.setContent {
            MetroTheme {
                PullSyncPanel(PullSyncUi(enabled = true, status = "synced 3:42 pm"), {}, {}, Modifier.fillMaxSize().testTag("home")) {
                    HomePivot(
                        shell = shell,
                        loading = false,
                        openMonth = shell.month,
                        monthWindow = MonthWindow.around(shell.month),
                        initialSection = HomeSections.headers.indexOf(section),
                        onGesture = {},
                        onSection = { selected = it },
                        assistant = AssistantUi(report = AssistantReport(emptyList())),
                        modifier = it,
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("home").performTouchInput { swipeDown(startY = top + 400f, endY = bottom - 20f, durationMillis = 400) }
        compose.waitForIdle()
        compose.onNodeWithTag("pull-sync-scrim").assertIsDisplayed()
    }

    @Test
    fun anEmptyInboxPulls() = pullOn(HomeSections.INBOX)

    @Test
    fun anInboxWithOnlyItsFeaturedRowPulls() = pullOn(HomeSections.INBOX, PullSyncSections.accounts, PullSyncSections.inbox.take(1))

    @Test
    fun theBudgetPulls() = pullOn(HomeSections.BUDGET)

    @Test
    fun noAccountsPulls() = pullOn(HomeSections.ACCOUNTS)

    @Test
    fun accountsPull() = pullOn(HomeSections.ACCOUNTS, PullSyncSections.accounts)

    @Test
    fun reportsPull() = pullOn(HomeSections.REPORTS)

    @Test
    fun anAssistantWithNothingFoundPulls() = pullOn(HomeSections.ASSISTANT)

    @Test
    fun aSidewaysSwipeStillTurnsThePivot() {
        val shell = PullSyncSections.shell(emptyList(), emptyList())
        compose.setContent {
            MetroTheme {
                PullSyncPanel(PullSyncUi(enabled = true), {}, {}, Modifier.fillMaxSize().testTag("home")) {
                    HomePivot(
                        shell = shell,
                        loading = false,
                        openMonth = shell.month,
                        monthWindow = MonthWindow.around(shell.month),
                        initialSection = HomeSections.headers.indexOf(HomeSections.ASSISTANT) - 1,
                        onGesture = {},
                        onSection = { selected = it },
                        assistant = AssistantUi(report = AssistantReport(emptyList())),
                        modifier = it,
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("home").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(HomeSections.headers.indexOf(HomeSections.ASSISTANT), selected)
    }
}
