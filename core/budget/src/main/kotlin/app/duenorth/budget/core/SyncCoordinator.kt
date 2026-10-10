package app.duenorth.budget.core

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import javax.crypto.SecretKey

data class ConnectResult(
    val files: List<RemoteFile>,
    val error: String?,
)

sealed interface OpenResult {
    data object Opened : OpenResult

    data object NeedsPassword : OpenResult

    data class Failed(
        val reason: String,
    ) : OpenResult
}

sealed interface UnlockResult {
    data object Unlocked : UnlockResult

    data class Failed(
        val reason: String,
    ) : UnlockResult
}

sealed interface EditResult {
    data object Saved : EditResult

    data class Rejected(
        val reason: String,
    ) : EditResult
}

sealed interface UploadResult {
    data class Uploaded(
        val fileId: String,
    ) : UploadResult

    data class Failed(
        val reason: String,
    ) : UploadResult
}

data class SyncResult(
    val status: String,
)

data class BudgetConflict(
    val id: String,
    val label: String,
    val localText: String,
    val remoteText: String,
)

interface SecretStore {
    fun get(key: String): String?

    fun put(
        key: String,
        value: String,
    )

    fun remove(key: String)
}

class MemorySecretStore : SecretStore {
    private val values = HashMap<String, String>()

    override fun get(key: String): String? = values[key]

    override fun put(
        key: String,
        value: String,
    ) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }

    fun snapshot(): Map<String, String> = values.toMap()
}

fun syncStatusLabel(
    epochMillis: Long,
    zone: ZoneId,
): String {
    val time = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime()
    val hour = time.hour % 12
    val shown = if (hour == 0) 12 else hour
    val minute = time.minute.toString().padStart(2, '0')
    val suffix = if (time.hour < 12) "am" else "pm"
    return "synced $shown:$minute $suffix"
}

fun syncFraction(
    received: Long,
    total: Long,
): Float =
    when {
        total > 0 -> (received.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        received <= 0L -> 0.08f
        else -> (received.toFloat() / (received + 65_536f)).coerceIn(0.08f, 0.9f)
    }

/**
 * Actual sync for one phone. Network calls are the caller's job to keep off
 * the thread that draws; this type does not open a socket on its own schedule.
 * The sqlite file is closed before each HTTP call.
 */
class SyncCoordinator(
    private val library: BudgetLibrary,
    private val transport: ActualTransport,
    private val secrets: SecretStore,
    private val dates: BudgetClock,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val ids: () -> String = { UUID.randomUUID().toString() },
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val syncClock: SyncClock
    private val memoryKeys = HashMap<String, SecretKey>()
    private val pendingMaterial = HashMap<String, KeyMaterial>()

    var remoteFiles: List<RemoteFile> = emptyList()
        private set

    init {
        val settings = library.settings()
        val node = settings.syncNode?.takeIf { it.length == 16 } ?: newNode()
        if (settings.syncNode != node) library.save(settings.copy(syncNode = node))
        syncClock = SyncClock(node, now)
    }

    fun signedIn(): Boolean = !secrets.get(TOKEN).isNullOrBlank()

    fun syncToken(): String? = secrets.get(TOKEN)

    fun connect(
        address: String,
        password: String,
    ): ConnectResult {
        val normalized = normalizeAddress(address) ?: return ConnectResult(emptyList(), SyncCopy.ADDRESS_FAILED)
        return try {
            val token = transport.login(normalized, password)
            secrets.put(TOKEN, token)
            library.updateSettings { it.copy(serverAddress = normalized) }
            val files = transport.listFiles(normalized, token)
            remoteFiles = files
            ConnectResult(files, null)
        } catch (_: SyncAuthException) {
            ConnectResult(remoteFiles, SyncCopy.SIGN_IN_FAILED)
        } catch (_: Exception) {
            ConnectResult(remoteFiles, SyncCopy.ADDRESS_FAILED)
        }
    }

    fun refreshRemote(): ConnectResult {
        val token = secrets.get(TOKEN) ?: return ConnectResult(emptyList(), SyncCopy.SIGN_IN_AGAIN)
        val address = library.settings().serverAddress ?: return ConnectResult(emptyList(), SyncCopy.ADDRESS_FAILED)
        return try {
            val files = transport.listFiles(address, token)
            remoteFiles = files
            ConnectResult(files, null)
        } catch (_: SyncAuthException) {
            secrets.remove(TOKEN)
            remoteFiles = emptyList()
            ConnectResult(emptyList(), SyncCopy.SIGN_IN_AGAIN)
        } catch (_: Exception) {
            ConnectResult(remoteFiles, SyncCopy.ADDRESS_FAILED)
        }
    }

    fun signOut() {
        secrets.remove(TOKEN)
        remoteFiles = emptyList()
    }

    fun unlock(
        file: RemoteFile,
        password: String,
        askEachTime: Boolean,
    ): UnlockResult {
        val material =
            localMaterial(file.fileId) ?: try {
                fetchMaterial(file)
            } catch (_: SyncAuthException) {
                return UnlockResult.Failed(SyncCopy.SIGN_IN_AGAIN)
            } catch (_: Exception) {
                return UnlockResult.Failed(SyncCopy.ADDRESS_FAILED)
            }
        if (material == null) return UnlockResult.Failed(SyncCopy.PASSWORD_WRONG)
        val key = BudgetCrypto.verify(password, material.salt, material.test) ?: return UnlockResult.Failed(SyncCopy.PASSWORD_WRONG)
        memoryKeys[file.fileId] = key
        pendingMaterial[file.fileId] = material
        library.useDatabase(file.fileId) { session ->
            SyncSchema.putState(session, "keySalt", material.salt)
            SyncSchema.putState(session, "keyTest", material.test)
            SyncSchema.putState(session, "keyId", material.id)
            SyncSchema.putState(session, "encrypted", "1")
        }
        library.updateSettings { settings ->
            val asking = settings.askEachTime.toMutableSet()
            if (askEachTime) asking.add(file.fileId) else asking.remove(file.fileId)
            settings.copy(askEachTime = asking.toList())
        }
        if (askEachTime) {
            secrets.remove(keySecret(file.fileId))
        } else {
            secrets.put(keySecret(file.fileId), BudgetCrypto.export(key))
        }
        return UnlockResult.Unlocked
    }

    fun isUnlocked(fileId: String): Boolean {
        if (memoryKeys.containsKey(fileId)) return true
        if (asksEachTime(fileId)) return false
        val saved = secrets.get(keySecret(fileId)) ?: return false
        memoryKeys[fileId] = BudgetCrypto.import(saved)
        return true
    }

    fun needsPassword(budgetId: String): Boolean {
        if (state(budgetId, "encrypted") != "1") return false
        return !isUnlocked(budgetId)
    }

    fun lock(budgetId: String) {
        if (state(budgetId, "encrypted") != "1") return
        if (!asksEachTime(budgetId)) return
        memoryKeys.remove(budgetId)
    }

    fun open(
        file: RemoteFile,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): OpenResult {
        val token = secrets.get(TOKEN) ?: return OpenResult.Failed(SyncCopy.SIGN_IN_AGAIN)
        val address = library.settings().serverAddress ?: return OpenResult.Failed(SyncCopy.ADDRESS_FAILED)
        if (file.encryptKeyId != null && !isUnlocked(file.fileId)) return OpenResult.NeedsPassword
        val tracker = DownloadTracker()
        val bytes =
            try {
                transport.download(address, token, file.fileId) { received, total ->
                    tracker.onBytes(received)
                    onProgress(tracker.received, total)
                }
            } catch (_: SyncAuthException) {
                tracker.fail()
                onProgress(0, 0)
                return OpenResult.Failed(SyncCopy.SIGN_IN_AGAIN)
            } catch (_: Exception) {
                tracker.fail()
                onProgress(0, 0)
                return OpenResult.Failed(SyncCopy.ADDRESS_FAILED)
            }
        val plain =
            if (file.encryptKeyId != null) {
                val key = memoryKeys[file.fileId] ?: return OpenResult.NeedsPassword
                try {
                    BudgetCrypto.decrypt(bytes, key)
                } catch (_: Exception) {
                    return OpenResult.Failed(SyncCopy.PASSWORD_WRONG)
                }
            } else {
                bytes
            }
        return try {
            library.installDownloaded(file.fileId, file.name, plain)
            library.useDatabase(file.fileId) { session ->
                SyncSchema.putState(session, "cloudFileId", file.fileId)
                SyncSchema.putState(session, "groupId", file.groupId)
                SyncSchema.putState(session, "keyId", file.encryptKeyId.orEmpty())
                SyncSchema.putState(session, "encrypted", if (file.encryptKeyId != null) "1" else "0")
                pendingMaterial.remove(file.fileId)?.let { material ->
                    SyncSchema.putState(session, "keySalt", material.salt)
                    SyncSchema.putState(session, "keyTest", material.test)
                }
            }
            library.switchTo(file.fileId)
            sync(file.fileId)
            OpenResult.Opened
        } catch (_: Exception) {
            OpenResult.Failed(SyncCopy.ADDRESS_FAILED)
        }
    }

    fun sync(budgetId: String): SyncResult {
        val token = secrets.get(TOKEN) ?: return fail(budgetId, "auth")
        val address = library.settings().serverAddress ?: return SyncResult("")
        val cloud = state(budgetId, "cloudFileId").orEmpty()
        val group = state(budgetId, "groupId").orEmpty()
        if (cloud.isBlank() || group.isBlank()) return SyncResult(status(budgetId))
        if (state(budgetId, "encrypted") == "1" && !isUnlocked(budgetId)) {
            return SyncResult(SyncCopy.PASSWORD_WRONG)
        }
        restoreClock(budgetId)
        val since = state(budgetId, "lastRemote").orEmpty().ifBlank { SyncClock.ZERO }
        val pending = pending(budgetId)
        val body =
            SyncProto.encodeRequest(
                fileId = cloud,
                groupId = group,
                keyId = state(budgetId, "keyId").orEmpty(),
                since = since,
                messages = pending.map { encodeOutgoing(budgetId, it) },
            )
        val responseBytes =
            try {
                transport.sync(address, token, body)
            } catch (_: SyncAuthException) {
                return fail(budgetId, "auth")
            } catch (_: Exception) {
                return fail(budgetId, "failed")
            }
        val response =
            try {
                SyncProto.decodeResponse(responseBytes)
            } catch (_: Exception) {
                return fail(budgetId, "failed")
            }
        val decoded = mutableListOf<Pair<String, CellMessage>>()
        for (envelope in response.messages) {
            val cell = decodeIncoming(budgetId, envelope) ?: return SyncResult(SyncCopy.PASSWORD_WRONG)
            decoded.add(envelope.timestamp to cell)
        }
        library.useDatabase(budgetId) { session ->
            decoded.forEach { (timestamp, cell) ->
                syncClock.observe(timestamp)
                SyncSchema.applyRemote(session, timestamp, cell)
            }
            SyncSchema.acceptPending(session, pending.map { it.timestamp })
            val newest = response.messages.maxOfOrNull { it.timestamp }
            if (newest != null && newest > since) SyncSchema.putState(session, "lastRemote", newest)
            SyncSchema.putState(session, "lastSuccessEpoch", now().toString())
            SyncSchema.putState(session, "lastFailure", "")
            SyncSchema.putState(session, "merkle", response.merkle)
        }
        return SyncResult(status(budgetId))
    }

    fun assign(
        budgetId: String,
        categoryId: String,
        month: YearMonth,
        amountText: String,
    ): EditResult {
        val amount = MoneyFormat.parse(amountText, decimals(budgetId)) ?: return EditResult.Rejected(SyncCopy.ENTER_AMOUNT)
        restoreClock(budgetId)
        library.useDatabase(budgetId) { session ->
            BudgetEdits.assign(session, syncClock, month.toActualMonth(), categoryId, amount)
        } ?: return EditResult.Rejected(SyncCopy.LAST_FAILED)
        return EditResult.Saved
    }

    fun addTransaction(
        budgetId: String,
        payee: String,
        amountText: String,
    ): EditResult {
        val amount = MoneyFormat.parse(amountText, decimals(budgetId)) ?: return EditResult.Rejected(SyncCopy.ENTER_AMOUNT)
        val name = payee.trim().ifEmpty { ShellCopy.NO_PAYEE }
        restoreClock(budgetId)
        library.useDatabase(budgetId) { session ->
            BudgetEdits.addTransaction(session, syncClock, dates.today(), name, amount, ids)
        } ?: return EditResult.Rejected(SyncCopy.LAST_FAILED)
        return EditResult.Saved
    }

    fun uploadNew(budgetId: String): UploadResult = upload(budgetId, fileId = null, groupId = null, name = budgetName(budgetId))

    fun uploadOnto(
        budgetId: String,
        file: RemoteFile,
    ): UploadResult = upload(budgetId, file.fileId, file.groupId, file.name)

    fun status(budgetId: String): String {
        when (state(budgetId, "lastFailure")) {
            "auth" -> return SyncCopy.SIGN_IN_AGAIN
            "failed" -> return SyncCopy.LAST_FAILED
        }
        val epoch = state(budgetId, "lastSuccessEpoch")?.toLongOrNull() ?: return ""
        return syncStatusLabel(epoch, zone)
    }

    fun conflicts(budgetId: String): List<BudgetConflict> {
        val currency = currency(budgetId)
        return library.useDatabase(budgetId) { session ->
            SyncSchema.ensure(session)
            session.query("SELECT id, row, local_value, remote_value FROM conflicts ORDER BY remote_timestamp").map { row ->
                val local = row.str("local_value")?.toLongOrNull() ?: 0L
                val remoteValue = row.str("remote_value")?.toLongOrNull() ?: 0L
                BudgetConflict(
                    id = row.str("id").orEmpty(),
                    label = categoryLabel(session, row.str("row").orEmpty()),
                    localText = MoneyFormat.format(local, currency),
                    remoteText = MoneyFormat.format(remoteValue, currency),
                )
            }
        } ?: emptyList()
    }

    fun markConflictsSeen(budgetId: String) {
        library.useDatabase(budgetId) { session ->
            SyncSchema.ensure(session)
            session.exec("UPDATE conflicts SET seen = 1")
        }
    }

    fun categories(budgetId: String): List<CategoryChoice> =
        library.useDatabase(budgetId) { session ->
            session
                .query(
                    """
                    SELECT c.id AS id, c.name AS name, COALESCE(g.name, '') AS group_name
                    FROM categories c
                    LEFT JOIN category_groups g ON g.id = c.cat_group
                    WHERE IFNULL(c.tombstone, 0) = 0 AND IFNULL(c.is_income, 0) = 0
                    ORDER BY g.sort_order, c.sort_order, c.id
                    """.trimIndent(),
                ).map {
                    CategoryChoice(
                        id = it.str("id").orEmpty(),
                        name = it.str("name").orEmpty(),
                        groupName = it.str("group_name").orEmpty(),
                        income = false,
                    )
                }
        } ?: emptyList()

    private fun upload(
        budgetId: String,
        fileId: String?,
        groupId: String?,
        name: String,
    ): UploadResult {
        val token = secrets.get(TOKEN) ?: return UploadResult.Failed(SyncCopy.SIGN_IN_AGAIN)
        val address = library.settings().serverAddress ?: return UploadResult.Failed(SyncCopy.ADDRESS_FAILED)
        val database = java.io.File(java.io.File(library.root, budgetId), "db.sqlite")
        if (!database.isFile) return UploadResult.Failed(SyncCopy.LAST_FAILED)
        val chosen =
            fileId ?: run {
                val taken = runCatching { transport.listFiles(address, token).map { it.fileId }.toSet() }.getOrDefault(emptySet())
                var created = ids()
                while (created in taken) created = ids()
                created
            }
        return try {
            val group =
                transport.upload(
                    address = address,
                    token = token,
                    fileId = chosen,
                    groupId = groupId,
                    name = name,
                    bytes = database.readBytes(),
                )
            library.useDatabase(budgetId) { session ->
                SyncSchema.putState(session, "cloudFileId", chosen)
                if (group.isNotBlank()) SyncSchema.putState(session, "groupId", group)
            }
            remoteFiles = transport.listFiles(address, token)
            UploadResult.Uploaded(chosen)
        } catch (_: SyncAuthException) {
            fail(budgetId, "auth")
            UploadResult.Failed(SyncCopy.SIGN_IN_AGAIN)
        } catch (_: Exception) {
            UploadResult.Failed(SyncCopy.ADDRESS_FAILED)
        }
    }

    private fun fail(
        budgetId: String,
        kind: String,
    ): SyncResult {
        library.useDatabase(budgetId) { session ->
            SyncSchema.putState(session, "lastFailure", kind)
        }
        if (kind == "auth") secrets.remove(TOKEN)
        return SyncResult(if (kind == "auth") SyncCopy.SIGN_IN_AGAIN else SyncCopy.LAST_FAILED)
    }

    private fun pending(budgetId: String): List<Pending> =
        library.useDatabase(budgetId) { session ->
            SyncSchema.ensure(session)
            session
                .query(
                    """
                    SELECT timestamp, dataset, row, column, value FROM messages
                    WHERE pending = 1 ORDER BY timestamp
                    """.trimIndent(),
                ).map { row ->
                    Pending(
                        timestamp = row.str("timestamp").orEmpty(),
                        cell =
                            CellMessage(
                                dataset = row.str("dataset").orEmpty(),
                                row = row.str("row").orEmpty(),
                                column = row.str("column").orEmpty(),
                                value = row.str("value").orEmpty(),
                            ),
                    )
                }
        } ?: emptyList()

    private fun encodeOutgoing(
        budgetId: String,
        pending: Pending,
    ): Envelope {
        val plain = SyncProto.encodeMessage(pending.cell)
        val key = memoryKeys[budgetId]
        return if (key != null && state(budgetId, "encrypted") == "1") {
            Envelope(pending.timestamp, true, BudgetCrypto.encryptMessage(plain, key))
        } else {
            Envelope(pending.timestamp, false, plain)
        }
    }

    private fun decodeIncoming(
        budgetId: String,
        envelope: Envelope,
    ): CellMessage? {
        val bytes =
            if (!envelope.isEncrypted) {
                envelope.content
            } else {
                val key = memoryKeys[budgetId] ?: return null
                try {
                    BudgetCrypto.decryptMessage(envelope.content, key)
                } catch (_: Exception) {
                    return null
                }
            }
        return SyncProto.decodeMessage(bytes)
    }

    private fun fetchMaterial(file: RemoteFile): KeyMaterial? {
        val token = secrets.get(TOKEN) ?: throw SyncAuthException(SyncCopy.SIGN_IN_AGAIN)
        val address = library.settings().serverAddress ?: return null
        return transport.encryptionKey(address, token, file.fileId)
    }

    private fun localMaterial(fileId: String): KeyMaterial? {
        val salt = state(fileId, "keySalt") ?: return null
        val test = state(fileId, "keyTest") ?: return null
        val id = state(fileId, "keyId").orEmpty()
        if (salt.isBlank() || test.isBlank()) return null
        return KeyMaterial(id, salt, test)
    }

    private fun restoreClock(budgetId: String) {
        syncClock.restoreIfBehind(state(budgetId, "clock"))
    }

    private fun state(
        budgetId: String,
        key: String,
    ): String? = library.useDatabase(budgetId) { SyncSchema.state(it, key) }

    private fun asksEachTime(fileId: String): Boolean = fileId in library.settings().askEachTime

    private fun decimals(budgetId: String): Int = currency(budgetId).decimals

    private fun currency(budgetId: String): CurrencySpec {
        val code =
            library.useDatabase(budgetId) { session ->
                session.query("SELECT value FROM preferences WHERE id = ?", listOf("currency")).firstOrNull()?.str("value")
            }
        return Currencies.byCode(code.orEmpty()) ?: Currencies.byCode("USD")!!
    }

    private fun budgetName(budgetId: String): String = library.list().find { it.id == budgetId }?.name ?: "budget"

    private fun categoryLabel(
        session: SqlSession,
        row: String,
    ): String {
        val categoryId = row.substringAfter('-', row)
        return session.query("SELECT name FROM categories WHERE id = ?", listOf(categoryId)).firstOrNull()?.str("name")
            ?: row
    }

    private fun keySecret(fileId: String): String = "budget.key.$fileId"

    private data class Pending(
        val timestamp: String,
        val cell: CellMessage,
    )

    companion object {
        private const val TOKEN = "actual.token"

        fun normalizeAddress(raw: String): String? {
            val trimmed = raw.trim().trimEnd('/')
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return null
            if (trimmed.any { it.isWhitespace() }) return null
            return trimmed
        }

        private fun newNode(): String =
            UUID
                .randomUUID()
                .toString()
                .replace("-", "")
                .takeLast(16)
                .uppercase()
    }
}
