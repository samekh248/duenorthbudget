package app.duenorth.budget.core.parity

import app.duenorth.budget.core.ScheduleCopy
import app.duenorth.budget.core.ScheduleWriteResult
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Moving schedules among those due the same day, against desktop Actual 26.10.0 (`parity/schedule-order/`). */
class ScheduleOrderParityTest {
    @Test
    fun movesLikeDesktop() {
        val scenarios = ParityFixture.scenarios("schedule-order")
        assertEquals(5, scenarios.size)
        for (name in scenarios) {
            val fixture = ParityFixture.load("schedule-order", name)
            fixture.open().use { opened ->
                assertTrue(opened.library.canReorderSchedules(opened.budgetId))
                for (step in fixture.action.jsonObject
                    .getValue("steps")
                    .jsonArray) {
                    val args = step.jsonObject.getValue("args").jsonObject
                    val id = fixture.id("schedules", args.getValue("id").jsonPrimitive.content)
                    val target = args["targetId"]?.jsonPrimitive?.contentOrNull?.let { fixture.id("schedules", it) }
                    assertEquals(name, ScheduleWriteResult.Posted, opened.library.moveSchedule(opened.budgetId, id, target))
                }
                opened.db { session -> fixture.assertTable(session, "schedules") }
            }
        }
    }

    @Test
    fun schedulesDueTheSameDayListInDesktopOrder() {
        val fixture = ParityFixture.load("schedule-order", "move-top-then-after")
        fixture.open().use { opened ->
            for (step in listOf("Gym" to null, "Water" to "Gym")) {
                opened.library.moveSchedule(
                    opened.budgetId,
                    fixture.id("schedules", step.first),
                    step.second?.let { fixture.id("schedules", it) },
                )
            }
            // Gym and Water were moved up; Rent, Phone and Water's old place tie at 0 and go by amount.
            assertEquals(
                listOf("Gym", "Water", "Rent", "Phone", "Power"),
                opened.library.readSchedules(opened.budgetId).map { it.name },
            )
        }
    }

    @Test
    fun anOlderBudgetCannotBeReordered() {
        val fixture = ParityFixture.load("schedules-rules", "schedule-monthly-autopost")
        fixture.open().use { opened ->
            assertFalse(opened.library.canReorderSchedules(opened.budgetId))
            assertEquals(
                ScheduleWriteResult.Rejected(ScheduleCopy.NO_ORDER),
                opened.library.moveSchedule(opened.budgetId, "any", null),
            )
        }
    }
}
