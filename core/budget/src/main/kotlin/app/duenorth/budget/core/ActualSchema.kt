package app.duenorth.budget.core

object ActualSchema {
    const val VERSION = "1"
    const val CARD_PAYMENT_PREFIX = "ccPayment:"

    fun cardPaymentKey(accountId: String): String = CARD_PAYMENT_PREFIX + accountId

    val statements: List<String> =
        listOf(
            """
            CREATE TABLE preferences (
                id TEXT PRIMARY KEY,
                value TEXT
            )
            """.trimIndent(),
            """
            CREATE TABLE accounts (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                offbudget INTEGER NOT NULL DEFAULT 0,
                closed INTEGER NOT NULL DEFAULT 0,
                type TEXT NOT NULL DEFAULT 'checking',
                sort_order REAL NOT NULL DEFAULT 0,
                last_reconciled TEXT,
                tombstone INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
            """
            CREATE TABLE payees (
                id TEXT PRIMARY KEY,
                name TEXT,
                transfer_acct TEXT,
                tombstone INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
            """
            CREATE TABLE category_groups (
                id TEXT PRIMARY KEY,
                name TEXT,
                is_income INTEGER NOT NULL DEFAULT 0,
                hidden INTEGER NOT NULL DEFAULT 0,
                sort_order REAL NOT NULL DEFAULT 0,
                tombstone INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
            """
            CREATE TABLE categories (
                id TEXT PRIMARY KEY,
                name TEXT,
                is_income INTEGER NOT NULL DEFAULT 0,
                hidden INTEGER NOT NULL DEFAULT 0,
                cat_group TEXT,
                sort_order REAL NOT NULL DEFAULT 0,
                tombstone INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
            """
            CREATE TABLE transactions (
                id TEXT PRIMARY KEY,
                isParent INTEGER NOT NULL DEFAULT 0,
                isChild INTEGER NOT NULL DEFAULT 0,
                parent_id TEXT,
                acct TEXT,
                category TEXT,
                amount INTEGER NOT NULL DEFAULT 0,
                description TEXT,
                notes TEXT,
                date INTEGER,
                starting_balance_flag INTEGER NOT NULL DEFAULT 0,
                transferred_id TEXT,
                sort_order REAL NOT NULL DEFAULT 0,
                cleared INTEGER NOT NULL DEFAULT 1,
                reconciled INTEGER NOT NULL DEFAULT 0,
                tombstone INTEGER NOT NULL DEFAULT 0,
                schedule TEXT
            )
            """.trimIndent(),
            """
            CREATE TABLE zero_budgets (
                id TEXT PRIMARY KEY,
                month INTEGER,
                category TEXT,
                amount INTEGER NOT NULL DEFAULT 0,
                carryover INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
            """
            CREATE TABLE reflect_budgets (
                id TEXT PRIMARY KEY,
                month INTEGER,
                category TEXT,
                amount INTEGER NOT NULL DEFAULT 0,
                carryover INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
            """
            CREATE TABLE zero_budget_months (
                id INTEGER PRIMARY KEY,
                buffered INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
            """
            CREATE TABLE rules (
                id TEXT PRIMARY KEY,
                stage TEXT,
                conditions_op TEXT NOT NULL DEFAULT 'and',
                conditions TEXT,
                actions TEXT,
                tombstone INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
            """
            CREATE TABLE schedules (
                id TEXT PRIMARY KEY,
                name TEXT,
                rule TEXT NOT NULL,
                completed INTEGER NOT NULL DEFAULT 0,
                posts_transaction INTEGER NOT NULL DEFAULT 0,
                custom_upcoming_length TEXT,
                tombstone INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
            """
            CREATE TABLE schedules_next_date (
                id TEXT PRIMARY KEY,
                schedule_id TEXT NOT NULL,
                local_next_date TEXT,
                local_next_date_ts REAL,
                base_next_date TEXT,
                base_next_date_ts REAL
            )
            """.trimIndent(),
            "CREATE INDEX idx_transactions_acct ON transactions (acct)",
            "CREATE INDEX idx_transactions_category_date ON transactions (category, date)",
            "CREATE INDEX idx_schedules_rule ON schedules (rule)",
        )

    fun create(session: SqlSession) {
        session.transaction {
            statements.forEach(session::exec)
            session.exec(
                "INSERT INTO preferences (id, value) VALUES (?, ?)",
                listOf("dueNorthSchema", VERSION),
            )
            ensure(session)
        }
    }

    /** Adds columns and tables introduced after the first phone files. Safe to call on every open. */
    fun ensure(session: SqlSession) {
        ensureAccountType(session)
        ensureScheduleTables(session)
        ensureTransactionScheduleColumn(session)
        ensureTransactionImportColumns(session)
        ensureAccountBankColumns(session)
        ensureParityTables(session)
        ensureParityColumns(session)
        SyncSchema.forgetShapes(session)
    }

    private fun ensureAccountType(session: SqlSession) {
        val names = session.query("PRAGMA table_info(accounts)").mapNotNull { it.str("name") }.toSet()
        if (names.isEmpty()) return
        if ("type" !in names) {
            session.exec("ALTER TABLE accounts ADD COLUMN type TEXT NOT NULL DEFAULT 'checking'")
        }
        if ("last_reconciled" !in names) {
            session.exec("ALTER TABLE accounts ADD COLUMN last_reconciled TEXT")
        }
    }

    private fun ensureScheduleTables(session: SqlSession) {
        val tables = session.query("SELECT name FROM sqlite_master WHERE type = 'table'").mapNotNull { it.str("name") }.toSet()
        if ("rules" !in tables) {
            session.exec(
                """
                CREATE TABLE rules (
                    id TEXT PRIMARY KEY,
                    stage TEXT,
                    conditions_op TEXT NOT NULL DEFAULT 'and',
                    conditions TEXT,
                    actions TEXT,
                    tombstone INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
        }
        if ("schedules" !in tables) {
            session.exec(
                """
                CREATE TABLE schedules (
                    id TEXT PRIMARY KEY,
                    name TEXT,
                    rule TEXT NOT NULL,
                    completed INTEGER NOT NULL DEFAULT 0,
                    posts_transaction INTEGER NOT NULL DEFAULT 0,
                    custom_upcoming_length TEXT,
                    tombstone INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
        }
        if ("schedules_next_date" !in tables) {
            session.exec(
                """
                CREATE TABLE schedules_next_date (
                    id TEXT PRIMARY KEY,
                    schedule_id TEXT NOT NULL,
                    local_next_date TEXT,
                    local_next_date_ts REAL,
                    base_next_date TEXT,
                    base_next_date_ts REAL
                )
                """.trimIndent(),
            )
        }
    }

    private fun ensureTransactionScheduleColumn(session: SqlSession) {
        val names = session.query("PRAGMA table_info(transactions)").mapNotNull { it.str("name") }.toSet()
        if (names.isEmpty() || "schedule" in names) return
        session.exec("ALTER TABLE transactions ADD COLUMN schedule TEXT")
    }

    private fun ensureTransactionImportColumns(session: SqlSession) {
        val names = session.query("PRAGMA table_info(transactions)").mapNotNull { it.str("name") }.toSet()
        if (names.isEmpty()) return
        if ("financial_id" !in names) {
            session.exec("ALTER TABLE transactions ADD COLUMN financial_id TEXT")
        }
        if ("imported_description" !in names) {
            session.exec("ALTER TABLE transactions ADD COLUMN imported_description TEXT")
        }
        // Actual keeps the bank's copy of a synced row here; its `booked` says whether the bank had posted it.
        if ("raw_synced_data" !in names) {
            session.exec("ALTER TABLE transactions ADD COLUMN raw_synced_data TEXT")
        }
    }

    private fun ensureAccountBankColumns(session: SqlSession) {
        val names = session.query("PRAGMA table_info(accounts)").mapNotNull { it.str("name") }.toSet()
        if (names.isEmpty()) return
        if ("account_id" !in names) {
            session.exec("ALTER TABLE accounts ADD COLUMN account_id TEXT")
        }
        if ("account_sync_source" !in names) {
            session.exec("ALTER TABLE accounts ADD COLUMN account_sync_source TEXT")
        }
    }

    /** Spec 010: tables desktop files carry that phone files lacked. */
    private fun ensureParityTables(session: SqlSession) {
        val tables = session.query("SELECT name FROM sqlite_master WHERE type = 'table'").mapNotNull { it.str("name") }.toSet()
        if ("preferences" !in tables) return
        if ("notes" !in tables) {
            session.exec("CREATE TABLE notes (id TEXT PRIMARY KEY, note TEXT)")
        }
        if ("banks" !in tables) {
            session.exec(
                "CREATE TABLE banks (id TEXT PRIMARY KEY, bank_id TEXT, name TEXT, tombstone INTEGER NOT NULL DEFAULT 0)",
            )
        }
        if ("cleanup_groups" !in tables) {
            session.exec("CREATE TABLE cleanup_groups (id TEXT PRIMARY KEY, name TEXT NOT NULL, tombstone INTEGER DEFAULT 0)")
        }
    }

    /** Spec 010: columns the sync funnel writes, added where a phone file lacks them. */
    private fun ensureParityColumns(session: SqlSession) {
        val wanted =
            mapOf(
                "accounts" to
                    listOf(
                        "bank" to "TEXT",
                        "balance_current" to "INTEGER",
                        "balance_available" to "INTEGER",
                        "balance_limit" to "INTEGER",
                        "last_sync" to "TEXT",
                    ),
                "categories" to listOf("goal_def" to "TEXT", "template_settings" to "TEXT", "cleanup_def" to "TEXT"),
                "zero_budgets" to listOf("goal" to "INTEGER", "long_goal" to "INTEGER"),
                "reflect_budgets" to listOf("goal" to "INTEGER", "long_goal" to "INTEGER"),
                "schedules" to listOf("active" to "INTEGER NOT NULL DEFAULT 0"),
                "schedules_next_date" to listOf("tombstone" to "INTEGER NOT NULL DEFAULT 0"),
                "payees" to listOf("learn_categories" to "INTEGER NOT NULL DEFAULT 1"),
            )
        for ((table, additions) in wanted) {
            val names = session.query("PRAGMA table_info($table)").mapNotNull { it.str("name") }.toSet()
            if (names.isEmpty()) continue
            for ((column, type) in additions) {
                if (column !in names) session.exec("ALTER TABLE $table ADD COLUMN $column $type")
            }
        }
    }
}
