package app.duenorth.budget.core

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

object MatchCopy {
    const val POSSIBLE_MATCH = "possible match"
    const val FROM_BANK = "from the bank"
    const val ENTERED = "entered by you"
    const val MATCH = "match"
    const val NOT_A_MATCH = "not a match"
    const val TO_MATCH = "to match"
    const val SAME_AMOUNT = "same amount"
    const val SAME_DAY = "same day"
    const val SAME_ACCOUNT = "same account"
    const val NO_CATEGORY = "no category"
    const val GONE = "One of these transactions is gone. Pull to refresh."

    fun apart(days: Int): String =
        when (days) {
            0 -> SAME_DAY
            1 -> "1 day apart"
            else -> "$days days apart"
        }
}

/** One side of a possible match, as the pair panel shows it. */
data class MatchSide(
    val id: String,
    val payee: String,
    val date: Int,
    val amountMinor: Long,
    val accountId: String,
    val accountName: String,
    val categoryId: String?,
    val categoryName: String,
    val note: String,
)

/**
 * A transaction the bank sent and one entered by hand that look like the same purchase: the same
 * account and amount, dated at most [MatchFinder.WINDOW_DAYS] apart.
 */
data class MatchPair(
    val bank: MatchSide,
    val entered: MatchSide,
) {
    val key: String get() = MatchFinder.key(bank.id, entered.id)

    val daysApart: Int get() = MatchFinder.daysBetween(bank.date, entered.date)

    val ids: Set<String> get() = setOf(bank.id, entered.id)
}

/**
 * Finds possible matches. A bank transaction is one that came in from bank sync or a file import
 * (it carries an imported id or imported payee); an entered one carries neither. Each transaction
 * sits in at most one pair, and a pair someone called not a match is never offered again.
 */
object MatchFinder {
    /** The same window Actual's bank sync uses to match an imported transaction to an existing one. */
    const val WINDOW_DAYS = 7

    /** A synced preference holding the pairs called not a match, so other phones skip them too. */
    const val NOT_A_MATCH_PREF = "duenorth.notMatched"

    private val json = Json
    private val keys = ListSerializer(String.serializer())

    fun key(
        bankId: String,
        enteredId: String,
    ) = "$bankId|$enteredId"

    fun daysBetween(
        a: Int,
        b: Int,
    ): Int {
        val first = toDate(a) ?: return Int.MAX_VALUE
        val second = toDate(b) ?: return Int.MAX_VALUE
        return abs(ChronoUnit.DAYS.between(first, second)).toInt()
    }

    /** Pairs in [accountId], or in every account when null, newest bank transaction first. */
    fun find(
        session: SqlSession,
        accountId: String? = null,
    ): List<MatchPair> {
        val dismissed = dismissed(session)
        val rows =
            session.query(
                """
                SELECT b.id AS b_id, e.id AS e_id
                FROM transactions b
                JOIN transactions e
                    ON e.acct = b.acct AND e.amount = b.amount AND e.id <> b.id
                WHERE ${candidate("b")} AND ${candidate("e")}
                    AND (b.financial_id IS NOT NULL OR b.imported_description IS NOT NULL)
                    AND e.financial_id IS NULL AND e.imported_description IS NULL
                    ${if (accountId != null) "AND b.acct = ?" else ""}
                ORDER BY b.date DESC, b.sort_order DESC, b.id DESC
                """.trimIndent(),
                listOfNotNull(accountId),
            )
        if (rows.isEmpty()) return emptyList()
        val sides = sides(session, rows.flatMap { listOf(it.str("b_id").orEmpty(), it.str("e_id").orEmpty()) }.toSet())
        val candidates =
            rows
                .mapNotNull { row ->
                    val bank = sides[row.str("b_id")] ?: return@mapNotNull null
                    val entered = sides[row.str("e_id")] ?: return@mapNotNull null
                    MatchPair(bank, entered).takeIf { it.daysApart <= WINDOW_DAYS && it.key !in dismissed }
                }.groupBy { it.bank.id }
        // Each bank transaction takes the closest entry still free: nearest date, then the same payee.
        val taken = mutableSetOf<String>()
        return candidates.values.mapNotNull { options ->
            options
                .filter { it.entered.id !in taken }
                .minWithOrNull(compareBy<MatchPair> { it.daysApart }.thenBy { if (samePayee(it)) 0 else 1 })
                ?.also { taken += it.entered.id }
        }
    }

    fun dismissed(session: SqlSession): Set<String> {
        val raw =
            session
                .query("SELECT value FROM preferences WHERE id = ?", listOf(NOT_A_MATCH_PREF))
                .firstOrNull()
                ?.str("value")
                ?: return emptySet()
        return runCatching { json.decodeFromString(keys, raw).toSet() }.getOrDefault(emptySet())
    }

    internal fun encode(keys: Collection<String>): String = json.encodeToString(this.keys, keys.sorted())

    private fun samePayee(pair: MatchPair) = pair.bank.payee.equals(pair.entered.payee, ignoreCase = true)

    /** A plain transaction that can be matched: alive, not a split or a part of one, not a transfer. */
    internal fun candidate(t: String) =
        """
        IFNULL($t.tombstone, 0) = 0 AND IFNULL($t.isParent, 0) = 0 AND IFNULL($t.isChild, 0) = 0
            AND $t.transferred_id IS NULL AND IFNULL($t.reconciled, 0) = 0
        """.trimIndent()

    internal fun sides(
        session: SqlSession,
        ids: Set<String>,
    ): Map<String, MatchSide> {
        if (ids.isEmpty()) return emptyMap()
        return session
            .query(
                """
                SELECT t.id AS id, t.date AS date, t.amount AS amount, t.acct AS acct, COALESCE(a.name, '') AS account_name,
                    t.category AS category, COALESCE(c.name, '') AS category_name, COALESCE(t.notes, '') AS notes,
                    COALESCE(p.name, '') AS payee, t.imported_description AS imported
                FROM transactions t
                JOIN accounts a ON a.id = t.acct AND IFNULL(a.tombstone, 0) = 0
                LEFT JOIN payees p ON p.id = t.description AND IFNULL(p.tombstone, 0) = 0
                LEFT JOIN categories c ON c.id = t.category AND IFNULL(c.tombstone, 0) = 0
                WHERE t.id IN (${ids.joinToString(",") { "?" }})
                """.trimIndent(),
                ids.toList(),
            ).associate { row ->
                val id = row.str("id").orEmpty()
                val categoryName = row.str("category_name").orEmpty()
                id to
                    MatchSide(
                        id = id,
                        payee =
                            row
                                .str("payee")
                                .orEmpty()
                                .ifBlank { row.str("imported").orEmpty() }
                                .ifBlank { ShellCopy.NO_PAYEE },
                        date = row.long("date").toInt(),
                        amountMinor = row.long("amount"),
                        accountId = row.str("acct").orEmpty(),
                        accountName = row.str("account_name").orEmpty(),
                        categoryId = row.str("category")?.takeIf { categoryName.isNotBlank() },
                        categoryName = categoryName,
                        note = row.str("notes").orEmpty().trim(),
                    )
            }
    }

    internal fun toDate(date: Int): LocalDate? = runCatching { LocalDate.of(date / 10000, (date / 100) % 100, date % 100) }.getOrNull()
}

/**
 * Settles a possible match. A match keeps the bank's transaction, so its imported id, date and
 * cleared state stay and the next sync sees it as already there, and moves the entry's payee,
 * category and note onto it; the entry is deleted. The note gains one line naming both, the only
 * trace that there were two.
 */
class MatchBook(
    private val session: SqlSession,
    private val today: LocalDate,
) {
    fun match(
        bankId: String,
        enteredId: String,
    ): WriteResult {
        val box = arrayOf<WriteResult>(WriteResult.Saved)
        session.transaction {
            val pair =
                MatchFinder.find(session).firstOrNull { it.bank.id == bankId && it.entered.id == enteredId }
                    ?: run {
                        box[0] = WriteResult.Rejected(MatchCopy.GONE)
                        return@transaction
                    }
            settle(pair.bank, pair.entered)
        }
        return box[0]
    }

    /**
     * Keeps [bank] with [entered]'s payee, category and note, and deletes [entered]. [movedFrom] names the account
     * the entry sat in when it was moved over first, so the note says so.
     */
    internal fun settle(
        bank: MatchSide,
        entered: MatchSide,
        movedFrom: String? = null,
    ) {
        val payeeId = payeeOf(entered.id) ?: payeeOf(bank.id)
        val note =
            listOf(entered.note, bank.note)
                .filter { it.isNotBlank() }
                .distinct()
                .plus(evidence(bank, entered, movedFrom))
                .joinToString("\n")
        SyncSchema.writeRow(
            session,
            "transactions",
            bank.id,
            "description" to payeeId,
            "category" to (entered.categoryId ?: bank.categoryId),
            "notes" to note,
            "cleared" to 1,
        )
        SyncSchema.writeRow(session, "transactions", entered.id, "tombstone" to 1)
    }

    /** Remembers the pair as not a match, in a synced preference, and drops pairs whose transactions are gone. */
    fun notAMatch(
        bankId: String,
        enteredId: String,
    ): WriteResult {
        session.transaction {
            val alive =
                MatchFinder
                    .dismissed(session)
                    .filter { key -> key.split('|').all { id -> isAlive(id) } }
                    .toSet()
            val keys = alive + MatchFinder.key(bankId, enteredId)
            SyncSchema.writeRow(session, "preferences", MatchFinder.NOT_A_MATCH_PREF, "value" to MatchFinder.encode(keys))
        }
        return WriteResult.Saved
    }

    /** "Matched 10/18: bank CORNER COFFEE #42 (10/15) + entered Corner Coffee (10/14)". */
    internal fun evidence(
        bank: MatchSide,
        entered: MatchSide,
        movedFrom: String? = null,
    ): String {
        val bankName = importedPayee(bank.id) ?: bank.payee
        val moved = movedFrom?.let { " from $it" }.orEmpty()
        return "Matched ${short(today)}$moved: bank $bankName (${short(bank.date)}) + entered ${entered.payee} (${short(entered.date)})"
    }

    private fun payeeOf(id: String): String? =
        session.query("SELECT description FROM transactions WHERE id = ?", listOf(id)).firstOrNull()?.str("description")

    private fun importedPayee(id: String): String? =
        session
            .query("SELECT imported_description FROM transactions WHERE id = ?", listOf(id))
            .firstOrNull()
            ?.str("imported_description")
            ?.ifBlank { null }

    private fun isAlive(id: String): Boolean =
        session
            .query("SELECT id FROM transactions WHERE id = ? AND IFNULL(tombstone, 0) = 0", listOf(id))
            .isNotEmpty()

    private fun short(date: LocalDate) = "${date.monthValue}/${date.dayOfMonth}"

    private fun short(date: Int) = "${(date / 100) % 100}/${date % 100}"
}
