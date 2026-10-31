package app.duenorth.budget

import app.duenorth.budget.core.ScheduleRepeat
import app.duenorth.budget.core.ScheduleSummary
import org.junit.Assert.assertEquals
import org.junit.Test

/** How upcoming orders schedules due the same day, and how a drop among them reads as desktop's move. */
class ScheduleOccurrencesTest {
    @Test
    fun sameDaySchedulesGoBySortOrderThenAmount() {
        val rows =
            ScheduleOccurrences.of(
                listOf(
                    schedule("rent", -150000, 0.0),
                    schedule("phone", -4500, 0.0),
                    schedule("gym", -3000, 16384.0),
                    schedule("water", -2500, 8192.0),
                    schedule("power", -9000, 99999.0, date = 20261103),
                ),
            )
        assertEquals(listOf("gym", "water", "rent", "phone", "power"), rows.map { it.schedule.id })
    }

    @Test
    fun aDropBeforeARowGoesAfterTheOneAboveIt() {
        val day = listOf("a", "b", "c", "d")
        assertEquals(null, ScheduleOccurrences.moveTarget(day, "c", "a"))
        assertEquals("a", ScheduleOccurrences.moveTarget(day, "d", "b"))
        assertEquals("b", ScheduleOccurrences.moveTarget(day, "a", "c"))
        assertEquals("d", ScheduleOccurrences.moveTarget(day, "a", null))
        assertEquals("c", ScheduleOccurrences.moveTarget(day, "d", null))
    }

    private fun schedule(
        id: String,
        amount: Long,
        sortOrder: Double,
        date: Int = 20261028,
    ) = ScheduleSummary(
        id = id,
        name = id,
        accountId = "checking",
        accountName = "Checking",
        payee = id,
        amountMinor = amount,
        amountOp = "is",
        categoryLabel = "",
        nextDate = date,
        completed = false,
        postsTransaction = false,
        repeat = ScheduleRepeat.MONTHLY,
        interval = 1,
        dates = listOf(date),
        sortOrder = sortOrder,
    )
}
