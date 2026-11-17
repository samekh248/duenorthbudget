package app.duenorth.budget.core

import java.util.UUID

object PayeeCopy {
    const val MERGE_CONFIRM = "merge into the existing payee"
    const val IN_USE = "payee still has transactions"
    const val NOT_FOUND = "no payee"
    const val ENTER_NAME = "enter a name"
    const val PICK_TWO = "pick two or more payees"
}

/** The payees page as read: every payee, and the names that look like one payee. */
data class PayeesPage(
    val rows: List<PayeeRow>,
    val lookalikes: List<PayeeLookalike>,
)

sealed interface PayeeWriteResult {
    data object Saved : PayeeWriteResult

    data class Rejected(
        val reason: String,
    ) : PayeeWriteResult

    data class ConfirmMerge(
        val reason: String,
        val targetId: String,
    ) : PayeeWriteResult
}

data class PayeeRow(
    val id: String,
    val name: String,
    val transfer: Boolean,
    val transactionCount: Int,
)

class PayeeBook(
    private val session: SqlSession,
    private val ids: () -> String = { UUID.randomUUID().toString() },
) {
    private val rules = RulesBook(session, ids)

    fun list(): List<PayeeRow> =
        session
            .query(
                """
                SELECT
                    p.id AS id,
                    p.name AS name,
                    p.transfer_acct AS transfer_acct,
                    (
                        SELECT COUNT(*)
                        FROM transactions t
                        WHERE t.description = p.id AND IFNULL(t.tombstone, 0) = 0
                    ) AS txn_count
                FROM payees p
                WHERE IFNULL(p.tombstone, 0) = 0
                ORDER BY LOWER(p.name), p.id
                """.trimIndent(),
            ).map { row ->
                PayeeRow(
                    id = row.str("id").orEmpty(),
                    name = row.str("name").orEmpty(),
                    transfer = row.str("transfer_acct") != null,
                    transactionCount = row.long("txn_count").toInt(),
                )
            }

    fun rename(
        payeeId: String,
        newName: String,
        confirmMerge: Boolean = false,
        rememberRule: Boolean = false,
    ): PayeeWriteResult {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return PayeeWriteResult.Rejected(PayeeCopy.ENTER_NAME)
        val existing =
            session
                .query(
                    """
                    SELECT id, name, transfer_acct
                    FROM payees
                    WHERE id = ? AND IFNULL(tombstone, 0) = 0
                    """.trimIndent(),
                    listOf(payeeId),
                ).firstOrNull() ?: return PayeeWriteResult.Rejected(PayeeCopy.NOT_FOUND)
        if (existing.str("transfer_acct") != null) {
            return PayeeWriteResult.Rejected(PayeeCopy.IN_USE)
        }
        val collision =
            session
                .query(
                    """
                    SELECT id FROM payees
                    WHERE LOWER(name) = LOWER(?) AND id != ? AND IFNULL(tombstone, 0) = 0
                    """.trimIndent(),
                    listOf(trimmed, payeeId),
                ).firstOrNull()
                ?.str("id")
        if (collision != null && !confirmMerge) {
            return PayeeWriteResult.ConfirmMerge(PayeeCopy.MERGE_CONFIRM, collision)
        }
        if (collision != null) {
            merge(payeeId, collision, rememberRule)
            return PayeeWriteResult.Saved
        }
        SyncSchema.write(session, "payees", payeeId, "name", trimmed)
        if (rememberRule) {
            rules.insertPayeeRenameRule(payeeId, payeeId)
        }
        return PayeeWriteResult.Saved
    }

    /** The payees page: every payee, and look-alikes the person has not called different. */
    fun page(dismissed: Set<String>): PayeesPage {
        val rows = list()
        return PayeesPage(rows, PayeeLookalikes.find(rows, dismissed))
    }

    /**
     * Makes [payeeIds] one payee called [name]. A payee already called that, in the list or not, takes the rest;
     * otherwise the first id is renamed and takes them. [rememberRule] adds a rule for each merged name, so the bank's
     * spelling lands on the kept payee next time.
     */
    fun mergeAll(
        payeeIds: List<String>,
        name: String,
        rememberRule: Boolean,
    ): PayeeWriteResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return PayeeWriteResult.Rejected(PayeeCopy.ENTER_NAME)
        val ids = payeeIds.distinct()
        if (ids.size < 2) return PayeeWriteResult.Rejected(PayeeCopy.PICK_TWO)
        val live =
            ids.filter { id ->
                session
                    .query(
                        "SELECT id FROM payees WHERE id = ? AND transfer_acct IS NULL AND IFNULL(tombstone, 0) = 0",
                        listOf(id),
                    ).isNotEmpty()
            }
        if (live.size != ids.size) return PayeeWriteResult.Rejected(PayeeCopy.NOT_FOUND)
        val named =
            session
                .query(
                    """
                    SELECT id FROM payees
                    WHERE LOWER(name) = LOWER(?) AND transfer_acct IS NULL AND IFNULL(tombstone, 0) = 0
                    ORDER BY id
                    """.trimIndent(),
                    listOf(trimmed),
                ).mapNotNull { it.str("id") }
        val target = named.firstOrNull { it in ids } ?: named.firstOrNull() ?: ids.first()
        session.transaction {
            SyncSchema.write(session, "payees", target, "name", trimmed)
            ids.filter { it != target }.forEach { merge(it, target, rememberRule) }
        }
        return PayeeWriteResult.Saved
    }

    /** Deletes [payeeIds], every one unused; none goes if any still has transactions. */
    fun deleteAll(payeeIds: List<String>): PayeeWriteResult {
        val used =
            payeeIds.any { id ->
                session
                    .query(
                        "SELECT COUNT(*) AS n FROM transactions WHERE description = ? AND IFNULL(tombstone, 0) = 0",
                        listOf(id),
                    ).first()
                    .long("n") > 0
            }
        if (used) return PayeeWriteResult.Rejected(PayeeCopy.IN_USE)
        session.transaction { payeeIds.forEach { SyncSchema.write(session, "payees", it, "tombstone", 1) } }
        return PayeeWriteResult.Saved
    }

    fun delete(payeeId: String): PayeeWriteResult {
        val count =
            session
                .query(
                    """
                    SELECT COUNT(*) AS n
                    FROM transactions
                    WHERE description = ? AND IFNULL(tombstone, 0) = 0
                    """.trimIndent(),
                    listOf(payeeId),
                ).first()
                .long("n")
        if (count > 0) return PayeeWriteResult.Rejected(PayeeCopy.IN_USE)
        SyncSchema.write(session, "payees", payeeId, "tombstone", 1)
        return PayeeWriteResult.Saved
    }

    private fun merge(
        fromId: String,
        toId: String,
        rememberRule: Boolean,
    ) {
        session
            .query("SELECT id FROM transactions WHERE description = ?", listOf(fromId))
            .mapNotNull { it.str("id") }
            .forEach { id -> SyncSchema.write(session, "transactions", id, "description", toId) }
        SyncSchema.write(session, "payees", fromId, "tombstone", 1)
        if (rememberRule) {
            rules.insertPayeeRenameRule(fromId, toId)
        }
    }
}
