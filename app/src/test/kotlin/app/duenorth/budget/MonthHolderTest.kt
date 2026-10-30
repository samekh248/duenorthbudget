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

/** The month actions holder on its own, over a stand-in shell. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MonthHolderTest {
    @Test
    fun anAmountThatDoesNotReadIsNotMoved() {
        val host = host("month-holder-amount")
        val category =
            host.state.value.shell!!
                .groups
                .flatMap { it.categories }
                .first()
        MonthHolder(host).transferToBudget(category.id, "lots")
        assertEquals(SyncCopy.ENTER_AMOUNT, host.state.value.editError)
        assertEquals(0, host.backs)
    }

    @Test
    fun undoWithNothingToUndoSaysNothing() {
        val host = host("month-holder-undo")
        MonthHolder(host).undo()
        assertEquals(null, host.state.value.monthUi.notice)
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
