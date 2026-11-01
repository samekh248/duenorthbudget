package app.duenorth.budget

import androidx.annotation.VisibleForTesting
import java.time.YearMonth

/**
 * Today's month, for the month control's "this month" button and its grid. Screenshot tests pin it to their sample
 * month, so the button doesn't come and go with the date the tests run on.
 */
object CalendarMonth {
    @VisibleForTesting
    var pinned: YearMonth? = null

    fun now(): YearMonth = pinned ?: YearMonth.now()
}
