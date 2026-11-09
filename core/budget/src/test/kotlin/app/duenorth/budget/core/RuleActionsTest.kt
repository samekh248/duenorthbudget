package app.duenorth.budget.core

import app.duenorth.budget.core.parity.ParityFixture
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rule actions as Actual runs them (`action.ts`, `rule.ts`), and when it runs them (only on new transactions). */
class RuleActionsTest {
    private val fixture = ParityFixture.load("schedules-rules", "learning-off")
    private val food = fixture.id("categories", "Food")
    private val bills = fixture.id("categories", "Bills")
    private val checking = fixture.id("accounts", "Checking")

    private val cafeCondition = """{"op":"is","field":"imported_description","value":"CAFE 42","type":"string"}"""
    private val payeeIsCafe = """{"op":"is","field":"description","value":"${fixture.id("payees", "Cafe")}","type":"id"}"""

    private fun SqlSession.rule(
        condition: String,
        vararg actions: String,
    ) {
        RulesBook(this).insertRule(
            null,
            Json.parseToJsonElement("[$condition]").jsonArray,
            Json.parseToJsonElement(actions.joinToString(",", "[", "]")).jsonArray,
        )
    }

    private fun draft(
        id: String = "t-new",
        category: String? = null,
        note: String? = null,
        cleared: Boolean? = null,
    ) = TransactionDraft(id, checking, 20261007, "Cafe", -3001L, category, note, cleared = cleared)

    private fun row(
        session: SqlSession,
        id: String,
    ) = session.query("SELECT * FROM transactions WHERE id = ?", listOf(id)).single()

    @Test
    fun anImportSavesTheDateAndClearedARuleSets() {
        withSession { session ->
            session.rule(
                cafeCondition,
                """{"op":"set","field":"date","value":"2026-10-01","type":"date"}""",
                """{"op":"set","field":"cleared","value":false,"type":"boolean"}""",
            )
            val ruled = RegisterBook(session).applyRulesForImport(draft(), importedPayee = "CAFE 42").draft
            assertEquals(20261001, ruled.date)
            assertEquals(false, ruled.cleared)
        }
    }

    @Test
    fun aNewTransactionOnlyFillsWhatThePersonLeftEmpty() {
        withSession { session ->
            session.rule(
                payeeIsCafe,
                """{"op":"set","field":"category","value":"$bills","type":"id"}""",
                """{"op":"set","field":"date","value":"2026-10-01","type":"date"}""",
                """{"op":"set","field":"cleared","value":false,"type":"boolean"}""",
                """{"op":"append-notes","value":" #work","type":"string"}""",
            )
            val book = RegisterBook(session)
            book.save(draft("picked", category = food, note = "lunch"))
            val picked = row(session, "picked")
            assertEquals(food, picked.str("category"))
            assertEquals("lunch #work", picked.str("notes"))
            // The date and the cleared flag the person gave stay.
            assertEquals(20261007L, picked.long("date"))
            assertTrue(picked.bool("cleared"))

            book.save(draft("empty"))
            assertEquals(bills, row(session, "empty").str("category"))
            assertEquals(" #work", row(session, "empty").str("notes"))
        }
    }

    @Test
    fun anEditSavesWhatThePersonTyped() {
        withSession { session ->
            val book = RegisterBook(session)
            book.save(draft("t1"))
            assertNull(row(session, "t1").str("category"))
            session.rule(payeeIsCafe, """{"op":"set","field":"category","value":"$bills","type":"id"}""")
            // Rules run only on new transactions, so a rule made later does not touch this one when it is edited.
            book.save(draft("t1", note = "edited"))
            assertNull(row(session, "t1").str("category"))
            assertEquals("edited", row(session, "t1").str("notes"))
        }
    }

    @Test
    fun aSplitRuleSharesTheAmountOut() {
        withSession { session ->
            session.rule(
                payeeIsCafe,
                """{"op":"set-split-amount","value":50,"options":{"splitIndex":1,"method":"fixed-percent"},"type":"number"}""",
                """{"op":"set","field":"category","value":"$food","options":{"splitIndex":1},"type":"id"}""",
                """{"op":"set-split-amount","options":{"splitIndex":2,"method":"remainder"},"type":"number"}""",
                """{"op":"set","field":"category","value":"$bills","options":{"splitIndex":2},"type":"id"}""",
            )
            RegisterBook(session).save(draft("split"))
            assertTrue(row(session, "split").bool("isParent"))
            assertNull(row(session, "split").str("category"))
            val lines =
                session
                    .query(
                        "SELECT category, amount FROM transactions WHERE parent_id = 'split' AND IFNULL(tombstone, 0) = 0 ORDER BY sort_order",
                    ).map { it.str("category") to it.long("amount") }
            // Half of -30.01 rounds as Actual's Math.round does; the remainder line takes what is left.
            assertEquals(listOf(food to -1500L, bills to -1501L), lines)
        }
    }

    @Test
    fun aDeleteRuleSavesTheTransactionDeleted() {
        withSession { session ->
            session.rule(payeeIsCafe, """{"op":"delete-transaction","value":null}""")
            RegisterBook(session).save(draft("gone"))
            assertTrue(row(session, "gone").bool("tombstone"))
        }
    }

    @Test
    fun formulaAndTemplateValuesAreNotWrittenAsText() {
        withSession { session ->
            session.rule(
                payeeIsCafe,
                """{"op":"set","field":"notes","value":"","options":{"template":"{{payee_name}} visit"},"type":"string"}""",
                """{"op":"set","field":"category","value":"","options":{"formula":"=IF(amount<0, 1, 2)"},"type":"id"}""",
            )
            RegisterBook(session).save(draft("templ", note = "mine"))
            assertEquals("mine", row(session, "templ").str("notes"))
            assertNull(row(session, "templ").str("category"))
        }
    }

    @Test
    fun aNewPayeeActionMakesThePayee() {
        withSession { session ->
            session.rule(cafeCondition, """{"op":"set","field":"payee_name","value":"Cafe Downtown","type":"string"}""")
            val ruled = RegisterBook(session).applyRulesForImport(draft(), importedPayee = "CAFE 42").draft
            assertEquals("Cafe Downtown", ruled.payee)
        }
    }

    @Test
    fun theCurrencyIsActualsSetting() {
        withSession { session ->
            session.exec("INSERT OR REPLACE INTO preferences (id, value) VALUES ('currency', 'USD')")
            session.exec("INSERT OR REPLACE INTO preferences (id, value) VALUES ('defaultCurrencyCode', 'JPY')")
            val yen = BudgetCurrency.read(session)
            assertEquals("JPY", yen.code)
            // Typed yen are kept in hundredths, as Actual keeps every currency.
            assertEquals(150_000L, MoneyParse.parseMagnitude("1500", yen.decimals))
            session.exec("DELETE FROM preferences WHERE id = 'defaultCurrencyCode'")
            assertEquals("USD", BudgetCurrency.read(session).code)
        }
    }

    @Test
    fun aNewBudgetSavesActualsCurrencySetting() {
        val root =
            kotlin.io.path
                .createTempDirectory("currency")
                .toFile()
        try {
            val library = BudgetLibrary(root, JdbcSessionOpener(), BudgetClock.System)
            val id = (library.create("Trip", "EUR") as CreateResult.Created).id
            val saved =
                library.useDatabase(id) { session ->
                    session.query("SELECT value FROM preferences WHERE id = 'defaultCurrencyCode'").single().str("value")
                }
            assertEquals("EUR", saved)
            assertEquals("EUR", library.readShell(id)!!.currency.code)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun withSession(block: (SqlSession) -> Unit) {
        fixture.open().use { opened -> opened.db(block) }
    }
}
