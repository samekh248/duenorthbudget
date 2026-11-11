package app.duenorth.budget.core

import java.time.YearMonth

object ReviewCopy {
    const val INCOME = "income"
    const val SPENDING = "spending"
    const val NET_WORTH = "net worth"
    const val ON_BUDGET = ShellCopy.ON_BUDGET
    const val OFF_BUDGET = ShellCopy.OFF_BUDGET
    const val TOTAL = "total"
    const val NO_ACCOUNTS = "no accounts yet"
    const val INCLUDE_OFF_BUDGET = "include off budget"
}

data class ReviewCategoryRow(
    val id: String,
    val name: String,
    val spentMinor: Long,
)

data class MonthReviewPage(
    val month: YearMonth,
    val currency: CurrencySpec,
    val incomeMinor: Long,
    val categories: List<ReviewCategoryRow>,
)

data class ReviewTransactionRow(
    val id: String,
    val date: Int,
    val payee: String,
    val amountMinor: Long,
    /** The account the transaction sits in, so a tap can open its register. */
    val accountId: String = "",
    /** The row the register lists it as: a split's part shows there as its parent. */
    val registerId: String = id,
)

data class CategoryMonthPage(
    val categoryId: String,
    val categoryName: String,
    val month: YearMonth,
    val currency: CurrencySpec,
    val totalMinor: Long,
    val rows: List<ReviewTransactionRow>,
)

data class NetWorthPage(
    val currency: CurrencySpec,
    val onBudgetMinor: Long,
    val offBudgetMinor: Long,
    val includeOffBudget: Boolean,
) {
    val totalMinor: Long
        get() = onBudgetMinor + if (includeOffBudget) offBudgetMinor else 0L
}

object ReviewReader {
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

    private const val NOT_ON_BUDGET_TRANSFER =
        """
        NOT (
            t.transferred_id IS NOT NULL
            AND EXISTS (
                SELECT 1
                FROM transactions o
                JOIN accounts src ON src.id = t.acct AND IFNULL(src.tombstone, 0) = 0
                JOIN accounts dst ON dst.id = o.acct AND IFNULL(dst.tombstone, 0) = 0
                WHERE o.id = t.transferred_id
                    AND IFNULL(o.tombstone, 0) = 0
                    AND IFNULL(src.offbudget, 0) = 0
                    AND IFNULL(dst.offbudget, 0) = 0
            )
        )
        """

    fun monthReview(
        session: SqlSession,
        month: YearMonth,
    ): MonthReviewPage {
        val currency = currency(session)
        val target = month.toActualMonth()
        val categories =
            session.query(
                """
                SELECT id, name, is_income
                FROM categories
                WHERE IFNULL(tombstone, 0) = 0
                """.trimIndent(),
            )
        val spent = spentForMonth(session, target)
        val income =
            categories
                .filter { it.bool("is_income") }
                .sumOf { spent[it.str("id").orEmpty()] ?: 0L }
        val expenseRows =
            categories
                .filter { !it.bool("is_income") }
                .mapNotNull { row ->
                    val id = row.str("id").orEmpty()
                    val raw = spent[id] ?: return@mapNotNull null
                    if (raw == 0L) return@mapNotNull null
                    ReviewCategoryRow(
                        id = id,
                        name = row.str("name").orEmpty(),
                        spentMinor = raw,
                    )
                }.sortedByDescending { kotlin.math.abs(it.spentMinor) }
        return MonthReviewPage(
            month = month,
            currency = currency,
            incomeMinor = income,
            categories = expenseRows,
        )
    }

    fun categoryMonth(
        session: SqlSession,
        categoryId: String,
        month: YearMonth,
    ): CategoryMonthPage? {
        val category =
            session
                .query(
                    """
                    SELECT id, name
                    FROM categories
                    WHERE id = ? AND IFNULL(tombstone, 0) = 0
                    """.trimIndent(),
                    listOf(categoryId),
                ).firstOrNull()
                ?: return null
        val target = month.toActualMonth()
        val rows =
            session
                .query(
                    """
                    SELECT t.id AS id, t.date AS date, t.amount AS amount, COALESCE(p.name, '') AS payee, t.acct AS acct,
                        p.transfer_acct AS transfer_acct, COALESCE(target.name, '') AS transfer_name,
                        CASE WHEN IFNULL(t.isChild, 0) = 1 AND t.parent_id IS NOT NULL THEN t.parent_id ELSE t.id END
                            AS register_id
                    FROM transactions t
                    JOIN accounts a ON a.id = t.acct AND IFNULL(a.tombstone, 0) = 0
                    LEFT JOIN payees p ON p.id = t.description AND IFNULL(p.tombstone, 0) = 0
                    LEFT JOIN accounts target ON target.id = p.transfer_acct
                    WHERE $alive
                        AND t.category = ?
                        AND t.date / 100 = ?
                        AND IFNULL(a.offbudget, 0) = 0
                        AND $NOT_ON_BUDGET_TRANSFER
                    ORDER BY t.date DESC, t.sort_order DESC, t.id DESC
                    """.trimIndent(),
                    listOf(categoryId, target),
                ).map { row ->
                    val amount = row.long("amount")
                    ReviewTransactionRow(
                        id = row.str("id").orEmpty(),
                        date = row.long("date").toInt(),
                        // A transfer's payee has no name of its own; label it as the register does.
                        payee =
                            if (row.str("transfer_acct") != null) {
                                RegisterEntry.transferLabel(
                                    amount,
                                    row.str("transfer_name").orEmpty().ifBlank { row.str("payee").orEmpty() },
                                )
                            } else {
                                row.str("payee").orEmpty().ifBlank { ShellCopy.NO_PAYEE }
                            },
                        amountMinor = amount,
                        accountId = row.str("acct").orEmpty(),
                        registerId = row.str("register_id") ?: row.str("id").orEmpty(),
                    )
                }
        val total = rows.sumOf { it.amountMinor }
        return CategoryMonthPage(
            categoryId = categoryId,
            categoryName = category.str("name").orEmpty(),
            month = month,
            currency = currency(session),
            totalMinor = total,
            rows = rows,
        )
    }

    fun netWorth(
        session: SqlSession,
        includeOffBudget: Boolean,
    ): NetWorthPage {
        val currency = currency(session)
        val balances =
            session
                .query(
                    """
                    SELECT a.id AS id, IFNULL(a.offbudget, 0) AS offbudget, COALESCE(SUM(t.amount), 0) AS balance
                    FROM accounts a
                    LEFT JOIN transactions t
                        ON t.acct = a.id
                        AND $alive
                    WHERE IFNULL(a.tombstone, 0) = 0
                    GROUP BY a.id, a.offbudget
                    """.trimIndent(),
                )
        var onBudget = 0L
        var offBudget = 0L
        balances.forEach { row ->
            val balance = row.long("balance")
            if (row.bool("offbudget")) {
                offBudget += balance
            } else {
                onBudget += balance
            }
        }
        return NetWorthPage(
            currency = currency,
            onBudgetMinor = onBudget,
            offBudgetMinor = offBudget,
            includeOffBudget = includeOffBudget,
        )
    }

    private fun spentForMonth(
        session: SqlSession,
        month: Int,
    ): Map<String, Long> {
        val spent = HashMap<String, Long>()
        session
            .query(
                """
                SELECT t.category AS category, SUM(t.amount) AS spent
                FROM transactions t
                JOIN accounts a ON a.id = t.acct AND IFNULL(a.tombstone, 0) = 0
                WHERE $alive
                    AND t.category IS NOT NULL
                    AND t.date / 100 = ?
                    AND IFNULL(a.offbudget, 0) = 0
                    AND $NOT_ON_BUDGET_TRANSFER
                GROUP BY t.category
                """.trimIndent(),
                listOf(month),
            ).forEach { row ->
                spent[row.str("category").orEmpty()] = row.long("spent")
            }
        return spent
    }

    private fun currency(session: SqlSession): CurrencySpec = BudgetCurrency.read(session)
}
