package app.duenorth.budget

import app.duenorth.budget.core.BudgetClock
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.CreateResult
import app.duenorth.budget.core.MockBudgets
import app.duenorth.budget.core.NetWorthSpan
import app.duenorth.budget.core.createFromMock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

/** The net worth graph's holder on its own, over a stand-in shell. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NetWorthHolderTest {
    @Test
    fun openingReadsTheLineThroughToday() {
        val host = host("net-worth-holder-open")
        NetWorthHolder(host).open()
        assertEquals(listOf<ShellRoute>(ShellRoute.NetWorth), host.pushed)
        val ui = host.state.value.netWorthGraph
        assertFalse(ui.loading)
        assertNotNull(ui.history)
        val history = ui.history!!
        assertEquals(NetWorthSpan.SIX_MONTHS, history.span)
        assertEquals(LocalDate.now(), history.last?.date)
        assertTrue(history.points.size > 1)
    }

    @Test
    fun pickingASpanReadsItAndKeepsIt() {
        val host = host("net-worth-holder-span")
        val graph = NetWorthHolder(host)
        graph.open()
        graph.setSpan(NetWorthSpan.ONE_MONTH)
        val ui = host.state.value.netWorthGraph
        assertEquals(NetWorthSpan.ONE_MONTH, ui.span)
        assertEquals(NetWorthSpan.ONE_MONTH, ui.history?.span)
        assertEquals(LocalDate.now().minusMonths(1), ui.history?.first?.date)
        graph.setSpan(NetWorthSpan.ALL)
        assertEquals(
            NetWorthSpan.ALL,
            host.state.value.netWorthGraph.history
                ?.span,
        )
    }

    @Test
    fun theOffBudgetChoiceIsTheSharedSetting() {
        val host = host("net-worth-holder-include")
        val graph = NetWorthHolder(host)
        graph.open()
        val before = host.state.value.netWorthGraph.history
        graph.setIncludeOffBudget(false)
        assertFalse(host.state.value.settings.netWorthIncludeOffBudget)
        assertEquals("both sides are already in the line, so nothing is read again", before, host.state.value.netWorthGraph.history)
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
