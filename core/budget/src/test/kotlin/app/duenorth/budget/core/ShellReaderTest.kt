package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.LocalDate

class ShellReaderTest {
    @Test
    fun preloadedFileMatchesTheEnvelopeFixture() {
        val file = File.createTempFile("shell", ".sqlite")
        file.delete()
        JdbcSessionOpener().use(file) { session ->
            ActualSchema.create(session)
            session.exec("INSERT INTO preferences (id, value) VALUES (?, ?)", listOf("budgetType", "envelope"))
            session.exec("INSERT INTO preferences (id, value) VALUES (?, ?)", listOf("currency", "USD"))
            session.exec(
                "INSERT INTO category_groups (id, name, is_income, sort_order) VALUES (?, ?, ?, ?)",
                listOf("g-inc", "Income", 1, 0),
            )
            session.exec(
                "INSERT INTO category_groups (id, name, is_income, sort_order) VALUES (?, ?, ?, ?)",
                listOf("g-liv", "Living", 0, 1),
            )
            session.exec(
                "INSERT INTO categories (id, name, is_income, cat_group) VALUES (?, ?, ?, ?)",
                listOf("c-sal", "Salary", 1, "g-inc"),
            )
            session.exec(
                "INSERT INTO categories (id, name, is_income, cat_group) VALUES (?, ?, ?, ?)",
                listOf("c-food", "Food", 0, "g-liv"),
            )
            session.exec(
                "INSERT INTO categories (id, name, is_income, cat_group) VALUES (?, ?, ?, ?)",
                listOf("c-rent", "Rent", 0, "g-liv"),
            )
            session.exec(
                "INSERT INTO zero_budgets (id, month, category, amount, carryover) VALUES (?, ?, ?, ?, ?)",
                listOf("b1", 202609, "c-food", 200_000, 0),
            )
            session.exec(
                "INSERT INTO zero_budgets (id, month, category, amount, carryover) VALUES (?, ?, ?, ?, ?)",
                listOf("b2", 202609, "c-rent", 100_000, 0),
            )
            session.exec(
                "INSERT INTO accounts (id, name, offbudget, sort_order) VALUES (?, ?, ?, ?)",
                listOf("checking", "Checking", 0, 0),
            )
            session.exec(
                "INSERT INTO accounts (id, name, offbudget, sort_order) VALUES (?, ?, ?, ?)",
                listOf("savings", "Savings", 1, 1),
            )
            session.exec(
                "INSERT INTO payees (id, name) VALUES (?, ?)",
                listOf("p-cafe", "Cafe"),
            )
            session.exec(
                "INSERT INTO payees (id, name) VALUES (?, ?)",
                listOf("p-bus", "Bus"),
            )
            insertTxn(session, "t-pay", "checking", "c-sal", 500_000, "p-cafe", 20260915, 1.0)
            insertTxn(session, "t-food", "checking", "c-food", -150_000, "p-cafe", 20260916, 1.0)
            insertTxn(session, "t-rent", "checking", "c-rent", -120_000, "p-bus", 20260917, 1.0)
            insertTxn(session, "t-save", "savings", null, 10_000, null, 20261001, 1.0)
            insertTxn(session, "t-bus", "checking", null, -1_000, "p-bus", 20261001, 1.0)
            insertTxn(session, "t-cafe", "checking", null, -2_500, "p-cafe", 20261002, 2.0)
            insertTxn(session, "t-xfer", "checking", null, -400, null, 20261003, 1.0, transfer = "other")
            insertTxn(session, "t-parent", "checking", null, -100, null, 20261004, 1.0, parent = true)
            insertTxn(session, "t-child", "checking", "c-food", -100, null, 20261004, 1.0, childOf = "t-parent")
            val shell =
                ShellReader.read(
                    session,
                    BudgetMetadata("home", "Home"),
                    LocalDate.of(2026, 10, 7),
                )
            assertEquals(180_000L, shell.headerMinor)
            assertEquals("Living", shell.groups.single().name)
            // October's split child spends 100 more from Food, so the rolled leftover is 49900.
            assertEquals(49_900L, shell.groups.single().availableMinor)
            assertEquals(listOf("Checking", "Savings"), shell.accounts.map { it.name })
            assertEquals(false, shell.accounts[0].offBudget)
            assertEquals(true, shell.accounts[1].offBudget)
            // Parent is excluded. Children and the other checking rows count.
            // 500000 - 150000 - 120000 - 1000 - 2500 - 400 - 100 = 226000
            assertEquals(226_000L, shell.accounts[0].balanceMinor)
            assertEquals(10_000L, shell.accounts[1].balanceMinor)
            assertEquals(listOf("Cafe", "Bus"), shell.inbox.map { it.payee })
            assertEquals(listOf(-2_500L, -1_000L), shell.inbox.map { it.amountMinor })
        }
    }

    private fun insertTxn(
        session: SqlSession,
        id: String,
        account: String,
        category: String?,
        amount: Long,
        payee: String?,
        date: Int,
        sort: Double,
        transfer: String? = null,
        parent: Boolean = false,
        childOf: String? = null,
    ) {
        session.exec(
            """
            INSERT INTO transactions
                (id, isParent, isChild, parent_id, acct, category, amount, description, date, transferred_id, sort_order)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            listOf(
                id,
                if (parent) 1 else 0,
                if (childOf != null) 1 else 0,
                childOf,
                account,
                category,
                amount,
                payee,
                date,
                transfer,
                sort,
            ),
        )
    }
}
