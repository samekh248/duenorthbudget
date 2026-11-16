package app.duenorth.budget.core

/**
 * One payee kept under several names: [keep] is the name used most, and [others] look like it. The payees page leads
 * with these, and the assistant shows each other name as a look-alike finding.
 */
data class PayeeLookalike(
    val keep: PayeeRow,
    val others: List<PayeeRow>,
) {
    /** Every name in the group, the one kept first. */
    val names: List<PayeeRow> get() = listOf(keep) + others

    /** Transactions across every name in the group. */
    val transactionCount: Int get() = names.sumOf { it.transactionCount }

    /** What a "not the same" remembers, one key per other name: the assistant's own keys, so both places forget it. */
    val keys: List<String> get() = others.map { key(it.id, keep.id) }

    /** Stable while the group's names stay: the list key and test tag. */
    val id: String get() = keep.id

    companion object {
        fun key(
            otherId: String,
            keepId: String,
        ): String = AssistantBook.key(TidyChecks.LOOKALIKE_KEY, otherId, keepId)
    }
}

/**
 * Finds payees that are probably one payee: names that match once case, punctuation, store numbers and endings like
 * ".com" or "Inc" go ("COSTCO #1123" and "Costco"), and names that only add words to another ("Shell" and "SHELL OIL
 * 5123").
 */
object PayeeLookalikes {
    /** Names shorter than this, once tidied, are too short to call alike. */
    const val MIN_LENGTH = 4

    /** A name only counts as the start of a longer one from this many letters, so "Taco" does not swallow "Taco Bell". */
    const val PREFIX_MIN_LENGTH = 5

    /** Endings a bank or a store adds that do not change who was paid. */
    private val ENDINGS = setOf("com", "inc", "llc", "ltd", "co", "corp", "net", "org")

    fun find(
        rows: List<PayeeRow>,
        dismissed: Set<String> = emptySet(),
    ): List<PayeeLookalike> {
        val named =
            rows
                .filter { !it.transfer && it.name.isNotBlank() }
                .map { it to words(it.name) }
                .filter { (_, words) -> words.joinToString("").length >= MIN_LENGTH }
        val parent = IntArray(named.size) { it }

        fun root(i: Int): Int {
            var at = i
            while (parent[at] != at) {
                parent[at] = parent[parent[at]]
                at = parent[at]
            }
            return at
        }

        fun join(
            a: Int,
            b: Int,
        ) {
            parent[root(a)] = root(b)
        }
        // The same name once tidied.
        named.indices
            .groupBy { named[it].second.joinToString("") }
            .values
            .forEach { same -> same.drop(1).forEach { join(it, same.first()) } }
        // One name is the first words of another.
        for (i in named.indices) {
            val short = named[i].second
            if (short.joinToString("").length < PREFIX_MIN_LENGTH) continue
            for (j in named.indices) {
                val long = named[j].second
                if (i != j && long.size > short.size && long.subList(0, short.size) == short) join(i, j)
            }
        }
        return named.indices
            .groupBy(::root)
            .values
            .filter { it.size > 1 }
            .mapNotNull { members ->
                val group = members.map { named[it].first }
                // The name used most stays; the others merge into it.
                val keep = group.sortedWith(compareByDescending<PayeeRow> { it.transactionCount }.thenBy { it.name }).first()
                val others =
                    group
                        .filter { it != keep && PayeeLookalike.key(it.id, keep.id) !in dismissed }
                        .sortedWith(compareByDescending<PayeeRow> { it.transactionCount }.thenBy { it.name.lowercase() })
                if (others.isEmpty()) null else PayeeLookalike(keep, others)
            }.sortedWith(compareByDescending<PayeeLookalike> { it.transactionCount }.thenBy { it.keep.name.lowercase() })
    }

    /** A name as one string with case, store numbers, punctuation and trailing digits taken away: "COSTCO #1123" is "costco". */
    fun tidy(name: String): String = words(name).joinToString("")

    /** A name's words, lowercase, without store numbers, punctuation, endings like ".com", or trailing digits. */
    internal fun words(name: String): List<String> {
        val words =
            name
                .lowercase()
                .replace(Regex("#\\s*\\d+"), " ")
                .split(Regex("[^\\p{L}\\p{N}]+"))
                .filter { it.isNotEmpty() }
                .toMutableList()
        while (words.size > 1 && (words.last() in ENDINGS || words.last().all { it.isDigit() })) words.removeAt(words.lastIndex)
        if (words.isNotEmpty()) words[words.lastIndex] = words.last().trimEnd { it.isDigit() }
        return words.filter { it.isNotEmpty() }
    }
}
