package app.duenorth.budget.core

import java.util.UUID

object BudgetBookStore {
    private val alive =
        """
        IFNULL(t.tombstone, 0) = 0
        AND IFNULL(t.isParent, 0) = 0
        AND NOT (
            IFNULL(t.isChild, 0) = 1
            AND EXISTS (
                SELECT 1 FROM transactions p
                WHERE p.id = t.parent_id AND IFNULL(p.tombstone, 0) = 1
            )
        )
        """.trimIndent()

    fun load(
        session: SqlSession,
        metadata: BudgetMetadata,
    ): BudgetBook {
        val preferences =
            session.query("SELECT id, value FROM preferences").associate { row ->
                row.str("id").orEmpty() to row.str("value").orEmpty()
            }
        val mode = if (preferences["budgetType"] == "tracking") BudgetMode.TRACKING else BudgetMode.ENVELOPE
        val currency = Currencies.byCode(preferences["currency"].orEmpty()) ?: Currencies.byCode("USD")!!
        val groups =
            session.query(
                """
                SELECT id, name, is_income, hidden, sort_order, tombstone
                FROM category_groups
                """.trimIndent(),
            )
        val categories =
            session.query(
                """
                SELECT id, name, is_income, hidden, cat_group, sort_order, tombstone
                FROM categories
                """.trimIndent(),
            )
        val spent =
            session
                .query(
                    """
                    SELECT t.category AS category, t.date / 100 AS month, SUM(t.amount) AS spent
                    FROM transactions t
                    WHERE $alive AND t.category IS NOT NULL AND t.date IS NOT NULL
                    GROUP BY t.category, t.date / 100
                    """.trimIndent(),
                ).associate { row ->
                    (row.long("month").toInt() to row.str("category").orEmpty()) to row.long("spent")
                }
        val categorized =
            session
                .query(
                    """
                    SELECT DISTINCT t.category AS category
                    FROM transactions t
                    WHERE $alive AND t.category IS NOT NULL
                    """.trimIndent(),
                ).mapNotNull { row -> row.str("category") }
                .toSet()
        val table = if (mode == BudgetMode.ENVELOPE) "zero_budgets" else "reflect_budgets"
        val assignments =
            session.query("SELECT month, category, amount, carryover FROM $table").map { row ->
                ActualMonthMath.AssignmentFact(
                    month = row.long("month").toInt(),
                    categoryId = row.str("category").orEmpty(),
                    amount = row.long("amount"),
                    carryover = row.bool("carryover"),
                )
            }
        val buffers =
            if (mode == BudgetMode.ENVELOPE) {
                session.query("SELECT id, buffered FROM zero_budget_months").associate { row ->
                    row.long("id").toInt() to row.long("buffered")
                }
            } else {
                emptyMap()
            }
        val accounts =
            session.query(
                """
                SELECT id, name, offbudget, closed, sort_order
                FROM accounts
                WHERE IFNULL(tombstone, 0) = 0
                ORDER BY offbudget, sort_order, name, id
                """.trimIndent(),
            )
        val balances =
            session
                .query(
                    """
                    SELECT t.acct AS acct, SUM(t.amount) AS balance
                    FROM transactions t
                    WHERE $alive AND t.acct IS NOT NULL
                    GROUP BY t.acct
                    """.trimIndent(),
                ).associate { row -> row.str("acct").orEmpty() to row.long("balance") }
        val inbox =
            session.query(
                """
                SELECT t.id AS id, t.amount AS amount, COALESCE(p.name, '') AS payee
                FROM transactions t
                JOIN accounts a
                    ON a.id = t.acct
                    AND IFNULL(a.tombstone, 0) = 0
                    AND IFNULL(a.offbudget, 0) = 0
                LEFT JOIN payees p
                    ON p.id = t.description
                    AND IFNULL(p.tombstone, 0) = 0
                WHERE $alive
                    AND t.category IS NULL
                    AND t.transferred_id IS NULL
                ORDER BY t.date DESC, t.sort_order DESC, t.id DESC
                """.trimIndent(),
            )
        return BudgetBook(
            id = metadata.id,
            name = metadata.budgetName,
            mode = mode,
            currency = currency,
            groups =
                groups.map { row ->
                    GroupRecord(
                        id = row.str("id").orEmpty(),
                        name = row.str("name").orEmpty(),
                        isIncome = row.bool("is_income"),
                        hidden = row.bool("hidden"),
                        sortOrder = row.dbl("sort_order"),
                        tombstone = row.bool("tombstone"),
                    )
                },
            categories =
                categories.map { row ->
                    CategoryRecord(
                        id = row.str("id").orEmpty(),
                        name = row.str("name").orEmpty(),
                        groupId = row.str("cat_group").orEmpty(),
                        isIncome = row.bool("is_income"),
                        hidden = row.bool("hidden"),
                        sortOrder = row.dbl("sort_order"),
                        tombstone = row.bool("tombstone"),
                    )
                },
            spent = spent,
            categorized = categorized,
            assignments = assignments.filter { it.amount != 0L || it.carryover },
            buffers = buffers.filterValues { it != 0L },
            accounts =
                accounts.map { row ->
                    val id = row.str("id").orEmpty()
                    AccountRow(
                        id = id,
                        name = row.str("name").orEmpty(),
                        offBudget = row.bool("offbudget"),
                        closed = row.bool("closed"),
                        balanceMinor = balances[id] ?: 0L,
                    )
                },
            inbox =
                inbox.map { row ->
                    InboxRow(
                        id = row.str("id").orEmpty(),
                        payee = row.str("payee").orEmpty().ifBlank { ShellCopy.NO_PAYEE },
                        amountMinor = row.long("amount"),
                    )
                },
        )
    }

    fun save(
        session: SqlSession,
        book: BudgetBook,
    ) {
        val table = if (book.mode == BudgetMode.ENVELOPE) "zero_budgets" else "reflect_budgets"
        session.transaction {
            saveGroups(session, book)
            saveCategories(session, book)
            saveAssignments(session, table, book)
            if (book.mode == BudgetMode.ENVELOPE) saveBuffers(session, book)
        }
    }

    private fun saveGroups(
        session: SqlSession,
        book: BudgetBook,
    ) {
        book.groups.forEach { group ->
            val existing =
                session.query(
                    "SELECT id FROM category_groups WHERE id = ?",
                    listOf(group.id),
                )
            if (existing.isEmpty()) {
                session.exec(
                    """
                    INSERT INTO category_groups
                        (id, name, is_income, hidden, sort_order, tombstone)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    listOf(group.id, group.name, group.isIncome, group.hidden, group.sortOrder, group.tombstone),
                )
            } else {
                session.exec(
                    """
                    UPDATE category_groups
                    SET name = ?, is_income = ?, hidden = ?, sort_order = ?, tombstone = ?
                    WHERE id = ?
                    """.trimIndent(),
                    listOf(group.name, group.isIncome, group.hidden, group.sortOrder, group.tombstone, group.id),
                )
            }
        }
    }

    private fun saveCategories(
        session: SqlSession,
        book: BudgetBook,
    ) {
        book.categories.forEach { category ->
            val existing = session.query("SELECT id FROM categories WHERE id = ?", listOf(category.id))
            if (existing.isEmpty()) {
                session.exec(
                    """
                    INSERT INTO categories
                        (id, name, is_income, hidden, cat_group, sort_order, tombstone)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    listOf(
                        category.id,
                        category.name,
                        category.isIncome,
                        category.hidden,
                        category.groupId,
                        category.sortOrder,
                        category.tombstone,
                    ),
                )
            } else {
                session.exec(
                    """
                    UPDATE categories
                    SET name = ?, is_income = ?, hidden = ?, cat_group = ?, sort_order = ?, tombstone = ?
                    WHERE id = ?
                    """.trimIndent(),
                    listOf(
                        category.name,
                        category.isIncome,
                        category.hidden,
                        category.groupId,
                        category.sortOrder,
                        category.tombstone,
                        category.id,
                    ),
                )
            }
        }
    }

    private fun saveAssignments(
        session: SqlSession,
        table: String,
        book: BudgetBook,
    ) {
        val keep = book.assignments.filter { it.amount != 0L || it.carryover }
        val existing =
            session.query("SELECT id, month, category FROM $table").associate { row ->
                (row.long("month").toInt() to row.str("category").orEmpty()) to row.str("id").orEmpty()
            }
        val seen = HashSet<Pair<Int, String>>()
        keep.forEach { fact ->
            val key = fact.month to fact.categoryId
            seen.add(key)
            val id = existing[key]
            if (id == null) {
                session.exec(
                    "INSERT INTO $table (id, month, category, amount, carryover) VALUES (?, ?, ?, ?, ?)",
                    listOf(UUID.randomUUID().toString(), fact.month, fact.categoryId, fact.amount, fact.carryover),
                )
            } else {
                session.exec(
                    "UPDATE $table SET amount = ?, carryover = ? WHERE id = ?",
                    listOf(fact.amount, fact.carryover, id),
                )
            }
        }
        existing.forEach { (key, id) ->
            if (key !in seen) session.exec("DELETE FROM $table WHERE id = ?", listOf(id))
        }
    }

    private fun saveBuffers(
        session: SqlSession,
        book: BudgetBook,
    ) {
        val keep = book.buffers.filterValues { it != 0L }
        val existing = session.query("SELECT id FROM zero_budget_months").map { it.long("id").toInt() }.toSet()
        keep.forEach { (month, amount) ->
            if (month in existing) {
                session.exec(
                    "UPDATE zero_budget_months SET buffered = ? WHERE id = ?",
                    listOf(amount, month),
                )
            } else {
                session.exec(
                    "INSERT INTO zero_budget_months (id, buffered) VALUES (?, ?)",
                    listOf(month, amount),
                )
            }
        }
        existing.filter { it !in keep.keys }.forEach { month ->
            session.exec("DELETE FROM zero_budget_months WHERE id = ?", listOf(month))
        }
    }
}

private fun SqlRow.dbl(column: String): Double {
    val value = values[column] ?: return 0.0
    return when (value) {
        is Double -> value
        is Float -> value.toDouble()
        is Number -> value.toDouble()
        else -> value.toString().toDoubleOrNull() ?: 0.0
    }
}
