package app.duenorth.budget.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import kotlin.math.roundToLong

data class UpcomingScheduleRow(
    val id: String,
    val name: String,
    val accountId: String,
    val accountName: String,
    val payee: String,
    val amountMinor: Long,
    val categoryId: String?,
    val categoryLabel: String,
    val nextDate: Int,
    val dueToday: Boolean,
    val status: String,
)

sealed interface ScheduleWriteResult {
    data object Posted : ScheduleWriteResult

    data object Skipped : ScheduleWriteResult

    data class Rejected(
        val reason: String,
    ) : ScheduleWriteResult

    data class ConfirmDuplicate(
        val reason: String,
    ) : ScheduleWriteResult
}

class SchedulesBook(
    private val session: SqlSession,
    private val clock: BudgetClock,
    private val ids: () -> String = { UUID.randomUUID().toString() },
) {
    private val rules = RulesBook(session, ids)

    fun listUpcoming(): List<UpcomingScheduleRow> {
        val today = clock.today()
        val todayIso = ScheduleDates.toIso(today.toActualDate())
        return loadSchedules()
            .filter { !it.completed && it.nextIso >= todayIso }
            .sortedWith(compareBy({ it.nextIso }, { it.name }))
            .map { schedule ->
                val account = account(schedule.accountId)
                UpcomingScheduleRow(
                    id = schedule.id,
                    name = schedule.name,
                    accountId = schedule.accountId,
                    accountName = account?.name.orEmpty(),
                    payee = schedule.payeeName,
                    amountMinor = schedule.amountMinor,
                    categoryId = schedule.categoryId,
                    categoryLabel = schedule.categoryLabel,
                    nextDate = ScheduleDates.fromIso(schedule.nextIso) ?: today.toActualDate(),
                    dueToday = schedule.nextIso == todayIso,
                    status = if (schedule.nextIso == todayIso) "due" else "upcoming",
                )
            }
    }

    fun post(
        scheduleId: String,
        forceDuplicate: Boolean = false,
    ): ScheduleWriteResult {
        val schedule = loadSchedules().firstOrNull { it.id == scheduleId }
            ?: return ScheduleWriteResult.Rejected(ScheduleCopy.NO_SCHEDULE)
        if (schedule.completed) return ScheduleWriteResult.Rejected(ScheduleCopy.SCHEDULE_DONE)
        val nextDate = ScheduleDates.fromIso(schedule.nextIso)
            ?: return ScheduleWriteResult.Rejected(RegisterCopy.ENTER_DATE)
        if (!forceDuplicate && hasMatchingTransaction(schedule, nextDate)) {
            return ScheduleWriteResult.ConfirmDuplicate(ScheduleCopy.DUPLICATE)
        }
        val book = RegisterBook(session, ids)
        var payeeName = schedule.payeeName
        var categoryId = schedule.categoryId
        var amount = schedule.amountMinor
        val view =
            RuleTransactionView(
                account = schedule.accountId,
                payee = schedule.payeeId,
                payeeName = payeeName,
                category = categoryId,
                amount = amount,
                notes = null,
                dateIso = schedule.nextIso,
                schedule = schedule.id,
            )
        val ruled = rules.applyRules(view, scheduleRuleId = schedule.ruleId)
        payeeName = ruled.payeeName
        categoryId = ruled.category
        amount = ruled.amount
        val payeeId = ruled.payee ?: schedule.payeeId
        val draft =
            TransactionDraft(
                id = ids(),
                accountId = schedule.accountId,
                date = nextDate,
                payee = payeeName,
                amountMinor = amount,
                categoryId = categoryId,
                note = ruled.notes,
                force = false,
                scheduleId = schedule.id,
            )
        when (book.save(draft)) {
            is WriteResult.Saved -> Unit
            is WriteResult.Rejected -> return ScheduleWriteResult.Rejected(RegisterCopy.ENTER_PAYEE)
            is WriteResult.Confirm -> return ScheduleWriteResult.Rejected(RegisterCopy.RECONCILE_WARN)
        }
        advanceNextDate(schedule)
        return ScheduleWriteResult.Posted
    }

    fun skip(scheduleId: String): ScheduleWriteResult {
        val schedule = loadSchedules().firstOrNull { it.id == scheduleId }
            ?: return ScheduleWriteResult.Rejected(ScheduleCopy.NO_SCHEDULE)
        if (schedule.completed) return ScheduleWriteResult.Rejected(ScheduleCopy.SCHEDULE_DONE)
        advanceNextDate(schedule)
        return ScheduleWriteResult.Skipped
    }

    fun createFromTransaction(
        transactionId: String,
        frequency: String,
        nextDateIso: String,
        occurrences: Int? = null,
    ): ScheduleWriteResult {
        val txn =
            session
                .query(
                    """
                    SELECT id, acct, category, amount, description, date
                    FROM transactions
                    WHERE id = ? AND IFNULL(tombstone, 0) = 0
                    """.trimIndent(),
                    listOf(transactionId),
                ).firstOrNull() ?: return ScheduleWriteResult.Rejected(ShellCopy.NO_ACCOUNTS)
        val payeeId = txn.str("description")
        val accountId = txn.str("acct") ?: return ScheduleWriteResult.Rejected(ShellCopy.NO_ACCOUNTS)
        val amount = txn.long("amount")
        val categoryId = txn.str("category")
        val payeeName =
            payeeId?.let { id ->
                session
                    .query("SELECT name FROM payees WHERE id = ?", listOf(id))
                    .firstOrNull()
                    ?.str("name")
            }.orEmpty()
        val dateCond =
            when (frequency) {
                "once" ->
                    buildJsonObject {
                        put("op", "is")
                        put("field", "date")
                        put("value", nextDateIso)
                    }
                else -> buildRecurDate(nextDateIso, frequency, occurrences)
            }
        val conditions = buildScheduleConditions(accountId, payeeId, amount, dateCond)
        val scheduleId = ids()
        val ruleId = rules.insertRule(null, conditions, linkScheduleActions(scheduleId))
        session.exec(
            """
            INSERT INTO schedules (id, name, rule, completed, posts_transaction, tombstone)
            VALUES (?, ?, ?, 0, 0, 0)
            """.trimIndent(),
            listOf(scheduleId, payeeName.ifBlank { "schedule" }, ruleId),
        )
        val ndId = ids()
        val now = System.currentTimeMillis().toDouble()
        session.exec(
            """
            INSERT INTO schedules_next_date (
                id, schedule_id, local_next_date, local_next_date_ts, base_next_date, base_next_date_ts
            ) VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            listOf(ndId, scheduleId, nextDateIso, now, nextDateIso, now),
        )
        return ScheduleWriteResult.Posted
    }

    fun updateAmount(
        scheduleId: String,
        amountMinor: Long,
    ): ScheduleWriteResult {
        val schedule = loadSchedules().firstOrNull { it.id == scheduleId }
            ?: return ScheduleWriteResult.Rejected(ScheduleCopy.NO_SCHEDULE)
        val updated = replaceAmountCondition(schedule.conditionsJson, amountMinor)
        session.exec(
            "UPDATE rules SET conditions = ? WHERE id = ?",
            listOf(updated.toString(), schedule.ruleId),
        )
        return ScheduleWriteResult.Posted
    }

    fun delete(scheduleId: String): ScheduleWriteResult {
        val schedule = loadSchedules().firstOrNull { it.id == scheduleId }
            ?: return ScheduleWriteResult.Rejected(ScheduleCopy.NO_SCHEDULE)
        session.exec("UPDATE schedules SET tombstone = 1 WHERE id = ?", listOf(scheduleId))
        session.exec("UPDATE rules SET tombstone = 1 WHERE id = ?", listOf(schedule.ruleId))
        return ScheduleWriteResult.Posted
    }

    private data class LoadedSchedule(
        val id: String,
        val name: String,
        val ruleId: String,
        val completed: Boolean,
        val accountId: String,
        val payeeId: String?,
        val payeeName: String,
        val amountMinor: Long,
        val categoryId: String?,
        val categoryLabel: String,
        val nextIso: String,
        val conditionsJson: JsonArray,
        val dateCondition: JsonObject,
    )

    private fun loadSchedules(): List<LoadedSchedule> {
        val rows =
            session.query(
                """
                SELECT
                    s.id AS id,
                    s.name AS name,
                    s.rule AS rule,
                    s.completed AS completed,
                    r.conditions AS conditions,
                    nd.local_next_date AS next_date
                FROM schedules s
                JOIN rules r ON r.id = s.rule
                LEFT JOIN schedules_next_date nd ON nd.schedule_id = s.id
                WHERE IFNULL(s.tombstone, 0) = 0
                  AND IFNULL(r.tombstone, 0) = 0
                """.trimIndent(),
            )
        return rows.mapNotNull { row ->
            val conditionsRaw = row.str("conditions") ?: return@mapNotNull null
            val conditions = Json.parseToJsonElement(conditionsRaw).jsonArray
            val accountId = extractField(conditions, "account", "acct") ?: return@mapNotNull null
            val payeeId = extractField(conditions, "payee", "description")
            val amount = extractAmount(conditions) ?: return@mapNotNull null
            val dateCond =
                conditions.firstOrNull {
                    val obj = it.jsonObject
                    obj["field"]?.jsonPrimitive?.contentOrNull == "date"
                }?.jsonObject ?: return@mapNotNull null
            val nextIso = row.str("next_date") ?: ScheduleRecurrence.firstDate(dateCond) ?: return@mapNotNull null
            val payeeName =
                payeeId?.let { id ->
                    session
                        .query("SELECT name FROM payees WHERE id = ?", listOf(id))
                        .firstOrNull()
                        ?.str("name")
                }.orEmpty()
            val categoryId = extractCategoryFromActions(row.str("rule") ?: "")
            val categoryLabel =
                categoryId?.let { id ->
                    session
                        .query("SELECT name FROM categories WHERE id = ?", listOf(id))
                        .firstOrNull()
                        ?.str("name")
                }.orEmpty()
                    .ifBlank { RegisterCopy.NO_CATEGORY }
            LoadedSchedule(
                id = row.str("id") ?: return@mapNotNull null,
                name = row.str("name").orEmpty(),
                ruleId = row.str("rule") ?: return@mapNotNull null,
                completed = row.long("completed") == 1L,
                accountId = accountId,
                payeeId = payeeId,
                payeeName = payeeName.ifBlank { ShellCopy.NO_PAYEE },
                amountMinor = amount,
                categoryId = categoryId,
                categoryLabel = categoryLabel,
                nextIso = nextIso,
                conditionsJson = conditions,
                dateCondition = dateCond,
            )
        }
    }

    private fun extractCategoryFromActions(ruleId: String): String? {
        val actions =
            session
                .query("SELECT actions FROM rules WHERE id = ?", listOf(ruleId))
                .firstOrNull()
                ?.str("actions")
                ?: return null
        val array = Json.parseToJsonElement(actions).jsonArray
        return array.firstOrNull {
            it.jsonObject["op"]?.jsonPrimitive?.contentOrNull == "set" &&
                it.jsonObject["field"]?.jsonPrimitive?.contentOrNull == "category"
        }?.jsonObject?.get("value")?.jsonPrimitive?.contentOrNull
    }

    private fun hasMatchingTransaction(
        schedule: LoadedSchedule,
        date: Int,
    ): Boolean {
        val start = date - 2
        val payeeId = schedule.payeeId ?: return false
        val count =
            session
                .query(
                    """
                    SELECT COUNT(*) AS n
                    FROM transactions
                    WHERE IFNULL(tombstone, 0) = 0
                      AND IFNULL(isParent, 0) = 0
                      AND acct = ?
                      AND description = ?
                      AND amount = ?
                      AND date BETWEEN ? AND ?
                    """.trimIndent(),
                    listOf(schedule.accountId, payeeId, schedule.amountMinor, start, date),
                ).first()
                .long("n")
        return count > 0
    }

    private fun advanceNextDate(schedule: LoadedSchedule) {
        val next =
            ScheduleRecurrence.nextDate(schedule.dateCondition, schedule.nextIso, afterPost = true)
                ?: run {
                    markCompleted(schedule.id)
                    return
                }
        if (next == schedule.nextIso) {
            markCompleted(schedule.id)
            return
        }
        updateNextDate(schedule.id, next)
        if (ScheduleRecurrence.isFinished(schedule.dateCondition, next)) {
            markCompleted(schedule.id)
        }
    }

    private fun markCompleted(scheduleId: String) {
        session.exec("UPDATE schedules SET completed = 1 WHERE id = ?", listOf(scheduleId))
    }

    private fun updateNextDate(
        scheduleId: String,
        iso: String,
    ) {
        val now = System.currentTimeMillis().toDouble()
        val existing =
            session
                .query(
                    "SELECT id FROM schedules_next_date WHERE schedule_id = ?",
                    listOf(scheduleId),
                ).firstOrNull()
                ?.str("id")
        if (existing == null) {
            session.exec(
                """
                INSERT INTO schedules_next_date (
                    id, schedule_id, local_next_date, local_next_date_ts, base_next_date, base_next_date_ts
                ) VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                listOf(ids(), scheduleId, iso, now, iso, now),
            )
        } else {
            session.exec(
                """
                UPDATE schedules_next_date
                SET local_next_date = ?, local_next_date_ts = ?
                WHERE schedule_id = ?
                """.trimIndent(),
                listOf(iso, now, scheduleId),
            )
        }
    }

    private fun account(id: String): RegisterAccount? {
        val row =
            session
                .query(
                    """
                    SELECT id, name, offbudget, type, closed, tombstone
                    FROM accounts WHERE id = ?
                    """.trimIndent(),
                    listOf(id),
                ).firstOrNull() ?: return null
        return RegisterAccount(
            id = row.str("id") ?: id,
            name = row.str("name").orEmpty(),
            offBudget = row.long("offbudget") == 1L,
            type = row.str("type") ?: "checking",
            closed = row.long("closed") == 1L,
        )
    }
}

private fun linkScheduleActions(scheduleId: String): JsonArray =
    buildJsonArray {
        add(
            buildJsonObject {
                put("op", "link-schedule")
                put("value", scheduleId)
            },
        )
    }

private fun buildScheduleConditions(
    accountId: String,
    payeeId: String?,
    amountMinor: Long,
    date: JsonObject,
): JsonArray =
    buildJsonArray {
        add(
            buildJsonObject {
                put("op", "is")
                put("field", "account")
                put("value", accountId)
                put("type", "id")
            },
        )
        if (payeeId != null) {
            add(
                buildJsonObject {
                    put("op", "is")
                    put("field", "payee")
                    put("value", payeeId)
                    put("type", "id")
                },
            )
        }
        add(
            buildJsonObject {
                put("op", "is")
                put("field", "amount")
                put("value", amountMinor)
                put("type", "number")
            },
        )
        add(date)
    }

private fun buildRecurDate(
    startIso: String,
    frequency: String,
    occurrences: Int?,
): JsonObject =
    buildJsonObject {
        put("op", "isapprox")
        put("field", "date")
        put(
            "value",
            buildJsonObject {
                put("frequency", frequency)
                put("start", startIso)
                put("interval", 1)
                if (occurrences != null) {
                    put("endMode", "after_n_occurrences")
                    put("endOccurrences", occurrences)
                } else {
                    put("endMode", "never")
                }
            },
        )
    }

private fun replaceAmountCondition(
    conditions: JsonArray,
    amountMinor: Long,
): JsonArray =
    buildJsonArray {
        conditions.forEach { element ->
            val obj = element.jsonObject
            if (obj["field"]?.jsonPrimitive?.contentOrNull == "amount") {
                add(
                    buildJsonObject {
                        put("op", "is")
                        put("field", "amount")
                        put("value", amountMinor)
                        put("type", "number")
                    },
                )
            } else {
                add(obj)
            }
        }
    }

private fun extractField(
    conditions: JsonArray,
    vararg names: String,
): String? =
    conditions.firstOrNull {
        val field = it.jsonObject["field"]?.jsonPrimitive?.contentOrNull
        field in names
    }?.jsonObject?.get("value")?.jsonPrimitive?.contentOrNull

private fun extractAmount(conditions: JsonArray): Long? {
    val cond =
        conditions.firstOrNull {
            it.jsonObject["field"]?.jsonPrimitive?.contentOrNull == "amount"
        }?.jsonObject ?: return null
    val value = cond["value"] ?: return null
    return when {
        value is JsonPrimitive && value.contentOrNull != null ->
            value.contentOrNull?.toDoubleOrNull()?.roundToLong()
        value is JsonObject -> {
            val n1 = value["num1"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            val n2 = value["num2"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            if (n1 != null && n2 != null) ((n1 + n2) / 2.0).roundToLong() else null
        }
        else -> null
    }
}

private fun LocalDate.toActualDate(): Int = year * 10000 + monthValue * 100 + dayOfMonth

object ScheduleRecurrence {
    fun firstDate(dateCond: JsonObject): String? {
        val value = dateCond["value"] ?: return null
        if (value is JsonPrimitive) return value.contentOrNull
        return value.jsonObject["start"]?.jsonPrimitive?.contentOrNull
    }

    fun nextDate(
        dateCond: JsonObject,
        startIso: String,
        afterPost: Boolean,
    ): String? {
        val value = dateCond["value"] ?: return null
        if (value is JsonPrimitive) {
            val iso = value.contentOrNull ?: return null
            return if (afterPost) null else iso
        }
        val config = value.jsonObject
        val frequency = config["frequency"]?.jsonPrimitive?.contentOrNull ?: return null
        val interval = config["interval"]?.jsonPrimitive?.intOrNull ?: 1
        val cursor = ScheduleDates.parseIso(startIso) ?: return null
        val endMode = config["endMode"]?.jsonPrimitive?.contentOrNull ?: "never"
        val maxCount = config["endOccurrences"]?.jsonPrimitive?.intOrNull
        val endDate =
            config["endDate"]?.jsonPrimitive?.contentOrNull?.let { ScheduleDates.parseIso(it) }
        if (!afterPost) return cursor.toString()

        val candidate =
            when (frequency) {
                "daily" -> cursor.plusDays(interval.toLong())
                "weekly" -> cursor.plusWeeks(interval.toLong())
                "monthly" -> cursor.plusMonths(interval.toLong())
                "yearly" -> cursor.plusYears(interval.toLong())
                else -> return null
            }
        if (endDate != null && candidate.isAfter(endDate)) return null
        if (endMode == "after_n_occurrences" && maxCount != null) {
            val posted =
                sessionCount(config["start"]?.jsonPrimitive?.contentOrNull, startIso, frequency, interval)
            if (posted + 1 >= maxCount) return null
        }
        return candidate.toString()
    }

    private fun sessionCount(
        startIso: String?,
        currentIso: String,
        frequency: String,
        interval: Int,
    ): Int {
        val start = startIso?.let { ScheduleDates.parseIso(it) } ?: return 1
        val current = ScheduleDates.parseIso(currentIso) ?: return 1
        return when (frequency) {
            "monthly" ->
                ((current.year - start.year) * 12 + (current.monthValue - start.monthValue)) / interval + 1
            "weekly" -> java.time.temporal.ChronoUnit.WEEKS.between(start, current).toInt() / interval + 1
            "daily" -> java.time.temporal.ChronoUnit.DAYS.between(start, current).toInt() / interval + 1
            "yearly" -> (current.year - start.year) / interval + 1
            else -> 1
        }
    }

    fun isFinished(
        dateCond: JsonObject,
        nextIso: String,
    ): Boolean {
        val value = dateCond["value"] ?: return false
        if (value is JsonPrimitive) return true
        val config = value.jsonObject
        if (config["endMode"]?.jsonPrimitive?.contentOrNull == "on_date") {
            val end = config["endDate"]?.jsonPrimitive?.contentOrNull ?: return false
            return nextIso > end
        }
        return false
    }

    private fun dateCondStart(dateCond: JsonObject): String {
        val value = dateCond["value"]
        if (value is JsonPrimitive) return value.contentOrNull.orEmpty()
        return value?.jsonObject?.get("start")?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    private fun LocalDate.with(
        startAdjust: Boolean,
        block: (LocalDate) -> LocalDate,
    ): LocalDate {
        var date = block(this)
        if (date.dayOfWeek == DayOfWeek.SATURDAY) date = date.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        if (date.dayOfWeek == DayOfWeek.SUNDAY) date = date.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        return date
    }
}
