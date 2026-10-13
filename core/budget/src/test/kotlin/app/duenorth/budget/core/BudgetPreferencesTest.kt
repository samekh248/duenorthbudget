package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class BudgetPreferencesTest {
    @Test
    fun preferenceKeysRoundTripAndExperimentalDefaultsOff() {
        val root = tempRoot()
        val library = BudgetLibrary(root, JdbcSessionOpener(), BudgetClock { LocalDate.of(2026, 10, 7) })
        val id = (library.create("Home", "USD") as CreateResult.Created).id
        val defaults = library.readFormatPrefs(id)
        assertEquals("MM/dd/yyyy", defaults.dateFormat)
        assertEquals(FormatOptions.NUMBER_COMMA_DOT, defaults.numberFormat)
        assertFalse(defaults.hideFraction)
        assertEquals(0, defaults.firstDayOfWeekIdx)
        assertFalse(defaults.experimental)
        assertEquals("envelope", defaults.budgetType)

        assertTrue(library.setPreference(id, "dateFormat", "dd/MM/yyyy"))
        assertTrue(library.setPreference(id, "numberFormat", FormatOptions.NUMBER_DOT_COMMA))
        assertTrue(library.setPreference(id, "hideFraction", "true"))
        assertTrue(library.setPreference(id, "firstDayOfWeekIdx", "1"))
        assertTrue(library.setPreference(id, "flags.experimental", "true"))
        assertFalse(library.setPreference(id, "budgetType", "tracking"))

        val prefs = library.readFormatPrefs(id)
        assertEquals("dd/MM/yyyy", prefs.dateFormat)
        assertEquals(FormatOptions.NUMBER_DOT_COMMA, prefs.numberFormat)
        assertTrue(prefs.hideFraction)
        assertEquals(1, prefs.firstDayOfWeekIdx)
        assertTrue(prefs.experimental)
        assertEquals("envelope", prefs.budgetType)
        assertEquals("envelope", library.preference(id, "budgetType"))
    }

    @Test
    fun preferenceWriteEnqueuesPendingMessageWhenSynced() {
        val root = tempRoot()
        val library = BudgetLibrary(root, JdbcSessionOpener(), BudgetClock { LocalDate.of(2026, 10, 7) })
        val id = (library.create("Home", "USD") as CreateResult.Created).id
        library.useDatabase(id) { session ->
            SyncSchema.putState(session, "cloudFileId", "cloud-1")
            SyncSchema.putState(session, "groupId", "group-1")
        }
        assertTrue(library.setPreference(id, "hideFraction", "true"))
        val pending =
            library.useDatabase(id) { session ->
                session
                    .query(
                        """
                        SELECT dataset, row, column, value, pending FROM messages
                        WHERE dataset = 'preferences' AND row = 'hideFraction'
                        """.trimIndent(),
                    ).single()
            }!!
        assertEquals("preferences", pending.str("dataset"))
        assertEquals("hideFraction", pending.str("row"))
        assertEquals("value", pending.str("column"))
        assertEquals("true", pending.str("value"))
        assertEquals(1L, pending.long("pending"))
    }

    private fun tempRoot(): File =
        File.createTempFile("prefs", "").apply {
            delete()
            mkdirs()
        }
}
