package app.duenorth.budget.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class PreviewRowStatus { New, Duplicate }

/** A copy that came back sits this close to the one it was merged into, as the assistant's duplicates do. */
private const val RETURNED_DAYS = AssistantReader.DUPLICATE_DAYS

data class ImportPreviewRow(
    val index: Int,
    val date: Int,
    val payee: String,
    val amountMinor: Long,
    val financialId: String?,
    val status: PreviewRowStatus,
    val existingTransactionId: String? = null,
)

data class ImportPreview(
    val accountId: String,
    val currency: CurrencySpec,
    val rows: List<ImportPreviewRow>,
    val adding: Int,
    val skipping: Int,
)

data class ImportPreviewBundle(
    val preview: ImportPreview,
    val candidates: List<ParsedImportRow>,
)

data class ImportConfirmResult(
    val batchId: String,
    val addedIds: List<String>,
    val skipped: Int,
)

data class ImportReviewRow(
    val id: String,
    val date: Int,
    val payee: String,
    val amountMinor: Long,
    val categoryId: String?,
    val categoryLabel: String,
)

data class ImportReviewPage(
    val accountId: String,
    val batchId: String,
    val currency: CurrencySpec,
    val rows: List<ImportReviewRow>,
    val categories: List<CategoryChoice>,
    val uncategorizedCount: Int,
)

@Serializable
data class ImportBatchState(
    val accountId: String,
    val batchId: String,
    val transactionIds: List<String>,
)

object ImportBatch {
    const val PREFERENCE_ID = "dueNorthImportBatch"

    private val json = Json { ignoreUnknownKeys = true }

    fun read(session: SqlSession): ImportBatchState? {
        val raw =
            session
                .query("SELECT value FROM preferences WHERE id = ?", listOf(PREFERENCE_ID))
                .firstOrNull()
                ?.str("value")
                ?: return null
        return runCatching { json.decodeFromString<ImportBatchState>(raw) }.getOrNull()
    }

    fun write(
        session: SqlSession,
        state: ImportBatchState,
    ) {
        session.exec(
            "INSERT OR REPLACE INTO preferences (id, value) VALUES (?, ?)",
            listOf(PREFERENCE_ID, json.encodeToString(state)),
        )
    }

    fun clear(session: SqlSession) {
        session.exec("DELETE FROM preferences WHERE id = ?", listOf(PREFERENCE_ID))
    }
}

class ImportBook(
    private val session: SqlSession,
    private val ids: () -> String,
) {
    fun previewFile(
        accountId: String,
        bytes: ByteArray,
        fileName: String,
    ): Pair<ImportPreviewBundle?, String?> {
        if (account(accountId) == null) return null to ShellCopy.NO_ACCOUNTS
        val currency = currency()
        when (val parsed = ImportFileParser.parse(bytes, fileName, currency.decimals)) {
            ParseFileResult.Unreadable -> return null to ImportCopy.FILE_NOT_UNDERSTOOD
            is ParseFileResult.Ok -> {
                val preview = previewCandidates(accountId, currency, parsed.rows)
                return ImportPreviewBundle(preview, parsed.rows) to null
            }
        }
    }

    fun previewCandidates(
        accountId: String,
        currency: CurrencySpec,
        candidates: List<ParsedImportRow>,
    ): ImportPreview {
        val held = matchHeld(accountId, candidates, bankSince = null).held
        val rows =
            candidates.mapIndexed { index, row ->
                val dup = held[index]
                ImportPreviewRow(
                    index = index,
                    date = row.date,
                    payee = row.payee,
                    amountMinor = row.amountMinor,
                    financialId = row.financialId,
                    status = if (dup == null) PreviewRowStatus.New else PreviewRowStatus.Duplicate,
                    existingTransactionId = dup,
                )
            }
        val adding = rows.count { it.status == PreviewRowStatus.New }
        return ImportPreview(accountId, currency, rows, adding, rows.size - adding)
    }

    /**
     * Adds the rows that are not duplicates; [review] keeps them as the batch the review page shows. A bank sync passes
     * [bankSince], the first day it asked the bank for: a pending row from then on that nothing the bank sent stands
     * for any more was dropped by the bank (a hold released, or posted at another amount and cleared here), so it goes.
     */
    fun confirm(
        accountId: String,
        candidates: List<ParsedImportRow>,
        review: Boolean = true,
        bankSince: Int? = null,
    ): ImportConfirmResult {
        val batchId = ids()
        val added = mutableListOf<String>()
        var skipped = 0
        session.transaction {
            // Matched before anything is added, so a row added now is never taken for the next one's copy.
            val matches = matchHeld(accountId, candidates, bankSince)
            candidates.forEachIndexed { index, row ->
                val held = matches.held[index]
                if (held != null) {
                    // A row the bank has posted since clears the pending copy, as Actual's sync does.
                    if (!row.pending) settlePending(held, row, takeAmount = index in matches.newAmount)
                    skipped++
                    return@forEachIndexed
                }
                val id = insertImported(accountId, row)
                added += id
            }
            if (bankSince != null &&
                candidates.isNotEmpty()
            ) {
                dropVanishedPending(accountId, bankSince, matches.held.filterNotNull().toSet())
            }
            if (review && added.isNotEmpty()) {
                ImportBatch.write(
                    session,
                    ImportBatchState(accountId, batchId, added),
                )
            }
        }
        foldReturned(accountId)
        return ImportConfirmResult(batchId, added, skipped)
    }

    /**
     * Deletes the bank copies that came back after a merge, from before deleted rows counted as already held: a live
     * row sharing its bank id with a deleted one, beside another live row of the same amount a few days off, which is
     * the one it was merged into. That row is not touched, so its cleared state stays; a reconciled copy, or one made
     * a transfer, is left alone. Returns how many it deleted.
     */
    fun foldReturned(accountId: String): Int {
        val pairs =
            session
                .query(
                    """
                    SELECT r.id AS r_id, k.id AS k_id, r.date AS r_date, k.date AS k_date
                    FROM transactions r
                    JOIN transactions gone
                        ON gone.acct = r.acct AND gone.financial_id = r.financial_id AND gone.id <> r.id
                            AND IFNULL(gone.tombstone, 0) = 1
                    JOIN transactions k
                        ON k.acct = r.acct AND k.amount = r.amount AND k.id <> r.id
                            AND IFNULL(k.financial_id, '') <> r.financial_id
                    WHERE r.acct = ? AND IFNULL(r.financial_id, '') <> '' AND IFNULL(r.transferred_id, '') = ''
                        AND ${MatchFinder.candidate("r")} AND ${MatchFinder.candidate("k")}
                        AND IFNULL(r.reconciled, 0) = 0 AND IFNULL(k.reconciled, 0) = 0
                    ORDER BY r.id, k.id
                    """.trimIndent(),
                    listOf(accountId),
                ).filter { MatchFinder.daysBetween(it.long("r_date").toInt(), it.long("k_date").toInt()) <= RETURNED_DAYS }
                .map { it.str("r_id").orEmpty() to it.str("k_id").orEmpty() }
                .distinct()
        // Only a copy with one partner, and a partner with one copy: with two to choose from, the person picks.
        val perReturned = pairs.groupingBy { it.first }.eachCount()
        val perKept = pairs.groupingBy { it.second }.eachCount()
        val returned = pairs.filter { (copy, kept) -> perReturned[copy] == 1 && perKept[kept] == 1 }.map { it.first }
        // Only the copy goes: the row it was merged into keeps its cleared state and everything else.
        returned.forEach { SyncSchema.writeRow(session, "transactions", it, "tombstone" to 1) }
        return returned.size
    }

    fun readReview(): ImportReviewPage? {
        val batch = ImportBatch.read(session) ?: return null
        val account = account(batch.accountId) ?: return null
        if (account.tombstone) return null
        val currency = currency()
        val categories = categories()
        val rows =
            batch.transactionIds.mapNotNull { id ->
                val txn = findTransaction(id) ?: return@mapNotNull null
                if (txn.tombstone || txn.accountId != batch.accountId) return@mapNotNull null
                val payeeName = payeeName(txn.payeeId)
                val categoryName = txn.categoryId?.let { catName(it) }
                ImportReviewRow(
                    id = id,
                    date = txn.date,
                    payee = payeeName,
                    amountMinor = txn.amount,
                    categoryId = txn.categoryId,
                    categoryLabel = categoryName ?: RegisterCopy.NO_CATEGORY,
                )
            }
        val uncategorized =
            rows.count { row ->
                row.categoryId == null && !isTransfer(row.id)
            }
        return ImportReviewPage(
            accountId = batch.accountId,
            batchId = batch.batchId,
            currency = currency,
            rows = rows,
            categories = categories,
            uncategorizedCount = uncategorized,
        )
    }

    fun finishReview() {
        ImportBatch.clear(session)
    }

    fun setReviewCategory(
        transactionId: String,
        categoryId: String?,
    ): WriteResult = RegisterBook(session, ids).setCategory(transactionId, categoryId, false)

    private fun insertImported(
        accountId: String,
        row: ParsedImportRow,
    ): String {
        val draft =
            TransactionDraft(
                id = ids(),
                accountId = accountId,
                date = row.date,
                payee = row.payee,
                amountMinor = row.amountMinor,
                categoryId = null,
                note = row.note,
            )
        val book = RegisterBook(session, ids)
        val outcome = book.applyRulesForImport(draft, importedPayee = row.importedPayee ?: row.payee, cleared = !row.pending)
        val ruled = outcome.draft
        // A bank row with no payee at all still comes in, with none, as Actual adds it.
        val payeeId = ruled.payee.takeIf { it.isNotBlank() }?.let { book.payeeIdForName(it) }
        book.insertImportedTransaction(
            id = ruled.id,
            accountId = ruled.accountId,
            categoryId = ruled.categoryId,
            amount = ruled.amountMinor,
            payeeId = payeeId,
            note = ruled.note,
            date = ruled.date,
            financialId = row.financialId,
            importedPayee = outcome.importedPayee,
            cleared = ruled.cleared ?: !row.pending,
            rawSyncedData = row.raw,
            scheduleId = ruled.scheduleId,
        )
        book.finishRuled(ruled.id, outcome)
        return ruled.id
    }

    /**
     * The one pending row a newly posted bank row stands for: banks give the posted row a new id, so it is found by
     * its amount within a few days, as Actual matches it. With two to choose from, neither is taken. A pending row that
     * a pending copy still sent by the bank holds is taken too when the payee is the same: the first sync after the
     * bank posts a charge often carries both copies.
     */
    private fun postedPending(
        accountId: String,
        row: ParsedImportRow,
        taken: Set<String>,
        heldByPending: Set<String>,
    ): String? {
        if (row.pending) return null
        val near =
            pendingRows(accountId).filter {
                it.amount == row.amountMinor && MatchFinder.daysBetween(it.date, row.date) <= BankPending.POSTED_WITHIN_DAYS
            }
        val free = near.filter { it.id !in taken }
        if (free.isNotEmpty()) return free.singleOrNull()?.id
        return near.filter { it.id in heldByPending && samePayee(it, row) }.singleOrNull()?.id
    }

    /**
     * A tip or a fuel hold: the bank posts another amount under a new id and stops sending the pending copy. The one
     * pending row of the same payee within a few days that nothing in this fetch still holds is that charge.
     */
    private fun postedAtAnotherAmount(
        accountId: String,
        row: ParsedImportRow,
        since: Int,
        taken: Set<String>,
    ): String? {
        if (row.pending || row.amountMinor == 0L) return null
        return pendingRows(accountId)
            .filter {
                it.id !in taken &&
                    it.date >= since &&
                    it.amount != row.amountMinor &&
                    (it.amount < 0) == (row.amountMinor < 0) &&
                    MatchFinder.daysBetween(it.date, row.date) <= BankPending.POSTED_WITHIN_DAYS &&
                    samePayee(it, row)
            }.singleOrNull()
            ?.id
    }

    /**
     * The row a pending bank row has already posted as: it took the posted id when it was cleared, and kept the
     * pending copy (as Actual's sync keeps it) whose bank id is this one.
     */
    private fun postedAs(
        accountId: String,
        row: ParsedImportRow,
    ): String? {
        val financialId = row.financialId?.takeIf { it.isNotBlank() } ?: return null
        if (!row.pending) return null
        return session
            .query(
                """
                SELECT id, raw_synced_data FROM transactions
                WHERE acct = ? AND IFNULL(tombstone, 0) = 0 AND IFNULL(isChild, 0) = 0
                    AND IFNULL(financial_id, '') <> ? AND instr(raw_synced_data, ?) > 0
                """.trimIndent(),
                listOf(accountId, financialId, financialId),
            ).firstOrNull { BankPending.bankId(it.str("raw_synced_data")) == financialId }
            ?.str("id")
    }

    /**
     * A pending copy of a row this fetch's posted copy already holds, cleared by a build that kept only the posted
     * copy: the same day, amount and payee as that cleared row.
     */
    private fun postedSameDay(
        accountId: String,
        row: ParsedImportRow,
        heldByPosted: Set<String>,
    ): String? {
        if (!row.pending || heldByPosted.isEmpty()) return null
        val payeeId = payeeIdForName(row.payee) ?: return null
        return session
            .query(
                """
                SELECT id FROM transactions
                WHERE acct = ? AND IFNULL(tombstone, 0) = 0 AND IFNULL(cleared, 0) = 1
                    AND date = ? AND amount = ? AND description = ?
                ORDER BY id
                """.trimIndent(),
                listOf(accountId, row.date, row.amountMinor, payeeId),
            ).mapNotNull { it.str("id") }
            .firstOrNull { it in heldByPosted }
    }

    /** Live bank rows not posted yet, the kind a bank sync may clear or drop: no splits, transfers or locked rows. */
    private fun pendingRows(accountId: String): List<PendingRow> =
        session
            .query(
                """
                SELECT t.id, t.date, t.amount, t.description, t.imported_description, t.raw_synced_data
                FROM transactions t
                WHERE t.acct = ? AND ${MatchFinder.candidate("t")}
                    AND IFNULL(t.cleared, 0) = 0 AND t.raw_synced_data IS NOT NULL
                """.trimIndent(),
                listOf(accountId),
            ).filter { BankPending.unbooked(it.str("raw_synced_data")) }
            .map {
                PendingRow(
                    id = it.str("id").orEmpty(),
                    date = it.long("date").toInt(),
                    amount = it.long("amount"),
                    payeeId = it.str("description"),
                    importedPayee = it.str("imported_description"),
                )
            }

    private data class PendingRow(
        val id: String,
        val date: Int,
        val amount: Long,
        val payeeId: String?,
        val importedPayee: String?,
    )

    private fun samePayee(
        held: PendingRow,
        row: ParsedImportRow,
    ): Boolean {
        val theirs = listOfNotNull(row.payee, row.importedPayee).map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val ours =
            listOfNotNull(held.importedPayee, held.payeeId?.let { payeeName(it) })
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
        return theirs.any { it in ours }
    }

    /** Pending rows from [since] on that nothing the bank sent this time stands for: the bank dropped them. */
    private fun dropVanishedPending(
        accountId: String,
        since: Int,
        held: Set<String>,
    ) {
        pendingRows(accountId)
            .filter { it.date >= since && it.id !in held }
            .forEach { SyncSchema.writeRow(session, "transactions", it.id, "tombstone" to 1) }
    }

    /**
     * Clears a pending row the bank has now posted, and takes the posted row's id so the next sync knows it. The
     * pending copy stays in `raw_synced_data`, as Actual's sync keeps it, so the pending id still finds the row while
     * the bank goes on sending it. [takeAmount]: the bank posted another amount (a tip, a fuel hold).
     */
    private fun settlePending(
        id: String,
        row: ParsedImportRow,
        takeAmount: Boolean,
    ) {
        val held =
            session
                .query(
                    "SELECT cleared, reconciled, tombstone, raw_synced_data FROM transactions WHERE id = ?",
                    listOf(id),
                ).firstOrNull() ?: return
        if (held.bool("tombstone") || !BankPending.isPending(held.str("raw_synced_data"), held.bool("cleared"), held.bool("reconciled"))) {
            return
        }
        val values = mutableListOf<Pair<String, Any?>>("cleared" to 1)
        if (!row.financialId.isNullOrBlank()) values += "financial_id" to row.financialId
        if (takeAmount) values += "amount" to row.amountMinor
        SyncSchema.writeRow(session, "transactions", id, *values.toTypedArray())
    }

    private class HeldMatches(
        val held: List<String?>,
        /** Candidates that clear a pending row posted at another amount. */
        val newAmount: Set<Int>,
    )

    /**
     * The held row each candidate stands for, null for a new one, matched as Actual's sync does: every candidate's
     * bank id first, then the looser matches (same day, amount and payee; a pending row it posts), each held row taken
     * once at most. So two alike purchases on one day are two rows, and a row the feed still sends under its own bank
     * id is never taken for another's copy. A pending copy the bank still sends after the row posted finds that row.
     */
    private fun matchHeld(
        accountId: String,
        candidates: List<ParsedImportRow>,
        bankSince: Int?,
    ): HeldMatches {
        val byId = candidates.map { byFinancialId(accountId, it) ?: postedAs(accountId, it) }
        val taken = byId.filterNotNull().toMutableSet()
        val heldByPending =
            candidates.indices
                .filter { candidates[it].pending }
                .mapNotNull { byId[it] }
                .toSet()
        val heldByPosted =
            candidates.indices
                .filter { !candidates[it].pending }
                .mapNotNull { byId[it] }
                .toSet()
        val newAmount = mutableSetOf<Int>()
        val held =
            candidates
                .mapIndexed { index, row ->
                    byId[index]
                        ?: postedSameDay(accountId, row, heldByPosted)
                        ?: (sameDayCopy(accountId, row, taken) ?: postedPending(accountId, row, taken, heldByPending))?.also { taken += it }
                }.toMutableList()
        if (bankSince != null) {
            candidates.forEachIndexed { index, row ->
                if (held[index] != null) return@forEachIndexed
                postedAtAnotherAmount(accountId, row, bankSince, taken + held.filterNotNull())?.let {
                    held[index] = it
                    taken += it
                    newAmount += index
                }
            }
        }
        return HeldMatches(held, newAmount)
    }

    private fun byFinancialId(
        accountId: String,
        row: ParsedImportRow,
    ): String? {
        val financialId = row.financialId?.takeIf { it.isNotBlank() } ?: return null
        val found =
            session
                .query(
                    """
                    SELECT id, IFNULL(tombstone, 0) AS gone FROM transactions
                    WHERE acct = ? AND financial_id = ?
                    ORDER BY IFNULL(tombstone, 0)
                    LIMIT 1
                    """.trimIndent(),
                    listOf(accountId, financialId),
                ).firstOrNull() ?: return null
        val id = found.str("id")
        if (!found.bool("gone")) return id
        // A deleted bank row comes back, as Actual's sync brings it back unless the account says not to. A copy merged
        // away stays gone: every sync fetches the last 90 days again, and the row it was merged into stands in for it.
        if (!reimportsDeleted(accountId)) return id
        return id.takeIf { standsIn(accountId, row, financialId) }
    }

    /** Actual's per-account "reimport deleted transactions" switch, on unless the account turned it off. */
    private fun reimportsDeleted(accountId: String): Boolean =
        session
            .query("SELECT value FROM preferences WHERE id = ?", listOf("sync-reimport-deleted-$accountId"))
            .firstOrNull()
            ?.str("value")
            ?.trim() != "false"

    /**
     * A live row of the same amount a few days off, holding no copy of this bank id: what a deleted copy was merged
     * into, by the same measure [foldReturned] uses, so a row brought back is never folded away again.
     */
    private fun standsIn(
        accountId: String,
        row: ParsedImportRow,
        financialId: String,
    ): Boolean =
        session
            .query(
                """
                SELECT date FROM transactions
                WHERE acct = ? AND amount = ? AND IFNULL(tombstone, 0) = 0 AND IFNULL(financial_id, '') <> ?
                """.trimIndent(),
                listOf(accountId, row.amountMinor, financialId),
            ).any { MatchFinder.daysBetween(it.long("date").toInt(), row.date) <= RETURNED_DAYS }

    private fun sameDayCopy(
        accountId: String,
        row: ParsedImportRow,
        taken: Set<String>,
    ): String? {
        val payeeId = payeeIdForName(row.payee) ?: return null
        return session
            .query(
                """
                SELECT id FROM transactions
                WHERE acct = ? AND IFNULL(tombstone, 0) = 0
                    AND date = ? AND amount = ? AND description = ?
                ORDER BY id
                """.trimIndent(),
                listOf(accountId, row.date, row.amountMinor, payeeId),
            ).mapNotNull { it.str("id") }
            .firstOrNull { it !in taken }
    }

    private fun payeeIdForName(name: String): String? =
        session
            .query(
                """
                SELECT id FROM payees
                WHERE IFNULL(tombstone, 0) = 0 AND transfer_acct IS NULL AND lower(name) = lower(?)
                LIMIT 1
                """.trimIndent(),
                listOf(name.trim()),
            ).firstOrNull()
            ?.str("id")

    private fun payeeName(payeeId: String?): String {
        if (payeeId.isNullOrBlank()) return ShellCopy.NO_PAYEE
        return session
            .query("SELECT name FROM payees WHERE id = ?", listOf(payeeId))
            .firstOrNull()
            ?.str("name")
            .orEmpty()
            .ifBlank { ShellCopy.NO_PAYEE }
    }

    private fun catName(categoryId: String): String? =
        session
            .query(
                "SELECT name FROM categories WHERE id = ? AND IFNULL(tombstone, 0) = 0",
                listOf(categoryId),
            ).firstOrNull()
            ?.str("name")

    private fun isTransfer(transactionId: String): Boolean {
        val transfer =
            session
                .query(
                    "SELECT transferred_id FROM transactions WHERE id = ?",
                    listOf(transactionId),
                ).firstOrNull()
                ?.str("transferred_id")
        return transfer != null
    }

    private fun findTransaction(id: String): RawTransaction? {
        val row =
            session
                .query(
                    """
                    SELECT id, acct, category, amount, description, notes, date, tombstone, transferred_id
                    FROM transactions WHERE id = ?
                    """.trimIndent(),
                    listOf(id),
                ).firstOrNull() ?: return null
        return RawTransaction(
            id = row.str("id").orEmpty(),
            accountId = row.str("acct").orEmpty(),
            categoryId = row.str("category"),
            amount = row.long("amount"),
            payeeId = row.str("description"),
            note = row.str("notes"),
            date = row.long("date").toInt(),
            tombstone = row.bool("tombstone"),
            transferId = row.str("transferred_id"),
            child = false,
            parent = false,
        )
    }

    private data class RawTransaction(
        val id: String,
        val accountId: String,
        val categoryId: String?,
        val amount: Long,
        val payeeId: String?,
        val note: String?,
        val date: Int,
        val tombstone: Boolean,
        val transferId: String?,
        val child: Boolean,
        val parent: Boolean,
    )

    private fun account(id: String): AccountRow? {
        val row =
            session
                .query(
                    """
                    SELECT id, name, offbudget, closed, type, tombstone
                    FROM accounts WHERE id = ?
                    """.trimIndent(),
                    listOf(id),
                ).firstOrNull() ?: return null
        return AccountRow(
            id = row.str("id").orEmpty(),
            name = row.str("name").orEmpty(),
            offBudget = row.bool("offbudget"),
            type = row.str("type").orEmpty().ifBlank { "checking" },
            closed = row.bool("closed"),
            tombstone = row.bool("tombstone"),
        )
    }

    private data class AccountRow(
        val id: String,
        val name: String,
        val offBudget: Boolean,
        val type: String,
        val closed: Boolean,
        val tombstone: Boolean,
    )

    private fun currency(): CurrencySpec = BudgetCurrency.read(session)

    private fun categories(): List<CategoryChoice> =
        session
            .query(
                """
                SELECT c.id, c.name, g.name AS group_name, c.is_income
                FROM categories c
                LEFT JOIN category_groups g ON g.id = c.cat_group
                WHERE IFNULL(c.tombstone, 0) = 0 AND IFNULL(c.hidden, 0) = 0
                ORDER BY g.sort_order, c.sort_order, c.name
                """.trimIndent(),
            ).map { row ->
                CategoryChoice(
                    id = row.str("id").orEmpty(),
                    name = row.str("name").orEmpty(),
                    groupName = row.str("group_name").orEmpty(),
                    income = row.bool("is_income"),
                )
            }
}
