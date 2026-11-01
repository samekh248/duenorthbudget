package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconcileHelpTest {
    @Test
    fun balancedNeedsNoHelp() {
        assertEquals(emptyList<ReconcileHint>(), ReconcileHelp.hints(page(difference = 0, rows = listOf(row("a", -500)))))
    }

    @Test
    fun anExactUnclearedRowIsTheOnlyHint() {
        val shell = row("shell", -4_217)
        val hints = ReconcileHelp.hints(page(difference = -4_217, rows = listOf(row("x", -100), shell)))
        assertEquals(listOf(ReconcileHint.Exact(shell)), hints)
        assertEquals(setOf("shell"), ReconcileHelp.rowsOf(hints))
    }

    @Test
    fun withoutAnExactRowThePlacesToLookFollowInOrder() {
        val spotify = row("spotify", -1_099)
        val parking = row("parking", -450)
        val netflix = row("netflix", -1_549, date = 20261002)
        val cleared = row("refund", 1_549, cleared = true)
        val hints =
            ReconcileHelp.hints(page(difference = -1_549, rows = listOf(netflix, spotify, row("big", -9_999), parking, cleared)))
        assertEquals(
            listOf(
                ReconcileHint.Pair(spotify, parking),
                ReconcileHint.Uncleared(cleared),
                ReconcileHint.Late(netflix),
                ReconcileHint.Missing,
            ),
            hints,
        )
    }

    @Test
    fun aDifferenceThatDividesByNineSuggestsSwappedDigits() {
        val hints = ReconcileHelp.hints(page(difference = 900, rows = listOf(row("a", 5))))
        assertEquals(listOf(ReconcileHint.Swapped, ReconcileHint.Missing), hints)
    }

    @Test
    fun reconciledRowsAreNeverSuggested() {
        val hints = ReconcileHelp.hints(page(difference = -500, rows = listOf(row("old", -500, cleared = true, reconciled = true))))
        assertTrue(hints.none { it is ReconcileHint.Exact })
        assertEquals(listOf(ReconcileHint.Missing), hints)
    }

    @Test
    fun aClearedRowEnteredTheWrongWayRoundIsFlippedToBalance() {
        val refund = row("amazon", 2_883, cleared = true)
        val page = page(difference = -5_766, rows = listOf(row("x", -100), refund))
        val hints = ReconcileHelp.hints(page)
        assertEquals(ReconcileHint.Flip(refund), hints.first())
        assertEquals(0L, ReconcileHelp.after(page, hints.first()))
    }

    @Test
    fun anUnclearedRowTheWrongWayRoundIsFlippedAndCleared() {
        val refund = row("amazon", 2_883)
        val page = page(difference = -2_883, rows = listOf(refund))
        val flip = ReconcileHelp.hints(page).filterIsInstance<ReconcileHint.Flip>().single()
        assertEquals(0L, ReconcileHelp.after(page, flip))
    }

    @Test
    fun transfersAreNeverFlipped() {
        val transfer = row("savings", 2_883, cleared = true).copy(transfer = true)
        assertTrue(ReconcileHelp.hints(page(difference = -5_766, rows = listOf(transfer))).none { it is ReconcileHint.Flip })
    }

    @Test
    fun theSamePayeeAndAmountDaysApartIsAPossibleCopy() {
        val kept = row("safeway", -8_634, date = 20260926, cleared = true).copy(payee = "Safeway")
        val copy = row("safeway2", -8_634, date = 20260924).copy(payee = "Safeway")
        val far = row("safeway3", -8_634, date = 20260901).copy(payee = "Safeway")
        val hints = ReconcileHelp.hints(page(difference = -1, rows = listOf(kept, copy, far)))
        val duplicate = hints.filterIsInstance<ReconcileHint.Duplicate>().single()
        assertEquals(kept, duplicate.first)
        assertEquals(copy, duplicate.second)
    }

    @Test
    fun twoClearedCopiesOfferToUnclearOne() {
        val a = row("a", -1_000, cleared = true)
        val b = row("b", -1_000, cleared = true).copy(payee = "a")
        val page = page(difference = 1_000, rows = listOf(a, b))
        val duplicate = ReconcileHelp.hints(page).filterIsInstance<ReconcileHint.Duplicate>().single()
        assertEquals(0L, ReconcileHelp.after(page, duplicate))
    }

    @Test
    fun oldUnclearedRowsAreCalledOutOldestFirst() {
        val check = row("check", -12_000, date = 20260714)
        val older = row("older", -500, date = 20260601)
        val recent = row("recent", -700, date = 20260920)
        val page = page(difference = -1, rows = listOf(recent, check, older))
        val stale = ReconcileHelp.hints(page).filterIsInstance<ReconcileHint.Stale>()
        assertEquals(listOf(ReconcileHint.Stale(older, 121), ReconcileHint.Stale(check, 78)), stale)
        assertEquals(11_999L, ReconcileHelp.after(page, stale[1]))
    }

    @Test
    fun leadsThatWouldBalanceComeFirst() {
        val check = row("check", -12_000, date = 20260714)
        val late = row("late", -5_766, date = 20261002)
        val hints = ReconcileHelp.hints(page(difference = -5_766, rows = listOf(late, check)))
        assertEquals(listOf(ReconcileHint.Late(late), ReconcileHint.Stale(check, 78), ReconcileHint.Missing), hints)
    }

    private fun row(
        id: String,
        amount: Long,
        date: Int = 20260929,
        cleared: Boolean = false,
        reconciled: Boolean = false,
    ) = ReconcileRow(id, date, id, amount, cleared, reconciled)

    private fun page(
        difference: Long,
        rows: List<ReconcileRow>,
    ) = ReconcilePage(
        account = RegisterAccount("checking", "Checking", offBudget = false, type = "checking", closed = false),
        currency = Currencies.byCode("USD")!!,
        statementMinor = 0,
        statementDate = 20260930,
        clearedTotalMinor = -difference,
        differenceMinor = difference,
        rows = rows,
        active = true,
    )
}
