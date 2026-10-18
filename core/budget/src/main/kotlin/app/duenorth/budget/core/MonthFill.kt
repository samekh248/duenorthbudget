package app.duenorth.budget.core

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor

/** The three ways to fill a month, as Actual names them. */
enum class MonthFillKind { COPY_LAST, AVERAGE, ZERO }

/** Auto-hold choices for an income category (`contracts/month-actions.md`). */
enum class AutoHoldChoice { ON, OFF_THIS_MONTH, OFF }

object MonthFillCopy {
    const val COPY_LAST = "copy last month"
    const val AVERAGE = "3 month average"
    const val ZERO = "set to zero"
    const val BREAKDOWN = "to budget breakdown"
    const val TO_BUDGET = "to budget"
    const val AUTO_HOLD = "auto-hold"
    const val NOTHING_TO_COPY = "nothing to copy"
    const val REPLACES = "this replaces amounts already budgeted this month"
    const val ZERO_CONFIRM = "this sets every budgeted amount this month to zero"
    const val ENVELOPE_ONLY = "only in envelope budgets"
    const val INCOME_ONLY = "only for income categories"
}

sealed interface MonthFillResult {
    data object Filled : MonthFillResult

    data object NothingToCopy : MonthFillResult

    /** Nothing written; call again with `confirmed = true`. */
    data class NeedsConfirm(
        val reason: String,
    ) : MonthFillResult

    data class Rejected(
        val reason: String,
    ) : MonthFillResult
}

/**
 * Month actions ported from Actual 26.8.1 `server/budget/actions.ts`:
 * `copyPreviousMonth`, `set3MonthAvg`, `setZero`, `transferCategory` to to-budget,
 * `setCategoryCarryover` and `resetIncomeCarryover`. Every write goes through the sync funnel.
 */
class MonthFillBook(
    private val session: SqlSession,
    private val today: LocalDate,
) {
    private val tracking: Boolean =
        session
            .query("SELECT value FROM preferences WHERE id = 'budgetType'")
            .firstOrNull()
            ?.str("value") == "tracking"
    private val table = if (tracking) "reflect_budgets" else "zero_budgets"

    fun fill(
        kind: MonthFillKind,
        month: YearMonth,
        confirmed: Boolean,
    ): MonthFillResult {
        val amounts = targets(kind, month)
        if (amounts.isEmpty()) return MonthFillResult.NothingToCopy
        if (!confirmed) {
            if (kind == MonthFillKind.ZERO) return MonthFillResult.NeedsConfirm(MonthFillCopy.ZERO_CONFIRM)
            if (amounts.keys.any { budgeted(month, it) != 0L }) return MonthFillResult.NeedsConfirm(MonthFillCopy.REPLACES)
        }
        session.transaction {
            amounts.forEach { (category, amount) -> setBudget(month, category, amount) }
        }
        return MonthFillResult.Filled
    }

    /** What [fill] would budget each category for [month]; writes nothing. Empty when there is nothing to copy. */
    fun targets(
        kind: MonthFillKind,
        month: YearMonth,
    ): Map<String, Long> =
        when (kind) {
            MonthFillKind.COPY_LAST -> copyTargets(month)
            MonthFillKind.AVERAGE -> averageTargets(month)
            MonthFillKind.ZERO -> zeroTargets()
        }

    /** Envelope only: moves [amountMinor] of the category's budget back to to-budget. */
    fun transferToBudget(
        month: YearMonth,
        categoryId: String,
        amountMinor: Long,
    ): MonthFillResult {
        if (tracking) return MonthFillResult.Rejected(MonthFillCopy.ENVELOPE_ONLY)
        if (amountMinor <= 0L) return MonthFillResult.Rejected(EnvelopeCopy.ENTER_AMOUNT)
        val available =
            ShellReader.loadDetailed(session, month).categories[categoryId]?.availableMinor
                ?: return MonthFillResult.Rejected(EnvelopeCopy.ENTER_AMOUNT)
        if (amountMinor > available) return MonthFillResult.Rejected(EnvelopeCopy.NOT_ENOUGH)
        session.transaction {
            setBudget(month, categoryId, budgeted(month, categoryId) - amountMinor)
            addMovementNote(month, categoryId, amountMinor)
        }
        return MonthFillResult.Filled
    }

    /** Envelope, income categories only. */
    fun setAutoHold(
        month: YearMonth,
        categoryId: String,
        choice: AutoHoldChoice,
    ): MonthFillResult {
        if (tracking) return MonthFillResult.Rejected(MonthFillCopy.ENVELOPE_ONLY)
        val income =
            session
                .query("SELECT is_income FROM categories WHERE id = ? AND IFNULL(tombstone, 0) = 0", listOf(categoryId))
                .firstOrNull()
                ?.bool("is_income") == true
        if (!income) return MonthFillResult.Rejected(MonthFillCopy.INCOME_ONLY)
        session.transaction {
            when (choice) {
                AutoHoldChoice.ON -> createdMonthsFrom(month).forEach { setCarryover(it, categoryId, true) }
                AutoHoldChoice.OFF -> createdMonthsFrom(month).forEach { setCarryover(it, categoryId, false) }
                AutoHoldChoice.OFF_THIS_MONTH ->
                    session
                        .query("SELECT id FROM categories WHERE IFNULL(is_income, 0) = 1 AND IFNULL(tombstone, 0) = 0")
                        .mapNotNull { it.str("id") }
                        .forEach { setCarryover(month, it, false) }
            }
        }
        return MonthFillResult.Filled
    }

    /** `copyPreviousMonth`: last month's budget rows, minus hidden and (envelope) income. */
    private fun copyTargets(month: YearMonth): Map<String, Long> =
        session
            .query(
                """
                SELECT b.category AS category, b.amount AS amount, c.is_income AS is_income,
                       c.hidden AS hidden, g.hidden AS group_hidden
                FROM $table b
                LEFT JOIN categories c ON b.category = c.id
                LEFT JOIN category_groups g ON c.cat_group = g.id
                WHERE c.tombstone = 0 AND b.month = ?
                """.trimIndent(),
                listOf(month.minusMonths(1).toActualMonth()),
            ).filter { row ->
                !(row.bool("is_income") && !tracking) && !row.bool("hidden") && !row.bool("group_hidden")
            }.associate { it.str("category").orEmpty() to it.long("amount") }

    /** `set3MonthAvg`: visible categories in visible groups, average spending, sign flipped for expenses. */
    private fun averageTargets(month: YearMonth): Map<String, Long> =
        session
            .query(
                """
                SELECT c.id AS id, c.is_income AS is_income
                FROM categories c
                LEFT JOIN category_groups g ON c.cat_group = g.id
                WHERE c.tombstone = 0 AND c.hidden = 0 AND g.hidden = 0
                ORDER BY c.sort_order, c.id
                """.trimIndent(),
            ).filter { !(it.bool("is_income") && !tracking) }
            .associate { row ->
                val id = row.str("id").orEmpty()
                val average = average(month, id, AVERAGE_MONTHS)
                id to if (row.bool("is_income")) average else -average
            }

    /** `setZero`: every live category, hidden included, minus (envelope) income. */
    private fun zeroTargets(): Map<String, Long> =
        session
            .query("SELECT id, is_income FROM categories WHERE tombstone = 0 ORDER BY sort_order, id")
            .filter { !(it.bool("is_income") && !tracking) }
            .associate { it.str("id").orEmpty() to 0L }

    /** `getCategoryAverage`: up to [maxMonths] before [month], stopping before the first activity. */
    internal fun average(
        month: YearMonth,
        categoryId: String,
        maxMonths: Int,
    ): Long {
        val current = YearMonth.from(today)
        val start = month.minusMonths(1).let { if (it >= current) current.minusMonths(1) else it }
        val first = firstActivity(categoryId, start)
        val months = ArrayList<YearMonth>()
        var cursor = start
        repeat(maxMonths) {
            if (first != null && cursor < first) return@repeat
            months.add(cursor)
            cursor = cursor.minusMonths(1)
        }
        if (months.isEmpty()) return 0L
        val sum = months.sumOf { spending(it, categoryId) }
        // JavaScript's Math.round: halves go up.
        return floor(sum.toDouble() / months.size + 0.5).toLong()
    }

    private fun firstActivity(
        categoryId: String,
        end: YearMonth,
    ): YearMonth? {
        val endMonth = end.toActualMonth()
        val row =
            session
                .query(
                    """
                    SELECT MIN(month) AS month FROM (
                        SELECT month FROM $table WHERE category = ? AND month <= ?
                        UNION ALL
                        SELECT CAST(t.date / 100 AS INTEGER) AS month
                        FROM transactions t
                        LEFT JOIN accounts a ON a.id = t.acct
                        WHERE t.category = ? AND CAST(t.date / 100 AS INTEGER) <= ?
                          AND IFNULL(a.offbudget, 1) = 0 AND $ALIVE
                    )
                    """.trimIndent(),
                    listOf(categoryId, endMonth, categoryId, endMonth),
                ).firstOrNull() ?: return null
        val value = row.values["month"] ?: return null
        val number = (value as? Number)?.toInt() ?: value.toString().toIntOrNull() ?: return null
        return YearMonth.of(number / 100, number % 100)
    }

    /** The sheet's `sum-amount-<category>`: alive on-budget transactions in the month. */
    private fun spending(
        month: YearMonth,
        categoryId: String,
    ): Long {
        val actual = month.toActualMonth()
        return session
            .query(
                """
                SELECT COALESCE(SUM(t.amount), 0) AS total
                FROM transactions t
                LEFT JOIN accounts a ON a.id = t.acct
                WHERE t.category = ? AND t.date >= ? AND t.date <= ?
                  AND IFNULL(a.offbudget, 1) = 0 AND $ALIVE
                """.trimIndent(),
                listOf(categoryId, actual * 100 + 1, actual * 100 + 31),
            ).first()
            .long("total")
    }

    /** Actual's `getAllMonths`: from [start] through the last month the sheet creates, today + 12. */
    private fun createdMonthsFrom(start: YearMonth): List<YearMonth> {
        val latest = YearMonth.from(today).plusMonths(CREATED_AHEAD)
        val months = ArrayList<YearMonth>()
        var cursor = start
        while (cursor <= latest) {
            months.add(cursor)
            cursor = cursor.plusMonths(1)
        }
        return months
    }

    private fun budgeted(
        month: YearMonth,
        categoryId: String,
    ): Long =
        session
            .query("SELECT amount FROM $table WHERE month = ? AND category = ?", listOf(month.toActualMonth(), categoryId))
            .firstOrNull()
            ?.long("amount") ?: 0L

    /** Actual's `setBudget`: update the row for (month, category), else insert one. */
    private fun setBudget(
        month: YearMonth,
        categoryId: String,
        amount: Long,
    ) {
        val existing = existingRow(month, categoryId)
        if (existing != null) {
            SyncSchema.write(session, table, existing, "amount", amount)
            return
        }
        SyncSchema.writeRow(
            session,
            table,
            "${month.toActualMonth()}-$categoryId",
            "month" to month.toActualMonth(),
            "category" to categoryId,
            "amount" to amount,
        )
    }

    private fun setCarryover(
        month: YearMonth,
        categoryId: String,
        flag: Boolean,
    ) {
        val existing = existingRow(month, categoryId)
        if (existing != null) {
            SyncSchema.write(session, table, existing, "carryover", flag)
            return
        }
        SyncSchema.writeRow(
            session,
            table,
            "${month.toActualMonth()}-$categoryId",
            "month" to month.toActualMonth(),
            "category" to categoryId,
            "carryover" to flag,
        )
    }

    private fun existingRow(
        month: YearMonth,
        categoryId: String,
    ): String? =
        session
            .query("SELECT id FROM $table WHERE month = ? AND category = ?", listOf(month.toActualMonth(), categoryId))
            .firstOrNull()
            ?.str("id")

    /** Actual's `addMovementNotes`: one line appended to the month's note. */
    private fun addMovementNote(
        month: YearMonth,
        categoryId: String,
        amountMinor: Long,
    ) {
        val noteId = "budget-$month"
        val existing =
            session
                .query("SELECT note FROM notes WHERE id = ?", listOf(noteId))
                .firstOrNull()
                ?.str("note")
        val prefix = if (existing.isNullOrEmpty()) "" else "$existing\n"
        val preferences =
            session.query("SELECT id, value FROM preferences").associate { it.str("id").orEmpty() to it.str("value").orEmpty() }
        val currency = Currencies.byCode(preferences["currency"].orEmpty()) ?: Currencies.byCode("USD")!!
        val shown =
            MoneyFormat.format(
                amountMinor,
                currency.copy(symbol = ""),
                FormatOptions(numberFormat = preferences["numberFormat"] ?: FormatOptions.NUMBER_COMMA_DOT),
            )
        val name =
            session
                .query("SELECT name FROM categories WHERE id = ?", listOf(categoryId))
                .firstOrNull()
                ?.str("name")
        val day = today.format(DateTimeFormatter.ofPattern("MMMM dd", Locale.ENGLISH))
        SyncSchema.writeRow(session, "notes", noteId, "note" to "$prefix- Reassigned $shown from $name → To Budget on $day")
    }

    companion object {
        private const val AVERAGE_MONTHS = 3
        private const val CREATED_AHEAD = 12L

        /** `v_transactions_internal_alive`: live, not a split parent, parent not deleted. */
        private val ALIVE =
            """
            IFNULL(t.tombstone, 0) = 0
            AND IFNULL(t.isParent, 0) = 0
            AND NOT (
                IFNULL(t.isChild, 0) = 1
                AND EXISTS (SELECT 1 FROM transactions p WHERE p.id = t.parent_id AND IFNULL(p.tombstone, 0) = 1)
            )
            """.trimIndent()
    }
}
