package app.duenorth.budget

import app.duenorth.budget.ParityTestSupport.await
import app.duenorth.budget.ParityTestSupport.household
import app.duenorth.budget.core.RegisterRow
import app.duenorth.budget.core.TransactionDraft
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/** Saving the transaction form goes back to the page that opened it, and back from there still leads where it did. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SaveReturnsTest {
    @Test
    fun anEditFromTheRegisterGoesBackToTheRegister() {
        val (model, _) = household("save-register-edit")
        val accountId = openFirstAccount(model)
        val row =
            model.state.value.register!!
                .rows
                .first { !it.parent && it.transferId == null }
        model.openEdit(row.id)
        save(model, edited(row, accountId))
        assertEquals(ShellRoute.Register(accountId), model.state.value.route)
        model.back()
        assertEquals(ShellRoute.Home, model.state.value.route)
    }

    @Test
    fun aNewTransactionFromTheRegisterGoesBackToTheRegister() {
        val (model, _) = household("save-register-new")
        val accountId = openFirstAccount(model)
        model.openNewTransaction()
        save(model, fresh(accountId))
        assertEquals(ShellRoute.Register(accountId), model.state.value.route)
        model.back()
        assertEquals(ShellRoute.Home, model.state.value.route)
    }

    @Test
    fun aNewTransactionFromHomeGoesBackHome() {
        val (model, _) = household("save-home-new")
        model.showAddTransaction()
        val accountId = (model.state.value.route as ShellRoute.EditTransaction).accountId
        await { model.state.value.register != null }
        save(model, fresh(accountId))
        assertEquals(ShellRoute.Home, model.state.value.route)
    }

    @Test
    fun anInboxRowGoesBackToTheInbox() {
        val (model, _) = household("save-inbox")
        val inbox =
            model.state.value.shell!!
                .inbox
                .first()
        model.inbox.openDetails(inbox.id)
        await {
            model.state.value.register
                ?.rows
                ?.any { it.id == inbox.id } == true
        }
        val row =
            model.state.value.register!!
                .rows
                .first { it.id == inbox.id }
        save(model, edited(row, inbox.accountId))
        assertEquals(ShellRoute.Home, model.state.value.route)
    }

    @Test
    fun anEditFromAReconcileGoesBackToTheReconcile() {
        val (model, _) = household("save-reconcile")
        val accountId = openFirstAccount(model)
        model.openReconcile()
        val row =
            model.state.value.register!!
                .rows
                .first { !it.parent && it.transferId == null && !it.reconciled }
        model.openEdit(row.id)
        save(model, edited(row, accountId))
        assertEquals(ShellRoute.Reconcile(accountId), model.state.value.route)
        model.back()
        assertEquals(ShellRoute.Register(accountId), model.state.value.route)
    }

    @Test
    fun aTransactionMovedToAnotherAccountStillGoesBackToTheRegisterItCameFrom() {
        val (model, _) = household("save-register-move")
        val accountId = openFirstAccount(model)
        val other = otherAccount(model, accountId)
        val row =
            model.state.value.register!!
                .rows
                .first { !it.parent && it.transferId == null }
        model.openEdit(row.id)
        save(model, edited(row, other).copy(categoryId = null))
        assertEquals(ShellRoute.Register(accountId), model.state.value.route)
        await {
            model.state.value.register
                ?.rows
                ?.none { it.id == row.id } == true
        }
        model.back()
        assertEquals(ShellRoute.Home, model.state.value.route)
    }

    @Test
    fun aNewTransactionPutInAnotherAccountGoesBackToTheRegisterItCameFrom() {
        val (model, _) = household("save-register-new-move")
        val accountId = openFirstAccount(model)
        model.openNewTransaction()
        save(model, fresh(otherAccount(model, accountId)))
        assertEquals(ShellRoute.Register(accountId), model.state.value.route)
        assertEquals(
            accountId,
            model.state.value.register
                ?.account
                ?.id,
        )
    }

    @Test
    fun aTransactionMovedOutOfAReconcileGoesBackToTheReconcile() {
        val (model, _) = household("save-reconcile-move")
        val accountId = openFirstAccount(model)
        model.openReconcile()
        val row =
            model.state.value.register!!
                .rows
                .first { !it.parent && it.transferId == null && !it.reconciled }
        model.openEdit(row.id)
        save(model, edited(row, otherAccount(model, accountId)).copy(categoryId = null))
        assertEquals(ShellRoute.Reconcile(accountId), model.state.value.route)
    }

    private fun otherAccount(
        model: ShellViewModel,
        accountId: String,
    ): String =
        model.state.value.shell!!
            .accounts
            .first { !it.closed && it.id != accountId }
            .id

    private fun openFirstAccount(model: ShellViewModel): String {
        val accountId =
            model.state.value.shell!!
                .accounts
                .first { !it.closed && !it.offBudget }
                .id
        model.openAccount(accountId)
        await {
            model.state.value.register
                ?.account
                ?.id == accountId
        }
        return accountId
    }

    private fun save(
        model: ShellViewModel,
        draft: TransactionDraft,
    ) {
        val form = model.state.value.route
        model.submitTransaction(draft)
        await { model.state.value.route != form || model.state.value.writeError != null || model.state.value.confirm != null }
        assertEquals(null, model.state.value.writeError)
    }

    private fun edited(
        row: RegisterRow,
        accountId: String,
    ) = TransactionDraft(row.id, accountId, row.date, row.payee, row.amountMinor, row.categoryId, "${row.note} edited")

    private fun fresh(accountId: String) =
        TransactionDraft(UUID.randomUUID().toString(), accountId, 20261015, "Corner shop", -1_250, null, "")
}
