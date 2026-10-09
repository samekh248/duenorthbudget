package app.duenorth.budget.core

import java.time.LocalDate

enum class ApplyOutcome { Applied, Duplicate, Conflict }

object SyncSchema {
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

    private val numeric =
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
        )

    private val columns =
        mapOf(
            "preferences" to setOf("value"),
            "accounts" to setOf("name", "offbudget", "closed", "sort_order", "tombstone"),
            "payees" to setOf("name", "transfer_acct", "tombstone"),
            "category_groups" to setOf("name", "is_income", "hidden", "sort_order", "tombstone"),
            "categories" to setOf("name", "is_income", "hidden", "cat_group", "sort_order", "tombstone"),
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
                ),
            "zero_budgets" to setOf("month", "category", "amount", "carryover"),
            "reflect_budgets" to setOf("month", "category", "amount", "carryover"),
            "zero_budget_months" to setOf("buffered"),
        )

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

    fun recordLocal(
        session: SqlSession,
        clock: SyncClock,
        dataset: String,
        row: String,
        column: String,
        value: String,
    ) {
        ensure(session)
        val stamp = clock.send()
        session.transaction {
            writeCell(session, dataset, row, column, value)
            session.exec(
                "INSERT INTO messages (timestamp, dataset, row, column, value, pending) VALUES (?, ?, ?, ?, ?, 1)",
                listOf(stamp, dataset, row, column, value),
            )
            writeState(session, "clock", stamp)
        }
    }

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
        var outcome = ApplyOutcome.Applied
        session.transaction {
            session.exec(
                "INSERT INTO messages (timestamp, dataset, row, column, value, pending) VALUES (?, ?, ?, ?, ?, 0)",
                listOf(timestamp, message.dataset, message.row, message.column, message.value),
            )
            if (pending != null && pending.str("value") != message.value) {
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
                        pending.str("value"),
                        message.value,
                        pending.str("timestamp"),
                        timestamp,
                    ),
                )
                outcome = ApplyOutcome.Conflict
            } else {
                writeCell(session, message.dataset, message.row, message.column, message.value)
            }
        }
        return outcome
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

    private fun writeCell(
        session: SqlSession,
        dataset: String,
        row: String,
        column: String,
        value: String,
    ) {
        val allowed = columns[dataset] ?: return
        if (column !in allowed) return
        ensureRow(session, dataset, row)
        val stored: Any? = if (column in numeric) value.toLongOrNull() ?: 0L else value
        val quoted = "\"" + column.replace("\"", "") + "\""
        if (dataset == "zero_budget_months") {
            session.exec(
                "UPDATE zero_budget_months SET $quoted = ? WHERE id = ?",
                listOf(stored, row.toLongOrNull() ?: 0L),
            )
        } else {
            session.exec("UPDATE $dataset SET $quoted = ? WHERE id = ?", listOf(stored, row))
        }
    }

    private fun ensureRow(
        session: SqlSession,
        dataset: String,
        row: String,
    ) {
        when (dataset) {
            "preferences" -> session.exec("INSERT OR IGNORE INTO preferences (id, value) VALUES (?, '')", listOf(row))
            "accounts" -> session.exec("INSERT OR IGNORE INTO accounts (id, name) VALUES (?, '')", listOf(row))
            "payees" -> session.exec("INSERT OR IGNORE INTO payees (id, name) VALUES (?, '')", listOf(row))
            "category_groups" ->
                session.exec("INSERT OR IGNORE INTO category_groups (id, name) VALUES (?, '')", listOf(row))
            "categories" -> session.exec("INSERT OR IGNORE INTO categories (id, name) VALUES (?, '')", listOf(row))
            "transactions" -> session.exec("INSERT OR IGNORE INTO transactions (id, amount) VALUES (?, 0)", listOf(row))
            "zero_budgets" ->
                session.exec(
                    "INSERT OR IGNORE INTO zero_budgets (id, month, category, amount, carryover) VALUES (?, 0, '', 0, 0)",
                    listOf(row),
                )
            "reflect_budgets" ->
                session.exec(
                    "INSERT OR IGNORE INTO reflect_budgets (id, month, category, amount, carryover) VALUES (?, 0, '', 0, 0)",
                    listOf(row),
                )
            "zero_budget_months" ->
                session.exec(
                    "INSERT OR IGNORE INTO zero_budget_months (id, buffered) VALUES (?, 0)",
                    listOf(row.toLongOrNull() ?: 0L),
                )
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
        val id = assignmentId(month, categoryId)
        SyncSchema.recordLocal(session, clock, "zero_budgets", id, "month", month.toString())
        SyncSchema.recordLocal(session, clock, "zero_budgets", id, "category", categoryId)
        SyncSchema.recordLocal(session, clock, "zero_budgets", id, "amount", amount.toString())
        SyncSchema.recordLocal(session, clock, "zero_budgets", id, "carryover", "0")
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
