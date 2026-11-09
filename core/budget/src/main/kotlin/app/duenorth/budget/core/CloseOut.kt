package app.duenorth.budget.core

import java.time.LocalDate
import java.time.YearMonth

/**
 * End-of-month close out: every expense category of a month, sorted into over (spent more than it had), left over
 * (money still in it) and even. Over is fixed by covering it from another category or to budget, or by letting it come
 * out of next month's to budget (Actual's default for uncovered overspending). Left over rolls over (Actual carries a
 * positive balance by default) or goes back to to budget. When nothing is left to fix, the month is all square.
 */
object CloseOutCopy {
    const val ASSISTANT_TITLE = "close out"
    const val REVIEW = "in review"
    const val ALL_SQUARE = "all square."
    const val ALMOST_SQUARE = "almost square."
    const val ROUGH_MONTH = "rough month."
    const val OVER = "over"
    const val LEFT = "left over"
    const val EVEN = "even"
    const val OVER_TAG = "OVER"
    const val LEFT_TAG = "LEFT"
    const val COVER_FROM = "cover it from"
    const val WHAT_NOW = "what should it do?"
    const val ROLL_OVER = "roll over"
    const val BACK_TO_BUDGET = "back to"
    const val TO_BUDGET = "to budget"
    const val SKIP = "skip"
    const val REVIEW_LINK = "back to the review"
    const val COVERED = "covered"
    const val ROLLED = "rolled"
    const val RETURNED = "to budget"
    const val NEXT_MONTH = "next month"
    const val CLOSED_HINT = "every category is settled"
    const val FIX_HINT = "cover what went over, then decide what to do with what is left."
    const val NOTHING_TO_COVER_FROM = "nothing has money left to cover it"

    fun title(month: YearMonth): String = "close out ${monthName(month)}"

    fun kicker(month: YearMonth): String = "${monthName(month).uppercase()} IN REVIEW"

    fun closedKicker(month: YearMonth): String = "${monthName(month).uppercase()} IS CLOSED"

    fun endsIn(
        month: YearMonth,
        today: LocalDate,
    ): String {
        val days = month.atEndOfMonth().toEpochDay() - today.toEpochDay()
        return when {
            days <= 0L -> "${monthName(month).uppercase()} ENDS TODAY"
            days == 1L -> "${monthName(month).uppercase()} ENDS TOMORROW"
            else -> "${monthName(month).uppercase()} ENDS IN $days DAYS"
        }
    }

    fun settle(next: YearMonth): String = "settle every category before ${monthName(next)} starts"

    fun closed(month: YearMonth): String = "${monthName(month)} closed"

    fun fix(count: Int): String = "fix $count ›"

    fun start(rough: Boolean): String = if (rough) "let’s fix it ›" else "start ›"

    fun takeFrom(next: YearMonth): String = "take it from ${monthName(next)}"

    fun keepFor(
        amount: String,
        name: String,
        next: YearMonth,
    ): String = "keep $amount in $name for ${monthName(next)}"

    fun roughHint(
        over: Int,
        overAmount: String,
        leftAmount: String,
    ): String = "$over categories went over. $overAmount to cover, with $leftAmount left over elsewhere to help."

    fun facts(
        budgeted: String,
        spent: String,
    ): String = "budgeted $budgeted · spent $spent"

    fun step(
        index: Int,
        count: Int,
    ): String = "$index of $count"

    fun goTo(next: YearMonth): String = "go to ${monthName(next)} ›"

    fun counts(
        over: Int,
        left: Int,
    ): String = "$over over · $left left over"

    private fun monthName(month: YearMonth): String = month.month.name.lowercase()
}

/** When the assistant offers close out: the last [days] days of a month; 0 never. Settings → budget. */
object CloseOutWindow {
    const val DEFAULT_DAYS = 3
    val CHOICES = listOf(1, 3, 7, 0)

    fun label(days: Int): String =
        when (days) {
            0 -> "off"
            1 -> "on the last day of the month"
            else -> "the last $days days of the month"
        }

    /** The month [today] would close, while its last [days] days are running; null outside them or when off. */
    fun monthFor(
        today: LocalDate,
        days: Int,
    ): YearMonth? {
        if (days <= 0) return null
        val left = today.lengthOfMonth() - today.dayOfMonth
        return if (left < days) YearMonth.from(today) else null
    }
}

enum class CloseOutKind { OVER, LEFT, EVEN }

/** How a category was settled during close out, for its tile. */
enum class CloseOutOutcome {
    COVERED,
    ROLLED,
    RETURNED,
    NEXT_MONTH,
}

data class CloseOutItem(
    val categoryId: String,
    val name: String,
    val budgetedMinor: Long,
    val spentMinor: Long,
    val availableMinor: Long,
    /** The person chose to leave it as it is: roll a left over balance on, or let overspending come out of next month. */
    val kept: Boolean,
) {
    val kind: CloseOutKind
        get() =
            when {
                availableMinor < 0L -> CloseOutKind.OVER
                availableMinor > 0L -> CloseOutKind.LEFT
                else -> CloseOutKind.EVEN
            }

    /** Nothing left to decide: even, or kept as it is. */
    val settled: Boolean get() = kind == CloseOutKind.EVEN || kept
}

/** A place an overspent category can be covered from: to budget ([categoryId] null) or a category with money left. */
data class CoverSource(
    val categoryId: String?,
    val name: String,
    val availableMinor: Long,
)

data class CloseOutReview(
    val month: YearMonth,
    val items: List<CloseOutItem>,
    val toBudgetMinor: Long,
) {
    val over: List<CloseOutItem> get() = items.filter { it.kind == CloseOutKind.OVER }
    val left: List<CloseOutItem> get() = items.filter { it.kind == CloseOutKind.LEFT }
    val even: List<CloseOutItem> get() = items.filter { it.kind == CloseOutKind.EVEN }

    /** What still needs a decision, overspent first (they may need the left over money), each in budget order. */
    val todo: List<CloseOutItem>
        get() = items.filter { !it.settled && it.kind == CloseOutKind.OVER } + items.filter { !it.settled && it.kind == CloseOutKind.LEFT }

    val allSquare: Boolean get() = todo.isEmpty()

    /** More than [ROUGH_OVER] categories over: the review says so plainly. */
    val rough: Boolean get() = over.count { !it.kept } > ROUGH_OVER

    val overMinor: Long get() = over.sumOf { it.availableMinor }
    val leftMinor: Long get() = left.sumOf { it.availableMinor }

    /** Where [item] can be covered from: to budget first when it has money, then categories with money left, most first. */
    fun sources(item: CloseOutItem): List<CoverSource> =
        buildList {
            if (toBudgetMinor > 0L) add(CoverSource(null, CloseOutCopy.TO_BUDGET, toBudgetMinor))
            items
                .filter { it.categoryId != item.categoryId && it.availableMinor > 0L }
                .sortedByDescending { it.availableMinor }
                .forEach { add(CoverSource(it.categoryId, it.name, it.availableMinor)) }
        }

    companion object {
        const val ROUGH_OVER = 5

        /** The review of [shell]'s visible expense categories; [kept] holds the ids the person chose to leave as they are. */
        fun of(
            shell: MonthShell,
            kept: Set<String>,
        ): CloseOutReview =
            CloseOutReview(
                month = shell.month,
                items =
                    shell.groups
                        .filter { !it.hidden }
                        .flatMap { group -> group.categories.filter { !it.hidden } }
                        .map {
                            CloseOutItem(
                                categoryId = it.id,
                                name = it.name,
                                budgetedMinor = it.budgetedMinor,
                                spentMinor = it.spentMinor,
                                availableMinor = it.availableMinor,
                                kept = it.id in kept,
                            )
                        },
                toBudgetMinor = shell.headerMinor,
            )
    }
}

/**
 * What close out remembers on this phone: which categories were kept as they are, and which months were closed,
 * each as "budgetId:yyyy-MM" or "budgetId:yyyy-MM:categoryId". Entries older than the month before are dropped.
 */
object CloseOutMarks {
    fun month(
        budgetId: String,
        month: YearMonth,
    ): String = "$budgetId:$month"

    fun kept(
        marks: List<String>,
        budgetId: String,
        month: YearMonth,
    ): Set<String> {
        val prefix = month(budgetId, month) + ":"
        return marks.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }.toSet()
    }

    fun keep(
        marks: List<String>,
        budgetId: String,
        month: YearMonth,
        categoryId: String,
    ): List<String> = prune((marks + "${month(budgetId, month)}:$categoryId").distinct(), month)

    /** Keeps marks for [month] and the month before it, so the lists never grow. */
    fun prune(
        marks: List<String>,
        month: YearMonth,
    ): List<String> {
        val recent = setOf(month.toString(), month.minusMonths(1).toString())
        return marks.filter { mark -> mark.split(':').getOrNull(1) in recent }
    }
}
