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

    @Test
    fun readsTheViewMonthsGoalsForEachCategory() {
        val file = File.createTempFile("shell", ".sqlite")
        file.delete()
        JdbcSessionOpener().use(file) { session ->
            ActualSchema.create(session)
            session.exec("INSERT INTO preferences (id, value) VALUES (?, ?)", listOf("budgetType", "envelope"))
            session.exec(
                "INSERT INTO category_groups (id, name, is_income, sort_order) VALUES (?, ?, ?, ?)",
                listOf("g-liv", "Living", 0, 0),
            )
            listOf("c-power" to "Power", "c-car" to "Car repair", "c-dining" to "Dining").forEachIndexed { i, (id, name) ->
                session.exec(
                    "INSERT INTO categories (id, name, is_income, cat_group, sort_order) VALUES (?, ?, ?, ?, ?)",
                    listOf(id, name, 0, "g-liv", i),
                )
            }
            listOf(
                listOf("b1", 202609, "c-car", 110_000, null, null),
                listOf("b2", 202610, "c-power", 10_000, 14_000, null),
                listOf("b3", 202610, "c-car", 15_000, 200_000, 1),
                listOf("b4", 202610, "c-dining", 8_000, null, null),
                listOf("b5", 202611, "c-dining", 8_000, 9_000, null),
            ).forEach {
                session.exec("INSERT INTO zero_budgets (id, month, category, amount, goal, long_goal) VALUES (?, ?, ?, ?, ?, ?)", it)
            }
            val shell = ShellReader.read(session, BudgetMetadata("home", "Home"), LocalDate.of(2026, 10, 7))
            val rows =
                shell.groups
                    .single()
                    .categories
                    .associateBy { it.id }
            assertEquals(14_000L to false, rows.getValue("c-power").let { it.goalMinor to it.longGoal })
            assertEquals(200_000L to true, rows.getValue("c-car").let { it.goalMinor to it.longGoal })
            // November's goal is not October's.
            assertEquals(null to false, rows.getValue("c-dining").let { it.goalMinor to it.longGoal })
            // A template goal measures what was budgeted; a #goal measures the balance (1100 + 150 of 2000).
            assertEquals(10f / 14f, rows.getValue("c-power").goalProgress!!, 0.0001f)
            assertEquals(0.625f, rows.getValue("c-car").goalProgress!!, 0.0001f)
            assertEquals(null, rows.getValue("c-dining").goalProgress)
        }
    }

    @Test
    fun goalProgressStaysBetweenEmptyAndFull() {
        fun row(
            budgeted: Long,
            available: Long,
            goal: Long?,
            long: Boolean = false,
        ) = CategoryRow("c", "C", budgeted, 0, available, carryover = false, goalMinor = goal, longGoal = long)
        assertEquals(1f, row(20_000, 0, 10_000).goalProgress!!, 0f)
        assertEquals(0f, row(0, -5_000, 10_000, long = true).goalProgress!!, 0f)
        assertEquals(null, row(5_000, 5_000, 0).goalProgress)
    }

    @Test
    fun hiddenGroupsAndCategoriesStayOutUnlessAskedFor() {
        val file = File.createTempFile("shell", ".sqlite")
        file.delete()
        JdbcSessionOpener().use(file) { session ->
            ActualSchema.create(session)
            session.exec("INSERT INTO preferences (id, value) VALUES (?, ?)", listOf("budgetType", "envelope"))
            listOf(
                listOf("g-inc", "Income", 1, 0, 0),
                listOf("g-old", "Old income", 1, 1, 1),
                listOf("g-liv", "Living", 0, 0, 2),
                listOf("g-gone", "Gone", 0, 1, 3),
            ).forEach {
                session.exec("INSERT INTO category_groups (id, name, is_income, hidden, sort_order) VALUES (?, ?, ?, ?, ?)", it)
            }
            listOf(
                listOf("c-sal", "Salary", 1, "g-inc", 0, 0),
                listOf("c-bonus", "Bonus", 1, "g-inc", 1, 1),
                listOf("c-side", "Side job", 1, "g-old", 0, 0),
                listOf("c-food", "Food", 0, "g-liv", 0, 0),
                listOf("c-gym", "Gym", 0, "g-liv", 1, 1),
                listOf("c-boat", "Boat", 0, "g-gone", 0, 0),
            ).forEach {
                session.exec("INSERT INTO categories (id, name, is_income, cat_group, hidden, sort_order) VALUES (?, ?, ?, ?, ?, ?)", it)
            }
            session.exec(
                "INSERT INTO zero_budgets (id, month, category, amount, carryover) VALUES (?, ?, ?, ?, ?)",
                listOf("b1", 202610, "c-gym", 5_000, 0),
            )
            val today = LocalDate.of(2026, 10, 7)
            val shell = ShellReader.read(session, BudgetMetadata("home", "Home"), today)
            assertEquals(listOf("g-liv"), shell.groups.map { it.id })
            val living = shell.groups.single()
            assertEquals(listOf("c-food"), living.categories.map { it.id })
            assertEquals(listOf("g-inc"), shell.incomeGroups.map { it.id })
            val income = shell.incomeGroups.single()
            assertEquals(listOf("c-sal"), income.categories.map { it.id })
            // Hidden categories still count toward the month.
            assertEquals(-5_000L, shell.headerMinor)
            assertEquals(5_000L, living.availableMinor)

            val all = ShellReader.read(session, BudgetMetadata("home", "Home"), today, showHidden = true)
            assertEquals(listOf("g-liv" to false, "g-gone" to true), all.groups.map { it.id to it.hidden })
            assertEquals(listOf("c-food" to false, "c-gym" to true), all.groups[0].categories.map { it.id to it.hidden })
            assertEquals(listOf("c-boat"), all.groups[1].categories.map { it.id })
            assertEquals(listOf("g-inc" to false, "g-old" to true), all.incomeGroups.map { it.id to it.hidden })
            assertEquals(listOf("c-sal" to false, "c-bonus" to true), all.incomeGroups[0].categories.map { it.id to it.hidden })
            assertEquals(shell.headerMinor, all.headerMinor)
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
