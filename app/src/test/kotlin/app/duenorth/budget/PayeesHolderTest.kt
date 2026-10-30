package app.duenorth.budget

import app.duenorth.budget.core.BudgetClock
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.CreateResult
import app.duenorth.budget.core.MockBudgets
import app.duenorth.budget.core.createFromMock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** The payees holder on its own, over a stand-in shell. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PayeesHolderTest {
    @Test
    fun showOpensThePageWithTheSavedPayees() {
        val host = host("payees-holder-show")
        host.mutate { it.copy(payees = PayeesState(mergeTarget = "old")) }
        PayeesHolder(host).show()
        val state = host.state.value
        assertEquals(ShellRoute.Payees, state.route)
        assertEquals(null, state.payees.mergeTarget)
        assertTrue("the household sample has payees", state.payees.rows.isNotEmpty())
    }

    @Test
    fun renamingOntoAnotherPayeeAsksBeforeMerging() {
        val host = host("payees-holder-merge")
        val payees = PayeesHolder(host)
        payees.load()
        val (first, second) =
            host.state.value.payees.rows
                .filter { !it.transfer }
                .take(2)
        payees.rename(first.id, second.name, confirmMerge = false, rememberRule = false)
        assertEquals(second.id, host.state.value.payees.mergeTarget)
        payees.cancelMerge()
        assertEquals(null, host.state.value.payees.mergeTarget)
    }

    private fun host(name: String): FakeShellHost {
        val root =
            File(RuntimeEnvironment.getApplication().filesDir, name).apply {
                deleteRecursively()
                mkdirs()
            }
        val library = BudgetLibrary(root, AndroidSessionOpener(), BudgetClock.System)
        (library.createFromMock(MockBudgets.HOUSEHOLD) as CreateResult.Created).id
        return FakeShellHost(library, ShellUiState(settings = library.settings()))
    }
}
