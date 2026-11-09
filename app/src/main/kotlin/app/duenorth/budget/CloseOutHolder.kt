package app.duenorth.budget

import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CloseOutItem
import app.duenorth.budget.core.CloseOutKind
import app.duenorth.budget.core.CloseOutMarks
import app.duenorth.budget.core.CloseOutOutcome
import app.duenorth.budget.core.CloseOutReview
import app.duenorth.budget.core.CloseOutWindow
import app.duenorth.budget.core.CoverSource
import app.duenorth.budget.core.EditResult
import app.duenorth.budget.core.MoneyParse
import app.duenorth.budget.core.MonthFillResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

/** End-of-month close out's own state, inside [ShellUiState.closeOut]. */
data class CloseOutUi(
    /** The month the assistant's card offers to close, while its window is open; null otherwise. */
    val summary: CloseOutReview? = null,
    /** True once [summary]'s month was taken to all square on this phone. */
    val summaryClosed: Boolean = false,
    /** The close out page's month, read again after every fix; null until read. */
    val review: CloseOutReview? = null,
    /** The category being fixed, one card at a time; null shows the review card. */
    val step: String? = null,
    /** How each category was settled on this visit, for its tile. */
    val outcomes: Map<String, CloseOutOutcome> = emptyMap(),
    /** Each category as the review card last showed it; null before it first showed, so it flips every tile in. */
    val shown: Map<String, CloseOutItem>? = null,
    /** A fix is saving; the card's choices wait for it. */
    val saving: Boolean = false,
    val error: String? = null,
)

/**
 * End-of-month close out: the assistant's card in the month's last days, and the page that reviews every category,
 * then fixes them one card at a time, covering what went over and rolling over or returning what is left.
 */
class CloseOutHolder internal constructor(
    private val host: ShellHost,
) {
    private val state get() = host.state.value

    private fun update(transform: (CloseOutUi) -> CloseOutUi) = host.mutate { it.copy(closeOut = transform(it.closeOut)) }

    /** Reads the month the assistant's card offers, on io; no card outside the window, when off, or on a tracking budget. */
    fun loadSummary(today: LocalDate = LocalDate.now()) {
        val budgetId = state.settings.openBudgetId ?: return update { it.copy(summary = null) }
        val month = CloseOutWindow.monthFor(today, state.settings.closeOutDays) ?: return update { it.copy(summary = null) }
        host.scope.launch {
            val review = withContext(host.io) { read(budgetId, month) }
            val closed = CloseOutMarks.month(budgetId, month) in state.settings.closeOutMarks
            update { it.copy(summary = review, summaryClosed = closed && review?.allSquare == true) }
        }
    }

    /** The assistant card's tap: opens close out on the card's month. */
    fun open() {
        val month = state.closeOut.summary?.month ?: YearMonth.now()
        host.push(ShellRoute.CloseOut(month))
        update { CloseOutUi(summary = it.summary, summaryClosed = it.summaryClosed) }
        reload(month)
    }

    /** The review's fix button: the first category still to settle. */
    fun startFix() {
        val first =
            state.closeOut.review
                ?.todo
                ?.firstOrNull() ?: return
        update { it.copy(step = first.categoryId, error = null) }
    }

    fun showReview() = update { it.copy(step = null, error = null) }

    /** The review card has shown [items]; later visits flip only what was settled since. */
    fun markShown(items: List<CloseOutItem>) = update { it.copy(shown = items.associateBy { item -> item.categoryId }) }

    /** Covers the open card's category from [source], as much as it needs or as much as [source] has. */
    fun cover(source: CoverSource) {
        val item = current() ?: return
        if (item.kind != CloseOutKind.OVER) return
        val shell = state.shell ?: return
        val month = state.closeOut.review?.month ?: return
        val amount = minOf(-item.availableMinor, source.availableMinor)
        if (amount <= 0L) return
        val text = MoneyParse.formatMagnitude(amount, shell.currency.decimals)
        val fromId = source.categoryId
        write(item, CloseOutOutcome.COVERED) { budgetId ->
            if (fromId == null) {
                val budgeted = item.budgetedMinor + amount
                val budgetedText = (if (budgeted < 0) "-" else "") + MoneyParse.formatMagnitude(budgeted, shell.currency.decimals)
                host.sync.assign(budgetId, item.categoryId, month, budgetedText)
            } else {
                host.sync.moveCategory(budgetId, fromId, item.categoryId, month, text)
            }
        }
    }

    /** Left over: keeps the balance in the category for next month, as Actual carries it. */
    fun rollOver() = keep(CloseOutOutcome.ROLLED)

    /** Over: leaves it, so it comes out of next month's to budget, as Actual does with uncovered overspending. */
    fun takeFromNext() = keep(CloseOutOutcome.NEXT_MONTH)

    /** Left over: sends the balance back to to budget. */
    fun returnToBudget() {
        val item = current() ?: return
        if (item.kind != CloseOutKind.LEFT) return
        val shell = state.shell ?: return
        val month = state.closeOut.review?.month ?: return
        val text = MoneyParse.formatMagnitude(item.availableMinor, shell.currency.decimals)
        write(item, CloseOutOutcome.RETURNED) { budgetId ->
            when (val result = host.sync.transferToBudget(budgetId, item.categoryId, month, text)) {
                is MonthFillResult.Rejected -> EditResult.Rejected(result.reason)
                else -> EditResult.Saved
            }
        }
    }

    /** Leaves the open card for now and shows the next one still to settle, or the review after the last. */
    fun skip() {
        val ui = state.closeOut
        val todo = ui.review?.todo.orEmpty()
        val index = todo.indexOfFirst { it.categoryId == ui.step }
        val next = todo.getOrNull(index + 1)
        update { it.copy(step = next?.categoryId, error = null) }
    }

    private fun current(): CloseOutItem? {
        val ui = state.closeOut
        if (ui.saving) return null
        return ui.review?.items?.firstOrNull { it.categoryId == ui.step }
    }

    private fun keep(outcome: CloseOutOutcome) {
        val item = current() ?: return
        val budgetId = state.settings.openBudgetId ?: return
        val month = state.closeOut.review?.month ?: return
        val marks = CloseOutMarks.keep(state.settings.closeOutMarks, budgetId, month, item.categoryId)
        saveMarks(marks)
        val review = state.closeOut.review ?: return
        val updated = review.copy(items = review.items.map { if (it.categoryId == item.categoryId) it.copy(kept = true) else it })
        settle(updated, item.categoryId, outcome)
    }

    private fun write(
        item: CloseOutItem,
        outcome: CloseOutOutcome,
        block: suspend (String) -> EditResult,
    ) {
        val budgetId = state.settings.openBudgetId ?: return
        val month = state.closeOut.review?.month ?: return
        update { it.copy(saving = true, error = null) }
        host.scope.launch {
            val result = withContext(host.io) { block(budgetId) }
            host.mutate { it.copy(undoLabel = host.library.undoLabel(budgetId)) }
            val review = withContext(host.io) { read(budgetId, month) }
            if (result is EditResult.Rejected || review == null) {
                update { it.copy(saving = false, error = (result as? EditResult.Rejected)?.reason, review = review ?: it.review) }
                return@launch
            }
            val now = review.items.firstOrNull { it.categoryId == item.categoryId }
            if (now != null && !now.settled) {
                // A partial cover: the card stays, showing what is still over.
                update { it.copy(saving = false, review = review) }
            } else {
                settle(review, item.categoryId, outcome)
            }
            host.afterMonthWrite(budgetId)
        }
    }

    /** Puts [review] in place with [categoryId] settled, and moves to the next card, or the review after the last. */
    private fun settle(
        review: CloseOutReview,
        categoryId: String,
        outcome: CloseOutOutcome,
    ) {
        val next = review.todo.firstOrNull()
        update {
            it.copy(
                review = review,
                step = next?.categoryId,
                outcomes = it.outcomes + (categoryId to outcome),
                saving = false,
                summary = if (it.summary?.month == review.month) review else it.summary,
            )
        }
        if (review.allSquare) markClosed(review.month)
    }

    private fun markClosed(month: YearMonth) {
        val budgetId = state.settings.openBudgetId ?: return
        val mark = CloseOutMarks.month(budgetId, month)
        if (mark !in state.settings.closeOutMarks) saveMarks(CloseOutMarks.prune(state.settings.closeOutMarks + mark, month))
        update { it.copy(summaryClosed = it.summary?.month == month || it.summaryClosed) }
    }

    private fun saveMarks(marks: List<String>) {
        val settings = state.settings.copy(closeOutMarks = marks)
        host.mutate { it.copy(settings = settings) }
        host.scope.launch(host.io) { host.library.save(settings) }
    }

    private fun reload(month: YearMonth) {
        val budgetId = state.settings.openBudgetId ?: return
        host.scope.launch {
            val review = withContext(host.io) { read(budgetId, month) }
            update { it.copy(review = review) }
        }
    }

    private fun read(
        budgetId: String,
        month: YearMonth,
    ): CloseOutReview? {
        val shell = runCatching { host.library.readShell(budgetId, month) }.getOrNull() ?: return null
        if (shell.mode == BudgetMode.TRACKING) return null
        return CloseOutReview.of(shell, CloseOutMarks.kept(state.settings.closeOutMarks, budgetId, month))
    }
}
