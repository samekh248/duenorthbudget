package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The payees page's look-alike suggestions, and merging or deleting several payees at once. */
class PayeeLookalikesTest {
    private fun row(
        id: String,
        name: String,
        count: Int = 1,
        transfer: Boolean = false,
    ) = PayeeRow(id, name, transfer, count)

    @Test
    fun tidyDropsCaseNumbersPunctuationAndEndings() {
        assertEquals("costco", PayeeLookalikes.tidy("COSTCO #1123"))
        assertEquals("costco", PayeeLookalikes.tidy("Costco."))
        assertEquals("amazon", PayeeLookalikes.tidy("Amazon.com"))
        assertEquals("acmeplumbing", PayeeLookalikes.tidy("Acme Plumbing, LLC"))
        assertEquals("walmart", PayeeLookalikes.tidy("WAL-MART 0042"))
    }

    @Test
    fun sameNameOnceTidiedIsOneGroupKeepingTheMostUsed() {
        val groups =
            PayeeLookalikes.find(
                listOf(row("a", "Amazon", 12), row("b", "AMAZON.COM", 3), row("c", "Corner Coffee", 5)),
            )
        val group = groups.single()
        assertEquals("a", group.keep.id)
        assertEquals(listOf("b"), group.others.map { it.id })
    }

    @Test
    fun aNameThatOnlyAddsWordsLooksAlike() {
        val group = PayeeLookalikes.find(listOf(row("s", "Shell", 9), row("o", "SHELL OIL 5123", 2))).single()
        assertEquals("s", group.keep.id)
        assertEquals(listOf("o"), group.others.map { it.id })
    }

    @Test
    fun shortNamesAndTransfersAreLeftAlone() {
        val groups =
            PayeeLookalikes.find(
                listOf(
                    row("t1", "Taco", 4),
                    row("t2", "Taco Bell", 2),
                    row("x1", "Savings", 3, transfer = true),
                    row("x2", "SAVINGS", 1),
                ),
            )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun notTheSameHidesOnlyThatName() {
        val rows = listOf(row("a", "Amazon", 12), row("b", "AMAZON.COM", 3), row("c", "Amazon Marketplace", 2))
        val all = PayeeLookalikes.find(rows).single()
        assertEquals(listOf("b", "c"), all.others.map { it.id })
        val left = PayeeLookalikes.find(rows, dismissed = setOf(PayeeLookalike.key("b", "a"))).single()
        assertEquals(listOf("c"), left.others.map { it.id })
        assertTrue(PayeeLookalikes.find(rows, dismissed = all.keys.toSet()).isEmpty())
    }

    @Test
    fun mergeAllUnderANewNameKeepsOnePayee() {
        db { session ->
            val book = PayeeBook(session)
            assertEquals(PayeeWriteResult.Saved, book.mergeAll(listOf("p-amz", "p-amzn", "p-mkt"), "Amazon", rememberRule = true))
            val left = book.list().filter { !it.transfer }
            assertEquals(listOf("Amazon", "Corner Coffee", "Old Gym"), left.map { it.name })
            assertEquals(4, left.first().transactionCount)
            assertEquals("p-amz", left.first().id)
        }
    }

    @Test
    fun mergeAllOntoAnotherExistingNameUsesThatPayee() {
        db { session ->
            val book = PayeeBook(session)
            assertEquals(PayeeWriteResult.Saved, book.mergeAll(listOf("p-amzn", "p-mkt"), "amazon", rememberRule = false))
            val amazon = book.list().single { it.name.equals("amazon", ignoreCase = true) }
            assertEquals("p-amz", amazon.id)
            assertEquals(4, amazon.transactionCount)
        }
    }

    @Test
    fun mergeAllNeedsTwoAndAName() {
        db { session ->
            val book = PayeeBook(session)
            assertEquals(PayeeWriteResult.Rejected(PayeeCopy.PICK_TWO), book.mergeAll(listOf("p-amz"), "Amazon", false))
            assertEquals(PayeeWriteResult.Rejected(PayeeCopy.ENTER_NAME), book.mergeAll(listOf("p-amz", "p-mkt"), " ", false))
        }
    }

    @Test
    fun deleteAllOnlyTakesUnusedPayees() {
        db { session ->
            val book = PayeeBook(session)
            assertEquals(PayeeWriteResult.Rejected(PayeeCopy.IN_USE), book.deleteAll(listOf("p-gym", "p-amz")))
            assertEquals(5, book.list().count { !it.transfer })
            assertEquals(PayeeWriteResult.Saved, book.deleteAll(listOf("p-gym")))
            assertTrue(book.list().none { it.id == "p-gym" })
        }
    }

    @Test
    fun pageSkipsLookalikesTheAssistantWasToldAbout() {
        db { session ->
            val book = PayeeBook(session)
            val group = book.page(emptySet()).lookalikes.single()
            assertEquals("p-amz", group.keep.id)
            AssistantBook(session, java.time.LocalDate.of(2026, 11, 1)).dismiss(group.keys.first())
            val after = book.page(AssistantBook.dismissed(session)).lookalikes.single()
            assertEquals(group.others.drop(1), after.others)
        }
    }

    private fun db(block: (SqlSession) -> Unit) {
        val file = File.createTempFile("payees", ".sqlite")
        file.delete()
        JdbcSessionOpener().use(file) { session ->
            ActualSchema.create(session)
            ActualSchema.ensure(session)
            session.exec("INSERT INTO accounts (id, name, offbudget, type, sort_order) VALUES ('checking', 'Checking', 0, 'checking', 0)")
            listOf(
                "p-amz" to "Amazon",
                "p-amzn" to "AMAZON.COM",
                "p-mkt" to "Amazon Marketplace",
                "p-cafe" to "Corner Coffee",
                "p-gym" to "Old Gym",
            ).forEach { (id, name) -> session.exec("INSERT INTO payees (id, name) VALUES ('$id', '$name')") }
            listOf("t1" to "p-amz", "t5" to "p-amz", "t2" to "p-amzn", "t3" to "p-mkt", "t4" to "p-cafe").forEach { (id, payee) ->
                session.exec(
                    "INSERT INTO transactions (id, acct, amount, description, date, tombstone) " +
                        "VALUES ('$id', 'checking', -100, '$payee', 20261001, 0)",
                )
            }
            block(session)
        }
    }
}
