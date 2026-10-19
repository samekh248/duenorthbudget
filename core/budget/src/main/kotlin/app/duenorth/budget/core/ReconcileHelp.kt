package app.duenorth.budget.core

/** One way the reconcile header helps find a difference. Clearing a row adds its amount to the cleared total. */
sealed interface ReconcileHint {
    /** An uncleared row on or before the statement date that is exactly the difference. */
    data class Exact(
        val row: ReconcileRow,
    ) : ReconcileHint

    /** Two uncleared rows on or before the statement date that add up to the difference. */
    data class Pair(
        val first: ReconcileRow,
        val second: ReconcileRow,
    ) : ReconcileHint

    /** An uncleared row dated after the statement that is exactly the difference. */
    data class Late(
        val row: ReconcileRow,
    ) : ReconcileHint

    /** A cleared row whose amount is the difference the other way: it may not be on this statement. */
    data class Uncleared(
        val row: ReconcileRow,
    ) : ReconcileHint

    /** The difference divides by 9 in minor units, the mark of two swapped digits. */
    data object Swapped : ReconcileHint

    /** Something on the statement may never have been entered. */
    data object Missing : ReconcileHint
}

object ReconcileHelp {
    /** How many uncleared rows the pair search looks through, newest first. */
    private const val PAIR_SEARCH = 80

    /**
     * Hints for [page]'s difference: just the exact match when there is one, otherwise the places worth a look,
     * ending with the chance that something was never entered. Nothing when it balances.
     */
    fun hints(page: ReconcilePage): List<ReconcileHint> {
        val difference = page.differenceMinor
        if (difference == 0L) return emptyList()
        val open = page.rows.filter { !it.reconciled }
        val uncleared = open.filter { !it.cleared && it.date <= page.statementDate }
        uncleared.firstOrNull { it.amountMinor == difference }?.let { return listOf(ReconcileHint.Exact(it)) }
        return buildList {
            pair(uncleared.take(PAIR_SEARCH), difference)?.let { add(it) }
            open
                .firstOrNull { !it.cleared && it.date > page.statementDate && it.amountMinor == difference }
                ?.let { add(ReconcileHint.Late(it)) }
            open
                .firstOrNull { it.cleared && it.amountMinor == -difference }
                ?.let { add(ReconcileHint.Uncleared(it)) }
            if (difference % 9 == 0L) add(ReconcileHint.Swapped)
            add(ReconcileHint.Missing)
        }
    }

    /** Ids a hint points at, so the register can outline those rows. */
    fun rowsOf(hints: List<ReconcileHint>): Set<String> =
        hints
            .flatMap { hint ->
                when (hint) {
                    is ReconcileHint.Exact -> listOf(hint.row.id)
                    is ReconcileHint.Pair -> listOf(hint.first.id, hint.second.id)
                    is ReconcileHint.Late -> listOf(hint.row.id)
                    is ReconcileHint.Uncleared -> listOf(hint.row.id)
                    ReconcileHint.Swapped, ReconcileHint.Missing -> emptyList()
                }
            }.toSet()

    private fun pair(
        rows: List<ReconcileRow>,
        difference: Long,
    ): ReconcileHint.Pair? {
        val seen = HashMap<Long, ReconcileRow>()
        for (row in rows) {
            seen[difference - row.amountMinor]?.let { return ReconcileHint.Pair(it, row) }
            seen.putIfAbsent(row.amountMinor, row)
        }
        return null
    }
}
