package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class InstanceSwitchTest {
    @Test
    fun switchRemoveSignOutAndOfflineActivation() {
        LocalActualServer().use { serverA ->
            LocalActualServer().use { serverB ->
                val root = tempRoot()
                val secrets = MemorySecretStore()
                val library = BudgetLibrary(root, JdbcSessionOpener(), BudgetClock { LocalDate.of(2026, 10, 7) })
                val home = (library.create("Home", "USD") as CreateResult.Created).id
                val before = File(root, "$home/db.sqlite").readBytes()
                val sync = coordinator(root, secrets)

                assertNull(sync.connect(serverA.baseUrl, "server-secret").error)
                val idA = library.settings().activeInstanceId!!
                assertNull(sync.connect(serverB.baseUrl, "server-secret").error)
                val idB = library.settings().activeInstanceId!!
                assertEquals(2, library.settings().instances.size)
                assertEquals(idB, library.settings().activeInstanceId)
                assertTrue(sync.remoteFiles.isEmpty() || sync.signedIn())

                assertTrue(sync.makeActive(idA))
                assertEquals(idA, library.settings().activeInstanceId)
                assertTrue(sync.remoteFiles.isEmpty())
                assertEquals(serverA.sessionToken(), secrets.get("actual.token.$idA"))

                sync.removeInstance(idB)
                assertEquals(listOf(idA), library.settings().instances.map { it.id })
                assertEquals(idA, library.settings().activeInstanceId)
                assertNull(secrets.get("actual.token.$idB"))
                assertTrue(File(root, "$home/db.sqlite").readBytes().contentEquals(before))

                sync.signOut()
                assertFalse(sync.signedIn())
                assertNull(secrets.get("actual.token.$idA"))
                assertTrue(sync.remoteFiles.isEmpty())
                assertTrue(File(root, home).isDirectory)
                assertEquals(home, library.settings().openBudgetId)

                assertTrue(sync.activateOffline(idA))
                assertEquals(idA, library.settings().activeInstanceId)
                assertFalse(sync.signedIn())
                assertEquals(SyncCopy.NOT_REACHED, SyncCopy.NOT_REACHED)
            }
        }
    }

    @Test
    fun removeActiveClearsSession() {
        LocalActualServer().use { server ->
            val root = tempRoot()
            val secrets = MemorySecretStore()
            val sync = coordinator(root, secrets)
            assertNull(sync.connect(server.baseUrl, "server-secret").error)
            val id = BudgetLibrary(root, JdbcSessionOpener(), BudgetClock { LocalDate.of(2026, 10, 7) }).settings().activeInstanceId!!
            sync.removeInstance(id)
            val settings = BudgetLibrary(root, JdbcSessionOpener(), BudgetClock { LocalDate.of(2026, 10, 7) }).settings()
            assertTrue(settings.instances.isEmpty())
            assertNull(settings.activeInstanceId)
            assertFalse(sync.signedIn())
            assertTrue(sync.remoteFiles.isEmpty())
        }
    }

    private fun coordinator(
        root: File,
        secrets: SecretStore,
    ): SyncCoordinator {
        val today = LocalDate.of(2026, 10, 7)
        val now = Instant.parse("2026-10-07T15:04:00Z").toEpochMilli()
        return SyncCoordinator(
            BudgetLibrary(root, JdbcSessionOpener(), BudgetClock { today }),
            HttpActualTransport(),
            secrets,
            BudgetClock { today },
            ZoneOffset.UTC,
            now = { now },
        )
    }

    private fun tempRoot(): File =
        File.createTempFile("switch", "").apply {
            delete()
            mkdirs()
        }
}
