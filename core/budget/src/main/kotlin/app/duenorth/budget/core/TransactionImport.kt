package app.duenorth.budget.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class PreviewRowStatus { New, Duplicate }

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
        val rows =
            candidates.mapIndexed { index, row ->
                val dup = findDuplicate(accountId, row)
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

    fun confirm(
        accountId: String,
        candidates: List<ParsedImportRow>,
    ): ImportConfirmResult {
        val batchId = ids()
        val added = mutableListOf<String>()
        var skipped = 0
        session.transaction {
            candidates.forEach { row ->
                if (findDuplicate(accountId, row) != null) {
                    skipped++
                    return@forEach
                }
                val id = insertImported(accountId, row)
                added += id
            }
            if (added.isNotEmpty()) {
                ImportBatch.write(
                    session,
                    ImportBatchState(accountId, batchId, added),
                )
            }
        }
        return ImportConfirmResult(batchId, added, skipped)
    }

    fun confirmPreview(preview: ImportPreview, candidates: List<ParsedImportRow>): ImportConfirmResult {
        val toAdd =
            preview.rows
                .filter { it.status == PreviewRowStatus.New }
                .map { candidates[it.index] }
        return confirm(preview.accountId, toAdd)
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
        val ruled = book.applyRulesForImport(draft)
        val payeeId = book.payeeIdForName(ruled.payee)
        book.insertImportedTransaction(
            id = ruled.id,
            accountId = ruled.accountId,
            categoryId = ruled.categoryId,
            amount = ruled.amountMinor,
            payeeId = payeeId,
            note = ruled.note,
            date = ruled.date,
            financialId = row.financialId,
            importedPayee = row.importedPayee ?: row.payee,
        )
        return ruled.id
    }

    private fun findDuplicate(
        accountId: String,
        row: ParsedImportRow,
    ): String? {
        if (!row.financialId.isNullOrBlank()) {
            val byId =
                session
                    .query(
                        """
                        SELECT id FROM transactions
                        WHERE acct = ? AND IFNULL(tombstone, 0) = 0
                            AND financial_id = ?
                        LIMIT 1
                        """.trimIndent(),
                        listOf(accountId, row.financialId),
                    ).firstOrNull()
                    ?.str("id")
            if (byId != null) return byId
        }
        val payeeId = payeeIdForName(row.payee) ?: return null
        return session
            .query(
                """
                SELECT id FROM transactions
                WHERE acct = ? AND IFNULL(tombstone, 0) = 0
                    AND date = ? AND amount = ? AND description = ?
                LIMIT 1
                """.trimIndent(),
                listOf(accountId, row.date, row.amountMinor, payeeId),
            ).firstOrNull()
            ?.str("id")
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

    private fun currency(): CurrencySpec {
        val code =
            session
                .query("SELECT value FROM preferences WHERE id = ?", listOf("currency"))
                .firstOrNull()
                ?.str("value")
                .orEmpty()
        return Currencies.byCode(code) ?: Currencies.byCode("USD")!!
    }

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
