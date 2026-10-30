package app.duenorth.budget

import app.duenorth.budget.core.BudgetClock
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.CreateResult
import app.duenorth.budget.core.MockBudgets
import app.duenorth.budget.core.SyncCopy
import app.duenorth.budget.core.createFromMock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** The budget edit holder on its own, over a stand-in shell. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BudgetEditHolderTest {
    @Test
    fun typedAmountsStayInTheSandboxUntilCancelled() {
        val host = host("budget-edit-holder-cancel")
        val edit = BudgetEditHolder(host, EnvelopesHolder(host))
        val category = firstCategory(host)
        edit.start()
        edit.focusBox(category)
        "42".forEach { edit.typeKey(it.toString()) }
        edit.closeKeypad()
        edit.focusBox(category)
        edit.typeKey("7")
        edit.doneBox()
        assertEquals(
            setOf(category),
            host.state.value.budgetEdit!!
                .budgeted.keys,
        )
        edit.cancel()
        assertNull(host.state.value.budgetEdit)
    }

    @Test
    fun typingThatDoesNotReadKeepsTheKeypadOpen() {
        val host = host("budget-edit-holder-bad")
        val edit = BudgetEditHolder(host, EnvelopesHolder(host))
        val category = firstCategory(host)
        edit.start()
        edit.focusBox(category)
        edit.typeKey("+")
        assertFalse(edit.doneBox())
        assertEquals(SyncCopy.ENTER_AMOUNT, host.state.value.editError)
        assertEquals(
            category,
            host.state.value.budgetEdit!!
                .categoryId,
        )
    }

    private fun firstCategory(host: FakeShellHost) =
        host.state.value.shell!!
            .groups
            .flatMap { it.categories }
            .first()
            .id

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
