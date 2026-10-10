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
        }
    }

    /** Adds columns and tables introduced after the first phone files. Safe to call on every open. */
    fun ensure(session: SqlSession) {
        ensureAccountType(session)
        ensureScheduleTables(session)
        ensureTransactionScheduleColumn(session)
        ensureTransactionImportColumns(session)
        ensureAccountBankColumns(session)
    }

    private fun ensureAccountType(session: SqlSession) {
        val names = session.query("PRAGMA table_info(accounts)").mapNotNull { it.str("name") }.toSet()
        if (names.isEmpty() || "type" in names) return
        session.exec("ALTER TABLE accounts ADD COLUMN type TEXT NOT NULL DEFAULT 'checking'")
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
}
