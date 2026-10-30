package app.duenorth.budget

import app.duenorth.budget.core.BudgetClock
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.CreateResult
import app.duenorth.budget.core.MockBudgets
import app.duenorth.budget.core.ReportPreset
import app.duenorth.budget.core.SpendingGroupBy
import app.duenorth.budget.core.createFromMock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** The reports holder on its own, over a stand-in shell. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReportsHolderTest {
    @Test
    fun openingAReportReadsItsRange() {
        val host = host("reports-holder-open")
        val reports = ReportsHolder(host)
        reports.open(ReportItems.SPENDING)
        assertEquals(listOf<ShellRoute>(ShellRoute.ReportPivot(ReportItems.SPENDING)), host.pushed)
        assertNotNull(host.state.value.reports.page)
        assertFalse(host.state.value.reports.loading)
        reports.setGroupBy(SpendingGroupBy.PAYEE)
        assertEquals(SpendingGroupBy.PAYEE, host.state.value.reports.groupBy)
    }

    @Test
    fun aCustomPresetWaitsForItsRange() {
        val host = host("reports-holder-custom")
        val reports = ReportsHolder(host)
        reports.setPreset(ReportPreset.CUSTOM)
        assertEquals(ReportPreset.CUSTOM, host.state.value.reports.preset)
        assertEquals("no range yet, so nothing is read", null, host.state.value.reports.page)
    }

    private fun host(name: String): FakeShellHost {
        val root =
            File(RuntimeEnvironment.getApplication().filesDir, name).apply {
                deleteRecursively()
                mkdirs()
            }
        val library = BudgetLibrary(root, AndroidSessionOpener(), BudgetClock.System)
        val id = (library.createFromMock(MockBudgets.HOUSEHOLD) as CreateResult.Created).id
        return FakeShellHost(library, ShellUiState(settings = library.settings(), shell = library.readShell(id)))
    }
}
