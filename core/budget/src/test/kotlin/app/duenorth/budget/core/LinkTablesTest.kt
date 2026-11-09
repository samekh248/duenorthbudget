package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.YearMonth

/** Actual reads a transaction's category and payee through `category_mapping` and `payee_mapping`. */
class LinkTablesTest {
    @Test
    fun aNewCategoryAndPayeeLinkToThemselvesAndTheLinksAreSent() {
        withFile { session ->
            val clock = SyncClock("PHONE00000000001")
            val book = EnvelopeBook(session, clock)
            val group = book.addGroup("Bills")
            val category = book.addCategory(group, "Power")
            val payee = SyncSchema.withClock(session, clock) { RegisterBook(session).payeeIdForName("Power Co") }
            assertEquals(category, link(session, "category_mapping", "transferId", category))
            assertEquals(payee, link(session, "payee_mapping", "targetId", payee))
            val sent = pending(session)
            assertEquals("S:$category", sent[Triple("category_mapping", category, "transferId")])
            assertEquals("S:$payee", sent[Triple("payee_mapping", payee, "targetId")])
        }
    }

    @Test
    fun renamingAnExistingCategorySendsNoNewLink() {
        withFile { session ->
            val clock = SyncClock("PHONE00000000001")
            val book = EnvelopeBook(session, clock)
            val category = book.addCategory(book.addGroup("Bills"), "Power")
            val before = pending(session).keys.count { it.first == "category_mapping" }
            book.renameCategory(category, "Electric")
            assertEquals(before, pending(session).keys.count { it.first == "category_mapping" })
        }
    }

    @Test
    fun aDesktopMergeOrDeleteIntoAnotherReadsTheSameHere() {
        withFile { session ->
            seed(session)
            SyncSchema.repairLinks(session)
            val remote = SyncClock("REMOTE0000000001")
            // Desktop merges payee "Cafe" into "Coffee" and deletes "Dining" into "Food".
            SyncSchema.applyRemote(session, remote.send(), CellMessage("payee_mapping", "p-cafe", "targetId", "S:p-coffee"))
            SyncSchema.applyRemote(session, remote.send(), CellMessage("category_mapping", "c-dining", "transferId", "S:c-food"))
            SyncSchema.applyRemote(session, remote.send(), CellMessage("categories", "c-dining", "tombstone", "N:1"))
            SyncSchema.repairLinks(session)
            val row = session.query("SELECT category, description FROM transactions WHERE id = 't1'").single()
            assertEquals("c-food", row.str("category"))
            assertEquals("p-coffee", row.str("description"))
            val october = ShellReader.loadDetailed(session, YearMonth.of(2026, 10))
            assertEquals(-1_200L, october.categories["c-food"]?.spentMinor)
            // Repointing stays on this phone: desktop already reads the same through its links.
            assertTrue(pending(session).keys.none { it.first == "transactions" })
        }
    }

    @Test
    fun aSyncedPhoneFileWithoutLinksGetsThemOnce() {
        withFile { session ->
            seed(session)
            SyncSchema.putState(session, "cloudFileId", "cloud-1")
            SyncSchema.repairLinks(session, SyncClock("PHONE00000000001"))
            val sent = pending(session)
            assertEquals("S:c-food", sent[Triple("category_mapping", "c-food", "transferId")])
            assertEquals("S:p-cafe", sent[Triple("payee_mapping", "p-cafe", "targetId")])
            val count = sent.size
            SyncSchema.repairLinks(session, SyncClock("PHONE00000000001"))
            assertEquals(count, pending(session).size)
        }
    }

    @Test
    fun aPhoneOnlyFileGetsLinksWithoutMessages() {
        withFile { session ->
            seed(session)
            SyncSchema.repairLinks(session)
            assertEquals("c-dining", link(session, "category_mapping", "transferId", "c-dining"))
            assertTrue(pending(session).isEmpty())
        }
    }

    private fun seed(session: SqlSession) {
        session.exec("INSERT INTO category_groups (id, name, is_income, sort_order) VALUES ('g', 'Living', 0, 1)")
        session.exec("INSERT INTO categories (id, name, is_income, cat_group) VALUES ('c-food', 'Food', 0, 'g')")
        session.exec("INSERT INTO categories (id, name, is_income, cat_group) VALUES ('c-dining', 'Dining', 0, 'g')")
        session.exec("INSERT INTO payees (id, name) VALUES ('p-cafe', 'Cafe')")
        session.exec("INSERT INTO payees (id, name) VALUES ('p-coffee', 'Coffee')")
        session.exec("INSERT INTO accounts (id, name, offbudget) VALUES ('checking', 'Checking', 0)")
        session.exec(
            "INSERT INTO transactions (id, acct, category, description, amount, date) VALUES ('t1', 'checking', 'c-dining', 'p-cafe', -1200, 20261005)",
        )
    }

    private fun link(
        session: SqlSession,
        table: String,
        column: String,
        id: String,
    ): String? = session.query("SELECT $column AS v FROM $table WHERE id = ?", listOf(id)).firstOrNull()?.str("v")

    private fun pending(session: SqlSession): Map<Triple<String?, String?, String?>, String?> =
        session
            .query("SELECT dataset, row, column, value FROM messages WHERE pending = 1")
            .associate { Triple(it.str("dataset"), it.str("row"), it.str("column")) to it.str("value") }

    private fun withFile(block: (SqlSession) -> Unit) {
        val file = File.createTempFile("links", ".sqlite")
        file.delete()
        try {
            JdbcSessionOpener().use(file) { session ->
                ActualSchema.create(session)
                SyncSchema.ensure(session)
                assertNull(session.query("SELECT 1 AS x FROM category_mapping LIMIT 1").firstOrNull())
                block(session)
            }
        } finally {
            file.delete()
        }
    }
}
