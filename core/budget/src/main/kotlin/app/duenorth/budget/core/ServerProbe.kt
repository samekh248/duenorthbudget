package app.duenorth.budget.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import javax.net.ssl.SSLException

/** The sign-ins the phone can do. Actual's `openid` is the browser sign-in. */
@Serializable
enum class SignInMethod {
    PASSWORD,
    BROWSER,
}

/** What the phone learned about an address before asking for a sign-in (spec 011 FR-003). */
sealed interface ServerCheck {
    data class Ready(
        val address: String,
        val secure: Boolean,
        val methods: Set<SignInMethod>,
    ) : ServerCheck

    data class Unreachable(
        val reason: UnreachableReason,
    ) : ServerCheck

    data class NotActual(
        val httpStatus: Int,
    ) : ServerCheck

    data object NotSetUp : ServerCheck

    /** An Actual server whose only sign-ins are ones the phone cannot do, such as `header`. */
    data class Unsupported(
        val methods: List<String>,
    ) : ServerCheck
}

enum class UnreachableReason {
    NO_ANSWER,
    TIMEOUT,
    CERTIFICATE_NOT_TRUSTED,
}

object OnboardingCopy {
    const val ENTER_ADDRESS = "enter the server's address"
    const val NOT_REACHED = "the server could not be reached"
    const val TIMED_OUT = "the server took too long to answer"
    const val CERTIFICATE = "the server's certificate is not trusted by this phone"
    const val NOT_ACTUAL = "that address is not an Actual server"
    const val NOT_SET_UP = "this server is not set up yet. finish setting it up in Actual, then try again"
    const val NOT_ENCRYPTED = "this connection is not encrypted"
    const val TRIED_SECURE = "tried as https:// first"
    const val HTTP_WARNING =
        "http is not encrypted. your password and budget cross the network where others can read them. " +
            "choose https unless the server only runs on your home network"
    const val HTTP_ONLY = "https:// did not answer. next again connects over http://, which is not encrypted"
    const val PASSWORD_IN_THE_CLEAR = "this connection is not encrypted, so your password is sent as plain text"
    const val ENTER_NAME = "enter a name"

    fun unsupported(methods: List<String>): String {
        val named = methods.firstOrNull()?.takeIf { it.isNotBlank() } ?: "another"
        return "this server only allows $named sign-in, which the phone cannot use"
    }

    fun message(check: ServerCheck): String? =
        when (check) {
            is ServerCheck.Ready -> null
            is ServerCheck.Unreachable ->
                when (check.reason) {
                    UnreachableReason.NO_ANSWER -> NOT_REACHED
                    UnreachableReason.TIMEOUT -> TIMED_OUT
                    UnreachableReason.CERTIFICATE_NOT_TRUSTED -> CERTIFICATE
                }
            is ServerCheck.NotActual -> NOT_ACTUAL
            ServerCheck.NotSetUp -> NOT_SET_UP
            is ServerCheck.Unsupported -> unsupported(check.methods)
        }
}

/** One GET without a token. Throws [IOException] when nothing answers. */
fun interface ProbeHttp {
    fun get(url: String): ProbeResponse
}

class ProbeResponse(
    val code: Int,
    val body: String,
)

/**
 * Reads, never writes: `GET /account/needs-bootstrap`, then `GET /account/login-methods`
 * when the first gives no sign-in methods (spec 011 contracts/server-check.md).
 */
class ServerProbe(
    private val http: ProbeHttp = UrlConnectionProbeHttp(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun check(address: ServerAddress): ServerCheck {
        var failure = UnreachableReason.NO_ANSWER
        for (candidate in address.candidates) {
            val boot =
                try {
                    http.get("$candidate/account/needs-bootstrap")
                } catch (_: SSLException) {
                    return ServerCheck.Unreachable(UnreachableReason.CERTIFICATE_NOT_TRUSTED)
                } catch (_: SocketTimeoutException) {
                    failure = UnreachableReason.TIMEOUT
                    continue
                } catch (_: IOException) {
                    failure = UnreachableReason.NO_ANSWER
                    continue
                }
            return classify(candidate, boot)
        }
        return ServerCheck.Unreachable(failure)
    }

    private fun classify(
        address: String,
        boot: ProbeResponse,
    ): ServerCheck {
        val bootJson = actualJson(boot)
        val bootData = bootJson?.get("data") as? JsonObject
        val bootstrapped = (bootData?.get("bootstrapped") as? JsonPrimitive)?.booleanOrNull
        if (bootstrapped == false) return ServerCheck.NotSetUp
        var methods = methodsFrom(bootData)
        var actual = bootData != null && bootstrapped != null
        if (methods.isEmpty()) {
            val listed =
                try {
                    http.get("$address/account/login-methods")
                } catch (_: IOException) {
                    null
                }
            val listedJson = listed?.let(::actualJson)
            if (listedJson != null) {
                val fromList = methodsFrom(listedJson["data"] as? JsonObject).ifEmpty { methodsFrom(listedJson) }
                if (fromList.isNotEmpty() || listedJson["status"] != null) actual = true
                methods = fromList
            }
        }
        if (!actual) {
            // A proxy in front of a stopped server answers 502/503: that server is down, not something else.
            if (boot.code >= 500) return ServerCheck.Unreachable(UnreachableReason.NO_ANSWER)
            return ServerCheck.NotActual(boot.code)
        }
        // Servers too old to list their sign-ins only had the password.
        if (methods.isEmpty()) methods = listOf("password")
        val usable =
            methods
                .mapNotNull {
                    when (it.lowercase()) {
                        "password" -> SignInMethod.PASSWORD
                        "openid" -> SignInMethod.BROWSER
                        else -> null
                    }
                }.toSet()
        if (usable.isEmpty()) return ServerCheck.Unsupported(methods)
        return ServerCheck.Ready(address, ServerAddress.isSecure(address), usable)
    }

    /** Actual answers with a JSON object; anything else (an HTML page, a proxy error) is not Actual. */
    private fun actualJson(response: ProbeResponse): JsonObject? {
        if (response.code !in 200..499) return null
        val parsed = runCatching { json.parseToJsonElement(response.body) }.getOrNull() as? JsonObject ?: return null
        val shaped = parsed["status"] != null || parsed["data"] != null || parsed["methods"] != null
        return if (shaped) parsed else null
    }

    /**
     * Method lists come as plain strings or as `{ method, active }` objects. When any entry
     * says which one is active, only active ones count.
     */
    private fun methodsFrom(data: JsonObject?): List<String> {
        if (data == null) return emptyList()
        val listed =
            (data["availableLoginMethods"] as? JsonArray)
                ?: (data["methods"] as? JsonArray)
        val entries = listed.orEmpty().mapNotNull(::methodEntry)
        val chosen =
            if (entries.any { it.second != null }) {
                entries.filter { it.second == true }.map { it.first }
            } else {
                entries.map { it.first }
            }
        val single = (data["loginMethod"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        return (chosen + listOfNotNull(single)).distinct()
    }

    private fun methodEntry(element: JsonElement): Pair<String, Boolean?>? =
        when (element) {
            is JsonPrimitive -> element.contentOrNull?.takeIf { it.isNotBlank() }?.let { it to null }
            is JsonObject -> {
                val name = (element["method"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                val active =
                    when (val flag = element["active"]) {
                        is JsonPrimitive -> flag.booleanOrNull ?: flag.contentOrNull?.let { it == "1" }
                        else -> null
                    }
                name?.let { it to active }
            }
            else -> null
        }
}

class UrlConnectionProbeHttp(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 15_000,
) : ProbeHttp {
    override fun get(url: String): ProbeResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            val body = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            return ProbeResponse(code, body.take(MAX_BODY))
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MAX_BODY = 64 * 1024
    }
}
