package app.duenorth.budget.core

import java.time.LocalDate

object TransferCopy {
    const val LOOKS_LIKE = "looks like a transfer"
    const val MAKE_TRANSFER = "make transfer"
    const val SAYS_TRANSFER = "says transfer"
    const val SAYS_PAYMENT = "says payment"
    const val SAME_AMOUNT_BEFORE = "same amount as before"
    const val FILED_AS = "filed as"

    /** "transfer to Savings" for money out of the row's account, "transfer from Savings" for money in. */
    fun guess(
        amountMinor: Long,
        account: String,
    ): String = "${if (amountMinor < 0) "transfer to" else "transfer from"} $account"

    fun names(word: String): String = "names “$word”"

    fun endsIn(digits: String): String = "ends in $digits"

    fun otherSide(
        account: String,
        days: Int,
    ): String = "other side in $account" + if (days == 0) "" else ", ${MatchCopy.apart(days)}"

    fun before(times: Int): String = if (times == 1) "went there once before" else "went there $times times before"
}

/**
 * The account an inbox transaction most likely moved money to or from, and why. [partnerId] is a plain transaction
 * already in that account for the opposite amount; when it is set, making the transfer links the two, otherwise it
 * adds the other side.
 */
data class TransferGuess(
    val accountId: String,
    val accountName: String,
    val partnerId: String? = null,
    val reasons: List<String> = emptyList(),
) {
    fun label(amountMinor: Long): String = TransferCopy.guess(amountMinor, accountName)
}

/**
 * Guesses which inbox transactions are transfers between the person's own accounts, from what a bank's text and the
 * budget's history give away: the opposite amount already in another account a few days off, the words a bank uses
 * for a transfer or a card payment, an account's name or last digits in the text, and where transactions worded the
 * same way went before. The guess is only offered; nothing changes until the person makes the transfer.
 *
 * Only on-budget accounts are guessed, since a transfer between two of them needs no category and so leaves the inbox.
 */
object TransferGuesser {
    /** How far apart the two sides of a transfer can be dated: banks post them a day or two apart, rarely more. */
    const val PARTNER_DAYS = 3

    /** How far back earlier transfers count as where this kind of transaction usually goes. */
    const val HISTORY_DAYS = 365L

    /** The score a guess needs, and how far it must lead the next account. */
    const val NEEDED = 3

    private const val PARTNER = 3
    private const val HISTORY = 3
    private const val DIGITS = 3
    private const val NAME = 2
    private const val SAME_AMOUNT = 1
    private const val CARD_PAYMENT = 1
    private const val KEYWORD = 1

    /** Words banks use for money moved between accounts. */
    private val TRANSFER_WORDS =
        setOf("transfer", "transfers", "xfer", "xfr", "trnsfr", "trsfr", "tfr", "trf", "xfers", "transferred", "sweep")

    /** Words banks use for paying a card or a loan from checking. */
    private val PAYMENT_WORDS = setOf("payment", "pmt", "pymt", "autopay", "epay", "epayment", "billpay")

    /** Words that name the kind of payment, not the account it went to. */
    private val NOISE =
        setOf(
            "the",
            "and",
            "for",
            "from",
            "to",
            "my",
            "acct",
            "account",
            "online",
            "mobile",
            "web",
            "internet",
            "bank",
            "banking",
            "ach",
            "ref",
            "conf",
            "confirmation",
            "id",
            "ppd",
            "web",
            "des",
            "indn",
            "co",
            "inc",
            "llc",
            "debit",
            "deposit",
            "withdrawal",
            "thank",
            "you",
            "via",
            "app",
            "recurring",
            "scheduled",
            "auto",
        ) + TRANSFER_WORDS + PAYMENT_WORDS

    /** Short forms banks print for an account's kind, so "SAV" names Savings and "CHK" names Checking. */
    private val SHORT_FORMS =
        mapOf(
            "savings" to setOf("sav", "savs", "svg", "svgs", "sv"),
            "saving" to setOf("sav", "svg", "sv"),
            "checking" to setOf("chk", "chkg", "chking", "ck", "chq", "dda"),
            "credit" to setOf("crd", "cr", "cc"),
            "card" to setOf("crd", "cc"),
            "visa" to setOf("vsa"),
            "mastercard" to setOf("mc", "mstrcard"),
            "american" to setOf("amex"),
            "express" to setOf("amex"),
            "money" to setOf("mm", "mmkt", "mma"),
            "market" to setOf("mm", "mmkt", "mma"),
            "brokerage" to setOf("brk", "brkg"),
            "investment" to setOf("inv", "invest"),
            "mortgage" to setOf("mtg", "mtge"),
            "loan" to setOf("ln"),
        )

    /** Guesses for [rows], by transaction id; rows with no clear guess are left out. */
    fun guess(
        session: SqlSession,
        rows: List<InboxRow>,
        today: LocalDate,
    ): Map<String, TransferGuess> {
        if (rows.isEmpty()) return emptyMap()
        val accounts = accounts(session)
        if (accounts.size < 2) return emptyMap()
        val imported = imported(session, rows.map { it.id })
        val history = history(session, today)
        val partners = partners(session, rows, accounts.keys)
        val taken = mutableSetOf<String>()
        val out = mutableMapOf<String, TransferGuess>()
        for (row in rows) {
            val source = accounts[row.accountId] ?: continue
            val text = listOf(row.payee, imported[row.id].orEmpty()).joinToString(" ")
            val words = words(text)
            val keyword = words.any { it in TRANSFER_WORDS }
            val payment = words.any { it in PAYMENT_WORDS }
            val shape = shape(imported[row.id] ?: row.payee)
            val scored =
                accounts.values
                    .filter { it.id != source.id }
                    .map { account ->
                        var score = 0
                        val reasons = mutableListOf<String>()
                        val partner =
                            partners[row.id]
                                .orEmpty()
                                .filter { it.accountId == account.id && it.id !in taken }
                                .minByOrNull { MatchFinder.daysBetween(it.date, row.date) }
                        if (partner != null) {
                            score += PARTNER
                            reasons += TransferCopy.otherSide(account.name, MatchFinder.daysBetween(partner.date, row.date))
                        }
                        val digits = account.digits.firstOrNull { it in words }
                        if (digits != null) {
                            score += DIGITS
                            reasons += TransferCopy.endsIn(digits)
                        }
                        val named = named(account, source, words)
                        if (named != null && digits == null) {
                            score += NAME
                            reasons += TransferCopy.names(named)
                        }
                        val past = history.filter { it.fromAccountId == source.id && it.toAccountId == account.id }
                        val alike = past.count { shape.isNotEmpty() && similar(it.shape, shape) }
                        if (alike > 0) {
                            score += HISTORY
                            reasons += TransferCopy.before(alike)
                        } else if (past.any { it.amount == row.amountMinor }) {
                            score += SAME_AMOUNT
                            reasons += TransferCopy.SAME_AMOUNT_BEFORE
                        }
                        if (payment && account.credit && row.amountMinor < 0) score += CARD_PAYMENT
                        Scored(account, score, reasons, partner?.id)
                    }.sortedByDescending { it.score }
            val best = scored.firstOrNull() ?: continue
            val runnerUp = scored.getOrNull(1)?.score ?: 0
            val hint = keyword || payment
            val total = best.score + if (hint) KEYWORD else 0
            // A name or digits alone could be a store's name; they count once the text also says it's a transfer.
            val grounded = best.partnerId != null || best.reasons.any { it.startsWith("went there") } || hint
            if (total < NEEDED || best.score <= runnerUp || !grounded) continue
            val reasons =
                listOfNotNull(
                    TransferCopy.SAYS_TRANSFER.takeIf { keyword },
                    TransferCopy.SAYS_PAYMENT.takeIf { payment && !keyword },
                ) + best.reasons
            best.partnerId?.let { taken += it }
            out[row.id] = TransferGuess(best.account.id, best.account.name, best.partnerId, reasons)
        }
        // A row another guess links as its other side is settled by that guess, not guessed on its own.
        return out.filterKeys { it !in taken }
    }

    /** The account name word the text uses, in full or in a bank's short form, that the source account's name lacks. */
    private fun named(
        account: Account,
        source: Account,
        words: List<String>,
    ): String? {
        val own = source.words.flatMap { forms(it) }.toSet()
        return account.words.firstOrNull { word ->
            val forms = forms(word) - own
            word !in own &&
                words.any { it in forms || (it.length >= 4 && word.length >= 4 && (word.startsWith(it) || it.startsWith(word))) }
        }
    }

    private fun forms(word: String): Set<String> = setOf(word) + SHORT_FORMS[word].orEmpty()

    /** Lowercase words and numbers, splitting "XXXXXX4821" and "TO-SAV" alike. */
    internal fun words(text: String): List<String> =
        text
            .lowercase()
            .split(Regex("[^a-z0-9]+"))
            .flatMap { token -> Regex("[a-z]+|[0-9]+").findAll(token).map { it.value }.toList() }
            .filter { it.isNotBlank() }

    /** The words of a bank's text without numbers or noise, so two transfers a month apart read the same. */
    internal fun shape(text: String): Set<String> =
        words(text).filter { word -> word.none { it.isDigit() } && word.length >= 2 && word !in NOISE }.toSet()

    private fun similar(
        a: Set<String>,
        b: Set<String>,
    ): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        val shared = a.intersect(b).size
        return shared * 2 >= maxOf(a.size, b.size) && shared >= 1
    }

    private data class Account(
        val id: String,
        val name: String,
        val credit: Boolean,
    ) {
        val words: List<String> = words(name).filter { w -> w.none { it.isDigit() } && w.length >= 2 && w !in NOISE }

        /** Runs of three to four digits in the name, as banks print an account's last digits. */
        val digits: List<String> = words(name).filter { w -> w.all { it.isDigit() } && w.length in 3..4 }
    }

    private data class Scored(
        val account: Account,
        val score: Int,
        val reasons: List<String>,
        val partnerId: String?,
    )

    private data class Past(
        val fromAccountId: String,
        val toAccountId: String,
        val amount: Long,
        val shape: Set<String>,
    )

    private data class Partner(
        val id: String,
        val accountId: String,
        val date: Int,
        val amount: Long,
    )

    private fun accounts(session: SqlSession): Map<String, Account> =
        session
            .query(
                """
                SELECT id, name, type FROM accounts
                WHERE IFNULL(tombstone, 0) = 0 AND IFNULL(closed, 0) = 0 AND IFNULL(offbudget, 0) = 0
                """.trimIndent(),
            ).associate { row ->
                val id = row.str("id").orEmpty()
                id to Account(id, row.str("name").orEmpty(), row.str("type").orEmpty().lowercase() == "credit")
            }

    private fun imported(
        session: SqlSession,
        ids: List<String>,
    ): Map<String, String> =
        ids
            .chunked(CHUNK)
            .flatMap { chunk ->
                session
                    .query(
                        "SELECT id, imported_description FROM transactions WHERE id IN (${chunk.joinToString(",") { "?" }})",
                        chunk,
                    ).mapNotNull { row -> row.str("imported_description")?.ifBlank { null }?.let { row.str("id").orEmpty() to it } }
            }.toMap()

    /** Transfers of the last [HISTORY_DAYS] out of or into each account, with the bank's words for them. */
    private fun history(
        session: SqlSession,
        today: LocalDate,
    ): List<Past> =
        session
            .query(
                """
                SELECT t.acct AS from_acct, o.acct AS to_acct, t.amount AS amount,
                    COALESCE(t.imported_description, '') AS imported
                FROM transactions t
                JOIN transactions o ON o.id = t.transferred_id AND IFNULL(o.tombstone, 0) = 0
                WHERE IFNULL(t.tombstone, 0) = 0 AND IFNULL(t.isChild, 0) = 0 AND t.date >= ?
                """.trimIndent(),
                listOf(today.minusDays(HISTORY_DAYS).toActualDate()),
            ).map { row ->
                Past(
                    fromAccountId = row.str("from_acct").orEmpty(),
                    toAccountId = row.str("to_acct").orEmpty(),
                    amount = row.long("amount"),
                    shape = shape(row.str("imported").orEmpty()),
                )
            }

    /** For each row, plain transactions in another on-budget account for the opposite amount within [PARTNER_DAYS]. */
    private fun partners(
        session: SqlSession,
        rows: List<InboxRow>,
        onBudget: Set<String>,
    ): Map<String, List<Partner>> {
        val earliest = rows.mapNotNull { MatchFinder.toDate(it.date) }.minOrNull() ?: return emptyMap()
        val candidates =
            session
                .query(
                    """
                    SELECT t.id AS id, t.acct AS acct, t.amount AS amount, t.date AS date
                    FROM transactions t
                    WHERE ${MatchFinder.candidate("t")} AND t.date >= ?
                    """.trimIndent(),
                    listOf(earliest.minusDays(PARTNER_DAYS.toLong()).toActualDate()),
                ).map { Partner(it.str("id").orEmpty(), it.str("acct").orEmpty(), it.long("date").toInt(), it.long("amount")) }
                .filter { it.accountId in onBudget }
        val ids = rows.map { it.id }.toSet()
        return rows.associate { row ->
            row.id to
                candidates
                    .filter { partner ->
                        partner.id != row.id && partner.amount == -row.amountMinor && partner.accountId != row.accountId &&
                            MatchFinder.daysBetween(partner.date, row.date) <= PARTNER_DAYS &&
                            // Two inbox rows can each be the other's side; the one with money out is guessed, not both.
                            (partner.id !in ids || row.amountMinor < 0)
                    }
        }
    }

    private const val CHUNK = 500
}

/** Makes an inbox transaction the transfer it was guessed to be. */
class TransferBook(
    private val session: SqlSession,
    private val today: LocalDate,
    private val ids: () -> String = {
        java.util.UUID
            .randomUUID()
            .toString()
    },
) {
    /**
     * Links [transactionId] with [partnerId] when one is given, as the assistant's "link as transfer" does; otherwise
     * makes it a transfer to or from [accountId], which adds the other side there for the opposite amount.
     */
    fun make(
        transactionId: String,
        accountId: String,
        partnerId: String?,
    ): WriteResult {
        if (partnerId != null) {
            val amount =
                session
                    .query("SELECT amount FROM transactions WHERE id = ?", listOf(transactionId))
                    .firstOrNull()
                    ?.long("amount") ?: return WriteResult.Rejected(AssistantCopy.GONE)
            val book = AssistantBook(session, today, ids)
            return if (amount < 0) book.linkTransfer(transactionId, partnerId) else book.linkTransfer(partnerId, transactionId)
        }
        val row =
            session
                .query(
                    """
                    SELECT t.acct AS acct, t.date AS date, t.amount AS amount, t.notes AS notes
                    FROM transactions t WHERE t.id = ? AND ${MatchFinder.candidate("t")}
                    """.trimIndent(),
                    listOf(transactionId),
                ).firstOrNull() ?: return WriteResult.Rejected(AssistantCopy.GONE)
        return RegisterBook(session, ids).save(
            TransactionDraft(
                id = transactionId,
                accountId = row.str("acct").orEmpty(),
                date = row.long("date").toInt(),
                payee = "",
                amountMinor = row.long("amount"),
                categoryId = null,
                note = row.str("notes"),
                transferAccountId = accountId,
            ),
        )
    }
}

private fun LocalDate.toActualDate(): Int = year * 10000 + monthValue * 100 + dayOfMonth
