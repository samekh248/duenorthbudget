package app.duenorth.budget.core

import java.time.LocalDate
import java.util.Collections
import java.util.UUID
import java.util.WeakHashMap

enum class ApplyOutcome { Applied, Duplicate, Conflict }

/** One cell an [EditJournal] step wrote: values are [SyncValues.canonical] text. */
data class JournalCell(
    val dataset: String,
    val row: String,
    val column: String,
    val old: String?,
    val new: String?,
    val rowExisted: Boolean,
)

object SyncSchema {
    const val BACKFILL_STATE = "parity-backfill"

    private val statements =
        listOf(
            """
            CREATE TABLE IF NOT EXISTS messages (
                timestamp TEXT PRIMARY KEY,
                dataset TEXT NOT NULL,
                row TEXT NOT NULL,
                column TEXT NOT NULL,
                value TEXT,
                pending INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS conflicts (
                id TEXT PRIMARY KEY,
                dataset TEXT NOT NULL,
                row TEXT NOT NULL,
                column TEXT NOT NULL,
                local_value TEXT,
                remote_value TEXT,
                local_timestamp TEXT,
                remote_timestamp TEXT,
                seen INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS sync_state (
                id TEXT PRIMARY KEY,
                value TEXT
            )
            """.trimIndent(),
        )

    /** Columns that are numbers in Actual's schema and go on the wire as `N:`. */
    val numericColumns: Set<String> =
        setOf(
            "offbudget",
            "closed",
            "tombstone",
            "is_income",
            "hidden",
            "isParent",
            "isChild",
            "amount",
            "date",
            "starting_balance_flag",
            "sort_order",
            "cleared",
            "reconciled",
            "carryover",
            "month",
            "buffered",
            "active",
            "completed",
            "posts_transaction",
            "local_next_date",
            "local_next_date_ts",
            "base_next_date",
            "base_next_date_ts",
            "balance_current",
            "balance_available",
            "balance_limit",
            "goal",
            "long_goal",
            "learn_categories",
        )

    /** Numeric columns the phone schema declares NOT NULL; a null from the wire stores 0. */
    private val notNullNumeric =
        setOf(
            "offbudget",
            "closed",
            "tombstone",
            "is_income",
            "hidden",
            "isParent",
            "isChild",
            "amount",
            "starting_balance_flag",
            "sort_order",
            "cleared",
            "reconciled",
            "carryover",
            "buffered",
            "completed",
            "posts_transaction",
        )

    val columns: Map<String, Set<String>> =
        mapOf(
            "preferences" to setOf("value"),
            "accounts" to
                setOf(
                    "name",
                    "offbudget",
                    "closed",
                    "sort_order",
                    "tombstone",
                    "account_id",
                    "bank",
                    "account_sync_source",
                    "balance_current",
                    "balance_available",
                    "balance_limit",
                    "last_sync",
                    "last_reconciled",
                ),
            "payees" to setOf("name", "transfer_acct", "tombstone", "learn_categories"),
            "category_groups" to setOf("name", "is_income", "hidden", "sort_order", "tombstone"),
            "categories" to
                setOf("name", "is_income", "hidden", "cat_group", "sort_order", "tombstone", "goal_def", "template_settings"),
            "transactions" to
                setOf(
                    "isParent",
                    "isChild",
                    "parent_id",
                    "acct",
                    "category",
                    "amount",
                    "description",
                    "notes",
                    "date",
                    "starting_balance_flag",
                    "transferred_id",
                    "sort_order",
                    "cleared",
                    "reconciled",
                    "tombstone",
                    "schedule",
                    "financial_id",
                    "imported_description",
                ),
            "zero_budgets" to setOf("month", "category", "amount", "carryover", "goal", "long_goal"),
            "reflect_budgets" to setOf("month", "category", "amount", "carryover", "goal", "long_goal"),
            "zero_budget_months" to setOf("buffered"),
            "notes" to setOf("note"),
            "banks" to setOf("bank_id", "name", "tombstone"),
            "rules" to setOf("stage", "conditions_op", "conditions", "actions", "tombstone"),
            "schedules" to setOf("name", "rule", "active", "completed", "posts_transaction", "tombstone"),
            "schedules_next_date" to
                setOf(
                    "schedule_id",
                    "local_next_date",
                    "local_next_date_ts",
                    "base_next_date",
                    "base_next_date_ts",
                    "tombstone",
                ),
        )

    /** Columns that name a budget cell rather than hold a value; undo of a created row leaves them. */
    private val identityColumns = setOf("month", "category", "schedule_id")

    private val clocks: MutableMap<SqlSession, SyncClock> = Collections.synchronizedMap(WeakHashMap())
    private val captures: MutableMap<SqlSession, MutableList<JournalCell>> = Collections.synchronizedMap(WeakHashMap())
    private val tableColumns: MutableMap<SqlSession, MutableMap<String, Map<String, String>>> =
        Collections.synchronizedMap(WeakHashMap())

    fun ensure(session: SqlSession) {
        session.transaction {
            statements.forEach(session::exec)
        }
    }

    fun state(
        session: SqlSession,
        id: String,
    ): String? {
        ensure(session)
        return session.query("SELECT value FROM sync_state WHERE id = ?", listOf(id)).firstOrNull()?.str("value")
    }

    fun putState(
        session: SqlSession,
        id: String,
        value: String,
    ) {
        ensure(session)
        writeState(session, id, value)
    }

    private fun writeState(
        session: SqlSession,
        id: String,
        value: String,
    ) {
        session.exec("INSERT OR REPLACE INTO sync_state (id, value) VALUES (?, ?)", listOf(id, value))
    }

    /** Runs [block] with [clock] as the clock [write] uses for [session]. */
    fun <T> withClock(
        session: SqlSession,
        clock: SyncClock,
        block: () -> T,
    ): T {
        val previous = clocks.put(session, clock)
        try {
            return block()
        } finally {
            if (previous == null) clocks.remove(session) else clocks[session] = previous
        }
    }

    /**
     * The clock bound to [session], or one for this file alone, moved past every
     * stamp the file has written or received.
     */
    fun clockFor(session: SqlSession): SyncClock =
        clocks[session] ?: run {
            val node =
                state(session, "node")?.takeIf { it.length == 16 }
                    ?: UUID
                        .randomUUID()
                        .toString()
                        .replace("-", "")
                        .takeLast(16)
                        .uppercase()
                        .also { putState(session, "node", it) }
            SyncClock(node).also { clock ->
                restore(session, clock)
                clocks[session] = clock
            }
        }

    fun restore(
        session: SqlSession,
        clock: SyncClock,
    ) {
        clock.restoreIfBehind(state(session, "clock"))
        clock.restoreIfBehind(state(session, "lastRemote"))
    }

    /** [recordLocal] with the session's clock and any value type; a write that changes nothing is skipped. */
    fun write(
        session: SqlSession,
        dataset: String,
        row: String,
        column: String,
        value: Any?,
    ) {
        val raw = SyncValues.raw(value)
        val current = readCell(session, dataset, row, column)
        if (current != null && column in columns[dataset].orEmpty() && current.second == SyncValues.canonical(column, raw)) {
            return
        }
        recordLocal(session, clockFor(session), dataset, row, column, raw)
    }

    /**
     * Writes several cells of one row. A new row sends every cell, so desktop
     * never depends on its own column defaults; an existing row skips cells that
     * already hold the value.
     */
    fun writeRow(
        session: SqlSession,
        dataset: String,
        row: String,
        vararg values: Pair<String, Any?>,
    ) {
        val exists = readCell(session, dataset, row, "id") != null
        values.forEach { (column, value) ->
            if (exists) {
                write(session, dataset, row, column, value)
            } else {
                recordLocal(session, clockFor(session), dataset, row, column, SyncValues.raw(value))
            }
        }
    }

    fun recordLocal(
        session: SqlSession,
        clock: SyncClock,
        dataset: String,
        row: String,
        column: String,
        value: String?,
    ) {
        ensure(session)
        val stamp = clock.send()
        session.transaction {
            captures[session]?.takeIf { column in columns[dataset].orEmpty() }?.let { cells ->
                val before = readCell(session, dataset, row, column)
                cells.add(
                    JournalCell(
                        dataset = dataset,
                        row = row,
                        column = column,
                        old = before?.second,
                        new = SyncValues.canonical(column, value),
                        rowExisted = before != null,
                    ),
                )
            }
            writeCell(session, dataset, row, column, value)
            session.exec(
                "INSERT INTO messages (timestamp, dataset, row, column, value, pending) VALUES (?, ?, ?, ?, ?, 1)",
                listOf(stamp, dataset, row, column, SyncValues.serialize(column, value)),
            )
            writeState(session, "clock", stamp)
        }
    }

    internal fun beginCapture(session: SqlSession): MutableList<JournalCell>? {
        if (captures.containsKey(session)) return null
        return mutableListOf<JournalCell>().also { captures[session] = it }
    }

    internal fun endCapture(session: SqlSession) {
        captures.remove(session)
    }

    /**
     * The cell's current value, canonical, paired with true; null when the row
     * does not exist. A column the file lacks reads as null.
     */
    internal fun readCell(
        session: SqlSession,
        dataset: String,
        row: String,
        column: String,
    ): Pair<Boolean, String?>? {
        val known = columnsOf(session, dataset)
        if (known.isEmpty()) return null
        val key = rowKey(session, dataset, row) ?: return null
        if (column !in known) {
            return if (session.query("SELECT 1 AS x FROM $dataset WHERE id = ?", listOf(key)).isEmpty()) null else true to null
        }
        val found = session.query("SELECT ${quote(column)} AS v FROM $dataset WHERE id = ?", listOf(key)).firstOrNull()
        return found?.let { true to SyncValues.canonical(column, it.values["v"]) }
    }

    /** Cells a created row gets on undo: a tombstone where the dataset has one. */
    internal fun hasTombstone(dataset: String): Boolean = "tombstone" in columns[dataset].orEmpty()

    internal fun isIdentity(column: String): Boolean = column in identityColumns

    fun applyRemote(
        session: SqlSession,
        timestamp: String,
        message: CellMessage,
    ): ApplyOutcome {
        ensure(session)
        if (session.query("SELECT timestamp FROM messages WHERE timestamp = ?", listOf(timestamp)).isNotEmpty()) {
            return ApplyOutcome.Duplicate
        }
        val pending =
            session
                .query(
                    """
                    SELECT value, timestamp FROM messages
                    WHERE dataset = ? AND row = ? AND column = ? AND pending = 1
                    ORDER BY timestamp DESC
                    """.trimIndent(),
                    listOf(message.dataset, message.row, message.column),
                ).firstOrNull()
        val remote = SyncValues.deserialize(message.value)
        var outcome = ApplyOutcome.Applied
        session.transaction {
            session.exec(
                "INSERT INTO messages (timestamp, dataset, row, column, value, pending) VALUES (?, ?, ?, ?, ?, 0)",
                listOf(timestamp, message.dataset, message.row, message.column, message.value),
            )
            val local = pending?.let { SyncValues.deserialize(it.str("value")) }
            if (pending != null &&
                SyncValues.canonical(message.column, local) != SyncValues.canonical(message.column, remote)
            ) {
                val id = "${message.dataset}|${message.row}|${message.column}"
                session.exec(
                    """
                    INSERT OR REPLACE INTO conflicts (
                        id, dataset, row, column, local_value, remote_value, local_timestamp, remote_timestamp, seen
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
                    """.trimIndent(),
                    listOf(
                        id,
                        message.dataset,
                        message.row,
                        message.column,
                        local,
                        remote,
                        pending.str("timestamp"),
                        timestamp,
                    ),
                )
                outcome = ApplyOutcome.Conflict
            } else {
                writeCell(session, message.dataset, message.row, message.column, remote)
            }
        }
        return outcome
    }

    /** Records a conflict that no pending message caused, such as an undo of a cell changed remotely. */
    fun recordConflict(
        session: SqlSession,
        cell: JournalCell,
        current: String?,
    ) {
        ensure(session)
        val remoteStamp =
            session
                .query(
                    """
                    SELECT timestamp FROM messages
                    WHERE dataset = ? AND row = ? AND column = ?
                    ORDER BY timestamp DESC LIMIT 1
                    """.trimIndent(),
                    listOf(cell.dataset, cell.row, cell.column),
                ).firstOrNull()
                ?.str("timestamp")
        session.exec(
            """
            INSERT OR REPLACE INTO conflicts (
                id, dataset, row, column, local_value, remote_value, local_timestamp, remote_timestamp, seen
            ) VALUES (?, ?, ?, ?, ?, ?, NULL, ?, 0)
            """.trimIndent(),
            listOf(
                "${cell.dataset}|${cell.row}|${cell.column}",
                cell.dataset,
                cell.row,
                cell.column,
                cell.old,
                current,
                remoteStamp,
            ),
        )
    }

    fun acceptPending(
        session: SqlSession,
        timestamps: List<String>,
    ) {
        timestamps.forEach { stamp ->
            val row =
                session
                    .query(
                        "SELECT dataset, row, column FROM messages WHERE timestamp = ?",
                        listOf(stamp),
                    ).firstOrNull() ?: return@forEach
            val conflict =
                session.query(
                    """
                    SELECT id FROM conflicts
                    WHERE dataset = ? AND row = ? AND column = ? AND local_timestamp = ?
                    """.trimIndent(),
                    listOf(row.str("dataset"), row.str("row"), row.str("column"), stamp),
                )
            if (conflict.isEmpty()) {
                session.exec("UPDATE messages SET pending = 0 WHERE timestamp = ?", listOf(stamp))
            }
        }
    }

    /**
     * Once per budget: rules, schedules and next dates written with plain SQL
     * before spec 010 get pending messages, so desktop finally sees them.
     */
    fun backfill(
        session: SqlSession,
        clock: SyncClock,
    ): Int {
        ensure(session)
        if (state(session, BACKFILL_STATE) != null) return 0
        if (state(session, "cloudFileId").isNullOrBlank()) return 0
        var emitted = 0
        session.transaction {
            for (dataset in listOf("rules", "schedules", "schedules_next_date")) {
                val known = columnsOf(session, dataset)
                if (known.isEmpty()) continue
                val live = if ("tombstone" in known) "WHERE IFNULL(t.tombstone, 0) = 0" else ""
                val rows =
                    session.query(
                        """
                        SELECT t.* FROM $dataset t $live
                        ${if (live.isEmpty()) "WHERE" else "AND"} NOT EXISTS (
                            SELECT 1 FROM messages m WHERE m.dataset = ? AND m.row = t.id
                        )
                        """.trimIndent(),
                        listOf(dataset),
                    )
                for (row in rows) {
                    val id = row.str("id") ?: continue
                    for (column in columns.getValue(dataset)) {
                        if (column !in known) continue
                        var value = SyncValues.raw(row.values[column])
                        if (column == "local_next_date" || column == "base_next_date") value = ScheduleDates.toStored(value)
                        recordLocal(session, clock, dataset, id, column, value)
                        emitted++
                    }
                }
            }
            writeState(session, BACKFILL_STATE, "1")
        }
        return emitted
    }

    /** A downloaded or freshly uploaded file matches the server; nothing to backfill. */
    fun markBackfilled(session: SqlSession) {
        putState(session, BACKFILL_STATE, "1")
    }

    private fun writeCell(
        session: SqlSession,
        dataset: String,
        row: String,
        column: String,
        value: String?,
    ) {
        val allowed = columns[dataset] ?: return
        if (column !in allowed) return
        if (column !in columnsOf(session, dataset)) return
        val key = rowKey(session, dataset, row) ?: return
        ensureRow(session, dataset, key)
        session.exec("UPDATE $dataset SET ${quote(column)} = ? WHERE id = ?", listOf(store(column, value), key))
    }

    private fun store(
        column: String,
        value: String?,
    ): Any? {
        if (column !in numericColumns) return value
        val fallback: Long? = if (column in notNullNumeric) 0L else null
        if (value == null) return fallback
        value.toLongOrNull()?.let { return it }
        val parsed = value.toDoubleOrNull() ?: return fallback
        return if (parsed % 1.0 == 0.0 && kotlin.math.abs(parsed) < 9.0e15) parsed.toLong() else parsed
    }

    /**
     * `zero_budget_months.id` is `2026-10` in desktop files and an integer
     * `202610` in files the phone created before spec 010.
     */
    private fun rowKey(
        session: SqlSession,
        dataset: String,
        row: String,
    ): Any? {
        if (dataset != "zero_budget_months") return row
        val compact = row.replace("-", "")
        val integer = columnsOf(session, dataset)["id"].orEmpty().uppercase().contains("INT")
        if (integer) return compact.toLongOrNull()
        return if (compact.length == 6 && compact.all { it.isDigit() }) compact.take(4) + "-" + compact.drop(4) else row
    }

    private fun columnsOf(
        session: SqlSession,
        dataset: String,
    ): Map<String, String> {
        val cache = tableColumns.getOrPut(session) { HashMap() }
        cache[dataset]?.let { if (it.isNotEmpty()) return it }
        val found =
            session
                .query("PRAGMA table_info($dataset)")
                .mapNotNull { info -> info.str("name")?.let { it to info.str("type").orEmpty() } }
                .toMap()
        cache[dataset] = found
        return found
    }

    /** Forget cached table shapes after a schema change in this session. */
    internal fun forgetShapes(session: SqlSession) {
        tableColumns.remove(session)
    }

    private fun quote(column: String): String = "\"" + column.replace("\"", "") + "\""

    private fun ensureRow(
        session: SqlSession,
        dataset: String,
        key: Any?,
    ) {
        when (dataset) {
            "preferences" -> session.exec("INSERT OR IGNORE INTO preferences (id, value) VALUES (?, '')", listOf(key))
            "accounts" -> session.exec("INSERT OR IGNORE INTO accounts (id, name) VALUES (?, '')", listOf(key))
            "payees" -> session.exec("INSERT OR IGNORE INTO payees (id, name) VALUES (?, '')", listOf(key))
            "category_groups" ->
                session.exec("INSERT OR IGNORE INTO category_groups (id, name) VALUES (?, '')", listOf(key))
            "categories" -> session.exec("INSERT OR IGNORE INTO categories (id, name) VALUES (?, '')", listOf(key))
            "transactions" -> session.exec("INSERT OR IGNORE INTO transactions (id, amount) VALUES (?, 0)", listOf(key))
            "zero_budgets" ->
                session.exec(
                    "INSERT OR IGNORE INTO zero_budgets (id, month, category, amount, carryover) VALUES (?, 0, '', 0, 0)",
                    listOf(key),
                )
            "reflect_budgets" ->
                session.exec(
                    "INSERT OR IGNORE INTO reflect_budgets (id, month, category, amount, carryover) VALUES (?, 0, '', 0, 0)",
                    listOf(key),
                )
            "zero_budget_months" ->
                session.exec("INSERT OR IGNORE INTO zero_budget_months (id, buffered) VALUES (?, 0)", listOf(key))
            "notes" -> session.exec("INSERT OR IGNORE INTO notes (id) VALUES (?)", listOf(key))
            "banks" -> session.exec("INSERT OR IGNORE INTO banks (id) VALUES (?)", listOf(key))
            "rules" -> session.exec("INSERT OR IGNORE INTO rules (id) VALUES (?)", listOf(key))
            "schedules" -> session.exec("INSERT OR IGNORE INTO schedules (id, rule) VALUES (?, '')", listOf(key))
            "schedules_next_date" ->
                session.exec("INSERT OR IGNORE INTO schedules_next_date (id, schedule_id) VALUES (?, '')", listOf(key))
        }
    }
}

object BudgetEdits {
    fun assignmentId(
        month: Int,
        categoryId: String,
    ): String = "$month-$categoryId"

    fun assign(
        session: SqlSession,
        clock: SyncClock,
        month: Int,
        categoryId: String,
        amount: Long,
    ) {
        // Actual's `setBudget`: update the row already there for (month, category), else insert one.
        // Tracking files keep budgets in reflect_budgets.
        val tracking =
            session
                .query("SELECT value FROM preferences WHERE id = 'budgetType'")
                .firstOrNull()
                ?.str("value") == "tracking"
        val table = if (tracking) "reflect_budgets" else "zero_budgets"
        val existing =
            session
                .query("SELECT id FROM $table WHERE month = ? AND category = ?", listOf(month, categoryId))
                .firstOrNull()
                ?.str("id")
        if (existing != null) {
            SyncSchema.recordLocal(session, clock, table, existing, "amount", amount.toString())
            return
        }
        val id = assignmentId(month, categoryId)
        SyncSchema.recordLocal(session, clock, table, id, "month", month.toString())
        SyncSchema.recordLocal(session, clock, table, id, "category", categoryId)
        SyncSchema.recordLocal(session, clock, table, id, "amount", amount.toString())
        SyncSchema.recordLocal(session, clock, table, id, "carryover", "0")
    }

    fun addTransaction(
        session: SqlSession,
        clock: SyncClock,
        today: LocalDate,
        payeeName: String,
        amountMinor: Long,
        ids: () -> String,
    ): String {
        val account =
            session
                .query(
                    """
                    SELECT id FROM accounts
                    WHERE IFNULL(offbudget, 0) = 0 AND IFNULL(tombstone, 0) = 0
                    LIMIT 1
                    """.trimIndent(),
                ).firstOrNull()
                ?.str("id")
        val accountId =
            account ?: ids().also { created ->
                SyncSchema.recordLocal(session, clock, "accounts", created, "name", "checking")
                SyncSchema.recordLocal(session, clock, "accounts", created, "offbudget", "0")
                SyncSchema.recordLocal(session, clock, "accounts", created, "closed", "0")
                SyncSchema.recordLocal(session, clock, "accounts", created, "tombstone", "0")
            }
        val payeeId = ids()
        SyncSchema.recordLocal(session, clock, "payees", payeeId, "name", payeeName)
        SyncSchema.recordLocal(session, clock, "payees", payeeId, "tombstone", "0")
        val transactionId = ids()
        val date = today.year * 10_000 + today.monthValue * 100 + today.dayOfMonth
        SyncSchema.recordLocal(session, clock, "transactions", transactionId, "acct", accountId)
        SyncSchema.recordLocal(session, clock, "transactions", transactionId, "amount", amountMinor.toString())
        SyncSchema.recordLocal(session, clock, "transactions", transactionId, "description", payeeId)
        SyncSchema.recordLocal(session, clock, "transactions", transactionId, "date", date.toString())
        SyncSchema.recordLocal(session, clock, "transactions", transactionId, "isParent", "0")
        SyncSchema.recordLocal(session, clock, "transactions", transactionId, "isChild", "0")
        SyncSchema.recordLocal(session, clock, "transactions", transactionId, "tombstone", "0")
        return transactionId
    }
}
