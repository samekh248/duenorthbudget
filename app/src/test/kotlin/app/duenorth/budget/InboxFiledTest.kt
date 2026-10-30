package app.duenorth.budget

import app.duenorth.budget.ParityTestSupport.await
import app.duenorth.budget.ParityTestSupport.household
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.InboxRow
import app.duenorth.budget.core.MonthShell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth

/** A filed inbox row stays filed until the reload has it gone, so the next row never flickers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class InboxFiledTest {
    @Test
    fun aConfirmationThatEndsBeforeTheWriteKeepsTheRowFiled() {
        val (model, _) = household("inbox-filed-early")
        val row =
            model.state.value.shell!!
                .inbox
                .firstOrNull { it.suggestionId != null }
        assertNotNull("the sample needs an inbox row with a suggestion", row)
        model.inbox.accept(row!!.id)
        // The confirmation plays out before the write and its reload have landed.
        model.inbox.filedShown(row.id)
        assertTrue(
            "a row still listed must stay filed, or it shows again as waiting",
            model.state.value.inbox.filed
                .any { it.row.id == row.id },
        )
        await {
            model.state.value.shell!!
                .inbox
                .none { it.id == row.id } &&
                model.state.value.inbox.filed
                    .isEmpty()
        }
    }

    @Test
    fun aConfirmationThatEndsAfterTheWriteLeaves() {
        val (model, _) = household("inbox-filed-late")
        val row =
            model.state.value.shell!!
                .inbox
                .first { it.suggestionId != null }
        model.inbox.accept(row.id)
        await {
            model.state.value.shell!!
                .inbox
                .none { it.id == row.id }
        }
        model.inbox.filedShown(row.id)
        await {
            model.state.value.inbox.filed
                .isEmpty()
        }
    }

    @Test
    fun theQueueLeadsWithTheRowAMatchLeft() {
        val rows = listOf("a", "b", "c").map { InboxRow(it, it.uppercase(), -100, 20261015, "visa", "Visa", "") }
        val shell =
            MonthShell(
                budgetId = "b",
                budgetName = "Home",
                mode = BudgetMode.ENVELOPE,
                currency = Currencies.byCode("USD")!!,
                month = YearMonth.of(2026, 10),
                headerLabel = "to budget",
                headerMinor = 0,
                groups = emptyList(),
                accounts = emptyList(),
                inbox = rows,
            )
        assertEquals(listOf("c", "a", "b"), inboxQueue(shell, "c").map { it.id })
        // Filed at its place in that order, the lead comes back to the top until it leaves.
        val filed = InboxFiled(rows[2], 0, "Dining")
        val entries = inboxEntries(inboxQueue(shell.copy(inbox = rows.take(2)), "c"), listOf(filed))
        assertEquals(listOf("c", "a", "b"), entries.map { it.row.id })
    }
}
