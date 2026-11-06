package app.duenorth.budget

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.InboxRow
import app.duenorth.budget.core.MatchPair
import app.duenorth.budget.core.MatchSide
import app.duenorth.budget.core.MonthShell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.YearMonth

/** The app icon's count: the same number as the inbox header, carried by one quiet notification. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InboxBadgeTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val manager = app.getSystemService(NotificationManager::class.java)

    @Test
    fun theCountIsTheInboxHeadersRowsToCategorizePlusPossibleMatches() {
        val bank = MatchSide("bank", "CORNER COFFEE #42", 20261015, -575, "visa", "Visa", null, "", "")
        val mine = MatchSide("mine", "Corner Coffee", 20261014, -575, "visa", "Visa", "c-dining", "Dining", "")
        val inbox =
            listOf(
                InboxRow("bank", "CORNER COFFEE #42", -575, 20261015, "visa", "Visa"),
                InboxRow("shell", "Shell", -4_210, 20261014, "checking", "Checking"),
                InboxRow("atm", "(no payee)", -2_000, 20261011, "checking", "Checking"),
            )
        // The bank row sits in the match, not the queue: two rows to categorize and one match.
        assertEquals(3, InboxBadge.count(shell(inbox, listOf(MatchPair(bank, mine)))))
        assertEquals(0, InboxBadge.count(shell(emptyList(), emptyList())))
    }

    @Test
    fun aCountPostsOneQuietBadgedNotificationAndZeroTakesItAway() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        InboxBadge.show(app, 4)
        val posted = shadowOf(manager).allNotifications
        assertEquals(1, posted.size)
        assertEquals(4, posted.single().number)
        val channel = manager.getNotificationChannel(InboxBadge.CHANNEL)
        assertTrue(channel.canShowBadge())
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)

        InboxBadge.show(app, 2)
        assertEquals(2, shadowOf(manager).allNotifications.single().number)

        InboxBadge.show(app, 0)
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }

    @Test
    fun withoutThePermissionNothingIsPostedAndTheAppAsksOnce() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        InboxBadge.show(app, 4)
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
        assertTrue(InboxBadge.shouldAsk(app))
        InboxBadge.markAsked(app)
        assertFalse(InboxBadge.shouldAsk(app))
    }

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
}
