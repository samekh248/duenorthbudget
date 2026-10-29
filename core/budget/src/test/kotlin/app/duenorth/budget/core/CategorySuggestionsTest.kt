package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class CategorySuggestionsTest {
    @Test
    fun payeeHistoryLeadsMostUsedFirstAndSkipsHiddenCategories() {
        db { session ->
            txn(session, "a", "p-cafe", "c-food", date = 20261001)
            txn(session, "b", "p-cafe", "c-food", date = 20261002)
            txn(session, "c", "p-cafe", "c-fun", date = 20261003)
            txn(session, "d", "p-cafe", "c-old", date = 20261004)
            txn(session, "new", "p-cafe", null, date = 20261010)
            val suggester = CategorySuggester(session)
            assertEquals(listOf("c-food", "c-fun"), suggester.forTransaction("new"))
            assertEquals(listOf("c-food", "c-fun"), suggester.index().forPayee(" cafe ", income = false))
        }
    }

    @Test
    fun aMatchingRuleComesFirst() {
        db { session ->
            txn(session, "a", "p-cafe", "c-food", date = 20261001)
            txn(session, "new", "p-cafe", null, date = 20261010)
            RulesBook(session).insertPayeeCategoryRule("p-cafe", "c-fun")
            assertEquals(listOf("c-fun", "c-food"), CategorySuggester(session).forTransaction("new"))
        }
    }

    @Test
    fun aNewPayeeGetsTheCategoriesUsedLastForItsDirection() {
        db { session ->
            txn(session, "a", "p-cafe", "c-food", date = 20261001)
            txn(session, "b", "p-cafe", "c-fun", date = 20261005)
            txn(session, "pay", "p-work", "c-sal", date = 20261003, amount = 50_000)
            txn(session, "new", "p-shop", null, date = 20261010)
            txn(session, "refund", "p-shop", null, date = 20261010, amount = 500)
            val suggester = CategorySuggester(session)
            assertEquals(listOf("c-fun", "c-food"), suggester.forTransaction("new"))
            assertEquals(listOf("c-sal"), suggester.forTransaction("refund"))
            val index = suggester.index()
            assertEquals(listOf("c-fun", "c-food"), index.forPayee("Somewhere new", income = false))
            assertEquals(listOf("c-sal"), index.forPayee("", income = true))
        }
    }

    @Test
    fun anEmptyBudgetSuggestsNothing() {
        db { session ->
            txn(session, "new", "p-cafe", null, date = 20261010)
            assertEquals(emptyList<String>(), CategorySuggester(session).forTransaction("new"))
            assertEquals(emptyList<String>(), CategorySuggester(session).index().forPayee("Cafe", income = false))
        }
    }

    @Test
    fun theRegisterAndInboxTargetCarryTheirSuggestions() {
        db { session ->
            txn(session, "a", "p-cafe", "c-food", date = 20261001)
            txn(session, "new", "p-cafe", null, date = 20261010)
            val book = RegisterBook(session)
            assertEquals(listOf("c-food"), book.categoryTarget("new")!!.suggested)
            assertEquals(listOf("c-food"), book.read("checking")!!.suggestions.forPayee("Cafe", income = false))
        }
    }

    private fun txn(
        session: SqlSession,
        id: String,
        payee: String,
        category: String?,
        date: Int,
        amount: Long = -1_000,
    ) {
        session.exec(
            "INSERT INTO transactions (id, acct, amount, description, date, sort_order, category) VALUES (?, 'checking', ?, ?, ?, 0, ?)",
            listOf(id, amount, payee, date, category),
        )
    }

    private fun db(block: (SqlSession) -> Unit) {
        val file = File.createTempFile("suggest", ".sqlite")
        file.delete()
        try {
            JdbcSessionOpener().use(file) { session ->
                ActualSchema.create(session)
                session.exec("INSERT OR REPLACE INTO preferences (id, value) VALUES ('currency', 'USD')")
                session.exec("INSERT INTO category_groups (id, name, is_income, sort_order) VALUES ('g-inc', 'Income', 1, 0)")
                session.exec("INSERT INTO category_groups (id, name, is_income, sort_order) VALUES ('g-live', 'Living', 0, 1)")
                session.exec("INSERT INTO categories (id, name, is_income, cat_group) VALUES ('c-sal', 'Salary', 1, 'g-inc')")
                session.exec("INSERT INTO categories (id, name, is_income, cat_group) VALUES ('c-food', 'Groceries', 0, 'g-live')")
                session.exec("INSERT INTO categories (id, name, is_income, cat_group) VALUES ('c-fun', 'Fun', 0, 'g-live')")
                session.exec("INSERT INTO categories (id, name, is_income, cat_group, hidden) VALUES ('c-old', 'Old', 0, 'g-live', 1)")
                session.exec(
                    "INSERT INTO accounts (id, name, offbudget, type, sort_order) VALUES ('checking', 'Checking', 0, 'checking', 0)",
                )
                session.exec("INSERT INTO payees (id, name) VALUES ('p-cafe', 'Cafe')")
                session.exec("INSERT INTO payees (id, name) VALUES ('p-work', 'Work')")
                session.exec("INSERT INTO payees (id, name) VALUES ('p-shop', 'Shop')")
                block(session)
            }
        } finally {
            file.delete()
        }
    }
}
