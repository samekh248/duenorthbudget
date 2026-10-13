package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/** Spec 010 T007: the undo journal (`contracts/undo.md`). */
class EditJournalTest {
    @Test
    fun aStepCapturesOldAndNewValuesPerCell() {
        withFile { session, journal ->
            journal.step(session, BUDGET, "assign") { assign(session, 2_500) }
            val created = journal.newest(BUDGET)!!
            assertEquals("assign", created.label)
            val amount = created.cells.single { it.column == "amount" }
            assertNull(amount.old)
            assertEquals("2500", amount.new)
            assertFalse(amount.rowExisted)

            journal.step(session, BUDGET, "assign") { assign(session, 4_000) }
            val changed = journal.newest(BUDGET)!!.cells.single()
            assertEquals("amount", changed.column)
            assertEquals("2500", changed.old)
            assertEquals("4000", changed.new)
            assertTrue(changed.rowExisted)
            assertEquals(2, journal.size(BUDGET))
        }
    }

    @Test
    fun undoWritesOldValuesBackAsPendingMessages() {
        withFile { session, journal ->
            journal.step(session, BUDGET, "assign") { assign(session, 2_500) }
            journal.step(session, BUDGET, "assign") { assign(session, 4_000) }
            val before = pendingCount(session)
            assertEquals(UndoResult.Undone("assign"), journal.undo(session, BUDGET, clock()))
            assertEquals(2_500L, amount(session))
            val newest = newestMessage(session)
            assertEquals("amount", newest.str("column"))
            assertEquals("N:2500", newest.str("value"))
            assertEquals(1L, newest.long("pending"))
            assertEquals(before + 1, pendingCount(session))

            // The step that created the budget row: no tombstone in zero_budgets, so its value goes to zero.
            assertEquals(UndoResult.Undone("assign"), journal.undo(session, BUDGET, clock()))
            assertEquals(0L, amount(session))
            assertEquals(UndoResult.Empty, journal.undo(session, BUDGET, clock()))
        }
    }

    @Test
    fun undoOfACreatedRowTombstonesIt() {
        withFile { session, journal ->
            SyncSchema.writeRow(session, "accounts", "checking", "name" to "Checking", "offbudget" to 0, "closed" to 0, "tombstone" to 0)
            journal.step(session, BUDGET, UndoCopy.SAVE_TRANSACTION) {
                RegisterBook(session) { "t1" }.save(
                    TransactionDraft(
                        id = "t1",
                        accountId = "checking",
                        date = 20261007,
                        payee = "Grocer",
                        amountMinor = -1_200,
                        categoryId = null,
                        note = null,
                    ),
                )
            }
            assertEquals(UndoResult.Undone(UndoCopy.SAVE_TRANSACTION), journal.undo(session, BUDGET, clock()))
            assertEquals(1L, session.query("SELECT tombstone FROM transactions WHERE id = 't1'").single().long("tombstone"))
            assertEquals(1L, session.query("SELECT tombstone FROM payees WHERE name = 'Grocer'").single().long("tombstone"))
        }
    }

    @Test
    fun aRemoteChangeTurnsUndoIntoAConflictWithNoWrite() {
        withFile { session, journal ->
            assign(session, 1_000)
            journal.step(session, BUDGET, "assign") { assign(session, 2_500) }
            // Sync sends the edit, then desktop's later change to the same cell arrives.
            SyncSchema.acceptPending(session, session.query("SELECT timestamp FROM messages").map { it.str("timestamp")!! })
            SyncSchema.applyRemote(
                session,
                SyncClock("REMOTE0000000001").send(),
                CellMessage("zero_budgets", ROW, "amount", "N:4000"),
            )
            val before = pendingCount(session)
            assertEquals(UndoResult.Conflict("assign"), journal.undo(session, BUDGET, clock()))
            assertEquals(4_000L, amount(session))
            assertEquals(before, pendingCount(session))
            val conflict = session.query("SELECT row, column, local_value, remote_value FROM conflicts").single()
            assertEquals(ROW, conflict.str("row"))
            assertEquals("1000", conflict.str("local_value"))
            assertEquals("4000", conflict.str("remote_value"))
            assertFalse(journal.canUndo(BUDGET))
        }
    }

    @Test
    fun theStackCapsAtOneHundredAndClearsOnBudgetSwitch() {
        withFile { session, journal ->
            repeat(105) { index -> journal.step(session, BUDGET, "assign $index") { assign(session, index * 100L + 100) } }
            assertEquals(EditJournal.MAX_STEPS, journal.size(BUDGET))
            assertEquals("assign 104", journal.newestLabel(BUDGET))
            journal.switchTo("other")
            assertFalse(journal.canUndo(BUDGET))
            assertEquals(UndoResult.Empty, journal.undo(session, BUDGET, clock()))
        }
    }

    @Test
    fun aStepInsideAStepIsOneStepAndAnEmptyStepIsNone() {
        withFile { session, journal ->
            journal.step(session, BUDGET, "outer") {
                assign(session, 2_500)
                journal.step(session, BUDGET, "inner") { SyncSchema.write(session, "zero_budgets", ROW, "carryover", 1) }
            }
            assertEquals(1, journal.size(BUDGET))
            assertEquals("outer", journal.newestLabel(BUDGET))
            journal.step(session, BUDGET, "nothing") { assign(session, 2_500) }
            assertEquals(1, journal.size(BUDGET))
        }
    }

    @Test
    fun coordinatorAndLibraryEditsUndoNewestFirstOffline() {
        val root = tempRoot()
        try {
            val library = BudgetLibrary(root, JdbcSessionOpener(), BudgetClock { TODAY })
            val sync = SyncCoordinator(library, HttpActualTransport(), MemorySecretStore(), BudgetClock { TODAY }, ZoneOffset.UTC)
            val id = (library.create("Home", "USD") as CreateResult.Created).id
            library.switchTo(id)
            library.useDatabase(id) { session ->
                SyncSchema.writeRow(session, "accounts", "checking", "name" to "Checking", "offbudget" to 0, "tombstone" to 0)
                SyncSchema.writeRow(session, "category_groups", "g1", "name" to "Food", "is_income" to 0, "tombstone" to 0)
                SyncSchema.writeRow(
                    session,
                    "categories",
                    "c-food",
                    "name" to "Groceries",
                    "cat_group" to "g1",
                    "is_income" to 0,
                    "tombstone" to 0,
                )
            }
            assertEquals(EditResult.Saved, sync.assign(id, "c-food", YearMonth.of(2026, 10), "25"))
            val saved =
                library.saveTransaction(
                    id,
                    TransactionDraft("t1", "checking", 20261007, "Grocer", -1_200, "c-food", null),
                )
            assertEquals(WriteResult.Saved, saved)
            assertEquals(2, library.journal.size(id))
            assertEquals(UndoCopy.SAVE_TRANSACTION, library.undoLabel(id))

            val started = System.nanoTime()
            assertEquals(UndoResult.Undone(UndoCopy.SAVE_TRANSACTION), library.undo(id))
            assertEquals(UndoResult.Undone(UndoCopy.ASSIGN), sync.undo(id))
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            assertTrue("undo took $elapsedMs ms", elapsedMs < 200)

            library.useDatabase(id) { session ->
                assertEquals(1L, session.query("SELECT tombstone FROM transactions WHERE id = 't1'").single().long("tombstone"))
                assertEquals(0L, session.query("SELECT amount FROM zero_budgets WHERE id = '202610-c-food'").single().long("amount"))
            }
            assertFalse(library.canUndo(id))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun assignUpdatesTheRowAlreadyThereAndKeepsItsRollover() {
        withFile { session, _ ->
            SyncSchema.writeRow(
                session,
                "zero_budgets",
                "seeded",
                "month" to 202610,
                "category" to "c-food",
                "amount" to 100,
                "carryover" to 1,
            )
            BudgetEdits.assign(session, clock(), 202610, "c-food", 2_500)
            val rows = session.query("SELECT id, amount, carryover FROM zero_budgets WHERE month = 202610 AND category = 'c-food'")
            assertEquals(listOf("seeded"), rows.map { it.str("id") })
            assertEquals(2_500L, rows.single().long("amount"))
            assertTrue(rows.single().bool("carryover"))
        }
    }

    @Test
    fun assignOnATrackingFileWritesTrackingBudgets() {
        withFile { session, _ ->
            session.exec("INSERT INTO preferences (id, value) VALUES ('budgetType', 'tracking')")
            BudgetEdits.assign(session, clock(), 202610, "c-sal", 500_000)
            val tracked = session.query("SELECT amount FROM reflect_budgets WHERE month = 202610 AND category = 'c-sal'")
            assertEquals(listOf(500_000L), tracked.map { it.long("amount") })
            assertTrue(session.query("SELECT id FROM zero_budgets WHERE category = 'c-sal'").isEmpty())
        }
    }

    private fun assign(
        session: SqlSession,
        amount: Long,
    ) {
        SyncSchema.write(session, "zero_budgets", ROW, "month", 202610)
        SyncSchema.write(session, "zero_budgets", ROW, "category", "c-food")
        SyncSchema.write(session, "zero_budgets", ROW, "amount", amount)
    }

    private fun amount(session: SqlSession): Long =
        session.query("SELECT amount FROM zero_budgets WHERE id = ?", listOf(ROW)).single().long("amount")

    private fun pendingCount(session: SqlSession): Int = session.query("SELECT timestamp FROM messages WHERE pending = 1").size

    private fun newestMessage(session: SqlSession): SqlRow =
        session.query("SELECT column, value, pending FROM messages ORDER BY timestamp DESC LIMIT 1").single()

    private val phoneClock = SyncClock("PHONE00000000001")

    private fun clock(): SyncClock = phoneClock

    private fun withFile(block: (SqlSession, EditJournal) -> Unit) {
        val file = File.createTempFile("journal", ".sqlite")
        file.delete()
        try {
            JdbcSessionOpener().use(file) { session ->
                ActualSchema.create(session)
                SyncSchema.ensure(session)
                SyncSchema.withClock(session, clock()) {
                    block(session, EditJournal().also { it.switchTo(BUDGET) })
                }
            }
        } finally {
            file.delete()
        }
    }

    companion object {
        private const val BUDGET = "budget-1"
        private const val ROW = "202610-c-food"
        private val TODAY: LocalDate = LocalDate.of(2026, 10, 7)
    }
}
