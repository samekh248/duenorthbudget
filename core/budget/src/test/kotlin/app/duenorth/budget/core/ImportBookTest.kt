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

    // A row the bank has not posted comes in not cleared and reads as pending, as Actual imports it.
    @Test
    fun pendingBankRowComesInPendingAndNotCleared() {
        withBook { session, register, import ->
            import.confirm(
                "checking",
                listOf(
                    ParsedImportRow(20261030, "Streamer", -1_599, financialId = "p-1", pending = true, raw = UNBOOKED),
                    ParsedImportRow(20261029, "Market", -8_420, financialId = "b-1", raw = BOOKED),
                ),
                review = false,
            )
            val rows = register.read("checking")!!.rows.associateBy { it.payee }
            assertEquals(true, rows.getValue("Streamer").pending)
            assertEquals(false, rows.getValue("Streamer").cleared)
            assertEquals(false, rows.getValue("Market").pending)
            assertEquals(true, rows.getValue("Market").cleared)
            assertEquals(2, alive(session))
        }
    }

    // Banks give the posted row a new id: it clears the pending row instead of landing beside it.
    @Test
    fun postedRowClearsItsPendingCopy() {
        withBook { session, register, import ->
            import.confirm(
                "checking",
                listOf(ParsedImportRow(20261030, "Streamer", -1_599, financialId = "p-1", pending = true, raw = UNBOOKED)),
                review = false,
            )
            val posted = ParsedImportRow(20261101, "STREAMER.COM", -1_599, financialId = "b-9", raw = BOOKED)
            val result = import.confirm("checking", listOf(posted), review = false)
            assertEquals(0, result.addedIds.size)
            val row = register.read("checking")!!.rows.single()
            assertEquals(false, row.pending)
            assertEquals(true, row.cleared)
            // The next sync knows it by the posted id.
            assertEquals(0, import.confirm("checking", listOf(posted), review = false).addedIds.size)
            assertEquals(1, alive(session))
        }
    }

    // Two alike purchases on one day (two fares, two coffees) are two bank rows with their own ids: both come in.
    @Test
    fun alikeRowsWithTheirOwnBankIdsAllComeIn() {
        withBook { session, _, import ->
            val first = ParsedImportRow(20261105, "Metro", -290, financialId = "t-1", raw = BOOKED)
            val second = ParsedImportRow(20261105, "Metro", -290, financialId = "t-2", raw = BOOKED)
            assertEquals(2, import.confirm("checking", listOf(first, second), review = false).addedIds.size)
            assertEquals(0, import.confirm("checking", listOf(first, second), review = false).addedIds.size)
            assertEquals(2, alive(session))
        }
    }

    // The second of them, landing at the bank after a sync took the first, comes in on the next one.
    @Test
    fun alikeRowInALaterSyncComesIn() {
        withBook { session, _, import ->
            val first = ParsedImportRow(20261105, "Metro", -290, financialId = "t-1", raw = BOOKED)
            import.confirm("checking", listOf(first), review = false)
            val second = ParsedImportRow(20261105, "Metro", -290, financialId = "t-2", raw = BOOKED)
            assertEquals(1, import.confirm("checking", listOf(first, second), review = false).addedIds.size)
            assertEquals(2, alive(session))
        }
    }

    // A posted charge is not taken for another pending charge of the same amount that the bank still sends as pending.
    @Test
    fun postedRowNeverClearsAnotherStillPendingRow() {
        withBook { session, register, import ->
            val rows =
                listOf(
                    ParsedImportRow(20261108, "Streamer", -1_599, financialId = "p-1", pending = true, raw = UNBOOKED),
                    ParsedImportRow(20261103, "Music", -1_599, financialId = "b-2", raw = BOOKED),
                )
            assertEquals(2, import.confirm("checking", rows, review = false).addedIds.size)
            assertEquals(0, import.confirm("checking", rows, review = false).addedIds.size)
            val held = register.read("checking")!!.rows.associateBy { it.payee }
            assertEquals(true, held.getValue("Streamer").pending)
            assertEquals(true, held.getValue("Music").cleared)
            assertEquals(2, alive(session))
        }
    }

    // Every sync fetches the last 90 days again: a bank row the person deleted keeps its bank id and stays gone.
    @Test
    fun deletedBankRowStaysGone() {
        withBook { session, _, import ->
            insert(session, "deleted", 20261010, "fit-9", tombstone = 1, amount = -900)
            val row = ParsedImportRow(20261010, "Cafe", -900, financialId = "fit-9", raw = BOOKED)
            val result = import.confirm("checking", listOf(row), review = false, bankSince = 20260801)
            assertEquals(0, result.addedIds.size)
            assertEquals(1, result.skipped)
            assertEquals(0, import.confirm("checking", listOf(row), review = false, bankSince = 20260801).addedIds.size)
            assertEquals(0, alive(session))
        }
    }

    // An account that turned Actual's "reimport deleted" switch on gets a deleted row back, once.
    @Test
    fun deletedBankRowComesBackWhenTheAccountSaysSo() {
        withBook { session, _, import ->
            session.exec("INSERT INTO preferences (id, value) VALUES ('sync-reimport-deleted-checking', 'true')")
            insert(session, "deleted", 20261010, "fit-9", tombstone = 1, amount = -900)
            val row = ParsedImportRow(20261010, "Cafe", -900, financialId = "fit-9", raw = BOOKED)
            assertEquals(1, import.confirm("checking", listOf(row), review = false).addedIds.size)
            assertEquals(0, import.confirm("checking", listOf(row), review = false).addedIds.size)
            assertEquals(1, alive(session))
        }
    }

    // The switch turned off, as Actual writes it, keeps it gone too.
    @Test
    fun deletedBankRowStaysGoneWhenTheAccountSaysSo() {
        withBook { session, _, import ->
            session.exec("INSERT INTO preferences (id, value) VALUES ('sync-reimport-deleted-checking', 'false')")
            insert(session, "deleted", 20261010, "fit-9", tombstone = 1, amount = -900)
            val row = ParsedImportRow(20261010, "Cafe", -900, financialId = "fit-9", raw = BOOKED)
            assertEquals(0, import.confirm("checking", listOf(row), review = false).addedIds.size)
            assertEquals(0, alive(session))
        }
    }

    // A pending row a bank sync adds is one the bank sent: the same sync never drops it as vanished.
    @Test
    fun pendingRowAddedBySyncStays() {
        withBook { session, register, import ->
            val pending = ParsedImportRow(20261101, "Streamer", -1_599, financialId = "p-1", pending = true, raw = UNBOOKED)
            assertEquals(1, import.confirm("checking", listOf(pending), review = false, bankSince = 20260801).addedIds.size)
            assertEquals(1, alive(session))
            assertEquals(
                true,
                register
                    .read("checking")!!
                    .rows
                    .single()
                    .pending,
            )
            assertEquals(0, import.confirm("checking", listOf(pending), review = false, bankSince = 20260801).addedIds.size)
            assertEquals(1, alive(session))
        }
    }

    // A pending row deleted before it posted: the bank posts it under a new id, and that copy stays gone as well,
    // on this sync and every one after, even once the bank stops sending the pending copy.
    @Test
    fun deletedPendingRowStaysGoneOnceItPosts() {
        withBook { session, _, import ->
            val pending = ParsedImportRow(20261101, "Streamer", -1_599, financialId = "p-1", pending = true, raw = UNBOOKED)
            val added = import.confirm("checking", listOf(pending), review = false, bankSince = 20260801).addedIds.single()
            RegisterBook(session).delete(added, force = false)
            assertEquals(0, import.confirm("checking", listOf(pending), review = false, bankSince = 20260801).addedIds.size)

            val posted = ParsedImportRow(20261103, "Streamer", -1_599, financialId = "b-2", raw = BOOKED)
            assertEquals(0, import.confirm("checking", listOf(pending, posted), review = false, bankSince = 20260801).addedIds.size)
            assertEquals(0, import.confirm("checking", listOf(posted), review = false, bankSince = 20260801).addedIds.size)
            assertEquals(0, alive(session))
            // The deleted row now holds the posted id, so the next sync knows it by that id alone.
            assertEquals(
                "b-2",
                session.query("SELECT financial_id FROM transactions WHERE id = ?", listOf(added)).single().str("financial_id"),
            )
        }
    }

    // A pending row the bank dropped (a hold released) is not one the person deleted: a charge posted later comes in.
    @Test
    fun pendingRowTheBankDroppedLetsALaterChargeIn() {
        withBook { session, _, import ->
            val pending = ParsedImportRow(20261101, "Hotel", -10_000, financialId = "p-1", pending = true, raw = UNBOOKED)
            assertEquals(1, import.confirm("checking", listOf(pending), review = false, bankSince = 20260801).addedIds.size)
            val other = ParsedImportRow(20261020, "Cafe", -500, financialId = "b-0", raw = BOOKED)
            assertEquals(1, import.confirm("checking", listOf(other), review = false, bankSince = 20260801).addedIds.size)
            assertEquals(1, alive(session))

            val posted = ParsedImportRow(20261104, "Hotel", -10_000, financialId = "b-2", raw = BOOKED)
            assertEquals(1, import.confirm("checking", listOf(other, posted), review = false, bankSince = 20260801).addedIds.size)
            assertEquals(2, alive(session))
        }
    }

    // A posted row deleted after it cleared its pending copy: the pending copy the bank still sends stays gone too.
    @Test
    fun deletedPostedRowKeepsItsPendingCopyAway() {
        withBook { session, _, import ->
            val pending = ParsedImportRow(20261101, "Streamer", -1_599, financialId = "p-1", pending = true, raw = UNBOOKED)
            val posted = ParsedImportRow(20261103, "Streamer", -1_599, financialId = "b-2", raw = BOOKED)
            val id = import.confirm("checking", listOf(pending), review = false, bankSince = 20260801).addedIds.single()
            assertEquals(0, import.confirm("checking", listOf(pending, posted), review = false, bankSince = 20260801).addedIds.size)
            RegisterBook(session).delete(id, force = false)

            assertEquals(0, import.confirm("checking", listOf(pending, posted), review = false, bankSince = 20260801).addedIds.size)
            assertEquals(0, alive(session))
        }
    }

    // A bank row merged into a hand-entered one stays one row, though the bank sends it on every sync.
    @Test
    fun bankRowMergedIntoAnEntryStaysMerged() {
        withBook { session, register, import ->
            register.save(draft("entered", date = 20261005, payee = "Cafe", amount = -700))
            val bank = ParsedImportRow(20261007, "CAFE 123", -700, financialId = "fit-7", raw = BOOKED)
            val added = import.confirm("checking", listOf(bank), review = false, bankSince = 20260801).addedIds.single()
            assertEquals(WriteResult.Saved, MergeBook(session).merge(listOf("entered", added), force = false))
            assertEquals(1, alive(session))

            assertEquals(0, import.confirm("checking", listOf(bank), review = false, bankSince = 20260801).addedIds.size)
            assertEquals(1, alive(session))
        }
    }

    @Test
    fun lockedOrHandClearedRowsAreNeverPending() {
        assertEquals(true, BankPending.isPending(UNBOOKED, cleared = false, reconciled = false))
        assertEquals(false, BankPending.isPending(UNBOOKED, cleared = true, reconciled = false))
        assertEquals(false, BankPending.isPending(UNBOOKED, cleared = false, reconciled = true))
        assertEquals(false, BankPending.isPending(BOOKED, cleared = false, reconciled = false))
        assertEquals(false, BankPending.isPending(null, cleared = false, reconciled = false))
        assertEquals(false, BankPending.isPending("not json booked", cleared = false, reconciled = false))
    }

    // Every sync fetches the last 90 days again: a bank copy merged away must not come back as a new duplicate.
    @Test
    fun mergedBankCopyStaysMergedOnNextSync() {
        withBook { session, _, import ->
            val rows =
                listOf(
                    ParsedImportRow(20261001, "Cafe", -500, financialId = "pending-1"),
                    ParsedImportRow(20261002, "Cafe", -500, financialId = "posted-1"),
                )
            val first = import.confirm("checking", rows, review = false)
            assertEquals(2, first.addedIds.size)
            assertEquals(WriteResult.Saved, MergeBook(session).merge(first.addedIds, force = false))
            assertEquals(1, alive(session))

            val again = import.confirm("checking", rows, review = false)
            assertEquals(0, again.addedIds.size)
            assertEquals(2, again.skipped)
            assertEquals(1, alive(session))
        }
    }

    // A copy that came back before deleted rows counted is merged back into the one it was merged into.
    @Test
    fun returnedCopyFoldsBackOnNextImport() {
        withBook { session, _, import ->
            insert(session, "kept", 20261001, "fit-1")
            insert(session, "merged-away", 20261002, "fit-2", tombstone = 1)
            insert(session, "came-back", 20261002, "fit-2")
            session.exec("UPDATE transactions SET cleared = 0 WHERE id = 'kept'")
            val result = import.confirm("checking", emptyList(), review = false)
            assertEquals(0, result.addedIds.size)
            assertEquals(1, alive(session))
            val kept = session.query("SELECT tombstone, cleared FROM transactions WHERE id = 'kept'").single()
            assertEquals(false, kept.bool("tombstone"))
            // The row it was merged into keeps its own cleared state, though the copy was cleared.
            assertEquals(false, kept.bool("cleared"))
        }
    }

    // With two rows it could belong to, the copy stays for the person to merge; with none, a deleted row stays deleted.
    @Test
    fun returnedCopyStaysWhenUnclear() {
        withBook { session, _, import ->
            insert(session, "kept-a", 20261001, "fit-1")
            insert(session, "kept-b", 20261003, "fit-3")
            insert(session, "merged-away", 20261002, "fit-2", tombstone = 1)
            insert(session, "came-back", 20261002, "fit-2")
            insert(session, "deleted", 20261010, "fit-9", tombstone = 1, amount = -900)
            insert(session, "deleted-back", 20261010, "fit-9", amount = -900)
            assertEquals(0, import.foldReturned("checking"))
            assertEquals(4, alive(session))
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

    // The accounts page's panel names each account as it starts and each one that fails, and the rest still run.
    @Test
    fun syncAllReportsEachAccountAndItsFailure() {
        withBook { session, _, _ ->
            session.exec("INSERT INTO accounts (id, name, offbudget, type, sort_order) VALUES ('card', 'Card', 0, 'credit', 2)")
            session.exec("UPDATE accounts SET account_sync_source = 'simpleFin', account_id = 'bank-' || id")
            val transport =
                object : BankSyncTransport {
                    override fun fetchTransactions(
                        serverAddress: String,
                        token: String,
                        bankAccountId: String,
                        startDateIso: String,
                    ): List<ParsedImportRow> {
                        if (bankAccountId == "bank-checking") throw BankSyncException(ImportCopy.FETCH_FAILED)
                        return listOf(ParsedImportRow(20261008, "New", -300, financialId = "n1"))
                    }
                }
            val started = mutableListOf<String>()
            val failed = mutableListOf<BankSyncFailure>()
            val summary =
                BankSyncBook(session, transport).syncAll(
                    "https://example.test",
                    "token",
                    LocalDate.of(2026, 10, 10),
                    0L,
                    { UUID.randomUUID().toString() },
                    onAccount = { started += it.name },
                    onFailure = { failed += it },
                )
            assertEquals(listOf("Checking", "Card"), started)
            assertEquals(listOf(BankSyncFailure("Checking", ImportCopy.FETCH_FAILED, "checking")), failed)
            assertEquals(failed, summary.failures)
            assertEquals(1, summary.failed)
            assertEquals(1, summary.added)
        }
    }

    // Every bank-linked account syncs, whatever its provider; one the budget cannot take stops only that one.
    @Test
    fun syncAllTakesEveryProviderAndOneBadAccountStopsOnlyItself() {
        withBook { session, _, _ ->
            session.exec("INSERT INTO accounts (id, name, offbudget, type, sort_order) VALUES ('euro', 'Euro', 0, 'checking', 2)")
            session.exec("INSERT INTO accounts (id, name, offbudget, type, sort_order) VALUES ('card', 'Card', 0, 'credit', 3)")
            session.exec("INSERT INTO banks (id, bank_id, name) VALUES ('b-gc', 'req-1', 'Euro Bank')")
            session.exec("UPDATE accounts SET account_sync_source = 'simpleFin', account_id = 'sf-1' WHERE id = 'checking'")
            session.exec("UPDATE accounts SET account_sync_source = 'goCardless', account_id = 'gc-1', bank = 'b-gc' WHERE id = 'euro'")
            session.exec("UPDATE accounts SET account_sync_source = 'pluggyai', account_id = 'pl-1' WHERE id = 'card'")
            val asked = mutableListOf<List<BankLinkedAccount>>()
            val transport =
                object : BankSyncTransport {
                    override fun fetchTransactions(
                        serverAddress: String,
                        token: String,
                        bankAccountId: String,
                        startDateIso: String,
                    ): List<ParsedImportRow> = emptyList()

                    override fun fetchAll(
                        serverAddress: String,
                        token: String,
                        accounts: List<BankLinkedAccount>,
                        startDateIso: String,
                    ): Map<String, BankFetch> {
                        asked += accounts
                        return accounts.associate {
                            val id =
                                if (it.syncSource ==
                                    "goCardless"
                                ) {
                                    HttpBankSyncTransport.INTERNAL_ID_PREFIX + "int-1"
                                } else {
                                    "${it.accountId}-1"
                                }
                            it.accountId to BankFetch(listOf(ParsedImportRow(20261008, "New", -300, financialId = id)), null)
                        }
                    }
                }
            val book = BankSyncBook(session, transport)
            val ids = { UUID.randomUUID().toString() }
            val failed = mutableListOf<BankSyncFailure>()
            val summary =
                BankSyncBook.syncAll(
                    transport = transport,
                    serverAddress = "https://example.test",
                    token = "token",
                    today = LocalDate.of(2026, 10, 10),
                    linked = book::linkedAccounts,
                    store = { account, fetch ->
                        if (account.accountId == "checking") error("disk full")
                        book.store(account, fetch, 0L, ids)
                    },
                    onFailure = { failed += it },
                )
            assertEquals(listOf(listOf("checking"), listOf("euro"), listOf("card")), asked.map { list -> list.map { it.accountId } })
            assertEquals("req-1", asked[1].single().requisitionId)
            assertEquals(listOf(BankSyncFailure("Checking", BankSyncBook.STORE_FAILED, "checking")), failed)
            assertEquals(2, summary.added)
            val idsHeld = session.query("SELECT financial_id FROM transactions ORDER BY financial_id").map { it.str("financial_id") }
            assertEquals(listOf("card-1", "euro-int-1"), idsHeld)
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

    private fun insert(
        session: SqlSession,
        id: String,
        date: Int,
        financialId: String,
        tombstone: Int = 0,
        amount: Long = -500,
    ) = session.exec(
        """
        INSERT INTO transactions (
            id, acct, amount, description, date, financial_id, tombstone, cleared, reconciled, isParent, isChild, sort_order
        ) VALUES ('$id', 'checking', $amount, 'p-cafe', $date, '$financialId', $tombstone, 1, 0, 0, 0, 1)
        """.trimIndent(),
    )

    private fun alive(session: SqlSession): Int =
        session
            .query("SELECT COUNT(*) AS n FROM transactions WHERE IFNULL(tombstone, 0) = 0")
            .first()
            .long("n")
            .toInt()
}

private const val UNBOOKED = """{"booked":false,"transactionId":"p-1","amount":"-15.99"}"""
private const val BOOKED = """{"booked":true,"transactionId":"b-1"}"""
