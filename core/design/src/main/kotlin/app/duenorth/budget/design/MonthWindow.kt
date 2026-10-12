package app.duenorth.budget.design

import java.time.YearMonth

/** Calendar months from [anchor] minus [radius] through plus [radius], inclusive. */
data class MonthWindow(
    val anchor: YearMonth,
    val radius: Int = DEFAULT_RADIUS,
) {
    init {
        require(radius >= 0) { "radius must be non-negative" }
    }

    val months: List<YearMonth> =
        (0..(radius * 2)).map { offset ->
            anchor.minusMonths(radius.toLong()).plusMonths(offset.toLong())
        }

    val oldest: YearMonth get() = months.first()

    val newest: YearMonth get() = months.last()

    fun contains(month: YearMonth): Boolean = month in months

    fun previous(month: YearMonth): YearMonth? {
        val index = months.indexOf(month)
        return if (index > 0) months[index - 1] else null
    }

    fun next(month: YearMonth): YearMonth? {
        val index = months.indexOf(month)
        return if (index >= 0 && index < months.lastIndex) months[index + 1] else null
    }

    companion object {
        const val DEFAULT_RADIUS = 24

        fun around(anchor: YearMonth = YearMonth.now()): MonthWindow = MonthWindow(anchor)
    }
}
