package app.duenorth.budget.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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
import java.time.LocalDate
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

/**
 * One schedule as the schedules view lists it: what it is, how it repeats, and the dates it falls
 * on from its next date for about a year ([dates]; empty once it has ended).
 */
data class ScheduleSummary(
    val id: String,
    val name: String,
    val accountId: String,
    val accountName: String,
    val payee: String,
    val amountMinor: Long,
    /** `is`, `isapprox`, or `isbetween`. */
    val amountOp: String,
    val categoryLabel: String,
    val nextDate: Int,
    val completed: Boolean,
    val postsTransaction: Boolean,
    val repeat: ScheduleRepeat,
    val interval: Int,
    val dates: List<Int>,
    /** Desktop's `sort_order` (Actual 26.10): higher comes first among schedules due the same day. */
    val sortOrder: Double = 0.0,
) {
    /** The schedule's own name, or its payee's when it has none. */
    val title: String get() = name.ifBlank { payee }
}

/** A transaction a schedule posted. */
data class SchedulePosted(
    val id: String,
    val date: Int,
    val amountMinor: Long,
    val categoryLabel: String,
)

/** A schedule's own page: the summary and the transactions it has posted, newest first. */
data class ScheduleDetails(
    val summary: ScheduleSummary,
    val posted: List<SchedulePosted>,
)

/** How a schedule repeats in the editor; `ONCE` stores a plain date. */
enum class ScheduleRepeat(
    val frequency: String?,
) {
    ONCE(null),
    DAILY("daily"),
    WEEKLY("weekly"),
    MONTHLY("monthly"),
    YEARLY("yearly"),
}

enum class ScheduleEnd(
    val mode: String,
) {
    NEVER("never"),
    AFTER("after_n_occurrences"),
    ON_DATE("on_date"),
}

/**
 * The schedule editor (`contracts/schedules-rules.md`). [keptDate] is the stored recurrence
 * config; its patterns, `skipWeekend` and `weekendSolveMode` are written back unchanged.
 */
data class ScheduleDraft(
    val id: String? = null,
    val name: String = "",
    val accountId: String = "",
    val payeeName: String = "",
    val amountMinor: Long = 0L,
    /** `is`, `isapprox`, or `isbetween` (with [amount2Minor]). */
    val amountOp: String = "is",
    val amount2Minor: Long = 0L,
    val categoryId: String? = null,
    val repeat: ScheduleRepeat = ScheduleRepeat.MONTHLY,
    val interval: Int = 1,
    val startIso: String = "",
    val end: ScheduleEnd = ScheduleEnd.NEVER,
    val endOccurrences: Int = 1,
    val endDateIso: String = "",
    val postsTransaction: Boolean = false,
    val keptDate: JsonObject? = null,
) {
    /** True when the stored config has parts the editor does not change. */
    val hasKeptDateParts: Boolean
        get() =
            keptDate != null &&
                (
                    (keptDate["patterns"] as? JsonArray)?.isNotEmpty() == true ||
                        keptDate["skipWeekend"]?.jsonPrimitive?.contentOrNull == "true"
                )
}

object ScheduleEditorCopy {
    const val CHOOSE_ACCOUNT = "choose an account"
    const val ENTER_START = "enter a date"
    const val AUTO_POST = "auto-post"
}

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
            .sortedWith(compareBy<LoadedSchedule> { it.nextIso }.then(SAME_DAY))
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

    /** Every schedule, ended ones included, by next date. */
    fun listAll(): List<ScheduleSummary> {
        val accounts = HashMap<String, String>()
        return loadSchedules()
            .sortedWith(compareBy<LoadedSchedule> { it.nextIso }.then(SAME_DAY))
            .map { schedule ->
                val name = accounts.getOrPut(schedule.accountId) { account(schedule.accountId)?.name.orEmpty() }
                summary(schedule, name)
            }
    }

    /** One schedule with what it has posted; null when it is gone. */
    fun details(
        scheduleId: String,
        postedLimit: Int = 12,
    ): ScheduleDetails? {
        val schedule = loadSchedules().firstOrNull { it.id == scheduleId } ?: return null
        val posted =
            session
                .query(
                    """
                    SELECT t.id AS id, t.date AS date, t.amount AS amount, c.name AS category
                    FROM transactions t
                    LEFT JOIN categories c ON c.id = t.category
                    WHERE IFNULL(t.tombstone, 0) = 0
                      AND IFNULL(t.isChild, 0) = 0
                      AND t.schedule = ?
                    ORDER BY t.date DESC, t.sort_order DESC
                    LIMIT ?
                    """.trimIndent(),
                    listOf(scheduleId, postedLimit),
                ).map { row ->
                    SchedulePosted(
                        id = row.str("id").orEmpty(),
                        date = row.long("date").toInt(),
                        amountMinor = row.long("amount"),
                        categoryLabel = row.str("category").orEmpty().ifBlank { RegisterCopy.NO_CATEGORY },
                    )
                }
        return ScheduleDetails(summary(schedule, account(schedule.accountId)?.name.orEmpty()), posted)
    }

    private fun summary(
        schedule: LoadedSchedule,
        accountName: String,
    ): ScheduleSummary {
        val config = schedule.dateCondition["value"] as? JsonObject
        val frequency = config?.get("frequency")?.jsonPrimitive?.contentOrNull
        val next = ScheduleDates.parseIso(schedule.nextIso)
        val dates =
            when {
                schedule.completed || next == null -> emptyList()
                config == null -> listOf(next)
                else -> {
                    val horizon = next.plusYears(1)
                    ScheduleRecurrence.occurrences(config, next, limit = DATES_LIMIT).filter { it <= horizon }
                }
            }.map { it.toActualDate() }
        val amountOp =
            schedule.conditionsJson
                .firstOrNull { it.jsonObject["field"]?.jsonPrimitive?.contentOrNull == "amount" }
                ?.jsonObject
                ?.get("op")
                ?.jsonPrimitive
                ?.contentOrNull ?: "is"
        return ScheduleSummary(
            id = schedule.id,
            name = schedule.name,
            accountId = schedule.accountId,
            accountName = accountName,
            payee = schedule.payeeName,
            amountMinor = schedule.amountMinor,
            amountOp = amountOp,
            categoryLabel = schedule.categoryLabel,
            nextDate = ScheduleDates.fromIso(schedule.nextIso) ?: 0,
            completed = schedule.completed,
            postsTransaction = schedule.postsTransaction,
            repeat = ScheduleRepeat.entries.firstOrNull { it.frequency == frequency } ?: ScheduleRepeat.ONCE,
            interval =
                config
                    ?.get("interval")
                    ?.jsonPrimitive
                    ?.intOrNull
                    ?.coerceAtLeast(1) ?: 1,
            dates = dates,
            sortOrder = schedule.sortOrder,
        )
    }

    /** True once desktop Actual 26.10 has added `schedules.sort_order` to this file; the phone never adds it. */
    fun canReorder(): Boolean = "sort_order" in session.query("PRAGMA table_info(schedules)").mapNotNull { it.str("name") }

    /**
     * Desktop's `schedule/move`: puts [scheduleId] just after [targetId] in the
     * order schedules due the same day are listed, or first when [targetId] is
     * null, shoving the ones after it down when there is no room.
     */
    fun move(
        scheduleId: String,
        targetId: String?,
    ): ScheduleWriteResult {
        if (!canReorder()) return ScheduleWriteResult.Rejected(ScheduleCopy.NO_ORDER)
        session
            .query("SELECT id FROM schedules WHERE id = ? AND IFNULL(tombstone, 0) = 0", listOf(scheduleId))
            .firstOrNull() ?: return ScheduleWriteResult.Rejected(ScheduleCopy.NO_SCHEDULE)
        val items =
            session
                .query("SELECT id, sort_order FROM schedules WHERE IFNULL(tombstone, 0) = 0 ORDER BY sort_order DESC, id")
                .map { it.str("id").orEmpty() to it.double("sort_order") }
        val (updates, order) = shoveSortOrdersDescending(items, targetId, scheduleId)
        updates.forEach { (id, sort) -> SyncSchema.write(session, "schedules", id, "sort_order", sort) }
        SyncSchema.write(session, "schedules", scheduleId, "sort_order", order)
        return ScheduleWriteResult.Posted
    }

    fun post(
        scheduleId: String,
        forceDuplicate: Boolean = false,
    ): ScheduleWriteResult {
        val schedule =
            loadSchedules().firstOrNull { it.id == scheduleId }
                ?: return ScheduleWriteResult.Rejected(ScheduleCopy.NO_SCHEDULE)
        if (schedule.completed) return ScheduleWriteResult.Rejected(ScheduleCopy.SCHEDULE_DONE)
        val nextDate =
            ScheduleDates.fromIso(schedule.nextIso)
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
        val schedule =
            loadSchedules().firstOrNull { it.id == scheduleId }
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
            payeeId
                ?.let { id ->
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
        SyncSchema.writeRow(
            session,
            "schedules",
            scheduleId,
            "name" to payeeName.ifBlank { "schedule" },
            "rule" to ruleId,
            "completed" to 0,
            "posts_transaction" to 0,
            "tombstone" to 0,
        )
        val now = System.currentTimeMillis()
        val stored = ScheduleDates.toStored(nextDateIso)
        SyncSchema.writeRow(
            session,
            "schedules_next_date",
            ids(),
            "schedule_id" to scheduleId,
            "local_next_date" to stored,
            "local_next_date_ts" to now,
            "base_next_date" to stored,
            "base_next_date_ts" to now,
            "tombstone" to 0,
        )
        return ScheduleWriteResult.Posted
    }

    fun delete(scheduleId: String): ScheduleWriteResult {
        val schedule =
            loadSchedules().firstOrNull { it.id == scheduleId }
                ?: return ScheduleWriteResult.Rejected(ScheduleCopy.NO_SCHEDULE)
        SyncSchema.write(session, "schedules", scheduleId, "tombstone", 1)
        rules.delete(schedule.ruleId)
        return ScheduleWriteResult.Posted
    }

    /** The editor's view of an existing schedule; unknown date parts stay in [ScheduleDraft.keptDate]. */
    fun draft(scheduleId: String): ScheduleDraft? {
        val schedule = loadSchedules().firstOrNull { it.id == scheduleId } ?: return null
        val posts =
            session
                .query("SELECT posts_transaction FROM schedules WHERE id = ?", listOf(scheduleId))
                .firstOrNull()
                ?.bool("posts_transaction") == true
        val amountCond =
            schedule.conditionsJson
                .firstOrNull { it.jsonObject["field"]?.jsonPrimitive?.contentOrNull == "amount" }
                ?.jsonObject
        val amountOp = amountCond?.get("op")?.jsonPrimitive?.contentOrNull ?: "is"
        val between = amountCond?.get("value") as? JsonObject
        val dateValue = schedule.dateCondition["value"]
        val config = dateValue as? JsonObject
        val frequency = config?.get("frequency")?.jsonPrimitive?.contentOrNull
        return ScheduleDraft(
            id = schedule.id,
            name = schedule.name,
            accountId = schedule.accountId,
            payeeName = if (schedule.payeeId == null) "" else schedule.payeeName,
            amountMinor =
                if (between != null) between["num1"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L else schedule.amountMinor,
            amountOp = amountOp,
            amount2Minor =
                between
                    ?.get("num2")
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.toLongOrNull() ?: 0L,
            categoryId = schedule.categoryId,
            repeat = ScheduleRepeat.entries.firstOrNull { it.frequency == frequency } ?: ScheduleRepeat.ONCE,
            interval = config?.get("interval")?.jsonPrimitive?.intOrNull ?: 1,
            startIso = config?.get("start")?.jsonPrimitive?.contentOrNull ?: (dateValue as? JsonPrimitive)?.contentOrNull.orEmpty(),
            end =
                when (config?.get("endMode")?.jsonPrimitive?.contentOrNull) {
                    "after_n_occurrences" -> ScheduleEnd.AFTER
                    "on_date" -> ScheduleEnd.ON_DATE
                    else -> ScheduleEnd.NEVER
                },
            endOccurrences = config?.get("endOccurrences")?.jsonPrimitive?.intOrNull ?: 1,
            endDateIso =
                config
                    ?.get("endDate")
                    ?.jsonPrimitive
                    ?.contentOrNull
                    .orEmpty(),
            postsTransaction = posts,
            keptDate = config,
        )
    }

    /** Writes the rule, the schedule, and its next date: Actual's `schedule/create` and `schedule/update`. */
    fun save(draft: ScheduleDraft): ScheduleWriteResult {
        if (draft.accountId.isBlank()) return ScheduleWriteResult.Rejected(ScheduleEditorCopy.CHOOSE_ACCOUNT)
        if (ScheduleDates.parseIso(draft.startIso) == null) return ScheduleWriteResult.Rejected(ScheduleEditorCopy.ENTER_START)
        if (draft.end == ScheduleEnd.ON_DATE && ScheduleDates.parseIso(draft.endDateIso) == null) {
            return ScheduleWriteResult.Rejected(ScheduleEditorCopy.ENTER_START)
        }
        val payeeId =
            draft.payeeName
                .trim()
                .takeIf { it.isNotEmpty() }
                ?.let { RegisterBook(session, ids).payeeIdForName(it) }
        val date = dateCondition(draft)
        val conditions =
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("op", "is")
                        put("field", "acct")
                        put("value", draft.accountId)
                    },
                )
                if (payeeId != null) {
                    add(
                        buildJsonObject {
                            put("op", "is")
                            put("field", "description")
                            put("value", payeeId)
                        },
                    )
                }
                add(
                    buildJsonObject {
                        put("op", draft.amountOp)
                        put("field", "amount")
                        if (draft.amountOp == "isbetween") {
                            put(
                                "value",
                                buildJsonObject {
                                    put("num1", draft.amountMinor)
                                    put("num2", draft.amount2Minor)
                                },
                            )
                        } else {
                            put("value", draft.amountMinor)
                        }
                    },
                )
                add(date)
            }
        val existing = draft.id?.let { id -> loadSchedules().firstOrNull { it.id == id } }
        val scheduleId = existing?.id ?: draft.id ?: ids()
        val actions =
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("op", "link-schedule")
                        put("value", scheduleId)
                    },
                )
                if (!draft.categoryId.isNullOrBlank()) {
                    add(
                        buildJsonObject {
                            put("op", "set")
                            put("field", "category")
                            put("value", draft.categoryId)
                            put("type", "id")
                        },
                    )
                }
            }
        val ruleId = existing?.ruleId ?: ids()
        SyncSchema.writeRow(
            session,
            "rules",
            ruleId,
            "stage" to null,
            "conditions_op" to "and",
            "conditions" to conditions.toString(),
            "actions" to actions.toString(),
            "tombstone" to 0,
        )
        SyncSchema.writeRow(
            session,
            "schedules",
            scheduleId,
            "name" to draft.name.trim().ifEmpty { null },
            "rule" to ruleId,
            "active" to 0,
            "completed" to 0,
            "posts_transaction" to draft.postsTransaction,
            "tombstone" to 0,
        )
        val next = ScheduleRecurrence.firstOnOrAfter(date, clock.today())
        if (next == null) {
            SyncSchema.write(session, "schedules", scheduleId, "completed", 1)
        } else {
            setBaseNextDate(scheduleId, next)
        }
        return ScheduleWriteResult.Posted
    }

    /**
     * Posts or advances the first due auto-post occurrence (`advanceSchedulesService`).
     * Returns false when nothing is due; the caller runs it once per undo step.
     */
    fun autoPostOne(): Boolean {
        val due = nextAutoPost() ?: return false
        val date = ScheduleDates.fromIso(due.nextIso) ?: return false
        if (hasMatchingTransaction(due, date)) {
            advanceNextDate(due)
            return true
        }
        val before = session.query("SELECT id FROM transactions").mapNotNull { it.str("id") }.toSet()
        when (post(due.id, forceDuplicate = true)) {
            ScheduleWriteResult.Posted -> {
                // Actual posts scheduled transactions uncleared.
                session
                    .query("SELECT id FROM transactions WHERE schedule = ? AND IFNULL(tombstone, 0) = 0", listOf(due.id))
                    .mapNotNull { it.str("id") }
                    .filter { it !in before }
                    .forEach { SyncSchema.write(session, "transactions", it, "cleared", 0) }
            }
            else -> {
                advanceNextDate(due)
            }
        }
        return true
    }

    /** True when an auto-post schedule is due; a read, so callers can skip opening an undo step. */
    fun hasAutoPostDue(): Boolean = nextAutoPost() != null

    private fun nextAutoPost(): LoadedSchedule? {
        val todayIso = clock.today().toString()
        val posting =
            session
                .query(
                    "SELECT id FROM schedules WHERE IFNULL(posts_transaction, 0) = 1 AND IFNULL(completed, 0) = 0 AND IFNULL(tombstone, 0) = 0",
                ).mapNotNull { it.str("id") }
                .toSet()
        if (posting.isEmpty()) return null
        return loadSchedules()
            .filter { it.id in posting && !it.completed && it.nextIso <= todayIso }
            .minByOrNull { it.nextIso }
    }

    private fun dateCondition(draft: ScheduleDraft): JsonObject {
        val frequency = draft.repeat.frequency
        if (frequency == null) {
            return buildJsonObject {
                put("op", "is")
                put("field", "date")
                put("value", draft.startIso)
            }
        }
        val kept = draft.keptDate
        val config = LinkedHashMap<String, JsonElement>()
        config["start"] = JsonPrimitive(draft.startIso)
        config["frequency"] = JsonPrimitive(frequency)
        config["interval"] = JsonPrimitive(draft.interval.coerceAtLeast(1))
        config["endMode"] = JsonPrimitive(draft.end.mode)
        if (draft.end == ScheduleEnd.AFTER) config["endOccurrences"] = JsonPrimitive(draft.endOccurrences.coerceAtLeast(1))
        if (draft.end == ScheduleEnd.ON_DATE) config["endDate"] = JsonPrimitive(draft.endDateIso)
        config["patterns"] = kept?.get("patterns") ?: JsonArray(emptyList())
        config["skipWeekend"] = kept?.get("skipWeekend") ?: JsonPrimitive(false)
        config["weekendSolveMode"] = kept?.get("weekendSolveMode") ?: JsonPrimitive("after")
        // Anything else desktop stored stays as it was.
        kept?.forEach { (key, value) -> if (key !in config && key !in EDITED_DATE_KEYS) config[key] = value }
        return buildJsonObject {
            put("op", "isapprox")
            put("field", "date")
            put("value", JsonObject(config))
        }
    }

    private fun setBaseNextDate(
        scheduleId: String,
        iso: String,
    ) {
        val now = System.currentTimeMillis()
        val stored = ScheduleDates.toStored(iso)
        val existing =
            session
                .query(
                    "SELECT id FROM schedules_next_date WHERE schedule_id = ? AND IFNULL(tombstone, 0) = 0",
                    listOf(scheduleId),
                ).firstOrNull()
                ?.str("id")
        SyncSchema.writeRow(
            session,
            "schedules_next_date",
            existing ?: ids(),
            "schedule_id" to scheduleId,
            "local_next_date" to stored,
            "local_next_date_ts" to now,
            "base_next_date" to stored,
            "base_next_date_ts" to now,
            "tombstone" to 0,
        )
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
        val postsTransaction: Boolean = false,
        val sortOrder: Double = 0.0,
    )

    private fun loadSchedules(): List<LoadedSchedule> {
        val sortOrder = if (canReorder()) "IFNULL(s.sort_order, 0)" else "0"
        val rows =
            session.query(
                """
                SELECT
                    $sortOrder AS sort_order,
                    s.id AS id,
                    s.name AS name,
                    s.rule AS rule,
                    s.completed AS completed,
                    s.posts_transaction AS posts_transaction,
                    r.conditions AS conditions,
                    nd.local_next_date AS next_date
                FROM schedules s
                JOIN rules r ON r.id = s.rule
                LEFT JOIN schedules_next_date nd ON nd.schedule_id = s.id AND IFNULL(nd.tombstone, 0) = 0
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
                conditions
                    .firstOrNull {
                        val obj = it.jsonObject
                        obj["field"]?.jsonPrimitive?.contentOrNull == "date"
                    }?.jsonObject ?: return@mapNotNull null
            val nextIso =
                ScheduleDates.storedToIso(row.str("next_date"))
                    ?: ScheduleRecurrence.firstDate(dateCond)
                    ?: return@mapNotNull null
            val payeeName =
                payeeId
                    ?.let { id ->
                        session
                            .query("SELECT name FROM payees WHERE id = ?", listOf(id))
                            .firstOrNull()
                            ?.str("name")
                    }.orEmpty()
            val categoryId = extractCategoryFromActions(row.str("rule") ?: "")
            val categoryLabel =
                categoryId
                    ?.let { id ->
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
                postsTransaction = row.long("posts_transaction") == 1L,
                sortOrder = row.double("sort_order"),
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
        return array
            .firstOrNull {
                it.jsonObject["op"]?.jsonPrimitive?.contentOrNull == "set" &&
                    it.jsonObject["field"]?.jsonPrimitive?.contentOrNull == "category"
            }?.jsonObject
            ?.get("value")
            ?.jsonPrimitive
            ?.contentOrNull
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
        SyncSchema.write(session, "schedules", scheduleId, "completed", 1)
    }

    private fun updateNextDate(
        scheduleId: String,
        iso: String,
    ) {
        val now = System.currentTimeMillis()
        val stored = ScheduleDates.toStored(iso)
        val existing =
            session
                .query(
                    "SELECT id FROM schedules_next_date WHERE schedule_id = ? AND IFNULL(tombstone, 0) = 0",
                    listOf(scheduleId),
                ).firstOrNull()
                ?.str("id")
        if (existing == null) {
            SyncSchema.writeRow(
                session,
                "schedules_next_date",
                ids(),
                "schedule_id" to scheduleId,
                "local_next_date" to stored,
                "local_next_date_ts" to now,
                "base_next_date" to stored,
                "base_next_date_ts" to now,
                "tombstone" to 0,
            )
            return
        }
        SyncSchema.writeRow(
            session,
            "schedules_next_date",
            existing,
            "local_next_date" to stored,
            "local_next_date_ts" to now,
        )
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

    companion object {
        private const val SORT_INCREMENT = 16384.0

        /** Desktop's order for schedules due the same day: `sort_order` high first, then the smaller amount, then name. */
        private val SAME_DAY: Comparator<LoadedSchedule> =
            compareByDescending<LoadedSchedule> { it.sortOrder }.thenBy { it.amountMinor }.thenBy { it.name }

        /**
         * Actual's `shoveSortOrdersDescending`: the order that puts an item after
         * [targetId] in a list sorted high to low (first when null, last when
         * [targetId] is gone), plus the items after it that must move down to
         * make room. [movingId] is skipped, as it is the one moving.
         */
        fun shoveSortOrdersDescending(
            items: List<Pair<String, Double>>,
            targetId: String?,
            movingId: String? = null,
        ): Pair<List<Pair<String, Double>>, Double> {
            if (targetId == null) {
                return emptyList<Pair<String, Double>>() to (items.firstOrNull()?.second?.plus(SORT_INCREMENT) ?: SORT_INCREMENT)
            }
            val at = items.indexOfFirst { it.first == targetId }
            if (at == -1) {
                return emptyList<Pair<String, Double>>() to (items.lastOrNull()?.second?.minus(SORT_INCREMENT) ?: SORT_INCREMENT)
            }
            val target = items[at].second
            val nextAt = items.indices.firstOrNull { it > at && items[it].first != movingId }
            val next = nextAt?.let { items[it].second }
            if (target - (next ?: 0.0) > 2) {
                val order = if (next == null) target - SORT_INCREMENT else Math.round((target + next) / 2).toDouble()
                return emptyList<Pair<String, Double>>() to order
            }
            val updates = ArrayList<Pair<String, Double>>()
            var order = target - SORT_INCREMENT
            for (i in at + 1 until items.size) {
                if (items[i].first == movingId) continue
                if (items[i].second < order) break
                updates.add(items[i].first to order)
                order -= SORT_INCREMENT
            }
            return updates to Math.round(target - SORT_INCREMENT / 2).toDouble()
        }
    }
}

/** Enough dates for a weekly schedule's year; a daily one shows its next few months. */
private const val DATES_LIMIT = 120

private val EDITED_DATE_KEYS = setOf("start", "frequency", "interval", "endMode", "endOccurrences", "endDate")

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

private fun extractField(
    conditions: JsonArray,
    vararg names: String,
): String? =
    conditions
        .firstOrNull {
            val field = it.jsonObject["field"]?.jsonPrimitive?.contentOrNull
            field in names
        }?.jsonObject
        ?.get("value")
        ?.jsonPrimitive
        ?.contentOrNull

private fun extractAmount(conditions: JsonArray): Long? {
    val cond =
        conditions
            .firstOrNull {
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

/**
 * Actual's recurrence configs (`{start, frequency, interval, endMode, endOccurrences, endDate,
 * patterns, skipWeekend, weekendSolveMode}`) or a plain one-time date. Patterns and weekend moves
 * are evaluated even though the phone's editor does not change them.
 */
object ScheduleRecurrence {
    fun firstDate(dateCond: JsonObject): String? {
        val value = dateCond["value"] ?: return null
        if (value is JsonPrimitive) return value.contentOrNull
        return value.jsonObject["start"]?.jsonPrimitive?.contentOrNull
    }

    /** The occurrence after [startIso] when [afterPost], else [startIso] itself; null when the schedule has ended. */
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
        if (!afterPost) return startIso
        val current = ScheduleDates.parseIso(startIso) ?: return null
        return occurrences(value.jsonObject, current.plusDays(1)).firstOrNull()?.toString()
    }

    /** The first occurrence on or after [from]; null when there is none. */
    fun firstOnOrAfter(
        dateCond: JsonObject,
        from: LocalDate,
    ): String? {
        val value = dateCond["value"] ?: return null
        if (value is JsonPrimitive) return value.contentOrNull
        return occurrences(value.jsonObject, from).firstOrNull()?.toString()
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

    /**
     * Occurrences on or after [from], in order, honouring the end mode. [byRawDate] compares the date before a
     * weekend move with [from], as rschedule's `occurrences({start})` does; [moveWeekends] false leaves them unmoved.
     */
    fun occurrences(
        config: JsonObject,
        from: LocalDate,
        limit: Int = 64,
        byRawDate: Boolean = false,
        moveWeekends: Boolean = true,
    ): List<LocalDate> {
        val start = config["start"]?.jsonPrimitive?.contentOrNull?.let { ScheduleDates.parseIso(it) } ?: return emptyList()
        val frequency = config["frequency"]?.jsonPrimitive?.contentOrNull ?: return emptyList()
        val interval = (config["interval"]?.jsonPrimitive?.intOrNull ?: 1).coerceAtLeast(1)
        val endMode = config["endMode"]?.jsonPrimitive?.contentOrNull ?: "never"
        val maxCount = if (endMode == "after_n_occurrences") config["endOccurrences"]?.jsonPrimitive?.intOrNull else null
        val endDate =
            if (endMode == "on_date") config["endDate"]?.jsonPrimitive?.contentOrNull?.let { ScheduleDates.parseIso(it) } else null
        val patterns = (config["patterns"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val skipWeekend = config["skipWeekend"]?.jsonPrimitive?.contentOrNull == "true"
        val solveBefore = config["weekendSolveMode"]?.jsonPrimitive?.contentOrNull == "before"
        val out = ArrayList<LocalDate>()
        var count = 0
        var period = 0L
        // Enough periods to reach [from] plus [limit] more, with a hard stop for broken configs.
        while (out.size < limit && period < 20_000) {
            val raw = periodDates(start, frequency, interval, period, patterns)
            period++
            for (date in raw) {
                if (date < start) continue
                if (endDate != null && date > endDate) return out
                count++
                if (maxCount != null && count > maxCount) return out
                val moved = if (skipWeekend && moveWeekends) moveOffWeekend(date, solveBefore) else date
                if ((if (byRawDate) date else moved) >= from) out.add(moved)
                if (out.size >= limit) return out
            }
        }
        return out
    }

    private fun periodDates(
        start: LocalDate,
        frequency: String,
        interval: Int,
        period: Long,
        patterns: List<JsonObject>,
    ): List<LocalDate> {
        val step = period * interval
        return when (frequency) {
            "daily" -> listOf(start.plusDays(step))
            "weekly" -> listOf(start.plusWeeks(step))
            "yearly" -> listOf(start.plusYears(step))
            "monthly" -> {
                if (patterns.isEmpty()) {
                    listOf(start.plusMonths(step))
                } else {
                    val month =
                        java.time.YearMonth
                            .from(start)
                            .plusMonths(step)
                    patterns.mapNotNull { patternDate(month, it) }.distinct().sorted()
                }
            }
            else -> emptyList()
        }
    }

    /** `{type: "day", value: N}` (−1 = last day) or `{type: "MO".."SU", value: n}` (−1 = last). */
    private fun patternDate(
        month: java.time.YearMonth,
        pattern: JsonObject,
    ): LocalDate? {
        val type = pattern["type"]?.jsonPrimitive?.contentOrNull ?: return null
        val value = pattern["value"]?.jsonPrimitive?.intOrNull ?: return null
        if (type == "day") {
            val day = if (value < 0) month.lengthOfMonth() + value + 1 else value
            return if (day in 1..month.lengthOfMonth()) month.atDay(day) else null
        }
        val weekday =
            when (type) {
                "MO" -> java.time.DayOfWeek.MONDAY
                "TU" -> java.time.DayOfWeek.TUESDAY
                "WE" -> java.time.DayOfWeek.WEDNESDAY
                "TH" -> java.time.DayOfWeek.THURSDAY
                "FR" -> java.time.DayOfWeek.FRIDAY
                "SA" -> java.time.DayOfWeek.SATURDAY
                "SU" -> java.time.DayOfWeek.SUNDAY
                else -> return null
            }
        val matches = (1..month.lengthOfMonth()).map { month.atDay(it) }.filter { it.dayOfWeek == weekday }
        return if (value < 0) matches.getOrNull(matches.size + value) else matches.getOrNull(value - 1)
    }

    /** The last time a schedule that ends ever falls; null for one that never ends or never fell. */
    fun lastOccurrence(
        config: JsonObject,
        moveWeekends: Boolean = true,
    ): LocalDate? {
        val endMode = config["endMode"]?.jsonPrimitive?.contentOrNull ?: "never"
        if (endMode != "after_n_occurrences" && endMode != "on_date") return null
        val start = config["start"]?.jsonPrimitive?.contentOrNull?.let { ScheduleDates.parseIso(it) } ?: return null
        return occurrences(config, start, limit = 100_000, byRawDate = true, moveWeekends = moveWeekends).lastOrNull()
    }

    /** `getDateWithSkippedWeekend`: a Saturday or Sunday moved to the Friday before, or the Monday after. */
    fun offWeekend(
        date: LocalDate,
        before: Boolean,
    ): LocalDate = moveOffWeekend(date, before)

    private fun moveOffWeekend(
        date: LocalDate,
        before: Boolean,
    ): LocalDate =
        when (date.dayOfWeek) {
            java.time.DayOfWeek.SATURDAY -> if (before) date.minusDays(1) else date.plusDays(2)
            java.time.DayOfWeek.SUNDAY -> if (before) date.minusDays(2) else date.plusDays(1)
            else -> date
        }
}
