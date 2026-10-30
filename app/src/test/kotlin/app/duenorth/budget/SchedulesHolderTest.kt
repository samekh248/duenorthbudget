package app.duenorth.budget

import app.duenorth.budget.core.BudgetClock
import app.duenorth.budget.core.BudgetLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** The schedules and rules holders on their own, over a stand-in shell. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SchedulesHolderTest {
    @Test
    fun openingSchedulesStartsOnUpcomingAndReloads() {
        val host = host("schedules-holder-open")
        val schedules = SchedulesHolder(host, RulesHolder(host))
        schedules.setSection(SchedulesSection.ALL)
        schedules.toggleCalendar()
        schedules.open()
        assertEquals(SchedulesSection.UPCOMING, host.state.value.schedulesUi.section)
        assertTrue(host.state.value.schedulesUi.calendar)
        assertEquals(listOf<ShellRoute>(ShellRoute.Schedules), host.pushed)
        assertEquals(1, host.reloads)
    }

    @Test
    fun keepingADuplicateDropsTheQuestion() {
        val host = host("schedules-holder-duplicate")
        host.mutate { it.copy(schedulesUi = it.schedulesUi.copy(duplicateId = "s1")) }
        SchedulesHolder(host, RulesHolder(host)).cancelDuplicate()
        assertEquals(null, host.state.value.schedulesUi.duplicateId)
    }

    @Test
    fun aNewRuleStartsBlank() {
        val host = host("rules-holder-new")
        RulesHolder(host).openNew()
        assertEquals(listOf<ShellRoute>(ShellRoute.RuleEditor(null)), host.pushed)
        assertEquals(
            null,
            host.state.value.rules.draft!!
                .id,
        )
    }

    private fun host(name: String): FakeShellHost {
        val root =
            File(RuntimeEnvironment.getApplication().filesDir, name).apply {
                deleteRecursively()
                mkdirs()
            }
        val library = BudgetLibrary(root, AndroidSessionOpener(), BudgetClock.System)
        return FakeShellHost(library, ShellUiState(settings = library.settings()))
    }
}
