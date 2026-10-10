package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.util.UUID

class ImportBookTest {
    @Test
    fun qifPreviewSeparatesDuplicates() {
        withBook { session, register, import ->
            register.save(draft("existing", date = 20261001, payee = "Cafe", amount = -500))
            val qif =
                """
                !Type:Bank
                D10/01/2026
                T-5.00
                PCafe
                ^
                D10/02/2026
                T-12.40
                PGrocery
                ^
                D10/03/2026
                T20.00
                PPaycheck
                ^
                D10/04/2026
                T-5.00
                PCafe
                ^
                """.trimIndent()
            val (bundle, error) = import.previewFile("checking", qif.toByteArray(), "bank.qif")
            assertEquals(null, error)
            val preview = bundle!!.preview
            assertEquals(3, preview.adding)
            assertEquals(1, preview.skipping)
            val toAdd =
                preview.rows
                    .filter { it.status == PreviewRowStatus.New }
                    .map { bundle.candidates[it.index] }
            val result = import.confirm(preview.accountId, toAdd)
            assertEquals(3, result.addedIds.size)
            assertEquals(0, result.skipped)
            assertEquals(4, alive(session))
            val page = register.read("checking")!!
            assertEquals(-500 - 1_240 + 2_000 - 500, page.balanceMinor)
        }
    }

    @Test
    fun financialIdMatchesDespitePayeeText() {
        withBook { session, _, import ->
            session.exec(
                """
                INSERT INTO transactions (
                    id, acct, amount, description, date, financial_id, tombstone, cleared, reconciled, isParent, isChild, sort_order
                ) VALUES ('t1', 'checking', -500, 'p-cafe', 20261001, 'fit-1', 0, 1, 0, 0, 0, 1)
                """.trimIndent(),
            )
            val row =
                ParsedImportRow(
                    date = 20261005,
                    payee = "Cafe Renamed",
                    amountMinor = -600,
                    financialId = "fit-1",
                )
            val preview = import.previewCandidates("checking", Currencies.byCode("USD")!!, listOf(row))
            assertEquals(PreviewRowStatus.Duplicate, preview.rows.single().status)
        }
    }

    @Test
    fun reviewBatchUpdatesCategory() {
        withBook { _, _, import ->
            val candidates =
                listOf(
                    ParsedImportRow(20261002, "A", -100),
                    ParsedImportRow(20261003, "B", -200),
                )
            import.confirm("checking", candidates)
            val review = import.readReview()!!
            assertEquals(2, review.rows.size)
            assertEquals(2, review.uncategorizedCount)
            import.setReviewCategory(review.rows.first().id, "c-food")
            val again = import.readReview()!!
            assertEquals(1, again.uncategorizedCount)
            assertEquals("Groceries", again.rows.first { it.id == review.rows.first().id }.categoryLabel)
        }
    }

    @Test
    fun bankFetchUsesFakeTransport() {
        withBook { session, _, _ ->
            session.exec(
                """
                UPDATE accounts
                SET account_sync_source = 'simpleFin', account_id = 'bank-1'
                WHERE id = 'checking'
                """.trimIndent(),
            )
            session.exec(
                """
                INSERT INTO transactions (
                    id, acct, amount, description, date, financial_id, tombstone, cleared, reconciled, isParent, isChild, sort_order
                ) VALUES ('old', 'checking', -500, 'p-cafe', 20261001, 'dup', 0, 1, 0, 0, 0, 1)
                """.trimIndent(),
            )
            val transport =
                FakeBankSyncTransport(
                    listOf(
                        ParsedImportRow(20261008, "New", -300, financialId = "n1"),
                        ParsedImportRow(20261001, "Cafe", -500, financialId = "dup"),
                    ),
                )
            val (bundle, error) =
                BankSyncBook(session, transport).previewFetch(
                    "checking",
                    "https://example.test",
                    "token",
                    LocalDate.of(2026, 10, 10),
                )
            assertEquals(null, error)
            assertEquals(1, bundle!!.preview.adding)
            assertEquals(1, bundle.preview.skipping)
        }
    }

    private fun withBook(block: (SqlSession, RegisterBook, ImportBook) -> Unit) {
        val file = File.createTempFile("import", ".sqlite")
        file.delete()
        JdbcSessionOpener().use(file) { session ->
            ActualSchema.create(session)
            seed(session)
            ActualSchema.ensure(session)
            val ids = { UUID.randomUUID().toString() }
            block(session, RegisterBook(session, ids), ImportBook(session, ids))
        }
    }

    private fun seed(session: SqlSession) {
        session.exec("INSERT INTO preferences (id, value) VALUES ('currency', 'USD')")
        session.exec("INSERT INTO accounts (id, name, offbudget, type, sort_order) VALUES ('checking', 'Checking', 0, 'checking', 1)")
        session.exec("INSERT INTO category_groups (id, name, sort_order) VALUES ('g', 'Group', 1)")
        session.exec("INSERT INTO categories (id, name, cat_group, sort_order) VALUES ('c-food', 'Groceries', 'g', 1)")
        session.exec("INSERT INTO payees (id, name) VALUES ('p-cafe', 'Cafe')")
    }

    private fun draft(
        id: String,
        date: Int,
        payee: String,
        amount: Long,
    ) = TransactionDraft(id, "checking", date, payee, amount, "c-food", null)

    private fun alive(session: SqlSession): Int =
        session
            .query("SELECT COUNT(*) AS n FROM transactions WHERE IFNULL(tombstone, 0) = 0")
            .first()
            .long("n")
            .toInt()
}
