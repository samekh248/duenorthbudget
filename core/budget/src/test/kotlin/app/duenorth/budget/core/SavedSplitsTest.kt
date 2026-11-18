package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class SavedSplitsTest {
    private val rentAndParking =
        SavedSplit(
            id = "rent",
            name = "Rent + parking",
            lines = listOf(SavedSplitLine("c-rent", amountMinor = 180_000), SavedSplitLine("c-parking")),
            payees = listOf("Oak Street Apartments"),
            totalMinor = -195_000,
        )

    private val costco =
        SavedSplit(
            id = "costco",
            name = "Costco run",
            lines = listOf(SavedSplitLine("c-groceries", shareBp = 6_000), SavedSplitLine("c-household")),
            payees = listOf("Costco"),
            totalMinor = -20_000,
        )

    @Test
    fun fixedLinesKeepTheirAmountAndTheRestLineTakesWhatIsLeft() {
        val parts = rentAndParking.partsFor(-196_500)!!
        assertEquals(listOf(-180_000L, -16_500L), parts.map { it.amountMinor })
        assertEquals(listOf("c-rent", "c-parking"), parts.map { it.categoryId })
    }

    @Test
    fun sharesFitAnyAmountAndFollowItsSign() {
        assertEquals(listOf(-7_407L, -4_938L), costco.partsFor(-12_345)!!.map { it.amountMinor })
        assertEquals(listOf(600L, 400L), costco.partsFor(1_000)!!.map { it.amountMinor })
    }

    @Test
    fun sharesWithoutARestLineTakeTheRoundingOnTheLastShare() {
        val thirds =
            SavedSplit(
                "t",
                "Thirds",
                listOf(SavedSplitLine("a", shareBp = 3_333), SavedSplitLine("b", shareBp = 3_333), SavedSplitLine("c", shareBp = 3_334)),
            )
        val parts = thirds.partsFor(-1_000)!!
        assertEquals(-1_000L, parts.sumOf { it.amountMinor })
    }

    @Test
    fun aSplitThatCantReachTheTotalDoesntFit() {
        // The fixed line alone is more than the whole transaction.
        assertNull(rentAndParking.partsFor(-100_000))
        // Exactly the fixed line leaves nothing for the rest.
        assertNull(rentAndParking.partsFor(-180_000))
        val fixed = SavedSplit("f", "Fixed", listOf(SavedSplitLine("a", amountMinor = 100), SavedSplitLine("b", amountMinor = 200)))
        assertEquals(listOf(100L, 200L), fixed.partsFor(300)!!.map { it.amountMinor })
        assertNull(fixed.partsFor(301))
    }

    @Test
    fun savingFromTheFormKeepsAmountsOrShares() {
        val parts = listOf(SplitPart("c-groceries", -12_000), SplitPart("c-household", -8_000))
        val amounts = SavedSplits.fromParts("id", " Costco run ", parts, -20_000, shares = false, payee = "Costco")
        assertEquals("Costco run", amounts.name)
        assertEquals(listOf(12_000L, null), amounts.lines.map { it.amountMinor })
        assertTrue(amounts.lines.last().rest)
        assertEquals(listOf("Costco"), amounts.payees)

        val shares = SavedSplits.fromParts("id", "Costco run", parts, -20_000, shares = true, payee = ShellCopy.NO_PAYEE)
        assertEquals(listOf(6_000, null), shares.lines.map { it.shareBp })
        assertTrue(shares.payees.isEmpty())
        assertEquals(
            "Groceries 60% · Household the rest",
            SavedSplits.summary(shares, {
                it!!.removePrefix("c-").replaceFirstChar(Char::uppercase)
            }, { "$it" }),
        )
    }

    @Test
    fun savingUnderAnExistingNameReplacesItAndKeepsItsPayees() {
        val again = costco.copy(id = "new", name = "costco RUN", payees = listOf("Costco Gas"))
        val kept = SavedSplits.put(listOf(rentAndParking, costco), again)
        assertEquals(2, kept.size)
        assertEquals("costco", kept[1].id)
        assertEquals(listOf("Costco", "Costco Gas"), kept[1].payees)
    }

    @Test
    fun usingASplitOnANewPayeeTeachesIt() {
        val learned = SavedSplits.learn(listOf(costco), "costco", "COSTCO WHSE #0482")
        assertEquals(listOf("Costco", "COSTCO WHSE #0482"), learned.single().payees)
        assertEquals(learned, SavedSplits.learn(learned, "costco", "costco whse #0482"))
    }

    @Test
    fun theSamePayeeOrABanksWordingOfItIsMatched() {
        val splits = listOf(rentAndParking, costco)
        val same = SavedSplitMatcher.guess("Oak Street Apartments", -196_500, splits)!!
        assertEquals("rent", same.splitId)
        assertEquals(listOf("same payee", "about the usual amount"), same.reasons)

        val bank = SavedSplitMatcher.guess("COSTCO WHSE #0482", -8_412, splits)!!
        assertEquals("costco", bank.splitId)
        assertEquals(listOf("names “Costco”"), bank.reasons)
        assertEquals(-8_412L, bank.parts.sumOf { it.amountMinor })
    }

    @Test
    fun theExactAmountAloneIsAMatchButACloseOneIsNot() {
        val splits = listOf(rentAndParking)
        assertEquals(listOf("same amount"), SavedSplitMatcher.guess("ACH DEBIT 99812", -195_000, splits)!!.reasons)
        assertNull(SavedSplitMatcher.guess("ACH DEBIT 99812", -196_000, splits))
    }

    @Test
    fun moneyInIsntMatchedToASplitSavedFromMoneyOut() {
        assertNull(SavedSplitMatcher.guess("Costco", 5_000, listOf(costco)))
    }

    @Test
    fun twoSplitsThatLookAsLikelyAreNeitherOffered() {
        val other = costco.copy(id = "other", name = "Costco for mom")
        assertNull(SavedSplitMatcher.guess("Costco", -9_000, listOf(costco, other)))
    }

    @Test
    fun theInboxOffersASavedSplitAndApplyingItSplitsTheTransaction() {
        db { session ->
            SavedSplits.write(session, listOf(costco))
            bank(session, "t", 20261104, -8_412, "COSTCO WHSE #0482")
            val row = inbox(session).single()
            assertEquals("costco", row.split?.splitId)
            assertEquals(1, ShellReader.read(session, BudgetMetadata("home", "Home"), TODAY).savedSplits.size)

            assertEquals(WriteResult.Saved, RegisterBook(session).split("t", row.split!!.parts, false))
            assertTrue(inbox(session).isEmpty())
            val kids =
                session
                    .query(
                        "SELECT category, amount FROM transactions WHERE parent_id = 't' AND IFNULL(tombstone, 0) = 0 ORDER BY sort_order",
                    )
            assertEquals(listOf("c-groceries", "c-household"), kids.map { it.str("category") })
            assertEquals(listOf(-5_047L, -3_365L), kids.map { it.long("amount") })
        }
    }

    @Test
    fun aRowThatLooksLikeATransferIsNotOfferedASplit() {
        db { session ->
            SavedSplits.write(session, listOf(rentAndParking.copy(totalMinor = -50_000, payees = emptyList())))
            bank(session, "t", 20261104, -50_000, "ONLINE TRANSFER TO SAV XXXXXX4821")
            val row = inbox(session).single()
            assertTrue(row.transfer != null)
            assertNull(row.split)
        }
    }

    @Test
    fun aBrokenPreferenceReadsAsNoSplits() {
        assertTrue(SavedSplits.decode("{not json").isEmpty())
        assertTrue(SavedSplits.decode(null).isEmpty())
        assertEquals(listOf(costco), SavedSplits.decode(SavedSplits.encode(listOf(costco))))
    }

    private fun inbox(session: SqlSession) = ShellReader.read(session, BudgetMetadata("home", "Home"), TODAY).inbox

    private fun bank(
        session: SqlSession,
        id: String,
        date: Int,
        amount: Long,
        imported: String,
    ) {
        val payee = "p-$id"
        session.exec("INSERT INTO payees (id, name) VALUES (?, ?)", listOf(payee, imported))
        session.exec(
            """
            INSERT INTO transactions (id, acct, amount, description, date, financial_id, imported_description, sort_order)
            VALUES (?, 'checking', ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            listOf(id, amount, payee, date, "f-$id", imported, date.toDouble()),
        )
    }

    private fun db(block: (SqlSession) -> Unit) {
        val file = File.createTempFile("saved-splits", ".sqlite")
        file.delete()
        try {
            JdbcSessionOpener().use(file) { session ->
                ActualSchema.create(session)
                session.exec("INSERT OR REPLACE INTO preferences (id, value) VALUES ('budgetType', 'envelope')")
                session.exec("INSERT OR REPLACE INTO preferences (id, value) VALUES ('currency', 'USD')")
                session.exec("INSERT INTO category_groups (id, name, is_income, sort_order) VALUES ('g', 'Spending', 0, 0)")
                listOf("c-groceries" to "Groceries", "c-household" to "Household", "c-rent" to "Rent", "c-parking" to "Parking")
                    .forEach { (id, name) ->
                        session.exec("INSERT INTO categories (id, name, is_income, cat_group) VALUES (?, ?, 0, 'g')", listOf(id, name))
                    }
                session.exec(
                    "INSERT INTO accounts (id, name, offbudget, type, sort_order) VALUES ('checking', 'Checking 1234', 0, 'checking', 0)",
                )
                session.exec(
                    "INSERT INTO accounts (id, name, offbudget, type, sort_order) VALUES ('savings', 'Ally Savings 4821', 0, 'savings', 1)",
                )
                block(session)
            }
        } finally {
            file.delete()
        }
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 11, 10)
    }
}
