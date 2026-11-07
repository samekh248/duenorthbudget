package app.duenorth.budget

import app.duenorth.budget.ParityTestSupport.await
import app.duenorth.budget.ParityTestSupport.household
import app.duenorth.budget.core.ScheduleDraft
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Undo picked on a page that keeps its own list shows what it put back there. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class UndoRepaintTest {
    @Test
    fun undoOnRulesRemovesTheRuleFromTheList() {
        val (model, library) = household("audit-rules")
        val budgetId = model.state.value.settings.openBudgetId!!
        model.rules.open()
        await { model.state.value.route == ShellRoute.Rules }
        val before = model.state.value.rules.list.size
        val category =
            model.state.value.shell!!
                .groups
                .flatMap { it.categories }
                .first()
                .id
        val payee = library.listPayees(budgetId).first { !it.transfer }.id
        library.makeCategoryRule(budgetId, payee, category)
        model.rules.open()
        await { model.state.value.rules.list.size == before + 1 }
        model.mutate { it.copy(undoLabel = library.undoLabel(budgetId)) }
        model.month.undo()
        await { model.state.value.undoLabel == null }
        await { model.state.value.rules.list.size == before }
        assertEquals(before, library.listRules(budgetId).size)
    }

    @Test
    fun undoOnSchedulesRemovesTheScheduleFromTheList() {
        val (model, library) = household("audit-schedules")
        val budgetId = model.state.value.settings.openBudgetId!!
        model.schedules.open()
        await { model.state.value.route == ShellRoute.Schedules }
        val before = model.state.value.schedules.size
        val account =
            model.state.value.shell!!
                .accounts
                .first()
                .id
        library.saveSchedule(
            budgetId,
            ScheduleDraft(name = "Audit", accountId = account, payeeName = "Audit", amountMinor = -100, startIso = "2026-12-01"),
        )
        model.refresh()
        await { model.state.value.schedules.size == before + 1 }
        model.mutate { it.copy(undoLabel = library.undoLabel(budgetId)) }
        model.month.undo()
        await { model.state.value.undoLabel == null }
        await { model.state.value.schedules.size == before }
        assertEquals(before, library.readSchedules(budgetId).size)
    }
}
