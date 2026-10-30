package app.duenorth.budget

import app.duenorth.budget.core.BudgetClock
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.CreateResult
import app.duenorth.budget.core.MockBudgets
import app.duenorth.budget.core.SyncCopy
import app.duenorth.budget.core.createFromMock
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** The envelopes holder on its own, over a stand-in shell. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EnvelopesHolderTest {
    @Test
    fun anAmountThatDoesNotReadIsRefusedBeforeAnyWrite() {
        val host = host("envelopes-holder-amount")
        val shell = host.state.value.shell!!
        val before = shell.groups
        val categoryId =
            shell.groups
                .flatMap { it.categories }
                .first()
                .id
        EnvelopesHolder(host).assign(categoryId, "twelve")
        assertEquals(SyncCopy.ENTER_AMOUNT, host.state.value.editError)
        assertEquals(
            before,
            host.state.value.shell!!
                .groups,
        )
    }

    @Test
    fun aMoveDuringEditModeChangesTheEditInstead() {
        val host = host("envelopes-holder-edit")
        host.mutate { it.copy(route = ShellRoute.MoveCategory("food"), budgetEdit = BudgetEdit()) }
        EnvelopesHolder(host).submitMove("rent", "10.00")
        assertEquals(listOf(Triple("food", "rent", "10.00")), host.budgetEditMoves)
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
