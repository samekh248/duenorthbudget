package app.duenorth.budget.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.roundToLong

object SavedSplitCopy {
    const val TITLE = "saved splits"
    const val LOOKS_LIKE = "looks like your saved split"
    const val SPLIT = "split"
    const val USE = "use a saved split"
    const val SAVE = "save this split"
    const val SAVE_TITLE = "save split"
    const val NAME = "name"
    const val KEEP = "keep"
    const val AMOUNTS = "amounts"
    const val SHARES = "shares"
    const val AMOUNTS_HINT = "Each line keeps the amount it has now; the last line takes whatever is left."
    const val SHARES_HINT = "Each line keeps its share of the total, so the split fits any amount."
    const val REST = "the rest"
    const val SAME_PAYEE = "same payee"
    const val SAME_AMOUNT = "same amount"
    const val CLOSE_AMOUNT = "about the usual amount"
    const val SPLIT_AS = "split as"
    const val NONE = "No saved splits yet. Split a transaction, then tap “save this split” under its lines."
    const val MATCHES = "suggested for"
    const val ANY_PAYEE = "suggested by amount only"
    const val DELETE = "delete"
    const val NAME_FIRST = "name the split"
    const val DOESNT_FIT = "doesn't fit this amount"

    fun names(word: String): String = "names “$word”"
}

/**
 * One line of a saved split: its category and how much of the total it takes. A line takes a fixed amount
 * ([amountMinor], always positive; it takes the transaction's sign), a share ([shareBp], in hundredths of a percent),
 * or, with neither, whatever the other lines leave.
 */
@Serializable
data class SavedSplitLine(
    val categoryId: String? = null,
    val amountMinor: Long? = null,
    val shareBp: Int? = null,
    val payee: String? = null,
    val note: String? = null,
) {
    val rest: Boolean get() = amountMinor == null && shareBp == null
}

/**
 * A split kept to use again, held in a synced preference so every phone on the budget has it. [payees] and
 * [totalMinor] are what an incoming transaction is matched by: the payees it was saved from or used on, and the
 * signed total it was saved from.
 */
@Serializable
data class SavedSplit(
    val id: String,
    val name: String,
    val lines: List<SavedSplitLine>,
    val payees: List<String> = emptyList(),
    val totalMinor: Long? = null,
) {
    /** The split's parts for a transaction of [totalMinor], or null when it can't fit that amount. */
    fun partsFor(totalMinor: Long): List<SplitPart>? = SavedSplits.parts(this, totalMinor)
}

/** A saved split that fits an inbox transaction, and why it looks like one. */
data class SavedSplitGuess(
    val splitId: String,
    val name: String,
    val parts: List<SplitPart>,
    val reasons: List<String> = emptyList(),
    /** Each part's category name, in [parts]' order; blank for a part with none. */
    val categoryNames: List<String> = emptyList(),
)

object SavedSplits {
    /** The synced preference holding every saved split, as JSON. */
    const val PREF = "duenorth.savedSplits"

    /** A whole share, in hundredths of a percent. */
    const val WHOLE_BP = 10_000

    private val json = Json { ignoreUnknownKeys = true }
    private val list = ListSerializer(SavedSplit.serializer())

    fun decode(raw: String?): List<SavedSplit> =
        raw?.takeIf { it.isNotBlank() }?.let { runCatching { json.decodeFromString(list, it) }.getOrNull() }.orEmpty()

    fun encode(splits: List<SavedSplit>): String = json.encodeToString(list, splits)

    fun read(session: SqlSession): List<SavedSplit> =
        decode(session.query("SELECT value FROM preferences WHERE id = ?", listOf(PREF)).firstOrNull()?.str("value"))

    fun write(
        session: SqlSession,
        splits: List<SavedSplit>,
    ) {
        SyncSchema.writeRow(session, "preferences", PREF, "value" to encode(splits))
    }

    /**
     * [split]'s lines as amounts for [totalMinor]: fixed lines take their amount, share lines their share rounded to
     * the minor unit, and the rest line what is left. Without a rest line, rounding goes to the last share line. Null
     * when the lines can't add up to the total running its way, or any line comes to nothing.
     */
    fun parts(
        split: SavedSplit,
        totalMinor: Long,
    ): List<SplitPart>? {
        if (totalMinor == 0L || split.lines.size < 2) return null
        val sign = if (totalMinor < 0) -1L else 1L
        val whole = abs(totalMinor)
        val restIndex = split.lines.indexOfLast { it.rest }
        val amounts =
            split.lines
                .mapIndexed { index, line ->
                    when {
                        line.amountMinor != null -> line.amountMinor
                        line.shareBp != null -> (whole.toDouble() * line.shareBp / WHOLE_BP).roundToLong()
                        // Only the last rest line takes the rest; an earlier one has nothing to go on.
                        index == restIndex -> 0L
                        else -> return null
                    }
                }.toMutableList()
        val left = whole - amounts.sum()
        when {
            restIndex >= 0 -> amounts[restIndex] = left
            left != 0L -> {
                // Rounding only: a few minor units go on the last share line. Fixed lines that miss the total don't fit.
                val lastShare = split.lines.indexOfLast { it.shareBp != null }
                if (lastShare < 0 || abs(left) > split.lines.size) return null
                amounts[lastShare] += left
            }
        }
        if (amounts.any { it <= 0L }) return null
        return split.lines.mapIndexed { index, line ->
            SplitPart(line.categoryId, sign * amounts[index], line.payee?.ifBlank { null }, line.note?.ifBlank { null })
        }
    }

    /**
     * A saved split made from a split as it stands: [parts] adding up to [totalMinor]. With [shares], each line keeps its
     * share of the total; without, its amount. Either way the line at [restIndex] (the last, when null) takes the rest.
     */
    fun fromParts(
        id: String,
        name: String,
        parts: List<SplitPart>,
        totalMinor: Long,
        shares: Boolean,
        payee: String?,
        restIndex: Int? = null,
    ): SavedSplit {
        val rest = restIndex ?: parts.lastIndex
        val whole = abs(totalMinor).coerceAtLeast(1L)
        val lines =
            parts.mapIndexed { index, part ->
                val amount = abs(part.amountMinor)
                SavedSplitLine(
                    categoryId = part.categoryId,
                    amountMinor = amount.takeIf { !shares && index != rest },
                    shareBp = (amount * WHOLE_BP.toDouble() / whole).roundToLong().toInt().takeIf { shares && index != rest },
                    payee = part.payee,
                    note = part.note,
                )
            }
        return SavedSplit(
            id = id,
            name = name.trim(),
            lines = lines,
            payees = listOfNotNull(payee?.trim()?.ifBlank { null }?.takeIf { it != ShellCopy.NO_PAYEE }),
            totalMinor = totalMinor,
        )
    }

    /**
     * [splits] with [split] in: a split of the same name (any case) is replaced, keeping the payees it was matched by.
     */
    fun put(
        splits: List<SavedSplit>,
        split: SavedSplit,
    ): List<SavedSplit> {
        val old = splits.firstOrNull { it.id == split.id || it.name.equals(split.name, ignoreCase = true) }
        val merged =
            if (old == null) {
                split
            } else {
                split.copy(id = old.id, payees = (old.payees + split.payees).distinctBy { it.lowercase() })
            }
        val index = splits.indexOfFirst { it.id == merged.id }
        return if (index < 0) splits + merged else splits.toMutableList().also { it[index] = merged }
    }

    /** [splits] with [payee] added to the one with [splitId]'s payees, so the next one from it is matched. */
    fun learn(
        splits: List<SavedSplit>,
        splitId: String,
        payee: String?,
    ): List<SavedSplit> {
        val name = payee?.trim()?.ifBlank { null }?.takeIf { it != ShellCopy.NO_PAYEE } ?: return splits
        return splits.map { split ->
            if (split.id != splitId ||
                split.payees.any { it.equals(name, ignoreCase = true) }
            ) {
                split
            } else {
                split.copy(payees = split.payees + name)
            }
        }
    }

    fun summary(
        split: SavedSplit,
        categoryName: (String?) -> String,
        amount: (Long) -> String,
    ): String =
        split.lines.joinToString(" · ") { line ->
            val share =
                when {
                    line.amountMinor != null -> amount(line.amountMinor)
                    line.shareBp != null -> percent(line.shareBp)
                    else -> SavedSplitCopy.REST
                }
            "${categoryName(line.categoryId)} $share"
        }

    fun percent(bp: Int): String {
        val whole = bp / 100
        val frac = bp % 100
        return if (frac == 0) "$whole%" else "$whole.${frac.toString().padStart(2, '0').trimEnd('0')}%"
    }
}

/**
 * Guesses which saved split an inbox transaction is: one saved from or used on the same payee, or saved from the same
 * amount, that fits the transaction's amount. The guess is only offered; nothing changes until the person splits it.
 */
object SavedSplitMatcher {
    /** The score a guess needs, and how far it must lead the next split. */
    const val NEEDED = 2

    private const val SAME_PAYEE = 2
    private const val LIKE_PAYEE = 2
    private const val SAME_AMOUNT = 2
    private const val CLOSE_AMOUNT = 1

    /** How close an amount must be to the one the split was saved from to count as about the same: a tenth. */
    private const val CLOSE = 0.10

    /** Guesses for [rows], by transaction id; rows with no clear guess are left out. */
    fun guess(
        rows: List<InboxRow>,
        splits: List<SavedSplit>,
    ): Map<String, SavedSplitGuess> {
        if (splits.isEmpty()) return emptyMap()
        return rows.mapNotNull { row -> guess(row.payee, row.amountMinor, splits)?.let { row.id to it } }.toMap()
    }

    fun guess(
        payee: String,
        amountMinor: Long,
        splits: List<SavedSplit>,
    ): SavedSplitGuess? {
        val scored =
            splits
                .mapNotNull { split ->
                    // A split saved from money out isn't offered for money in, and the other way round.
                    if (split.totalMinor != null && (split.totalMinor < 0) != (amountMinor < 0)) return@mapNotNull null
                    val parts = split.partsFor(amountMinor) ?: return@mapNotNull null
                    val reasons = mutableListOf<String>()
                    var score = 0
                    val payeeScore = payeeScore(payee, split.payees)
                    if (payeeScore != null) {
                        score += payeeScore.first
                        reasons += payeeScore.second
                    }
                    val total = split.totalMinor
                    if (total != null) {
                        when {
                            total == amountMinor -> {
                                score += SAME_AMOUNT
                                reasons += SavedSplitCopy.SAME_AMOUNT
                            }
                            abs(total - amountMinor) <= abs(total) * CLOSE -> {
                                score += CLOSE_AMOUNT
                                reasons += SavedSplitCopy.CLOSE_AMOUNT
                            }
                        }
                    }
                    Triple(split, parts, score to reasons)
                }.sortedByDescending { it.third.first }
        val best = scored.firstOrNull() ?: return null
        val score = best.third.first
        if (score < NEEDED) return null
        // Two splits that look as likely as each other: offer neither, rather than guess.
        if (scored.getOrNull(1)?.third?.first == score) return null
        return SavedSplitGuess(best.first.id, best.first.name, best.second, best.third.second)
    }

    /** How much [payee] looks like one of [payees]: the same name, or one name inside the other (a bank's wording). */
    private fun payeeScore(
        payee: String,
        payees: List<String>,
    ): Pair<Int, String>? {
        val name = payee.trim()
        if (name.isBlank() || name == ShellCopy.NO_PAYEE) return null
        if (payees.any { it.equals(name, ignoreCase = true) }) return SAME_PAYEE to SavedSplitCopy.SAME_PAYEE
        val plain = squash(name)
        val like =
            payees.firstOrNull { saved ->
                val other = squash(saved)
                other.length >= MIN_LIKE && plain.length >= MIN_LIKE && (plain.contains(other) || other.contains(plain))
            } ?: return null
        return LIKE_PAYEE to SavedSplitCopy.names(like)
    }

    private const val MIN_LIKE = 4

    private fun squash(text: String) = text.lowercase().filter { it.isLetterOrDigit() }
}
