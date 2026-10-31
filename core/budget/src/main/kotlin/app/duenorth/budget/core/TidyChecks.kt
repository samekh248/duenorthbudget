package app.duenorth.budget.core

import java.time.LocalDate

/**
 * The assistant's tidy-up checks: a payee that always gets the same category but has no rule for it, and one payee
 * kept under two names.
 */
internal object TidyChecks {
    const val RULE_KEY = "rule"
    const val LOOKALIKE_KEY = "lookalike"

    /** How far back the rule check looks. */
    const val RULE_LOOKBACK_DAYS = 180

    /** A rule is worth it once a payee has this many transactions, all in one category. */
    const val RULE_MIN = 4

    /** Names shorter than this, once tidied, are too short to call alike. */
    const val LOOKALIKE_MIN_LENGTH = 4

    fun read(
        session: SqlSession,
        today: LocalDate,
    ): List<Finding> = ruleIdeas(session, today) + lookalikes(session)

    private fun ruleIdeas(
        session: SqlSession,
        today: LocalDate,
    ): List<Finding> {
        val since = today.minusDays(RULE_LOOKBACK_DAYS.toLong()).let { it.year * 10000 + it.monthValue * 100 + it.dayOfMonth }
        val ruled = payeesWithCategoryRules(session)
        return session
            .query(
                """
                SELECT t.description AS payee, p.name AS name, COUNT(*) AS n,
                    COUNT(DISTINCT t.category) AS kinds, MIN(t.category) AS category, MIN(c.name) AS category_name,
                    SUM(CASE WHEN t.category IS NULL THEN 1 ELSE 0 END) AS blank
                FROM transactions t
                JOIN payees p ON p.id = t.description
                LEFT JOIN categories c ON c.id = t.category
                WHERE IFNULL(t.tombstone, 0) = 0 AND IFNULL(t.isParent, 0) = 0 AND t.date >= ?
                    AND p.transfer_acct IS NULL AND IFNULL(p.tombstone, 0) = 0
                    AND (p.learn_categories IS NULL OR p.learn_categories != 0)
                GROUP BY t.description
                ORDER BY n DESC, p.name
                """.trimIndent(),
                listOf(since),
            ).mapNotNull { row ->
                val payee = row.str("payee") ?: return@mapNotNull null
                val category = row.str("category") ?: return@mapNotNull null
                val count = row.long("n").toInt()
                if (payee in ruled || row.long("kinds") != 1L || row.long("blank") > 0 || count < RULE_MIN) return@mapNotNull null
                val categoryName = row.str("category_name").orEmpty()
                Finding(
                    key = AssistantBook.key(RULE_KEY, payee, category),
                    kind = FindingKind.RULE_IDEA,
                    sure = FindingSure.HIGH,
                    headline = AssistantCopy.ruleIdea(row.str("name").orEmpty(), categoryName, count),
                    sides = emptyList(),
                    reasons = emptyList(),
                    actions = listOf(FindingAction.MakeRule(payee, category), FindingAction.Dismiss(AssistantCopy.NO_THANKS)),
                    accountId = "",
                )
            }
    }

    /** Payees some rule already sets a category for. */
    private fun payeesWithCategoryRules(session: SqlSession): Set<String> =
        RulesBook(session)
            .loadRules()
            .filter { rule -> rule.actions.toString().contains("\"category\"") }
            .flatMap { rule ->
                rule.conditions.mapNotNull { condition ->
                    val text = condition.toString()
                    if ("\"description\"" !in text && "\"payee\"" !in text) return@mapNotNull null
                    Regex("\"value\":\"([^\"]+)\"").find(text)?.groupValues?.get(1)
                }
            }.toSet()

    private fun lookalikes(session: SqlSession): List<Finding> {
        val payees =
            session
                .query(
                    """
                    SELECT p.id AS id, p.name AS name, COUNT(t.id) AS n
                    FROM payees p
                    JOIN transactions t ON t.description = p.id AND IFNULL(t.tombstone, 0) = 0
                    WHERE IFNULL(p.tombstone, 0) = 0 AND p.transfer_acct IS NULL AND IFNULL(p.name, '') != ''
                    GROUP BY p.id
                    """.trimIndent(),
                ).map { Payee(it.str("id").orEmpty(), it.str("name").orEmpty(), it.long("n").toInt()) }
        return payees
            .groupBy { tidy(it.name) }
            .filter { (name, group) -> name.length >= LOOKALIKE_MIN_LENGTH && group.size > 1 }
            .values
            .flatMap { group ->
                // The name used most stays; the others merge into it.
                val keep = group.sortedWith(compareByDescending<Payee> { it.count }.thenBy { it.name }).first()
                group.filter { it != keep }.map { other ->
                    Finding(
                        key = AssistantBook.key(LOOKALIKE_KEY, other.id, keep.id),
                        kind = FindingKind.LOOKALIKE_PAYEE,
                        sure = FindingSure.MAYBE,
                        headline = AssistantCopy.lookalike(other.name, keep.name),
                        sides = emptyList(),
                        reasons = listOf(AssistantCopy.usedTimes(other.name, other.count), AssistantCopy.usedTimes(keep.name, keep.count)),
                        actions = listOf(FindingAction.MergePayees(other.id, keep.name), FindingAction.Dismiss(AssistantCopy.NOT_THE_SAME)),
                        accountId = "",
                    )
                }
            }
    }

    /** A name with case, store numbers, punctuation and trailing digits taken away: "COSTCO #1123" is "costco". */
    internal fun tidy(name: String): String =
        name
            .lowercase()
            .replace(Regex("#\\s*\\d+"), "")
            .replace(Regex("[^\\p{L}\\p{N}]"), "")
            .trimEnd { it.isDigit() }

    private data class Payee(
        val id: String,
        val name: String,
        val count: Int,
    )
}
