package app.duenorth.budget.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The assistant's account checks: whether each account can be trusted right now. A bank link that has gone quiet,
 * an account not reconciled in a while, and old transactions the bank never cleared.
 */
internal object AccountChecks {
    /** A linked account is stale once its last sync is this many days old. */
    const val STALE_DAYS = 3

    /** An account is due a reconcile once its last one is this old. */
    const val RECONCILE_DAYS = 45

    /** An account never reconciled is worth one once it has this many cleared transactions. */
    const val RECONCILE_NEVER_MIN = 10

    /** Uncleared transactions older than this, in an account the bank feeds, need a look. */
    const val UNCLEARED_DAYS = 30

    /**
     * [fed] are the accounts the bank sent transactions to lately; [uncleared] are the account and date of every
     * uncleared transaction the transaction checks read.
     */
    fun read(
        session: SqlSession,
        today: LocalDate,
        fed: Set<String>,
        uncleared: List<Pair<String, Int>>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Finding> {
        val accounts =
            session
                .query(
                    """
                    SELECT id, name, account_sync_source AS source, account_id AS bank_id, last_sync, balance_current
                    FROM accounts
                    WHERE IFNULL(tombstone, 0) = 0 AND IFNULL(closed, 0) = 0
                    ORDER BY sort_order, id
                    """.trimIndent(),
                ).map { row ->
                    Account(
                        id = row.str("id").orEmpty(),
                        name = row.str("name").orEmpty(),
                        linked = row.str("source") == "simpleFin" && !row.str("bank_id").isNullOrBlank(),
                        lastSync = row.str("last_sync")?.toLongOrNull(),
                        bankBalance = row.str("balance_current")?.toLongOrNull(),
                    )
                }
        return balance(session, accounts, today, zone) + stale(accounts, today, zone) + reconcile(session, accounts, today) +
            oldUncleared(accounts, fed, uncleared, today)
    }

    /**
     * A linked account that synced lately whose bank balance is neither its register's balance nor its cleared
     * balance: something is missing, doubled, or entered wrong.
     */
    private fun balance(
        session: SqlSession,
        accounts: List<Account>,
        today: LocalDate,
        zone: ZoneId,
    ): List<Finding> =
        accounts
            .filter { it.linked && it.bankBalance != null && fresh(it, today, zone) }
            .mapNotNull { account ->
                val bank = account.bankBalance ?: return@mapNotNull null
                val row =
                    session
                        .query(
                            """
                            SELECT IFNULL(SUM(amount), 0) AS total,
                                IFNULL(SUM(CASE WHEN IFNULL(cleared, 0) = 1 THEN amount ELSE 0 END), 0) AS cleared
                            FROM transactions
                            WHERE acct = ? AND IFNULL(tombstone, 0) = 0 AND IFNULL(isParent, 0) = 0
                            """.trimIndent(),
                            listOf(account.id),
                        ).firstOrNull() ?: return@mapNotNull null
                val total = row.long("total")
                val cleared = row.long("cleared")
                if (bank == total || bank == cleared) return@mapNotNull null
                Finding(
                    key = AssistantBook.key("balance", account.id, "$bank", "$total"),
                    kind = FindingKind.BANK_BALANCE,
                    sure = FindingSure.HIGH,
                    headline = AssistantCopy.bankBalance(account.name),
                    sides = emptyList(),
                    reasons = emptyList(),
                    actions = listOf(FindingAction.Reconcile(account.id), FindingAction.Dismiss(AssistantCopy.LATER)),
                    accountId = account.id,
                    difference = bank - total,
                    figures = listOf(FindingFigure(AssistantCopy.AT_THE_BANK, bank), FindingFigure(AssistantCopy.IN_REGISTER, total)),
                )
            }

    private fun fresh(
        account: Account,
        today: LocalDate,
        zone: ZoneId,
    ): Boolean {
        val last = account.lastSync?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: return false
        return ChronoUnit.DAYS.between(last, today) < STALE_DAYS
    }

    private fun stale(
        accounts: List<Account>,
        today: LocalDate,
        zone: ZoneId,
    ): List<Finding> =
        accounts
            .filter { it.linked }
            .mapNotNull { account ->
                val last = account.lastSync?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
                val days = last?.let { ChronoUnit.DAYS.between(it, today).toInt() }
                if (days != null && days < STALE_DAYS) return@mapNotNull null
                Finding(
                    key = AssistantBook.key("stale", account.id, account.lastSync?.toString() ?: "never"),
                    kind = FindingKind.STALE_SYNC,
                    sure = FindingSure.HIGH,
                    headline = AssistantCopy.staleSync(account.name, days),
                    sides = emptyList(),
                    reasons = emptyList(),
                    actions = listOf(FindingAction.SyncBank, FindingAction.Dismiss(AssistantCopy.LATER)),
                    accountId = account.id,
                )
            }

    private fun reconcile(
        session: SqlSession,
        accounts: List<Account>,
        today: LocalDate,
    ): List<Finding> {
        val stats =
            session
                .query(
                    """
                    SELECT t.acct AS acct,
                        MAX(CASE WHEN IFNULL(t.reconciled, 0) = 1 THEN t.date END) AS last,
                        COUNT(*) AS n
                    FROM transactions t
                    WHERE IFNULL(t.tombstone, 0) = 0 AND IFNULL(t.isChild, 0) = 0 AND IFNULL(t.cleared, 0) = 1
                    GROUP BY t.acct
                    """.trimIndent(),
                ).associateBy { it.str("acct").orEmpty() }
        val due = today.minusDays(RECONCILE_DAYS.toLong()).let { it.year * 10000 + it.monthValue * 100 + it.dayOfMonth }
        return accounts.mapNotNull { account ->
            val row = stats[account.id] ?: return@mapNotNull null
            val last = row.str("last")?.toIntOrNull()
            val since =
                session
                    .query(
                        """
                        SELECT COUNT(*) AS n FROM transactions
                        WHERE acct = ? AND IFNULL(tombstone, 0) = 0 AND IFNULL(isChild, 0) = 0
                            AND IFNULL(cleared, 0) = 1 AND IFNULL(reconciled, 0) = 0 AND date > ?
                        """.trimIndent(),
                        listOf(account.id, last ?: 0),
                    ).firstOrNull()
                    ?.long("n")
                    ?.toInt() ?: 0
            val wanted = if (last == null) since >= RECONCILE_NEVER_MIN else last < due && since > 0
            if (!wanted) return@mapNotNull null
            Finding(
                key = AssistantBook.key("reconcile", account.id, last?.toString() ?: "never"),
                kind = FindingKind.RECONCILE,
                sure = FindingSure.HIGH,
                headline = AssistantCopy.reconcile(account.name, last?.let { RegisterEntry.registerDateLabel(it) }, since),
                sides = emptyList(),
                reasons = emptyList(),
                actions = listOf(FindingAction.Reconcile(account.id), FindingAction.Dismiss(AssistantCopy.LATER)),
                accountId = account.id,
            )
        }
    }

    private fun oldUncleared(
        accounts: List<Account>,
        fed: Set<String>,
        uncleared: List<Pair<String, Int>>,
        today: LocalDate,
    ): List<Finding> {
        val before = today.minusDays(UNCLEARED_DAYS.toLong()).let { it.year * 10000 + it.monthValue * 100 + it.dayOfMonth }
        val byAccount = uncleared.filter { (acct, date) -> acct in fed && date < before }.groupBy({ it.first }, { it.second })
        return accounts.mapNotNull { account ->
            val dates = byAccount[account.id] ?: return@mapNotNull null
            Finding(
                key = AssistantBook.key("uncleared", account.id, "${dates.size}", "${dates.max()}"),
                kind = FindingKind.OLD_UNCLEARED,
                sure = FindingSure.HIGH,
                headline = AssistantCopy.oldUncleared(account.name, dates.size, UNCLEARED_DAYS),
                sides = emptyList(),
                reasons = emptyList(),
                actions = listOf(FindingAction.OpenAccount(account.id), FindingAction.Dismiss(AssistantCopy.LATER)),
                accountId = account.id,
            )
        }
    }

    private data class Account(
        val id: String,
        val name: String,
        val linked: Boolean,
        val lastSync: Long?,
        val bankBalance: Long? = null,
    )
}
