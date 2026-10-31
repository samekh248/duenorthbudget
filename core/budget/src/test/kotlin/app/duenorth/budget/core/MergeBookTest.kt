package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MergeBookTest {
    @Test
    fun ruleNeedsTwoOrMoreOfOneAccountAndAmountAndOneTransferAccount() {
        fun side(
            amount: Long = -575,
            account: String = "visa",
            transfer: String? = null,
        ) = TransactionMerge.Side(account, amount, transfer)
        assertEquals(MergeCopy.TWO_OR_MORE, TransactionMerge.whyNot(listOf(side())))
        assertNull(TransactionMerge.whyNot(listOf(side(), side())))
        assertNull(TransactionMerge.whyNot(listOf(side(), side(), side())))
        assertEquals(MergeCopy.AMOUNTS, TransactionMerge.whyNot(listOf(side(), side(amount = -600))))
        assertEquals(MergeCopy.ACCOUNTS, TransactionMerge.whyNot(listOf(side(), side(account = "checking"))))
        assertNull(TransactionMerge.whyNot(listOf(side(transfer = "savings"), side())))
        assertNull(TransactionMerge.whyNot(listOf(side(transfer = "savings"), side(transfer = "savings"))))
        assertEquals(MergeCopy.TRANSFERS, TransactionMerge.whyNot(listOf(side(transfer = "savings"), side(transfer = "checking"))))
    }

    @Test
    fun keepsTheBankSyncedOneAndFillsItsBlanksFromTheOther() {
        db { session ->
            insert(session, "mine", date = 20261014, payee = "p-corner", category = "c-dining", note = "with Sam", cleared = false)
            insert(session, "bank", date = 20261015, payee = "p-bank", financialId = "f-1", imported = "CORNER COFFEE #42")
            assertEquals(WriteResult.Saved, MergeBook(session).merge(listOf("mine", "bank"), force = false))
            assertEquals(listOf("bank"), alive(session))
            val row = session.query("SELECT * FROM transactions WHERE id = 'bank'").single()
            assertEquals("p-bank", row.str("description"))
            assertEquals("c-dining", row.str("category"))
            assertEquals("with Sam", row.str("notes"))
            assertEquals(20261015L, row.long("date"))
            assertEquals("f-1", row.str("financial_id"))
            assertTrue(row.bool("cleared"))
        }
    }

    @Test
    fun keepsTheFileImportedOneOverOneEnteredByHand() {
        db { session ->
            insert(session, "mine", date = 20261014, payee = "p-corner")
            insert(session, "file", date = 20261016, payee = null, imported = "CORNER COFFEE")
            MergeBook(session).merge(listOf("mine", "file"), force = false)
            assertEquals(listOf("file"), alive(session))
            assertEquals("p-corner", session.query("SELECT description FROM transactions WHERE id = 'file'").single().str("description"))
        }
    }

    @Test
    fun withNeitherImportedKeepsTheEarlier() {
        db { session ->
            insert(session, "late", date = 20261016, payee = "p-corner", category = "c-dining")
            insert(session, "early", date = 20261012, payee = null, note = "lunch")
            MergeBook(session).merge(listOf("late", "early"), force = false)
            assertEquals(listOf("early"), alive(session))
            val row = session.query("SELECT * FROM transactions WHERE id = 'early'").single()
            assertEquals("p-corner", row.str("description"))
            assertEquals("c-dining", row.str("category"))
            assertEquals("lunch", row.str("notes"))
        }
    }

    @Test
    fun threeOrMoreFoldIntoOne() {
        db { session ->
            insert(session, "a", date = 20261016, payee = null, note = "from a")
            insert(session, "b", date = 20261015, payee = "p-bank", financialId = "f-1", imported = "CORNER COFFEE #42")
            insert(session, "c", date = 20261014, payee = null, category = "c-dining")
            assertEquals(WriteResult.Saved, MergeBook(session).merge(listOf("a", "b", "c"), force = false))
            assertEquals(listOf("b"), alive(session))
            val row = session.query("SELECT * FROM transactions WHERE id = 'b'").single()
            assertEquals("c-dining", row.str("category"))
            assertEquals("from a", row.str("notes"))
        }
    }

    @Test
    fun theKeptOneTakesTheOthersSplitParts() {
        db { session ->
            insert(session, "bank", date = 20261015, payee = "p-bank", financialId = "f-1")
            insert(session, "split", date = 20261014, payee = "p-corner", parent = true)
            insert(session, "part-1", date = 20261014, payee = "p-corner", amount = -300, category = "c-dining", parentId = "split")
            insert(session, "part-2", date = 20261014, payee = "p-corner", amount = -275, category = "c-dining", parentId = "split")
            MergeBook(session).merge(listOf("bank", "split"), force = false)
            val row = session.query("SELECT * FROM transactions WHERE id = 'bank'").single()
            assertTrue(row.bool("isParent"))
            assertNull(row.str("category"))
            val parts = session.query("SELECT id FROM transactions WHERE parent_id = 'bank' AND tombstone = 0").map { it.str("id") }
            assertEquals(setOf("part-1", "part-2"), parts.toSet())
            assertEquals(1, session.query("SELECT id FROM transactions WHERE id = 'split' AND tombstone = 1").size)
        }
    }

    @Test
    fun aTransferMergedWithTheBanksCopyKeepsItsPartner() {
        db { session ->
            session.exec("INSERT INTO payees (id, name, transfer_acct) VALUES ('p-to-checking', '', 'checking')")
            session.exec("INSERT INTO payees (id, name, transfer_acct) VALUES ('p-to-visa', '', 'visa')")
            insert(session, "out", date = 20261014, payee = "p-to-checking", transferId = "in")
            insert(session, "in", date = 20261014, payee = "p-to-visa", amount = 575, account = "checking", transferId = "out")
            insert(session, "bank", date = 20261015, payee = "p-bank", category = "c-dining", financialId = "f-1")
            MergeBook(session).merge(listOf("out", "bank"), force = false)
            assertEquals(listOf("bank", "in"), alive(session))
            val bank = session.query("SELECT * FROM transactions WHERE id = 'bank'").single()
            assertEquals("in", bank.str("transferred_id"))
            assertEquals("p-to-checking", bank.str("description"))
            // A transfer between budget accounts has no category.
            assertNull(bank.str("category"))
            assertEquals("bank", session.query("SELECT transferred_id FROM transactions WHERE id = 'in'").single().str("transferred_id"))
        }
    }

    @Test
    fun differentAmountsAreRefusedAndNothingChanges() {
        db { session ->
            insert(session, "a", date = 20261014, payee = "p-corner")
            insert(session, "b", date = 20261015, payee = "p-corner", amount = -600)
            assertEquals(WriteResult.Rejected(MergeCopy.AMOUNTS), MergeBook(session).merge(listOf("a", "b"), force = false))
            assertEquals(listOf("a", "b"), alive(session))
        }
    }

    @Test
    fun aReconciledTransactionAsksFirst() {
        db { session ->
            insert(session, "a", date = 20261014, payee = "p-corner", reconciled = true)
            insert(session, "b", date = 20261015, payee = "p-corner")
            val asked = MergeBook(session).merge(listOf("a", "b"), force = false)
            assertEquals(WriteResult.Confirm(RegisterCopy.RECONCILE_WARN, ForcedWrite.Merge(listOf("a", "b"))), asked)
            assertEquals(listOf("a", "b"), alive(session))
            assertEquals(WriteResult.Saved, MergeBook(session).merge(listOf("a", "b"), force = true))
            assertEquals(listOf("a"), alive(session))
            assertTrue(session.query("SELECT reconciled FROM transactions WHERE id = 'a'").single().bool("reconciled"))
        }
    }

    @Test
    fun oneTransactionIsNotAMerge() {
        db { session ->
            insert(session, "a", date = 20261014, payee = "p-corner")
            assertEquals(WriteResult.Rejected(MergeCopy.TWO_OR_MORE), MergeBook(session).merge(listOf("a", "a"), force = false))
            assertFalse(alive(session).isEmpty())
        }
    }

    private fun alive(session: SqlSession): List<String?> =
        session
            .query("SELECT id FROM transactions WHERE IFNULL(tombstone, 0) = 0 AND IFNULL(isChild, 0) = 0 ORDER BY id")
            .map { it.str("id") }

    private fun insert(
        session: SqlSession,
        id: String,
        date: Int,
        payee: String?,
        amount: Long = -575,
        account: String = "visa",
        category: String? = null,
        note: String? = null,
        financialId: String? = null,
        imported: String? = null,
        cleared: Boolean = true,
        reconciled: Boolean = false,
        parent: Boolean = false,
        parentId: String? = null,
        transferId: String? = null,
    ) {
        session.exec(
            """
            INSERT INTO transactions (id, acct, amount, description, notes, date, category, financial_id, imported_description,
                sort_order, cleared, reconciled, isParent, isChild, parent_id, transferred_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            listOf(
                id,
                account,
                amount,
                payee,
                note,
                date,
                category,
                financialId,
                imported,
                date.toDouble(),
                if (cleared) 1 else 0,
                if (reconciled) 1 else 0,
                if (parent) 1 else 0,
                if (parentId != null) 1 else 0,
                parentId,
                transferId,
            ),
        )
    }

    private fun db(block: (SqlSession) -> Unit) {
        val file = File.createTempFile("merge", ".sqlite")
        file.delete()
        try {
            JdbcSessionOpener().use(file) { session ->
                ActualSchema.create(session)
                session.exec("INSERT OR REPLACE INTO preferences (id, value) VALUES ('budgetType', 'envelope')")
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
}
