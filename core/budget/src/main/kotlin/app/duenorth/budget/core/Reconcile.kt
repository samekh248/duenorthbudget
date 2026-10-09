package app.duenorth.budget.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object ReconcileCopy {
    const val ENTER_BALANCE = "enter the statement balance"
    const val ENTER_DATE = "enter the statement date"
    const val TOO_MANY_DECIMALS = "too many decimal places"
    const val NOT_BALANCED = "cleared total must match the statement"
    const val NO_SESSION = "no reconciliation in progress"
    const val DIFFERENCE = "difference"
    const val STATEMENT = "statement"
    const val CLEARED = "cleared"
    const val RECONCILE = "reconcile"
    const val FINISH = "finish"
    const val CANCEL = "cancel"
}

@Serializable
data class ReconcileBaselineEntry(
    val cleared: Boolean,
    val reconciled: Boolean,
)

@Serializable
data class ReconcileSession(
    val statementMinor: Long,
    val statementDate: Int,
    val baseline: Map<String, ReconcileBaselineEntry>,
)

data class ReconcileRow(
    val id: String,
    val date: Int,
    val payee: String,
    val amountMinor: Long,
    val cleared: Boolean,
    val reconciled: Boolean,
)

data class ReconcilePage(
    val account: RegisterAccount,
    val currency: CurrencySpec,
    val statementMinor: Long,
    val statementDate: Int,
    val clearedTotalMinor: Long,
    val differenceMinor: Long,
    val rows: List<ReconcileRow>,
    val active: Boolean,
)

sealed interface ReconcileStartResult {
    data class Started(
        val page: ReconcilePage,
    ) : ReconcileStartResult

    data class Rejected(
        val reason: String,
    ) : ReconcileStartResult
}

sealed interface ReconcileFinishResult {
    data object Finished : ReconcileFinishResult

    data class Rejected(
        val reason: String,
    ) : ReconcileFinishResult
}

class ReconcileBook(
    private val session: SqlSession,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    fun read(accountId: String): ReconcilePage? {
        val account = account(accountId) ?: return null
        val sessionData = loadSession(accountId)
        if (sessionData == null) return null
        return page(account, sessionData)
    }

    fun start(
        accountId: String,
        balanceText: String,
        dateText: String,
    ): ReconcileStartResult {
        val account = account(accountId) ?: return ReconcileStartResult.Rejected(ShellCopy.NO_ACCOUNTS)
        val currency = currency()
        val minor = MoneyParse.parseMagnitude(balanceText, currency.decimals)
            ?: return ReconcileStartResult.Rejected(
                if (balanceText.isBlank()) ReconcileCopy.ENTER_BALANCE else ReconcileCopy.TOO_MANY_DECIMALS,
            )
        val date =
            RegisterEntry.parseDate(dateText)
                ?: return ReconcileStartResult.Rejected(
                    if (dateText.isBlank()) ReconcileCopy.ENTER_DATE else RegisterCopy.ENTER_DATE,
                )
        loadSession(accountId)?.let { existing ->
            return ReconcileStartResult.Started(page(account, existing))
        }
        val baseline = snapshot(accountId)
        session.transaction {
            for ((id, entry) in baseline) {
                if (!entry.reconciled) {
                    session.exec(
                        "UPDATE transactions SET cleared = 0 WHERE id = ?",
                        listOf(id),
                    )
                }
            }
            saveSession(
                accountId,
                ReconcileSession(
                    statementMinor = minor,
                    statementDate = date,
                    baseline = baseline,
                ),
            )
        }
        val sessionData =
            loadSession(accountId)
                ?: return ReconcileStartResult.Rejected(ReconcileCopy.NO_SESSION)
        return ReconcileStartResult.Started(page(account, sessionData))
    }

    fun toggleCleared(
        accountId: String,
        transactionId: String,
    ): ReconcilePage? {
        val account = account(accountId) ?: return null
        val sessionData = loadSession(accountId) ?: return null
        val row = find(accountId, transactionId) ?: return page(account, sessionData)
        if (row.reconciled) return page(account, sessionData)
        session.exec(
            "UPDATE transactions SET cleared = ? WHERE id = ?",
            listOf(if (row.cleared) 0 else 1, transactionId),
        )
        return page(account, sessionData)
    }

    fun finish(accountId: String): ReconcileFinishResult {
        val account = account(accountId) ?: return ReconcileFinishResult.Rejected(ShellCopy.NO_ACCOUNTS)
        val sessionData = loadSession(accountId) ?: return ReconcileFinishResult.Rejected(ReconcileCopy.NO_SESSION)
        val current = page(account, sessionData)
        if (current.differenceMinor != 0L) {
            return ReconcileFinishResult.Rejected(ReconcileCopy.NOT_BALANCED)
        }
        session.transaction {
            session.exec(
                """
                UPDATE transactions
                SET reconciled = 1
                WHERE acct = ?
                    AND IFNULL(tombstone, 0) = 0
                    AND IFNULL(isChild, 0) = 0
                    AND IFNULL(reconciled, 0) = 0
                    AND IFNULL(cleared, 0) = 1
                """.trimIndent(),
                listOf(accountId),
            )
            session.exec(
                "INSERT OR REPLACE INTO preferences (id, value) VALUES (?, ?)",
                listOf(statementBalanceKey(accountId), sessionData.statementMinor.toString()),
            )
            session.exec(
                "INSERT OR REPLACE INTO preferences (id, value) VALUES (?, ?)",
                listOf(statementDateKey(accountId), sessionData.statementDate.toString()),
            )
            session.exec(
                "UPDATE accounts SET last_reconciled = ? WHERE id = ?",
                listOf(System.currentTimeMillis().toString(), accountId),
            )
            clearSession(accountId)
        }
        return ReconcileFinishResult.Finished
    }

    fun cancel(accountId: String): Boolean {
        val sessionData = loadSession(accountId) ?: return false
        session.transaction {
            for ((id, entry) in sessionData.baseline) {
                session.exec(
                    "UPDATE transactions SET cleared = ?, reconciled = ? WHERE id = ?",
                    listOf(if (entry.cleared) 1 else 0, if (entry.reconciled) 1 else 0, id),
                )
            }
            clearSession(accountId)
        }
        return true
    }

    private fun page(
        account: AccountRow,
        sessionData: ReconcileSession,
    ): ReconcilePage {
        val clearedTotal = clearedTotal(account.id)
        val difference = sessionData.statementMinor - clearedTotal
        val rows =
            session
                .query(
                    """
                    SELECT
                        t.id AS id,
                        t.date AS date,
                        t.amount AS amount,
                        t.cleared AS cleared,
                        t.reconciled AS reconciled,
                        COALESCE(p.name, '') AS payee
                    FROM transactions t
                    LEFT JOIN payees p
                        ON p.id = t.description AND IFNULL(p.tombstone, 0) = 0
                    WHERE t.acct = ?
                        AND IFNULL(t.tombstone, 0) = 0
                        AND IFNULL(t.isChild, 0) = 0
                    ORDER BY t.date DESC, t.sort_order DESC, t.id DESC
                    """.trimIndent(),
                    listOf(account.id),
                ).map { row ->
                    ReconcileRow(
                        id = row.str("id").orEmpty(),
                        date = row.long("date").toInt(),
                        payee = row.str("payee").orEmpty().ifBlank { ShellCopy.NO_PAYEE },
                        amountMinor = row.long("amount"),
                        cleared = row.bool("cleared"),
                        reconciled = row.bool("reconciled"),
                    )
                }
        return ReconcilePage(
            account =
                RegisterAccount(
                    id = account.id,
                    name = account.name,
                    offBudget = account.offBudget,
                    type = account.type,
                    closed = account.closed,
                ),
            currency = currency(),
            statementMinor = sessionData.statementMinor,
            statementDate = sessionData.statementDate,
            clearedTotalMinor = clearedTotal,
            differenceMinor = difference,
            rows = rows,
            active = true,
        )
    }

    private fun clearedTotal(accountId: String): Long =
        session
            .query(
                """
                SELECT COALESCE(SUM(t.amount), 0) AS total
                FROM transactions t
                WHERE t.acct = ?
                    AND $ALIVE
                    AND (IFNULL(t.reconciled, 0) = 1 OR IFNULL(t.cleared, 0) = 1)
                """.trimIndent(),
                listOf(accountId),
            ).first()
            .long("total")

    private fun snapshot(accountId: String): Map<String, ReconcileBaselineEntry> =
        session
            .query(
                """
                SELECT id, cleared, reconciled
                FROM transactions
                WHERE acct = ?
                    AND IFNULL(tombstone, 0) = 0
                    AND IFNULL(isChild, 0) = 0
                """.trimIndent(),
                listOf(accountId),
            ).associate { row ->
                row.str("id").orEmpty() to
                    ReconcileBaselineEntry(
                        cleared = row.bool("cleared"),
                        reconciled = row.bool("reconciled"),
                    )
            }

    private fun find(
        accountId: String,
        transactionId: String,
    ): ReconcileRow? =
        session
            .query(
                """
                SELECT cleared, reconciled
                FROM transactions
                WHERE id = ? AND acct = ? AND IFNULL(tombstone, 0) = 0
                """.trimIndent(),
                listOf(transactionId, accountId),
            ).firstOrNull()
            ?.let { row ->
                ReconcileRow(
                    id = transactionId,
                    date = 0,
                    payee = "",
                    amountMinor = 0,
                    cleared = row.bool("cleared"),
                    reconciled = row.bool("reconciled"),
                )
            }

    private fun loadSession(accountId: String): ReconcileSession? {
        val raw =
            session
                .query(
                    "SELECT value FROM preferences WHERE id = ?",
                    listOf(sessionKey(accountId)),
                ).firstOrNull()
                ?.str("value")
                .orEmpty()
        if (raw.isEmpty()) return null
        return json.decodeFromString(raw)
    }

    private fun saveSession(
        accountId: String,
        data: ReconcileSession,
    ) {
        session.exec(
            "INSERT OR REPLACE INTO preferences (id, value) VALUES (?, ?)",
            listOf(sessionKey(accountId), json.encodeToString(data)),
        )
    }

    private fun clearSession(accountId: String) {
        session.exec("DELETE FROM preferences WHERE id = ?", listOf(sessionKey(accountId)))
    }

    private fun account(accountId: String): AccountRow? =
        session
            .query(
                """
                SELECT id, name, offbudget, closed, type
                FROM accounts
                WHERE id = ? AND IFNULL(tombstone, 0) = 0
                """.trimIndent(),
                listOf(accountId),
            ).firstOrNull()
            ?.let { row ->
                AccountRow(
                    id = row.str("id").orEmpty(),
                    name = row.str("name").orEmpty(),
                    offBudget = row.bool("offbudget"),
                    closed = row.bool("closed"),
                    type = row.str("type").orEmpty().ifBlank { "checking" },
                )
            }

    private fun currency(): CurrencySpec {
        val code =
            session
                .query("SELECT value FROM preferences WHERE id = ?", listOf("currency"))
                .firstOrNull()
                ?.str("value")
                .orEmpty()
        return Currencies.byCode(code) ?: Currencies.byCode("USD")!!
    }

    private data class AccountRow(
        val id: String,
        val name: String,
        val offBudget: Boolean,
        val closed: Boolean,
        val type: String,
    )

    companion object {
        private const val ALIVE =
            """
            IFNULL(t.tombstone, 0) = 0
            AND IFNULL(t.isParent, 0) = 0
            AND NOT (
                IFNULL(t.isChild, 0) = 1
                AND EXISTS (
                    SELECT 1 FROM transactions p
                    WHERE p.id = t.parent_id AND IFNULL(p.tombstone, 0) = 1
                )
            )
            """

        fun sessionKey(accountId: String): String = "dueNorthReconcile:$accountId"

        fun statementBalanceKey(accountId: String): String = "dueNorthStatementBalance:$accountId"

        fun statementDateKey(accountId: String): String = "dueNorthStatementDate:$accountId"
    }
}
