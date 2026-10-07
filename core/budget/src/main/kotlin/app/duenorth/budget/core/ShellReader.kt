package app.duenorth.budget.core

import java.time.LocalDate
import java.time.YearMonth

object ShellReader {
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

    fun read(
        session: SqlSession,
        metadata: BudgetMetadata,
        today: LocalDate,
    ): MonthShell {
        val preferences =
            session.query("SELECT id, value FROM preferences").associate { row ->
                row.str("id").orEmpty() to row.str("value").orEmpty()
            }
        val mode =
            if (preferences["budgetType"] == "tracking") {
                BudgetMode.TRACKING
            } else {
                BudgetMode.ENVELOPE
            }
        val currency = Currencies.byCode(preferences["currency"].orEmpty()) ?: Currencies.byCode("USD")!!
        val month = YearMonth.from(today)
        val groups =
            session.query(
                """
                SELECT id, name, is_income, sort_order
                FROM category_groups
                WHERE IFNULL(tombstone, 0) = 0
                ORDER BY sort_order, id
                """.trimIndent(),
            )
        val categories =
            session.query(
                """
                SELECT id, is_income, cat_group
                FROM categories
                WHERE IFNULL(tombstone, 0) = 0
                """.trimIndent(),
            )
        val spent = HashMap<Pair<Int, String>, Long>()
        session
            .query(
                """
                SELECT t.category AS category, t.date / 100 AS month, SUM(t.amount) AS spent
                FROM transactions t
                WHERE $alive
                    AND t.category IS NOT NULL
                    AND t.date IS NOT NULL
                    AND NOT EXISTS (
                        SELECT 1 FROM accounts off
                        WHERE off.id = t.acct AND IFNULL(off.offbudget, 0) = 1
                    )
                GROUP BY t.category, t.date / 100
                """.trimIndent(),
            ).forEach { row ->
                spent[row.long("month").toInt() to row.str("category").orEmpty()] = row.long("spent")
            }
        if (mode == BudgetMode.ENVELOPE) {
            applyCardMoves(session, spent)
        }
        val table = if (mode == BudgetMode.ENVELOPE) "zero_budgets" else "reflect_budgets"
        val assignments =
            session.query(
                "SELECT month, category, amount, carryover FROM $table",
            )
        val buffers =
            if (mode == BudgetMode.ENVELOPE) {
                session.query("SELECT id, buffered FROM zero_budget_months").associate { row ->
                    row.long("id").toInt() to row.long("buffered")
                }
            } else {
                emptyMap()
            }
        val figures =
            ActualMonthMath.project(
                mode = mode,
                month = month,
                groups =
                    groups.map { row ->
                        ActualMonthMath.GroupFact(
                            id = row.str("id").orEmpty(),
                            isIncome = row.bool("is_income"),
                            sortOrder = row.long("sort_order").toDouble(),
                        )
                    },
                categories =
                    categories.map { row ->
                        ActualMonthMath.CategoryFact(
                            id = row.str("id").orEmpty(),
                            groupId = row.str("cat_group").orEmpty(),
                            isIncome = row.bool("is_income"),
                        )
                    },
                spentByCategoryMonth = spent,
                assignments =
                    assignments.map { row ->
                        ActualMonthMath.AssignmentFact(
                            month = row.long("month").toInt(),
                            categoryId = row.str("category").orEmpty(),
                            amount = row.long("amount"),
                            carryover = row.bool("carryover"),
                        )
                    },
                buffers = buffers,
            )
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
        return MonthShell(
            budgetId = metadata.id,
            budgetName = metadata.budgetName,
            mode = mode,
            currency = currency,
            month = month,
            headerLabel = figures.headerLabel,
            headerMinor = figures.headerMinor,
            groups =
                groups
                    .filter { !it.bool("is_income") }
                    .map { row ->
                        val id = row.str("id").orEmpty()
                        GroupRow(
                            id = id,
                            name = row.str("name").orEmpty(),
                            availableMinor = figures.groupAvailable[id] ?: 0L,
                        )
                    },
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
                    val payee = row.str("payee").orEmpty().ifBlank { ShellCopy.NO_PAYEE }
                    InboxRow(
                        id = row.str("id").orEmpty(),
                        payee = payee,
                        amountMinor = row.long("amount"),
                    )
                },
        )
    }

    /**
     * Envelope-only. Adds the negation of card spending and of on-budget card payments
     * into the payment category's spent. See specs/003-account-register/research.md R3.
     */
    private fun applyCardMoves(
        session: SqlSession,
        spent: MutableMap<Pair<Int, String>, Long>,
    ) {
        val prefix = ActualSchema.CARD_PAYMENT_PREFIX
        val queries =
            listOf(
                """
                SELECT t.date / 100 AS month, link.value AS payment, SUM(-t.amount) AS moved
                FROM transactions t
                JOIN accounts card
                    ON card.id = t.acct
                    AND IFNULL(card.tombstone, 0) = 0
                    AND IFNULL(card.offbudget, 0) = 0
                    AND card.type = 'credit'
                JOIN preferences link
                    ON link.id = ('$prefix' || card.id)
                    AND IFNULL(link.value, '') != ''
                JOIN categories spend
                    ON spend.id = t.category
                    AND IFNULL(spend.tombstone, 0) = 0
                    AND IFNULL(spend.is_income, 0) = 0
                JOIN categories payment
                    ON payment.id = link.value
                    AND IFNULL(payment.tombstone, 0) = 0
                    AND IFNULL(payment.is_income, 0) = 0
                WHERE $alive
                    AND t.date IS NOT NULL
                    AND t.category != link.value
                    AND t.transferred_id IS NULL
                GROUP BY t.date / 100, link.value
                """.trimIndent(),
                """
                SELECT t.date / 100 AS month, link.value AS payment, SUM(-t.amount) AS moved
                FROM transactions t
                JOIN accounts card
                    ON card.id = t.acct
                    AND IFNULL(card.tombstone, 0) = 0
                    AND IFNULL(card.offbudget, 0) = 0
                    AND card.type = 'credit'
                JOIN preferences link
                    ON link.id = ('$prefix' || card.id)
                    AND IFNULL(link.value, '') != ''
                JOIN categories payment
                    ON payment.id = link.value
                    AND IFNULL(payment.tombstone, 0) = 0
                    AND IFNULL(payment.is_income, 0) = 0
                JOIN transactions other
                    ON other.id = t.transferred_id
                    AND IFNULL(other.tombstone, 0) = 0
                JOIN accounts partner
                    ON partner.id = other.acct
                    AND IFNULL(partner.tombstone, 0) = 0
                    AND IFNULL(partner.offbudget, 0) = 0
                WHERE $alive
                    AND t.date IS NOT NULL
                GROUP BY t.date / 100, link.value
                """.trimIndent(),
            )
        queries.forEach { sql ->
            session.query(sql).forEach { row ->
                val category = row.str("payment").orEmpty()
                if (category.isEmpty()) return@forEach
                val key = row.long("month").toInt() to category
                spent[key] = (spent[key] ?: 0L) + row.long("moved")
            }
        }
    }
}
