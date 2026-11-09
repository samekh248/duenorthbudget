package app.duenorth.budget.core

import app.duenorth.budget.core.parity.ParityFixture
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Transfers, reconciling, split lines, and deleting categories, as Actual does them. */
class ActualBehaviorTest {
    private val fixture = ParityFixture.load("schedules-rules", "learning-off")
    private val food = fixture.id("categories", "Food")
    private val bills = fixture.id("categories", "Bills")
    private val income = fixture.id("categories", "Income")
    private val checking = fixture.id("accounts", "Checking")
    private val savings = "acct-savings"

    private fun withSession(block: (SqlSession) -> Unit) {
        fixture.open().use { opened ->
            opened.db { session ->
                SyncSchema.writeRow(session, "accounts", savings, "name" to "Savings", "offbudget" to 0, "closed" to 0, "tombstone" to 0)
                block(session)
            }
        }
    }

    private fun row(
        session: SqlSession,
        id: String,
    ) = session.query("SELECT * FROM transactions WHERE id = ?", listOf(id)).single()

    @Test
    fun theOtherSideOfATransferComesInUnclearedAndRunsItsRules() {
        withSession { session ->
            RulesBook(session).insertRule(
                null,
                Json.parseToJsonElement("""[{"op":"is","field":"acct","value":"$savings","type":"id"}]""").jsonArray,
                Json.parseToJsonElement("""[{"op":"append-notes","value":" (savings)","type":"string"}]""").jsonArray,
            )
            val saved =
                RegisterBook(session).save(
                    TransactionDraft("t-out", checking, 20261007, "Savings", -5_000L, null, "move", transferAccountId = savings),
                )
            assertEquals(WriteResult.Saved, saved)
            val partnerId = row(session, "t-out").str("transferred_id")!!
            val partner = row(session, partnerId)
            assertEquals(false, partner.bool("cleared"))
            assertEquals("move (savings)", partner.str("notes"))
            assertTrue(row(session, "t-out").bool("cleared"))
            assertEquals("move", row(session, "t-out").str("notes"))
        }
    }

    @Test
    fun finishingAReconcileLocksASplitsLines() {
        withSession { session ->
            val register = RegisterBook(session)
            register.save(
                TransactionDraft(
                    "split",
                    checking,
                    20261007,
                    "Cafe",
                    -3_000L,
                    null,
                    null,
                    parts = listOf(SplitPart(food, -2_000L), SplitPart(bills, -1_000L)),
                ),
            )
            val reconcile = ReconcileBook(session, BudgetClock { fixture.today })
            reconcile.start(checking, "0", "2026-10-07")
            assertEquals(ReconcileFinishResult.Finished, reconcile.finish(checking, adjust = true))
            val lines = session.query("SELECT reconciled FROM transactions WHERE parent_id = 'split' AND IFNULL(tombstone, 0) = 0")
            assertEquals(2, lines.size)
            assertTrue(lines.all { it.bool("reconciled") })
        }
    }

    @Test
    fun aSplitLineCanHaveItsOwnPayeeAndNotesOrNoCategory() {
        withSession { session ->
            val register = RegisterBook(session)
            val saved =
                register.save(
                    TransactionDraft(
                        "split",
                        checking,
                        20261007,
                        "Cafe",
                        -3_000L,
                        null,
                        "lunch",
                        parts = listOf(SplitPart(food, -2_000L, payee = "Corner Shop", note = "snacks"), SplitPart(null, -1_000L)),
                    ),
                )
            assertEquals(WriteResult.Saved, saved)
            val lines =
                register
                    .read(checking)!!
                    .rows
                    .single { it.id == "split" }
                    .parts
            assertEquals("Corner Shop", lines[0].payee)
            assertEquals("snacks", lines[0].note)
            assertEquals("", lines[1].categoryId)
            assertNull(lines[1].payee)
            assertNull(lines[1].note)
        }
    }

    @Test
    fun deletingAUsedCategoryMovesItsMoneyAndTransactions() {
        withSession { session ->
            val clock = SyncClock("local-test")
            BudgetEdits.assign(session, clock, 202610, food, 5_000L)
            BudgetEdits.assign(session, clock, 202610, bills, 1_000L)
            RegisterBook(session).save(TransactionDraft("t-food", checking, 20261007, "Cafe", -700L, food, null))
            val book = EnvelopeBook(session, clock)
            assertTrue(book.deleteCategory(food) is EnvelopeEditResult.Rejected)
            assertEquals(EnvelopeEditResult.Rejected(EnvelopeCopy.TRANSFER_SAME_KIND), book.deleteCategory(food, income))
            assertEquals(EnvelopeEditResult.Saved, book.deleteCategory(food, bills))
            val budgeted =
                session
                    .query("SELECT amount FROM zero_budgets WHERE month = 202610 AND category = ?", listOf(bills))
                    .single()
                    .long("amount")
            assertEquals(6_000L, budgeted)
            assertEquals(bills, row(session, "t-food").str("category"))
            assertEquals(
                bills,
                session.query("SELECT transferId FROM category_mapping WHERE id = ?", listOf(food)).single().str("transferId"),
            )
            assertTrue(session.query("SELECT tombstone FROM categories WHERE id = ?", listOf(food)).single().bool("tombstone"))
        }
    }
}
