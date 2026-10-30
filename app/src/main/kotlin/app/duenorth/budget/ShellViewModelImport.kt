package app.duenorth.budget

import androidx.lifecycle.viewModelScope
import app.duenorth.budget.core.PreviewRowStatus
import app.duenorth.budget.core.WriteResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Importing: a file preview, a bank fetch and the review of what came in.

fun ShellViewModel.previewImportFile(
    accountId: String,
    bytes: ByteArray,
    fileName: String,
) {
    val budgetId = state.value.settings.openBudgetId ?: return
    viewModelScope.launch {
        val (bundle, error) =
            withContext(io) {
                library.previewImportFile(budgetId, accountId, bytes, fileName)
            }
        if (bundle == null) {
            mutate { it.copy(importError = error) }
            return@launch
        }
        mutate {
            it.copy(
                route = ShellRoute.ImportPreviewRoute(accountId),
                importPreview = bundle.preview,
                importCandidates = bundle.candidates,
                importError = error,
            )
        }
    }
}

fun ShellViewModel.confirmImport() {
    val budgetId = state.value.settings.openBudgetId ?: return
    val preview = state.value.importPreview ?: return
    val candidates = state.value.importCandidates
    viewModelScope.launch {
        withContext(io) {
            val book = library
            val toAdd =
                preview.rows
                    .filter { it.status == PreviewRowStatus.New }
                    .map { candidates[it.index] }
            book.confirmImport(budgetId, preview.accountId, toAdd)
        }
        push(ShellRoute.ImportReviewRoute(preview.accountId))
        reloadReview(preview.accountId)
        apply(load(preview.accountId), preview.accountId)
    }
}

fun ShellViewModel.cancelImport() {
    val accountId =
        (state.value.route as? ShellRoute.ImportPreviewRoute)?.accountId
            ?: openAccountId()
            ?: return
    mutate {
        it.copy(
            importPreview = null,
            importCandidates = emptyList(),
            importError = null,
            route = ShellRoute.Register(accountId),
        )
    }
}

fun ShellViewModel.fetchBankTransactions(accountId: String) {
    val budgetId = state.value.settings.openBudgetId ?: return
    val settings = state.value.settings
    mutate { it.copy(importFetchPending = true, importError = null, syncProgress = 0.1f) }
    viewModelScope.launch {
        val (bundle, error) =
            withContext(io) {
                library.previewBankFetch(
                    budgetId,
                    accountId,
                    settings.serverAddress,
                    sync.syncToken(),
                    bankSyncTransport,
                    java.time.LocalDate.now(),
                )
            }
        mutate { it.copy(importFetchPending = false, syncProgress = null) }
        if (bundle == null) {
            mutate { it.copy(importError = error) }
            return@launch
        }
        mutate {
            it.copy(
                route = ShellRoute.ImportPreviewRoute(accountId),
                importPreview = bundle.preview,
                importCandidates = bundle.candidates,
                importError = null,
            )
        }
    }
}

fun ShellViewModel.setImportReviewCategory(
    transactionId: String,
    categoryId: String?,
) {
    val budgetId = state.value.settings.openBudgetId ?: return
    val accountId = state.value.importReview?.accountId ?: return
    viewModelScope.launch {
        val result =
            withContext(io) {
                library.setImportReviewCategory(budgetId, transactionId, categoryId)
            }
        when (result) {
            is WriteResult.Rejected -> mutate { it.copy(importError = result.reason) }
            else -> reloadReview(accountId)
        }
        apply(load(accountId), accountId)
    }
}

fun ShellViewModel.finishImportReview() {
    val budgetId = state.value.settings.openBudgetId ?: return
    val accountId = state.value.importReview?.accountId ?: openAccountId() ?: return
    viewModelScope.launch {
        withContext(io) { library.finishImportReview(budgetId) }
        mutate {
            it.copy(
                importReview = null,
                importPreview = null,
                importCandidates = emptyList(),
                importError = null,
                route = ShellRoute.Register(accountId),
            )
        }
        apply(load(accountId), accountId)
    }
}

private suspend fun ShellViewModel.reloadReview(accountId: String) {
    val budgetId = state.value.settings.openBudgetId ?: return
    val review = withContext(io) { library.readImportReview(budgetId) }
    mutate { it.copy(importReview = review, importError = null) }
}
