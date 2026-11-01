package app.duenorth.budget.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

object ReconcileCopy {
    const val ENTER_BALANCE = "enter the statement balance"
    const val ENTER_DATE = "enter the statement date"
    const val TOO_MANY_DECIMALS = "too many decimal places"
    const val NOT_BALANCED = "cleared total must match the statement"
    const val NO_SESSION = "no reconciliation in progress"
    const val STATEMENT = "statement"
    const val CLEARED = "cleared"
    const val RECONCILE = "reconcile"
    const val FINISH = "finish"
    const val CANCEL = "cancel"
    const val ADJUSTMENT_PAYEE = "Reconciliation balance adjustment"
    const val START = "start"
    const val STATEMENT_BALANCE = "statement ending balance"
    const val STATEMENT_DATE = "statement ending date"
    const val PREFILLED = "starts at your cleared balance"
    const val OFF_BY = "off by"
    const val BALANCED = "balanced"
    const val TAP_TO_CLEAR = "tap each transaction you find on your statement"
    const val AFTER_STATEMENT = "after the statement"
    const val BEFORE_LAST = "before your last reconcile"
    const val FINISH_LOCKS = "finish locks"
    const val FINISH_ANYWAY = "finish anyway?"
    const val ADD_AND_FINISH = "add and finish"
    const val KEEP_LOOKING = "keep looking"
    const val CLEAR_IT = "clear it"
    const val CLEAR_BOTH = "clear both"
    const val UNCLEAR_IT = "unclear it"
    const val UNCLEAR_ONE = "unclear one"
    const val FLIP_IT = "flip it"
    const val OPEN_IT = "open it"
    const val ADD_IT = "add it"
    const val NO_CATEGORY_LATER = "it has no category; you can give it one later."
    const val LOCKED = "locked"
    const val NOT_CLEARED = "not cleared"
    const val LOCKED_NOTE = "matched an earlier statement, so they stay as they are"

    // Lead tags: what kind of problem each one is.
    const val TAG_EXACT = "exact match"
    const val TAG_FLIP = "sign flip"
    const val TAG_PAIR = "two add up"
    const val TAG_MAYBE_NOT = "maybe not on it"
    const val TAG_LATE = "after statement"
    const val TAG_DUPLICATE = "duplicate?"
    const val TAG_SWAPPED = "swapped digits?"
    const val TAG_MISSING = "never entered?"

    const val MISSING = "something on your statement may not be in your register"

    fun adjustExplain(account: String) = "add an adjustment so $account matches your bank. it is marked so you can find it later."

    fun leads(count: Int) = if (count == 1) "1 lead" else "$count leads, most likely first"

    fun lockedThrough(date: String) = "locked thru $date"

    fun reconciledThrough(
        date: String,
        count: Int,
    ) = "reconciled through $date · $count"

    fun staleTag(days: Int) = "$days days old"

    fun exact(
        payee: String,
        amount: String,
        date: String,
    ) = "$payee $amount on $date isn't cleared"

    fun flip(
        payee: String,
        amount: String,
        cleared: Boolean,
    ): String {
        val turned = "$payee $amount the other way round"
        return if (cleared) "$turned would balance exactly" else "$payee $amount isn't cleared; the other way round it would balance"
    }

    fun late(
        payee: String,
        amount: String,
        date: String,
    ) = "$payee $amount is dated $date"

    fun maybeNot(
        payee: String,
        amount: String,
    ) = "$payee $amount is cleared but may not be on this statement"

    fun duplicate(
        payee: String,
        amount: String,
        first: String,
        second: String,
    ) = if (first == second) "$payee $amount twice on $first" else "$payee $amount on $second and again $first"

    fun stale(
        payee: String,
        amount: String,
        date: String,
    ) = "$payee $amount, not cleared since $date"

    fun swapped(amount: String) = "$amount divides by 9, the mark of two digits swapped"

    fun leaves(amount: String) = "→ $amount"
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
    /** A transfer: its sign is tied to the other account's half, so the helper never offers to flip it. */
    val transfer: Boolean = false,
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
        val magnitude =
            MoneyParse.parseMagnitude(balanceText, currency.decimals)
                ?: return ReconcileStartResult.Rejected(
                    if (balanceText.isBlank()) ReconcileCopy.ENTER_BALANCE else ReconcileCopy.TOO_MANY_DECIMALS,
                )
        // A card's statement can be owed, so a leading minus is kept.
        val minor = if (balanceText.trim().startsWith("-")) -magnitude else magnitude
        val date =
            RegisterEntry.parseDate(dateText)
                ?: return ReconcileStartResult.Rejected(
                    if (dateText.isBlank()) ReconcileCopy.ENTER_DATE else RegisterCopy.ENTER_DATE,
                )
        loadSession(accountId)?.let { existing ->
            return ReconcileStartResult.Started(page(account, existing))
        }
        // Cleared marks made in the register stay; the baseline only lets cancel put them back.
        val baseline = snapshot(accountId)
        session.transaction {
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
        SyncSchema.write(session, "transactions", transactionId, "cleared", !row.cleared)
        return page(account, sessionData)
    }

    /** Sets several rows' cleared mark at once, as a helper's "clear both" does. Reconciled rows are left alone. */
    fun setCleared(
        accountId: String,
        transactionIds: List<String>,
        cleared: Boolean,
    ): ReconcilePage? {
        val account = account(accountId) ?: return null
        val sessionData = loadSession(accountId) ?: return null
        session.transaction {
            for (id in transactionIds) {
                val row = find(accountId, id) ?: continue
                if (!row.reconciled) SyncSchema.write(session, "transactions", id, "cleared", cleared)
            }
        }
        return page(account, sessionData)
    }

    /**
     * Turns [transactionId]'s amount around, money in for money out, as a helper's "flip it" does; a split's
     * parts turn with it. Reconciled rows and transfers are left alone.
     */
    fun flipSign(
        accountId: String,
        transactionId: String,
    ): ReconcilePage? {
        val account = account(accountId) ?: return null
        val sessionData = loadSession(accountId) ?: return null
        val row =
            session
                .query(
                    """
                    SELECT t.amount AS amount, t.reconciled AS reconciled, p.transfer_acct AS transfer_acct
                    FROM transactions t
                    LEFT JOIN payees p ON p.id = t.description
                    WHERE t.id = ? AND t.acct = ? AND IFNULL(t.tombstone, 0) = 0
                    """.trimIndent(),
                    listOf(transactionId, accountId),
                ).firstOrNull() ?: return page(account, sessionData)
        if (row.bool("reconciled") || row.str("transfer_acct") != null) return page(account, sessionData)
        session.transaction {
            SyncSchema.write(session, "transactions", transactionId, "amount", -row.long("amount"))
            session
                .query(
                    "SELECT id, amount FROM transactions WHERE parent_id = ? AND IFNULL(tombstone, 0) = 0",
                    listOf(transactionId),
                ).forEach { part ->
                    SyncSchema.write(session, "transactions", part.str("id").orEmpty(), "amount", -part.long("amount"))
                }
        }
        return page(account, sessionData)
    }

    /**
     * Locks the cleared rows. When the cleared total is still off and [adjust] is set, a cleared
     * "reconciliation balance adjustment" for the difference is added on the statement date first, as Actual does.
     */
    fun finish(
        accountId: String,
        adjust: Boolean = false,
        ids: () -> String = { UUID.randomUUID().toString() },
    ): ReconcileFinishResult {
        val account = account(accountId) ?: return ReconcileFinishResult.Rejected(ShellCopy.NO_ACCOUNTS)
        val sessionData = loadSession(accountId) ?: return ReconcileFinishResult.Rejected(ReconcileCopy.NO_SESSION)
        val current = page(account, sessionData)
        if (current.differenceMinor != 0L && !adjust) {
            return ReconcileFinishResult.Rejected(ReconcileCopy.NOT_BALANCED)
        }
        session.transaction {
            if (current.differenceMinor != 0L) {
                val register = RegisterBook(session, ids)
                register.insertImportedTransaction(
                    id = ids(),
                    accountId = accountId,
                    categoryId = null,
                    amount = current.differenceMinor,
                    payeeId = register.payeeIdForName(ReconcileCopy.ADJUSTMENT_PAYEE),
                    note = null,
                    date = sessionData.statementDate,
                    financialId = null,
                    importedPayee = null,
                )
            }
            session
                .query(
                    """
                    SELECT id FROM transactions
                    WHERE acct = ?
                        AND IFNULL(tombstone, 0) = 0
                        AND IFNULL(isChild, 0) = 0
                        AND IFNULL(reconciled, 0) = 0
                        AND IFNULL(cleared, 0) = 1
                    """.trimIndent(),
                    listOf(accountId),
                ).mapNotNull { it.str("id") }
                .forEach { id -> SyncSchema.write(session, "transactions", id, "reconciled", 1) }
            session.exec(
                "INSERT OR REPLACE INTO preferences (id, value) VALUES (?, ?)",
                listOf(statementBalanceKey(accountId), sessionData.statementMinor.toString()),
            )
            session.exec(
                "INSERT OR REPLACE INTO preferences (id, value) VALUES (?, ?)",
                listOf(statementDateKey(accountId), sessionData.statementDate.toString()),
            )
            SyncSchema.write(session, "accounts", accountId, "last_reconciled", System.currentTimeMillis().toString())
            clearSession(accountId)
        }
        return ReconcileFinishResult.Finished
    }

    fun cancel(accountId: String): Boolean {
        val sessionData = loadSession(accountId) ?: return false
        session.transaction {
            for ((id, entry) in sessionData.baseline) {
                SyncSchema.write(session, "transactions", id, "cleared", entry.cleared)
                SyncSchema.write(session, "transactions", id, "reconciled", entry.reconciled)
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
                        COALESCE(p.name, '') AS payee,
                        target.name AS transfer_name
                    FROM transactions t
                    LEFT JOIN payees p
                        ON p.id = t.description AND IFNULL(p.tombstone, 0) = 0
                    LEFT JOIN accounts target
                        ON target.id = p.transfer_acct
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
                        payee =
                            row.str("transfer_name")?.let { RegisterEntry.transferLabel(row.long("amount"), it) }
                                ?: row.str("payee").orEmpty().ifBlank { ShellCopy.NO_PAYEE },
                        amountMinor = row.long("amount"),
                        cleared = row.bool("cleared"),
                        reconciled = row.bool("reconciled"),
                        transfer = row.str("transfer_name") != null,
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
