package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ServerSocket
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException

class ServerProbeTest {
    private val probe = ServerProbe(UrlConnectionProbeHttp(connectTimeoutMs = 2_000, readTimeoutMs = 2_000))

    @Test
    fun readyServerListsItsSignIns() {
        LocalActualServer(methods = listOf("password", "openid")).use { server ->
            val check = probe.check(ServerAddress.parse(server.baseUrl)!!)
            assertEquals(
                ServerCheck.Ready(server.baseUrl, secure = false, methods = setOf(SignInMethod.PASSWORD, SignInMethod.BROWSER)),
                check,
            )
        }
    }

    @Test
    fun browserOnlyServerOffersOnlyTheBrowser() {
        LocalActualServer(methods = listOf("openid")).use { server ->
            val check = probe.check(ServerAddress.parse(server.baseUrl)!!) as ServerCheck.Ready
            assertEquals(setOf(SignInMethod.BROWSER), check.methods)
        }
    }

    @Test
    fun serverNotSetUpSaysSo() {
        LocalActualServer().use { server ->
            server.bootstrapped = false
            assertEquals(ServerCheck.NotSetUp, probe.check(ServerAddress.parse(server.baseUrl)!!))
        }
    }

    @Test
    fun headerOnlyServerIsUnsupported() {
        LocalActualServer(methods = listOf("header")).use { server ->
            assertEquals(ServerCheck.Unsupported(listOf("header")), probe.check(ServerAddress.parse(server.baseUrl)!!))
        }
    }

    @Test
    fun webPageIsNotActual() {
        LocalActualServer().use { server ->
            server.htmlOnly = true
            assertEquals(ServerCheck.NotActual(200), probe.check(ServerAddress.parse(server.baseUrl)!!))
        }
    }

    @Test
    fun olderServerWithoutBootstrapFallsBackToLoginMethods() {
        LocalActualServer(methods = listOf("password")).use { server ->
            server.bootstrapRoute = false
            val check = probe.check(ServerAddress.parse(server.baseUrl)!!) as ServerCheck.Ready
            assertEquals(setOf(SignInMethod.PASSWORD), check.methods)
        }
    }

    @Test
    fun stoppedServerBehindAProxyIsUnreachable() {
        LocalActualServer().use { server ->
            server.online = false
            assertEquals(
                ServerCheck.Unreachable(UnreachableReason.NO_ANSWER),
                probe.check(ServerAddress.parse(server.baseUrl)!!),
            )
        }
    }

    @Test
    fun closedPortIsUnreachable() {
        val port = ServerSocket(0).use { it.localPort }
        assertEquals(
            ServerCheck.Unreachable(UnreachableReason.NO_ANSWER),
            probe.check(ServerAddress.parse("http://127.0.0.1:$port")!!),
        )
    }

    @Test
    fun bareHostFallsBackToPlainHttpOnlyWhenSecureCannotConnect() {
        val asked = mutableListOf<String>()
        val http =
            ProbeHttp { url ->
                asked += url
                if (url.startsWith("https://")) throw IOException("refused")
                ProbeResponse(200, """{"status":"ok","data":{"bootstrapped":true,"loginMethod":"password"}}""")
            }
        val check = ServerProbe(http).check(ServerAddress.parse("budget.lan:5006")!!) as ServerCheck.Ready
        assertEquals("http://budget.lan:5006", check.address)
        assertEquals(false, check.secure)
        assertEquals("https://budget.lan:5006/account/needs-bootstrap", asked.first())
    }

    @Test
    fun certificateFailureDoesNotDowngrade() {
        val asked = mutableListOf<String>()
        val http =
            ProbeHttp { url ->
                asked += url
                throw SSLHandshakeException("untrusted")
            }
        val check = ServerProbe(http).check(ServerAddress.parse("budget.example.com")!!)
        assertEquals(ServerCheck.Unreachable(UnreachableReason.CERTIFICATE_NOT_TRUSTED), check)
        assertTrue(asked.none { it.startsWith("http://") })
    }

    @Test
    fun timeoutIsReportedAsTimeout() {
        val http = ProbeHttp { throw SocketTimeoutException("slow") }
        assertEquals(
            ServerCheck.Unreachable(UnreachableReason.TIMEOUT),
            ServerProbe(http).check(ServerAddress.parse("https://slow.example.com")!!),
        )
    }

    @Test
    fun inactiveMethodsAreIgnored() {
        val http =
            ProbeHttp {
                ProbeResponse(
                    200,
                    """{"status":"ok","data":{"bootstrapped":true,"availableLoginMethods":""" +
                        """[{"method":"password","active":0},{"method":"openid","active":1}]}}""",
                )
            }
        val check = ServerProbe(http).check(ServerAddress.parse("https://a.example")!!) as ServerCheck.Ready
        assertEquals(setOf(SignInMethod.BROWSER), check.methods)
    }

    @Test
    fun everyOutcomeHasItsOwnMessage() {
        val messages =
            listOf(
                ServerCheck.Unreachable(UnreachableReason.NO_ANSWER),
                ServerCheck.Unreachable(UnreachableReason.TIMEOUT),
                ServerCheck.Unreachable(UnreachableReason.CERTIFICATE_NOT_TRUSTED),
                ServerCheck.NotActual(200),
                ServerCheck.NotSetUp,
                ServerCheck.Unsupported(listOf("header")),
            ).map { OnboardingCopy.message(it) }
        assertEquals(messages.size, messages.toSet().size)
    }
}
