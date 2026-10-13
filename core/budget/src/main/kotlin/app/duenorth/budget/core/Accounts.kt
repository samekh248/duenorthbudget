package app.duenorth.budget.core

import java.time.LocalDate
import java.util.UUID

object AccountsCopy {
    const val ADD = "add account"
    const val EDIT = "account change"
    const val NOTE = "account note"
    const val REORDER = "reorder accounts"
    const val CLOSE = "close account"
    const val FORCE_CLOSE = "force close"
    const val REOPEN = "reopen account"
    const val ENTER_NAME = "enter a name"
    const val CHOOSE_DESTINATION = "choose where the balance goes"
    const val CHOOSE_CATEGORY = "choose a category for money leaving the budget"
    const val SAME_ACCOUNT = "choose a different account"
    const val FORCE_CONFIRM =
        "force close deletes this account and its transactions; transfers in other accounts lose their payee"
    const val CLOSING_NOTE = "Closing account"
    const val STARTING_BALANCE = "Starting Balance"
    const val NOT_FOUND = "account not found"
}

sealed interface AccountWriteResult {
    data class Saved(
        val accountId: String,
    ) : AccountWriteResult

    data class Rejected(
        val reason: String,
    ) : AccountWriteResult

    /** Nothing written; call again with the confirm flag. */
    data class NeedsConfirm(
        val reason: String,
    ) : AccountWriteResult
}

/** One account as the account screens need it. */
data class AccountDetail(
    val id: String,
    val name: String,
    val offBudget: Boolean,
    val closed: Boolean,
    val balanceMinor: Long,
    val transactionCount: Int,
    val note: String,
    val linked: Boolean,
)

/**
 * Accounts ported from Actual 26.8.1 `server/accounts/app.ts` (`contracts/accounts.md`):
 * `createAccount`, `updateAccount`, `moveAccount`, `closeAccount` (with transfer, category
 * and the forced path) and `reopenAccount`. Every write goes through the sync funnel.
 */
class AccountsBook(
    private val session: SqlSession,
    private val today: LocalDate,
    private val ids: () -> String = { UUID.randomUUID().toString() },
) {
    fun detail(accountId: String): AccountDetail? {
        val row =
            session
                .query(
                    """
                    SELECT id, name, offbudget, closed, account_id, account_sync_source
                    FROM accounts WHERE id = ? AND IFNULL(tombstone, 0) = 0
                    """.trimIndent(),
                    listOf(accountId),
                ).firstOrNull() ?: return null
        val (balance, count) = properties(accountId)
        return AccountDetail(
            id = accountId,
            name = row.str("name").orEmpty(),
            offBudget = row.bool("offbudget"),
            closed = row.bool("closed"),
            balanceMinor = balance,
            transactionCount = count,
            note = note(accountId),
            linked = !row.str("account_id").isNullOrBlank() && !row.str("account_sync_source").isNullOrBlank(),
        )
    }

    /** Live accounts, open first, in Actual's order; closed ones only when [includeClosed]. */
    fun list(includeClosed: Boolean): List<AccountDetail> =
        session
            .query(
                """
                SELECT id FROM accounts
                WHERE IFNULL(tombstone, 0) = 0 ${if (includeClosed) "" else "AND IFNULL(closed, 0) = 0"}
                ORDER BY closed, offbudget, sort_order, name, id
                """.trimIndent(),
            ).mapNotNull { detail(it.str("id").orEmpty()) }

    /** `createAccount`: the account, its transfer payee, and a Starting Balance transaction when [balanceMinor] ≠ 0. */
    fun create(
        name: String,
        offBudget: Boolean,
        balanceMinor: Long,
    ): AccountWriteResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return AccountWriteResult.Rejected(AccountsCopy.ENTER_NAME)
        val id = ids()
        session.transaction {
            SyncSchema.writeRow(
                session,
                "accounts",
                id,
                "name" to trimmed,
                "offbudget" to offBudget,
                "closed" to false,
                "tombstone" to false,
                "sort_order" to nextSortOrder(offBudget),
            )
            SyncSchema.writeRow(session, "payees", ids(), "name" to "", "transfer_acct" to id, "tombstone" to false)
            if (balanceMinor != 0L) {
                val payee = findOrCreatePayee(AccountsCopy.STARTING_BALANCE)
                writeTransaction(
                    id = ids(),
                    accountId = id,
                    amount = balanceMinor,
                    payeeId = payee,
                    categoryId = if (offBudget) null else startingBalanceCategory(),
                    notes = null,
                    cleared = true,
                    startingBalance = true,
                    transferId = null,
                )
            }
        }
        return AccountWriteResult.Saved(id)
    }

    fun rename(
        accountId: String,
        name: String,
    ): AccountWriteResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return AccountWriteResult.Rejected(AccountsCopy.ENTER_NAME)
        if (!exists(accountId)) return AccountWriteResult.Rejected(AccountsCopy.NOT_FOUND)
        SyncSchema.write(session, "accounts", accountId, "name", trimmed)
        return AccountWriteResult.Saved(accountId)
    }

    /** `notes-save` for `account-<id>`. */
    fun setNote(
        accountId: String,
        note: String,
    ): AccountWriteResult {
        if (!exists(accountId)) return AccountWriteResult.Rejected(AccountsCopy.NOT_FOUND)
        SyncSchema.writeRow(session, "notes", "account-$accountId", "note" to note)
        return AccountWriteResult.Saved(accountId)
    }

    fun note(accountId: String): String =
        session
            .query("SELECT note FROM notes WHERE id = ?", listOf("account-$accountId"))
            .firstOrNull()
            ?.str("note")
            .orEmpty()

    /**
     * `moveAccount`: places [accountId] before [targetId] among open accounts on the same side
     * of the budget, or last when [targetId] is null, using Actual's `shoveSortOrders`.
     */
    fun move(
        accountId: String,
        targetId: String?,
    ): AccountWriteResult {
        val account =
            session
                .query("SELECT offbudget FROM accounts WHERE id = ? AND IFNULL(tombstone, 0) = 0", listOf(accountId))
                .firstOrNull() ?: return AccountWriteResult.Rejected(AccountsCopy.NOT_FOUND)
        val items =
            session
                .query(
                    """
                    SELECT id, sort_order FROM accounts
                    WHERE IFNULL(offbudget, 0) = ? AND IFNULL(closed, 0) = 0 AND IFNULL(tombstone, 0) = 0
                    ORDER BY sort_order, name
                    """.trimIndent(),
                    listOf(if (account.bool("offbudget")) 1 else 0),
                ).map { it.str("id").orEmpty() to it.double("sort_order") }
        val (updates, order) = shoveSortOrders(items, targetId)
        session.transaction {
            updates.forEach { (id, sort) -> SyncSchema.write(session, "accounts", id, "sort_order", sort) }
            SyncSchema.write(session, "accounts", accountId, "sort_order", order)
        }
        return AccountWriteResult.Saved(accountId)
    }

    /**
     * `closeAccount`. With no transactions the account is deleted, as Actual does. With a balance,
     * [destinationId] is required, and [categoryId] too when the money leaves the budget.
     * [forced] needs [confirmed] and deletes the account and its transactions.
     */
    fun close(
        accountId: String,
        destinationId: String?,
        categoryId: String?,
        forced: Boolean = false,
        confirmed: Boolean = false,
    ): AccountWriteResult {
        val account = accountRow(accountId) ?: return AccountWriteResult.Rejected(AccountsCopy.NOT_FOUND)
        if (account.closed) return AccountWriteResult.Saved(accountId)
        val (balance, count) = properties(accountId)
        if (forced) {
            if (!confirmed) return AccountWriteResult.NeedsConfirm(AccountsCopy.FORCE_CONFIRM)
        } else if (count > 0 && balance != 0L) {
            if (destinationId.isNullOrBlank()) return AccountWriteResult.Rejected(AccountsCopy.CHOOSE_DESTINATION)
            if (destinationId == accountId) return AccountWriteResult.Rejected(AccountsCopy.SAME_ACCOUNT)
            val destination = accountRow(destinationId) ?: return AccountWriteResult.Rejected(AccountsCopy.NOT_FOUND)
            if (!account.offBudget && destination.offBudget && !categoryAlive(categoryId)) {
                return AccountWriteResult.Rejected(AccountsCopy.CHOOSE_CATEGORY)
            }
        }
        session.transaction {
            unlink(accountId)
            when {
                count == 0 -> SyncSchema.write(session, "accounts", accountId, "tombstone", true)
                forced -> forceClose(accountId)
                else -> {
                    SyncSchema.write(session, "accounts", accountId, "closed", true)
                    if (balance != 0L && destinationId != null) transferOut(account, destinationId, balance, categoryId)
                }
            }
        }
        return AccountWriteResult.Saved(accountId)
    }

    fun reopen(accountId: String): AccountWriteResult {
        if (!exists(accountId)) return AccountWriteResult.Rejected(AccountsCopy.NOT_FOUND)
        SyncSchema.write(session, "accounts", accountId, "closed", false)
        return AccountWriteResult.Saved(accountId)
    }

    /** `unlinkAccount`: clears the bank link so fetch is no longer offered (spec 007). */
    fun unlink(accountId: String) {
        val row =
            session
                .query("SELECT account_id, bank, account_sync_source FROM accounts WHERE id = ?", listOf(accountId))
                .firstOrNull() ?: return
        if (row.str("account_id") == null && row.str("bank") == null && row.str("account_sync_source") == null) return
        SyncSchema.write(session, "accounts", accountId, "account_id", null)
        SyncSchema.write(session, "accounts", accountId, "bank", null)
        SyncSchema.write(session, "accounts", accountId, "account_sync_source", null)
        SyncSchema.write(session, "accounts", accountId, "balance_current", null)
        SyncSchema.write(session, "accounts", accountId, "balance_available", null)
        SyncSchema.write(session, "accounts", accountId, "balance_limit", null)
    }

    /** The forced branch: unlink transfer partners, delete every transaction, the account, and its transfer payee. */
    private fun forceClose(accountId: String) {
        session
            .query(
                "SELECT id, transferred_id FROM transactions WHERE acct = ? AND IFNULL(tombstone, 0) = 0",
                listOf(accountId),
            ).forEach { row ->
                val partner = row.str("transferred_id")
                if (!partner.isNullOrBlank()) {
                    SyncSchema.write(session, "transactions", partner, "description", null)
                    SyncSchema.write(session, "transactions", partner, "transferred_id", null)
                }
                SyncSchema.write(session, "transactions", row.str("id").orEmpty(), "tombstone", true)
            }
        SyncSchema.write(session, "accounts", accountId, "tombstone", true)
        session
            .query("SELECT id FROM payees WHERE transfer_acct = ? AND IFNULL(tombstone, 0) = 0", listOf(accountId))
            .forEach { SyncSchema.write(session, "payees", it.str("id").orEmpty(), "tombstone", true) }
    }

    /** `transaction-add` of `-balance` to the destination's transfer payee, which adds the partner side. */
    private fun transferOut(
        account: AccountRowInfo,
        destinationId: String,
        balance: Long,
        categoryId: String?,
    ) {
        val destination = accountRow(destinationId) ?: return
        val id = ids()
        val partnerId = ids()
        // Actual's transfer rules: a category only on the on-budget side of an on → off transfer.
        val category = if (!account.offBudget && destination.offBudget) categoryId else null
        writeTransaction(
            id = id,
            accountId = account.id,
            amount = -balance,
            payeeId = transferPayee(destinationId),
            categoryId = category,
            notes = AccountsCopy.CLOSING_NOTE,
            cleared = true,
            startingBalance = false,
            transferId = partnerId,
        )
        writeTransaction(
            id = partnerId,
            accountId = destinationId,
            amount = balance,
            payeeId = transferPayee(account.id),
            categoryId = null,
            notes = AccountsCopy.CLOSING_NOTE,
            cleared = false,
            startingBalance = false,
            transferId = id,
        )
    }

    private fun writeTransaction(
        id: String,
        accountId: String,
        amount: Long,
        payeeId: String?,
        categoryId: String?,
        notes: String?,
        cleared: Boolean,
        startingBalance: Boolean,
        transferId: String?,
    ) {
        SyncSchema.writeRow(
            session,
            "transactions",
            id,
            "acct" to accountId,
            "amount" to amount,
            "description" to payeeId,
            "notes" to notes,
            "date" to today.year * 10_000 + today.monthValue * 100 + today.dayOfMonth,
            "category" to categoryId,
            "isParent" to false,
            "isChild" to false,
            "starting_balance_flag" to startingBalance,
            "transferred_id" to transferId,
            "sort_order" to System.currentTimeMillis().toDouble(),
            "cleared" to cleared,
            "reconciled" to false,
            "tombstone" to false,
        )
    }

    /** Alive, non-parent balance and count, as `getAccountProperties` reads `v_transactions`. */
    private fun properties(accountId: String): Pair<Long, Int> {
        val row =
            session
                .query(
                    """
                    SELECT COALESCE(SUM(t.amount), 0) AS balance, COUNT(t.id) AS n
                    FROM transactions t
                    WHERE t.acct = ? AND $ALIVE
                    """.trimIndent(),
                    listOf(accountId),
                ).first()
        return row.long("balance") to row.long("n").toInt()
    }

    private fun nextSortOrder(offBudget: Boolean): Double {
        val last =
            session
                .query(
                    "SELECT MAX(sort_order) AS sort FROM accounts WHERE IFNULL(offbudget, 0) = ?",
                    listOf(if (offBudget) 1 else 0),
                ).firstOrNull()
                ?.values
                ?.get("sort")
        val value = (last as? Number)?.toDouble() ?: last?.toString()?.toDoubleOrNull()
        return if (value == null) SORT_INCREMENT else value + SORT_INCREMENT
    }

    /** `getStartingBalancePayee`'s category: income "Starting Balances", else the first income category. */
    private fun startingBalanceCategory(): String? =
        session
            .query(
                """
                SELECT id FROM categories
                WHERE IFNULL(is_income, 0) = 1 AND LOWER(name) = 'starting balances' AND IFNULL(tombstone, 0) = 0
                LIMIT 1
                """.trimIndent(),
            ).firstOrNull()
            ?.str("id")
            ?: session
                .query("SELECT id FROM categories WHERE IFNULL(is_income, 0) = 1 AND IFNULL(tombstone, 0) = 0 LIMIT 1")
                .firstOrNull()
                ?.str("id")

    private fun findOrCreatePayee(name: String): String =
        session
            .query(
                """
                SELECT id FROM payees
                WHERE LOWER(name) = LOWER(?) AND transfer_acct IS NULL AND IFNULL(tombstone, 0) = 0
                LIMIT 1
                """.trimIndent(),
                listOf(name),
            ).firstOrNull()
            ?.str("id")
            ?: ids().also { SyncSchema.writeRow(session, "payees", it, "name" to name, "tombstone" to false) }

    private fun transferPayee(accountId: String): String =
        session
            .query("SELECT id FROM payees WHERE transfer_acct = ? AND IFNULL(tombstone, 0) = 0 LIMIT 1", listOf(accountId))
            .firstOrNull()
            ?.str("id")
            ?: ids().also {
                SyncSchema.writeRow(session, "payees", it, "name" to "", "transfer_acct" to accountId, "tombstone" to false)
            }

    private fun categoryAlive(categoryId: String?): Boolean =
        !categoryId.isNullOrBlank() &&
            session
                .query("SELECT 1 AS ok FROM categories WHERE id = ? AND IFNULL(tombstone, 0) = 0", listOf(categoryId))
                .isNotEmpty()

    private fun exists(accountId: String): Boolean = accountRow(accountId) != null

    private fun accountRow(accountId: String): AccountRowInfo? =
        session
            .query("SELECT id, offbudget, closed FROM accounts WHERE id = ? AND IFNULL(tombstone, 0) = 0", listOf(accountId))
            .firstOrNull()
            ?.let { AccountRowInfo(it.str("id").orEmpty(), it.bool("offbudget"), it.bool("closed")) }

    private data class AccountRowInfo(
        val id: String,
        val offBudget: Boolean,
        val closed: Boolean,
    )

    companion object {
        private const val SORT_INCREMENT = 16384.0

        /**
         * Actual's `shoveSortOrders`: the order that puts an item before [targetId] (or last),
         * plus any items that must move up to make room.
         */
        fun shoveSortOrders(
            items: List<Pair<String, Double>>,
            targetId: String?,
        ): Pair<List<Pair<String, Double>>, Double> {
            val to = items.indexOfFirst { it.first == targetId }
            if (targetId == null || to == -1) {
                val order = if (items.isEmpty()) SORT_INCREMENT else items.last().second + SORT_INCREMENT
                return emptyList<Pair<String, Double>>() to order
            }
            val target = items[to]
            val before = items.getOrNull(to - 1)
            val updates = ArrayList<Pair<String, Double>>()
            if (target.second - (before?.second ?: 0.0) <= 2) {
                var next = to
                var order = target.second + SORT_INCREMENT
                while (next < items.size) {
                    if (order <= items[next].second) break
                    updates.add(items[next].first to order)
                    next++
                    order += SORT_INCREMENT
                }
            }
            val midpoint = if (before == null) target.second / 2 else (before.second + target.second) / 2
            return updates to midpoint
        }

        private val ALIVE =
            """
            IFNULL(t.tombstone, 0) = 0
            AND IFNULL(t.isParent, 0) = 0
            AND NOT (
                IFNULL(t.isChild, 0) = 1
                AND EXISTS (SELECT 1 FROM transactions p WHERE p.id = t.parent_id AND IFNULL(p.tombstone, 0) = 1)
            )
            """.trimIndent()
    }
}
