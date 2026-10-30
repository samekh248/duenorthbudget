package app.duenorth.budget

import androidx.lifecycle.viewModelScope
import app.duenorth.budget.core.InboxRow
import app.duenorth.budget.core.MatchPair
import app.duenorth.budget.core.WriteResult
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// The inbox: filing, matching and deleting what came in, one row or a selection at a time.

/** Accept: files the row under the category its payee last went to, or opens the picker when there is none. */
fun ShellViewModel.acceptInbox(transactionId: String) {
    val inbox = shownInbox() ?: return
    val index = inbox.indexOfFirst { it.id == transactionId }
    val row = inbox.getOrNull(index) ?: return
    val categoryId = row.suggestionId
    if (categoryId == null) {
        openInbox(transactionId)
        return
    }
    fileInbox(row, index, categoryId, row.suggestionName, fromPicker = false)
}

/** A tap on an inbox row opens its transaction, as the register does. */
fun ShellViewModel.openInboxDetails(transactionId: String) {
    openFromInbox(transactionId) { accountId -> ShellRoute.EditTransaction(accountId, transactionId) }
}

fun ShellViewModel.deleteInbox(transactionId: String) {
    val row =
        state.value.shell
            ?.inbox
            ?.firstOrNull { it.id == transactionId } ?: return
    launchWrite { id ->
        val result = withContext(io) { library.deleteTransaction(id, transactionId, false) }
        publish(result, row.accountId, stayOnRegister = false)
    }
}

/**
 * Match: keeps the bank's transaction with the entry's payee, category and note, and deletes the
 * entry. When neither side had a category the one left leads the inbox, ready to file.
 */
fun ShellViewModel.matchTransactions(
    pair: MatchPair,
    stayOnRegister: Boolean,
) {
    launchWrite { id ->
        val result = withContext(io) { library.matchTransactions(id, pair.bank.id, pair.entered.id) }
        if (result is WriteResult.Saved) mutate { it.copy(inboxLead = pair.bank.id) }
        publish(result, pair.bank.accountId, stayOnRegister)
    }
}

fun ShellViewModel.notAMatch(
    pair: MatchPair,
    stayOnRegister: Boolean,
) {
    launchWrite { id ->
        val result = withContext(io) { library.notAMatch(id, pair.bank.id, pair.entered.id) }
        publish(result, pair.bank.accountId, stayOnRegister)
    }
}

private fun ShellViewModel.openFromInbox(
    transactionId: String,
    route: (String) -> ShellRoute,
) {
    val row =
        state.value.shell
            ?.inbox
            ?.firstOrNull { it.id == transactionId } ?: return
    push(route(row.accountId))
    mutate { it.copy(editFromHome = true, registerSelection = null) }
    loadPayees()
    viewModelScope.launch { apply(load(row.accountId), row.accountId) }
}

fun ShellViewModel.inboxFiledShown(transactionId: String) {
    // The confirmation can end before the write and its reload land. Letting the row go then
    // would show it again, still waiting, until the reload swaps in the next one.
    if (transactionId in inboxWriting) {
        inboxPlayed += transactionId
        return
    }
    mutate { state -> state.copy(inboxFiled = state.inboxFiled.filterNot { it.row.id == transactionId }) }
}

/** The inbox rows in the order the inbox shows them, so a filed row's index is the place it left. */
internal fun ShellViewModel.shownInbox(): List<InboxRow>? = state.value.shell?.let { inboxQueue(it, state.value.inboxLead) }

/** The inbox bar's select: every row gets a check box, and the bar acts on the checked ones. */
fun ShellViewModel.startInboxSelecting() {
    mutate { it.copy(inboxSelection = emptySet()) }
}

fun ShellViewModel.toggleInboxSelected(transactionId: String) {
    mutate {
        val picked = it.inboxSelection ?: return@mutate it
        it.copy(inboxSelection = if (transactionId in picked) picked - transactionId else picked + transactionId)
    }
}

fun ShellViewModel.endInboxSelecting() {
    mutate { it.copy(inboxSelection = null) }
}

/**
 * Files every checked row that has a suggestion under it, as one undo step. Rows without a
 * suggestion stay checked, so categorize can file them next.
 */
fun ShellViewModel.acceptSelectedInbox() {
    val picked = state.value.inboxSelection ?: return
    val rows =
        shownInbox()
            .orEmpty()
            .withIndex()
            .filter { it.value.id in picked && it.value.suggestionId != null }
    if (rows.isEmpty() || writing) return
    val left = picked - rows.map { it.value.id }.toSet()
    mutate { it.copy(inboxSelection = left.ifEmpty { null }) }
    fileInboxRows(
        rows.associate {
            it.value.id to it.value.suggestionId
        },
        rows.map { InboxFiled(it.value, it.index, it.value.suggestionName) },
        fromPicker = false,
    )
}

/** Deletes every checked row as one undo step and leaves select mode. */
fun ShellViewModel.deleteSelectedInbox() {
    val picked = state.value.inboxSelection ?: return
    val rows =
        state.value.shell
            ?.inbox
            .orEmpty()
            .filter { it.id in picked }
    if (rows.isEmpty() || writing) return
    mutate { it.copy(inboxSelection = null) }
    launchWrite { id ->
        val result = withContext(io) { library.deleteTransactions(id, rows.map { it.id }, false) }
        publish(result, rows.first().accountId, stayOnRegister = false)
    }
}

/** Opens the picker for every checked row; the category picked files them all. */
fun ShellViewModel.categorizeSelectedInbox() {
    val picked = state.value.inboxSelection ?: return
    val ids =
        state.value.shell
            ?.inbox
            .orEmpty()
            .filter { it.id in picked }
            .map { it.id }
    if (ids.isEmpty()) return
    openInbox(ids.first(), ids.drop(1))
}

internal fun ShellViewModel.fileInbox(
    row: InboxRow,
    index: Int,
    categoryId: String,
    categoryName: String,
    fromPicker: Boolean,
) = fileInboxRows(mapOf(row.id to categoryId), listOf(InboxFiled(row, index, categoryName)), fromPicker)

/** Writes [assignments] as one undo step while [filed] play their confirmations. */
internal fun ShellViewModel.fileInboxRows(
    assignments: Map<String, String?>,
    filed: List<InboxFiled>,
    fromPicker: Boolean,
) {
    if (writing || filed.isEmpty() || state.value.settings.openBudgetId == null) return
    val ids = filed.map { it.row.id }
    mutate { it.copy(inboxFiled = it.inboxFiled + filed) }
    inboxWriting += ids
    val accountId = filed.first().row.accountId
    launchWrite { id ->
        val result =
            try {
                withContext(io) {
                    if (assignments.size == 1) {
                        val (txn, category) = assignments.entries.single()
                        library.setTransactionCategory(id, txn, category, false)
                    } else {
                        library.setTransactionsCategory(id, assignments, false)
                    }
                }
            } catch (e: Throwable) {
                settleInboxWrite(ids)
                throw e
            }
        if (result !is WriteResult.Saved) {
            settleInboxWrite(ids)
            ids.forEach(::inboxFiledShown)
        }
        if (result is WriteResult.Saved) {
            mutate {
                it.copy(
                    inboxSelection = it.inboxSelection?.minus(assignments.keys)?.ifEmpty { null },
                )
            }
        }
        if (fromPicker || result is WriteResult.Saved) {
            try {
                publish(result, accountId, stayOnRegister = false)
            } finally {
                // The reload has the rows gone, so any confirmation that already played can leave now.
                if (result is WriteResult.Saved) settleInboxWrite(ids)
            }
        } else {
            when (result) {
                is WriteResult.Rejected -> mutate { it.copy(writeError = result.reason) }
                is WriteResult.Confirm -> mutate { it.copy(confirm = result, writeError = null) }
                else -> Unit
            }
        }
    }
}

/** Ends [ids]' write: rows whose confirmation already played leave the inbox now. */
private fun ShellViewModel.settleInboxWrite(ids: List<String>) {
    inboxWriting -= ids.toSet()
    val played = ids.filter { inboxPlayed.remove(it) }.toSet()
    if (played.isNotEmpty()) {
        mutate { state -> state.copy(inboxFiled = state.inboxFiled.filterNot { it.row.id in played }) }
    }
}

fun ShellViewModel.openInbox(transactionId: String) = openInbox(transactionId, emptyList())

/** The picker for one inbox row, or for it and [others] together, headed by their count and total. */
private fun ShellViewModel.openInbox(
    transactionId: String,
    others: List<String>,
) {
    push(ShellRoute.PickCategory(transactionId, others))
    val budgetId = state.value.settings.openBudgetId ?: return
    val together =
        state.value.shell
            ?.inbox
            .orEmpty()
            .filter { it.id == transactionId || it.id in others }
    viewModelScope.launch {
        val target = withContext(io) { library.readCategoryTarget(budgetId, transactionId) }
        val shown =
            if (others.isEmpty()) {
                target
            } else {
                target?.copy(payee = "${together.size} ${InboxCopy.TRANSACTIONS}", amountMinor = together.sumOf { it.amountMinor })
            }
        mutate { it.copy(categoryTarget = shown, loading = false) }
    }
}
