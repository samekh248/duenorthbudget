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
    /** The phone's value is the newer one, so it is the value every device now shows. */
    val phoneKept: Boolean = false,
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
    /** Waits before each retry of a sync whose connection dropped; one entry per retry. */
    private val retryDelays: List<Long> = listOf(1_000L, 3_000L),
    private val pause: (Long) -> Unit = { Thread.sleep(it) },
    private val bankTransport: BankSyncTransport = HttpBankSyncTransport(),
) {
    private val syncClock: SyncClock
    private val syncLock = Any()
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

    fun signedIn(): Boolean = !syncToken().isNullOrBlank()

    fun syncToken(): String? {
        val activeId = library.settings().activeInstanceId ?: return migrateLegacyToken(null)
        return tokenFor(activeId) ?: migrateLegacyToken(activeId)
    }

    fun connect(
        address: String,
        password: String,
        label: String? = null,
    ): ConnectResult {
        val normalized = normalizeAddress(address) ?: return ConnectResult(emptyList(), SyncCopy.ADDRESS_FAILED)
        return try {
            val token = transport.login(normalized, password)
            val instance = upsertConnectedInstance(normalized, label)
            secrets.put(tokenKey(instance.id), token)
            secrets.remove(LEGACY_TOKEN)
            library.setActiveInstanceId(instance.id)
            val files = transport.listFiles(normalized, token)
            remoteFiles = files
            ConnectResult(files, null)
        } catch (_: SyncAuthException) {
            ConnectResult(remoteFiles, SyncCopy.SIGN_IN_FAILED)
        } catch (_: Exception) {
            ConnectResult(remoteFiles, SyncCopy.ADDRESS_FAILED)
        }
    }

    fun loginMethods(address: String): List<String> {
        val normalized = normalizeAddress(address) ?: return listOf("password")
        return try {
            transport.loginMethods(normalized)
        } catch (_: Exception) {
            listOf("password")
        }
    }

    fun beginOpenId(
        address: String,
        returnUrl: String,
    ): String? {
        val normalized = normalizeAddress(address) ?: return null
        return try {
            transport.beginOpenId(normalized, returnUrl)
        } catch (_: SyncAuthException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    fun completeOpenId(
        address: String,
        token: String,
        label: String? = null,
    ): ConnectResult {
        val normalized = normalizeAddress(address) ?: return ConnectResult(emptyList(), SyncCopy.ADDRESS_FAILED)
        if (token.isBlank()) return ConnectResult(remoteFiles, SyncCopy.SIGN_IN_FAILED)
        return try {
            val instance = upsertConnectedInstance(normalized, label)
            secrets.put(tokenKey(instance.id), token)
            secrets.remove(LEGACY_TOKEN)
            library.setActiveInstanceId(instance.id)
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
        val token = syncToken() ?: return ConnectResult(emptyList(), SyncCopy.SIGN_IN_AGAIN)
        val address = activeAddress() ?: return ConnectResult(emptyList(), SyncCopy.ADDRESS_FAILED)
        return try {
            val files = transport.listFiles(address, token)
            remoteFiles = files
            ConnectResult(files, null)
        } catch (_: SyncAuthException) {
            clearActiveToken()
            remoteFiles = emptyList()
            ConnectResult(emptyList(), SyncCopy.SIGN_IN_AGAIN)
        } catch (_: Exception) {
            ConnectResult(remoteFiles, SyncCopy.ADDRESS_FAILED)
        }
    }

    fun makeActive(instanceId: String): Boolean {
        val settings = library.settings()
        if (settings.instances.none { it.id == instanceId }) return false
        library.setActiveInstanceId(instanceId)
        remoteFiles = emptyList()
        return true
    }

    fun activateOffline(instanceId: String): Boolean = makeActive(instanceId)

    fun removeInstance(instanceId: String) {
        secrets.remove(tokenKey(instanceId))
        val wasActive = library.settings().activeInstanceId == instanceId
        library.removeInstance(instanceId)
        if (wasActive) {
            secrets.remove(LEGACY_TOKEN)
            remoteFiles = emptyList()
        }
    }

    fun signOut() {
        clearActiveToken()
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

    /** Whether the budget has an encryption password, whether or not it is unlocked now. */
    fun isEncrypted(budgetId: String): Boolean = state(budgetId, "encrypted") == "1"

    fun needsPassword(budgetId: String): Boolean {
        if (state(budgetId, "encrypted") != "1") return false
        return !isUnlocked(budgetId)
    }

    fun lock(budgetId: String) {
        if (state(budgetId, "encrypted") != "1") return
        if (!asksEachTime(budgetId)) return
        memoryKeys.remove(budgetId)
    }

    /**
     * Downloads [file] and makes it the open budget. With [replace], every other budget on the
     * phone is cleared once the download is in place, so the server file becomes the only one.
     */
    fun open(
        file: RemoteFile,
        replace: Boolean = false,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): OpenResult {
        val token = syncToken() ?: return OpenResult.Failed(SyncCopy.SIGN_IN_AGAIN)
        val address = activeAddress() ?: return OpenResult.Failed(SyncCopy.ADDRESS_FAILED)
        if (file.encryptKeyId != null && !isUnlocked(file.fileId)) return OpenResult.NeedsPassword
        // Changes made on this copy that the server has not stored yet survive the fresh download.
        val unsent =
            runCatching { library.useDatabase(file.fileId) { SyncSchema.pendingMessages(it) } }.getOrNull().orEmpty()
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
        try {
            library.installDownloaded(file.fileId, file.name, ActualFileFormat.database(plain))
            library.useDatabase(file.fileId) { session ->
                // A file another phone uploaded carries that phone's sync bookkeeping; none of it is ours.
                SyncSchema.ensure(session)
                session.exec("DELETE FROM conflicts")
                SyncSchema.putState(session, "lastFailure", "")
                SyncSchema.putState(session, "lastSuccessEpoch", "")
                SyncSchema.restorePending(session, unsent)
                SyncSchema.putState(session, "cloudFileId", file.fileId)
                SyncSchema.putState(session, "groupId", file.groupId)
                SyncSchema.putState(session, "keyId", file.encryptKeyId.orEmpty())
                SyncSchema.putState(session, "encrypted", if (file.encryptKeyId != null) "1" else "0")
                SyncSchema.markBackfilled(session)
                pendingMaterial.remove(file.fileId)?.let { material ->
                    SyncSchema.putState(session, "keySalt", material.salt)
                    SyncSchema.putState(session, "keyTest", material.test)
                }
            }
            library.switchTo(file.fileId)
            if (replace) clearOthers(file.fileId)
        } catch (_: Exception) {
            // The download arrived; it is the file that did not open.
            return OpenResult.Failed(SyncCopy.OPEN_FAILED)
        }
        // The budget is open; a sync that fails now shows on the budget and tries again later.
        runCatching { sync(file.fileId) }
        return OpenResult.Opened
    }

    /** Clears every budget on the phone except [keepId], with its saved key. */
    fun clearOthers(keepId: String) {
        library.list().filter { it.id != keepId }.forEach { budget ->
            memoryKeys.remove(budget.id)
            pendingMaterial.remove(budget.id)
            secrets.remove(keySecret(budget.id))
            library.delete(budget.id)
        }
    }

    /**
     * A sync with nobody watching, from the background job: the open budget, only when it lives on
     * a server this phone is signed in to and needs no password now. Null when it was left alone, so
     * a budget kept only on the phone, a signed-out phone, or a locked encrypted file sends nothing
     * and records no failure.
     */
    fun syncUnattended(): SyncResult? {
        val id = library.settings().openBudgetId ?: return null
        return syncBanksThenBudget(id)
    }

    /**
     * Bank sync, then [sync], so what the banks sent goes to the server in the same run: at
     * launch and from the background job. Null, with nothing fetched or sent, when the budget is
     * kept only on the phone, the phone is signed out, or the file is locked.
     */
    fun syncBanksThenBudget(budgetId: String): SyncResult? {
        if (!canSyncUnattended(budgetId)) return null
        runCatching { syncBanks(budgetId) }
        return sync(budgetId)
    }

    /** Fetches every bank-linked account of [budgetId] into the budget; null when it was left alone. */
    fun syncBanks(
        budgetId: String,
        onAccount: (BankLinkedAccount) -> Unit = {},
        onFailure: (BankSyncFailure) -> Unit = {},
    ): BankSyncSummary? {
        if (!canSyncUnattended(budgetId)) return null
        val token = syncToken() ?: return null
        val address = activeAddress() ?: return null
        return library.syncBankAccounts(budgetId, address, token, bankTransport, dates.today(), now(), onAccount, onFailure)
    }

    /**
     * [sync] only when the budget holds changes the server has not stored and can send them
     * now: after an edit, and as the app leaves the screen. Null, with nothing sent, otherwise.
     * Two calls in a row send once, since the second finds nothing left.
     */
    fun sendUnsent(budgetId: String): SyncResult? =
        synchronized(syncLock) {
            if (!canSyncUnattended(budgetId)) return null
            if (library.useDatabase(budgetId) { SyncSchema.hasPending(it) } != true) return null
            sync(budgetId)
        }

    /** Whether leaving the screen forgets this budget's key, so the next look asks for its password. */
    fun locksWhenAway(budgetId: String): Boolean = state(budgetId, "encrypted") == "1" && asksEachTime(budgetId)

    private fun canSyncUnattended(budgetId: String): Boolean =
        signedIn() &&
            activeAddress() != null &&
            !state(budgetId, "cloudFileId").isNullOrBlank() &&
            !state(budgetId, "groupId").isNullOrBlank() &&
            !needsPassword(budgetId)

    /** Syncs, then posts any due auto-post schedules (spec 010 US3); posting needs no server. */
    fun sync(budgetId: String): SyncResult {
        val result =
            try {
                // One sync at a time per phone: two would send and apply the same messages at once.
                synchronized(syncLock) { syncNetwork(budgetId) }
            } catch (_: Exception) {
                runCatching { fail(budgetId, "failed") }.getOrElse { SyncResult(SyncCopy.LAST_FAILED) }
            }
        runCatching { library.autoPostSchedules(budgetId) }
        return result
    }

    /**
     * Actual's sync: send what the server has not stored, apply what it sends back, then compare
     * merkle tries. When they differ (a device that was offline sent changes older than the last
     * one this phone saw, or a reply was lost), sync again from where they part, sending every
     * message from there on, until both sides hold the same messages.
     */
    private fun syncNetwork(budgetId: String): SyncResult {
        val token = syncToken() ?: return fail(budgetId, "auth")
        val address = activeAddress() ?: return SyncResult("")
        val cloud = state(budgetId, "cloudFileId").orEmpty()
        val group = state(budgetId, "groupId").orEmpty()
        if (cloud.isBlank() || group.isBlank()) return SyncResult(status(budgetId))
        if (state(budgetId, "encrypted") == "1" && !isUnlocked(budgetId)) {
            return SyncResult(SyncCopy.PASSWORD_WRONG)
        }
        restoreClock(budgetId)
        var since = state(budgetId, "lastRemote").orEmpty().ifBlank { SyncClock.ZERO }
        var everything = false
        repeat(MAX_SYNC_ROUNDS) {
            val outgoing = outgoing(budgetId, since, everything)
            val body =
                SyncProto.encodeRequest(
                    fileId = cloud,
                    groupId = group,
                    keyId = state(budgetId, "keyId").orEmpty(),
                    since = since,
                    messages = outgoing.map { encodeOutgoing(budgetId, it) },
                )
            val responseBytes =
                try {
                    send(address, token, body)
                } catch (_: SyncAuthException) {
                    return fail(budgetId, "auth")
                } catch (refused: SyncFileException) {
                    return fail(budgetId, refused.reason)
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
            for (envelope in response.messages.sortedBy { it.timestamp }) {
                val cell =
                    try {
                        decodeIncoming(budgetId, envelope)
                    } catch (_: Exception) {
                        return fail(budgetId, "failed")
                    } ?: return SyncResult(SyncCopy.PASSWORD_WRONG)
                decoded.add(envelope.timestamp to cell)
            }
            val serverTrie = SyncMerkle.parse(response.merkle)
            // All of a reply lands, or none of it: a phone killed halfway applies it again next time.
            val gap =
                library.serial {
                    library.useDatabase(budgetId) { session ->
                        var differs: Long? = null
                        session.transaction {
                            decoded.forEach { (timestamp, cell) ->
                                syncClock.observe(timestamp)
                                SyncSchema.applyRemote(session, timestamp, cell)
                            }
                            SyncSchema.acceptPending(session, outgoing.map { it.timestamp })
                            SyncSchema.backfill(session, syncClock)
                            val newest = (decoded.map { it.first } + listOfNotNull(SyncSchema.state(session, "lastRemote"))).maxOrNull()
                            if (newest != null) SyncSchema.putState(session, "lastRemote", newest)
                            differs = serverTrie?.let { SyncMerkle.diff(it, SyncSchema.merkle(session)) }
                            if (differs == null) {
                                SyncSchema.putState(session, "lastSuccessEpoch", now().toString())
                                SyncSchema.putState(session, "lastFailure", "")
                                SyncSchema.putState(session, "merkle", response.merkle)
                            }
                        }
                        differs ?: NO_GAP
                    }
                } ?: return fail(budgetId, "failed")
            if (gap == NO_GAP) return SyncResult(status(budgetId))
            since = SyncMerkle.since(gap)
            everything = true
        }
        return fail(budgetId, "failed")
    }

    /** One sync request, tried again after [retryDelays] when the connection drops. */
    private fun send(
        address: String,
        token: String,
        body: ByteArray,
    ): ByteArray {
        var attempt = 0
        while (true) {
            try {
                return transport.sync(address, token, body)
            } catch (error: SyncNetworkException) {
                if (!error.transient || attempt >= retryDelays.size) throw error
                pause(retryDelays[attempt])
                attempt++
            }
        }
    }

    fun assign(
        budgetId: String,
        categoryId: String,
        month: YearMonth,
        amountText: String,
    ): EditResult {
        val amount = MoneyFormat.parse(amountText, decimals(budgetId)) ?: return EditResult.Rejected(SyncCopy.ENTER_AMOUNT)
        journaled(budgetId, UndoCopy.ASSIGN) { session ->
            BudgetEdits.assign(session, syncClock, month.toActualMonth(), categoryId, amount)
        } ?: return EditResult.Rejected(SyncCopy.LAST_FAILED)
        return EditResult.Saved
    }

    /**
     * Edit mode's done: every changed budgeted amount for [month], written as one step so one undo takes it all
     * back. When edit mode ran the templates ([templates] is whether it overwrote), they run first so their goals
     * are saved too, and the amounts then land over theirs.
     */
    fun assignBudgets(
        budgetId: String,
        month: YearMonth,
        amounts: Map<String, Long>,
        templates: Boolean? = null,
    ): EditResult {
        if (amounts.isEmpty() && templates == null) return EditResult.Saved
        journaled(budgetId, UndoCopy.BUDGET_EDIT) { session ->
            if (templates != null) TemplateEngine(session, dates.today()).run(month, overwrite = templates, confirmed = true)
            amounts.forEach { (categoryId, amount) ->
                BudgetEdits.assign(session, syncClock, month.toActualMonth(), categoryId, amount)
            }
        } ?: return EditResult.Rejected(SyncCopy.LAST_FAILED)
        return EditResult.Saved
    }

    fun moveCategory(
        budgetId: String,
        fromCategoryId: String,
        toCategoryId: String,
        month: YearMonth,
        amountText: String,
    ): EditResult {
        val amount = MoneyFormat.parse(amountText, decimals(budgetId)) ?: return EditResult.Rejected(SyncCopy.ENTER_AMOUNT)
        return journaled(budgetId, UndoCopy.MOVE) { session ->
            when (
                val outcome =
                    EnvelopeBook(session, syncClock).moveAvailable(
                        month.toActualMonth(),
                        fromCategoryId,
                        toCategoryId,
                        amount,
                    )
            ) {
                EnvelopeEditResult.Saved -> EditResult.Saved
                is EnvelopeEditResult.Rejected -> EditResult.Rejected(outcome.reason)
            }
        } ?: EditResult.Rejected(SyncCopy.LAST_FAILED)
    }

    fun setCategoryCarryover(
        budgetId: String,
        categoryId: String,
        month: YearMonth,
        enabled: Boolean,
    ): EditResult {
        journaled(budgetId, UndoCopy.CARRYOVER) { session ->
            EnvelopeBook(session, syncClock).setCarryover(month.toActualMonth(), categoryId, enabled)
        } ?: return EditResult.Rejected(SyncCopy.LAST_FAILED)
        return EditResult.Saved
    }

    fun releaseMonthHold(
        budgetId: String,
        month: YearMonth,
    ): EditResult =
        journaled(budgetId, UndoCopy.HOLD) { session ->
            when (
                val outcome =
                    EnvelopeBook(session, syncClock).setHold(month.toActualMonth(), 0L, Long.MAX_VALUE)
            ) {
                EnvelopeEditResult.Saved -> EditResult.Saved
                is EnvelopeEditResult.Rejected -> EditResult.Rejected(outcome.reason)
            }
        } ?: EditResult.Rejected(SyncCopy.LAST_FAILED)

    fun holdForNextMonth(
        budgetId: String,
        month: YearMonth,
        amountText: String,
    ): EditResult {
        val amount = MoneyFormat.parse(amountText, decimals(budgetId)) ?: return EditResult.Rejected(SyncCopy.ENTER_AMOUNT)
        return journaled(budgetId, UndoCopy.HOLD) { session ->
            val header = ShellReader.loadDetailed(session, month).figures.headerMinor
            when (
                val outcome = EnvelopeBook(session, syncClock).setHold(month.toActualMonth(), amount, header)
            ) {
                EnvelopeEditResult.Saved -> EditResult.Saved
                is EnvelopeEditResult.Rejected -> EditResult.Rejected(outcome.reason)
            }
        } ?: EditResult.Rejected(SyncCopy.LAST_FAILED)
    }

    /** What a month fill would budget each category for [month], for edit mode to show before anything is saved. */
    fun fillTargets(
        budgetId: String,
        month: YearMonth,
        kind: MonthFillKind,
    ): Map<String, Long>? =
        library.serial {
            library.useDatabase(budgetId) { session -> MonthFillBook(session, dates.today()).targets(kind, month) }
        }

    /** Copy last month, three-month average or zero (spec 010 US1); one undo step. */
    fun fillMonth(
        budgetId: String,
        month: YearMonth,
        kind: MonthFillKind,
        confirmed: Boolean,
    ): MonthFillResult {
        val label =
            when (kind) {
                MonthFillKind.COPY_LAST -> MonthFillCopy.COPY_LAST
                MonthFillKind.AVERAGE -> MonthFillCopy.AVERAGE
                MonthFillKind.ZERO -> MonthFillCopy.ZERO
            }
        return journaled(budgetId, label) { session ->
            MonthFillBook(session, dates.today()).fill(kind, month, confirmed)
        } ?: MonthFillResult.Rejected(SyncCopy.LAST_FAILED)
    }

    fun transferToBudget(
        budgetId: String,
        categoryId: String,
        month: YearMonth,
        amountText: String,
    ): MonthFillResult {
        val amount =
            MoneyFormat.parse(amountText, decimals(budgetId)) ?: return MonthFillResult.Rejected(SyncCopy.ENTER_AMOUNT)
        return journaled(budgetId, MonthFillCopy.TO_BUDGET) { session ->
            MonthFillBook(session, dates.today()).transferToBudget(month, categoryId, amount)
        } ?: MonthFillResult.Rejected(SyncCopy.LAST_FAILED)
    }

    fun setAutoHold(
        budgetId: String,
        categoryId: String,
        month: YearMonth,
        choice: AutoHoldChoice,
    ): MonthFillResult =
        journaled(budgetId, MonthFillCopy.AUTO_HOLD) { session ->
            MonthFillBook(session, dates.today()).setAutoHold(month, categoryId, choice)
        } ?: MonthFillResult.Rejected(SyncCopy.LAST_FAILED)

    fun addCategoryGroup(
        budgetId: String,
        name: String,
    ): EditResult {
        journaled(budgetId, UndoCopy.CATEGORY) { session ->
            EnvelopeBook(session, syncClock).addGroup(name)
        } ?: return EditResult.Rejected(SyncCopy.LAST_FAILED)
        return EditResult.Saved
    }

    fun addEnvelopeCategory(
        budgetId: String,
        groupId: String,
        name: String,
    ): EditResult {
        journaled(budgetId, UndoCopy.CATEGORY) { session ->
            EnvelopeBook(session, syncClock).addCategory(groupId, name)
        } ?: return EditResult.Rejected(SyncCopy.LAST_FAILED)
        return EditResult.Saved
    }

    fun hideEnvelopeCategory(
        budgetId: String,
        categoryId: String,
        hidden: Boolean,
    ): EditResult {
        journaled(budgetId, UndoCopy.CATEGORY) { session ->
            EnvelopeBook(session, syncClock).hideCategory(categoryId, hidden)
        } ?: return EditResult.Rejected(SyncCopy.LAST_FAILED)
        return EditResult.Saved
    }

    fun renameCategoryGroup(
        budgetId: String,
        groupId: String,
        name: String,
    ): EditResult {
        if (name.trim().isEmpty()) return EditResult.Rejected(EnvelopeCopy.ENTER_NAME)
        journaled(budgetId, UndoCopy.CATEGORY) { session ->
            EnvelopeBook(session, syncClock).renameGroup(groupId, name)
        } ?: return EditResult.Rejected(SyncCopy.LAST_FAILED)
        return EditResult.Saved
    }

    fun renameEnvelopeCategory(
        budgetId: String,
        categoryId: String,
        name: String,
    ): EditResult {
        if (name.trim().isEmpty()) return EditResult.Rejected(EnvelopeCopy.ENTER_NAME)
        journaled(budgetId, UndoCopy.CATEGORY) { session ->
            EnvelopeBook(session, syncClock).renameCategory(categoryId, name)
        } ?: return EditResult.Rejected(SyncCopy.LAST_FAILED)
        return EditResult.Saved
    }

    fun deleteEnvelopeCategory(
        budgetId: String,
        categoryId: String,
    ): EditResult = envelopeEdit(budgetId) { deleteCategory(categoryId) }

    fun deleteCategoryGroup(
        budgetId: String,
        groupId: String,
    ): EditResult = envelopeEdit(budgetId) { deleteGroup(groupId) }

    fun hideCategoryGroup(
        budgetId: String,
        groupId: String,
        hidden: Boolean,
    ): EditResult {
        journaled(budgetId, UndoCopy.CATEGORY) { session ->
            EnvelopeBook(session, syncClock).hideGroup(groupId, hidden)
        } ?: return EditResult.Rejected(SyncCopy.LAST_FAILED)
        return EditResult.Saved
    }

    fun moveCategoryToGroup(
        budgetId: String,
        categoryId: String,
        groupId: String,
    ): EditResult = envelopeEdit(budgetId) { moveCategoryToGroup(categoryId, groupId) }

    /** A drop from reorder: [groupId] goes before [targetId], or last when null. */
    fun placeCategoryGroup(
        budgetId: String,
        groupId: String,
        targetId: String?,
    ): EditResult = envelopeEdit(budgetId) { placeGroup(groupId, targetId) }

    /** A drop from reorder: [categoryId] goes before [targetId] in its group, or last when null. */
    fun placeEnvelopeCategory(
        budgetId: String,
        categoryId: String,
        targetId: String?,
    ): EditResult = envelopeEdit(budgetId) { placeCategory(categoryId, targetId) }

    fun readCategoryManage(budgetId: String): CategoryManagePage? = library.readCategoryManage(budgetId)

    /** Runs an edit as one undo step, with this coordinator's clock for every write in it. */
    private fun <T> journaled(
        budgetId: String,
        label: String,
        block: (SqlSession) -> T,
    ): T? {
        restoreClock(budgetId)
        return library.serial {
            library.useDatabase(budgetId) { session ->
                SyncSchema.withClock(session, syncClock) {
                    library.journal.step(session, budgetId, label) { block(session) }
                }
            }
        }
    }

    /** Provider calls on the active server (spec 010 US6); null when signed out. */
    fun bankClient(): BankLinkClient? {
        val token = syncToken() ?: return null
        val address = activeAddress() ?: return null
        return BankLinkClient(transport, address, token)
    }

    /** Runs a provider call, turning network and auth failures into a reason to show. */
    fun <T> bankCall(block: (BankLinkClient) -> T): Result<T> {
        val client = bankClient() ?: return Result.failure(BankLinkException(BankLinkCopy.SIGN_IN_FIRST))
        return try {
            Result.success(block(client))
        } catch (error: BankLinkException) {
            Result.failure(error)
        } catch (error: SyncAuthException) {
            Result.failure(BankLinkException(SyncCopy.SIGN_IN_AGAIN))
        } catch (error: SyncNetworkException) {
            Result.failure(BankLinkException(BankLinkCopy.UNREACHABLE))
        }
    }

    fun linkBankAccount(
        budgetId: String,
        accountId: String,
        provider: BankProvider,
        external: ExternalAccount,
    ): BankLinkResult =
        journaled(budgetId, UndoCopy.BANK_LINK) { BankLinkBook(it, ids).link(accountId, provider, external) }
            ?: BankLinkResult.Rejected(SyncCopy.LAST_FAILED)

    fun unlinkBankAccount(
        budgetId: String,
        accountId: String,
    ): BankLinkResult =
        journaled(budgetId, UndoCopy.BANK_LINK) { BankLinkBook(it, ids).remove(accountId) }
            ?: BankLinkResult.Rejected(SyncCopy.LAST_FAILED)

    fun bankLinkOf(
        budgetId: String,
        accountId: String,
    ): Pair<BankProvider, String>? = library.useDatabase(budgetId) { BankLinkBook(it, ids).linked(accountId) }

    /** Undoes the newest step; a cell changed since lands in [conflicts]. */
    fun undo(budgetId: String): UndoResult {
        restoreClock(budgetId)
        return library.serial { library.useDatabase(budgetId) { session -> library.journal.undo(session, budgetId, syncClock) } }
            ?: UndoResult.Empty
    }

    private fun envelopeEdit(
        budgetId: String,
        block: EnvelopeBook.() -> EnvelopeEditResult,
    ): EditResult =
        journaled(budgetId, UndoCopy.CATEGORY) { session ->
            when (val outcome = EnvelopeBook(session, syncClock).block()) {
                EnvelopeEditResult.Saved -> EditResult.Saved
                is EnvelopeEditResult.Rejected -> EditResult.Rejected(outcome.reason)
            }
        } ?: EditResult.Rejected(SyncCopy.LAST_FAILED)

    fun addTransaction(
        budgetId: String,
        payee: String,
        amountText: String,
    ): EditResult {
        val amount = MoneyFormat.parse(amountText, decimals(budgetId)) ?: return EditResult.Rejected(SyncCopy.ENTER_AMOUNT)
        val name = payee.trim().ifEmpty { ShellCopy.NO_PAYEE }
        journaled(budgetId, UndoCopy.ADD_TRANSACTION) { session ->
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
            "file-has-reset", "file-needs-upload" -> return SyncCopy.SERVER_RESET
            "file-has-new-key" -> return SyncCopy.SERVER_NEW_KEY
            "file-not-found" -> return SyncCopy.SERVER_MISSING
        }
        val epoch = state(budgetId, "lastSuccessEpoch")?.toLongOrNull() ?: return ""
        return syncStatusLabel(epoch, zone)
    }

    fun conflicts(budgetId: String): List<BudgetConflict> {
        val currency = currency(budgetId)
        return library.useDatabase(budgetId) { session ->
            SyncSchema.ensure(session)
            session
                .query(
                    "SELECT id, row, local_value, remote_value, local_timestamp, remote_timestamp FROM conflicts ORDER BY remote_timestamp",
                ).map { row ->
                    val local = row.str("local_value")?.toLongOrNull() ?: 0L
                    val remoteValue = row.str("remote_value")?.toLongOrNull() ?: 0L
                    BudgetConflict(
                        id = row.str("id").orEmpty(),
                        label = categoryLabel(session, row.str("row").orEmpty()),
                        localText = MoneyFormat.format(local, currency),
                        remoteText = MoneyFormat.format(remoteValue, currency),
                        phoneKept = (row.str("local_timestamp") ?: "") > (row.str("remote_timestamp") ?: ""),
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

    fun setEncryptionPassword(
        budgetId: String,
        password: String,
    ): UnlockResult {
        if (password.isEmpty()) return UnlockResult.Failed(SyncCopy.PASSWORD_WRONG)
        if (state(budgetId, "encrypted") == "1") return UnlockResult.Failed(SyncCopy.PASSWORD_WRONG)
        val salt = BudgetCrypto.newSalt()
        val key = BudgetCrypto.derive(password, salt)
        val keyId = ids()
        val test = BudgetCrypto.testBlob(password, salt)
        memoryKeys[budgetId] = key
        library.useDatabase(budgetId) { session ->
            SyncSchema.putState(session, "keySalt", BudgetCrypto.saltText(salt))
            SyncSchema.putState(session, "keyTest", test)
            SyncSchema.putState(session, "keyId", keyId)
            SyncSchema.putState(session, "encrypted", "1")
        } ?: return UnlockResult.Failed(SyncCopy.LAST_FAILED)
        if (!asksEachTime(budgetId)) {
            secrets.put(keySecret(budgetId), BudgetCrypto.export(key))
        }
        return UnlockResult.Unlocked
    }

    fun changeEncryptionPassword(
        budgetId: String,
        currentPassword: String,
        newPassword: String,
    ): UnlockResult {
        if (newPassword.isEmpty()) return UnlockResult.Failed(SyncCopy.PASSWORD_WRONG)
        val material = localMaterial(budgetId) ?: return UnlockResult.Failed(SyncCopy.PASSWORD_WRONG)
        BudgetCrypto.verify(currentPassword, material.salt, material.test)
            ?: return UnlockResult.Failed(SyncCopy.PASSWORD_WRONG)
        val salt = BudgetCrypto.newSalt()
        val key = BudgetCrypto.derive(newPassword, salt)
        val test = BudgetCrypto.testBlob(newPassword, salt)
        memoryKeys[budgetId] = key
        library.useDatabase(budgetId) { session ->
            SyncSchema.putState(session, "keySalt", BudgetCrypto.saltText(salt))
            SyncSchema.putState(session, "keyTest", test)
            SyncSchema.putState(session, "encrypted", "1")
        } ?: return UnlockResult.Failed(SyncCopy.LAST_FAILED)
        if (!asksEachTime(budgetId)) {
            secrets.put(keySecret(budgetId), BudgetCrypto.export(key))
        } else {
            secrets.remove(keySecret(budgetId))
        }
        return UnlockResult.Unlocked
    }

    fun removeEncryptionPassword(
        budgetId: String,
        password: String,
    ): UnlockResult {
        val material = localMaterial(budgetId) ?: return UnlockResult.Failed(SyncCopy.PASSWORD_WRONG)
        BudgetCrypto.verify(password, material.salt, material.test)
            ?: return UnlockResult.Failed(SyncCopy.PASSWORD_WRONG)
        memoryKeys.remove(budgetId)
        secrets.remove(keySecret(budgetId))
        library.useDatabase(budgetId) { session ->
            SyncSchema.putState(session, "encrypted", "0")
            SyncSchema.putState(session, "keySalt", "")
            SyncSchema.putState(session, "keyTest", "")
            SyncSchema.putState(session, "keyId", "")
        } ?: return UnlockResult.Failed(SyncCopy.LAST_FAILED)
        return UnlockResult.Unlocked
    }

    fun resetSync(budgetId: String): Boolean = library.resetSync(budgetId)

    private fun upload(
        budgetId: String,
        fileId: String?,
        groupId: String?,
        name: String,
    ): UploadResult {
        val token = syncToken() ?: return UploadResult.Failed(SyncCopy.SIGN_IN_AGAIN)
        val address = activeAddress() ?: return UploadResult.Failed(SyncCopy.ADDRESS_FAILED)
        val database = java.io.File(java.io.File(library.root, budgetId), "db.sqlite")
        if (!database.isFile) return UploadResult.Failed(SyncCopy.LAST_FAILED)
        var chosenGroup = groupId
        val chosen =
            fileId ?: run {
                val listed = runCatching { transport.listFiles(address, token) }.getOrNull()
                // An earlier try may have reached the server even though its answer never came back;
                // finish that one rather than leave a second copy of the budget on the server.
                val earlier = state(budgetId, "uploadingFileId")?.takeIf { it.isNotBlank() }
                val landed = earlier?.let { id -> listed?.firstOrNull { it.fileId == id } }
                if (landed != null) {
                    chosenGroup = landed.groupId.ifBlank { null }
                    return@run landed.fileId
                }
                if (earlier != null && listed == null) return@run earlier
                val taken = listed.orEmpty().map { it.fileId }.toSet()
                var created = ids()
                while (created in taken) created = ids()
                library.useDatabase(budgetId) { SyncSchema.putState(it, "uploadingFileId", created) }
                created
            }
        return try {
            val group =
                transport.upload(
                    address = address,
                    token = token,
                    fileId = chosen,
                    groupId = chosenGroup,
                    name = name,
                    bytes = ActualFileFormat.pack(database.readBytes(), budgetId, name, chosen, chosenGroup),
                )
            library.useDatabase(budgetId) { session ->
                SyncSchema.putState(session, "cloudFileId", chosen)
                if (group.isNotBlank()) SyncSchema.putState(session, "groupId", group)
                SyncSchema.putState(session, "uploadingFileId", "")
                SyncSchema.markBackfilled(session)
            }
            // The upload is done; a list that fails now only leaves the server list as it was.
            runCatching { transport.listFiles(address, token) }.onSuccess { remoteFiles = it }
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
        if (kind == "auth") clearActiveToken()
        return SyncResult(status(budgetId).ifBlank { SyncCopy.LAST_FAILED })
    }

    /** Pending messages, or with [everything] every message after [since], as the server wants them. */
    private fun outgoing(
        budgetId: String,
        since: String,
        everything: Boolean,
    ): List<Pending> =
        library.useDatabase(budgetId) { session ->
            SyncSchema.ensure(session)
            val rows =
                if (everything) {
                    session.query(
                        "SELECT timestamp, dataset, row, column, value FROM messages WHERE timestamp > ? ORDER BY timestamp",
                        listOf(since),
                    )
                } else {
                    session.query(
                        "SELECT timestamp, dataset, row, column, value FROM messages WHERE pending = 1 ORDER BY timestamp",
                    )
                }
            rows.map { row ->
                Pending(
                    timestamp = row.str("timestamp").orEmpty(),
                    cell =
                        CellMessage(
                            dataset = row.str("dataset").orEmpty(),
                            row = row.str("row").orEmpty(),
                            column = row.str("column").orEmpty(),
                            value = SyncValues.wire(row.str("column").orEmpty(), row.str("value")),
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
        val token = syncToken() ?: throw SyncAuthException(SyncCopy.SIGN_IN_AGAIN)
        val address = activeAddress() ?: return null
        return transport.encryptionKey(address, token, file.fileId)
    }

    private fun activeAddress(): String? = library.settings().activeAddress()

    private fun upsertConnectedInstance(
        address: String,
        label: String? = null,
    ): SavedInstance {
        val settings = library.settings()
        val existing = settings.instances.firstOrNull { it.address == address }
        val named = label?.trim()?.takeIf { it.isNotEmpty() }
        val instance =
            existing?.let { if (named == null) it else it.copy(label = named) }
                ?: SavedInstance(
                    id = ids(),
                    address = address,
                    label = named ?: runCatching { java.net.URL(address).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: address,
                )
        library.upsertInstance(instance)
        return instance
    }

    private fun tokenFor(instanceId: String): String? = secrets.get(tokenKey(instanceId))

    private fun clearActiveToken() {
        val activeId = library.settings().activeInstanceId
        if (activeId != null) secrets.remove(tokenKey(activeId))
        secrets.remove(LEGACY_TOKEN)
    }

    private fun migrateLegacyToken(activeId: String?): String? {
        val legacy = secrets.get(LEGACY_TOKEN) ?: return null
        if (activeId != null) {
            secrets.put(tokenKey(activeId), legacy)
            secrets.remove(LEGACY_TOKEN)
        }
        return legacy
    }

    private fun tokenKey(instanceId: String): String = "$TOKEN_PREFIX$instanceId"

    private fun localMaterial(fileId: String): KeyMaterial? {
        val salt = state(fileId, "keySalt") ?: return null
        val test = state(fileId, "keyTest") ?: return null
        val id = state(fileId, "keyId").orEmpty()
        if (salt.isBlank() || test.isBlank()) return null
        return KeyMaterial(id, salt, test)
    }

    private fun restoreClock(budgetId: String) {
        syncClock.restoreIfBehind(state(budgetId, "clock"))
        syncClock.restoreIfBehind(state(budgetId, "lastRemote"))
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
        private const val LEGACY_TOKEN = "actual.token"
        private const val MAX_SYNC_ROUNDS = 5
        private const val NO_GAP = -1L
        private const val TOKEN_PREFIX = "actual.token."

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
