package app.duenorth.budget

import app.duenorth.budget.core.MonthReviewPage
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.design.MonthWindow
import java.time.YearMonth

data class MonthSnapshot(
    val month: YearMonth,
    val shell: MonthShell,
    val review: MonthReviewPage?,
)

/**
 * One open month for the home pivot. Cache hits apply shell and review in the same call.
 * Gestures hold row updates until [flushPending].
 */
class OpenMonthStore(
    initial: YearMonth = YearMonth.now(),
) {
    var openMonth: YearMonth = initial
        private set
    var pendingMonth: YearMonth? = null
        private set
    var shell: MonthShell? = null
        private set
    var review: MonthReviewPage? = null
        private set

    private val cache = LinkedHashMap<YearMonth, MonthSnapshot>()
    var window: MonthWindow = MonthWindow.around(YearMonth.now())
        private set

    fun rebuildWindow(anchor: YearMonth = YearMonth.now()) {
        window = MonthWindow.around(anchor)
    }

    fun reset(month: YearMonth = YearMonth.now()) {
        openMonth = month
        pendingMonth = null
        shell = null
        review = null
        cache.clear()
        rebuildWindow(YearMonth.now())
    }

    /** Forgets every cached month but keeps what is on screen; the next select reloads. */
    fun invalidate() {
        cache.clear()
    }

    fun put(snapshot: MonthSnapshot) {
        cache[snapshot.month] = snapshot
    }

    fun cached(month: YearMonth): MonthSnapshot? = cache[month]

    /** Returns true when shell and review were applied from cache. */
    fun select(
        month: YearMonth,
        gestureActive: Boolean,
    ): Boolean {
        if (!window.contains(month)) return false
        openMonth = month
        if (gestureActive) {
            pendingMonth = month
            return false
        }
        pendingMonth = null
        val hit = cache[month] ?: return false
        shell = hit.shell
        review = hit.review
        return true
    }

    fun flushPending(gestureActive: Boolean): Boolean {
        if (gestureActive) return false
        val month = pendingMonth ?: return false
        return select(month, gestureActive = false)
    }

    fun applyLoaded(
        snapshot: MonthSnapshot,
        gestureActive: Boolean,
    ) {
        put(snapshot)
        if (snapshot.month != openMonth) return
        if (gestureActive) {
            pendingMonth = snapshot.month
            return
        }
        shell = snapshot.shell
        review = snapshot.review
        pendingMonth = null
    }

    fun editorMonth(pinnedMonth: YearMonth?): YearMonth = pinnedMonth ?: openMonth
}
