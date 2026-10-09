package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.YearMonth

class ReconcileReviewTest {
    @Test
    fun reconcileFinishMarksClearedRowsAndCancelRestores() {
        db { session, register ->
            register.save(draft("a", amount = 100))
            register.save(draft("b", amount = 200))
            register.save(draft("c", amount = 300))
            register.save(draft("d", amount = 400))
            register.save(draft("e", amount = 500))
            val reconcile = ReconcileBook(session)
            val started =
                reconcile.start("checking", "6.00", "2026-10-07") as ReconcileStartResult.Started
            assertEquals(600L, started.page.differenceMinor)
            reconcile.toggleCleared("checking", "a")
            reconcile.toggleCleared("checking", "b")
            reconcile.toggleCleared("checking", "c")
            assertEquals(0L, reconcile.read("checking")!!.differenceMinor)
            assertEquals(ReconcileFinishResult.Finished, reconcile.finish("checking"))
            assertTrue(row(session, "a").reconciled)
            assertTrue(row(session, "b").reconciled)
            assertTrue(row(session, "c").reconciled)
            assertFalse(row(session, "d").reconciled)
            assertFalse(row(session, "e").reconciled)
        }
    }

    @Test
    fun reconcileCancelRestoresClearedWithoutChangingReconciled() {
        db { session, register ->
            register.save(draft("a", amount = 100))
            session.exec("UPDATE transactions SET reconciled = 1 WHERE id = 'a'")
            register.save(draft("b", amount = 200))
            session.exec("UPDATE transactions SET cleared = 0 WHERE id = 'b'")
            val reconcile = ReconcileBook(session)
            reconcile.start("checking", "3.00", "2026-10-07")
            reconcile.toggleCleared("checking", "b")
            assertTrue(reconcile.cancel("checking"))
            assertTrue(row(session, "a").reconciled)
            assertFalse(row(session, "b").cleared)
        }
    }

    @Test
    fun resumeDoesNotStartSecondSession() {
        db { session, register ->
            register.save(draft("a", amount = 100))
            val reconcile = ReconcileBook(session)
            reconcile.start("checking", "1.00", "2026-10-07")
            val again = reconcile.start("checking", "999", "2026-10-07") as ReconcileStartResult.Started
            assertEquals(100L, again.page.statementMinor)
        }
    }

    @Test
    fun monthReviewMatchesShellSpentAndSkipsTransfers() {
        db { session, register ->
            register.save(draft("food", amount = -4_000, category = "c-food"))
            register.save(draft("fun", amount = -1_000, category = "c-fun"))
            register.save(draft("pay", amount = 10_000, category = "c-income", payee = "Work"))
            session.exec(
                "INSERT INTO accounts (id, name, offbudget, type, sort_order) VALUES ('other', 'Other', 0, 'checking', 3)",
            )
            register.transfer(
                TransferDraft(
                    id = "xfer",
                    fromAccountId = "checking",
                    toAccountId = "other",
                    date = 20261007,
                    amountMinor = 500,
                    categoryId = null,
                    note = null,
                    force = false,
                ),
            )
            val month = YearMonth.of(2026, 10)
            val review = ReviewReader.monthReview(session, month)
            assertEquals(10_000L, review.incomeMinor)
            assertEquals(listOf("c-food", "c-fun"), review.categories.map { it.id })
            assertEquals(-4_000L, review.categories.first { it.id == "c-food" }.spentMinor)
            val category = ReviewReader.categoryMonth(session, "c-food", month)!!
            assertEquals(1, category.rows.size)
            assertEquals(-4_000L, category.totalMinor)
        }
    }

    @Test
    fun netWorthSplitsOnAndOffBudget() {
        db { session, register ->
            register.save(draft("on", amount = 1_000))
            register.save(draft("off", account = "savings", amount = 500, category = null))
            val page = ReviewReader.netWorth(session, includeOffBudget = true)
            assertEquals(1_000L, page.onBudgetMinor)
            assertEquals(500L, page.offBudgetMinor)
            assertEquals(1_500L, page.totalMinor)
            val hidden = ReviewReader.netWorth(session, includeOffBudget = false)
            assertEquals(1_000L, hidden.totalMinor)
        }
    }

    private fun row(
        session: SqlSession,
        id: String,
    ): TxFlags {
        val row =
            session
                .query("SELECT cleared, reconciled FROM transactions WHERE id = ?", listOf(id))
                .first()
        return TxFlags(row.bool("cleared"), row.bool("reconciled"))
    }

    private data class TxFlags(
        val cleared: Boolean,
        val reconciled: Boolean,
    )

    private fun db(block: (SqlSession, RegisterBook) -> Unit) {
        val file = File.createTempFile("review", ".sqlite")
        file.delete()
        JdbcSessionOpener().use(file) { session ->
            ActualSchema.create(session)
            seed(session)
            block(session, RegisterBook(session))
        }
    }

    private fun seed(session: SqlSession) {
        session.exec("INSERT INTO preferences (id, value) VALUES ('budgetType', 'envelope')")
        session.exec("INSERT INTO preferences (id, value) VALUES ('currency', 'USD')")
        session.exec(
            """
            INSERT INTO category_groups (id, name, is_income, sort_order)
            VALUES ('g-exp', 'Expenses', 0, 1), ('g-inc', 'Income', 1, 0)
            """.trimIndent(),
        )
        session.exec(
            """
            INSERT INTO categories (id, name, is_income, cat_group, sort_order)
            VALUES
                ('c-food', 'Groceries', 0, 'g-exp', 1),
                ('c-fun', 'Fun', 0, 'g-exp', 2),
                ('c-income', 'Pay', 1, 'g-inc', 1)
            """.trimIndent(),
        )
        session.exec(
            """
            INSERT INTO accounts (id, name, offbudget, type, sort_order)
            VALUES ('checking', 'Checking', 0, 'checking', 1),
                   ('savings', 'Savings', 1, 'savings', 2)
            """.trimIndent(),
        )
        session.exec("INSERT INTO payees (id, name) VALUES ('p-cafe', 'Cafe'), ('p-work', 'Work')")
        session.exec(
            "INSERT INTO zero_budgets (id, month, category, amount, carryover) VALUES ('b-food', 202610, 'c-food', 10000, 0)",
        )
    }

    private fun draft(
        id: String,
        account: String = "checking",
        amount: Long = -1_000,
        category: String? = "c-food",
        date: Int = 20261007,
        payee: String = "Cafe",
    ) = TransactionDraft(id, account, date, payee, amount, category, null, false)

}
