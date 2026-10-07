package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class RegisterBookTest {
    @Test
    fun ensureAddsAccountType() {
        val file = File.createTempFile("old", ".sqlite")
        file.delete()
        JdbcSessionOpener().use(file) { session ->
            session.exec(
                """
                CREATE TABLE accounts (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    offbudget INTEGER NOT NULL DEFAULT 0,
                    closed INTEGER NOT NULL DEFAULT 0,
                    sort_order REAL NOT NULL DEFAULT 0,
                    tombstone INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
            ActualSchema.ensure(session)
            val names = session.query("PRAGMA table_info(accounts)").map { it.str("name") }
            assertTrue(names.contains("type"))
            session.exec("INSERT INTO accounts (id, name) VALUES ('a', 'Checking')")
            assertEquals("checking", session.query("SELECT type FROM accounts").first().str("type"))
        }
    }

    @Test
    fun parsesAmountsAndDates() {
        assertEquals(1_240L, MoneyParse.parseMagnitude("12.40", 2))
        assertEquals(1_240L, MoneyParse.parseMagnitude("12.4", 2))
        assertEquals(124_050L, MoneyParse.parseMagnitude("1,240.50", 2))
        assertNull(MoneyParse.parseMagnitude("12.405", 2))
        assertNull(MoneyParse.parseMagnitude("abc", 2))
        assertEquals(0L, MoneyParse.parseMagnitude("0", 2))
        assertEquals(12L, MoneyParse.parseMagnitude("12", 0))
        assertNull(MoneyParse.parseMagnitude("12.0", 0))
        assertEquals(20261007, RegisterEntry.parseDate("2026-10-07"))
        assertEquals(20261007, RegisterEntry.parseDate("20261007"))
        assertNull(RegisterEntry.parseDate("2026-02-31"))
        val rejected =
            RegisterEntry.transaction("id", "checking", "2026-10-07", " ", "12.40", 2, true, null, "")
                as EntryResult.Rejected
        assertEquals(RegisterCopy.ENTER_PAYEE, rejected.reason)
        val zero =
            RegisterEntry.transaction("id", "checking", "2026-10-07", "Cafe", "0", 2, true, null, "")
                as EntryResult.Rejected
        assertEquals(RegisterCopy.AMOUNT_ZERO, zero.reason)
    }

    @Test
    fun registerIsNewestFirstAndOffBudgetDoesNotChangeToBudget() {
        db { session, book ->
            book.save(draft("old", amount = -100, date = 20261001, payee = "Bus"))
            book.save(draft("new", amount = -250, date = 20261002, payee = "Cafe"))
            val page = book.read("checking")!!
            assertEquals(listOf("new", "old"), page.rows.map { it.id })
            assertEquals("oct 2", RegisterEntry.registerDateLabel(page.rows.first().date))
            assertEquals("Cafe", page.rows.first().payee)
            assertEquals("Groceries", page.rows.first().categoryLabel)
            assertEquals(-350L, page.balanceMinor)
            val before = shell(session).headerMinor
            book.save(draft("off", account = "savings", amount = -1_240, category = "c-food"))
            assertEquals(before, shell(session).headerMinor)
            assertEquals(10_000L - 350L, available(shell(session), "Groceries"))
            assertEquals(-1_240L, book.read("savings")!!.balanceMinor)
            assertEquals(listOf("Cafe"), page.matching("caf").map { it.payee })
            assertEquals(2, book.read("checking")!!.matching(" ").size)
        }
    }

    @Test
    fun fiveHundredRowsReadUnderASecond() {
        db { session, book ->
            session.transaction {
                repeat(500) { index ->
                    session.exec(
                        """
                        INSERT INTO transactions
                            (id, acct, amount, description, date, sort_order, category)
                        VALUES (?, 'checking', -1, 'p-cafe', 20261007, ?, 'c-food')
                        """.trimIndent(),
                        listOf("t-$index", index.toDouble()),
                    )
                }
            }
            book.read("checking")
            val started = System.nanoTime()
            val page = book.read("checking")!!
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            assertTrue("read took ${elapsedMs}ms", elapsedMs < 1_000)
            assertEquals("t-499", page.rows.first().id)
            assertEquals(500, page.rows.size)
            assertEquals(-500L, page.balanceMinor)
        }
    }

    @Test
    fun expenseIncomeValidationAndRepeatedSave() {
        db { session, book ->
            val before = shell(session).headerMinor
            val saved = book.save(draft("groceries", amount = -1_240, payee = "Market"))
            assertTrue(saved is WriteResult.Saved)
            assertEquals(-1_240L, book.read("checking")!!.balanceMinor)
            assertEquals(-1_240L, spent(session, "c-food"))
            assertEquals(10_000L - 1_240L, available(shell(session), "Groceries"))
            assertEquals(before, shell(session).headerMinor)
            book.save(draft("groceries", amount = -1_240, payee = "Market"))
            assertEquals(1, aliveCount(session))
            val started = System.nanoTime()
            book.save(draft("fast", amount = -100, payee = "Bus"))
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            assertTrue("save took ${elapsedMs}ms", elapsedMs < 100)
            assertEquals(
                RegisterCopy.ENTER_PAYEE,
                (book.save(draft("blank", payee = " ")) as WriteResult.Rejected).reason,
            )
            assertEquals(
                RegisterCopy.AMOUNT_ZERO,
                (book.save(draft("zero", amount = 0)) as WriteResult.Rejected).reason,
            )
            book.save(draft("income", amount = 50_000, category = "c-sal", payee = "Work"))
            assertEquals(before + 50_000L, shell(session).headerMinor)
            book.save(draft("later", amount = -1_240, date = 20261102))
            val octoberFood = 10_000L - 1_240L - 100L
            assertEquals(octoberFood, available(shell(session), "Groceries"))
            assertEquals(octoberFood - 1_240L, available(shell(session, LocalDate.of(2026, 11, 2)), "Groceries"))
        }
    }

    @Test
    fun categorizeChangeAndClear() {
        db { session, book ->
            book.save(draft("inbox", category = null, payee = "Cafe"))
            assertEquals(listOf("Cafe"), shell(session).inbox.map { it.payee })
            book.setCategory("inbox", "c-food", false)
            assertTrue(shell(session).inbox.isEmpty())
            assertEquals(-1_000L, spent(session, "c-food"))
            book.setCategory("inbox", "c-house", false)
            assertEquals(0L, spent(session, "c-food"))
            assertEquals(-1_000L, spent(session, "c-house"))
            book.setCategory("inbox", null, false)
            assertEquals(listOf("Cafe"), shell(session).inbox.map { it.payee })
            assertEquals(0L, spent(session, "c-house"))
        }
    }

    @Test
    fun splitRefusesABadSumAndUnsplitRestoresOneCategory() {
        db { session, book ->
            book.save(draft("market", amount = -3_000, payee = "Market"))
            val refused =
                book.split(
                    "market",
                    listOf(SplitPart("c-food", -2_000), SplitPart("c-house", -500)),
                    false,
                )
            assertEquals(RegisterCopy.SPLIT_MUST_ADD, (refused as WriteResult.Rejected).reason)
            assertEquals(-3_000L, spent(session, "c-food"))
            assertFalse(
                book
                    .read("checking")!!
                    .rows
                    .single()
                    .parent,
            )
            assertTrue(
                book.split(
                    "market",
                    listOf(SplitPart("c-food", -2_000), SplitPart("c-house", -1_000)),
                    false,
                ) is WriteResult.Saved,
            )
            assertEquals(-2_000L, spent(session, "c-food"))
            assertEquals(-1_000L, spent(session, "c-house"))
            assertEquals(-3_000L, book.read("checking")!!.balanceMinor)
            val row = book.read("checking")!!.rows.single()
            assertEquals(RegisterCopy.SPLIT, row.categoryLabel)
            assertEquals(2, row.parts.size)
            book.unsplit("market", false)
            assertEquals(-3_000L, spent(session, "c-food"))
            assertEquals(0L, spent(session, "c-house"))
            assertFalse(
                book
                    .read("checking")!!
                    .rows
                    .single()
                    .parent,
            )
        }
    }

    @Test
    fun transferMovesBothBalancesAndDeletesAsAPair() {
        db { session, book ->
            val before = shell(session).headerMinor
            assertTrue(
                book.transfer(TransferDraft("move", "checking", "cash", 20261007, 10_000, "c-food", null))
                    is WriteResult.Saved,
            )
            assertEquals(-10_000L, book.read("checking")!!.balanceMinor)
            assertEquals(10_000L, book.read("cash")!!.balanceMinor)
            assertEquals(before, shell(session).headerMinor)
            assertEquals(0L, spent(session, "c-food"))
            assertEquals(
                RegisterCopy.TRANSFER,
                book
                    .read("checking")!!
                    .rows
                    .single()
                    .categoryLabel,
            )
            book.delete("move", false)
            assertEquals(0L, book.read("checking")!!.balanceMinor)
            assertEquals(0L, book.read("cash")!!.balanceMinor)
            assertEquals(0, aliveCount(session))
            val needsCategory =
                book.transfer(TransferDraft("out", "checking", "savings", 20261007, 5_000, null, null))
                    as WriteResult.Rejected
            assertEquals(RegisterCopy.CHOOSE_CATEGORY, needsCategory.reason)
            book.transfer(TransferDraft("out", "checking", "savings", 20261007, 5_000, "c-food", null))
            assertEquals(5_000L, book.read("savings")!!.balanceMinor)
            assertEquals(-5_000L, spent(session, "c-food"))
        }
    }

    @Test
    fun envelopeCardPaymentMovesAndTrackingDoesNot() {
        db { session, book ->
            session.exec("UPDATE zero_budgets SET amount = 4000 WHERE category = 'c-food'")
            book.save(draft("debit", account = "checking", amount = -4_000))
            assertEquals(0L, available(shell(session), "Groceries"))
            assertEquals(0L, available(shell(session), "Payment"))
            book.delete("debit", false)
            session.exec("UPDATE zero_budgets SET amount = 4000 WHERE category = 'c-food'")
            val before = shell(session).headerMinor
            book.save(draft("swipe", account = "card", amount = -4_000, payee = "Store"))
            assertEquals(0L, available(shell(session), "Groceries"))
            assertEquals(4_000L, available(shell(session), "Payment"))
            book.transfer(TransferDraft("bill", "checking", "card", 20261007, 4_000, null, null))
            val after = shell(session)
            assertEquals(0L, available(after, "Groceries"))
            assertEquals(0L, available(after, "Payment"))
            assertEquals(before, after.headerMinor)
            assertEquals(-4_000L, book.read("checking")!!.balanceMinor)
            assertEquals(0L, book.read("card")!!.balanceMinor)
        }
        db(tracking = true) { session, book ->
            book.save(draft("swipe", account = "card", amount = -4_000, payee = "Store"))
            assertEquals(0L, available(shell(session), "Groceries"))
            assertEquals(0L, available(shell(session), "Payment"))
        }
    }

    @Test
    fun editsDeletesNotesAndReconciledRows() {
        db { session, book ->
            book.save(draft("row", amount = -1_000))
            book.save(draft("row", amount = -1_400))
            assertEquals(-1_400L, book.read("checking")!!.balanceMinor)
            assertEquals(-1_400L, spent(session, "c-food"))
            book.save(draft("row", amount = -1_400, note = "milk"))
            assertEquals(-1_400L, book.read("checking")!!.balanceMinor)
            assertEquals(
                "milk",
                book
                    .read("checking")!!
                    .rows
                    .single()
                    .note,
            )
            book.save(draft("other", amount = -500))
            book.delete("other", false)
            assertEquals(-1_400L, book.read("checking")!!.balanceMinor)
            assertEquals(-1_400L, spent(session, "c-food"))
            session.exec("UPDATE transactions SET reconciled = 1 WHERE id = 'row'")
            val blocked = book.save(draft("row", amount = -2_000)) as WriteResult.Confirm
            assertEquals(RegisterCopy.RECONCILE_WARN, blocked.reason)
            assertEquals(-1_400L, book.read("checking")!!.balanceMinor)
            book.save(draft("row", amount = -2_000, force = true))
            assertEquals(-2_000L, book.read("checking")!!.balanceMinor)
            val deleteBlocked = book.delete("row", false) as WriteResult.Confirm
            assertEquals(RegisterCopy.RECONCILE_WARN, deleteBlocked.reason)
            assertEquals(1, aliveCount(session))
            book.delete("row", true)
            assertEquals(0, aliveCount(session))
        }
    }

    @Test
    fun librarySaveIsVisibleOnTheShell() {
        val root =
            java.nio.file.Files
                .createTempDirectory("budgets")
                .toFile()
        val library = BudgetLibrary(root, JdbcSessionOpener(), BudgetClock { LocalDate.of(2026, 10, 7) })
        val created = library.create("Home", "USD") as CreateResult.Created
        JdbcSessionOpener().use(File(root, created.id).resolve("db.sqlite")) { seed(it, false) }
        val saved =
            library.saveTransaction(
                created.id,
                draft("groceries", amount = -1_240, payee = "Market"),
            )
        assertTrue(saved is WriteResult.Saved)
        val shell = library.readShell(created.id)!!
        assertEquals(10_000L - 1_240L, available(shell, "Groceries"))
        val page = library.readRegister(created.id, "checking")!!
        assertEquals(-1_240L, page.balanceMinor)
        assertEquals("Market", page.rows.single().payee)
    }

    @Test
    fun registerRefreshWaitsUntilTheFingerLifts() {
        db { _, book ->
            book.save(draft("row"))
            val page = book.read("checking")!!
            val gate = RefreshGate<RegisterPage>()
            gate.offer(page)
            gate.beginGesture()
            val newer = page.copy(balanceMinor = page.balanceMinor - 1)
            assertFalse(gate.offer(newer))
            assertEquals(page.balanceMinor, gate.visible!!.balanceMinor)
            gate.endGesture()
            assertEquals(newer.balanceMinor, gate.visible!!.balanceMinor)
        }
    }

    private fun db(
        tracking: Boolean = false,
        block: (SqlSession, RegisterBook) -> Unit,
    ) {
        val file = File.createTempFile("register", ".sqlite")
        file.delete()
        try {
            JdbcSessionOpener().use(file) { session ->
                ActualSchema.create(session)
                seed(session, tracking)
                var generated = 0
                block(session, RegisterBook(session) { "gen-${generated++}" })
            }
        } finally {
            file.delete()
        }
    }

    private fun seed(
        session: SqlSession,
        tracking: Boolean,
    ) {
        session.exec(
            "INSERT OR REPLACE INTO preferences (id, value) VALUES (?, ?)",
            listOf("budgetType", if (tracking) "tracking" else "envelope"),
        )
        session.exec("INSERT OR REPLACE INTO preferences (id, value) VALUES (?, ?)", listOf("currency", "USD"))
        session.exec(
            "INSERT OR REPLACE INTO preferences (id, value) VALUES (?, ?)",
            listOf(ActualSchema.cardPaymentKey("card"), "c-pay"),
        )
        session.exec(
            "INSERT INTO category_groups (id, name, is_income, sort_order) VALUES ('g-inc', 'Income', 1, 0)",
        )
        session.exec(
            "INSERT INTO category_groups (id, name, is_income, sort_order) VALUES ('g-food', 'Groceries', 0, 1)",
        )
        session.exec(
            "INSERT INTO category_groups (id, name, is_income, sort_order) VALUES ('g-house', 'Household', 0, 2)",
        )
        session.exec(
            "INSERT INTO category_groups (id, name, is_income, sort_order) VALUES ('g-pay', 'Payment', 0, 3)",
        )
        session.exec(
            "INSERT INTO categories (id, name, is_income, cat_group) VALUES ('c-sal', 'Salary', 1, 'g-inc')",
        )
        session.exec(
            "INSERT INTO categories (id, name, is_income, cat_group) VALUES ('c-food', 'Groceries', 0, 'g-food')",
        )
        session.exec(
            "INSERT INTO categories (id, name, is_income, cat_group) VALUES ('c-house', 'Household', 0, 'g-house')",
        )
        session.exec(
            "INSERT INTO categories (id, name, is_income, cat_group) VALUES ('c-pay', 'Card', 0, 'g-pay')",
        )
        session.exec(
            "INSERT INTO accounts (id, name, offbudget, type, sort_order) VALUES ('checking', 'Checking', 0, 'checking', 0)",
        )
        session.exec(
            "INSERT INTO accounts (id, name, offbudget, type, sort_order) VALUES ('cash', 'Cash', 0, 'checking', 1)",
        )
        session.exec(
            "INSERT INTO accounts (id, name, offbudget, type, sort_order) VALUES ('card', 'Visa', 0, 'credit', 2)",
        )
        session.exec(
            "INSERT INTO accounts (id, name, offbudget, type, sort_order) VALUES ('savings', 'Savings', 1, 'savings', 3)",
        )
        session.exec("INSERT INTO payees (id, name) VALUES ('p-cafe', 'Cafe')")
        val table = if (tracking) "reflect_budgets" else "zero_budgets"
        val food = if (tracking) 4_000L else 10_000L
        session.exec(
            "INSERT INTO $table (id, month, category, amount, carryover) VALUES ('b-food', 202610, 'c-food', ?, 0)",
            listOf(food),
        )
    }

    private fun draft(
        id: String,
        account: String = "checking",
        amount: Long = -1_000,
        category: String? = "c-food",
        date: Int = 20261007,
        payee: String = "Cafe",
        note: String? = null,
        force: Boolean = false,
    ) = TransactionDraft(id, account, date, payee, amount, category, note, force)

    private fun shell(
        session: SqlSession,
        day: LocalDate = LocalDate.of(2026, 10, 7),
    ) = ShellReader.read(session, BudgetMetadata("home", "Home"), day)

    private fun available(
        shell: MonthShell,
        group: String,
    ) = shell.groups.first { it.name == group }.availableMinor

    private fun spent(
        session: SqlSession,
        category: String,
    ) = session
        .query(
            """
            SELECT COALESCE(SUM(amount), 0) AS spent
            FROM transactions
            WHERE category = ? AND IFNULL(tombstone, 0) = 0 AND IFNULL(isParent, 0) = 0
            """.trimIndent(),
            listOf(category),
        ).first()
        .long("spent")

    private fun aliveCount(session: SqlSession) =
        session
            .query("SELECT COUNT(*) AS n FROM transactions WHERE IFNULL(tombstone, 0) = 0")
            .first()
            .long("n")
            .toInt()
}
