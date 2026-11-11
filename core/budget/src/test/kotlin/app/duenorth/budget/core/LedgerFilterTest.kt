package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LedgerFilterTest {
    private val today = LocalDate.of(2026, 11, 11)
    private val usd = CurrencySpec("USD", "$", 2)

    private val rows =
        listOf(
            row("coffee", "Corner Coffee", "c-groc", -575, 20261110, pending = true),
            row("market", "Riverside Market", "c-groc", -8_420, 20261105, cleared = true),
            row(
                "cafe",
                "Cafe",
                null,
                -3_000,
                20261102,
                parent = true,
                parts = listOf(part("c-groc", -2_000), part("c-home", -1_000, note = "towels")),
            ),
            row("rent", "Riverside Property", "c-home", -120_000, 20261015, note = "October rent", cleared = true),
            row("mystery", "Amazon", null, -4_299, 20260820, label = RegisterCopy.NO_CATEGORY),
            row("pay", "transfer from Checking", null, 42_000, 20261101, transfer = true, label = RegisterCopy.TRANSFER),
        )

    private fun ids(filter: LedgerFilter) = filter.filter(rows, today, 2).map { it.id }

    @Test
    fun emptyFilterKeepsEveryRow() {
        assertFalse(LedgerFilter().active)
        assertEquals(0, LedgerFilter().count)
        assertEquals(rows.map { it.id }, ids(LedgerFilter()))
    }

    @Test
    fun categoryMatchesTheRowOrAnySplitPart() {
        assertEquals(listOf("coffee", "market", "cafe"), ids(LedgerFilter(categoryIds = setOf("c-groc"))))
        assertEquals(listOf("cafe", "rent"), ids(LedgerFilter(categoryIds = setOf("c-home"))))
    }

    @Test
    fun datesCountBackFromToday() {
        assertEquals(listOf("coffee", "market", "cafe", "pay"), ids(LedgerFilter(dates = LedgerDates.THIS_MONTH)))
        assertEquals(listOf("rent"), ids(LedgerFilter(dates = LedgerDates.LAST_MONTH)))
        // 90 days back from Nov 11 reaches Aug 14, so Aug 20 is in.
        assertEquals(rows.map { it.id }, ids(LedgerFilter(dates = LedgerDates.DAYS_90)))
    }

    @Test
    fun amountRangeMatchesSizeEitherSignAndIgnoresUnreadableText() {
        assertEquals(listOf("market", "rent", "pay"), ids(LedgerFilter(minAmount = "50")))
        assertEquals(listOf("coffee", "cafe", "mystery"), ids(LedgerFilter(maxAmount = "50")))
        assertEquals(listOf("market", "mystery"), ids(LedgerFilter(minAmount = "40", maxAmount = "100")))
        assertEquals(rows.map { it.id }, ids(LedgerFilter(minAmount = "abc")))
    }

    @Test
    fun statusSplitsPendingNotClearedAndCleared() {
        assertEquals(listOf("coffee"), ids(LedgerFilter(status = LedgerStatus.PENDING)))
        assertEquals(listOf("coffee", "cafe", "mystery", "pay"), ids(LedgerFilter(status = LedgerStatus.NOT_CLEARED)))
        assertEquals(listOf("market", "rent"), ids(LedgerFilter(status = LedgerStatus.CLEARED)))
    }

    @Test
    fun textMatchesPayeeAndNotesIncludingSplitParts() {
        assertEquals(listOf("market", "rent"), ids(LedgerFilter(text = "riverside")))
        assertEquals(listOf("cafe"), ids(LedgerFilter(text = "TOWELS")))
        assertEquals(listOf("rent"), ids(LedgerFilter(text = "october")))
    }

    @Test
    fun flagsMustAllHold() {
        assertEquals(listOf("cafe"), ids(LedgerFilter(flags = setOf(LedgerFlag.SPLIT))))
        assertEquals(listOf("pay"), ids(LedgerFilter(flags = setOf(LedgerFlag.TRANSFER))))
        // A transfer has no category but needs none.
        assertEquals(listOf("mystery"), ids(LedgerFilter(flags = setOf(LedgerFlag.NO_CATEGORY))))
        assertEquals(listOf("cafe", "rent"), ids(LedgerFilter(flags = setOf(LedgerFlag.HAS_NOTES))))
        assertEquals(listOf("cafe"), ids(LedgerFilter(flags = setOf(LedgerFlag.SPLIT, LedgerFlag.HAS_NOTES))))
    }

    @Test
    fun filtersCombine() {
        val filter = LedgerFilter(categoryIds = setOf("c-groc"), dates = LedgerDates.THIS_MONTH, minAmount = "50")
        assertEquals(listOf("market"), ids(filter))
        assertEquals(3, filter.count)
        assertTrue(filter.active)
    }

    @Test
    fun accountsNarrowOnlyWhenTheRowSaysWhichAccount() {
        val filter = LedgerFilter(accountIds = setOf("visa"))
        assertTrue(filter.matches(rows[0], today, 2, accountId = "visa"))
        assertFalse(filter.matches(rows[0], today, 2, accountId = "checking"))
        assertTrue(filter.matches(rows[0], today, 2))
    }

    @Test
    fun summaryReadsEachFilterInSheetOrder() {
        val categories =
            listOf(
                CategoryChoice("c-groc", "Groceries", "Food", false),
                CategoryChoice("c-home", "Household", "Home", false),
                CategoryChoice("c-gas", "Gas", "Car", false),
            )
        val filter =
            LedgerFilter(
                categoryIds = setOf("c-groc"),
                dates = LedgerDates.THIS_MONTH,
                minAmount = "50",
                status = LedgerStatus.NOT_CLEARED,
                text = " shell ",
                flags = setOf(LedgerFlag.HAS_NOTES, LedgerFlag.SPLIT),
            )
        assertEquals(
            "Groceries · this month · over $50.00 · not cleared · “shell” · split · has notes",
            filter.summary(categories, emptyList(), usd),
        )
        assertEquals("under $20.00", LedgerFilter(maxAmount = "20").summary(categories, emptyList(), usd))
        assertEquals("$5.00 to $20.00", LedgerFilter(minAmount = "5", maxAmount = "20").summary(categories, emptyList(), usd))
        assertEquals("Gas, Groceries", LedgerFilter(categoryIds = setOf("c-groc", "c-gas")).summary(categories, emptyList(), usd))
        assertEquals("Gas +2", LedgerFilter(categoryIds = setOf("c-groc", "c-gas", "c-home")).summary(categories, emptyList(), usd))
    }

    @Test
    fun spansCoverWholeMonthsAndYears() {
        assertEquals(20261101..20261130, LedgerDates.THIS_MONTH.span(today))
        assertEquals(20261001..20261031, LedgerDates.LAST_MONTH.span(today))
        assertEquals(20260101..20261231, LedgerDates.THIS_YEAR.span(today))
        assertEquals(20251201..20251231, LedgerDates.LAST_MONTH.span(LocalDate.of(2026, 1, 5)))
    }

    private fun part(
        categoryId: String,
        amount: Long,
        note: String? = null,
    ) = RegisterPart(id = "p-$categoryId-$amount", categoryId = categoryId, categoryName = categoryId, amountMinor = amount, note = note)

    private fun row(
        id: String,
        payee: String,
        categoryId: String?,
        amount: Long,
        date: Int,
        note: String = "",
        cleared: Boolean = false,
        pending: Boolean = false,
        parent: Boolean = false,
        transfer: Boolean = false,
        label: String = categoryId ?: RegisterCopy.SPLIT,
        parts: List<RegisterPart> = emptyList(),
    ) = RegisterRow(
        id = id,
        date = date,
        payee = payee,
        categoryId = categoryId,
        categoryLabel = label,
        amountMinor = amount,
        note = note,
        reconciled = false,
        transferId = if (transfer) "t-$id" else null,
        partnerOffBudget = null,
        partnerCategoryId = null,
        parent = parent,
        parts = parts,
        cleared = cleared,
        pending = pending,
    )
}
