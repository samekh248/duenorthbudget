package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.DriverManager

/** The schema checks run once per change to a budget file, not on every open. */
class SchemaGateTest {
    /** Counts every statement, so a test can tell whether the checks ran. */
    private class Counting(
        private val inner: SqlSession,
    ) : SqlSession {
        var statements = 0

        override fun exec(sql: String) {
            statements++
            inner.exec(sql)
        }

        override fun exec(
            sql: String,
            args: List<Any?>,
        ) {
            statements++
            inner.exec(sql, args)
        }

        override fun query(
            sql: String,
            args: List<Any?>,
        ): List<SqlRow> {
            statements++
            return inner.query(sql, args)
        }

        override fun transaction(body: () -> Unit) = inner.transaction(body)
    }

    /** Opens [file] as the app does, tracked, and returns how many statements [block] ran. */
    private fun opened(
        file: File,
        tracked: Boolean = true,
        block: (SqlSession) -> Unit,
    ): Int {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
            val counting = Counting(JdbcSqlSession(connection))
            block(if (tracked) SchemaGate.track(file, counting) else counting)
            return counting.statements
        }
    }

    private fun checks(session: SqlSession) {
        ActualSchema.ensure(session)
        SyncSchema.ensure(session)
    }

    private fun budgetFile(): File {
        val dir = Files.createTempDirectory("schema-gate").toFile()
        val file = File(dir, "db.sqlite")
        opened(file) { session ->
            ActualSchema.create(session)
            checks(session)
        }
        return file
    }

    @Test
    fun anUnchangedFileSkipsTheChecks() {
        val file = budgetFile()
        opened(file) { checks(it) }
        assertEquals(0, opened(file) { checks(it) })
        assertEquals(0, opened(file) { checks(it) })
    }

    @Test
    fun aWriteMakesTheNextOpenCheckAgain() {
        val file = budgetFile()
        opened(file) { checks(it) }
        opened(file) { it.exec("INSERT INTO payees (id, name) VALUES ('p1', 'Corner Coffee')") }
        assertTrue(opened(file) { checks(it) } > 0)
        assertEquals(0, opened(file) { checks(it) })
    }

    @Test
    fun aWriteInTheSameSessionMakesTheNextCheckInItRun() {
        val file = budgetFile()
        opened(file) { checks(it) }
        opened(file) { session ->
            checks(session)
            session.exec("INSERT INTO payees (id, name) VALUES ('p1', 'Corner Coffee')")
            // The link repair must see the new payee: it gains its payee_mapping row.
            ActualSchema.ensure(session)
            val linked = session.query("SELECT targetId FROM payee_mapping WHERE id = 'p1'").firstOrNull()?.str("targetId")
            assertEquals("p1", linked)
        }
    }

    @Test
    fun aReplacedFileIsCheckedAgain() {
        val file = budgetFile()
        opened(file) { checks(it) }
        // A file from elsewhere (a download or a restore) lands on the same path without the phone's tables.
        val other = File(Files.createTempDirectory("schema-gate-other").toFile(), "db.sqlite")
        opened(other, tracked = false) { ActualSchema.create(it) }
        Files.move(other.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        assertTrue(opened(file) { checks(it) } > 0)
        opened(file) { session ->
            assertTrue(session.query("SELECT name FROM sqlite_master WHERE name = 'sync_state'").isNotEmpty())
        }
    }

    @Test
    fun checksInsideAnOuterTransactionAreNotRemembered() {
        val file = budgetFile()
        opened(file) { checks(it) }
        opened(file) { it.exec("INSERT INTO payees (id, name) VALUES ('p1', 'Corner Coffee')") }
        opened(file) { session -> session.transaction { checks(session) } }
        assertTrue(opened(file) { checks(it) } > 0)
    }

    @Test
    fun untrackedSessionsAlwaysCheck() {
        val file = budgetFile()
        opened(file) { checks(it) }
        assertTrue(opened(file, tracked = false) { checks(it) } > 0)
    }

    @Test
    fun forgetMakesTheNextOpenCheck() {
        val file = budgetFile()
        opened(file) { checks(it) }
        SchemaGate.forget(file)
        assertTrue(opened(file) { checks(it) } > 0)
    }
}
