package app.duenorth.budget.core

import app.duenorth.budget.core.parity.ParityFixture
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Rule conditions Actual evaluates (`condition.ts`) that the phone used to treat as never matching. */
class RuleConditionsTest {
    private val fixture = ParityFixture.load("schedules-rules", "learning-off")
    private val food = fixture.id("categories", "Food")
    private val bills = fixture.id("categories", "Bills")
    private val cafe = fixture.id("payees", "Cafe")
    private val checking = fixture.id("accounts", "Checking")

    private fun view(
        category: String? = null,
        notes: String? = null,
        importedPayee: String? = null,
        cleared: Boolean? = true,
        reconciled: Boolean = false,
        payee: String? = cafe,
    ) = RuleTransactionView(
        account = checking,
        payee = payee,
        payeeName = "Cafe",
        category = category,
        amount = -3000L,
        notes = notes,
        dateIso = "2026-10-07",
        cleared = cleared,
        importedPayee = importedPayee,
        reconciled = reconciled,
    )

    /** One rule, stored as Actual stores it: the condition JSON as given, setting the category to [category]. */
    private fun SqlSession.rule(
        condition: String,
        category: String = bills,
        stage: String? = null,
    ) {
        RulesBook(this).insertRule(
            stage,
            Json.parseToJsonElement("[$condition]").jsonArray,
            Json.parseToJsonElement("""[{"op":"set","field":"category","value":"$category","type":"id"}]""").jsonArray,
        )
    }

    @Test
    fun importedPayeeMatchesTheBankWording() {
        withSession { session ->
            session.rule("""{"op":"contains","field":"imported_description","value":"SQ *CAFE","type":"string"}""")
            assertEquals(bills, RulesBook(session).applyRules(view(importedPayee = "sq *cafe downtown 0042")).category)
            assertNull(RulesBook(session).applyRules(view(importedPayee = "AMZN MKTP")).category)
            assertNull(RulesBook(session).applyRules(view(importedPayee = null)).category)
        }
    }

    @Test
    fun anImportRunsImportedPayeeRules() {
        withSession { session ->
            session.rule("""{"op":"is","field":"imported_description","value":"POS 1234 CAFE","type":"string"}""")
            val draft = TransactionDraft("t-new", checking, 20261007, "Cafe", -3000L, null, null)
            val ruled = RegisterBook(session).applyRulesForImport(draft, importedPayee = "POS 1234 CAFE")
            assertEquals(bills, ruled.categoryId)
        }
    }

    @Test
    fun payeeNameReadsTheStoredName() {
        withSession { session ->
            session.rule("""{"op":"oneOf","field":"payee_name","value":["CAFE","Diner"],"type":"string"}""")
            assertEquals(bills, RulesBook(session).applyRules(view()).category)
            assertNull(RulesBook(session).applyRules(view(payee = null)).category)
        }
    }

    @Test
    fun categoryGroupNeedsACategory() {
        withSession { session ->
            val group = session.query("SELECT cat_group FROM categories WHERE id = ?", listOf(food)).single().str("cat_group")
            session.rule("""{"op":"is","field":"category_group","value":"$group","type":"id"}""", stage = "post")
            assertEquals(bills, RulesBook(session).applyRules(view(category = food)).category)
            assertNull(RulesBook(session).applyRules(view(category = null)).category)
        }
        withSession { session ->
            session.rule("""{"op":"isNot","field":"category_group","value":"no-such-group","type":"id"}""")
            // No category means no group: Actual's condition is false even for "is not".
            assertNull(RulesBook(session).applyRules(view(category = null)).category)
        }
    }

    @Test
    fun clearedAndReconciledAreBooleans() {
        withSession { session ->
            session.rule("""{"op":"is","field":"cleared","value":false,"type":"boolean"}""")
            assertEquals(bills, RulesBook(session).applyRules(view(cleared = false)).category)
            assertNull(RulesBook(session).applyRules(view(cleared = true)).category)
        }
        withSession { session ->
            session.rule("""{"op":"is","field":"reconciled","value":true,"type":"boolean"}""")
            assertEquals(bills, RulesBook(session).applyRules(view(reconciled = true)).category)
            assertNull(RulesBook(session).applyRules(view(reconciled = false)).category)
        }
    }

    @Test
    fun accountOnOrOffBudget() {
        withSession { session ->
            session.rule("""{"op":"onBudget","field":"acct","value":null,"type":"id"}""")
            assertEquals(bills, RulesBook(session).applyRules(view()).category)
        }
        withSession { session ->
            session.rule("""{"op":"offBudget","field":"acct","value":null,"type":"id"}""")
            assertNull(RulesBook(session).applyRules(view()).category)
        }
    }

    @Test
    fun tagsInNotes() {
        withSession { session ->
            session.rule("""{"op":"hasTags","field":"notes","value":"#trip #food","type":"string"}""")
            assertEquals(bills, RulesBook(session).applyRules(view(notes = "lunch #Food #trip")).category)
            assertNull(RulesBook(session).applyRules(view(notes = "lunch #trip")).category)
            // A doubled # escapes the tag, and a longer word is a different tag.
            assertNull(RulesBook(session).applyRules(view(notes = "##trip #foods")).category)
        }
        withSession { session ->
            session.rule("""{"op":"hasAnyTag","field":"notes","value":"trip food","type":"string"}""")
            assertEquals(bills, RulesBook(session).applyRules(view(notes = "dinner #food")).category)
            assertNull(RulesBook(session).applyRules(view(notes = "dinner food")).category)
        }
    }

    @Test
    fun matchesIgnoresCaseLikeActual() {
        withSession { session ->
            session.rule("""{"op":"matches","field":"notes","value":"^REF-[0-9]+$","type":"string"}""")
            assertEquals(bills, RulesBook(session).applyRules(view(notes = "ref-42")).category)
        }
    }

    @Test
    fun registerOnlyFieldsNeverMatchARule() {
        withSession { session ->
            session.rule("""{"op":"is","field":"transfer","value":false,"type":"boolean"}""")
            assertNull(RulesBook(session).applyRules(view()).category)
        }
    }

    private fun withSession(block: (SqlSession) -> Unit) {
        fixture.open().use { opened -> opened.db(block) }
    }
}
