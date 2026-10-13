package app.duenorth.budget.core

import app.duenorth.budget.core.parity.ParityFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Spec 010 T038: auto-post runs once per due occurrence, late opens catch up, nothing doubles. */
class ScheduleAutoPostTest {
    private val fixture = ParityFixture.load("schedules-rules", "learning-off")
    private val checking = fixture.id("accounts", "Checking")

    private fun internet(start: String) =
        ScheduleDraft(
            name = "Internet",
            accountId = checking,
            payeeName = "Cafe",
            amountMinor = -6000L,
            repeat = ScheduleRepeat.MONTHLY,
            startIso = start,
            postsTransaction = true,
        )

    private fun posted(session: SqlSession): List<Long> =
        session
            .query("SELECT date FROM transactions WHERE schedule IS NOT NULL AND IFNULL(tombstone, 0) = 0 ORDER BY date")
            .map { it.long("date") }

    @Test
    fun aLateOpenPostsEachMissedOccurrenceOnce() {
        fixture.open().use { opened ->
            opened.db { SchedulesBook(it, BudgetClock { LocalDate.parse("2026-07-07") }).save(internet("2026-07-07")) }
            val late = BudgetClock { LocalDate.parse("2026-10-20") }
            val library = BudgetLibrary(opened.root, JdbcSessionOpener(), late)
            val started = System.nanoTime()
            val steps = library.autoPostSchedules(opened.budgetId)
            val millis = (System.nanoTime() - started) / 1_000_000
            assertEquals(4, steps)
            assertTrue("auto-post took $millis ms", millis < 1_000)
            assertEquals(0, library.autoPostSchedules(opened.budgetId))
            opened.db { session ->
                assertEquals(listOf(20260707L, 20260807L, 20260907L, 20261007L), posted(session))
                val next = session.query("SELECT local_next_date FROM schedules_next_date").single().long("local_next_date")
                assertEquals(20261107L, next)
            }
            // Each posting is its own undo step.
            assertEquals(UndoCopy.AUTO_POST, library.undoLabel(opened.budgetId))
        }
    }

    @Test
    fun anExistingMatchOnlyAdvancesTheDate() {
        fixture.open().use { opened ->
            opened.db { session ->
                val book = SchedulesBook(session, BudgetClock { fixture.today })
                book.save(internet("2026-10-07"))
                RegisterBook(session).save(TransactionDraft("typed", checking, 20261007, "Cafe", -6000L, null, null))
                while (book.autoPostOne()) Unit
                assertEquals(emptyList<Long>(), posted(session))
                val next = session.query("SELECT local_next_date FROM schedules_next_date").single().long("local_next_date")
                assertEquals(20261107L, next)
            }
        }
    }

    @Test
    fun schedulesThatDoNotPostAreLeftForTheUser() {
        fixture.open().use { opened ->
            opened.db { session ->
                val book = SchedulesBook(session, BudgetClock { fixture.today })
                book.save(internet("2026-10-01").copy(postsTransaction = false))
                assertEquals(false, book.autoPostOne())
                assertEquals(emptyList<Long>(), posted(session))
            }
        }
    }

    @Test
    fun editorRejectsMissingAccountOrStart() {
        fixture.open().use { opened ->
            opened.db { session ->
                val book = SchedulesBook(session, BudgetClock { fixture.today })
                assertEquals(
                    ScheduleWriteResult.Rejected(ScheduleEditorCopy.CHOOSE_ACCOUNT),
                    book.save(internet("2026-10-07").copy(accountId = "")),
                )
                assertEquals(
                    ScheduleWriteResult.Rejected(ScheduleEditorCopy.ENTER_START),
                    book.save(internet("not a date")),
                )
            }
        }
    }
}
