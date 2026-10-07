package app.duenorth.budget.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class RemoteFile(
    val fileId: String,
    val groupId: String,
    val name: String,
    val encryptKeyId: String?,
)

data class KeyMaterial(
    val id: String,
    val salt: String,
    val test: String,
)

interface ActualTransport {
    fun login(
        address: String,
        password: String,
    ): String

    fun listFiles(
        address: String,
        token: String,
    ): List<RemoteFile>

    fun download(
        address: String,
        token: String,
        fileId: String,
        onProgress: (received: Long, total: Long) -> Unit,
    ): ByteArray

    fun upload(
        address: String,
        token: String,
        fileId: String,
        groupId: String?,
        name: String,
        bytes: ByteArray,
    ): String

    fun sync(
        address: String,
        token: String,
        body: ByteArray,
    ): ByteArray

    fun encryptionKey(
        address: String,
        token: String,
        fileId: String,
    ): KeyMaterial?
}

class SyncAuthException(
    message: String,
) : Exception(message)

class SyncNetworkException(
    cause: Exception,
) : Exception(cause)

class DownloadTracker {
    var received: Long = 0
        private set

    var failed: Boolean = false
        private set

    fun onBytes(next: Long) {
        if (failed) return
        if (next > received) received = next
    }

    fun fail() {
        failed = true
        received = 0
    }
}

object SyncCopy {
    const val ADDRESS_FAILED = "the address could not be reached"
    const val SIGN_IN_FAILED = "that sign-in was not accepted"
    const val SIGN_IN_AGAIN = "sign in again"
    const val LAST_FAILED = "the last try failed"
    const val PASSWORD_WRONG = "that password did not open this budget"
    const val ENTER_AMOUNT = "enter an amount"
    const val NO_SERVER_FILES = "no budgets on this server"
    const val SYNCING = "syncing"
}

/**
 * Actual's HTTP API on HttpURLConnection, which ships with Android 26.
 * java.net.http does not.
 */
class HttpActualTransport : ActualTransport {
    private val json = Json { ignoreUnknownKeys = true }

    override fun login(
        address: String,
        password: String,
    ): String {
        val body =
            buildJsonObject {
                put("password", password)
                put("loginMethod", "password")
            }.toString()
        val response =
            send(
                "$address/account/login",
                "POST",
                token = null,
                contentType = "application/json",
                body = body.toByteArray(Charsets.UTF_8),
            )
        if (response.code == 400 || response.code == 401) throw SyncAuthException(SyncCopy.SIGN_IN_FAILED)
        val token =
            jsonObject(response.bytes)["data"]
                ?.jsonObject
                ?.get("token")
                ?.jsonPrimitive
                ?.contentOrNull
        if (response.code != 200 || token.isNullOrBlank()) throw SyncAuthException(SyncCopy.SIGN_IN_FAILED)
        return token
    }

    override fun listFiles(
        address: String,
        token: String,
    ): List<RemoteFile> {
        val response = send("$address/sync/list-user-files", "GET", token, contentType = null, body = null)
        if (response.code == 401) throw SyncAuthException(SyncCopy.SIGN_IN_AGAIN)
        if (response.code != 200) throw SyncNetworkException(IOException("list ${response.code}"))
        val data = jsonObject(response.bytes)["data"] as? JsonArray ?: return emptyList()
        return data.mapNotNull { element ->
            val row = element as? JsonObject ?: return@mapNotNull null
            if (row.int("deleted") == 1) return@mapNotNull null
            val id = row.string("fileId") ?: return@mapNotNull null
            RemoteFile(
                fileId = id,
                groupId = row.string("groupId").orEmpty(),
                name = row.string("name").orEmpty(),
                encryptKeyId = row.string("encryptKeyId"),
            )
        }
    }

    override fun download(
        address: String,
        token: String,
        fileId: String,
        onProgress: (received: Long, total: Long) -> Unit,
    ): ByteArray {
        val response =
            send(
                "$address/sync/download-user-file",
                "GET",
                token,
                contentType = null,
                body = null,
                headers = mapOf("X-ACTUAL-FILE-ID" to fileId),
                onProgress = onProgress,
            )
        if (response.code == 401) throw SyncAuthException(SyncCopy.SIGN_IN_AGAIN)
        if (response.code != 200) throw SyncNetworkException(IOException("download ${response.code}"))
        return response.bytes
    }

    override fun upload(
        address: String,
        token: String,
        fileId: String,
        groupId: String?,
        name: String,
        bytes: ByteArray,
    ): String {
        val headers = linkedMapOf("X-ACTUAL-FILE-ID" to fileId, "X-ACTUAL-NAME" to encodeName(name))
        if (!groupId.isNullOrBlank()) headers["X-ACTUAL-GROUP-ID"] = groupId
        val response =
            send(
                "$address/sync/upload-user-file",
                "POST",
                token,
                contentType = "application/octet-stream",
                body = bytes,
                headers = headers,
            )
        if (response.code == 401) throw SyncAuthException(SyncCopy.SIGN_IN_AGAIN)
        if (response.code != 200) throw SyncNetworkException(IOException("upload ${response.code}"))
        return jsonObject(response.bytes)["groupId"]?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    override fun sync(
        address: String,
        token: String,
        body: ByteArray,
    ): ByteArray {
        val response =
            send(
                "$address/sync/sync",
                "POST",
                token,
                contentType = "application/actual-sync",
                body = body,
            )
        if (response.code == 401) throw SyncAuthException(SyncCopy.SIGN_IN_AGAIN)
        if (response.code != 200) throw SyncNetworkException(IOException("sync ${response.code}"))
        return response.bytes
    }

    override fun encryptionKey(
        address: String,
        token: String,
        fileId: String,
    ): KeyMaterial? {
        val body = buildJsonObject { put("fileId", fileId) }.toString().toByteArray(Charsets.UTF_8)
        val response =
            send(
                "$address/sync/user-get-key",
                "POST",
                token,
                contentType = "application/json",
                body = body,
            )
        if (response.code == 401) throw SyncAuthException(SyncCopy.SIGN_IN_AGAIN)
        if (response.code != 200) return null
        val data = jsonObject(response.bytes)["data"]?.jsonObject ?: return null
        val id = data.string("id") ?: return null
        val salt = data.string("salt") ?: return null
        val test = data.string("test") ?: return null
        return KeyMaterial(id, salt, test)
    }

    private fun jsonObject(bytes: ByteArray): JsonObject = json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject

    private fun encodeName(name: String): String = URLEncoder.encode(name, Charsets.UTF_8.name()).replace("+", "%20")

    private fun send(
        url: String,
        method: String,
        token: String?,
        contentType: String?,
        body: ByteArray?,
        headers: Map<String, String> = emptyMap(),
        onProgress: ((Long, Long) -> Unit)? = null,
    ): HttpBytes {
        val connection =
            try {
                (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    instanceFollowRedirects = false
                    useCaches = false
                    connectTimeout = 10_000
                    readTimeout = 60_000
                    if (token != null) setRequestProperty("X-ACTUAL-TOKEN", token)
                    if (contentType != null) setRequestProperty("Content-Type", contentType)
                    headers.forEach { (key, value) -> setRequestProperty(key, value) }
                    if (body != null) {
                        doOutput = true
                        outputStream.use { it.write(body) }
                    }
                }
            } catch (error: IOException) {
                throw SyncNetworkException(error)
            }
        return try {
            val code = connection.responseCode
            val total = connection.contentLengthLong
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            val bytes =
                if (stream == null) {
                    ByteArray(0)
                } else {
                    stream.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            onProgress?.invoke(output.size().toLong(), total)
                        }
                        output.toByteArray()
                    }
                }
            HttpBytes(code, bytes)
        } catch (error: IOException) {
            throw SyncNetworkException(error)
        } finally {
            connection.disconnect()
        }
    }

    private class HttpBytes(
        val code: Int,
        val bytes: ByteArray,
    )
}

private fun JsonObject.string(name: String): String? {
    val value = this[name] ?: return null
    val primitive = value as? JsonPrimitive ?: return null
    if (primitive.isString.not() && primitive.contentOrNull == "null") return null
    return primitive.contentOrNull
}

private fun JsonObject.int(name: String): Int = string(name)?.toIntOrNull() ?: 0
