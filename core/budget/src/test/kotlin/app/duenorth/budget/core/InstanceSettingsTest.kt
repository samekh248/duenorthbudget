package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

class InstanceSettingsTest {
    @Test
    fun legacyServerAddressMigratesToOneActiveInstance() {
        val root = tempRoot()
        val ids = AtomicInteger(0)
        val library =
            BudgetLibrary(
                root,
                JdbcSessionOpener(),
                BudgetClock { LocalDate.of(2026, 10, 7) },
                ids = { "id-${ids.incrementAndGet()}" },
            )
        library.save(PhoneSettings(serverAddress = "http://actual.example:5006/"))
        val migrated = library.settings()
        assertEquals(1, migrated.instances.size)
        assertEquals("http://actual.example:5006", migrated.instances.single().address)
        assertEquals("actual.example", migrated.instances.single().label)
        assertEquals(migrated.instances.single().id, migrated.activeInstanceId)
        assertNull(migrated.serverAddress)
        val again = BudgetLibrary(root, JdbcSessionOpener(), BudgetClock { LocalDate.of(2026, 10, 7) }).settings()
        assertEquals(migrated.instances.single().id, again.activeInstanceId)
        assertEquals(1, again.instances.size)
        assertNull(again.serverAddress)
    }

    @Test
    fun inboxSwipePreviewIsRememberedAcrossLaunches() {
        val root = tempRoot()
        val library = BudgetLibrary(root, JdbcSessionOpener(), BudgetClock { LocalDate.of(2026, 10, 7) })
        assertTrue(!library.settings().inboxSwipeShown)
        library.updateSettings { it.copy(inboxSwipeShown = true) }
        assertTrue(BudgetLibrary(root, JdbcSessionOpener(), BudgetClock { LocalDate.of(2026, 10, 7) }).settings().inboxSwipeShown)
    }

    @Test
    fun atMostOneActiveAndRemoveNonActiveLeavesBudgets() {
        val root = tempRoot()
        val library = BudgetLibrary(root, JdbcSessionOpener(), BudgetClock { LocalDate.of(2026, 10, 7) })
        val home = (library.create("Home", "USD") as CreateResult.Created).id
        val before = File(root, "$home/db.sqlite").readBytes()
        val a = SavedInstance("a", "http://a.example", "a")
        val b = SavedInstance("b", "http://b.example", "b")
        library.save(PhoneSettings(instances = listOf(a, b), activeInstanceId = "a", openBudgetId = home))
        library.setActiveInstanceId("b")
        assertEquals("b", library.settings().activeInstanceId)
        library.removeInstance("a")
        val settings = library.settings()
        assertEquals(listOf("b"), settings.instances.map { it.id })
        assertEquals("b", settings.activeInstanceId)
        assertTrue(File(root, "$home/db.sqlite").readBytes().contentEquals(before))
        assertEquals(home, settings.openBudgetId)
    }

    private fun tempRoot(): File =
        File.createTempFile("instances", "").apply {
            delete()
            mkdirs()
        }
}
