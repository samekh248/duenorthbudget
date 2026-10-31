package app.duenorth.budget.core

object MergeCopy {
    const val MERGE = "merge"
    const val TWO_OR_MORE = "pick two or more transactions to merge"
    const val ACCOUNTS = "cannot merge transactions from different accounts"
    const val AMOUNTS = "cannot merge transactions with different amounts"
    const val TRANSFERS = "cannot merge transfers to different accounts"
    const val SPLIT_PART = "cannot merge part of a split"
}

/**
 * Whether picked transactions can be merged, by desktop Actual's rule (`validForMerge`): the same account, the same
 * amount, and transfers only with transfers to the same account. Actual merges a pair; this lets two or more through,
 * each pair of them meeting the same rule.
 */
object TransactionMerge {
    data class Side(
        val accountId: String,
        val amountMinor: Long,
        /** The account a transfer moves money to or from; null for an ordinary transaction. */
        val transferAccountId: String? = null,
    )

    /** Why [sides] cannot be merged, or null when they can. */
    fun whyNot(sides: List<Side>): String? =
        when {
            sides.size < 2 -> MergeCopy.TWO_OR_MORE
            sides.map { it.accountId }.distinct().size > 1 -> MergeCopy.ACCOUNTS
            sides.map { it.amountMinor }.distinct().size > 1 -> MergeCopy.AMOUNTS
            sides.mapNotNull { it.transferAccountId }.distinct().size > 1 -> MergeCopy.TRANSFERS
            else -> null
        }

    fun canMerge(sides: List<Side>): Boolean = whyNot(sides) == null

    fun ofRegister(rows: List<RegisterRow>): List<Side> =
        rows.map { Side("", it.amountMinor, if (it.transferId != null) it.transferAccountId else null) }

    fun ofInbox(rows: List<InboxRow>): List<Side> = rows.map { Side(it.accountId, it.amountMinor) }
}

/**
 * Merges transactions into one, as desktop Actual's `mergeTransactions` does, folding two or more a pair at a time.
 * Of each pair it keeps the one a bank sync brought in, else the one a file import brought in, else the earlier;
 * the kept one takes the other's payee, category, note and schedule where its own is empty, is cleared or reconciled
 * when either was, and takes the other's split parts when it has none. The other is deleted. Transfers keep their
 * partner: two transfers to the same account merge their partners too.
 */
class MergeBook(
    private val session: SqlSession,
) {
    fun merge(
        transactionIds: List<String>,
        force: Boolean,
    ): WriteResult {
        val ids = transactionIds.distinct()
        if (ids.size < 2) return WriteResult.Rejected(MergeCopy.TWO_OR_MORE)
        val box = arrayOf<WriteResult>(WriteResult.Saved)
        session.transaction {
            box[0] = run(ids, force)
        }
        return box[0]
    }

    private fun run(
        ids: List<String>,
        force: Boolean,
    ): WriteResult {
        val rows = ids.map { id -> load(id)?.takeIf { !it.tombstone } ?: return WriteResult.Rejected(MatchCopy.GONE) }
        if (rows.any { it.child }) return WriteResult.Rejected(MergeCopy.SPLIT_PART)
        TransactionMerge.whyNot(rows.map { TransactionMerge.Side(it.accountId, it.amount, it.transferAccountId) })?.let {
            return WriteResult.Rejected(it)
        }
        val partners = rows.mapNotNull { it.transferId?.let(::load) }
        if (!force && (rows + partners).any { it.reconciled }) {
            return WriteResult.Confirm(RegisterCopy.RECONCILE_WARN, ForcedWrite.Merge(ids))
        }
        rows.drop(1).fold(rows.first().id) { kept, next -> mergePair(kept, next.id) }
        return WriteResult.Saved
    }

    private fun mergePair(
        aId: String,
        bId: String,
    ): String {
        val a = load(aId)!!
        val b = load(bId)!!
        val aPartner =
            a.transferId
                ?.let(::load)
                ?.takeIf { !it.tombstone }
                ?.id
        val bPartner =
            b.transferId
                ?.let(::load)
                ?.takeIf { !it.tombstone }
                ?.id
        if (aPartner == null && bPartner == null) return mergeNoTransfer(a, b)
        val transferPayee = if (aPartner != null) a.payeeId else b.payeeId
        // Unlink first, so deleting the dropped side leaves its partner alone.
        listOfNotNull(a.id, b.id, aPartner, bPartner).forEach { write(it, "transferred_id" to null) }
        val partner =
            when {
                aPartner == null -> bPartner
                bPartner == null -> aPartner
                else -> mergeNoTransfer(load(aPartner)!!, load(bPartner)!!)
            }
        val kept = mergeNoTransfer(load(a.id)!!, load(b.id)!!)
        if (partner != null) {
            write(kept, "transferred_id" to partner, "description" to transferPayee)
            write(partner, "transferred_id" to kept)
        }
        // A transfer between budget accounts has no category, so one merged with an ordinary transaction loses it.
        if (aPartner == null || bPartner == null) {
            val onBudget =
                session
                    .query(
                        """
                        SELECT a.offbudget AS offbudget FROM payees p JOIN accounts a ON a.id = p.transfer_acct
                        WHERE p.id = ?
                        """.trimIndent(),
                        listOf(transferPayee),
                    ).firstOrNull()
                    ?.let { !it.bool("offbudget") } == true
            if (onBudget) write(kept, "category" to null)
        }
        return kept
    }

    private fun mergeNoTransfer(
        a: Txn,
        b: Txn,
    ): String {
        val (keep, drop) = keepDrop(a, b)
        val keepParts = if (keep.parent) children(keep.id) else emptyList()
        val dropParts = if (drop.parent) children(drop.id) else emptyList()
        val shared =
            arrayOf(
                "description" to (keep.payeeId.orBlankNull() ?: drop.payeeId),
                "notes" to (keep.note.orBlankNull() ?: drop.note),
                "cleared" to if (keep.cleared || drop.cleared) 1 else 0,
                "reconciled" to if (keep.reconciled || drop.reconciled) 1 else 0,
                "schedule" to (keep.schedule.orBlankNull() ?: drop.schedule),
            )
        if (keepParts.isEmpty() && dropParts.isNotEmpty()) {
            dropParts.forEach { write(it, "parent_id" to keep.id) }
            write(keep.id, "isParent" to 1, "category" to null, *shared)
        } else {
            // A split keeps its parts; its own category stays empty.
            val category = if (keepParts.isNotEmpty()) null else keep.categoryId.orBlankNull() ?: drop.categoryId
            write(keep.id, "category" to category, *shared)
        }
        write(drop.id, "tombstone" to 1)
        children(drop.id).forEach { write(it, "tombstone" to 1) }
        return keep.id
    }

    private fun keepDrop(
        a: Txn,
        b: Txn,
    ): Pair<Txn, Txn> =
        when {
            b.importedId != null && a.importedId == null -> b to a
            a.importedId != null && b.importedId == null -> a to b
            b.importedPayee != null && a.importedPayee == null -> b to a
            a.importedPayee != null && b.importedPayee == null -> a to b
            a.date < b.date -> a to b
            else -> b to a
        }

    private fun write(
        id: String,
        vararg values: Pair<String, Any?>,
    ) = SyncSchema.writeRow(session, "transactions", id, *values)

    private fun children(parentId: String): List<String> =
        session
            .query(
                "SELECT id FROM transactions WHERE parent_id = ? AND IFNULL(tombstone, 0) = 0 AND IFNULL(isChild, 0) = 1",
                listOf(parentId),
            ).mapNotNull { it.str("id") }

    private fun load(id: String): Txn? =
        session
            .query(
                """
                SELECT t.id AS id, t.isParent AS isParent, t.isChild AS isChild, t.acct AS acct, t.category AS category,
                    t.amount AS amount, t.description AS description, t.notes AS notes, t.date AS date,
                    t.transferred_id AS transferred_id, t.cleared AS cleared, t.reconciled AS reconciled,
                    t.tombstone AS tombstone, t.schedule AS schedule, t.financial_id AS financial_id,
                    t.imported_description AS imported_description, p.transfer_acct AS transfer_acct
                FROM transactions t
                LEFT JOIN payees p ON p.id = t.description
                WHERE t.id = ?
                """.trimIndent(),
                listOf(id),
            ).firstOrNull()
            ?.let { row ->
                val transferId = row.str("transferred_id").orBlankNull()
                Txn(
                    id = row.str("id").orEmpty(),
                    parent = row.bool("isParent"),
                    child = row.bool("isChild"),
                    accountId = row.str("acct").orEmpty(),
                    categoryId = row.str("category"),
                    amount = row.long("amount"),
                    payeeId = row.str("description"),
                    note = row.str("notes"),
                    date = row.long("date").toInt(),
                    transferId = transferId,
                    transferAccountId = row.str("transfer_acct").takeIf { transferId != null },
                    cleared = row.bool("cleared"),
                    reconciled = row.bool("reconciled"),
                    tombstone = row.bool("tombstone"),
                    schedule = row.str("schedule"),
                    importedId = row.str("financial_id").orBlankNull(),
                    importedPayee = row.str("imported_description").orBlankNull(),
                )
            }

    private fun String?.orBlankNull(): String? = this?.takeIf { it.isNotBlank() }

    private data class Txn(
        val id: String,
        val parent: Boolean,
        val child: Boolean,
        val accountId: String,
        val categoryId: String?,
        val amount: Long,
        val payeeId: String?,
        val note: String?,
        val date: Int,
        val transferId: String?,
        val transferAccountId: String?,
        val cleared: Boolean,
        val reconciled: Boolean,
        val tombstone: Boolean,
        val schedule: String?,
        val importedId: String?,
        val importedPayee: String?,
    )
}
