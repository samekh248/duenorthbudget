package app.duenorth.budget.core

/**
 * The categories a transaction most likely belongs in, for the suggested group atop a category list:
 * the ones its payee has been filed under, most used first, or failing any, the categories used last
 * for money moving the same way.
 */
data class CategorySuggestions(
    /** Category ids by payee name (trimmed, lower case), most used first. */
    val byPayee: Map<String, List<String>> = emptyMap(),
    /** Category ids last used on money going out, newest first. */
    val recentSpending: List<String> = emptyList(),
    /** Category ids last used on money coming in, newest first. */
    val recentIncome: List<String> = emptyList(),
) {
    /** At most [LIMIT] ids for [payee]: the payee's history, else the recent ones for its direction. */
    fun forPayee(
        payee: String,
        income: Boolean,
    ): List<String> {
        val history = byPayee[key(payee)].orEmpty()
        return (history.ifEmpty { if (income) recentIncome else recentSpending }).take(LIMIT)
    }

    companion object {
        const val LIMIT = 3

        val EMPTY = CategorySuggestions()

        fun key(payee: String): String = payee.trim().lowercase()
    }
}

/** Reads [CategorySuggestions] from the budget's transactions and rules. Hidden and deleted categories never show. */
class CategorySuggester(
    private val session: SqlSession,
) {
    fun index(): CategorySuggestions {
        val visible = visibleCategories()
        val uses =
            session
                .query(
                    """
                    SELECT p.name AS payee, t.category AS category, COUNT(*) AS uses, MAX(t.date) AS last
                    FROM transactions t
                    JOIN payees p ON p.id = t.description
                    WHERE IFNULL(t.tombstone, 0) = 0 AND IFNULL(t.isParent, 0) = 0
                      AND t.category IS NOT NULL
                      AND IFNULL(p.tombstone, 0) = 0 AND p.transfer_acct IS NULL
                    GROUP BY p.id, t.category
                    """.trimIndent(),
                ).mapNotNull { row ->
                    val category = row.str("category")?.takeIf { it in visible } ?: return@mapNotNull null
                    val payee = row.str("payee")?.let(CategorySuggestions::key)?.ifBlank { null } ?: return@mapNotNull null
                    Use(payee, category, row.long("uses"), row.long("last"))
                }
        val byPayee =
            uses
                .groupBy { it.payee }
                .mapValues { (_, rows) -> ranked(rows).take(CategorySuggestions.LIMIT) }
        return CategorySuggestions(
            byPayee = byPayee,
            recentSpending = recent(income = false, visible),
            recentIncome = recent(income = true, visible),
        )
    }

    /**
     * Suggestions for the saved transaction [transactionId]: the category its rules would set, then its
     * payee's history, else the recent categories for its direction.
     */
    fun forTransaction(transactionId: String): List<String> {
        val txn =
            session
                .query(
                    """
                    SELECT t.acct AS acct, t.description AS payee_id, p.name AS payee, t.amount AS amount,
                           t.notes AS notes, t.date AS date
                    FROM transactions t
                    LEFT JOIN payees p ON p.id = t.description
                    WHERE t.id = ? AND IFNULL(t.tombstone, 0) = 0
                    """.trimIndent(),
                    listOf(transactionId),
                ).firstOrNull() ?: return emptyList()
        val visible = visibleCategories()
        val payeeId = txn.str("payee_id")
        val amount = txn.long("amount")
        val ruled =
            runCatching {
                RulesBook(session)
                    .applyRules(
                        RuleTransactionView(
                            account = txn.str("acct").orEmpty(),
                            payee = payeeId,
                            payeeName = txn.str("payee").orEmpty(),
                            category = null,
                            amount = amount,
                            notes = txn.str("notes"),
                            dateIso = ScheduleDates.toIso(txn.long("date").toInt()),
                        ),
                    ).category
            }.getOrNull()
        val history =
            payeeId
                ?.let { id ->
                    session
                        .query(
                            """
                            SELECT category, COUNT(*) AS uses, MAX(date) AS last
                            FROM transactions
                            WHERE description = ? AND id <> ? AND category IS NOT NULL
                              AND IFNULL(tombstone, 0) = 0 AND IFNULL(isParent, 0) = 0
                            GROUP BY category
                            """.trimIndent(),
                            listOf(id, transactionId),
                        ).mapNotNull { row ->
                            val category = row.str("category") ?: return@mapNotNull null
                            Use("", category, row.long("uses"), row.long("last"))
                        }
                }.orEmpty()
                .filter { it.category in visible }
                .let(::ranked)
        val learned = (listOfNotNull(ruled?.takeIf { it in visible }) + history).distinct()
        return learned.ifEmpty { recent(income = amount > 0, visible) }.take(CategorySuggestions.LIMIT)
    }

    private fun recent(
        income: Boolean,
        visible: Set<String>,
    ): List<String> =
        session
            .query(
                """
                SELECT category, MAX(date) AS last
                FROM transactions
                WHERE category IS NOT NULL AND ${if (income) "amount > 0" else "amount < 0"}
                  AND IFNULL(tombstone, 0) = 0 AND IFNULL(isParent, 0) = 0
                GROUP BY category
                ORDER BY last DESC
                LIMIT ?
                """.trimIndent(),
                listOf(CategorySuggestions.LIMIT + HIDDEN_SLACK),
            ).mapNotNull { row -> row.str("category")?.takeIf { it in visible } }
            .take(CategorySuggestions.LIMIT)

    private fun visibleCategories(): Set<String> =
        session
            .query(
                """
                SELECT c.id AS id
                FROM categories c
                LEFT JOIN category_groups g ON g.id = c.cat_group
                WHERE IFNULL(c.tombstone, 0) = 0 AND IFNULL(c.hidden, 0) = 0 AND IFNULL(g.hidden, 0) = 0
                """.trimIndent(),
            ).mapNotNull { it.str("id") }
            .toSet()

    /** Most used first; a tie goes to the one used last. */
    private fun ranked(uses: List<Use>): List<String> =
        uses
            .groupBy { it.category }
            .map { (category, rows) -> Use("", category, rows.sumOf { it.uses }, rows.maxOf { it.last }) }
            .sortedWith(compareByDescending<Use> { it.uses }.thenByDescending { it.last })
            .map { it.category }

    private data class Use(
        val payee: String,
        val category: String,
        val uses: Long,
        val last: Long,
    )

    private companion object {
        /** How many extra rows the recent read takes, so hidden categories among them do not leave it short. */
        const val HIDDEN_SLACK = 20
    }
}
