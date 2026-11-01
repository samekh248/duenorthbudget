package app.duenorth.budget.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** One lead the reconcile screen offers for a difference. Clearing a row adds its amount to the cleared total. */
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

    /**
     * A row entered the wrong way round, money in for money out. Turning a cleared row around moves the cleared
     * total by twice its amount; turning an uncleared one around and clearing it moves it by its amount once.
     */
    data class Flip(
        val row: ReconcileRow,
    ) : ReconcileHint

    /** Two open rows with the same payee and amount a few days apart: one may be a copy. */
    data class Duplicate(
        val first: ReconcileRow,
        val second: ReconcileRow,
    ) : ReconcileHint

    /** An uncleared row from well before the statement, [days] old by the statement date. */
    data class Stale(
        val row: ReconcileRow,
        val days: Int,
    ) : ReconcileHint

    /** The difference divides by 9 in minor units, the mark of two swapped digits. */
    data object Swapped : ReconcileHint

    /** Something on the statement may never have been entered. */
    data object Missing : ReconcileHint
}

object ReconcileHelp {
    /** How many uncleared rows the pair search looks through, newest first. */
    private const val PAIR_SEARCH = 80

    /** Same payee and amount within this many days reads as a possible copy. */
    private const val DUPLICATE_DAYS = 4

    /** An uncleared row this many days before the statement is worth a look. */
    const val STALE_DAYS = 45

    /** At most this many duplicate or stale leads, so the list stays short. */
    private const val MAX_EACH = 2

    /**
     * Leads for [page]'s difference, the ones that would balance it first. An exact uncleared match stands
     * alone with the possible copies and old rows; otherwise every place worth a look follows, ending with the
     * chance of swapped digits and of something never entered. Nothing when it balances.
     */
    fun hints(page: ReconcilePage): List<ReconcileHint> {
        val difference = page.differenceMinor
        if (difference == 0L) return emptyList()
        val open = page.rows.filter { !it.reconciled }
        val uncleared = open.filter { !it.cleared && it.date <= page.statementDate }
        val problems = duplicates(open) + stale(page, uncleared)
        uncleared.firstOrNull { it.amountMinor == difference }?.let { return listOf(ReconcileHint.Exact(it)) + problems }
        val leads =
            buildList {
                flip(page, open)?.let { add(it) }
                pair(uncleared.take(PAIR_SEARCH), difference)?.let { add(it) }
                open
                    .firstOrNull { it.cleared && it.amountMinor == -difference }
                    ?.let { add(ReconcileHint.Uncleared(it)) }
                open
                    .firstOrNull { !it.cleared && it.date > page.statementDate && it.amountMinor == difference }
                    ?.let { add(ReconcileHint.Late(it)) }
                addAll(problems)
            }
        // A lead that would balance it goes ahead of one that would only move it.
        return leads.sortedBy { if (after(page, it) == 0L) 0 else 1 } +
            listOfNotNull(ReconcileHint.Swapped.takeIf { difference % 9 == 0L }, ReconcileHint.Missing)
    }

    /** The difference left once [hint]'s fix is made, or null when the lead has no one-tap fix. */
    fun after(
        page: ReconcilePage,
        hint: ReconcileHint,
    ): Long? {
        val d = page.differenceMinor
        return when (hint) {
            is ReconcileHint.Exact -> d - hint.row.amountMinor
            is ReconcileHint.Pair -> d - hint.first.amountMinor - hint.second.amountMinor
            is ReconcileHint.Late -> d - hint.row.amountMinor
            is ReconcileHint.Uncleared -> d + hint.row.amountMinor
            // Turned around and cleared, the row counts as -amount where it counted as +amount (or nothing).
            is ReconcileHint.Flip -> d + hint.row.amountMinor * (if (hint.row.cleared) 2 else 1)
            // Both copies cleared: unclearing one takes it back out of the cleared total.
            is ReconcileHint.Duplicate -> if (hint.first.cleared && hint.second.cleared) d + hint.second.amountMinor else null
            is ReconcileHint.Stale -> d - hint.row.amountMinor
            ReconcileHint.Swapped, ReconcileHint.Missing -> null
        }
    }

    /** Ids a hint points at, so the register can outline those rows. */
    fun rowsOf(hints: List<ReconcileHint>): Set<String> = hints.flatMap(::rowsOf).toSet()

    fun rowsOf(hint: ReconcileHint): List<String> =
        when (hint) {
            is ReconcileHint.Exact -> listOf(hint.row.id)
            is ReconcileHint.Pair -> listOf(hint.first.id, hint.second.id)
            is ReconcileHint.Late -> listOf(hint.row.id)
            is ReconcileHint.Uncleared -> listOf(hint.row.id)
            is ReconcileHint.Flip -> listOf(hint.row.id)
            is ReconcileHint.Duplicate -> listOf(hint.first.id, hint.second.id)
            is ReconcileHint.Stale -> listOf(hint.row.id)
            ReconcileHint.Swapped, ReconcileHint.Missing -> emptyList()
        }

    /** Days from [from] to [to], both yyyymmdd. */
    fun daysBetween(
        from: Int,
        to: Int,
    ): Int = ChronoUnit.DAYS.between(local(from), local(to)).toInt()

    private fun local(date: Int) = LocalDate.of(date / 10_000, date / 100 % 100, date % 100)

    /** A row, not a transfer, whose sign turned around would balance it. */
    private fun flip(
        page: ReconcilePage,
        open: List<ReconcileRow>,
    ): ReconcileHint.Flip? {
        val d = page.differenceMinor
        return open
            .firstOrNull { row ->
                !row.transfer &&
                    row.date <= page.statementDate &&
                    if (row.cleared) d % 2 == 0L && row.amountMinor == -d / 2 else row.amountMinor == -d
            }?.let { ReconcileHint.Flip(it) }
    }

    private fun duplicates(open: List<ReconcileRow>): List<ReconcileHint.Duplicate> =
        open
            .groupBy { it.payee.lowercase() to it.amountMinor }
            .values
            .filter { it.size > 1 }
            .mapNotNull { same ->
                // Register order is newest first, so the later copy leads.
                same.zipWithNext().firstOrNull { (a, b) -> daysBetween(b.date, a.date) <= DUPLICATE_DAYS }
            }.map { (newer, older) ->
                // The uncleared copy is the likelier stray, so it goes second, where the fix points.
                if (newer.cleared && !older.cleared) ReconcileHint.Duplicate(newer, older) else ReconcileHint.Duplicate(older, newer)
            }.take(MAX_EACH)

    private fun stale(
        page: ReconcilePage,
        uncleared: List<ReconcileRow>,
    ): List<ReconcileHint.Stale> =
        uncleared
            .map { it to daysBetween(it.date, page.statementDate) }
            .filter { (_, days) -> days >= STALE_DAYS }
            .sortedByDescending { (_, days) -> days }
            .take(MAX_EACH)
            .map { (row, days) -> ReconcileHint.Stale(row, days) }

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
