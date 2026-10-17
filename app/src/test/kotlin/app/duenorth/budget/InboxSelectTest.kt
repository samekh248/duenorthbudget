package app.duenorth.budget

import app.duenorth.budget.ParityTestSupport.await
import app.duenorth.budget.ParityTestSupport.household
import app.duenorth.budget.core.UndoCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The inbox's select mode: check rows, then file them all with one category as one undo step. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class InboxSelectTest {
    @Test
    fun categorizeFilesEveryCheckedRowAsOneStep() {
        val (model, _) = household("inbox-select")
        val inbox =
            model.state.value.shell!!
                .inbox
        assertTrue("the sample needs two inbox rows", inbox.size >= 2)
        val picked = inbox.take(2).map { it.id }
        model.startInboxSelecting()
        picked.forEach(model::toggleInboxSelected)
        assertEquals(picked.toSet(), model.state.value.inboxSelection)
        model.categorizeSelectedInbox()
        await { model.state.value.categoryTarget != null }
        val route = model.state.value.route as ShellRoute.PickCategory
        assertEquals(picked, listOf(route.transactionId) + route.others)
        val target = model.state.value.categoryTarget!!
        assertEquals("2 ${InboxCopy.TRANSACTIONS}", target.payee)
        assertEquals(inbox.take(2).sumOf { it.amountMinor }, target.amountMinor)
        model.submitCategory(target.categories.first { !it.income }.id)
        await {
            model.state.value.route == ShellRoute.Home &&
                model.state.value.shell!!
                    .inbox
                    .none { it.id in picked }
        }
        assertEquals(null, model.state.value.inboxSelection)
        assertEquals(UndoCopy.CATEGORIZE, model.state.value.undoLabel)
    }

    @Test
    fun backLeavesSelectMode() {
        val (model, _) = household("inbox-select-back")
        model.startInboxSelecting()
        assertTrue(model.back())
        assertEquals(null, model.state.value.inboxSelection)
    }
}
