package app.duenorth.budget.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * The assistant's schedule checks: a payment that comes every month with no schedule, a schedule whose date went by
 * with nothing posted, and a bill whose price changed.
 */
internal object ScheduleChecks {
    const val RECURRING_KEY = "recurring"
    const val MISSED_KEY = "missed"
    const val PRICE_KEY = "price"

    /** How far back the recurring check looks. */
    const val RECURRING_LOOKBACK_DAYS = 200

    /** A recurring payment needs this many monthly payments. */
    const val RECURRING_MIN = 3

    /** Each gap between payments, in days, to count as monthly. */
    val MONTHLY_GAP = 26..35

    /** Each payment within this share of the latest one's amount. */
    const val RECURRING_SHARE = 0.10

    /** The latest payment no older than this, or the payment has stopped. */
    const val RECURRING_RECENT_DAYS = 40

    /** A missed schedule older than this is left to the schedules page. */
    const val MISSED_DAYS = 60

    fun read(
        session: SqlSession,
        today: LocalDate,
    ): List<Finding> {
        val schedules = SchedulesBook(session, { today }).listAll().filter { !it.completed }
        return recurring(session, today, schedules) + missed(schedules, today) + priceChanges(session, schedules)
    }

    private fun recurring(
        session: SqlSession,
        today: LocalDate,
        schedules: List<ScheduleSummary>,
    ): List<Finding> {
        val scheduled = schedules.map { it.payee.lowercase() }.toSet()
        val since = today.minusDays(RECURRING_LOOKBACK_DAYS.toLong()).toInt()
        val rows =
            session
                .query(
                    """
                    SELECT t.id AS id, t.description AS payee, p.name AS name, t.amount AS amount, t.date AS date
                    FROM transactions t
                    JOIN payees p ON p.id = t.description
                    JOIN accounts a ON a.id = t.acct
                    WHERE IFNULL(t.tombstone, 0) = 0 AND IFNULL(t.isChild, 0) = 0 AND t.schedule IS NULL
                        AND p.transfer_acct IS NULL AND IFNULL(p.tombstone, 0) = 0
                        AND IFNULL(a.tombstone, 0) = 0 AND IFNULL(a.closed, 0) = 0
                        AND t.amount != 0 AND t.date >= ?
                    ORDER BY t.date, t.sort_order
                    """.trimIndent(),
                    listOf(since),
                ).map {
                    Payment(
                        it.str("id").orEmpty(),
                        it.str("payee").orEmpty(),
                        it.str("name").orEmpty(),
                        it.long("amount"),
                        it.long("date").toInt(),
                    )
                }
        val recent = today.minusDays(RECURRING_RECENT_DAYS.toLong()).toInt()
        return rows
            .filter { it.name.lowercase() !in scheduled }
            .groupBy { it.payeeId to (it.amount < 0) }
            .values
            .mapNotNull { group ->
                val run = monthlyRun(group) ?: return@mapNotNull null
                val last = run.last()
                if (last.date < recent) return@mapNotNull null
                var next = last.date.toLocalDate().plusMonths(1)
                while (next.isBefore(today)) next = next.plusMonths(1)
                val nextIso = ScheduleDates.toIso(next.toInt())
                Finding(
                    key = AssistantBook.key(RECURRING_KEY, last.payeeId, if (last.amount < 0) "out" else "in"),
                    kind = FindingKind.RECURRING,
                    sure = if (run.size > RECURRING_MIN) FindingSure.HIGH else FindingSure.MAYBE,
                    headline = AssistantCopy.recurring(last.name, run.size),
                    sides = emptyList(),
                    reasons = listOf(AssistantCopy.nextAround(RegisterEntry.registerDateLabel(next.toInt()))),
                    actions = listOf(FindingAction.MakeSchedule(last.id, nextIso), FindingAction.Dismiss(AssistantCopy.NOT_A_BILL)),
                    accountId = "",
                    figures = listOf(FindingFigure(AssistantCopy.LAST_PAID, last.amount)),
                )
            }
    }

    /** The latest payments of one payee that come about a month apart for similar amounts, or null. */
    private fun monthlyRun(group: List<Payment>): List<Payment>? {
        if (group.size < RECURRING_MIN) return null
        val run = mutableListOf(group.last())
        for (payment in group.dropLast(1).asReversed()) {
            val gap = ChronoUnit.DAYS.between(payment.date.toLocalDate(), run.first().date.toLocalDate()).toInt()
            if (gap !in MONTHLY_GAP) break
            if (abs(payment.amount - run.last().amount) > abs(run.last().amount) * RECURRING_SHARE) break
            run.add(0, payment)
        }
        return run.takeIf { it.size >= RECURRING_MIN }
    }

    private fun missed(
        schedules: List<ScheduleSummary>,
        today: LocalDate,
    ): List<Finding> {
        val todayInt = today.toInt()
        val oldest = today.minusDays(MISSED_DAYS.toLong()).toInt()
        return schedules
            .filter { it.nextDate in oldest until todayInt }
            .map { schedule ->
                Finding(
                    key = AssistantBook.key(MISSED_KEY, schedule.id, "${schedule.nextDate}"),
                    kind = FindingKind.MISSED_SCHEDULE,
                    sure = FindingSure.HIGH,
                    headline = AssistantCopy.missed(schedule.title, RegisterEntry.registerDateLabel(schedule.nextDate)),
                    sides = emptyList(),
                    reasons = listOfNotNull(schedule.accountName.ifBlank { null }),
                    actions =
                        listOf(
                            FindingAction.PostSchedule(schedule.id),
                            FindingAction.SkipSchedule(schedule.id),
                            FindingAction.Dismiss(AssistantCopy.LATER),
                        ),
                    accountId = schedule.accountId,
                    figures = listOf(FindingFigure(AssistantCopy.AMOUNT, schedule.amountMinor)),
                )
            }
    }

    /** A schedule for an exact amount whose last two payments were the same other amount. */
    private fun priceChanges(
        session: SqlSession,
        schedules: List<ScheduleSummary>,
    ): List<Finding> =
        schedules
            .filter { it.amountOp == "is" && it.amountMinor != 0L }
            .mapNotNull { schedule ->
                val paid =
                    session
                        .query(
                            """
                            SELECT amount FROM transactions
                            WHERE schedule = ? AND IFNULL(tombstone, 0) = 0 AND IFNULL(isChild, 0) = 0
                            ORDER BY date DESC, sort_order DESC
                            LIMIT 2
                            """.trimIndent(),
                            listOf(schedule.id),
                        ).map { it.long("amount") }
                if (paid.size < 2 || paid[0] != paid[1] || paid[0] == schedule.amountMinor) return@mapNotNull null
                val now = paid[0]
                Finding(
                    key = AssistantBook.key(PRICE_KEY, schedule.id, "$now"),
                    kind = FindingKind.PRICE_CHANGE,
                    sure = FindingSure.HIGH,
                    headline = AssistantCopy.priceChange(schedule.title),
                    sides = emptyList(),
                    reasons = emptyList(),
                    actions = listOf(FindingAction.UpdateScheduleAmount(schedule.id, now), FindingAction.Dismiss(AssistantCopy.KEEP_IT)),
                    accountId = schedule.accountId,
                    difference = now - schedule.amountMinor,
                    figures =
                        listOf(
                            FindingFigure(AssistantCopy.SCHEDULE_SAYS, schedule.amountMinor),
                            FindingFigure(AssistantCopy.LAST_TWO, now),
                        ),
                )
            }

    private data class Payment(
        val id: String,
        val payeeId: String,
        val name: String,
        val amount: Long,
        val date: Int,
    )

    private fun LocalDate.toInt(): Int = year * 10000 + monthValue * 100 + dayOfMonth

    private fun Int.toLocalDate(): LocalDate = LocalDate.of(this / 10000, this / 100 % 100, this % 100)
}
