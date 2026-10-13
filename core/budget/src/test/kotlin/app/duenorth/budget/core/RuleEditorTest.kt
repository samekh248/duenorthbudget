package app.duenorth.budget.core

import app.duenorth.budget.core.parity.ParityFixture
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 010 T039, T041: rule ordering, the new ops, and the save budget with 100 rules. */
class RuleEditorTest {
    private val fixture = ParityFixture.load("schedules-rules", "learning-off")
    private val food = fixture.id("categories", "Food")
    private val general = fixture.id("categories", "General")
    private val bills = fixture.id("categories", "Bills")
    private val cafe = fixture.id("payees", "Cafe")

    private fun rule(
        stage: String?,
        condition: RulePart,
        category: String,
    ) = RuleDraft(null, stage, "and", listOf(condition), listOf(RulePart.action("category", "set", JsonPrimitive(category))))

    private fun view(
        amount: Long = -3000L,
        notes: String? = null,
    ) = RuleTransactionView(fixture.id("accounts", "Checking"), cafe, "Cafe", null, amount, notes, "2026-10-07")

    @Test
    fun postStageRunsLastAndWins() {
        withSession { session ->
            val book = RuleEditorBook(session)
            book.save(rule("post", RulePart.condition("payee", "is", JsonPrimitive(cafe)), general))
            book.save(rule(null, RulePart.condition("payee", "is", JsonPrimitive(cafe)), food))
            book.save(rule("pre", RulePart.condition("payee", "is", JsonPrimitive(cafe)), bills))
            assertEquals(listOf("pre", null, "post"), book.list().map { it.stage })
            assertEquals(general, RulesBook(session).applyRules(view()).category)
        }
    }

    @Test
    fun newOpsMatch() {
        withSession { session ->
            val book = RuleEditorBook(session)
            book.save(rule(null, RulePart.condition("notes", "matches", JsonPrimitive("^ref-[0-9]+$")), food))
            assertEquals(food, RulesBook(session).applyRules(view(notes = "ref-42")).category)
            assertEquals(null, RulesBook(session).applyRules(view(notes = "ref-x")).category)
        }
        withSession { session ->
            RuleEditorBook(session).save(
                rule(null, RulePart.condition("amount", "gt", JsonPrimitive(5000), buildJsonObject { put("outflow", true) }), bills),
            )
            assertEquals(bills, RulesBook(session).applyRules(view(amount = -6000L)).category)
            assertEquals(null, RulesBook(session).applyRules(view(amount = -4000L)).category)
        }
        withSession { session ->
            val names = buildJsonArray { add(JsonPrimitive(cafe)) }
            RuleEditorBook(session).save(rule(null, RulePart.condition("payee", "notOneOf", names), bills))
            assertEquals(null, RulesBook(session).applyRules(view()).category)
        }
    }

    @Test
    fun anOpThePhoneDoesNotKnowNeverMatches() {
        withSession { session ->
            val odd =
                RulePart.fromRaw(
                    buildJsonObject {
                        put("op", "hasTags")
                        put("field", "notes")
                        put("value", "#x")
                        put("type", "string")
                    },
                    action = false,
                )
            assertTrue(odd.kept)
            RuleEditorBook(session).save(rule(null, odd, food))
            assertEquals(null, RulesBook(session).applyRules(view(notes = "#x")).category)
        }
    }

    @Test
    fun emptyRulesAreRefused() {
        withSession { session ->
            val book = RuleEditorBook(session)
            assertEquals(
                RuleSaveResult.Rejected(RuleEditorCopy.NEEDS_CONDITION),
                book.save(RuleDraft(null, null, "and", emptyList(), emptyList())),
            )
            assertEquals(
                RuleSaveResult.Rejected(RuleEditorCopy.NEEDS_ACTION),
                book.save(RuleDraft(null, null, "and", listOf(RulePart.condition("payee", "is", JsonPrimitive(cafe))), emptyList())),
            )
        }
    }

    @Test
    fun savingATransactionWithAHundredRulesStaysUnderFiftyMilliseconds() {
        withSession { session ->
            val book = RuleEditorBook(session)
            repeat(100) { book.save(rule(null, RulePart.condition("notes", "contains", JsonPrimitive("tag-$it")), food)) }
            val register = RegisterBook(session)
            // Warm the cache and the JIT once, then time the saves.
            register.save(TransactionDraft("warm", fixture.id("accounts", "Checking"), 20261007, "Cafe", -100L, null, "tag-1"))
            val runs = 20
            val started = System.nanoTime()
            repeat(runs) {
                register.save(TransactionDraft("t$it", fixture.id("accounts", "Checking"), 20261007, "Cafe", -100L, null, "tag-99"))
            }
            val perSave = (System.nanoTime() - started) / runs / 1_000_000.0
            assertTrue("save with 100 rules took $perSave ms", perSave < 50.0)
        }
    }

    private fun withSession(block: (SqlSession) -> Unit) {
        fixture.open().use { opened -> opened.db(block) }
    }
}
