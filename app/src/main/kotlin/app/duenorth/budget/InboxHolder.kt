package app.duenorth.budget

import app.duenorth.budget.core.InboxRow
import app.duenorth.budget.core.WriteResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The inbox's own state, inside [ShellUiState.inbox]. */
data class InboxUi(
    /** Rows just filed, kept on screen while their confirmation plays. */
    val filed: List<InboxFiled> = emptyList(),
    /** Rows checked in select mode; null when not selecting. */
    val selection: Set<String>? = null,
    /** A transaction just left by a match, shown first so it can be filed next. */
    val lead: String? = null,
    /** How the inbox's own sync went, shown under its totals until the pivot moves on. */
    val syncNotice: String? = null,
)

/**
 * The inbox: filing, deleting and opening what came in, one row or a selection at a time. It keeps its state in
 * [InboxUi] and reaches the rest of the shell through [host].
 */
class InboxHolder internal constructor(
    private val host: ShellHost,
) {
    /** Rows whose filing is still being written, and those of them whose confirmation has already played. */
    private val writing = mutableSetOf<String>()
    private val played = mutableSetOf<String>()

    private val state get() = host.state.value

    private fun update(transform: (InboxUi) -> InboxUi) = host.mutate { it.copy(inbox = transform(it.inbox)) }

    /** Accept: files the row under the category its payee last went to, or opens the picker when there is none. */
    fun accept(transactionId: String) {
        val inbox = shown() ?: return
        val index = inbox.indexOfFirst { it.id == transactionId }
        val row = inbox.getOrNull(index) ?: return
        val categoryId = row.suggestionId
        if (categoryId == null) {
            openPicker(transactionId)
            return
        }
        file(row, index, categoryId, row.suggestionName, fromPicker = false)
    }

    /** A tap on an inbox row opens its transaction, as the register does. */
    fun openDetails(transactionId: String) {
        openFromInbox(transactionId) { accountId -> ShellRoute.EditTransaction(accountId, transactionId) }
    }

    fun delete(transactionId: String) {
        val row =
            state.shell
                ?.inbox
                ?.firstOrNull { it.id == transactionId } ?: return
        host.launchWrite { id ->
            val result = withContext(host.io) { host.library.deleteTransaction(id, transactionId, false) }
            host.publish(result, row.accountId, stayOnRegister = false)
        }
    }

    private fun openFromInbox(
        transactionId: String,
        route: (String) -> ShellRoute,
    ) {
        val row =
            state.shell
                ?.inbox
                ?.firstOrNull { it.id == transactionId } ?: return
        host.push(route(row.accountId))
        host.mutate { it.copy(editFromHome = true, registerUi = it.registerUi.copy(selection = null)) }
        host.loadPayees()
        host.scope.launch { host.reloadAccount(row.accountId) }
    }

    fun filedShown(transactionId: String) {
        // The confirmation can end before the write and its reload land. Letting the row go then
        // would show it again, still waiting, until the reload swaps in the next one.
        if (transactionId in writing) {
            played += transactionId
            return
        }
        update { inbox -> inbox.copy(filed = inbox.filed.filterNot { it.row.id == transactionId }) }
    }

    /** The inbox rows in the order the inbox shows them, so a filed row's index is the place it left. */
    internal fun shown(): List<InboxRow>? = state.shell?.let { inboxQueue(it, state.inbox.lead) }

    /** The inbox bar's select: every row gets a check box, and the bar acts on the checked ones. */
    fun startSelecting() {
        update { it.copy(selection = emptySet()) }
    }

    fun toggleSelected(transactionId: String) {
        update {
            val picked = it.selection ?: return@update it
            it.copy(selection = if (transactionId in picked) picked - transactionId else picked + transactionId)
        }
    }

    fun endSelecting() {
        update { it.copy(selection = null) }
    }

    /**
     * Files every checked row that has a suggestion under it, as one undo step. Rows without a
     * suggestion stay checked, so categorize can file them next.
     */
    fun acceptSelected() {
        val picked = state.inbox.selection ?: return
        val rows =
            shown()
                .orEmpty()
                .withIndex()
                .filter { it.value.id in picked && it.value.suggestionId != null }
        if (rows.isEmpty() || host.writing) return
        val left = picked - rows.map { it.value.id }.toSet()
        update { it.copy(selection = left.ifEmpty { null }) }
        fileRows(
            rows.associate {
                it.value.id to it.value.suggestionId
            },
            rows.map { InboxFiled(it.value, it.index, it.value.suggestionName) },
            fromPicker = false,
        )
    }

    /** Deletes every checked row as one undo step and leaves select mode. */
    fun deleteSelected() {
        val picked = state.inbox.selection ?: return
        val rows =
            state.shell
                ?.inbox
                .orEmpty()
                .filter { it.id in picked }
        if (rows.isEmpty() || host.writing) return
        update { it.copy(selection = null) }
        host.launchWrite { id ->
            val result = withContext(host.io) { host.library.deleteTransactions(id, rows.map { it.id }, false) }
            host.publish(result, rows.first().accountId, stayOnRegister = false)
        }
    }

    /** Opens the picker for every checked row; the category picked files them all. */
    fun categorizeSelected() {
        val picked = state.inbox.selection ?: return
        val ids =
            state.shell
                ?.inbox
                .orEmpty()
                .filter { it.id in picked }
                .map { it.id }
        if (ids.isEmpty()) return
        openPicker(ids.first(), ids.drop(1))
    }

    internal fun file(
        row: InboxRow,
        index: Int,
        categoryId: String,
        categoryName: String,
        fromPicker: Boolean,
    ) = fileRows(mapOf(row.id to categoryId), listOf(InboxFiled(row, index, categoryName)), fromPicker)

    /** Writes [assignments] as one undo step while [filed] play their confirmations. */
    internal fun fileRows(
        assignments: Map<String, String?>,
        filed: List<InboxFiled>,
        fromPicker: Boolean,
    ) {
        if (host.writing || filed.isEmpty() || state.settings.openBudgetId == null) return
        val ids = filed.map { it.row.id }
        update { it.copy(filed = it.filed + filed) }
        writing += ids
        val accountId = filed.first().row.accountId
        host.launchWrite { id ->
            val result =
                try {
                    withContext(host.io) {
                        if (assignments.size == 1) {
                            val (txn, category) = assignments.entries.single()
                            host.library.setTransactionCategory(id, txn, category, false)
                        } else {
                            host.library.setTransactionsCategory(id, assignments, false)
                        }
                    }
                } catch (e: Throwable) {
                    settle(ids)
                    throw e
                }
            if (result !is WriteResult.Saved) {
                settle(ids)
                ids.forEach(::filedShown)
            }
            if (result is WriteResult.Saved) {
                update {
                    it.copy(
                        selection = it.selection?.minus(assignments.keys)?.ifEmpty { null },
                    )
                }
            }
            if (fromPicker || result is WriteResult.Saved) {
                try {
                    host.publish(result, accountId, stayOnRegister = false)
                } finally {
                    // The reload has the rows gone, so any confirmation that already played can leave now.
                    if (result is WriteResult.Saved) settle(ids)
                }
            } else {
                when (result) {
                    is WriteResult.Rejected -> host.mutate { it.copy(writeError = result.reason) }
                    is WriteResult.Confirm -> host.mutate { it.copy(confirm = result, writeError = null) }
                    else -> Unit
                }
            }
        }
    }

    /** Ends [ids]' write: rows whose confirmation already played leave the inbox now. */
    private fun settle(ids: List<String>) {
        writing -= ids.toSet()
        val done = ids.filter { played.remove(it) }.toSet()
        if (done.isNotEmpty()) {
            update { inbox -> inbox.copy(filed = inbox.filed.filterNot { it.row.id in done }) }
        }
    }

    fun openPicker(transactionId: String) = openPicker(transactionId, emptyList())

    /** The picker for one inbox row, or for it and [others] together, headed by their count and total. */
    private fun openPicker(
        transactionId: String,
        others: List<String>,
    ) {
        host.push(ShellRoute.PickCategory(transactionId, others))
        val budgetId = state.settings.openBudgetId ?: return
        val together =
            state.shell
                ?.inbox
                .orEmpty()
                .filter { it.id == transactionId || it.id in others }
        host.scope.launch {
            val target = withContext(host.io) { host.library.readCategoryTarget(budgetId, transactionId) }
            val shown =
                if (others.isEmpty()) {
                    target
                } else {
                    target?.copy(payee = "${together.size} ${InboxCopy.TRANSACTIONS}", amountMinor = together.sumOf { it.amountMinor })
                }
            host.mutate { it.copy(categoryTarget = shown, loading = false) }
        }
    }
}
