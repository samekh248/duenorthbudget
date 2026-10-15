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

    @Test
    fun pivotsCarryTheirOwnTitle() {
        assertNull(title(ShellRoute.Home))
        assertNull(title(ShellRoute.Settings))
        assertNull(title(ShellRoute.Reports))
        assertNull(title(ShellRoute.Welcome))
    }

    @Test
    fun topLevelViewsGetNone() {
        assertNull(title(ShellRoute.Accounts, homeSection = 1))
        assertNull(title(ShellRoute.Rules))
        assertNull(title(ShellRoute.Payees, settingsReturn = true))
        assertNull(title(ShellRoute.Templates))
        assertNull(title(ShellRoute.ManageCategories))
        assertNull(title(ShellRoute.Budgets))
    }

    @Test
    fun drillInsNameTheirSection() {
        assertEquals("ACCOUNTS", title(ShellRoute.Register("a1")))
        assertEquals("ACCOUNTS", title(ShellRoute.Reconcile("a1"), homeSection = 3))
        assertEquals("BUDGET", title(ShellRoute.CategoryBudget("c1"), homeSection = 1))
        assertEquals("DUE", title(ShellRoute.ScheduleEditor(null)))
        assertEquals("REVIEW", title(ShellRoute.ReviewCategory("c1")))
        assertEquals("RULES", title(ShellRoute.RuleEditor(null)))
        assertEquals("ACCOUNTS", title(ShellRoute.EditAccount("a1")))
        assertEquals("DUE NORTH BUDGET", title(ShellRoute.Server))
    }

    @Test
    fun sharedPagesFollowTheHomeSectionShowing() {
        assertEquals("ACCOUNTS", title(ShellRoute.EditTransaction("a1", "t1"), homeSection = 1))
        assertEquals("INBOX", title(ShellRoute.EditTransaction("a1", "t1"), homeSection = 5))
    }

    @Test
    fun pagesOpenedFromSettingsSaySettings() {
        assertEquals("SETTINGS", title(ShellRoute.EncryptionPassword(EncryptionMode.entries.first()), settingsReturn = true))
        assertEquals("SETTINGS", title(ShellRoute.Create, settingsReturn = true))
    }
}
