package app.duenorth.budget

import app.duenorth.budget.core.PayeeRow
import org.junit.Assert.assertEquals
import org.junit.Test

/** The merge page's "call it" choices: each spelling once. */
class PayeeMergeNamesTest {
    @Test
    fun sameSpellingIsOneChoiceUnderTheMostUsedWithCountsAdded() {
        val options =
            mergeNameOptions(
                listOf(
                    PayeeRow("a", "Amazon", false, 14),
                    PayeeRow("b", "AMAZON", false, 4),
                    PayeeRow("c", "AMAZON.COM", false, 3),
                    PayeeRow("d", " amazon  ", false, 1),
                ),
            )
        assertEquals(listOf("Amazon", "AMAZON.COM"), options.map { it.name })
        assertEquals(listOf(19, 3), options.map { it.transactionCount })
    }
}
