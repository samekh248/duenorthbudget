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
                tombstone INTEGER NOT NULL DEFAULT 0
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
            "CREATE INDEX idx_transactions_acct ON transactions (acct)",
            "CREATE INDEX idx_transactions_category_date ON transactions (category, date)",
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

    /** Adds columns introduced after the first phone files. Safe to call on every open. */
    fun ensure(session: SqlSession) {
        val names = session.query("PRAGMA table_info(accounts)").mapNotNull { it.str("name") }.toSet()
        if (names.isEmpty() || "type" in names) return
        session.exec("ALTER TABLE accounts ADD COLUMN type TEXT NOT NULL DEFAULT 'checking'")
    }
}
