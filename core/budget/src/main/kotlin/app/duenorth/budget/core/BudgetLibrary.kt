package app.duenorth.budget.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.YearMonth
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BudgetLibrary(
    val root: File,
    private val sessions: SessionOpener,
    private val clock: BudgetClock,
    private val ids: () -> String = { UUID.randomUUID().toString() },
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    /** Undo steps for the open budget (spec 010). */
    val journal = EditJournal()

    private val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }

    fun settings(): PhoneSettings {
        val file = phoneFile()
        if (!file.exists()) return PhoneSettings()
        var settings = json.decodeFromString(PhoneSettings.serializer(), file.readText())
        val legacy = settings.serverAddress?.trim()?.trimEnd('/')
        if (!legacy.isNullOrBlank() && settings.instances.isEmpty()) {
            val id = ids()
            val label = instanceHostLabel(legacy)
            settings =
                settings.copy(
                    instances = listOf(SavedInstance(id = id, address = legacy, label = label)),
                    activeInstanceId = id,
                )
            save(settings)
            settings = settings.copy(serverAddress = null)
        }
        return settings
    }

    fun save(settings: PhoneSettings) {
        root.mkdirs()
        val cleaned =
            if (settings.instances.isNotEmpty()) {
                settings.copy(serverAddress = null)
            } else {
                settings
            }
        writeAtomically(phoneFile(), json.encodeToString(cleaned))
    }

    fun updateSettings(block: (PhoneSettings) -> PhoneSettings) {
        save(block(settings()))
    }

    fun upsertInstance(instance: SavedInstance) {
        updateSettings { settings ->
            val without = settings.instances.filterNot { it.id == instance.id }
            settings.copy(instances = without + instance)
        }
    }

    fun removeInstance(instanceId: String) {
        updateSettings { settings ->
            val remaining = settings.instances.filterNot { it.id == instanceId }
            val active =
                if (settings.activeInstanceId == instanceId) {
                    null
                } else {
                    settings.activeInstanceId
                }
            settings.copy(instances = remaining, activeInstanceId = active)
        }
    }

    fun setActiveInstanceId(instanceId: String?) {
        updateSettings { settings ->
            if (instanceId != null && settings.instances.none { it.id == instanceId }) {
                settings
            } else {
                settings.copy(activeInstanceId = instanceId)
            }
        }
    }

    fun preference(
        budgetId: String,
        id: String,
    ): String? =
        open(budgetId) { session ->
            session.query("SELECT value FROM preferences WHERE id = ?", listOf(id)).firstOrNull()?.str("value")
        }

    fun setPreference(
        budgetId: String,
        id: String,
        value: String,
    ): Boolean {
        if (id == "budgetType") return false
        return open(budgetId) { session ->
            SyncSchema.ensure(session)
            session.exec("INSERT OR REPLACE INTO preferences (id, value) VALUES (?, ?)", listOf(id, value))
            val cloud = SyncSchema.state(session, "cloudFileId")
            if (!cloud.isNullOrBlank()) {
                val syncClock = syncClockFor(session)
                SyncSchema.recordLocal(session, syncClock, "preferences", id, "value", value)
            }
            true
        } ?: false
    }

    fun readFormatPrefs(budgetId: String): FormatPrefs =
        open(budgetId) { session ->
            fun value(id: String): String? =
                session.query("SELECT value FROM preferences WHERE id = ?", listOf(id)).firstOrNull()?.str("value")
            FormatPrefs(
                dateFormat = value("dateFormat") ?: "MM/dd/yyyy",
                numberFormat = value("numberFormat") ?: FormatOptions.NUMBER_COMMA_DOT,
                hideFraction = value("hideFraction") == "true",
                firstDayOfWeekIdx = value("firstDayOfWeekIdx")?.toIntOrNull()?.coerceIn(0, 6) ?: 0,
                experimental = value("flags.experimental") == "true",
                budgetType = value("budgetType") ?: "envelope",
                learnCategories = value("learn-categories") != "false",
            )
        } ?: FormatPrefs()

    fun exportZip(budgetId: String): ByteArray? {
        val dir = File(root, budgetId)
        val metadata = File(dir, "metadata.json")
        val database = File(dir, "db.sqlite")
        if (!metadata.isFile || !database.isFile) return null
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("metadata.json"))
            zip.write(metadata.readBytes())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("db.sqlite"))
            zip.write(database.readBytes())
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    fun exportFileName(budgetName: String): String {
        val sanitized =
            budgetName
                .trim()
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .ifEmpty { "budget" }
        return "$sanitized.zip"
    }

    fun rebuildTotals(budgetId: String): Boolean =
        open(budgetId) { session ->
            session
                .query(
                    """
                    SELECT name FROM sqlite_master
                    WHERE type = 'table' AND (name LIKE '%kvcache%' OR name LIKE '%cache%')
                    """.trimIndent(),
                ).forEach { row ->
                    val name = row.str("name").orEmpty()
                    if (name.isNotBlank() && name.all { it.isLetterOrDigit() || it == '_' }) {
                        session.exec("DROP TABLE IF EXISTS \"$name\"")
                    }
                }
            true
        } ?: false

    fun repairSplits(budgetId: String): Boolean =
        open(budgetId) { session ->
            // Empty parents (marked parent, no live children): tombstone them.
            session
                .query(
                    """
                    SELECT p.id AS id FROM transactions p
                    WHERE IFNULL(p.isParent, 0) = 1 AND IFNULL(p.tombstone, 0) = 0
                      AND NOT EXISTS (
                        SELECT 1 FROM transactions c
                        WHERE c.parent_id = p.id AND IFNULL(c.tombstone, 0) = 0
                      )
                    """.trimIndent(),
                ).forEach { row ->
                    SyncSchema.write(session, "transactions", row.str("id").orEmpty(), "tombstone", 1)
                }
            // Orphan children: parent missing or tombstoned → tombstone the child.
            session
                .query(
                    """
                    SELECT c.id AS id FROM transactions c
                    WHERE IFNULL(c.isChild, 0) = 1 AND IFNULL(c.tombstone, 0) = 0
                      AND (
                        c.parent_id IS NULL OR c.parent_id = ''
                        OR NOT EXISTS (
                          SELECT 1 FROM transactions p
                          WHERE p.id = c.parent_id AND IFNULL(p.tombstone, 0) = 0
                        )
                      )
                    """.trimIndent(),
                ).forEach { row ->
                    SyncSchema.write(session, "transactions", row.str("id").orEmpty(), "tombstone", 1)
                }
            // Children pointing at a live non-parent: re-link by marking parent.
            session
                .query(
                    """
                    SELECT DISTINCT c.parent_id AS parent_id FROM transactions c
                    JOIN transactions p ON p.id = c.parent_id
                    WHERE IFNULL(c.isChild, 0) = 1 AND IFNULL(c.tombstone, 0) = 0
                      AND IFNULL(p.tombstone, 0) = 0 AND IFNULL(p.isParent, 0) = 0
                    """.trimIndent(),
                ).forEach { row ->
                    val parentId = row.str("parent_id") ?: return@forEach
                    SyncSchema.writeRow(
                        session,
                        "transactions",
                        parentId,
                        "isParent" to 1,
                        "isChild" to 0,
                        "category" to null,
                    )
                }
            true
        } ?: false

    fun resetSync(budgetId: String): Boolean =
        open(budgetId) { session ->
            SyncSchema.ensure(session)
            val cloud = SyncSchema.state(session, "cloudFileId")
            val group = SyncSchema.state(session, "groupId")
            session.exec("DELETE FROM messages")
            session.exec("DELETE FROM conflicts")
            listOf("clock", "lastRemote", "lastSuccessEpoch", "lastFailure", "merkle").forEach { key ->
                session.exec("DELETE FROM sync_state WHERE id = ?", listOf(key))
            }
            if (!cloud.isNullOrBlank()) SyncSchema.putState(session, "cloudFileId", cloud)
            if (!group.isNullOrBlank()) SyncSchema.putState(session, "groupId", group)
            true
        } ?: false

    fun <T> useDatabase(
        id: String,
        block: (SqlSession) -> T,
    ): T? {
        val database = File(File(root, id), "db.sqlite")
        if (!database.isFile) return null
        return sessions.use(database, block)
    }

    /**
     * Replaces one budget directory after [bytes] open as sqlite.
     * A failed check leaves every existing directory alone.
     */
    fun installDownloaded(
        id: String,
        name: String,
        bytes: ByteArray,
    ) {
        require(id.isNotBlank() && !id.contains('/') && !id.contains('\\') && id != "." && id != "..")
        val staging = File(root, ".$id.staging")
        if (staging.exists()) staging.deleteRecursively()
        staging.mkdirs()
        val incoming = File(staging, "db.sqlite")
        incoming.writeBytes(bytes)
        try {
            sessions.use(incoming) { session ->
                SyncSchema.ensure(session)
                session.query("SELECT COUNT(*) AS n FROM preferences")
            }
        } catch (error: Exception) {
            staging.deleteRecursively()
            throw error
        }
        val metadata = json.encodeToString(BudgetMetadata(id = id, budgetName = name.trim()))
        writeAtomically(File(staging, "metadata.json"), metadata)
        val dest = File(root, id)
        if (!dest.exists()) {
            Files.move(staging.toPath(), dest.toPath(), StandardCopyOption.ATOMIC_MOVE)
            return
        }
        val destDatabase = File(dest, "db.sqlite")
        val backup = File(dest, "db.sqlite.previous")
        if (destDatabase.exists()) {
            Files.move(destDatabase.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
        try {
            Files.move(incoming.toPath(), destDatabase.toPath(), StandardCopyOption.ATOMIC_MOVE)
            writeAtomically(File(dest, "metadata.json"), metadata)
            if (backup.exists()) backup.delete()
            staging.deleteRecursively()
        } catch (error: Exception) {
            if (!destDatabase.exists() && backup.exists()) {
                Files.move(backup.toPath(), destDatabase.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            }
            staging.deleteRecursively()
            throw error
        }
    }

    fun list(): List<BudgetSummary> {
        val dirs = root.listFiles { file -> file.isDirectory } ?: return emptyList()
        return dirs.mapNotNull { dir -> summary(dir) }.sortedWith(
            compareBy({ it.name.lowercase() }, { it.id }),
        )
    }

    fun create(
        name: String,
        currencyCode: String,
    ): CreateResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return CreateResult.Rejected(ShellCopy.ENTER_NAME)
        val currency = Currencies.byCode(currencyCode) ?: return CreateResult.Rejected(ShellCopy.CHOOSE_CURRENCY)
        val id = ids()
        val dir = File(root, id)
        dir.mkdirs()
        return try {
            writeAtomically(
                File(dir, "metadata.json"),
                json.encodeToString(BudgetMetadata(id = id, budgetName = trimmed)),
            )
            sessions.use(File(dir, "db.sqlite")) { session ->
                ActualSchema.create(session)
                SyncSchema.ensure(session)
                session.exec(
                    "INSERT INTO preferences (id, value) VALUES (?, ?)",
                    listOf("budgetType", "envelope"),
                )
                session.exec(
                    "INSERT INTO preferences (id, value) VALUES (?, ?)",
                    listOf("currency", currency.code),
                )
            }
            save(settings().copy(openBudgetId = id))
            CreateResult.Created(id)
        } catch (error: Exception) {
            dir.deleteRecursively()
            throw error
        }
    }

    /**
     * Creates a local budget filled from a [MockBudgets] dataset (debug tooling).
     * Uses the dataset title as the budget name and USD unless the seed overrides currency.
     */
    fun createFromMock(
        datasetId: String,
        name: String? = null,
    ): CreateResult {
        val dataset = MockBudgets.byId(datasetId) ?: return CreateResult.Rejected(ShellCopy.UNKNOWN_SAMPLE)
        val budgetName = name?.trim()?.ifEmpty { null } ?: dataset.title
        val id = ids()
        val dir = File(root, id)
        dir.mkdirs()
        return try {
            writeAtomically(
                File(dir, "metadata.json"),
                json.encodeToString(BudgetMetadata(id = id, budgetName = budgetName)),
            )
            sessions.use(File(dir, "db.sqlite")) { session ->
                ActualSchema.create(session)
                ActualSchema.ensure(session)
                SyncSchema.ensure(session)
                MockBudgets.seed(session, dataset.id, clock.today())
            }
            save(settings().copy(openBudgetId = id))
            CreateResult.Created(id)
        } catch (error: Exception) {
            dir.deleteRecursively()
            throw error
        }
    }

    fun readShell(
        id: String,
        month: YearMonth? = null,
    ): MonthShell? {
        val dir = File(root, id)
        val metadata = readMetadata(dir) ?: return null
        val database = File(dir, "db.sqlite")
        if (!database.exists()) return null
        val viewMonth = month ?: YearMonth.from(clock.today())
        return sessions.use(database) { session ->
            ActualSchema.ensure(session)
            SyncSchema.ensure(session)
            ShellReader.read(session, metadata, clock.today(), viewMonth, settings().showClosedAccounts)
        }
    }

    /** Envelope to-budget breakdown for [month]; null on tracking budgets. */
    fun readBreakdown(
        budgetId: String,
        month: YearMonth,
    ): ActualMonthMath.MonthBreakdown? = open(budgetId) { ShellReader.loadDetailed(it, month).breakdown }

    fun readCategoryManage(budgetId: String): CategoryManagePage? =
        open(budgetId) { session ->
            ActualSchema.ensure(session)
            ShellReader.loadCategoryManage(session)
        }

    /** Undoes the newest step on [budgetId]; see `contracts/undo.md`. */
    fun undo(budgetId: String): UndoResult =
        serial { open(budgetId) { session -> journal.undo(session, budgetId, SyncSchema.clockFor(session)) } } ?: UndoResult.Empty

    fun canUndo(budgetId: String): Boolean = journal.canUndo(budgetId)

    fun undoLabel(budgetId: String): String? = journal.newestLabel(budgetId)

    /** Accounts for the account screens (spec 010 US2); closed ones only when [includeClosed]. */
    fun readAccounts(
        budgetId: String,
        includeClosed: Boolean,
    ): List<AccountDetail> = open(budgetId) { AccountsBook(it, clock.today(), ids).list(includeClosed) } ?: emptyList()

    fun readAccount(
        budgetId: String,
        accountId: String,
    ): AccountDetail? = open(budgetId) { AccountsBook(it, clock.today(), ids).detail(accountId) }

    fun createAccount(
        budgetId: String,
        name: String,
        offBudget: Boolean,
        balanceMinor: Long,
    ): AccountWriteResult =
        edit(budgetId, AccountsCopy.ADD) { AccountsBook(it, clock.today(), ids).create(name, offBudget, balanceMinor) }
            ?: AccountWriteResult.Rejected(ShellCopy.NO_ACCOUNTS)

    fun renameAccount(
        budgetId: String,
        accountId: String,
        name: String,
    ): AccountWriteResult =
        edit(budgetId, AccountsCopy.EDIT) { AccountsBook(it, clock.today(), ids).rename(accountId, name) }
            ?: AccountWriteResult.Rejected(ShellCopy.NO_ACCOUNTS)

    fun setAccountNote(
        budgetId: String,
        accountId: String,
        note: String,
    ): AccountWriteResult =
        edit(budgetId, AccountsCopy.NOTE) { AccountsBook(it, clock.today(), ids).setNote(accountId, note) }
            ?: AccountWriteResult.Rejected(ShellCopy.NO_ACCOUNTS)

    /** Places [accountId] before [targetId], or last when null. */
    fun moveAccount(
        budgetId: String,
        accountId: String,
        targetId: String?,
    ): AccountWriteResult =
        edit(budgetId, AccountsCopy.REORDER) { AccountsBook(it, clock.today(), ids).move(accountId, targetId) }
            ?: AccountWriteResult.Rejected(ShellCopy.NO_ACCOUNTS)

    fun closeAccount(
        budgetId: String,
        accountId: String,
        destinationId: String?,
        categoryId: String?,
        forced: Boolean = false,
        confirmed: Boolean = false,
    ): AccountWriteResult =
        edit(budgetId, if (forced) AccountsCopy.FORCE_CLOSE else AccountsCopy.CLOSE) {
            AccountsBook(it, clock.today(), ids).close(accountId, destinationId, categoryId, forced, confirmed)
        } ?: AccountWriteResult.Rejected(ShellCopy.NO_ACCOUNTS)

    fun reopenAccount(
        budgetId: String,
        accountId: String,
    ): AccountWriteResult =
        edit(budgetId, AccountsCopy.REOPEN) { AccountsBook(it, clock.today(), ids).reopen(accountId) }
            ?: AccountWriteResult.Rejected(ShellCopy.NO_ACCOUNTS)

    fun readRegister(
        budgetId: String,
        accountId: String,
    ): RegisterPage? = open(budgetId) { RegisterBook(it).read(accountId) }

    fun readCategoryTarget(
        budgetId: String,
        transactionId: String,
    ): CategoryTarget? = open(budgetId) { RegisterBook(it).categoryTarget(transactionId) }

    fun saveTransaction(
        budgetId: String,
        draft: TransactionDraft,
    ): WriteResult =
        edit(budgetId, UndoCopy.SAVE_TRANSACTION) { session ->
            RegisterBook(session).save(draft).also {
                if (it is WriteResult.Saved && draft.categoryId != null) CategoryLearning(session, ids).learnFrom(draft.id)
            }
        } ?: WriteResult.Rejected(ShellCopy.NO_ACCOUNTS)

    fun setTransactionCategory(
        budgetId: String,
        transactionId: String,
        categoryId: String?,
        force: Boolean,
    ): WriteResult =
        edit(budgetId, UndoCopy.CATEGORIZE) { session ->
            RegisterBook(session).setCategory(transactionId, categoryId, force).also {
                if (it is WriteResult.Saved) CategoryLearning(session, ids).learnFrom(transactionId)
            }
        } ?: WriteResult.Rejected(ShellCopy.NO_ACCOUNTS)

    fun deleteTransaction(
        budgetId: String,
        transactionId: String,
        force: Boolean,
    ): WriteResult =
        edit(budgetId, UndoCopy.DELETE_TRANSACTION) { RegisterBook(it).delete(transactionId, force) }
            ?: WriteResult.Rejected(ShellCopy.NO_ACCOUNTS)

    fun splitTransaction(
        budgetId: String,
        transactionId: String,
        parts: List<SplitPart>,
        force: Boolean,
    ): WriteResult =
        edit(budgetId, UndoCopy.SPLIT) { RegisterBook(it).split(transactionId, parts, force) }
            ?: WriteResult.Rejected(ShellCopy.NO_ACCOUNTS)

    fun unsplitTransaction(
        budgetId: String,
        transactionId: String,
        force: Boolean,
    ): WriteResult =
        edit(budgetId, UndoCopy.UNSPLIT) { RegisterBook(it).unsplit(transactionId, force) }
            ?: WriteResult.Rejected(ShellCopy.NO_ACCOUNTS)

    fun transfer(
        budgetId: String,
        draft: TransferDraft,
    ): WriteResult = edit(budgetId, UndoCopy.TRANSFER) { RegisterBook(it).transfer(draft) } ?: WriteResult.Rejected(ShellCopy.NO_ACCOUNTS)

    fun readMonthReview(
        budgetId: String,
        month: YearMonth,
    ): MonthReviewPage? = open(budgetId) { ReviewReader.monthReview(it, month) }

    fun readCategoryMonth(
        budgetId: String,
        categoryId: String,
        month: YearMonth,
    ): CategoryMonthPage? = open(budgetId) { ReviewReader.categoryMonth(it, categoryId, month) }

    fun readNetWorth(
        budgetId: String,
        includeOffBudget: Boolean,
    ): NetWorthPage? = open(budgetId) { ReviewReader.netWorth(it, includeOffBudget) }

    fun readReconcile(
        budgetId: String,
        accountId: String,
    ): ReconcilePage? = open(budgetId) { ReconcileBook(it).read(accountId) }

    fun startReconcile(
        budgetId: String,
        accountId: String,
        balanceText: String,
        dateText: String,
    ): ReconcileStartResult =
        open(budgetId) { ReconcileBook(it).start(accountId, balanceText, dateText) }
            ?: ReconcileStartResult.Rejected(ShellCopy.NO_ACCOUNTS)

    fun toggleReconcileCleared(
        budgetId: String,
        accountId: String,
        transactionId: String,
    ): ReconcilePage? = open(budgetId) { ReconcileBook(it).toggleCleared(accountId, transactionId) }

    fun finishReconcile(
        budgetId: String,
        accountId: String,
    ): ReconcileFinishResult =
        edit(budgetId, UndoCopy.RECONCILE) { ReconcileBook(it).finish(accountId) }
            ?: ReconcileFinishResult.Rejected(ShellCopy.NO_ACCOUNTS)

    fun cancelReconcile(
        budgetId: String,
        accountId: String,
    ): Boolean = open(budgetId) { ReconcileBook(it).cancel(accountId) } ?: false

    fun readUpcomingSchedules(budgetId: String): List<UpcomingScheduleRow> =
        open(budgetId) { SchedulesBook(it, clock).listUpcoming() } ?: emptyList()

    fun postSchedule(
        budgetId: String,
        scheduleId: String,
        forceDuplicate: Boolean = false,
    ): ScheduleWriteResult =
        edit(budgetId, UndoCopy.POST_SCHEDULE) { SchedulesBook(it, clock).post(scheduleId, forceDuplicate) }
            ?: ScheduleWriteResult.Rejected(ShellCopy.NO_ACCOUNTS)

    fun skipSchedule(
        budgetId: String,
        scheduleId: String,
    ): ScheduleWriteResult =
        edit(budgetId, UndoCopy.SKIP_SCHEDULE) { SchedulesBook(it, clock).skip(scheduleId) }
            ?: ScheduleWriteResult.Rejected(ShellCopy.NO_ACCOUNTS)

    fun createScheduleFromTransaction(
        budgetId: String,
        transactionId: String,
        frequency: String,
        nextDateIso: String,
        occurrences: Int? = null,
    ): ScheduleWriteResult =
        edit(budgetId, UndoCopy.SAVE_SCHEDULE) {
            SchedulesBook(it, clock).createFromTransaction(transactionId, frequency, nextDateIso, occurrences)
        }
            ?: ScheduleWriteResult.Rejected(ShellCopy.NO_ACCOUNTS)

    fun deleteSchedule(
        budgetId: String,
        scheduleId: String,
    ): ScheduleWriteResult =
        edit(budgetId, UndoCopy.DELETE_SCHEDULE) { SchedulesBook(it, clock).delete(scheduleId) }
            ?: ScheduleWriteResult.Rejected(ShellCopy.NO_ACCOUNTS)

    /** Schedule editor (spec 010 US3). */
    fun readScheduleDraft(
        budgetId: String,
        scheduleId: String,
    ): ScheduleDraft? = open(budgetId) { SchedulesBook(it, clock, ids).draft(scheduleId) }

    fun saveSchedule(
        budgetId: String,
        draft: ScheduleDraft,
    ): ScheduleWriteResult =
        edit(budgetId, UndoCopy.SAVE_SCHEDULE) { SchedulesBook(it, clock, ids).save(draft) }
            ?: ScheduleWriteResult.Rejected(ShellCopy.NO_ACCOUNTS)

    /** Posts every due auto-post occurrence, one undo step each; returns how many steps ran. */
    fun autoPostSchedules(budgetId: String): Int {
        if (open(budgetId) { SchedulesBook(it, clock, ids).hasAutoPostDue() } != true) return 0
        var steps = 0
        while (steps < MAX_AUTO_POST) {
            val ran = edit(budgetId, UndoCopy.AUTO_POST) { SchedulesBook(it, clock, ids).autoPostOne() } ?: false
            if (!ran) break
            steps++
        }
        return steps
    }

    /** Cash flow, net worth, and spending for [range] (spec 010 US4). */
    fun readReports(
        budgetId: String,
        range: ReportRange,
        includeOffBudget: Boolean,
        groupBy: SpendingGroupBy,
    ): ReportsPage? =
        open(budgetId) { session ->
            val code =
                session
                    .query("SELECT value FROM preferences WHERE id = ?", listOf("currency"))
                    .firstOrNull()
                    ?.str("value")
                    .orEmpty()
            val currency = Currencies.byCode(code) ?: Currencies.byCode("USD")!!
            ReportsReader(session, clock.today()).page(range, includeOffBudget, groupBy, currency)
        }

    fun listRules(budgetId: String): List<RuleSummary> = open(budgetId) { RuleEditorBook(it, ids).list() } ?: emptyList()

    fun readRuleDraft(
        budgetId: String,
        ruleId: String,
    ): RuleDraft? = open(budgetId) { RuleEditorBook(it, ids).draft(ruleId) }

    fun saveRule(
        budgetId: String,
        draft: RuleDraft,
    ): RuleSaveResult =
        edit(budgetId, RuleEditorCopy.SAVE_RULE) { RuleEditorBook(it, ids).save(draft) }
            ?: RuleSaveResult.Rejected(ShellCopy.NO_ACCOUNTS)

    fun deleteRule(
        budgetId: String,
        ruleId: String,
    ): RuleSaveResult =
        edit(budgetId, RuleEditorCopy.DELETE_RULE) { RuleEditorBook(it, ids).delete(ruleId) }
            ?: RuleSaveResult.Rejected(ShellCopy.NO_ACCOUNTS)

    /** Actual's `learn-categories` preference; absent means on. */
    fun learningEnabled(budgetId: String): Boolean = preference(budgetId, "learn-categories") != "false"

    fun setLearning(
        budgetId: String,
        enabled: Boolean,
    ): Boolean = setPreference(budgetId, "learn-categories", enabled.toString())

    fun listPayees(budgetId: String): List<PayeeRow> = open(budgetId) { PayeeBook(it).list() } ?: emptyList()

    fun renamePayee(
        budgetId: String,
        payeeId: String,
        newName: String,
        confirmMerge: Boolean = false,
        rememberRule: Boolean = false,
    ): PayeeWriteResult =
        edit(budgetId, UndoCopy.RENAME_PAYEE) { PayeeBook(it).rename(payeeId, newName, confirmMerge, rememberRule) }
            ?: PayeeWriteResult.Rejected(ShellCopy.NO_ACCOUNTS)

    fun setRuleEnabled(
        budgetId: String,
        ruleId: String,
        enabled: Boolean,
    ): Boolean =
        open(budgetId) {
            RulesBook(it).setEnabled(ruleId, enabled)
            true
        } ?: false

    fun previewImportFile(
        budgetId: String,
        accountId: String,
        bytes: ByteArray,
        fileName: String,
    ): Pair<ImportPreviewBundle?, String?> =
        open(budgetId) { ImportBook(it, ids).previewFile(accountId, bytes, fileName) }
            ?: (null to ShellCopy.NO_ACCOUNTS)

    fun confirmImport(
        budgetId: String,
        accountId: String,
        candidates: List<ParsedImportRow>,
    ): ImportConfirmResult? = edit(budgetId, UndoCopy.IMPORT) { ImportBook(it, ids).confirm(accountId, candidates) }

    fun readImportReview(budgetId: String): ImportReviewPage? = open(budgetId) { ImportBook(it, ids).readReview() }

    fun finishImportReview(budgetId: String) {
        open(budgetId) { ImportBook(it, ids).finishReview() }
    }

    fun setImportReviewCategory(
        budgetId: String,
        transactionId: String,
        categoryId: String?,
    ): WriteResult =
        edit(budgetId, UndoCopy.CATEGORIZE) { ImportBook(it, ids).setReviewCategory(transactionId, categoryId) }
            ?: WriteResult.Rejected(ShellCopy.NO_ACCOUNTS)

    fun bankLinked(
        budgetId: String,
        accountId: String,
    ): Boolean = open(budgetId) { BankSyncBook(it, FakeBankSyncTransport(emptyList())).linkedAccount(accountId) != null } ?: false

    fun previewBankFetch(
        budgetId: String,
        accountId: String,
        serverAddress: String?,
        token: String?,
        transport: BankSyncTransport,
        today: java.time.LocalDate,
    ): Pair<ImportPreviewBundle?, String?> =
        open(budgetId) { BankSyncBook(it, transport).previewFetch(accountId, serverAddress, token, today) }
            ?: (null to ShellCopy.NO_ACCOUNTS)

    fun switchTo(id: String): Boolean {
        val target = summary(File(root, id)) ?: return false
        val settings = settings()
        journal.switchTo(target.id)
        if (settings.openBudgetId == target.id) return true
        save(settings.copy(openBudgetId = target.id))
        return true
    }

    private val editLock = Any()

    private companion object {
        const val MAX_AUTO_POST = 500
    }

    private fun <T> open(
        id: String,
        block: (SqlSession) -> T,
    ): T? {
        val database = File(root, id).resolve("db.sqlite")
        if (!database.isFile) return null
        return sessions.use(database) { session ->
            ActualSchema.ensure(session)
            SyncSchema.withClock(session, syncClockFor(session)) { block(session) }
        }
    }

    /** [open] as one undo step labelled [label]. */
    private fun <T> edit(
        id: String,
        label: String,
        block: (SqlSession) -> T,
    ): T? =
        // One step at a time: a capture belongs to the session, and auto-post runs from sync on
        // another thread while the person edits.
        serial { open(id) { session -> journal.step(session, id, label) { block(session) } } }

    /** Journaled writes and undo run one at a time. */
    internal fun <T> serial(block: () -> T): T = synchronized(editLock) { block() }

    private fun summary(dir: File): BudgetSummary? {
        val metadata = readMetadata(dir) ?: return null
        val database = File(dir, "db.sqlite")
        val currency =
            if (!database.exists()) {
                ""
            } else {
                sessions.use(database) { session ->
                    session
                        .query(
                            "SELECT value FROM preferences WHERE id = ?",
                            listOf("currency"),
                        ).firstOrNull()
                        ?.str("value")
                        .orEmpty()
                }
            }
        return BudgetSummary(id = metadata.id, name = metadata.budgetName, currencyCode = currency)
    }

    private fun readMetadata(dir: File): BudgetMetadata? {
        val file = File(dir, "metadata.json")
        if (!file.isFile) return null
        return json.decodeFromString(BudgetMetadata.serializer(), file.readText())
    }

    private fun phoneFile(): File = File(root, "phone.json")

    private fun instanceHostLabel(address: String): String =
        runCatching { java.net.URL(address).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: address

    private fun syncClockFor(session: SqlSession): SyncClock {
        val settings = settings()
        val node = settings.syncNode?.takeIf { it.length == 16 } ?: "0000000000000000"
        val clock = SyncClock(node, now)
        SyncSchema.restore(session, clock)
        return clock
    }

    private fun writeAtomically(
        file: File,
        text: String,
    ) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(text)
        Files.move(
            temporary.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }
}
