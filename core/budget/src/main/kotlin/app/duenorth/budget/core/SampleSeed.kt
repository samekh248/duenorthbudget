package app.duenorth.budget.core

import java.time.LocalDate
import java.time.YearMonth

/** Writes the rows sample budgets are seeded with, straight into a new budget file's tables. */
object SampleSeed {
    fun prefs(
        session: SqlSession,
        currency: String,
    ) {
        session.exec(
            "INSERT OR REPLACE INTO preferences (id, value) VALUES (?, ?)",
            listOf("budgetType", "envelope"),
        )
        session.exec(
            "INSERT OR REPLACE INTO preferences (id, value) VALUES (?, ?)",
            listOf("currency", currency),
        )
    }

    fun group(
        session: SqlSession,
        id: String,
        name: String,
        income: Boolean = false,
        order: Int,
    ) {
        session.exec(
            """
            INSERT INTO category_groups (id, name, is_income, sort_order, tombstone)
            VALUES (?, ?, ?, ?, 0)
            """.trimIndent(),
            listOf(id, name, if (income) 1 else 0, order.toDouble()),
        )
    }

    fun category(
        session: SqlSession,
        id: String,
        name: String,
        groupId: String,
        income: Boolean = false,
        order: Int,
    ) {
        session.exec(
            """
            INSERT INTO categories (id, name, is_income, cat_group, sort_order, tombstone)
            VALUES (?, ?, ?, ?, ?, 0)
            """.trimIndent(),
            listOf(id, name, if (income) 1 else 0, groupId, order.toDouble()),
        )
    }

    fun account(
        session: SqlSession,
        id: String,
        name: String,
        offBudget: Boolean = false,
        type: String,
        order: Int,
        bankLinked: Boolean = false,
    ) {
        session.exec(
            """
            INSERT INTO accounts (
                id, name, offbudget, closed, type, sort_order, tombstone,
                account_id, account_sync_source
            ) VALUES (?, ?, ?, 0, ?, ?, 0, ?, ?)
            """.trimIndent(),
            listOf(
                id,
                name,
                if (offBudget) 1 else 0,
                type,
                order.toDouble(),
                if (bankLinked) "bank-$id" else null,
                if (bankLinked) "simpleFin" else null,
            ),
        )
    }

    fun payee(
        session: SqlSession,
        id: String,
        name: String,
    ) {
        session.exec(
            "INSERT INTO payees (id, name, tombstone) VALUES (?, ?, 0)",
            listOf(id, name),
        )
    }

    fun budget(
        session: SqlSession,
        id: String,
        month: Int,
        categoryId: String,
        amount: Long,
        carryover: Boolean = false,
    ) {
        session.exec(
            """
            INSERT INTO zero_budgets (id, month, category, amount, carryover)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            listOf(id, month, categoryId, amount, if (carryover) 1 else 0),
        )
    }

    fun txn(
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
        cleared: Boolean = true,
        note: String? = null,
    ) {
        session.exec(
            """
            INSERT INTO transactions (
                id, isParent, isChild, parent_id, acct, category, amount, description, notes,
                date, transferred_id, sort_order, cleared, reconciled, tombstone
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0)
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
                note,
                date,
                transfer,
                sort,
                if (cleared) 1 else 0,
            ),
        )
    }

    fun date(
        month: YearMonth,
        day: Int,
    ): Int = month.atDay(day.coerceIn(1, month.lengthOfMonth())).toActualDate()

    fun LocalDate.toActualDate(): Int = year * 10000 + monthValue * 100 + dayOfMonth
}
