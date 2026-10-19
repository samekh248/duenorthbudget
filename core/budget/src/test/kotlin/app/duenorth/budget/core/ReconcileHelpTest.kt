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
                ReconcileHint.Late(netflix),
                ReconcileHint.Uncleared(cleared),
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
