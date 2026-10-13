package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipInputStream

class BudgetExportTest {
    @Test
    fun exportZipContainsMetadataAndSqliteWithKnownRows() {
        val root = tempRoot()
        val library = BudgetLibrary(root, JdbcSessionOpener(), BudgetClock { LocalDate.of(2026, 10, 7) })
        val id = (library.create("Family Budget", "USD") as CreateResult.Created).id
        library.useDatabase(id) { session ->
            session.exec(
                "INSERT INTO accounts (id, name, offbudget) VALUES (?, ?, ?)",
                listOf("checking", "Checking", 0),
            )
            session.exec(
                """
                INSERT INTO transactions (id, acct, amount, date, tombstone)
                VALUES (?, ?, ?, ?, 0)
                """.trimIndent(),
                listOf("t-1", "checking", -1_250L, 20261007),
            )
        }
        val zip = library.exportZip(id)!!
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zip)).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                entries[entry.name] = input.readBytes()
            }
        }
        assertEquals(setOf("metadata.json", "db.sqlite"), entries.keys)
        assertTrue(entries.getValue("metadata.json").toString(Charsets.UTF_8).contains("Family Budget"))
        val sqlite = File.createTempFile("export", ".sqlite")
        sqlite.writeBytes(entries.getValue("db.sqlite"))
        try {
            JdbcSessionOpener().use(sqlite) { session ->
                assertEquals(
                    "Checking",
                    session.query("SELECT name FROM accounts WHERE id = ?", listOf("checking")).first().str("name"),
                )
                assertEquals(
                    -1_250L,
                    session.query("SELECT amount FROM transactions WHERE id = ?", listOf("t-1")).first().long("amount"),
                )
            }
        } finally {
            sqlite.delete()
        }
        val name = library.exportFileName("Family Budget")
        assertEquals("family-budget.zip", name)
        assertFalse(name.contains("password"))
        assertFalse(name.contains("secret"))
        // Display name only — encryption / server passwords are never appended by export.
        assertEquals("home.zip", library.exportFileName("Home"))
    }

    private fun tempRoot(): File =
        File.createTempFile("export", "").apply {
            delete()
            mkdirs()
        }
}
