package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class AssistantTest {
    @Test
    fun aBankTransactionAndItsEntryAreADuplicateThatMatches() {
        db { session ->
            entered(session, "mine", date = 20261014)
            bank(session, "bank", date = 20261015)
            val finding = read(session).findings.single()
            assertEquals(FindingKind.DUPLICATE, finding.kind)
            assertEquals("match:bank|mine", finding.key)
            assertEquals(listOf(AssistantCopy.BANK_SYNC, AssistantCopy.ENTERED), finding.sides.map { it.source })
            assertEquals(FindingAction.Match("bank", "mine"), finding.actions.first())
        }
    }

    @Test
    fun aSyncAndAFileImportOfOnePurchaseAreASureDuplicateKeepingTheSync() {
        db { session ->
            bank(session, "import", date = 20261013, financialId = null)
            bank(session, "sync", date = 20261012)
            val finding = read(session).findings.single()
            assertEquals(FindingKind.DUPLICATE, finding.kind)
            assertEquals(FindingSure.HIGH, finding.sure)
            assertEquals(listOf("sync", "import"), finding.sides.map { it.side.id })
            assertEquals(FindingAction.Merge(listOf("sync", "import")), finding.actions.first())
        }
    }

    @Test
    fun twoSyncedCopiesUnderOnePayeeAreOnlyMaybe() {
        db { session ->
            bank(session, "a", date = 20261012, financialId = "f-a")
            bank(session, "b", date = 20261014, financialId = "f-b")
            assertEquals(FindingSure.MAYBE, read(session).findings.single().sure)
        }
    }

    @Test
    fun theSameEntryTwiceIsADuplicateButOtherPayeesAndFarDatesAreNot() {
        db { session ->
            entered(session, "one", date = 20261001, account = "checking")
            entered(session, "two", date = 20261001, account = "checking")
            entered(session, "far", date = 20260920, account = "checking")
            entered(session, "other", date = 20261001, account = "checking", payee = "p-gas")
            val finding = read(session).findings.single()
            assertEquals(FindingSure.HIGH, finding.sure)
            assertEquals(setOf("one", "two"), finding.sides.map { it.side.id }.toSet())
        }
    }

    @Test
    fun anEntryWithItsBankCopyInAnotherAccountIsInTheWrongAccount() {
        db { session ->
            // Checking hears from the bank, so an entry there should have met its bank copy.
            bank(session, "paycheck", date = 20261001, account = "checking", amount = 250000, financialId = "f-pay")
            entered(session, "gas", date = 20261012, account = "checking", amount = -5420, category = "c-dining")
            bank(session, "visa-gas", date = 20261013, account = "visa", amount = -5420, financialId = "f-gas")
            val finding = read(session).findings.single()
            assertEquals(FindingKind.WRONG_ACCOUNT, finding.kind)
            assertEquals(FindingSure.HIGH, finding.sure)
            assertEquals("You entered this in Checking, but the bank shows the same charge in Visa.", finding.headline)
            assertEquals(FindingAction.MoveAndMatch("gas", "visa-gas"), finding.actions.first())
        }
    }

    @Test
    fun moveAndMatchLeavesTheBankCopyWithTheEntrysDetails() {
        db { session ->
            entered(session, "gas", date = 20261012, account = "checking", amount = -5420, category = "c-dining", note = "road trip")
            bank(session, "visa-gas", date = 20261013, account = "visa", amount = -5420, financialId = "f-gas")
            assertEquals(WriteResult.Saved, AssistantBook(session, TODAY).moveAndMatch("gas", "visa-gas"))
            val alive = session.query("SELECT id FROM transactions WHERE IFNULL(tombstone, 0) = 0").map { it.str("id") }
            assertEquals(listOf("visa-gas"), alive)
            val row = session.query("SELECT * FROM transactions WHERE id = 'visa-gas'").single()
            assertEquals("visa", row.str("acct"))
            assertEquals("c-dining", row.str("category"))
            assertEquals("p-corner", row.str("description"))
            assertTrue(row.str("notes")!!.startsWith("road trip\nMatched 10/31 from Checking: bank CORNER COFFEE #42"))
            assertTrue(read(session).findings.isEmpty())
        }
    }

    @Test
    fun moveAndMatchIntoAnOffBudgetAccountDropsTheCategory() {
        db { session ->
            session.exec("INSERT INTO accounts (id, name, offbudget, type, sort_order) VALUES ('loan', 'Loan', 1, 'other', 2)")
            entered(session, "pay", date = 20261012, account = "checking", amount = -9000, category = "c-dining")
            bank(session, "loan-pay", date = 20261012, account = "loan", amount = -9000, financialId = "f-loan")
            AssistantBook(session, TODAY).moveAndMatch("pay", "loan-pay")
            assertNull(session.query("SELECT category FROM transactions WHERE id = 'loan-pay'").single().str("category"))
        }
    }

    @Test
    fun anOldEntryTheBankNeverSentIsFlagged() {
        db { session ->
            bank(session, "paycheck", date = 20261001, account = "checking", amount = 250000, financialId = "f-pay")
            entered(session, "refund", date = 20261015, account = "checking", amount = 1999)
            entered(session, "recent", date = 20261028, account = "checking", amount = 777)
            entered(session, "cash", date = 20261001, account = "wallet", amount = -500)
            val finding = read(session).findings.single()
            assertEquals(FindingKind.NO_BANK_MATCH, finding.kind)
            assertEquals(
                "refund",
                finding.sides
                    .single()
                    .side.id,
            )
            assertEquals(listOf("16 days old", AssistantCopy.NOTHING_FROM_BANK), finding.reasons)
            assertEquals(FindingAction.Open("checking", "refund"), finding.actions.first())
        }
    }

    @Test
    fun aDismissedFindingStaysAwayAndAMatchDismissalIsTheInboxsNotAMatch() {
        db { session ->
            bank(session, "paycheck", date = 20261001, account = "checking", amount = 250000, financialId = "f-pay")
            entered(session, "refund", date = 20261015, account = "checking", amount = 1999)
            val key = read(session).findings.single().key
            AssistantBook(session, TODAY).dismiss(key)
            assertTrue(read(session).findings.isEmpty())
            assertEquals(setOf("nobank:refund"), AssistantBook.dismissed(session))
            assertEquals("bank" to "mine", AssistantBook.matchIds("match:bank|mine"))
            assertNull(AssistantBook.matchIds(key))
        }
    }

    @Test
    fun eachTransactionSitsInOneFinding() {
        db { session ->
            entered(session, "mine", date = 20261014)
            bank(session, "bank", date = 20261015)
            bank(session, "bank-copy", date = 20261015, financialId = null)
            val findings = read(session).findings
            val ids = findings.flatMap { f -> f.sides.map { it.side.id } }
            // The entry matches one bank copy; the other waits for that to settle.
            assertEquals(ids.size, ids.toSet().size)
            assertEquals(1, findings.size)
        }
    }

    private fun read(session: SqlSession) = AssistantReader.read(session, TODAY)

    private fun entered(
        session: SqlSession,
        id: String,
        date: Int,
        amount: Long = -575,
        account: String = "visa",
        category: String? = null,
        note: String? = null,
        payee: String = "p-corner",
    ) = insert(session, id, date, amount, account, payee, category, note, null, null)

    private fun bank(
        session: SqlSession,
        id: String,
        date: Int,
        financialId: String? = "f-1",
        account: String = "visa",
        amount: Long = -575,
    ) = insert(session, id, date, amount, account, "p-bank", null, null, financialId, "CORNER COFFEE #42")

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
        val file = File.createTempFile("assistant", ".sqlite")
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
                session.exec("INSERT INTO accounts (id, name, offbudget, type, sort_order) VALUES ('wallet', 'Wallet', 0, 'cash', 2)")
                session.exec("INSERT INTO payees (id, name) VALUES ('p-corner', 'Corner Coffee')")
                session.exec("INSERT INTO payees (id, name) VALUES ('p-bank', 'CORNER COFFEE #42')")
                session.exec("INSERT INTO payees (id, name) VALUES ('p-gas', 'Shell')")
                block(session)
            }
        } finally {
            file.delete()
        }
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 10, 31)
    }
}
