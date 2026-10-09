package app.duenorth.budget.core

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.Closeable
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Localhost stand-in for an Actual sync server. Speaks the same routes the phone uses.
 */
class LocalActualServer(
    private val password: String = "server-secret",
) : Closeable {
    var beforeSync: () -> Unit = {}
    var rejectToken: Boolean = false
    var online: Boolean = true

    private val json = Json { ignoreUnknownKeys = true }
    private val clock = SyncClock("SERVER0000000001")
    private val files = LinkedHashMap<String, ServerFile>()
    private val token = UUID.randomUUID().toString()
    private val pool = Executors.newCachedThreadPool()
    private val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val lock = Any()

    val baseUrl: String

    init {
        http.executor = pool
        http.createContext("/") { exchange ->
            try {
                route(exchange)
            } catch (error: Exception) {
                val bytes = error.message.orEmpty().toByteArray()
                exchange.sendResponseHeaders(500, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
        }
        http.start()
        baseUrl = "http://127.0.0.1:${http.address.port}"
    }

    fun addFile(
        name: String,
        bytes: ByteArray,
        fileId: String = UUID.randomUUID().toString(),
    ): RemoteFile {
        val file =
            ServerFile(
                id = fileId,
                groupId = UUID.randomUUID().toString(),
                name = name,
                bytes = bytes,
            )
        synchronized(lock) { files[fileId] = file }
        return file.remote()
    }

    fun addEncrypted(
        name: String,
        plain: ByteArray,
        budgetPassword: String,
        fileId: String = UUID.randomUUID().toString(),
    ): RemoteFile {
        val salt = BudgetCrypto.newSalt()
        val key = BudgetCrypto.derive(budgetPassword, salt)
        val file =
            ServerFile(
                id = fileId,
                groupId = UUID.randomUUID().toString(),
                name = name,
                bytes = BudgetCrypto.encrypt(plain, key),
                encryptKeyId = UUID.randomUUID().toString(),
                salt = BudgetCrypto.saltText(salt),
                test = BudgetCrypto.testBlob(budgetPassword, salt),
            )
        synchronized(lock) { files[fileId] = file }
        return file.remote()
    }

    fun appendChange(
        fileId: String,
        dataset: String,
        row: String,
        column: String,
        value: String,
    ) {
        val stamp = clock.send()
        val content = SyncProto.encodeMessage(CellMessage(dataset, row, column, value))
        synchronized(lock) {
            files.getValue(fileId).envelopes.add(Envelope(stamp, false, content))
        }
    }

    fun plainMessages(fileId: String): List<CellMessage> =
        synchronized(lock) {
            files
                .getValue(fileId)
                .envelopes
                .filter { !it.isEncrypted }
                .map { SyncProto.decodeMessage(it.content) }
        }

    fun bytes(fileId: String): ByteArray = synchronized(lock) { files.getValue(fileId).bytes.copyOf() }

    override fun close() {
        http.stop(0)
        pool.shutdownNow()
    }

    private fun route(exchange: HttpExchange) {
        if (!online) {
            exchange.respond(503, ByteArray(0), "text/plain")
            return
        }
        val path = exchange.requestURI.path
        val method = exchange.requestMethod
        when {
            method == "POST" && path == "/account/login" -> login(exchange)
            method == "GET" && path == "/sync/list-user-files" -> list(exchange)
            method == "GET" && path == "/sync/download-user-file" -> download(exchange)
            method == "POST" && path == "/sync/upload-user-file" -> upload(exchange)
            method == "POST" && path == "/sync/sync" -> sync(exchange)
            method == "POST" && path == "/sync/user-get-key" -> key(exchange)
            else -> exchange.respond(404, ByteArray(0), null)
        }
    }

    private fun login(exchange: HttpExchange) {
        val body = json.parseToJsonElement(exchange.requestBody.readBytes().toString(Charsets.UTF_8)).jsonObject
        val given = body["password"]?.jsonPrimitive?.content
        if (given != password) {
            exchange.respond(400, """{"status":"error","reason":"invalid-password"}""".toByteArray(), "application/json")
            return
        }
        val text =
            buildJsonObject {
                put("status", "ok")
                put("data", buildJsonObject { put("token", token) })
            }.toString()
        exchange.respond(200, text.toByteArray(), "application/json")
    }

    private fun list(exchange: HttpExchange) {
        if (!authorized(exchange)) {
            exchange.respond(401, """{"status":"error","reason":"unauthorized"}""".toByteArray(), "application/json")
            return
        }
        val rows =
            synchronized(lock) {
                files.values.joinToString(",") { file ->
                    buildJsonObject {
                        put("fileId", file.id)
                        put("groupId", file.groupId)
                        put("name", file.name)
                        put("deleted", 0)
                        if (file.encryptKeyId != null) put("encryptKeyId", file.encryptKeyId)
                    }.toString()
                }
            }
        exchange.respond(200, """{"status":"ok","data":[$rows]}""".toByteArray(), "application/json")
    }

    private fun download(exchange: HttpExchange) {
        if (!authorized(exchange)) {
            exchange.respond(401, """{"status":"error","reason":"unauthorized"}""".toByteArray(), "application/json")
            return
        }
        val fileId = exchange.requestHeaders.getFirst("X-ACTUAL-FILE-ID")
        val bytes = synchronized(lock) { files[fileId]?.bytes }
        if (bytes == null) {
            exchange.respond(400, "file-not-found".toByteArray(), "text/plain")
            return
        }
        exchange.respond(200, bytes, "application/octet-stream")
    }

    private fun upload(exchange: HttpExchange) {
        if (!authorized(exchange)) {
            exchange.respond(401, """{"status":"error","reason":"unauthorized"}""".toByteArray(), "application/json")
            return
        }
        val fileId = exchange.requestHeaders.getFirst("X-ACTUAL-FILE-ID")
        val name = URLDecoder.decode(exchange.requestHeaders.getFirst("X-ACTUAL-NAME").orEmpty(), Charsets.UTF_8)
        val groupHeader = exchange.requestHeaders.getFirst("X-ACTUAL-GROUP-ID")
        val body = exchange.requestBody.readBytes()
        if (fileId.isNullOrBlank()) {
            exchange.respond(400, "fileId is required".toByteArray(), "text/plain")
            return
        }
        val group =
            synchronized(lock) {
                val current = files[fileId]
                if (current == null) {
                    val created = groupHeader ?: UUID.randomUUID().toString()
                    files[fileId] =
                        ServerFile(id = fileId, groupId = created, name = name, bytes = body)
                    created
                } else {
                    current.bytes = body
                    current.name = name
                    if (!groupHeader.isNullOrBlank()) current.groupId = groupHeader
                    current.groupId
                }
            }
        exchange.respond(200, """{"status":"ok","groupId":"$group"}""".toByteArray(), "application/json")
    }

    private fun sync(exchange: HttpExchange) {
        if (!authorized(exchange)) {
            exchange.respond(401, """{"status":"error","reason":"unauthorized"}""".toByteArray(), "application/json")
            return
        }
        val request = SyncProto.decodeRequest(exchange.requestBody.readBytes())
        if (request.since.isBlank()) {
            exchange.respond(422, """{"status":"error","reason":"unprocessable-entity"}""".toByteArray(), "application/json")
            return
        }
        synchronized(lock) {
            val file = files[request.fileId]
            if (file == null) {
                exchange.respond(400, "file-not-found".toByteArray(), "text/plain")
                return
            }
            request.messages.forEach { envelope ->
                if (file.envelopes.none { it.timestamp == envelope.timestamp }) file.envelopes.add(envelope)
            }
        }
        beforeSync()
        val response =
            synchronized(lock) {
                val file = files.getValue(request.fileId)
                val outgoing = file.envelopes.filter { it.timestamp > request.since }
                SyncProto.encodeResponse("""{"hash":${file.envelopes.size}}""", outgoing)
            }
        exchange.responseHeaders.add("X-ACTUAL-SYNC-METHOD", "simple")
        exchange.respond(200, response, "application/actual-sync")
    }

    private fun key(exchange: HttpExchange) {
        if (!authorized(exchange)) {
            exchange.respond(401, """{"status":"error","reason":"unauthorized"}""".toByteArray(), "application/json")
            return
        }
        val body = json.parseToJsonElement(exchange.requestBody.readBytes().toString(Charsets.UTF_8)).jsonObject
        val fileId = body["fileId"]?.jsonPrimitive?.content
        val file = synchronized(lock) { files[fileId] }
        if (file?.salt == null || file.test == null) {
            exchange.respond(400, """{"status":"error","reason":"file-not-found"}""".toByteArray(), "application/json")
            return
        }
        val text =
            buildJsonObject {
                put("status", "ok")
                put(
                    "data",
                    buildJsonObject {
                        put("id", file.encryptKeyId)
                        put("salt", file.salt)
                        put("test", file.test)
                    },
                )
            }.toString()
        exchange.respond(200, text.toByteArray(), "application/json")
    }

    private fun authorized(exchange: HttpExchange): Boolean {
        if (rejectToken) return false
        return exchange.requestHeaders.getFirst("X-ACTUAL-TOKEN") == token
    }

    private class ServerFile(
        val id: String,
        var groupId: String,
        var name: String,
        var bytes: ByteArray,
        var encryptKeyId: String? = null,
        var salt: String? = null,
        var test: String? = null,
        val envelopes: MutableList<Envelope> = mutableListOf(),
    ) {
        fun remote() = RemoteFile(id, groupId, name, encryptKeyId)
    }
}

private fun HttpExchange.respond(
    code: Int,
    bytes: ByteArray,
    contentType: String?,
) {
    if (contentType != null) responseHeaders.add("Content-Type", contentType)
    sendResponseHeaders(code, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
}
