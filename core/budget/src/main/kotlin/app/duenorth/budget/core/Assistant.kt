package app.duenorth.budget.core

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.LocalDate

object AssistantCopy {
    const val TITLE = "assistant"
    const val ALL_CLEAR = "nothing needs you right now"
    const val ALL_CLEAR_DETAIL = "No duplicates, no transactions in the wrong account, and nothing missing at the bank."
    const val MAYBE = "maybe"
    const val TO_LOOK_AT = "to look at"
    const val LESS = "show fewer"

    const val BANK_SYNC = "bank sync"
    const val FILE_IMPORT = "file import"
    const val ENTERED = "you entered"

    const val MERGE = "merge"
    const val NOT_DUPLICATES = "not duplicates"
    const val MOVE_AND_MERGE = "move & merge"
    const val ITS_RIGHT = "it's right"
    const val OPEN = "open"
    const val DELETE = "delete"
    const val KEEP_WAITING = "keep waiting"

    const val SAME_AMOUNT = "same amount"
    const val SAME_ACCOUNT = "same account"
    const val SAME_PAYEE = "same payee"
    const val SAME_BANK_ID = "same bank id"
    const val BOTH_UNMATCHED = "both unmatched"
    const val NOTHING_FROM_BANK = "nothing from the bank"
    const val GONE = "This is already sorted out. Pull to refresh."

    fun more(count: Int): String = "show $count more"

    fun daysOld(days: Int): String = "$days days old"

    fun duplicate(account: String): String = "These look like the same transaction twice in $account."

    fun wrongAccount(
        from: String,
        to: String,
    ): String = "You entered this in $from, but the bank shows the same charge in $to."

    fun noBankMatch(
        account: String,
        days: Int,
    ): String = "You entered this in $account $days days ago and the bank still has nothing like it."
}

/** The headings the assistant's findings sit under, in the order they show. */
enum class FindingGroup(
    val title: String,
) {
    TRANSACTIONS("transactions"),
}

/** What a check found, which decides the heading and the buttons. */
enum class FindingKind(
    val group: FindingGroup,
    val title: String,
) {
    DUPLICATE(FindingGroup.TRANSACTIONS, "possible duplicates"),
    WRONG_ACCOUNT(FindingGroup.TRANSACTIONS, "wrong account?"),
    NO_BANK_MATCH(FindingGroup.TRANSACTIONS, "not at the bank yet"),
}

/** How sure a check is: sure findings lead their kind, the rest are labelled maybe. */
enum class FindingSure { HIGH, MAYBE }

/** One transaction in a finding, with where it came from (bank sync, a file import, or entered by hand). */
data class FindingSide(
    val source: String,
    val side: MatchSide,
)

/** A button on a finding. The first one a finding lists is its accent button. */
sealed interface FindingAction {
    val label: String

    /** A bank transaction and the entry it duplicates: the inbox's match, so the entry's details are kept. */
    data class Match(
        val bankId: String,
        val enteredId: String,
    ) : FindingAction {
        override val label = AssistantCopy.MERGE
    }

    /** Desktop Actual's merge of two transactions in one account. */
    data class Merge(
        val transactionIds: List<String>,
    ) : FindingAction {
        override val label = AssistantCopy.MERGE
    }

    /** Moves the entry into the bank transaction's account, then matches the two. */
    data class MoveAndMatch(
        val enteredId: String,
        val bankId: String,
    ) : FindingAction {
        override val label = AssistantCopy.MOVE_AND_MERGE
    }

    /** Opens the transaction's form. */
    data class Open(
        val accountId: String,
        val transactionId: String,
    ) : FindingAction {
        override val label = AssistantCopy.OPEN
    }

    data class Delete(
        val transactionId: String,
    ) : FindingAction {
        override val label = AssistantCopy.DELETE
    }

    /** Never shows this finding again, on any phone. */
    data class Dismiss(
        override val label: String,
    ) : FindingAction
}

data class Finding(
    /** Stable across reads: what a dismissal remembers. */
    val key: String,
    val kind: FindingKind,
    val sure: FindingSure,
    /** One sentence on what the assistant thinks is wrong. */
    val headline: String,
    val sides: List<FindingSide>,
    /** Short reasons, such as "same amount" or "2 days apart". */
    val reasons: List<String>,
    val actions: List<FindingAction>,
    /** The account a fix lands in, so the shell reloads it. */
    val accountId: String,
)

data class AssistantReport(
    val findings: List<Finding>,
) {
    val count: Int get() = findings.size

    /** The findings of each kind that has any, in [FindingKind] order. */
    fun byKind(): Map<FindingKind, List<Finding>> =
        FindingKind.entries.associateWith { kind -> findings.filter { it.kind == kind } }.filterValues { it.isNotEmpty() }

    companion object {
        val EMPTY = AssistantReport(emptyList())
    }
}

/**
 * Looks through the budget for transactions that are probably wrong. Every check reads the phone's copy only and
 * changes nothing; [AssistantBook] carries out what the person picks. A transaction sits in at most one finding, and
 * the checks run in order of how sure they are: matches, duplicates, the wrong account, then nothing at the bank.
 */
object AssistantReader {
    /** How far back the checks look. */
    const val LOOKBACK_DAYS = 120L

    /** Two bank copies or two entries count as one purchase only this close together. */
    const val DUPLICATE_DAYS = 3

    /** An entry and a bank transaction in another account, as close as a match. */
    const val TWIN_DAYS = MatchFinder.WINDOW_DAYS

    /** An entry in an account the bank keeps current is missing once it is this old. */
    const val NO_BANK_MATCH_DAYS = 10

    /** Older entries are left alone: they were settled long ago, one way or another. */
    const val NO_BANK_MATCH_MAX_DAYS = 90

    fun read(
        session: SqlSession,
        today: LocalDate,
    ): AssistantReport {
        val dismissed = AssistantBook.dismissed(session)
        val rows = rows(session, today)
        val byId = rows.associateBy { it.id }
        val taken = mutableSetOf<String>()
        val found = mutableListOf<Draft>()

        // 1. A bank transaction and an entry: the inbox's possible matches.
        MatchFinder.find(session).forEach { pair ->
            val bank = byId[pair.bank.id] ?: return@forEach
            val entered = byId[pair.entered.id] ?: return@forEach
            taken += pair.ids
            found += Draft.Match(bank, entered)
        }
        // A transaction that would match in its own account is not missing, and not in the wrong account.
        val matchable = matchableIds(session)

        // 2. Two bank copies, or the same thing entered twice.
        duplicates(rows.filter { it.bank }, taken) { a, b -> bankTwins(a, b) }.forEach { found += it }
        duplicates(rows.filter { !it.bank }, taken) { a, b -> enteredTwins(a, b) }.forEach { found += it }

        // 3. An entry with no bank copy, and a bank transaction with no entry, in different accounts.
        val fed = rows.filter { it.bank }.map { it.accountId }.toSet()
        val loneEntries = rows.filter { !it.bank && it.id !in taken && it.id !in matchable }
        val loneBank = rows.filter { it.bank && it.id !in taken && it.id !in matchable }
        loneEntries
            .sortedByDescending { it.date }
            .forEach { entry ->
                val twin =
                    loneBank
                        .filter {
                            it.id !in taken && it.accountId != entry.accountId && it.amount == entry.amount &&
                                days(it.date, entry.date) <= TWIN_DAYS
                        }.minWithOrNull(compareBy<Row> { days(it.date, entry.date) }.thenBy { if (samePayee(it, entry)) 0 else 1 })
                        ?: return@forEach
                taken += setOf(entry.id, twin.id)
                found += Draft.Twin(entry, twin, sure = entry.accountId in fed || samePayee(entry, twin))
            }

        // 4. An entry, old enough and not yet reconciled, in an account the bank keeps current, with nothing like it there.
        val todayInt = today.toActualDate()
        rows
            .filter { !it.bank && it.id !in taken && it.id !in matchable && it.accountId in fed && it.date <= todayInt }
            .forEach { entry ->
                val age = days(entry.date, todayInt)
                if (age < NO_BANK_MATCH_DAYS || age > NO_BANK_MATCH_MAX_DAYS) return@forEach
                taken += entry.id
                found += Draft.NoBank(entry, age)
            }

        val drafts = found.filter { it.key !in dismissed }
        if (drafts.isEmpty()) return AssistantReport.EMPTY
        val sides = MatchFinder.sides(session, drafts.flatMap { it.ids }.toSet())
        val findings = drafts.mapNotNull { it.finding(sides) }
        return AssistantReport(
            findings.sortedWith(
                compareBy<Finding> { it.kind.ordinal }
                    .thenBy { it.sure.ordinal }
                    .thenByDescending { f -> f.sides.maxOfOrNull { it.side.date } ?: 0 },
            ),
        )
    }

    /** Pairs [rows] of one account and amount that [twins] calls the same purchase, closest dates first. */
    private fun duplicates(
        rows: List<Row>,
        taken: MutableSet<String>,
        twins: (Row, Row) -> FindingSure?,
    ): List<Draft> {
        val out = mutableListOf<Draft>()
        rows
            .filter { it.id !in taken }
            .groupBy { it.accountId to it.amount }
            .values
            .filter { it.size > 1 }
            .forEach { group ->
                val pairs =
                    group.indices.flatMap { i ->
                        (i + 1 until group.size).mapNotNull { j ->
                            val (a, b) = group[i] to group[j]
                            if (days(a.date, b.date) > DUPLICATE_DAYS) return@mapNotNull null
                            twins(a, b)?.let { Triple(a, b, it) }
                        }
                    }
                pairs
                    .sortedWith(compareBy({ it.third.ordinal }, { days(it.first.date, it.second.date) }))
                    .forEach { (a, b, sure) ->
                        if (a.id in taken || b.id in taken) return@forEach
                        taken += setOf(a.id, b.id)
                        // The bank-sync copy first, then the file import, then the earlier: the one merge keeps.
                        val (kept, other) = listOf(a, b).sortedWith(compareBy({ it.rank }, { it.date })).let { it[0] to it[1] }
                        out += Draft.Duplicate(kept, other, sure)
                    }
            }
        return out
    }

    /** Two bank copies: the same bank id, or a sync and a file import, or one source twice under the same payee. */
    private fun bankTwins(
        a: Row,
        b: Row,
    ): FindingSure? =
        when {
            a.financialId != null && a.financialId == b.financialId -> FindingSure.HIGH
            a.synced != b.synced -> if (samePayee(a, b) || a.date == b.date) FindingSure.HIGH else FindingSure.MAYBE
            samePayee(a, b) -> FindingSure.MAYBE
            else -> null
        }

    /** The same payee entered twice: sure on one day, maybe a day or few apart. */
    private fun enteredTwins(
        a: Row,
        b: Row,
    ): FindingSure? =
        when {
            !samePayee(a, b) -> null
            a.date == b.date -> FindingSure.HIGH
            else -> FindingSure.MAYBE
        }

    private fun samePayee(
        a: Row,
        b: Row,
    ): Boolean =
        (a.payeeId != null && a.payeeId == b.payeeId) ||
            (a.payeeName.isNotBlank() && a.payeeName.equals(b.payeeName, ignoreCase = true))

    /** Ids that have a partner of the other kind (bank or entered) in their own account, dismissed or not. */
    private fun matchableIds(session: SqlSession): Set<String> =
        session
            .query(
                """
                SELECT b.id AS b_id, e.id AS e_id, b.date AS b_date, e.date AS e_date
                FROM transactions b
                JOIN transactions e
                    ON e.acct = b.acct AND e.amount = b.amount AND e.id <> b.id
                WHERE ${MatchFinder.candidate("b")} AND ${MatchFinder.candidate("e")}
                    AND (b.financial_id IS NOT NULL OR b.imported_description IS NOT NULL)
                    AND e.financial_id IS NULL AND e.imported_description IS NULL
                """.trimIndent(),
            ).filter { days(it.long("b_date").toInt(), it.long("e_date").toInt()) <= MatchFinder.WINDOW_DAYS }
            .flatMap { listOfNotNull(it.str("b_id"), it.str("e_id")) }
            .toSet()

    private fun rows(
        session: SqlSession,
        today: LocalDate,
    ): List<Row> =
        session
            .query(
                """
                SELECT t.id AS id, t.acct AS acct, t.amount AS amount, t.date AS date, t.description AS payee_id,
                    COALESCE(p.name, '') AS payee_name, t.financial_id AS financial_id,
                    t.imported_description AS imported
                FROM transactions t
                JOIN accounts a ON a.id = t.acct AND IFNULL(a.tombstone, 0) = 0 AND IFNULL(a.closed, 0) = 0
                LEFT JOIN payees p ON p.id = t.description AND IFNULL(p.tombstone, 0) = 0
                WHERE ${MatchFinder.candidate("t")} AND t.date >= ? AND IFNULL(t.amount, 0) <> 0
                ORDER BY t.date DESC, t.sort_order DESC, t.id DESC
                """.trimIndent(),
                listOf(today.minusDays(LOOKBACK_DAYS).toActualDate()),
            ).map { row ->
                Row(
                    id = row.str("id").orEmpty(),
                    accountId = row.str("acct").orEmpty(),
                    amount = row.long("amount"),
                    date = row.long("date").toInt(),
                    payeeId = row.str("payee_id"),
                    payeeName = row.str("payee_name").orEmpty().ifBlank { row.str("imported").orEmpty() },
                    financialId = row.str("financial_id")?.ifBlank { null },
                    imported = row.str("imported")?.ifBlank { null },
                )
            }

    internal fun days(
        a: Int,
        b: Int,
    ): Int = MatchFinder.daysBetween(a, b)

    private data class Row(
        val id: String,
        val accountId: String,
        val amount: Long,
        val date: Int,
        val payeeId: String?,
        val payeeName: String,
        val financialId: String?,
        val imported: String?,
    ) {
        val synced: Boolean get() = financialId != null

        /** Came from the bank: a sync or a file import. */
        val bank: Boolean get() = financialId != null || imported != null

        /** The order merge keeps them in: a bank sync, then a file import, then an entry. */
        val rank: Int get() =
            if (synced) {
                0
            } else if (bank) {
                1
            } else {
                2
            }

        val source: String get() =
            if (synced) {
                AssistantCopy.BANK_SYNC
            } else if (bank) {
                AssistantCopy.FILE_IMPORT
            } else {
                AssistantCopy.ENTERED
            }
    }

    /** A finding before its transactions are read for display. */
    private sealed interface Draft {
        val key: String
        val ids: List<String>

        fun finding(sides: Map<String, MatchSide>): Finding?

        data class Match(
            val bank: Row,
            val entered: Row,
        ) : Draft {
            override val key = AssistantBook.matchKey(bank.id, entered.id)
            override val ids = listOf(bank.id, entered.id)

            override fun finding(sides: Map<String, MatchSide>): Finding? {
                val b = sides[bank.id] ?: return null
                val e = sides[entered.id] ?: return null
                return Finding(
                    key = key,
                    kind = FindingKind.DUPLICATE,
                    sure = FindingSure.HIGH,
                    headline = AssistantCopy.duplicate(b.accountName),
                    sides = listOf(FindingSide(bank.source, b), FindingSide(entered.source, e)),
                    reasons =
                        listOfNotNull(
                            AssistantCopy.SAME_ACCOUNT,
                            AssistantCopy.SAME_AMOUNT,
                            MatchCopy.apart(days(bank.date, entered.date)),
                            AssistantCopy.SAME_PAYEE.takeIf { samePayee(bank, entered) },
                        ),
                    actions = listOf(FindingAction.Match(bank.id, entered.id), FindingAction.Dismiss(AssistantCopy.NOT_DUPLICATES)),
                    accountId = bank.accountId,
                )
            }
        }

        data class Duplicate(
            val kept: Row,
            val other: Row,
            val sure: FindingSure,
        ) : Draft {
            override val key = AssistantBook.key("dup", kept.id, other.id)
            override val ids = listOf(kept.id, other.id)

            override fun finding(sides: Map<String, MatchSide>): Finding? {
                val k = sides[kept.id] ?: return null
                val o = sides[other.id] ?: return null
                return Finding(
                    key = key,
                    kind = FindingKind.DUPLICATE,
                    sure = sure,
                    headline = AssistantCopy.duplicate(k.accountName),
                    sides = listOf(FindingSide(kept.source, k), FindingSide(other.source, o)),
                    reasons =
                        listOfNotNull(
                            AssistantCopy.SAME_ACCOUNT,
                            AssistantCopy.SAME_AMOUNT,
                            MatchCopy.apart(days(kept.date, other.date)),
                            AssistantCopy.SAME_PAYEE.takeIf { samePayee(kept, other) },
                            AssistantCopy.SAME_BANK_ID.takeIf { kept.financialId != null && kept.financialId == other.financialId },
                        ),
                    actions = listOf(FindingAction.Merge(listOf(kept.id, other.id)), FindingAction.Dismiss(AssistantCopy.NOT_DUPLICATES)),
                    accountId = kept.accountId,
                )
            }
        }

        data class Twin(
            val entered: Row,
            val bank: Row,
            val sure: Boolean,
        ) : Draft {
            override val key = AssistantBook.key("wrong", entered.id, bank.id)
            override val ids = listOf(entered.id, bank.id)

            override fun finding(sides: Map<String, MatchSide>): Finding? {
                val e = sides[entered.id] ?: return null
                val b = sides[bank.id] ?: return null
                return Finding(
                    key = key,
                    kind = FindingKind.WRONG_ACCOUNT,
                    sure = if (sure) FindingSure.HIGH else FindingSure.MAYBE,
                    headline = AssistantCopy.wrongAccount(e.accountName, b.accountName),
                    sides = listOf(FindingSide(entered.source, e), FindingSide(bank.source, b)),
                    reasons =
                        listOfNotNull(
                            AssistantCopy.SAME_AMOUNT,
                            MatchCopy.apart(days(entered.date, bank.date)),
                            AssistantCopy.SAME_PAYEE.takeIf { samePayee(entered, bank) },
                            AssistantCopy.BOTH_UNMATCHED,
                        ),
                    actions =
                        listOf(
                            FindingAction.MoveAndMatch(entered.id, bank.id),
                            FindingAction.Dismiss(AssistantCopy.ITS_RIGHT),
                        ),
                    accountId = bank.accountId,
                )
            }
        }

        data class NoBank(
            val entered: Row,
            val age: Int,
        ) : Draft {
            override val key = AssistantBook.key("nobank", entered.id)
            override val ids = listOf(entered.id)

            override fun finding(sides: Map<String, MatchSide>): Finding? {
                val e = sides[entered.id] ?: return null
                return Finding(
                    key = key,
                    kind = FindingKind.NO_BANK_MATCH,
                    sure = FindingSure.HIGH,
                    headline = AssistantCopy.noBankMatch(e.accountName, age),
                    sides = listOf(FindingSide(entered.source, e)),
                    reasons = listOf(AssistantCopy.daysOld(age), AssistantCopy.NOTHING_FROM_BANK),
                    actions =
                        listOf(
                            FindingAction.Open(entered.accountId, entered.id),
                            FindingAction.Dismiss(AssistantCopy.KEEP_WAITING),
                            FindingAction.Delete(entered.id),
                        ),
                    accountId = entered.accountId,
                )
            }
        }
    }
}

/**
 * Carries out the assistant's fixes that are its own (moving an entry to the bank's account and matching it), and
 * remembers findings the person called fine, in a synced preference so other phones skip them too.
 */
class AssistantBook(
    private val session: SqlSession,
    private val today: LocalDate,
) {
    /**
     * Moves [enteredId] into [bankId]'s account, then matches the two: the bank's copy stays with the entry's payee,
     * category and note. An entry moved into an off-budget account drops its category, as Actual requires.
     */
    fun moveAndMatch(
        enteredId: String,
        bankId: String,
    ): WriteResult {
        val box = arrayOf<WriteResult>(WriteResult.Saved)
        session.transaction {
            val sides = MatchFinder.sides(session, setOf(enteredId, bankId))
            val entered = sides[enteredId]
            val bank = sides[bankId]
            if (entered == null || bank == null || !plain(enteredId) || !plain(bankId)) {
                box[0] = WriteResult.Rejected(AssistantCopy.GONE)
                return@transaction
            }
            val offBudget =
                session
                    .query("SELECT offbudget FROM accounts WHERE id = ?", listOf(bank.accountId))
                    .firstOrNull()
                    ?.bool("offbudget") ?: false
            SyncSchema.writeRow(session, "transactions", enteredId, "acct" to bank.accountId)
            val kept = if (offBudget) entered.copy(categoryId = null) else entered
            val bankSide = if (offBudget) bank.copy(categoryId = null) else bank
            MatchBook(session, today).settle(bankSide, kept, movedFrom = entered.accountName)
        }
        return box[0]
    }

    /** Remembers [key] as fine, and forgets keys whose transactions are gone. */
    fun dismiss(key: String): WriteResult {
        session.transaction {
            val alive = dismissed(session).filter { known -> idsOf(known).all(::isAlive) }.toSet()
            SyncSchema.writeRow(session, "preferences", DISMISSED_PREF, "value" to encode(alive + key))
        }
        return WriteResult.Saved
    }

    private fun plain(id: String): Boolean =
        session
            .query("SELECT id FROM transactions t WHERE t.id = ? AND ${MatchFinder.candidate("t")}", listOf(id))
            .isNotEmpty()

    private fun isAlive(id: String): Boolean =
        session
            .query("SELECT id FROM transactions WHERE id = ? AND IFNULL(tombstone, 0) = 0", listOf(id))
            .isNotEmpty()

    companion object {
        /** A synced preference holding the findings called fine. */
        const val DISMISSED_PREF = "duenorth.assistantDismissed"

        private val json = Json
        private val keys = ListSerializer(String.serializer())

        fun key(
            kind: String,
            vararg ids: String,
        ): String = "$kind:${ids.joinToString("|")}"

        /** A possible match's key: dismissing it is the inbox's not a match, so both places forget it. */
        fun matchKey(
            bankId: String,
            enteredId: String,
        ): String = key(MATCH, bankId, enteredId)

        /** The bank and entered ids of a possible match's [key]; null for any other finding. */
        fun matchIds(key: String): Pair<String, String>? {
            if (!key.startsWith("$MATCH:")) return null
            val ids = idsOf(key)
            return if (ids.size == 2) ids[0] to ids[1] else null
        }

        fun dismissed(session: SqlSession): Set<String> {
            val raw =
                session
                    .query("SELECT value FROM preferences WHERE id = ?", listOf(DISMISSED_PREF))
                    .firstOrNull()
                    ?.str("value")
                    ?: return emptySet()
            return runCatching { json.decodeFromString(keys, raw).toSet() }.getOrDefault(emptySet())
        }

        internal fun encode(values: Collection<String>): String = json.encodeToString(keys, values.sorted())

        private fun idsOf(key: String): List<String> = key.substringAfter(':').split('|').filter { it.isNotBlank() }

        private const val MATCH = "match"
    }
}

private fun LocalDate.toActualDate(): Int = year * 10000 + monthValue * 100 + dayOfMonth
