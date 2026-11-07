package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class TransferGuessTest {
    @Test
    fun aBanksTransferWordsAndLastDigitsNameTheAccount() {
        db { session ->
            bank(session, "t", 20261104, -50_000, "checking", "ONLINE TRANSFER TO SAV XXXXXX4821 REF #IB0TQ7")
            val guess = guesses(session).getValue("t")
            assertEquals("savings", guess.accountId)
            assertNull(guess.partnerId)
            assertEquals(listOf("says transfer", "ends in 4821"), guess.reasons)
            assertEquals("transfer to Ally Savings 4821", guess.label(-50_000))
        }
    }

    @Test
    fun aShortFormOfTheAccountsKindNamesItToo() {
        db { session ->
            session.exec("UPDATE accounts SET name = 'Rainy Day Savings' WHERE id = 'savings'")
            bank(session, "t", 20261104, 20_000, "checking", "XFER FROM SVGS")
            val guess = guesses(session).getValue("t")
            assertEquals("savings", guess.accountId)
            assertEquals(listOf("says transfer", "names “savings”"), guess.reasons)
            assertEquals("transfer from Rainy Day Savings", guess.label(20_000))
        }
    }

    @Test
    fun theOtherSideAlreadyInAnotherAccountIsLinked() {
        db { session ->
            bank(session, "out", 20261104, -50_000, "checking", "ONLINE BANKING 991827")
            bank(session, "in", 20261105, 50_000, "savings", "DEPOSIT 7Q2", category = "c-income")
            val guess = guesses(session).getValue("out")
            assertEquals("savings", guess.accountId)
            assertEquals("in", guess.partnerId)
            assertEquals(listOf("other side in Ally Savings 4821, 1 day apart"), guess.reasons)

            assertEquals(WriteResult.Saved, TransferBook(session, TODAY).make("out", "savings", "in"))
            val out = session.query("SELECT transferred_id, category FROM transactions WHERE id = 'out'").single()
            val into = session.query("SELECT transferred_id, category FROM transactions WHERE id = 'in'").single()
            assertEquals("in", out.str("transferred_id"))
            assertEquals("out", into.str("transferred_id"))
            assertNull(into.str("category"))
            assertTrue(inbox(session).none { it.id == "out" })
        }
    }

    @Test
    fun wordingThatWentToAnAccountBeforeGoesThereAgain() {
        db { session ->
            bank(session, "old", 20261004, -25_000, "checking", "INTERNAL DEBIT 0000123")
            TransferBook(session, TODAY).make("old", "savings", null)
            bank(session, "new", 20261104, -30_000, "checking", "INTERNAL DEBIT 0000456")
            val guess = guesses(session).getValue("new")
            assertEquals("savings", guess.accountId)
            assertEquals(listOf("went there once before"), guess.reasons)
        }
    }

    @Test
    fun makingTheTransferAddsTheOtherSideAndLeavesTheInbox() {
        db { session ->
            bank(session, "t", 20261104, -50_000, "checking", "ONLINE TRANSFER TO SAV XXXXXX4821")
            assertEquals(WriteResult.Saved, TransferBook(session, TODAY, ids()).make("t", "savings", null))
            val side = session.query("SELECT transferred_id, financial_id FROM transactions WHERE id = 't'").single()
            val partner = side.str("transferred_id").orEmpty()
            assertEquals("f-t", side.str("financial_id"))
            val other = session.query("SELECT acct, amount, transferred_id FROM transactions WHERE id = ?", listOf(partner)).single()
            assertEquals("savings", other.str("acct"))
            assertEquals(50_000L, other.long("amount"))
            assertEquals("t", other.str("transferred_id"))
            assertTrue(inbox(session).isEmpty())
        }
    }

    @Test
    fun aCardPaymentGoesToTheCard() {
        db { session ->
            bank(session, "t", 20261104, -12_345, "checking", "CITI AUTOPAY PAYMENT 261104")
            val guess = guesses(session).getValue("t")
            assertEquals("visa", guess.accountId)
            assertEquals(listOf("says payment", "names “citi”"), guess.reasons)
        }
    }

    @Test
    fun storesAndVagueTransfersAreLeftAlone() {
        db { session ->
            bank(session, "store", 20261104, -4_210, "checking", "SAVE MART 0412")
            bank(session, "shop", 20261104, -4_821, "checking", "CORNER STORE 4821")
            bank(session, "vague", 20261103, -10_000, "checking", "ONLINE TRANSFER REF 88812")
            assertEquals(emptyMap<String, TransferGuess>(), guesses(session))
        }
    }

    @Test
    fun twoAccountsWithTheOtherSideIsTooCloseToCall() {
        db { session ->
            bank(session, "out", 20261104, -50_000, "checking", "ONLINE BANKING 991827")
            bank(session, "a", 20261105, 50_000, "savings", "DEPOSIT", category = "c-income")
            bank(session, "b", 20261105, 50_000, "visa", "PAYMENT RECEIVED", category = "c-income")
            assertNull(guesses(session)["out"])
        }
    }

    @Test
    fun bothSidesInTheInboxAreGuessedOnceFromTheMoneyOut() {
        db { session ->
            bank(session, "out", 20261104, -50_000, "checking", "TRANSFER TO SAV")
            bank(session, "in", 20261104, 50_000, "savings", "TRANSFER FROM CHK")
            val guesses = guesses(session)
            assertEquals("in", guesses.getValue("out").partnerId)
            assertNull(guesses["in"])
        }
    }

    @Test
    fun theShellCarriesTheGuessOnItsInboxRow() {
        db { session ->
            bank(session, "t", 20261104, -50_000, "checking", "ONLINE TRANSFER TO SAV XXXXXX4821")
            assertEquals("savings", inbox(session).single().transfer?.accountId)
        }
    }

    private fun guesses(session: SqlSession) = TransferGuesser.guess(session, inbox(session).map { it.copy(transfer = null) }, TODAY)

    private fun inbox(session: SqlSession) = ShellReader.read(session, BudgetMetadata("home", "Home"), TODAY).inbox

    private fun ids(): () -> String {
        var n = 0
        return { "id-${++n}" }
    }

    private fun bank(
        session: SqlSession,
        id: String,
        date: Int,
        amount: Long,
        account: String,
        imported: String,
        category: String? = null,
    ) {
        val payee = "p-$id"
        session.exec("INSERT INTO payees (id, name) VALUES (?, ?)", listOf(payee, imported))
        session.exec(
            """
            INSERT INTO transactions (id, acct, amount, description, date, category, financial_id, imported_description, sort_order)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            listOf(id, account, amount, payee, date, category, "f-$id", imported, date.toDouble()),
        )
    }

    private fun db(block: (SqlSession) -> Unit) {
        val file = File.createTempFile("transfer", ".sqlite")
        file.delete()
        try {
            JdbcSessionOpener().use(file) { session ->
                ActualSchema.create(session)
                session.exec("INSERT OR REPLACE INTO preferences (id, value) VALUES ('budgetType', 'envelope')")
                session.exec("INSERT OR REPLACE INTO preferences (id, value) VALUES ('currency', 'USD')")
                session.exec("INSERT INTO category_groups (id, name, is_income, sort_order) VALUES ('g-in', 'Income', 1, 0)")
                session.exec("INSERT INTO categories (id, name, is_income, cat_group) VALUES ('c-income', 'Income', 1, 'g-in')")
                session.exec(
                    "INSERT INTO accounts (id, name, offbudget, type, sort_order) VALUES ('checking', 'Checking 1234', 0, 'checking', 0)",
                )
                session.exec(
                    "INSERT INTO accounts (id, name, offbudget, type, sort_order) VALUES ('savings', 'Ally Savings 4821', 0, 'savings', 1)",
                )
                session.exec("INSERT INTO accounts (id, name, offbudget, type, sort_order) VALUES ('visa', 'Citi Visa', 0, 'credit', 2)")
                session.exec(
                    "INSERT INTO accounts (id, name, offbudget, type, sort_order) VALUES ('brokerage', 'Brokerage', 1, 'investment', 3)",
                )
                block(session)
            }
        } finally {
            file.delete()
        }
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 11, 7)
    }
}
