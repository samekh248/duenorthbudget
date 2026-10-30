package app.duenorth.budget

import app.duenorth.budget.core.BudgetClock
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.RegisterSearch
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** The register and reconcile holders on their own, over a stand-in shell. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RegisterHolderTest {
    @Test
    fun unpickingTheLastTransactionLeavesSelectMode() {
        val host = host("register-holder-select")
        val register = RegisterHolder(host, InboxHolder(host))
        register.startSelecting("a")
        register.toggleSelected("b")
        assertEquals(setOf("a", "b"), host.state.value.registerUi.selection)
        register.toggleSelected("a")
        register.toggleSelected("b")
        assertEquals(null, host.state.value.registerUi.selection)
    }

    @Test
    fun searchOnlyChangesWhileOpen() {
        val host = host("register-holder-search")
        val register = RegisterHolder(host, InboxHolder(host))
        register.setSearch(RegisterSearch(query = "coffee"))
        assertEquals(null, host.state.value.registerUi.search)
        register.toggleSearch()
        register.setSearch(RegisterSearch(query = "coffee"))
        assertEquals(
            "coffee",
            host.state.value.registerUi.search
                ?.query,
        )
        register.closeSearch()
        assertEquals(null, host.state.value.registerUi.search)
    }

    @Test
    fun addMissingWithoutAReconcileIsAPlainNewTransaction() {
        val host = host("register-holder-reconcile")
        host.mutate { it.copy(route = ShellRoute.Register("checking")) }
        val register = RegisterHolder(host, InboxHolder(host))
        ReconcileHolder(host, register).addMissing()
        assertEquals(listOf<ShellRoute>(ShellRoute.EditTransaction("checking", null)), host.pushed)
        assertEquals(null, host.state.value.newTransactionPrefill)
    }

    private fun host(name: String): FakeShellHost {
        val root =
            File(RuntimeEnvironment.getApplication().filesDir, name).apply {
                deleteRecursively()
                mkdirs()
            }
        return FakeShellHost(BudgetLibrary(root, AndroidSessionOpener(), BudgetClock.System), ShellUiState())
    }
}
