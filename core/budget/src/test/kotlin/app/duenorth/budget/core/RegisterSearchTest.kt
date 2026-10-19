package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegisterSearchTest {
    private val rows =
        listOf(
            row(
                "cafe",
                "Cafe",
                label = RegisterCopy.SPLIT,
                amount = -3_000,
                parent = true,
                parts = listOf(part("Groceries", -2_000), part("Household", -1_000)),
            ),
            row("coffee", "Corner Coffee", label = "Groceries", amount = -575),
            row(
                "rent",
                "Riverside Property",
                label = "Household",
                amount = -120_000,
                note = "October rent, late fee waived",
                cleared = true,
            ),
            row("mystery", "Amazon", label = RegisterCopy.NO_CATEGORY, amount = -4_299, categoryId = null),
            row("pay", "transfer from Checking", label = RegisterCopy.TRANSFER, amount = 42_000, categoryId = null, cleared = true),
        )

    private fun ids(search: RegisterSearch) = RegisterSearch.filter(rows, search, decimals = 2).map { it.id }

    @Test
    fun closedOrEmptySearchKeepsEveryRow() {
        assertFalse(RegisterSearch().active)
        assertEquals(rows.map { it.id }, ids(RegisterSearch()))
        assertEquals(rows.map { it.id }, ids(RegisterSearch(query = "   ")))
    }

    @Test
    fun textMatchesPayeeCategoryNoteAndSplitParts() {
        assertEquals(listOf("coffee"), ids(RegisterSearch(query = "corner")))
        // Groceries is Corner Coffee's category and one of Cafe's split parts.
        assertEquals(listOf("cafe", "coffee"), ids(RegisterSearch(query = "GROC")))
        assertEquals(listOf("rent"), ids(RegisterSearch(query = "late fee")))
        assertEquals(listOf("pay"), ids(RegisterSearch(query = "checking")))
    }

    @Test
    fun amountWithCentsMatchesExactlyEitherSign() {
        assertEquals(listOf("coffee"), ids(RegisterSearch(query = "5.75")))
        assertEquals(listOf("coffee"), ids(RegisterSearch(query = "$5.75")))
        assertEquals(listOf("pay"), ids(RegisterSearch(query = "420.00")))
        assertEquals(listOf("pay"), ids(RegisterSearch(query = "420.0")))
        assertEquals(listOf("rent"), ids(RegisterSearch(query = "1,200.00")))
        // A split part's amount finds its transaction.
        assertEquals(listOf("cafe"), ids(RegisterSearch(query = "20.00")))
    }

    @Test
    fun wholeAmountMatchesEveryCentsValue() {
        assertEquals(listOf("mystery"), ids(RegisterSearch(query = "42.99")))
        assertEquals(listOf("mystery"), ids(RegisterSearch(query = "42")))
        assertEquals(listOf("rent"), ids(RegisterSearch(query = "1200")))
        assertEquals(listOf("rent"), ids(RegisterSearch(query = "1,200")))
    }

    @Test
    fun commaDecimalsReadAsCents() {
        assertEquals(listOf("coffee"), ids(RegisterSearch(query = "5,75")))
        assertEquals(listOf("rent"), ids(RegisterSearch(query = "1.200,00")))
    }

    @Test
    fun textWithLettersIsNeverAnAmount() {
        assertNull(RegisterSearch.amountOf("7-eleven", 2))
        assertNull(RegisterSearch.amountOf("", 2))
        assertTrue(ids(RegisterSearch(query = "7-eleven")).isEmpty())
    }

    @Test
    fun zeroDecimalCurrencyReadsWholeUnits() {
        val yen = listOf(row("ramen", "Ramen", label = "Food", amount = -1_200))
        assertEquals(listOf("ramen"), RegisterSearch.filter(yen, RegisterSearch(query = "1,200"), decimals = 0).map { it.id })
        assertEquals(listOf("ramen"), RegisterSearch.filter(yen, RegisterSearch(query = "1200"), decimals = 0).map { it.id })
    }

    @Test
    fun showNotClearedAndNoCategory() {
        assertEquals(listOf("cafe", "coffee", "mystery"), ids(RegisterSearch(show = RegisterShow.NOT_CLEARED)))
        assertEquals(listOf("mystery"), ids(RegisterSearch(show = RegisterShow.NO_CATEGORY)))
        assertEquals(listOf("coffee"), ids(RegisterSearch(query = "corner", show = RegisterShow.NOT_CLEARED)))
        assertTrue(ids(RegisterSearch(query = "rent", show = RegisterShow.NOT_CLEARED)).isEmpty())
    }

    @Test
    fun splitWithAPartMissingItsCategoryNeedsOne() {
        val split =
            row(
                "split",
                "Market",
                label = RegisterCopy.SPLIT,
                amount = -1_000,
                parent = true,
                parts = listOf(part("Groceries", -600), part("", -400)),
            )
        assertEquals(
            listOf("split"),
            RegisterSearch.filter(listOf(split), RegisterSearch(show = RegisterShow.NO_CATEGORY), 2).map { it.id },
        )
    }

    @Test
    fun countReadsShownOfTotal() {
        assertEquals("2 of 41", RegisterSearch.count(2, 41))
    }

    private fun part(
        category: String,
        amount: Long,
    ) = RegisterPart(
        id = "p-$category-$amount",
        categoryId = if (category.isEmpty()) "" else "c-$category",
        categoryName = category,
        amountMinor = amount,
    )

    private fun row(
        id: String,
        payee: String,
        label: String,
        amount: Long,
        note: String = "",
        cleared: Boolean = false,
        categoryId: String? = "c-$label",
        parent: Boolean = false,
        parts: List<RegisterPart> = emptyList(),
    ) = RegisterRow(
        id = id,
        date = 20261007,
        payee = payee,
        categoryId = if (parent) null else categoryId,
        categoryLabel = label,
        amountMinor = amount,
        note = note,
        reconciled = false,
        transferId = null,
        partnerOffBudget = null,
        partnerCategoryId = null,
        parent = parent,
        parts = parts,
        cleared = cleared,
    )
}
