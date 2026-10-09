package app.duenorth.budget.core

import java.util.UUID

object PayeeCopy {
    const val MERGE_CONFIRM = "merge into the existing payee"
    const val IN_USE = "payee still has transactions"
    const val NOT_FOUND = "no payee"
    const val ENTER_NAME = "enter a name"
}

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
        session.exec(
            "UPDATE payees SET name = ? WHERE id = ?",
            listOf(trimmed, payeeId),
        )
        if (rememberRule) {
            rules.insertPayeeRenameRule(payeeId, payeeId)
        }
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
        session.exec("UPDATE payees SET tombstone = 1 WHERE id = ?", listOf(payeeId))
        return PayeeWriteResult.Saved
    }

    private fun merge(
        fromId: String,
        toId: String,
        rememberRule: Boolean,
    ) {
        session.exec(
            "UPDATE transactions SET description = ? WHERE description = ?",
            listOf(toId, fromId),
        )
        session.exec("UPDATE payees SET tombstone = 1 WHERE id = ?", listOf(fromId))
        if (rememberRule) {
            rules.insertPayeeRenameRule(fromId, toId)
        }
    }
}
