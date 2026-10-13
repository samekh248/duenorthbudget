package app.duenorth.budget.core

import app.duenorth.budget.core.parity.ParityFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/** Spec 010 T005: every dataset and column the funnel gained round-trips through the spec 004 server. */
class SyncDatasetsTest {
    @Test
    fun everyNewDatasetRoundTripsToAnotherPhone() {
        LocalActualServer().use { server ->
            val remote = server.addFile("Home", phoneFile())
            SyncPhone(server).use { first ->
                first.openSignedIn(remote)
                first.library.useDatabase(remote.fileId) { session ->
                    SyncSchema.writeRow(
                        session,
                        "rules",
                        "r1",
                        "stage" to "pre",
                        "conditions_op" to "and",
                        "conditions" to CONDITIONS,
                        "actions" to ACTIONS,
                        "tombstone" to 0,
                    )
                    SyncSchema.writeRow(session, "rules", "r2", "stage" to null, "conditions" to "[]", "actions" to "[]")
                    SyncSchema.writeRow(
                        session,
                        "schedules",
                        "s1",
                        "name" to "Rent",
                        "rule" to "r1",
                        "active" to 1,
                        "completed" to 0,
                        "posts_transaction" to 1,
                        "tombstone" to 0,
                    )
                    SyncSchema.writeRow(
                        session,
                        "schedules_next_date",
                        "nd1",
                        "schedule_id" to "s1",
                        "local_next_date" to 20261101,
                        "local_next_date_ts" to STAMP_MS,
                        "base_next_date" to 20261101,
                        "base_next_date_ts" to STAMP_MS,
                        "tombstone" to 0,
                    )
                    SyncSchema.writeRow(session, "notes", "c-food", "note" to "#template 50")
                    SyncSchema.writeRow(session, "banks", "b1", "bank_id" to "ins_1", "name" to "First Bank", "tombstone" to 0)
                    SyncSchema.writeRow(
                        session,
                        "accounts",
                        "checking",
                        "account_id" to "ext-1",
                        "bank" to "b1",
                        "account_sync_source" to "simpleFin",
                        "balance_current" to 12_345,
                        "balance_available" to 12_000,
                        "last_sync" to "1790000000000",
                    )
                    SyncSchema.writeRow(
                        session,
                        "categories",
                        "c-food",
                        "goal_def" to GOAL_DEF,
                        "template_settings" to """{"source":"notes"}""",
                        "sort_order" to 1.5,
                    )
                    SyncSchema.writeRow(
                        session,
                        "zero_budgets",
                        "202610-c-food",
                        "month" to 202610,
                        "category" to "c-food",
                        "amount" to 2_500,
                        "carryover" to 0,
                        "goal" to 5_000,
                        "long_goal" to 1,
                    )
                    SyncSchema.writeRow(session, "payees", "p1", "name" to "Landlord", "learn_categories" to 0, "tombstone" to 0)
                    SyncSchema.writeRow(
                        session,
                        "transactions",
                        "t-pay",
                        "financial_id" to "fin-1",
                        "imported_description" to "ACME PAYROLL",
                    )
                }
                first.sync.sync(remote.fileId)
            }

            val wire = server.plainMessages(remote.fileId)

            fun sent(
                dataset: String,
                row: String,
                column: String,
            ): String? = wire.lastOrNull { it.dataset == dataset && it.row == row && it.column == column }?.value
            assertEquals("S:$CONDITIONS", sent("rules", "r1", "conditions"))
            assertEquals("0:", sent("rules", "r2", "stage"))
            assertEquals("N:20261101", sent("schedules_next_date", "nd1", "local_next_date"))
            assertEquals("N:$STAMP_MS", sent("schedules_next_date", "nd1", "local_next_date_ts"))
            assertEquals("N:1.5", sent("categories", "c-food", "sort_order"))
            assertEquals("N:0", sent("payees", "p1", "learn_categories"))
            assertEquals("S:simpleFin", sent("accounts", "checking", "account_sync_source"))
            assertTrue(wire.all { it.value.startsWith("N:") || it.value.startsWith("S:") || it.value == "0:" })

            SyncPhone(server).use { second ->
                second.openSignedIn(remote)
                second.library.useDatabase(remote.fileId) { session ->
                    fun cell(
                        dataset: String,
                        row: String,
                        column: String,
                    ): String? = SyncSchema.readCell(session, dataset, row, column)?.second
                    assertEquals("pre", cell("rules", "r1", "stage"))
                    assertEquals(CONDITIONS, cell("rules", "r1", "conditions"))
                    assertEquals(ACTIONS, cell("rules", "r1", "actions"))
                    assertNull(cell("rules", "r2", "stage"))
                    assertEquals("Rent", cell("schedules", "s1", "name"))
                    assertEquals("1", cell("schedules", "s1", "active"))
                    assertEquals("1", cell("schedules", "s1", "posts_transaction"))
                    assertEquals("s1", cell("schedules_next_date", "nd1", "schedule_id"))
                    assertEquals("20261101", cell("schedules_next_date", "nd1", "local_next_date"))
                    assertEquals("$STAMP_MS", cell("schedules_next_date", "nd1", "base_next_date_ts"))
                    assertEquals("#template 50", cell("notes", "c-food", "note"))
                    assertEquals("First Bank", cell("banks", "b1", "name"))
                    assertEquals("ins_1", cell("banks", "b1", "bank_id"))
                    assertEquals("b1", cell("accounts", "checking", "bank"))
                    assertEquals("ext-1", cell("accounts", "checking", "account_id"))
                    assertEquals("12345", cell("accounts", "checking", "balance_current"))
                    assertEquals("12000", cell("accounts", "checking", "balance_available"))
                    assertEquals("1790000000000", cell("accounts", "checking", "last_sync"))
                    assertEquals(GOAL_DEF, cell("categories", "c-food", "goal_def"))
                    assertEquals("""{"source":"notes"}""", cell("categories", "c-food", "template_settings"))
                    assertEquals("1.5", cell("categories", "c-food", "sort_order"))
                    assertEquals("5000", cell("zero_budgets", "202610-c-food", "goal"))
                    assertEquals("1", cell("zero_budgets", "202610-c-food", "long_goal"))
                    assertEquals("0", cell("payees", "p1", "learn_categories"))
                    assertEquals("fin-1", cell("transactions", "t-pay", "financial_id"))
                    assertEquals("ACME PAYROLL", cell("transactions", "t-pay", "imported_description"))
                }
            }
        }
    }

    @Test
    fun desktopShapedValuesApplyOnThePhone() {
        LocalActualServer().use { server ->
            val remote = server.addFile("Home", phoneFile())
            SyncPhone(server).use { phone ->
                phone.openSignedIn(remote)
                server.appendChange(remote.fileId, "categories", "c-food", "goal_def", "S:$GOAL_DEF")
                server.appendChange(remote.fileId, "categories", "c-food", "hidden", "N:1")
                server.appendChange(remote.fileId, "zero_budget_months", "2026-10", "buffered", "N:3000")
                server.appendChange(remote.fileId, "transactions", "t-pay", "notes", "S:rent")
                server.appendChange(remote.fileId, "transactions", "t-pay", "notes", "0:")
                server.appendChange(remote.fileId, "rules", "r9", "conditions", "S:[]")
                phone.sync.sync(remote.fileId)
                phone.library.useDatabase(remote.fileId) { session ->
                    assertEquals(
                        GOAL_DEF,
                        session.query("SELECT goal_def FROM categories WHERE id = 'c-food'").single().str("goal_def"),
                    )
                    assertEquals(1L, session.query("SELECT hidden FROM categories WHERE id = 'c-food'").single().long("hidden"))
                    assertEquals(
                        3_000L,
                        session.query("SELECT buffered FROM zero_budget_months WHERE id = 202610").single().long("buffered"),
                    )
                    assertNull(session.query("SELECT notes FROM transactions WHERE id = 't-pay'").single().str("notes"))
                    assertEquals("[]", session.query("SELECT conditions FROM rules WHERE id = 'r9'").single().str("conditions"))
                }
            }
        }
    }

    @Test
    fun holdSendsActualsMonthId() {
        LocalActualServer().use { server ->
            val remote = server.addFile("Home", phoneFile())
            SyncPhone(server).use { phone ->
                phone.openSignedIn(remote)
                val held = phone.sync.holdForNextMonth(remote.fileId, YearMonth.of(2026, 10), "10")
                assertEquals(EditResult.Saved, held)
                phone.sync.sync(remote.fileId)
                val hold = server.plainMessages(remote.fileId).single { it.dataset == "zero_budget_months" }
                assertEquals("2026-10", hold.row)
                assertEquals("N:1000", hold.value)
                phone.library.useDatabase(remote.fileId) { session ->
                    assertEquals(
                        1_000L,
                        session.query("SELECT buffered FROM zero_budget_months WHERE id = 202610").single().long("buffered"),
                    )
                }
            }
        }
    }

    @Test
    fun desktopFilesKeepTextMonthIds() {
        ParityFixture.load("month", "breakdown").open().use { opened ->
            opened.db { session ->
                val remote = SyncClock("REMOTE0000000001")
                SyncSchema.applyRemote(session, remote.send(), CellMessage("zero_budget_months", "202611", "buffered", "N:500"))
                SyncSchema.write(session, "zero_budget_months", "2026-12", "buffered", 700)
                val rows =
                    session
                        .query("SELECT id, buffered FROM zero_budget_months ORDER BY id")
                        .associate { it.str("id") to it.long("buffered") }
                assertEquals(500L, rows["2026-11"])
                assertEquals(700L, rows["2026-12"])
                assertTrue(rows.keys.none { it?.contains("-") == false })
            }
        }
    }

    @Test
    fun legacyPendingValuesGoOutWithATypePrefix() {
        assertEquals("N:2500", SyncValues.wire("amount", "2500"))
        assertEquals("S:Groceries", SyncValues.wire("name", "Groceries"))
        assertEquals("0:", SyncValues.wire("category", null))
        assertEquals("N:2500", SyncValues.wire("amount", "N:2500"))
        assertEquals("N:16384", SyncValues.serialize("sort_order", "16384.0"))
        assertEquals("2500", SyncValues.deserialize("N:2500"))
        assertEquals("1.5", SyncValues.deserialize("N:1.5"))
        assertNull(SyncValues.deserialize("0:"))
        assertEquals("", SyncValues.deserialize("S:"))
        assertEquals("2500", SyncValues.deserialize("2500"))
    }

    @Test
    fun reorderKeepsFractionalSortOrder() {
        val root = tempRoot()
        try {
            val library = BudgetLibrary(root, JdbcSessionOpener(), BudgetClock { TODAY })
            val id = (library.create("Home", "USD") as CreateResult.Created).id
            library.useDatabase(id) { session ->
                SyncSchema.write(session, "categories", "c1", "sort_order", 16_384.5)
                val stored = session.query("SELECT sort_order FROM categories WHERE id = 'c1'").single()
                assertEquals(16_384.5, stored.double("sort_order"), 0.0)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    companion object {
        private const val CONDITIONS = """[{"op":"is","field":"payee","value":"p1","type":"id"}]"""
        private const val ACTIONS = """[{"op":"set","field":"category","value":"c-food","type":"id"}]"""
        private const val GOAL_DEF = """[{"type":"simple","monthly":50,"priority":0}]"""
        private const val STAMP_MS = 1_790_000_000_000L
    }
}

private val TODAY: LocalDate = LocalDate.of(2026, 10, 7)

internal fun tempRoot(): File =
    File.createTempFile("sync", "").apply {
        delete()
        mkdirs()
    }

/** A phone with real time, so the library's and the coordinator's clocks agree. */
internal class SyncPhone(
    private val server: LocalActualServer,
) : AutoCloseable {
    val root: File = tempRoot()
    val library = BudgetLibrary(root, JdbcSessionOpener(), BudgetClock { TODAY })
    val sync = SyncCoordinator(library, HttpActualTransport(), MemorySecretStore(), BudgetClock { TODAY }, ZoneOffset.UTC)

    fun openSignedIn(file: RemoteFile) {
        assertNull(sync.connect(server.baseUrl, "server-secret").error)
        assertEquals(OpenResult.Opened, sync.open(file))
    }

    override fun close() {
        root.deleteRecursively()
    }
}

/** A file the phone created: phone schema plus a checking account, two categories and a paycheck. */
internal fun phoneFile(): ByteArray {
    val file = File.createTempFile("budget", ".sqlite")
    file.delete()
    JdbcSessionOpener().use(file) { session ->
        ActualSchema.create(session)
        session.exec("INSERT INTO preferences (id, value) VALUES ('budgetType', 'envelope')")
        session.exec("INSERT INTO preferences (id, value) VALUES ('currency', 'USD')")
        session.exec("INSERT INTO category_groups (id, name, is_income, sort_order) VALUES ('g-inc', 'Income', 1, 0)")
        session.exec("INSERT INTO category_groups (id, name, is_income, sort_order) VALUES ('g-food', 'Food', 0, 1)")
        session.exec("INSERT INTO categories (id, name, is_income, cat_group) VALUES ('c-sal', 'Salary', 1, 'g-inc')")
        session.exec("INSERT INTO categories (id, name, is_income, cat_group) VALUES ('c-food', 'Groceries', 0, 'g-food')")
        session.exec("INSERT INTO accounts (id, name, offbudget) VALUES ('checking', 'Checking', 0)")
        session.exec(
            "INSERT INTO transactions (id, acct, category, amount, date) VALUES ('t-pay', 'checking', 'c-sal', 10000, 20261007)",
        )
    }
    return file.readBytes().also { file.delete() }
}
