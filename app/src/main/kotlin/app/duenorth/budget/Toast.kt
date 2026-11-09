package app.duenorth.budget

import app.duenorth.budget.core.WriteResult
import java.util.concurrent.atomic.AtomicLong

/**
 * A short word on how a save, add or delete went, shown over whatever page is up. It lives in [ShellUiState.toast],
 * not in a page, and [ToastHost] draws it from the app root, so it stays up when the write sends the app to
 * another page.
 */
data class Toast(
    val kind: ToastKind,
    val title: String,
    val message: String? = null,
    val action: ToastAction? = null,
    /** New for each toast, so the same words posted twice restart its timer. */
    val id: Long = nextId.incrementAndGet(),
) {
    private companion object {
        val nextId = AtomicLong()
    }
}

enum class ToastKind { Success, Failure }

/** The one button a toast can carry. */
enum class ToastAction { Undo, }

/** How long a toast stays up: a success for a few seconds, longer with a button; a failure until it is dismissed. */
object ToastTiming {
    const val SUCCESS_MS = 4_000L
    const val WITH_ACTION_MS = 6_000L

    fun shownFor(toast: Toast): Long? =
        when {
            toast.kind == ToastKind.Failure -> null
            toast.action != null -> WITH_ACTION_MS
            else -> SUCCESS_MS
        }
}

/** Shows [title] as done, with undo unless [undo] is false. Post before navigating, so it reads as one step. */
internal fun ShellHost.toastSuccess(
    title: String,
    message: String? = null,
    undo: Boolean = true,
) = mutate { it.copy(toast = Toast(ToastKind.Success, title, message, if (undo) ToastAction.Undo else null)) }

/** Shows [title] as failed, with the [reason] the write gave. */
internal fun ShellHost.toastFailure(
    title: String,
    reason: String? = null,
) = mutate { it.copy(toast = Toast(ToastKind.Failure, title, reason?.takeIf { r -> r.isNotBlank() })) }

/** A toast for a transaction write: [done] when it saved, [failed] with its reason when it was refused. */
internal fun ShellHost.toastFor(
    result: WriteResult,
    done: String,
    failed: String,
) {
    when (result) {
        is WriteResult.Saved -> toastSuccess(done)
        is WriteResult.Rejected -> toastFailure(failed, result.reason)
        // A question comes first; the toast follows its answer.
        is WriteResult.Confirm -> Unit
    }
}

object ToastCopy {
    const val UNDO = "undo"
    const val DISMISS = "dismiss"

    const val TRANSACTION_ADDED = "transaction added"
    const val TRANSACTION_SAVED = "transaction saved"
    const val TRANSACTION_SAVE_FAILED = "couldn't save transaction"
    const val TRANSACTION_DELETED = "transaction deleted"
    const val TRANSACTIONS_DELETED = "transactions deleted"
    const val TRANSACTION_DELETE_FAILED = "couldn't delete"
    const val TRANSACTIONS_MERGED = "transactions merged"
    const val MERGE_FAILED = "couldn't merge"
    const val MATCHED = "transactions matched"
    const val MATCH_FAILED = "couldn't match"

    const val ACCOUNT_ADDED = "account added"
    const val ACCOUNT_SAVED = "account saved"
    const val ACCOUNT_CLOSED = "account closed"
    const val ACCOUNT_SAVE_FAILED = "couldn't save account"
    const val ACCOUNT_CLOSE_FAILED = "couldn't close account"

    const val RULE_SAVED = "rule saved"
    const val RULE_SAVE_FAILED = "couldn't save rule"
    const val RULE_DELETED = "rule deleted"

    const val SCHEDULE_SAVED = "schedule saved"
    const val SCHEDULE_SAVE_FAILED = "couldn't save schedule"
    const val SCHEDULE_DELETED = "schedule deleted"
    const val SCHEDULE_POSTED = "transaction posted"
    const val SCHEDULE_SKIPPED = "skipped to next date"
    const val SCHEDULE_POST_FAILED = "couldn't post"
    const val SCHEDULE_SKIP_FAILED = "couldn't skip"

    const val CATEGORY_SAVED = "saved"
    const val CATEGORY_ADDED = "category added"
    const val GROUP_ADDED = "group added"
    const val CATEGORY_DELETED = "category deleted"
    const val GROUP_DELETED = "group deleted"
    const val RENAMED = "renamed"
    const val CATEGORY_SAVE_FAILED = "couldn't save"

    const val PAYEE_SAVED = "payee saved"
    const val PAYEE_SAVE_FAILED = "couldn't save payee"

    const val MONTH_FILLED = "budget filled"
    const val MONTH_FILL_FAILED = "couldn't fill the budget"
    const val MOVED_TO_BUDGET = "moved to to budget"
    const val MOVE_FAILED = "couldn't move"
    const val UNDO_FAILED = "couldn't undo"
    const val NOTHING_DONE = "nothing changed"
}
