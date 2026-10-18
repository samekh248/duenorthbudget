package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class MatchBookTest {
    @Test
    fun pairsABankTransactionWithTheEntryOfTheSameAmountNearby() {
        db { session ->
            entered(session, "mine", date = 20261014, category = "c-dining", note = "with Sam")
            bank(session, "bank", date = 20261015)
            val pair = MatchFinder.find(session).single()
            assertEquals("bank", pair.bank.id)
            assertEquals("mine", pair.entered.id)
            assertEquals(1, pair.daysApart)
            assertEquals("CORNER COFFEE #42", pair.bank.payee)
            assertEquals("Dining", pair.entered.categoryName)
            assertEquals(listOf("bank|mine"), MatchFinder.find(session, "visa").map { it.key })
            assertTrue(MatchFinder.find(session, "checking").isEmpty())
        }
    }

    @Test
    fun skipsOtherAmountsAccountsFarDatesAndTwoBankOrTwoEnteredRows() {
        db { session ->
            bank(session, "bank", date = 20261015)
            entered(session, "other-amount", date = 20261015, amount = -576)
            entered(session, "other-account", date = 20261015, account = "checking")
            entered(session, "too-far", date = 20261007)
            bank(session, "bank-twin", date = 20261015, financialId = "f-2")
            assertTrue(MatchFinder.find(session).isEmpty())
        }
    }

    @Test
    fun eachTransactionSitsInOnePairTakingTheClosestDate() {
        db { session ->
            entered(session, "far", date = 20261010)
            entered(session, "near", date = 20261014)
            bank(session, "first", date = 20261015, financialId = "f-1")
            bank(session, "second", date = 20261012, financialId = "f-2")
            val pairs = MatchFinder.find(session).associate { it.bank.id to it.entered.id }
            assertEquals(mapOf("first" to "near", "second" to "far"), pairs)
        }
    }

    @Test
    fun matchLeavesOneTransactionWithBothInItsNote() {
        db { session ->
            entered(session, "mine", date = 20261014, category = "c-dining", note = "with Sam")
            bank(session, "bank", date = 20261015)
            assertEquals(WriteResult.Saved, MatchBook(session, TODAY).match("bank", "mine"))
            val alive = session.query("SELECT id FROM transactions WHERE IFNULL(tombstone, 0) = 0").map { it.str("id") }
            assertEquals(listOf("bank"), alive)
            val row = session.query("SELECT * FROM transactions WHERE id = 'bank'").single()
            assertEquals("c-dining", row.str("category"))
            assertEquals("p-corner", row.str("description"))
            assertEquals("f-1", row.str("financial_id"))
            assertEquals(20261015L, row.long("date"))
            assertEquals(
                "with Sam\nMatched 10/18: bank CORNER COFFEE #42 (10/15) + entered Corner Coffee (10/14)",
                row.str("notes"),
            )
            assertTrue(MatchFinder.find(session).isEmpty())
        }
    }

    @Test
    fun matchWithNoCategoryOnEitherSideLeavesItForTheInbox() {
        db { session ->
            entered(session, "mine", date = 20261014)
            bank(session, "bank", date = 20261015)
            MatchBook(session, TODAY).match("bank", "mine")
            val shell = ShellReader.read(session, BudgetMetadata("home", "Home"), TODAY)
            assertEquals(listOf("bank"), shell.inbox.map { it.id })
            assertTrue(shell.matches.isEmpty())
            assertNull(session.query("SELECT category FROM transactions WHERE id = 'bank'").single().str("category"))
        }
    }

    @Test
    fun notAMatchIsRememberedInASyncedPreference() {
        db { session ->
            entered(session, "mine", date = 20261014)
            bank(session, "bank", date = 20261015)
            assertEquals(1, ShellReader.read(session, BudgetMetadata("home", "Home"), TODAY).matches.size)
            MatchBook(session, TODAY).notAMatch("bank", "mine")
            assertTrue(MatchFinder.find(session).isEmpty())
            assertEquals(setOf("bank|mine"), MatchFinder.dismissed(session))
            assertTrue(RegisterBook(session).read("visa")!!.matches.isEmpty())
        }
    }

    @Test
    fun matchingAGonePairIsRejected() {
        db { session ->
            entered(session, "mine", date = 20261014)
            bank(session, "bank", date = 20261015)
            session.exec("UPDATE transactions SET tombstone = 1 WHERE id = 'mine'")
            assertEquals(WriteResult.Rejected(MatchCopy.GONE), MatchBook(session, TODAY).match("bank", "mine"))
        }
    }

    private fun entered(
        session: SqlSession,
        id: String,
        date: Int,
        amount: Long = -575,
        account: String = "visa",
        category: String? = null,
        note: String? = null,
    ) = insert(session, id, date, amount, account, "p-corner", category, note, null, null)

    private fun bank(
        session: SqlSession,
        id: String,
        date: Int,
        financialId: String = "f-1",
    ) = insert(session, id, date, -575, "visa", "p-bank", null, null, financialId, "CORNER COFFEE #42")

    private fun insert(
        session: SqlSession,
        id: String,
        date: Int,
        amount: Long,
        account: String,
        payee: String,
        category: String?,
        note: String?,
        financialId: String?,
        imported: String?,
    ) {
        session.exec(
            """
            INSERT INTO transactions (id, acct, amount, description, notes, date, category, financial_id, imported_description, sort_order)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            listOf(id, account, amount, payee, note, date, category, financialId, imported, date.toDouble()),
        )
    }

    private fun db(block: (SqlSession) -> Unit) {
        val file = File.createTempFile("match", ".sqlite")
        file.delete()
        try {
            JdbcSessionOpener().use(file) { session ->
                ActualSchema.create(session)
                session.exec("INSERT OR REPLACE INTO preferences (id, value) VALUES ('budgetType', 'envelope')")
                session.exec("INSERT OR REPLACE INTO preferences (id, value) VALUES ('currency', 'USD')")
                session.exec("INSERT INTO category_groups (id, name, is_income, sort_order) VALUES ('g-fun', 'Fun', 0, 0)")
                session.exec("INSERT INTO categories (id, name, is_income, cat_group) VALUES ('c-dining', 'Dining', 0, 'g-fun')")
                session.exec("INSERT INTO accounts (id, name, offbudget, type, sort_order) VALUES ('visa', 'Visa', 0, 'credit', 0)")
                session.exec(
                    "INSERT INTO accounts (id, name, offbudget, type, sort_order) VALUES ('checking', 'Checking', 0, 'checking', 1)",
                )
                session.exec("INSERT INTO payees (id, name) VALUES ('p-corner', 'Corner Coffee')")
                session.exec("INSERT INTO payees (id, name) VALUES ('p-bank', 'CORNER COFFEE #42')")
                block(session)
            }
        } finally {
            file.delete()
        }
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 10, 18)
    }
}
