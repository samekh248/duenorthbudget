package app.duenorth.budget.core.parity

import app.duenorth.budget.core.BudgetClock
import app.duenorth.budget.core.CategoryLearning
import app.duenorth.budget.core.RegisterBook
import app.duenorth.budget.core.RuleDraft
import app.duenorth.budget.core.RuleEditorBook
import app.duenorth.budget.core.RulePart
import app.duenorth.budget.core.RuleSaveResult
import app.duenorth.budget.core.ScheduleDraft
import app.duenorth.budget.core.ScheduleEnd
import app.duenorth.budget.core.ScheduleRepeat
import app.duenorth.budget.core.SchedulesBook
import app.duenorth.budget.core.SqlSession
import app.duenorth.budget.core.SyncSchema
import app.duenorth.budget.core.TransactionDraft
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/** Spec 010 T039, T040: schedules, rules, and category learning against desktop Actual 26.8.1. */
class SchedulesRulesParityTest {
    private val area = "schedules-rules"

    @Test
    fun payeeRuleSetsTheCategoryOfANewTransaction() {
        val fixture = ParityFixture.load(area, "rule-payee-category")
        val rule = fixture.expectedRows("rules").single()["id"] as String
        val txn = fixture.expectedRows("transactions").single { it["amount"] == -800L }["id"] as String
        run(fixture, listOf(rule)) { session, ids ->
            val saved =
                RuleEditorBook(session, ids).save(
                    RuleDraft(
                        id = null,
                        stage = null,
                        conditionsOp = "and",
                        conditions = listOf(RulePart.condition("payee", "is", JsonPrimitive(fixture.id("payees", "Corner Shop")))),
                        actions = listOf(RulePart.action("category", "set", JsonPrimitive(fixture.id("categories", "Food")))),
                    ),
                )
            assertEquals(RuleSaveResult.Saved(rule), saved)
            RegisterBook(session, ids).save(
                TransactionDraft(txn, fixture.id("accounts", "Checking"), 20261002, "Corner Shop", -800L, null, null),
            )
            fixture.assertTable(session, "rules")
            fixture.assertTable(session, "transactions", ignore = setOf("sort_order", "cleared"))
        }
    }

    @Test
    fun ruleWithPartsThePhoneDoesNotEditIsSavedBackUnchanged() {
        val fixture = ParityFixture.load(area, "rule-kept-from-file")
        val row = fixture.expectedRows("rules").single()
        val id = row["id"] as String
        run(fixture, emptyList()) { session, ids ->
            // The rule as desktop wrote it.
            SyncSchema.writeRow(
                session,
                "rules",
                id,
                "stage" to row["stage"],
                "conditions_op" to row["conditions_op"],
                "conditions" to row["conditions"],
                "actions" to row["actions"],
                "tombstone" to 0,
            )
            val book = RuleEditorBook(session, ids)
            val draft = book.draft(id)!!
            assertTrue(draft.conditions.first().kept)
            assertTrue(book.list().single().hasKept)
            assertEquals(RuleSaveResult.Saved(id), book.save(draft.copy(conditionsOp = "or")))
            fixture.assertTable(session, "rules")
            // Editing a known part keeps the unknown one byte for byte.
            val edited =
                draft.copy(
                    actions = listOf(draft.actions.first(), RulePart.action("notes", "append-notes", JsonPrimitive(" (auto)"))),
                )
            book.save(edited)
            val conditions = session.query("SELECT conditions FROM rules WHERE id = ?", listOf(id)).single().str("conditions")
            assertEquals(row["conditions"], conditions)
        }
    }

    @Test
    fun monthlyScheduleAutoPostsOnceAcrossTwoRuns() {
        val fixture = ParityFixture.load(area, "schedule-monthly-autopost")
        val schedule = fixture.id("schedules", "Internet")
        val rule = fixture.expectedRows("rules").single()["id"] as String
        val next = fixture.expectedRows("schedules_next_date").single()["id"] as String
        val txn = fixture.expectedRows("transactions").single { it["schedule"] == schedule }["id"] as String
        run(fixture, listOf(schedule, rule, next, txn)) { session, ids ->
            val book = SchedulesBook(session, BudgetClock { fixture.today }, ids)
            book.save(
                ScheduleDraft(
                    name = "Internet",
                    accountId = fixture.id("accounts", "Checking"),
                    payeeName = "Cafe",
                    amountMinor = -6000L,
                    repeat = ScheduleRepeat.MONTHLY,
                    startIso = "2026-10-07",
                    postsTransaction = true,
                ),
            )
            while (book.autoPostOne()) Unit
            // The second run finds the posted transaction and does nothing.
            while (book.autoPostOne()) Unit
            fixture.assertTable(session, "schedules")
            fixture.assertTable(session, "rules")
            fixture.assertTable(session, "schedules_next_date", ignore = setOf("local_next_date_ts", "base_next_date_ts"))
            fixture.assertTable(session, "transactions", ignore = setOf("sort_order"))
        }
    }

    @Test
    fun lastFridayPatternAndWeekendMoveAreKept() {
        val fixture = ParityFixture.load(area, "schedule-pattern-kept")
        val schedule = fixture.id("schedules", "Payday")
        val ruleRow = fixture.expectedRows("rules").single()
        val next = fixture.expectedRows("schedules_next_date").single()["id"] as String
        val stored =
            Json
                .parseToJsonElement(ruleRow["conditions"] as String)
                .jsonArray
                .last()
                .jsonObject["value"]!!
                .jsonObject
        run(fixture, listOf(schedule, ruleRow["id"] as String, next)) { session, ids ->
            SchedulesBook(session, BudgetClock { fixture.today }, ids).save(
                ScheduleDraft(
                    name = "Payday",
                    accountId = fixture.id("accounts", "Checking"),
                    amountMinor = 290_000L,
                    amountOp = "isbetween",
                    amount2Minor = 310_000L,
                    repeat = ScheduleRepeat.MONTHLY,
                    startIso = "2026-10-01",
                    end = ScheduleEnd.AFTER,
                    endOccurrences = 12,
                    keptDate = stored,
                ),
            )
            fixture.assertTable(session, "schedules")
            fixture.assertTable(session, "rules")
            fixture.assertTable(session, "schedules_next_date", ignore = setOf("local_next_date_ts", "base_next_date_ts"))
            // Reading it back for the editor keeps the parts it does not change.
            val draft = SchedulesBook(session, BudgetClock { fixture.today }, ids).draft(schedule)!!
            assertTrue(draft.hasKeptDateParts)
            assertEquals(ScheduleEnd.AFTER, draft.end)
            assertEquals(12, draft.endOccurrences)
        }
    }

    @Test
    fun learningOnWritesAPayeeRule() {
        val fixture = ParityFixture.load(area, "learning-on")
        val rule = fixture.expectedRows("rules").single()["id"] as String
        fixture.open().use { opened ->
            opened.library.setPreference(opened.budgetId, "learn-categories", "true")
            opened.library.setTransactionCategory(opened.budgetId, CAFE_UNCATEGORIZED, fixture.id("categories", "Food"), force = false)
            opened.db { session ->
                val learned = session.query("SELECT id FROM rules").single().str("id")!!
                // The phone picks its own id; compare the row under the desktop's.
                session.exec("UPDATE rules SET id = ? WHERE id = ?", listOf(rule, learned))
                fixture.assertTable(session, "rules")
                fixture.assertTable(session, "transactions", ignore = setOf("sort_order"))
            }
        }
    }

    @Test
    fun learningOffWritesNoRule() {
        val fixture = ParityFixture.load(area, "learning-off")
        fixture.open().use { opened ->
            opened.library.setLearning(opened.budgetId, false)
            assertEquals(false, opened.library.learningEnabled(opened.budgetId))
            opened.library.setTransactionCategory(opened.budgetId, CAFE_UNCATEGORIZED, fixture.id("categories", "Food"), force = false)
            opened.db { session ->
                fixture.assertTable(session, "rules")
                fixture.assertTable(session, "transactions", ignore = setOf("sort_order"))
                assertEquals(false, CategoryLearning(session).enabled())
            }
        }
    }

    private fun run(
        fixture: ParityFixture,
        newIds: List<String>,
        block: (SqlSession, () -> String) -> Unit,
    ) {
        val queue = ArrayDeque(newIds)
        val ids = { queue.removeFirstOrNull() ?: UUID.randomUUID().toString() }
        fixture.open().use { opened -> opened.db { session -> block(session, ids) } }
    }

    private companion object {
        const val CAFE_UNCATEGORIZED = "cdbe716d-0b36-4bd5-9af9-5d3ca34746e6"
    }
}
