package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.util.UUID

/**
 * A pending bank row has to stop reading as pending once the bank posts it, whichever device did the bank sync, and
 * whatever the bank sends meanwhile: the pending copy for a few more days, a new id, or a different amount.
 */
class BankPendingSyncTest {
    // The bank keeps sending the pending copy for a while after it posts the charge under a new id.
    @Test
    fun pendingCopyStillSentAfterThePhoneClearedItAddsNothing() {
        withBook { session, register, import ->
            import.confirm("checking", listOf(pending("x-1", 20261103, -1_599)), review = false)
            import.confirm("checking", listOf(posted("y-1", 20261105, -1_599)), review = false)

            val again =
                import.confirm(
                    "checking",
                    listOf(pending("x-1", 20261103, -1_599), posted("y-1", 20261105, -1_599)),
                    review = false,
                )

            assertEquals(0, again.addedIds.size)
            val row = register.read("checking")!!.rows.single()
            assertEquals(false, row.pending)
            assertEquals(true, row.cleared)
            assertEquals(1, alive(session))
        }
    }

    // Actual's web app cleared the row (taking the posted id, keeping the pending copy): the phone's sync adds nothing.
    @Test
    fun pendingCopyStillSentAfterActualClearedItAddsNothing() {
        withBook { session, register, import ->
            insert(session, "web", 20261103, "y-1", cleared = 1, raw = raw("x-1", booked = false, amount = "-15.99"))

            val again =
                import.confirm(
                    "checking",
                    listOf(pending("x-1", 20261103, -1_599), posted("y-1", 20261105, -1_599)),
                    review = false,
                )

            assertEquals(0, again.addedIds.size)
            assertEquals(
                false,
                register
                    .read("checking")!!
                    .rows
                    .single()
                    .pending,
            )
            assertEquals(1, alive(session))
        }
    }

    // A row an older build cleared kept only the posted copy: its pending copy still never comes back.
    @Test
    fun pendingCopyOfARowClearedByAnOlderBuildAddsNothing() {
        withBook { session, register, import ->
            insert(session, "old", 20261103, "y-1", cleared = 1, raw = raw("y-1", booked = true, amount = "-15.99"))

            val again =
                import.confirm(
                    "checking",
                    listOf(pending("x-1", 20261103, -1_599), posted("y-1", 20261105, -1_599)),
                    review = false,
                )

            assertEquals(0, again.addedIds.size)
            assertEquals(
                false,
                register
                    .read("checking")!!
                    .rows
                    .single()
                    .pending,
            )
            assertEquals(1, alive(session))
        }
    }

    // The first sync after the bank posts it can carry both copies: the posted one clears the pending row.
    @Test
    fun postedCopyArrivingBesideItsPendingCopyClearsThePendingRow() {
        withBook { session, register, import ->
            import.confirm("checking", listOf(pending("x-1", 20261103, -1_599)), review = false)

            val both = listOf(pending("x-1", 20261103, -1_599), posted("y-1", 20261105, -1_599))
            assertEquals(0, import.confirm("checking", both, review = false).addedIds.size)
            assertEquals(0, import.confirm("checking", both, review = false).addedIds.size)

            val row = register.read("checking")!!.rows.single()
            assertEquals(false, row.pending)
            assertEquals(true, row.cleared)
            assertEquals(1, alive(session))
        }
    }

    // Two alike coffees a day apart: the first posts while the second is still pending; each keeps its own row.
    @Test
    fun alikePendingChargesPostOneAtATime() {
        withBook { session, register, import ->
            import.confirm("checking", listOf(pending("x-1", 20261103, -450), pending("x-2", 20261104, -450)), review = false)

            val next = listOf(posted("y-1", 20261105, -450), pending("x-2", 20261104, -450))
            assertEquals(0, import.confirm("checking", next, review = false, bankSince = SINCE).addedIds.size)

            val rows = register.read("checking")!!.rows.sortedBy { it.date }
            assertEquals(listOf(false, true), rows.map { it.pending })
            assertEquals(2, alive(session))
        }
    }

    // A tip or a fuel hold: the bank posts a different amount under a new id and stops sending the pending copy.
    @Test
    fun postedAtADifferentAmountClearsThePendingRowAndTakesTheAmount() {
        withBook { session, register, import ->
            import.confirm("checking", listOf(pending("x-1", 20261103, -2_000)), review = false)
            val id =
                register
                    .read("checking")!!
                    .rows
                    .single()
                    .id
            register.setCategory(id, "c-food", false)

            val result =
                import.confirm("checking", listOf(posted("y-1", 20261105, -2_400)), review = false, bankSince = SINCE)

            assertEquals(0, result.addedIds.size)
            val row = register.read("checking")!!.rows.single()
            assertEquals(id, row.id)
            assertEquals(-2_400L, row.amountMinor)
            assertEquals(false, row.pending)
            assertEquals(true, row.cleared)
            assertEquals("c-food", row.categoryId)
            assertEquals(
                0,
                import.confirm("checking", listOf(posted("y-1", 20261105, -2_400)), review = false, bankSince = SINCE).addedIds.size,
            )
            assertEquals(1, alive(session))
        }
    }

    // A hold the bank dropped without posting anything (a hotel or fuel authorization) goes with it.
    @Test
    fun pendingRowTheBankDroppedGoesAway() {
        withBook { session, register, import ->
            import.confirm("checking", listOf(pending("x-1", 20261103, -10_000), posted("b-1", 20261101, -500)), review = false)

            import.confirm("checking", listOf(posted("b-1", 20261101, -500)), review = false, bankSince = SINCE)

            val rows = register.read("checking")!!.rows
            assertEquals(listOf(-500L), rows.map { it.amountMinor })
            assertEquals(false, rows.single().pending)
        }
    }

    // A pending row older than what the bank was asked for, a file import, or an empty answer leaves it as it is.
    @Test
    fun pendingRowStaysWhenTheBankWasNotAskedAboutIt() {
        withBook { session, _, import ->
            import.confirm("checking", listOf(pending("x-1", 20261103, -10_000)), review = false)

            import.confirm("checking", listOf(posted("b-1", 20261105, -500)), review = false, bankSince = 20261104)
            import.confirm("checking", listOf(posted("b-2", 20261106, -700)), review = false)
            import.confirm("checking", emptyList(), review = false, bankSince = SINCE)

            assertEquals(3, alive(session))
            assertEquals(1, pendingCount(session))
        }
    }

    // The unattended bank sync passes what it asked the bank for, so a dropped hold goes there too.
    @Test
    fun unattendedBankSyncClearsAndDropsPendingRows() {
        withBook { session, register, import ->
            session.exec("UPDATE accounts SET account_sync_source = 'simpleFin', account_id = 'sf-1' WHERE id = 'checking'")
            import.confirm(
                "checking",
                listOf(pending("x-1", 20261103, -2_000), pending("x-2", 20261104, -9_000, payee = "Hotel")),
                review = false,
            )
            val book = BankSyncBook(session, FakeBankSyncTransport(listOf(posted("y-1", 20261105, -2_400))))

            val summary = book.syncAll("https://example.test", "token", LocalDate.of(2026, 11, 10), 0L, { UUID.randomUUID().toString() })

            assertEquals(0, summary.added)
            val row = register.read("checking")!!.rows.single()
            assertEquals(-2_400L, row.amountMinor)
            assertEquals(false, row.pending)
        }
    }

    private fun pending(
        id: String,
        date: Int,
        amount: Long,
        payee: String = "Cafe",
    ) = ParsedImportRow(date, payee, amount, financialId = id, pending = true, raw = raw(id, booked = false, amount = "$amount"))

    private fun posted(
        id: String,
        date: Int,
        amount: Long,
        payee: String = "Cafe",
    ) = ParsedImportRow(date, payee, amount, financialId = id, raw = raw(id, booked = true, amount = "$amount"))

    private fun raw(
        id: String,
        booked: Boolean,
        amount: String,
    ) = """{"booked":$booked,"transactionId":"$id","amount":"$amount","payeeName":"Cafe"}"""

    private fun withBook(block: (SqlSession, RegisterBook, ImportBook) -> Unit) {
        val file = File.createTempFile("pending", ".sqlite")
        file.delete()
        JdbcSessionOpener().use(file) { session ->
            ActualSchema.create(session)
            session.exec("INSERT INTO preferences (id, value) VALUES ('currency', 'USD')")
            session.exec("INSERT INTO accounts (id, name, offbudget, type, sort_order) VALUES ('checking', 'Checking', 0, 'checking', 1)")
            session.exec("INSERT INTO category_groups (id, name, sort_order) VALUES ('g', 'Group', 1)")
            session.exec("INSERT INTO categories (id, name, cat_group, sort_order) VALUES ('c-food', 'Groceries', 'g', 1)")
            session.exec("INSERT INTO payees (id, name) VALUES ('p-cafe', 'Cafe')")
            ActualSchema.ensure(session)
            val ids = { UUID.randomUUID().toString() }
            block(session, RegisterBook(session, ids), ImportBook(session, ids))
        }
    }

    private fun insert(
        session: SqlSession,
        id: String,
        date: Int,
        financialId: String,
        cleared: Int,
        raw: String,
    ) = session.exec(
        """
        INSERT INTO transactions (
            id, acct, amount, description, imported_description, date, financial_id, tombstone, cleared, reconciled,
            isParent, isChild, sort_order, raw_synced_data
        ) VALUES ('$id', 'checking', -1599, 'p-cafe', 'Cafe', $date, '$financialId', 0, $cleared, 0, 0, 0, 1, ?)
        """.trimIndent(),
        listOf(raw),
    )

    private fun alive(session: SqlSession): Int =
        session
            .query("SELECT COUNT(*) AS n FROM transactions WHERE IFNULL(tombstone, 0) = 0")
            .first()
            .long("n")
            .toInt()

    private fun pendingCount(session: SqlSession): Int =
        session
            .query("SELECT COUNT(*) AS n FROM transactions t WHERE IFNULL(t.tombstone, 0) = 0 AND ${BankPending.pendingSql("t")}")
            .first()
            .long("n")
            .toInt()

    private companion object {
        /** What a sync on 2026-11-10 asks the bank for: the last 90 days. */
        const val SINCE = 20260813
    }
}
