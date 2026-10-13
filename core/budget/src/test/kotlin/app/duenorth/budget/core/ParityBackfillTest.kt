package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Spec 010 T006: rules and schedules the phone wrote with plain SQL before spec 010 reach the server once. */
class ParityBackfillTest {
    @Test
    fun preUpgradeRulesAndSchedulesGetPendingMessagesOnce() {
        withFile { session ->
            SyncSchema.putState(session, "cloudFileId", "cloud-1")
            writeLikeBeforeSpec010(session)
            val clock = SyncClock("PHONE00000000001")
            val emitted = SyncSchema.backfill(session, clock)
            assertEquals(5 + 6 + 6, emitted)
            val pending =
                session
                    .query("SELECT dataset, row, column, value FROM messages WHERE pending = 1")
                    .associate { Triple(it.str("dataset"), it.str("row"), it.str("column")) to it.str("value") }
            assertEquals("S:Rent", pending[Triple("schedules", "s1", "name")])
            assertEquals("S:r1", pending[Triple("schedules", "s1", "rule")])
            assertEquals("S:$CONDITIONS", pending[Triple("rules", "r1", "conditions")])
            assertEquals("N:20261101", pending[Triple("schedules_next_date", "nd1", "local_next_date")])
            assertEquals("N:20261101", pending[Triple("schedules_next_date", "nd1", "base_next_date")])
            assertEquals(
                "20261101",
                session.query("SELECT local_next_date FROM schedules_next_date WHERE id = 'nd1'").single().str("local_next_date"),
            )
            assertTrue(pending.keys.none { it.second == "r-dead" })
            assertEquals(0, SyncSchema.backfill(session, clock))
            assertEquals(pending.size, session.query("SELECT timestamp FROM messages").size)
        }
    }

    @Test
    fun rowsThatAlreadySyncedAreLeftAlone() {
        withFile { session ->
            SyncSchema.putState(session, "cloudFileId", "cloud-1")
            writeLikeBeforeSpec010(session)
            val remote = SyncClock("REMOTE0000000001")
            SyncSchema.applyRemote(session, remote.send(), CellMessage("rules", "r1", "stage", "0:"))
            val emitted = SyncSchema.backfill(session, SyncClock("PHONE00000000001"))
            assertEquals(6 + 6, emitted)
            assertTrue(session.query("SELECT row FROM messages WHERE pending = 1 AND dataset = 'rules'").isEmpty())
        }
    }

    @Test
    fun filesWithoutSyncStateAreUntouched() {
        withFile { session ->
            writeLikeBeforeSpec010(session)
            assertEquals(0, SyncSchema.backfill(session, SyncClock("PHONE00000000001")))
            assertTrue(session.query("SELECT timestamp FROM messages").isEmpty())
            assertNull(SyncSchema.state(session, SyncSchema.BACKFILL_STATE))
        }
    }

    @Test
    fun downloadedRowsAreNotSentBack() {
        LocalActualServer().use { server ->
            val remote = server.addFile("Home", phoneFile())
            SyncPhone(server).use { phone ->
                phone.openSignedIn(remote)
                phone.library.useDatabase(remote.fileId) { session ->
                    assertNotNull(SyncSchema.state(session, SyncSchema.BACKFILL_STATE))
                    writeLikeBeforeSpec010(session)
                }
                phone.sync.sync(remote.fileId)
                assertTrue(server.plainMessages(remote.fileId).none { it.dataset == "rules" })
            }
        }
    }

    private fun writeLikeBeforeSpec010(session: SqlSession) {
        session.exec(
            "INSERT INTO rules (id, stage, conditions_op, conditions, actions, tombstone) VALUES ('r1', NULL, 'and', ?, '[]', 0)",
            listOf(CONDITIONS),
        )
        session.exec(
            "INSERT INTO rules (id, stage, conditions_op, conditions, actions, tombstone) VALUES ('r-dead', NULL, 'and', '[]', '[]', 1)",
        )
        session.exec(
            "INSERT INTO schedules (id, name, rule, completed, posts_transaction, tombstone) VALUES ('s1', 'Rent', 'r1', 0, 0, 0)",
        )
        session.exec(
            """
            INSERT INTO schedules_next_date (
                id, schedule_id, local_next_date, local_next_date_ts, base_next_date, base_next_date_ts
            ) VALUES ('nd1', 's1', '2026-11-01', 1790000000000.0, '2026-11-01', 1790000000000.0)
            """.trimIndent(),
        )
    }

    private fun withFile(block: (SqlSession) -> Unit) {
        val file = File.createTempFile("backfill", ".sqlite")
        file.delete()
        try {
            JdbcSessionOpener().use(file) { session ->
                ActualSchema.create(session)
                SyncSchema.ensure(session)
                block(session)
            }
        } finally {
            file.delete()
        }
    }

    companion object {
        private const val CONDITIONS = """[{"op":"is","field":"payee","value":"p1","type":"id"}]"""
    }
}
