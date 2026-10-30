package app.duenorth.budget

import androidx.lifecycle.viewModelScope
import app.duenorth.budget.core.PayeeWriteResult
import app.duenorth.budget.core.ScheduleWriteResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Payees and the schedule actions taken from a register or an upcoming list.

fun ShellViewModel.showPayees() {
    history.clear()
    mutate { it.copy(route = ShellRoute.Payees, writeError = null, payeeMergeTarget = null) }
    loadPayees()
}

/** Reads the saved payees, which the payees page lists and every payee box offers as you type. */
internal fun ShellViewModel.loadPayees() {
    viewModelScope.launch {
        val budgetId = state.value.settings.openBudgetId ?: return@launch
        val rows = withContext(io) { library.listPayees(budgetId) }
        mutate { it.copy(payees = rows) }
    }
}

fun ShellViewModel.postSchedule(scheduleId: String) {
    launchSchedule(scheduleId, forceDuplicate = false)
}

fun ShellViewModel.confirmScheduleDuplicate() {
    val id = state.value.scheduleDuplicateId ?: return
    mutate { it.copy(scheduleDuplicateId = null) }
    launchSchedule(id, forceDuplicate = true)
}

fun ShellViewModel.cancelScheduleDuplicate() {
    mutate { it.copy(scheduleDuplicateId = null) }
}

fun ShellViewModel.skipSchedule(scheduleId: String) {
    val budgetId = state.value.settings.openBudgetId ?: return
    viewModelScope.launch {
        when (withContext(io) { library.skipSchedule(budgetId, scheduleId) }) {
            is ScheduleWriteResult.Skipped,
            is ScheduleWriteResult.Posted,
            -> refresh()
            is ScheduleWriteResult.Rejected -> Unit
            is ScheduleWriteResult.ConfirmDuplicate -> Unit
        }
    }
}

fun ShellViewModel.createScheduleFromTransaction(transactionId: String) {
    val budgetId = state.value.settings.openBudgetId ?: return
    val next = state.value.today
    viewModelScope.launch {
        withContext(io) {
            library.createScheduleFromTransaction(budgetId, transactionId, "monthly", next)
        }
        refresh()
    }
}

fun ShellViewModel.renamePayee(
    payeeId: String,
    name: String,
    confirmMerge: Boolean,
    rememberRule: Boolean,
) {
    val budgetId = state.value.settings.openBudgetId ?: return
    viewModelScope.launch {
        when (
            val result =
                withContext(io) {
                    library.renamePayee(budgetId, payeeId, name, confirmMerge, rememberRule)
                }
        ) {
            is PayeeWriteResult.Saved -> {
                val rows = withContext(io) { library.listPayees(budgetId) }
                mutate { it.copy(payees = rows, writeError = null, payeeMergeTarget = null) }
            }
            is PayeeWriteResult.Rejected ->
                mutate { it.copy(writeError = result.reason) }
            is PayeeWriteResult.ConfirmMerge ->
                mutate { it.copy(payeeMergeTarget = result.targetId, writeError = null) }
        }
    }
}

fun ShellViewModel.confirmPayeeMerge(
    payeeId: String,
    name: String,
) {
    renamePayee(payeeId, name, confirmMerge = true, rememberRule = false)
}

fun ShellViewModel.cancelPayeeMerge() {
    mutate { it.copy(payeeMergeTarget = null) }
}

private fun ShellViewModel.launchSchedule(
    scheduleId: String,
    forceDuplicate: Boolean,
) {
    val budgetId = state.value.settings.openBudgetId ?: return
    viewModelScope.launch {
        when (
            withContext(io) {
                library.postSchedule(budgetId, scheduleId, forceDuplicate)
            }
        ) {
            is ScheduleWriteResult.Posted -> refresh()
            is ScheduleWriteResult.ConfirmDuplicate ->
                mutate { it.copy(scheduleDuplicateId = scheduleId) }
            is ScheduleWriteResult.Rejected -> Unit
            is ScheduleWriteResult.Skipped -> refresh()
        }
    }
}
