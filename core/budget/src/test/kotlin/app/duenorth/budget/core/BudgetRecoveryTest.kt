package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class BudgetRecoveryTest {
    @Test
    fun rebuildDropsCacheTablesSafely() {
        val root = tempRoot()
        val library = BudgetLibrary(root, JdbcSessionOpener(), BudgetClock { LocalDate.of(2026, 10, 7) })
        val id = (library.create("Home", "USD") as CreateResult.Created).id
        library.useDatabase(id) { session ->
            session.exec("CREATE TABLE kvcache (key TEXT PRIMARY KEY, value TEXT)")
            session.exec("INSERT INTO kvcache (key, value) VALUES ('month', 'stale')")
        }
        assertTrue(library.rebuildTotals(id))
        val tables =
            library.useDatabase(id) { session ->
                session
                    .query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'kvcache'")
                    .map { it.str("name") }
            }!!
        assertTrue(tables.isEmpty())
        assertTrue(library.rebuildTotals(id))
    }

    @Test
    fun repairFixesOrphanSplits() {
        val root = tempRoot()
        val library = BudgetLibrary(root, JdbcSessionOpener(), BudgetClock { LocalDate.of(2026, 10, 7) })
        val id = (library.create("Home", "USD") as CreateResult.Created).id
        library.useDatabase(id) { session ->
            session.exec("INSERT INTO accounts (id, name, offbudget) VALUES (?, ?, 0)", listOf("checking", "Checking"))
            session.exec(
                """
                INSERT INTO transactions (id, isParent, isChild, parent_id, acct, amount, date, tombstone)
                VALUES (?, 1, 0, NULL, 'checking', -1000, 20261007, 0)
                """.trimIndent(),
                listOf("empty-parent"),
            )
            session.exec(
                """
                INSERT INTO transactions (id, isParent, isChild, parent_id, acct, amount, date, tombstone)
                VALUES (?, 0, 1, 'missing-parent', 'checking', -500, 20261007, 0)
                """.trimIndent(),
                listOf("orphan-child"),
            )
            session.exec(
                """
                INSERT INTO transactions (id, isParent, isChild, parent_id, acct, amount, date, tombstone)
                VALUES (?, 0, 0, NULL, 'checking', -800, 20261007, 0)
                """.trimIndent(),
                listOf("live-parent"),
            )
            session.exec(
                """
                INSERT INTO transactions (id, isParent, isChild, parent_id, acct, amount, date, tombstone)
                VALUES (?, 0, 1, 'live-parent', 'checking', -800, 20261007, 0)
                """.trimIndent(),
                listOf("relink-child"),
            )
        }
        assertTrue(library.repairSplits(id))
        library.useDatabase(id) { session ->
            assertEquals(
                1L,
                session.query("SELECT tombstone FROM transactions WHERE id = ?", listOf("empty-parent")).first().long("tombstone"),
            )
            assertEquals(
                1L,
                session.query("SELECT tombstone FROM transactions WHERE id = ?", listOf("orphan-child")).first().long("tombstone"),
            )
            assertEquals(
                1L,
                session.query("SELECT isParent FROM transactions WHERE id = ?", listOf("live-parent")).first().long("isParent"),
            )
            assertEquals(
                0L,
                session.query("SELECT tombstone FROM transactions WHERE id = ?", listOf("relink-child")).first().long("tombstone"),
            )
        }
    }

    @Test
    fun encryptionSetChangeRemoveNeverWritesPasswordIntoBudget() {
        val root = tempRoot()
        val secrets = MemorySecretStore()
        val today = LocalDate.of(2026, 10, 7)
        val library = BudgetLibrary(root, JdbcSessionOpener(), BudgetClock { today })
        val id = (library.create("Home", "USD") as CreateResult.Created).id
        val sync =
            SyncCoordinator(
                library,
                HttpActualTransport(),
                secrets,
                BudgetClock { today },
                ZoneOffset.UTC,
                now = { Instant.parse("2026-10-07T15:04:00Z").toEpochMilli() },
            )
        assertTrue(sync.setEncryptionPassword(id, "correct-horse") is UnlockResult.Unlocked)
        assertEquals("1", library.useDatabase(id) { SyncSchema.state(it, "encrypted") })
        assertTrue(sync.changeEncryptionPassword(id, "correct-horse", "new-phrase") is UnlockResult.Unlocked)
        assertTrue(sync.removeEncryptionPassword(id, "wrong") is UnlockResult.Failed)
        assertTrue(sync.removeEncryptionPassword(id, "new-phrase") is UnlockResult.Unlocked)
        assertEquals("0", library.useDatabase(id) { SyncSchema.state(it, "encrypted") })
        root.walkTopDown().filter { it.isFile }.forEach { file ->
            val text = file.readBytes().toString(Charsets.ISO_8859_1)
            assertTrue(!text.contains("correct-horse"))
            assertTrue(!text.contains("new-phrase"))
        }
    }

    @Test
    fun resetSyncClearsMessagesKeepsCloudIdsAndRows() {
        val root = tempRoot()
        val secrets = MemorySecretStore()
        val today = LocalDate.of(2026, 10, 7)
        val library = BudgetLibrary(root, JdbcSessionOpener(), BudgetClock { today })
        val id = (library.create("Home", "USD") as CreateResult.Created).id
        library.useDatabase(id) { session ->
            SyncSchema.putState(session, "cloudFileId", "cloud-keep")
            SyncSchema.putState(session, "groupId", "group-keep")
            SyncSchema.putState(session, "clock", SyncClock.ZERO)
            SyncSchema.putState(session, "lastRemote", SyncClock.ZERO)
            SyncSchema.putState(session, "lastFailure", "failed")
            session.exec(
                "INSERT INTO messages (timestamp, dataset, row, column, value, pending) VALUES (?, ?, ?, ?, ?, 1)",
                listOf("t1", "preferences", "dateFormat", "value", "dd/MM/yyyy"),
            )
            session.exec("INSERT INTO accounts (id, name, offbudget) VALUES (?, ?, 0)", listOf("checking", "Checking"))
        }
        val sync =
            SyncCoordinator(
                library,
                HttpActualTransport(),
                secrets,
                BudgetClock { today },
                ZoneOffset.UTC,
                now = { Instant.parse("2026-10-07T15:04:00Z").toEpochMilli() },
            )
        assertTrue(sync.resetSync(id))
        library.useDatabase(id) { session ->
            assertEquals(0L, session.query("SELECT COUNT(*) AS n FROM messages").first().long("n"))
            assertEquals("cloud-keep", SyncSchema.state(session, "cloudFileId"))
            assertEquals("group-keep", SyncSchema.state(session, "groupId"))
            assertNull(SyncSchema.state(session, "clock"))
            assertNull(SyncSchema.state(session, "lastRemote"))
            assertNull(SyncSchema.state(session, "lastFailure"))
            assertEquals(
                "Checking",
                session.query("SELECT name FROM accounts WHERE id = ?", listOf("checking")).first().str("name"),
            )
        }
    }

    private fun tempRoot(): File =
        File.createTempFile("recovery", "").apply {
            delete()
            mkdirs()
        }
}
