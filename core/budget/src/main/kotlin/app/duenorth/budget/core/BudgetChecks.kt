package app.duenorth.budget.core

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * The assistant's budget checks: this month's envelopes (overspent, under goal, and to budget, in envelope mode
 * only), and bills due soon that an account cannot cover.
 */
internal object BudgetChecks {
    const val OVERSPENT_KEY = "overspent"
    const val UNDERFUNDED_KEY = "below"
    const val TO_BUDGET_KEY = "tobudget"
    const val BILLS_KEY = "bills"

    /** How far ahead bills ahead looks. */
    const val BILLS_DAYS = 14

    fun read(
        session: SqlSession,
        today: LocalDate,
    ): List<Finding> = envelopes(session, today) + billsAhead(session, today)

    private fun envelopes(
        session: SqlSession,
        today: LocalDate,
    ): List<Finding> {
        if (!isEnvelope(session)) return emptyList()
        val month = YearMonth.from(today)
        val detailed = ShellReader.loadDetailed(session, month)
        val monthKey = "${month.year * 100 + month.monthValue}"
        val shown =
            detailed.groups
                .filter { !it.bool("is_income") && !it.bool("hidden") }
                .mapNotNull { it.str("id") }
                .toSet()
        val rows = detailed.categoryRows.filter { it.groupId in shown && !it.hidden }
        val overspent =
            rows.filter { it.availableMinor < 0 }.map { row ->
                Finding(
                    key = AssistantBook.key(OVERSPENT_KEY, monthKey, row.id, "${row.availableMinor}"),
                    kind = FindingKind.OVERSPENT,
                    sure = FindingSure.HIGH,
                    headline = AssistantCopy.overspent(row.name),
                    sides = emptyList(),
                    reasons = emptyList(),
                    actions = listOf(FindingAction.BudgetCategory(row.id), FindingAction.Dismiss(AssistantCopy.LATER)),
                    accountId = "",
                    figures =
                        listOf(
                            FindingFigure(AssistantCopy.BUDGETED, row.budgetedMinor),
                            FindingFigure(AssistantCopy.SPENT, row.spentMinor),
                            FindingFigure(AssistantCopy.AVAILABLE, row.availableMinor),
                        ),
                )
            }
        val underfunded =
            rows.mapNotNull { row ->
                val goal = row.goalMinor?.takeIf { it > 0 } ?: return@mapNotNull null
                val toward = if (row.longGoal) row.availableMinor else row.budgetedMinor
                if (toward >= goal || row.availableMinor < 0) return@mapNotNull null
                Finding(
                    key = AssistantBook.key(UNDERFUNDED_KEY, monthKey, row.id, "$toward"),
                    kind = FindingKind.UNDERFUNDED,
                    sure = FindingSure.HIGH,
                    headline = AssistantCopy.underfunded(row.name, row.longGoal),
                    sides = emptyList(),
                    reasons = emptyList(),
                    actions = listOf(FindingAction.BudgetCategory(row.id), FindingAction.Dismiss(AssistantCopy.LATER)),
                    accountId = "",
                    figures =
                        listOf(
                            FindingFigure(if (row.longGoal) AssistantCopy.AVAILABLE else AssistantCopy.BUDGETED, toward),
                            FindingFigure(AssistantCopy.GOAL, goal),
                        ),
                )
            }
        val left = detailed.figures.headerMinor
        val toBudget =
            if (left == 0L) {
                emptyList()
            } else {
                val name = month.month.getDisplayName(TextStyle.FULL, Locale.US)
                listOf(
                    Finding(
                        key = AssistantBook.key(TO_BUDGET_KEY, monthKey, "$left"),
                        kind = FindingKind.TO_BUDGET,
                        sure = FindingSure.HIGH,
                        headline = AssistantCopy.toBudget(name, over = left < 0),
                        sides = emptyList(),
                        reasons = emptyList(),
                        actions =
                            if (left < 0) {
                                listOf(FindingAction.Dismiss(AssistantCopy.GOT_IT))
                            } else {
                                listOf(FindingAction.HoldForNext, FindingAction.Dismiss(AssistantCopy.LATER))
                            },
                        accountId = "",
                        figures = listOf(FindingFigure(AssistantCopy.TO_BUDGET, left)),
                    ),
                )
            }
        return overspent + underfunded + toBudget
    }

    private fun isEnvelope(session: SqlSession): Boolean =
        session
            .query("SELECT value FROM preferences WHERE id = ?", listOf("budgetType"))
            .firstOrNull()
            ?.str("value") != "tracking"

    /**
     * Each budget account that has money now, walked through the schedules due in the next [BILLS_DAYS] days: the
     * first day its balance would go below zero, and how low it gets.
     */
    private fun billsAhead(
        session: SqlSession,
        today: LocalDate,
    ): List<Finding> {
        val todayInt = today.toInt()
        val end = today.plusDays(BILLS_DAYS.toLong()).toInt()
        val due =
            SchedulesBook(session, { today })
                .listAll()
                .filter { !it.completed && it.accountId.isNotBlank() }
                .flatMap { schedule ->
                    schedule.dates.filter { it in todayInt..end }.map {
                        schedule.accountId to
                            (it to schedule.amountMinor)
                    }
                }.groupBy({ it.first }, { it.second })
        if (due.isEmpty()) return emptyList()
        val accounts =
            session
                .query(
                    """
                    SELECT a.id AS id, a.name AS name,
                        (SELECT IFNULL(SUM(t.amount), 0) FROM transactions t
                         WHERE t.acct = a.id AND IFNULL(t.tombstone, 0) = 0 AND IFNULL(t.isParent, 0) = 0
                             AND t.date <= ?) AS balance
                    FROM accounts a
                    WHERE IFNULL(a.tombstone, 0) = 0 AND IFNULL(a.closed, 0) = 0 AND IFNULL(a.offbudget, 0) = 0
                    ORDER BY a.sort_order, a.id
                    """.trimIndent(),
                    listOf(todayInt),
                )
        return accounts.mapNotNull { row ->
            val id = row.str("id").orEmpty()
            val bills = due[id] ?: return@mapNotNull null
            val start = row.long("balance")
            // A card already owes; bills ahead is about money running out.
            if (start < 0) return@mapNotNull null
            var running = start
            var short: Int? = null
            var lowest = start
            // Money out before money in on the same day, the careful way round.
            bills.sortedWith(compareBy({ it.first }, { it.second })).forEach { (date, amount) ->
                running += amount
                if (running < 0 && short == null) short = date
                if (running < lowest) lowest = running
            }
            val day = short ?: return@mapNotNull null
            Finding(
                key = AssistantBook.key(BILLS_KEY, "$day", id, "$lowest"),
                kind = FindingKind.BILLS_AHEAD,
                sure = FindingSure.HIGH,
                headline = AssistantCopy.billsAhead(row.str("name").orEmpty(), RegisterEntry.registerDateLabel(day), BILLS_DAYS),
                sides = emptyList(),
                reasons = emptyList(),
                actions = listOf(FindingAction.OpenSchedules, FindingAction.Dismiss(AssistantCopy.LATER)),
                accountId = id,
                figures =
                    listOf(
                        FindingFigure(AssistantCopy.BALANCE_NOW, start),
                        FindingFigure(AssistantCopy.dueIn(BILLS_DAYS), bills.sumOf { it.second }),
                        FindingFigure(AssistantCopy.LOWEST, lowest),
                    ),
            )
        }
    }

    private fun LocalDate.toInt(): Int = year * 10000 + monthValue * 100 + dayOfMonth
}
