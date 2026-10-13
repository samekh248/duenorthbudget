package app.duenorth.budget.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Spec 010 T037: Actual's recurrence configs, including the parts the editor keeps. */
class ScheduleRecurrenceTest {
    private fun config(json: String) = Json.parseToJsonElement(json).jsonObject

    private fun dates(
        json: String,
        from: String,
        limit: Int = 4,
    ) = ScheduleRecurrence.occurrences(config(json), LocalDate.parse(from), limit).map { it.toString() }

    @Test
    fun monthlyFromTheStartDay() {
        assertEquals(
            listOf("2026-10-07", "2026-11-07", "2026-12-07"),
            dates("""{"start":"2026-10-07","frequency":"monthly","interval":1,"endMode":"never"}""", "2026-10-01", 3),
        )
    }

    @Test
    fun monthlyOnThe31stClampsToShortMonths() {
        assertEquals(
            listOf("2027-01-31", "2027-02-28", "2027-03-31"),
            dates("""{"start":"2027-01-31","frequency":"monthly","interval":1,"endMode":"never"}""", "2027-01-01", 3),
        )
    }

    @Test
    fun everyTwoWeeks() {
        assertEquals(
            listOf("2026-10-02", "2026-10-16", "2026-10-30"),
            dates("""{"start":"2026-10-02","frequency":"weekly","interval":2,"endMode":"never"}""", "2026-10-01", 3),
        )
    }

    @Test
    fun lastFridayMovedBeforeTheWeekend() {
        val payday =
            """{"start":"2026-10-01","frequency":"monthly","interval":1,"endMode":"after_n_occurrences","endOccurrences":12,
               "patterns":[{"type":"FR","value":-1}],"skipWeekend":true,"weekendSolveMode":"before"}"""
        assertEquals(listOf("2026-10-30", "2026-11-27", "2026-12-25"), dates(payday, "2026-10-07", 3))
    }

    @Test
    fun lastDayPatternOnAWeekendMovesAfter() {
        // 2026-10-31 is a Saturday.
        val rent =
            """{"start":"2026-10-01","frequency":"monthly","interval":1,"endMode":"never",
               "patterns":[{"type":"day","value":-1}],"skipWeekend":true,"weekendSolveMode":"after"}"""
        assertEquals(listOf("2026-11-02", "2026-11-30"), dates(rent, "2026-10-07", 2))
    }

    @Test
    fun endAfterOccurrencesAndOnDate() {
        assertEquals(
            listOf("2026-10-07", "2026-11-07"),
            dates(
                """{"start":"2026-10-07","frequency":"monthly","interval":1,"endMode":"after_n_occurrences","endOccurrences":2}""",
                "2026-01-01",
            ),
        )
        assertEquals(
            listOf("2026-10-07", "2026-10-08", "2026-10-09"),
            dates(
                """{"start":"2026-10-07","frequency":"daily","interval":1,"endMode":"on_date","endDate":"2026-10-09"}""",
                "2026-01-01",
                10,
            ),
        )
        // Counting starts at the first occurrence, not at [from].
        assertEquals(
            listOf("2026-11-07"),
            dates(
                """{"start":"2026-10-07","frequency":"monthly","interval":1,"endMode":"after_n_occurrences","endOccurrences":2}""",
                "2026-10-08",
            ),
        )
    }

    @Test
    fun yearlyAndOneTime() {
        assertEquals(
            listOf("2027-03-01", "2028-03-01"),
            dates("""{"start":"2026-03-01","frequency":"yearly","interval":1,"endMode":"never"}""", "2026-10-07", 2),
        )
        val once = config("""{"op":"is","field":"date","value":"2026-12-01"}""")
        assertEquals("2026-12-01", ScheduleRecurrence.firstOnOrAfter(once, LocalDate.parse("2026-10-07")))
        assertNull(ScheduleRecurrence.nextDate(once, "2026-12-01", afterPost = true))
    }
}
