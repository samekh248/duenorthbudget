package app.duenorth.budget

import app.duenorth.budget.core.ImportPreview
import app.duenorth.budget.core.ImportReviewPage
import app.duenorth.budget.core.ParsedImportRow
import app.duenorth.budget.core.PreviewRowStatus
import app.duenorth.budget.core.WriteResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Importing's own state, inside [ShellUiState.import]. */
data class ImportState(
    val preview: ImportPreview? = null,
    val candidates: List<ParsedImportRow> = emptyList(),
    val review: ImportReviewPage? = null,
    val error: String? = null,
    /** A bank fetch for the preview is running. */
    val fetchPending: Boolean = false,
)

/** Importing: a file preview, a bank fetch and the review of what came in. */
class ImportHolder internal constructor(
    private val host: ShellHost,
) {
    private val state get() = host.state.value

    private fun update(transform: (ImportState) -> ImportState) = host.mutate { it.copy(import = transform(it.import)) }

    fun previewFile(
        accountId: String,
        bytes: ByteArray,
        fileName: String,
    ) {
        val budgetId = state.settings.openBudgetId ?: return
        host.scope.launch {
            val (bundle, error) =
                withContext(host.io) {
                    host.library.previewImportFile(budgetId, accountId, bytes, fileName)
                }
            if (bundle == null) {
                update { it.copy(error = error) }
                return@launch
            }
            host.mutate {
                it.copy(
                    route = ShellRoute.ImportPreviewRoute(accountId),
                    import = it.import.copy(preview = bundle.preview, candidates = bundle.candidates, error = error),
                )
            }
        }
    }

    fun confirm() {
        val budgetId = state.settings.openBudgetId ?: return
        val preview = state.import.preview ?: return
        val candidates = state.import.candidates
        host.scope.launch {
            withContext(host.io) {
                val book = host.library
                val toAdd =
                    preview.rows
                        .filter { it.status == PreviewRowStatus.New }
                        .map { candidates[it.index] }
                book.confirmImport(budgetId, preview.accountId, toAdd)
            }
            host.push(ShellRoute.ImportReviewRoute(preview.accountId))
            reloadReview()
            host.reloadAccount(preview.accountId)
        }
    }

    fun cancel() {
        val accountId =
            (state.route as? ShellRoute.ImportPreviewRoute)?.accountId
                ?: state.openAccountId()
                ?: return
        host.mutate {
            it.copy(
                import = it.import.copy(preview = null, candidates = emptyList(), error = null),
                route = ShellRoute.Register(accountId),
            )
        }
    }

    fun fetchBank(accountId: String) {
        val budgetId = state.settings.openBudgetId ?: return
        val settings = state.settings
        host.mutate { it.copy(import = it.import.copy(fetchPending = true, error = null), syncProgress = 0.1f) }
        host.scope.launch {
            val (bundle, error) =
                withContext(host.io) {
                    host.library.previewBankFetch(
                        budgetId,
                        accountId,
                        settings.serverAddress,
                        host.sync.syncToken(),
                        host.bankSyncTransport,
                        java.time.LocalDate.now(),
                    )
                }
            host.mutate { it.copy(import = it.import.copy(fetchPending = false), syncProgress = null) }
            if (bundle == null) {
                update { it.copy(error = error) }
                return@launch
            }
            host.mutate {
                it.copy(
                    route = ShellRoute.ImportPreviewRoute(accountId),
                    import = it.import.copy(preview = bundle.preview, candidates = bundle.candidates, error = null),
                )
            }
        }
    }

    fun setReviewCategory(
        transactionId: String,
        categoryId: String?,
    ) {
        val budgetId = state.settings.openBudgetId ?: return
        val accountId = state.import.review?.accountId ?: return
        host.scope.launch {
            val result =
                withContext(host.io) {
                    host.library.setImportReviewCategory(budgetId, transactionId, categoryId)
                }
            when (result) {
                is WriteResult.Rejected -> update { it.copy(error = result.reason) }
                else -> reloadReview()
            }
            host.reloadAccount(accountId)
        }
    }

    fun finishReview() {
        val budgetId = state.settings.openBudgetId ?: return
        val accountId = state.import.review?.accountId ?: state.openAccountId() ?: return
        host.scope.launch {
            withContext(host.io) { host.library.finishImportReview(budgetId) }
            host.mutate {
                it.copy(
                    import = it.import.copy(review = null, preview = null, candidates = emptyList(), error = null),
                    route = ShellRoute.Register(accountId),
                )
            }
            host.reloadAccount(accountId)
        }
    }

    private suspend fun reloadReview() {
        val budgetId = state.settings.openBudgetId ?: return
        val review = withContext(host.io) { host.library.readImportReview(budgetId) }
        update { it.copy(review = review, error = null) }
    }
}
