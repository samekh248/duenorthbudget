package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** Spec 010 T065: undo across kinds of edit, offline, and after a remote change (`contracts/undo.md`). */
class UndoFlowTest {
    private val october = YearMonth.of(2026, 10)

    @Test
    fun assignAccountAndTransactionEditsUndoNewestFirstOffline() {
        LocalActualServer().use { server ->
            val remote = server.addFile("Home", phoneFile())
            SyncPhone(server).use { phone ->
                phone.openSignedIn(remote)
                val id = remote.fileId
                server.online = false
                assertEquals(EditResult.Saved, phone.sync.assign(id, "c-food", october, "40"))
                assertTrue(phone.library.renameAccount(id, "checking", "Everyday") is AccountWriteResult.Saved)
                val draft = TransactionDraft("t-pay", "checking", 20261007, "Employer", 12_000, "c-sal", null)
                assertEquals(WriteResult.Saved, phone.library.saveTransaction(id, draft))

                assertEquals(UndoResult.Undone(UndoCopy.SAVE_TRANSACTION), phone.sync.undo(id))
                assertEquals(10_000L, value(phone, id, "SELECT amount FROM transactions WHERE id = 't-pay'"))
                assertEquals(UndoResult.Undone(AccountsCopy.EDIT), phone.sync.undo(id))
                assertEquals("Checking", value(phone, id, "SELECT name FROM accounts WHERE id = 'checking'"))
                assertEquals(UndoResult.Undone(UndoCopy.ASSIGN), phone.sync.undo(id))
                assertEquals(0L, value(phone, id, "SELECT amount FROM zero_budgets WHERE category = 'c-food'"))
                assertEquals(UndoResult.Empty, phone.sync.undo(id))

                // The undone values wait to sync like any edit.
                server.online = true
                phone.sync.sync(id)
                val wire = server.plainMessages(id)
                assertEquals("S:Checking", wire.last { it.dataset == "accounts" && it.column == "name" }.value)
                assertEquals("N:10000", wire.last { it.dataset == "transactions" && it.column == "amount" }.value)
            }
        }
    }

    @Test
    fun undoAfterARemoteChangeRecordsAConflictAndWritesNothing() {
        LocalActualServer().use { server ->
            val remote = server.addFile("Home", phoneFile())
            SyncPhone(server).use { phone ->
                phone.openSignedIn(remote)
                val id = remote.fileId
                phone.sync.assign(id, "c-food", october, "40")
                phone.sync.sync(id)
                server.appendChange(id, "zero_budgets", "$ACTUAL_OCTOBER-c-food", "amount", "N:7000")
                phone.sync.sync(id)
                assertEquals(7_000L, value(phone, id, "SELECT amount FROM zero_budgets WHERE category = 'c-food'"))

                assertEquals(UndoResult.Conflict(UndoCopy.ASSIGN), phone.sync.undo(id))
                assertEquals(7_000L, value(phone, id, "SELECT amount FROM zero_budgets WHERE category = 'c-food'"))
                assertTrue(phone.sync.conflicts(id).isNotEmpty())
                assertEquals(UndoResult.Empty, phone.sync.undo(id))
            }
        }
    }

    @Test
    fun undoTakesUnder100Milliseconds() {
        LocalActualServer().use { server ->
            val remote = server.addFile("Home", phoneFile())
            SyncPhone(server).use { phone ->
                phone.openSignedIn(remote)
                val id = remote.fileId
                repeat(20) { phone.sync.assign(id, "c-food", october, "${it + 1}") }
                // Warm the path once, then time the rest (SC-007).
                phone.sync.undo(id)
                val worst =
                    (0 until 10).maxOf {
                        val start = System.nanoTime()
                        assertTrue(phone.sync.undo(id) is UndoResult.Undone)
                        (System.nanoTime() - start) / 1_000_000
                    }
                assertTrue("slowest undo took $worst ms", worst < 100)
            }
        }
    }

    // A deleted bank row stays gone through the next bank sync; undoing the delete brings back that same row, and the
    // sync after that finds it by its bank id, so nothing doubles. A merge undone works the same way.
    @Test
    fun undoingADeletedOrMergedBankRowNeverDoublesIt() {
        LocalActualServer().use { server ->
            val remote = server.addFile("Home", phoneFile())
            SyncPhone(server).use { phone ->
                phone.openSignedIn(remote)
                val id = remote.fileId
                val bank =
                    ParsedImportRow(20261009, "Cafe", -900, financialId = "fit-9", raw = """{"booked":true,"transactionId":"fit-9"}""")
                val bankRows = "SELECT COUNT(*) FROM transactions WHERE financial_id = 'fit-9' AND IFNULL(tombstone, 0) = 0"
                val added =
                    phone.library
                        .confirmImport(id, "checking", listOf(bank))!!
                        .addedIds
                        .single()

                assertEquals(WriteResult.Saved, phone.library.deleteTransaction(id, added, force = false))
                assertEquals(
                    0,
                    phone.library
                        .confirmImport(id, "checking", listOf(bank))!!
                        .addedIds.size,
                )
                assertEquals(0L, value(phone, id, bankRows))

                assertEquals(UndoResult.Undone(UndoCopy.DELETE_TRANSACTION), phone.sync.undo(id))
                assertEquals(1L, value(phone, id, bankRows))
                assertEquals(
                    0,
                    phone.library
                        .confirmImport(id, "checking", listOf(bank))!!
                        .addedIds.size,
                )
                assertEquals(1L, value(phone, id, bankRows))

                // Merged into a hand-entered copy, then the merge undone: still one bank row, and the entry back.
                val entered = TransactionDraft("t-entered", "checking", 20261008, "Cafe", -900, "c-food", null)
                assertEquals(WriteResult.Saved, phone.library.saveTransaction(id, entered))
                assertEquals(WriteResult.Saved, phone.library.mergeTransactions(id, listOf("t-entered", added), force = false))
                assertEquals(
                    0,
                    phone.library
                        .confirmImport(id, "checking", listOf(bank))!!
                        .addedIds.size,
                )
                assertEquals(UndoResult.Undone(UndoCopy.MERGE), phone.sync.undo(id))
                assertEquals(
                    0,
                    phone.library
                        .confirmImport(id, "checking", listOf(bank))!!
                        .addedIds.size,
                )
                assertEquals(1L, value(phone, id, bankRows))
                assertEquals(0L, value(phone, id, "SELECT IFNULL(tombstone, 0) FROM transactions WHERE id = 't-entered'"))
            }
        }
    }

    private fun value(
        phone: SyncPhone,
        budgetId: String,
        sql: String,
    ): Any? =
        phone.library.useDatabase(budgetId) {
            it
                .query(sql)
                .single()
                .values.values
                .single()
                .let { v -> if (v is Int) v.toLong() else v }
        }

    private companion object {
        const val ACTUAL_OCTOBER = "202610"
    }
}
