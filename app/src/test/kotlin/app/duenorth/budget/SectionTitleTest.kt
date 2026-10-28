package app.duenorth.budget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SectionTitleTest {
    private fun title(
        route: ShellRoute,
        homeSection: Int = 0,
        settingsReturn: Boolean = false,
    ) = SectionTitle.of(ShellUiState(route = route, homeSection = homeSection, settingsReturn = settingsReturn))

    private fun section(name: String) = HomeSections.headers.indexOf(name)

    @Test
    fun pivotsCarryTheirOwnTitle() {
        assertNull(title(ShellRoute.Home))
        assertNull(title(ShellRoute.Settings))
        assertNull(title(ShellRoute.ReportPivot(ReportItems.CASH_FLOW)))
        assertNull(title(ShellRoute.Welcome))
    }

    @Test
    fun topLevelViewsGetNone() {
        assertNull(title(ShellRoute.Rules))
        assertNull(title(ShellRoute.Payees, settingsReturn = true))
        assertNull(title(ShellRoute.Templates))
        assertNull(title(ShellRoute.ManageCategories))
        assertNull(title(ShellRoute.Schedules))
    }

    @Test
    fun drillInsNameTheirSection() {
        assertEquals("ACCOUNTS", title(ShellRoute.Register("a1")))
        assertEquals("ACCOUNTS", title(ShellRoute.Reconcile("a1"), homeSection = section(HomeSections.REPORTS)))
        assertEquals("BUDGET", title(ShellRoute.CategoryBudget("c1"), homeSection = section(HomeSections.ACCOUNTS)))
        assertEquals("SCHEDULES", title(ShellRoute.ScheduleEditor(null)))
        assertEquals("SCHEDULES", title(ShellRoute.ScheduleDetails("s1")))
        assertEquals("REPORTS", title(ShellRoute.ReviewCategory("c1")))
        assertEquals("REPORTS", title(ShellRoute.MonthReview))
        assertEquals("RULES", title(ShellRoute.RuleEditor(null)))
        assertEquals("REPORTS", title(ShellRoute.NetWorth))
        assertEquals("ACCOUNTS", title(ShellRoute.EditAccount("a1")))
        assertEquals("DUE NORTH BUDGET", title(ShellRoute.Server))
    }

    @Test
    fun sharedPagesFollowTheHomeSectionShowing() {
        assertEquals("ACCOUNTS", title(ShellRoute.EditTransaction("a1", "t1"), homeSection = section(HomeSections.ACCOUNTS)))
        assertEquals("INBOX", title(ShellRoute.EditTransaction("a1", "t1"), homeSection = section(HomeSections.INBOX)))
    }

    @Test
    fun pagesOpenedFromSettingsSaySettings() {
        assertEquals("SETTINGS", title(ShellRoute.EncryptionPassword(EncryptionMode.entries.first()), settingsReturn = true))
        assertEquals("SETTINGS", title(ShellRoute.Create, settingsReturn = true))
    }
}
