package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

class EnvelopeMonthTest {
    @Test
    fun assignChangesToBudgetAndAvailable() {
        db { session, clock ->
            val book = EnvelopeBook(session, clock)
            book.assignBudgeted(202610, "c-food", 12_000L)
            val shell = ShellReader.read(session, BudgetMetadata("home", "Home"), LocalDate.of(2026, 10, 7))
            assertEquals(38_000L, shell.headerMinor)
            val food = shell.groups.first().categories.first { it.id == "c-food" }
            assertEquals(12_000L, food.availableMinor)
        }
    }

    @Test
    fun moveKeepsToBudget() {
        db { session, clock ->
            val book = EnvelopeBook(session, clock)
            book.assignBudgeted(202610, "c-food", 8_000L)
            book.assignBudgeted(202610, "c-fun", 2_000L)
            val before = ShellReader.read(session, BudgetMetadata("h", "H"), LocalDate.of(2026, 10, 7))
            assertEquals(EnvelopeEditResult.Saved, book.moveAvailable(202610, "c-food", "c-fun", 1_500L))
            val after = ShellReader.read(session, BudgetMetadata("h", "H"), LocalDate.of(2026, 10, 7))
            assertEquals(before.headerMinor, after.headerMinor)
            assertEquals(6_500L, after.groups.first().categories.first { it.id == "c-food" }.availableMinor)
            assertEquals(3_500L, after.groups.first().categories.first { it.id == "c-fun" }.availableMinor)
        }
    }

    @Test
    fun moveRefusesTooMuch() {
        db { session, clock ->
            val book = EnvelopeBook(session, clock)
            book.assignBudgeted(202610, "c-food", 1_000L)
            val result = book.moveAvailable(202610, "c-food", "c-fun", 2_000L)
            assertTrue(result is EnvelopeEditResult.Rejected)
        }
    }

    @Test
    fun renameHideReorderAndDeleteGroup() {
        db { session, clock ->
            val book = EnvelopeBook(session, clock)
            book.renameGroup("g-exp", "Spending")
            book.renameCategory("c-food", "Food")
            book.hideCategory("c-fun", true)
            val shell =
                ShellReader.read(session, BudgetMetadata("h", "H"), LocalDate.of(2026, 10, 7))
            assertEquals("Spending", shell.groups.first().name)
            assertTrue(shell.groups.first().categories.none { it.id == "c-fun" })
            val manage = ShellReader.loadCategoryManage(session)
            assertEquals("Food", manage.groups.first().categories.first { it.id == "c-food" }.name)
            assertTrue(manage.groups.first().categories.first { it.id == "c-fun" }.hidden)
            assertEquals(EnvelopeEditResult.Saved, book.moveCategoryLater("c-food"))
            assertEquals(EnvelopeEditResult.Saved, book.moveGroupEarlier("g-exp"))
            session.exec(
                """
                INSERT INTO category_groups (id, name, is_income, sort_order, tombstone)
                VALUES ('g-empty', 'Empty', 0, 99, 0)
                """.trimIndent(),
            )
            assertEquals(EnvelopeEditResult.Saved, book.deleteGroup("g-empty"))
            assertTrue(
                book.deleteGroup("g-exp") is EnvelopeEditResult.Rejected,
            )
        }
    }

    fun hiddenCategoryStaysOutOfMonthView() {
        db { session, clock ->
            EnvelopeBook(session, clock).hideCategory("c-food", true)
            val shell =
                ShellReader.read(session, BudgetMetadata("h", "H"), LocalDate.of(2026, 10, 7))
            assertTrue(shell.groups.first().categories.none { it.id == "c-food" })
            EnvelopeBook(session, clock).hideCategory("c-food", false)
            val restored =
                ShellReader.read(session, BudgetMetadata("h", "H"), LocalDate.of(2026, 10, 7))
            assertTrue(restored.groups.first().categories.any { it.id == "c-food" })
        }
    }

    fun holdLowersThisMonth() {
        db { session, clock ->
            val book = EnvelopeBook(session, clock)
            val october = YearMonth.of(2026, 10)
            val header = ShellReader.loadDetailed(session, october).figures.headerMinor
            assertEquals(EnvelopeEditResult.Saved, book.setHold(202610, 25_000L, header))
            val after = ShellReader.read(session, BudgetMetadata("h", "H"), LocalDate.of(2026, 10, 7))
            assertEquals(25_000L, after.headerMinor)
            val november =
                ShellReader.read(
                    session,
                    BudgetMetadata("h", "H"),
                    LocalDate.of(2026, 10, 7),
                    YearMonth.of(2026, 11),
                )
            assertEquals(50_000L, november.headerMinor)
        }
    }

    private fun db(block: (SqlSession, SyncClock) -> Unit) {
        val file = File.createTempFile("envelope", ".sqlite")
        file.delete()
        JdbcSessionOpener().use(file) { session ->
            ActualSchema.create(session)
            SyncSchema.ensure(session)
            session.exec("INSERT INTO preferences (id, value) VALUES ('budgetType', 'envelope')")
            session.exec("INSERT INTO preferences (id, value) VALUES ('currency', 'USD')")
            session.exec(
                """
                INSERT INTO category_groups (id, name, is_income, sort_order)
                VALUES ('g-inc', 'Income', 1, 0), ('g-exp', 'Expenses', 0, 1)
                """.trimIndent(),
            )
            session.exec(
                """
                INSERT INTO categories (id, name, is_income, cat_group, sort_order)
                VALUES ('c-sal', 'Pay', 1, 'g-inc', 1),
                       ('c-food', 'Groceries', 0, 'g-exp', 1),
                       ('c-fun', 'Fun', 0, 'g-exp', 2)
                """.trimIndent(),
            )
            session.exec(
                """
                INSERT INTO transactions (id, acct, amount, category, date, sort_order)
                VALUES ('inc', 'a', 50000, 'c-sal', 20261001, 1)
                """.trimIndent(),
            )
            session.exec(
                """
                INSERT INTO zero_budgets (id, month, category, amount, carryover)
                VALUES ('202610-c-food', 202610, 'c-food', 0, 0)
                """.trimIndent(),
            )
            session.exec("INSERT INTO accounts (id, name) VALUES ('a', 'Checking')")
            val clock = SyncClock("local-test")
            block(session, clock)
        }
    }
}
