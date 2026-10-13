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

class InstanceConnectTest {
    @Test
    fun passwordConnectStoresPerInstanceTokenAndSetsActive() {
        LocalActualServer().use { server ->
            val root = tempRoot()
            val secrets = MemorySecretStore()
            val sync = coordinator(root, secrets)
            val result = sync.connect(server.baseUrl, "server-secret")
            assertNull(result.error)
            val settings = BudgetLibrary(root, JdbcSessionOpener(), BudgetClock { LocalDate.of(2026, 10, 7) }).settings()
            assertEquals(1, settings.instances.size)
            val id = settings.activeInstanceId!!
            assertEquals(id, settings.instances.single().id)
            assertEquals(server.baseUrl, settings.instances.single().address)
            assertEquals(server.sessionToken(), secrets.get("actual.token.$id"))
            assertNull(secrets.get("actual.token"))
            assertTrue(sync.signedIn())
        }
    }

    @Test
    fun authFailureLeavesNoActiveInstance() {
        LocalActualServer().use { server ->
            val root = tempRoot()
            val secrets = MemorySecretStore()
            val sync = coordinator(root, secrets)
            val rejected = sync.connect(server.baseUrl, "wrong-password")
            assertEquals(SyncCopy.SIGN_IN_FAILED, rejected.error)
            assertFalse(sync.signedIn())
            val settings = BudgetLibrary(root, JdbcSessionOpener(), BudgetClock { LocalDate.of(2026, 10, 7) }).settings()
            assertTrue(settings.instances.isEmpty())
            assertNull(settings.activeInstanceId)
            assertTrue(secrets.snapshot().none { it.key.startsWith("actual.token") })
        }
    }

    @Test
    fun networkFailureShowsAddressFailedCopy() {
        val root = tempRoot()
        val sync = coordinator(root, MemorySecretStore())
        val refused = sync.connect("http://127.0.0.1:1", "server-secret")
        assertEquals(SyncCopy.ADDRESS_FAILED, refused.error)
        assertFalse(sync.signedIn())
    }

    @Test
    fun legacyTokenMigratesToActiveInstanceOnRead() {
        LocalActualServer().use { server ->
            val root = tempRoot()
            val secrets = MemorySecretStore()
            val library = BudgetLibrary(root, JdbcSessionOpener(), BudgetClock { LocalDate.of(2026, 10, 7) })
            library.save(
                PhoneSettings(
                    instances = listOf(SavedInstance("legacy-id", server.baseUrl, "local")),
                    activeInstanceId = "legacy-id",
                ),
            )
            secrets.put("actual.token", server.sessionToken())
            val sync = coordinator(root, secrets)
            assertEquals(server.sessionToken(), sync.syncToken())
            assertEquals(server.sessionToken(), secrets.get("actual.token.legacy-id"))
            assertNull(secrets.get("actual.token"))
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
        File.createTempFile("connect", "").apply {
            delete()
            mkdirs()
        }
}
