package app.duenorth.budget.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class OpenIdLoginTest {
    @Test
    fun openIdHandshakeActivatesInstance() {
        LocalActualServer().use { server ->
            val root = tempRoot()
            val secrets = MemorySecretStore()
            val sync = coordinator(root, secrets)
            val methods = sync.loginMethods(server.baseUrl)
            assertTrue(methods.contains("openid"))
            val redirect = sync.beginOpenId(server.baseUrl, "duenorth://oauth")
            assertTrue(redirect!!.startsWith(server.baseUrl))
            assertTrue(redirect.contains(server.openIdCallbackPath))
            val token = fetchToken(redirect)
            assertEquals(server.sessionToken(), token)
            val prior =
                BudgetLibrary(root, JdbcSessionOpener(), BudgetClock { LocalDate.of(2026, 10, 7) }).settings()
            assertNull(prior.activeInstanceId)
            val result = sync.completeOpenId(server.baseUrl, token)
            assertNull(result.error)
            assertTrue(sync.signedIn())
            val settings = BudgetLibrary(root, JdbcSessionOpener(), BudgetClock { LocalDate.of(2026, 10, 7) }).settings()
            val id = settings.activeInstanceId!!
            assertEquals(server.sessionToken(), secrets.get("actual.token.$id"))
        }
    }

    @Test
    fun cancelledOpenIdLeavesPriorActiveUnchanged() {
        LocalActualServer().use { first ->
            LocalActualServer().use { second ->
                val root = tempRoot()
                val secrets = MemorySecretStore()
                val sync = coordinator(root, secrets)
                assertNull(sync.connect(first.baseUrl, "server-secret").error)
                val before = BudgetLibrary(root, JdbcSessionOpener(), BudgetClock { LocalDate.of(2026, 10, 7) }).settings()
                val activeBefore = before.activeInstanceId!!
                second.openIdCancelled = true
                val redirect = sync.beginOpenId(second.baseUrl, "duenorth://oauth")!!
                val code =
                    (URL(redirect).openConnection() as HttpURLConnection).let { connection ->
                        connection.connectTimeout = 5_000
                        connection.readTimeout = 5_000
                        connection.responseCode.also { connection.disconnect() }
                    }
                assertEquals(400, code)
                val after = BudgetLibrary(root, JdbcSessionOpener(), BudgetClock { LocalDate.of(2026, 10, 7) }).settings()
                assertEquals(activeBefore, after.activeInstanceId)
                assertTrue(sync.signedIn())
                assertFalse(after.instances.any { it.address == second.baseUrl })
            }
        }
    }

    private fun fetchToken(redirect: String): String {
        val connection = URL(redirect).openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        return try {
            val body = connection.inputStream.readBytes().toString(Charsets.UTF_8)
            Json
                .parseToJsonElement(body)
                .jsonObject["data"]!!
                .jsonObject["token"]!!
                .jsonPrimitive.content
        } finally {
            connection.disconnect()
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
        File.createTempFile("openid", "").apply {
            delete()
            mkdirs()
        }
}
