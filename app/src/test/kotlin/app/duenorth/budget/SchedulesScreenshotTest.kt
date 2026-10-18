package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.ScheduleDetails
import app.duenorth.budget.core.SchedulePosted
import app.duenorth.budget.core.ScheduleRepeat
import app.duenorth.budget.core.ScheduleSummary
import app.duenorth.budget.design.components.MetroAppBar
import app.duenorth.budget.design.components.ScreenAction
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Accounts → schedules: upcoming as a list and a calendar, all, and one schedule's page. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class SchedulesScreenshotTest {
    private val usd = Currencies.byCode("USD")!!
    private val today = 20261018

    private fun monthly(
        id: String,
        name: String,
        account: String,
        category: String,
        amount: Long,
        day: Int,
        from: Int = 10,
        approx: Boolean = false,
        posts: Boolean = false,
    ): ScheduleSummary {
        val dates = (0 until 12).map { i -> ((from - 1 + i) / 12 + 2026) * 10000 + ((from - 1 + i) % 12 + 1) * 100 + day }
        return ScheduleSummary(
            id,
            name,
            account,
            account,
            name,
            amount,
            if (approx) "isapprox" else "is",
            category,
            dates.first(),
            completed = false,
            postsTransaction = posts,
            repeat = ScheduleRepeat.MONTHLY,
            interval = 1,
            dates = dates,
        )
    }

    private val pay =
        ScheduleSummary(
            "pay",
            "Employer",
            "checking",
            "Checking",
            "Employer",
            310_000,
            "is",
            "Paycheck",
            20261030,
            completed = false,
            postsTransaction = true,
            repeat = ScheduleRepeat.WEEKLY,
            interval = 2,
            dates = listOf(20261030, 20261113, 20261127, 20261211),
        )

    private val schedules =
        listOf(
            monthly("netflix", "Netflix", "Credit card", "Streaming", -1_549, 18, posts = true),
            monthly("phone", "Phone Co", "Credit card", "Phone", -6_500, 20),
            monthly("power", "Power Co", "Checking", "Power", -14_000, 24, approx = true),
            pay,
            monthly("rent", "Landlord", "Checking", "Rent", -145_000, 1, from = 11),
            ScheduleSummary(
                "car",
                "Car insurance",
                "checking",
                "Checking",
                "Acme Insurance",
                -61_200,
                "is",
                "Insurance",
                20270115,
                completed = false,
                postsTransaction = false,
                repeat = ScheduleRepeat.MONTHLY,
                interval = 6,
                dates = listOf(20270115, 20270715),
            ),
            ScheduleSummary(
                "gym",
                "Gym",
                "card",
                "Credit card",
                "Gym",
                -4_000,
                "is",
                "Fitness",
                20260901,
                completed = true,
                postsTransaction = false,
                repeat = ScheduleRepeat.MONTHLY,
                interval = 1,
                dates = emptyList(),
            ),
        )

    @Test
    fun upcomingLight() = schedulesSnap("schedules_upcoming_light", dark = false)

    @Test
    fun upcomingDark() = schedulesSnap("schedules_upcoming_dark", dark = true)

    @Test
    fun calendarLight() = schedulesSnap("schedules_calendar_light", dark = false, calendar = true)

    @Test
    fun calendarDark() = schedulesSnap("schedules_calendar_dark", dark = true, calendar = true)

    @Test
    fun allLight() = schedulesSnap("schedules_all_light", dark = false, section = SchedulesSection.ALL)

    @Test
    fun allDark() = schedulesSnap("schedules_all_dark", dark = true, section = SchedulesSection.ALL)

    @Test
    fun detailsLight() = detailsSnap("schedule_details_light", dark = false)

    @Test
    fun detailsDark() = detailsSnap("schedule_details_dark", dark = true)

    // The accounts pivot item's bar, opened: schedules sits beside add account and manage.
    @Test
    fun accountsBarLight() {
        val actions =
            ScreenBar
                .specsFor(ShellRoute.Home, hasBudget = true, mode = BudgetMode.ENVELOPE, homeSection = ScreenBar.HOME_ACCOUNTS)
                .map { ScreenAction(it.id, it.glyph, it.label, it.primary) {} }
        captureRoboImage("src/test/snapshots/app_bar_accounts_expanded_light.png") {
            MetroTheme(darkTheme = false) {
                Column(Modifier.width(390.dp)) {
                    MetroAppBar(actions, true, {}, windowInsets = WindowInsets(0))
                }
            }
        }
    }

    private fun schedulesSnap(
        name: String,
        dark: Boolean,
        section: Int = SchedulesSection.UPCOMING,
        calendar: Boolean = false,
    ) = snap(name, dark, ShellRoute.Schedules, SchedulesBar(section = section, calendar = calendar)) {
        SchedulesScreen(schedules, usd, today, section, calendar, {}, {}, {}, {}, {})
    }

    private fun detailsSnap(
        name: String,
        dark: Boolean,
    ) {
        val details =
            ScheduleDetails(
                schedules[1],
                listOf(
                    SchedulePosted("t1", 20260920, -6_500, "Phone"),
                    SchedulePosted("t2", 20260821, -6_500, "Phone"),
                ),
            )
        snap(name, dark, ShellRoute.ScheduleDetails("phone"), SchedulesBar()) {
            SectionTitled(SectionTitleCopy.SCHEDULES) { ScheduleDetailsScreen(details, usd) }
        }
    }

    private fun snap(
        name: String,
        dark: Boolean,
        route: ShellRoute,
        bar: SchedulesBar,
        content: @Composable () -> Unit,
    ) {
        val actions =
            ScreenBar
                .specsFor(route, hasBudget = true, mode = BudgetMode.ENVELOPE, schedules = bar)
                .map { ScreenAction(it.id, it.glyph, it.label, it.primary) {} }
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark) {
                Column(Modifier.width(390.dp).height(780.dp)) {
                    Box(Modifier.weight(1f)) { content() }
                    MetroAppBar(actions, false, {}, windowInsets = WindowInsets(0))
                }
            }
        }
    }
}
