package app.duenorth.budget

import app.duenorth.budget.ParityTestSupport.await
import app.duenorth.budget.ParityTestSupport.household
import app.duenorth.budget.core.AccountsCopy
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.SyncSchema
import app.duenorth.budget.core.UndoCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spec 010 T069, T070: the undo control on Home and Register, newest first, and conflicts. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class UndoContentTest {
    @Test
    fun registerAndHomeShowUndoOnlyWithAStep() {
        for (route in listOf(ShellRoute.Home, ShellRoute.Register("a"))) {
            assertFalse("undo" in ScreenBar.specsFor(route, true, BudgetMode.ENVELOPE).map { it.id })
            val undo = ScreenBar.specsFor(route, true, BudgetMode.ENVELOPE, UndoCopy.BANK_LINK).single { it.id == "undo" }
            assertEquals("undo bank link", undo.label)
        }
    }

    @Test
    fun theLabelNamesTheNewestStep() {
        val (model, library) = household("undo-order")
        val budgetId = model.state.value.settings.openBudgetId!!
        val accountId =
            model.state.value.shell!!
                .accounts
                .first()
                .id
        model.envelopes.openBudget("c-food")
        model.envelopes.saveBudget("250")
        await { model.state.value.undoLabel == UndoCopy.ASSIGN }
        library.renameAccount(budgetId, accountId, "Everyday")
        assertEquals(AccountsCopy.EDIT, library.undoLabel(budgetId))
        model.undo()
        await { model.state.value.undoLabel == UndoCopy.ASSIGN }
        model.undo()
        await { model.state.value.undoLabel == null }
    }

    @Test
    fun aConflictOpensTheConflictsList() {
        val (model, library) = household("undo-conflict")
        val budgetId = model.state.value.settings.openBudgetId!!
        val accountId =
            model.state.value.shell!!
                .accounts
                .first()
                .id
        library.renameAccount(budgetId, accountId, "Everyday")
        // Someone else renames it again before the undo.
        library.useDatabase(budgetId) { session -> SyncSchema.write(session, "accounts", accountId, "name", "Joint") }
        model.mutate { it.copy(undoLabel = library.undoLabel(budgetId)) }
        model.undo()
        await { model.state.value.route == ShellRoute.Conflicts }
        assertTrue(
            model.state.value.monthNotice!!
                .contains(MonthActionCopy.UNDO_CONFLICT),
        )
        await { model.state.value.undoLabel == null }
        // Nothing was written back.
        assertEquals(
            "Joint",
            library.useDatabase(budgetId) { session ->
                session.query("SELECT name FROM accounts WHERE id = ?", listOf(accountId)).single().str("name")
            },
        )
    }
}
