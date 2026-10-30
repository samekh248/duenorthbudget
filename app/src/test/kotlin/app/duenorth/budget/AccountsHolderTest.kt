package app.duenorth.budget

import app.duenorth.budget.core.BudgetClock
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.CreateResult
import app.duenorth.budget.core.SyncCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** The accounts holder on its own, over a stand-in shell. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AccountsHolderTest {
    @Test
    fun aBalanceThatDoesNotReadStaysOnTheForm() {
        val host = host("accounts-holder-balance")
        AccountsHolder(host).submitAdd("Checking", offBudget = false, balanceText = "twelve")
        assertEquals(SyncCopy.ENTER_AMOUNT, host.state.value.editError)
        assertEquals(0, host.backs)
    }

    @Test
    fun anAddedAccountLeavesTheFormAndReloads() {
        val host = host("accounts-holder-add")
        AccountsHolder(host).submitAdd("Checking", offBudget = false, balanceText = "12.50")
        assertEquals(1, host.backs)
        assertTrue(host.reloads > 0)
        assertTrue(
            host.state.value.accounts.list
                .any { it.name == "Checking" },
        )
    }

    @Test
    fun reorderToggles() {
        val host = host("accounts-holder-reorder")
        val accounts = AccountsHolder(host)
        accounts.toggleReorder()
        assertTrue(host.state.value.accounts.reorder)
    }

    private fun host(name: String): FakeShellHost {
        val root =
            File(RuntimeEnvironment.getApplication().filesDir, name).apply {
                deleteRecursively()
                mkdirs()
            }
        val library = BudgetLibrary(root, AndroidSessionOpener(), BudgetClock.System)
        val id = (library.create("Home", "USD") as CreateResult.Created).id
        return FakeShellHost(library, ShellUiState(settings = library.settings(), shell = library.readShell(id)))
    }
}
