package app.duenorth.budget.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
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

    /**
     * Told a budget's id after a call leaves that budget with changes the server has not stored,
     * so the app can send them right away. It runs on the writing thread and must return quickly.
     */
    @Volatile
    var onLocalChange: ((String) -> Unit)? = null

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

    /** First-run progress beside `phone.json` (spec 011). */
    fun onboarding(): OnboardingStore = OnboardingStore(File(root, "onboarding.json"))

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
                experimentalAsked = value("flags.experimental") != null,
                budgetType = value("budgetType") ?: "envelope",
                learnCategories = value("learn-categories") != "false",
                goalTemplates = value("flags.goalTemplatesEnabled") == "true",
                goalTemplatesUI = value("flags.goalTemplatesUIEnabled") == "true",
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
        return watched(id) { sessions.use(database, block) }
    }

    /** Runs [block], then tells [onLocalChange] when it recorded a local change for [id]. */
    private fun <T> watched(
        id: String,
        block: () -> T,
    ): T {
        val before = SyncSchema.localWritesOnThread()
        val result = block()
        if (SyncSchema.localWritesOnThread() != before) onLocalChange?.invoke(id)
        return result
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
                seedIncome(session)
            }
            save(settings().copy(openBudgetId = id))
            CreateResult.Created(id)
        } catch (error: Exception) {
            dir.deleteRecursively()
            throw error
        }
    }

    /**
     * The income group a new Actual file starts with, so an account's starting balance lands in
     * Starting Balances and counts toward to budget instead of staying uncategorized.
     */
    private fun seedIncome(session: SqlSession) {
        val group = ids()
        SyncSchema.writeRow(
            session,
            "category_groups",
            group,
            "name" to NewBudgetCopy.INCOME,
            "is_income" to true,
            "hidden" to false,
            "tombstone" to false,
            "sort_order" to 1.0,
        )
        listOf(NewBudgetCopy.INCOME, NewBudgetCopy.STARTING_BALANCES).forEachIndexed { index, name ->
            SyncSchema.writeRow(
                session,
                "categories",
                ids(),
                "name" to name,
                "cat_group" to group,
                "is_income" to true,
                "hidden" to false,
                "tombstone" to false,
                "sort_order" to (index + 1).toDouble(),
            )
        }
    }

    /**
     * Creates a local budget named [budgetName] and fills it with [seed], given the new file's session and today
     * (the sample budgets debug builds offer, see [SampleBudgets]).
     */
    fun createSeeded(
        budgetName: String,
        seed: (SqlSession, LocalDate) -> Unit,
    ): CreateResult {
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
                seed(session, clock.today())
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
        val templatesOn = readFormatPrefs(id).templatesOn
        return sessions.use(database) { session ->
            ActualSchema.ensure(session)
            SyncSchema.ensure(session)
            val shell =
                settings().let { settings ->
                    ShellReader.read(
                        session,
                        metadata,
                        clock.today(),
                        viewMonth,
                        settings.showClosedAccounts,
                        settings.showHiddenCategories,
                    )
                }
            // A goal is only stored once templates are applied that month; until then the rows measure what the
            // templates ask, so their bars are not left empty. The bars are a hint: a template that cannot be worked
            // out leaves the stored goals in place rather than taking the budget down with it.
            if (!templatesOn) return@use shell
            val goals = runCatching { TemplateEngine(session, clock.today()).goals(viewMonth) }.getOrElse { return@use shell }
            shell.copy(
                groups =
                    shell.groups.map { group ->
                        group.copy(
                            categories =
                                group.categories.map { row ->
                                    goals[row.id]?.let { (goal, long) -> row.copy(goalMinor = goal, longGoal = long) } ?: row
                                },
                        )
                    },
            )
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

    /** Files several transactions at once as one undo step, learning each payee's category. */
    fun setTransactionsCategory(
        budgetId: String,
        assignments: Map<String, String?>,
        force: Boolean,
    ): WriteResult =
        edit(budgetId, UndoCopy.CATEGORIZE) { session ->
            RegisterBook(session).setCategories(assignments, force).also {
                if (it is WriteResult.Saved) {
                    val learning = CategoryLearning(session, ids)
                    assignments.keys.forEach(learning::learnFrom)
                }
            }
        } ?: WriteResult.Rejected(ShellCopy.NO_ACCOUNTS)

    /** Matches a bank transaction with one entered by hand: one transaction stays, as one undo step. */
    fun matchTransactions(
        budgetId: String,
        bankId: String,
        enteredId: String,
    ): WriteResult =
        edit(budgetId, UndoCopy.MATCH) { session ->
            MatchBook(session, clock.today()).match(bankId, enteredId).also {
                if (it is WriteResult.Saved) CategoryLearning(session, ids).learnFrom(bankId)
            }
        } ?: WriteResult.Rejected(ShellCopy.NO_ACCOUNTS)

    /** Merges two or more transactions of one account into one, as desktop Actual's merge does, as one undo step. */
    fun mergeTransactions(
        budgetId: String,
        transactionIds: List<String>,
        force: Boolean,
    ): WriteResult =
        edit(budgetId, UndoCopy.MERGE) { MergeBook(it).merge(transactionIds, force) }
            ?: WriteResult.Rejected(ShellCopy.NO_ACCOUNTS)

    /** What the assistant found, read from the phone's copy; null when the budget is not on the phone. */
    fun readAssistant(budgetId: String): AssistantReport? = open(budgetId) { AssistantReader.read(it, clock.today()) }

    /** The assistant's move and merge: an entry goes to the bank copy's account and the two become one, as one undo step. */
    fun moveAndMatch(
        budgetId: String,
        enteredId: String,
        bankId: String,
    ): WriteResult =
        edit(budgetId, UndoCopy.MOVE_AND_MERGE) { session ->
            AssistantBook(session, clock.today(), ids).moveAndMatch(enteredId, bankId).also {
                if (it is WriteResult.Saved) CategoryLearning(session, ids).learnFrom(bankId)
            }
        } ?: WriteResult.Rejected(ShellCopy.NO_ACCOUNTS)

    /** The assistant's move: a transaction goes to another account, as one undo step. */
    fun moveTransaction(
        budgetId: String,
        transactionId: String,
        accountId: String,
    ): WriteResult =
        edit(budgetId, UndoCopy.MOVE_TRANSACTION) { AssistantBook(it, clock.today(), ids).moveTo(transactionId, accountId) }
            ?: WriteResult.Rejected(ShellCopy.NO_ACCOUNTS)

    /** The assistant's near-duplicate fix: the entry takes the bank's amount and the two become one. */
    fun adoptAndMatch(
        budgetId: String,
        enteredId: String,
        bankId: String,
    ): WriteResult =
        edit(budgetId, UndoCopy.MATCH) { session ->
            AssistantBook(session, clock.today(), ids).adoptAndMatch(enteredId, bankId).also {
                if (it is WriteResult.Saved) CategoryLearning(session, ids).learnFrom(bankId)
            }
        } ?: WriteResult.Rejected(ShellCopy.NO_ACCOUNTS)

    /** The assistant's transfer fix: two transactions in different accounts become one transfer. */
    fun linkTransfer(
        budgetId: String,
        fromId: String,
        toId: String,
    ): WriteResult =
        edit(budgetId, UndoCopy.LINK_TRANSFER) { AssistantBook(it, clock.today(), ids).linkTransfer(fromId, toId) }
            ?: WriteResult.Rejected(ShellCopy.NO_ACCOUNTS)

    /** The assistant's price change: the schedule's amount becomes what it has cost lately. */
    fun updateScheduleAmount(
        budgetId: String,
        scheduleId: String,
        amountMinor: Long,
    ): ScheduleWriteResult =
        edit(budgetId, UndoCopy.SAVE_SCHEDULE) { session ->
            val book = SchedulesBook(session, clock, ids)
            val draft = book.draft(scheduleId) ?: return@edit ScheduleWriteResult.Rejected(ScheduleCopy.NO_SCHEDULE)
            book.save(draft.copy(amountMinor = amountMinor))
        } ?: ScheduleWriteResult.Rejected(ShellCopy.NO_ACCOUNTS)

    /** The assistant's rule idea: transactions from [payeeId] get [categoryId] from now on. */
    fun makeCategoryRule(
        budgetId: String,
        payeeId: String,
        categoryId: String,
    ): WriteResult =
        edit(budgetId, UndoCopy.MAKE_RULE) { session ->
            RulesBook(session, ids).insertPayeeCategoryRule(payeeId, categoryId)
            WriteResult.Saved
        } ?: WriteResult.Rejected(ShellCopy.NO_ACCOUNTS)

    /** Never shows the assistant's finding [key] again; a possible match is the inbox's not a match. */
    fun dismissFinding(
        budgetId: String,
        key: String,
    ): WriteResult {
        AssistantBook.matchIds(key)?.let { (bankId, enteredId) -> return notAMatch(budgetId, bankId, enteredId) }
        return edit(budgetId, UndoCopy.DISMISS) { AssistantBook(it, clock.today()).dismiss(key) }
            ?: WriteResult.Rejected(ShellCopy.NO_ACCOUNTS)
    }

    fun notAMatch(
        budgetId: String,
        bankId: String,
        enteredId: String,
    ): WriteResult =
        edit(budgetId, UndoCopy.NOT_A_MATCH) { MatchBook(it, clock.today()).notAMatch(bankId, enteredId) }
            ?: WriteResult.Rejected(ShellCopy.NO_ACCOUNTS)

    fun deleteTransaction(
        budgetId: String,
        transactionId: String,
        force: Boolean,
    ): WriteResult =
        edit(budgetId, UndoCopy.DELETE_TRANSACTION) { RegisterBook(it).delete(transactionId, force) }
            ?: WriteResult.Rejected(ShellCopy.NO_ACCOUNTS)

    fun deleteTransactions(
        budgetId: String,
        transactionIds: List<String>,
        force: Boolean,
    ): WriteResult =
        edit(budgetId, UndoCopy.DELETE_TRANSACTION) { RegisterBook(it).deleteAll(transactionIds, force) }
            ?: WriteResult.Rejected(ShellCopy.NO_ACCOUNTS)

    fun setTransactionsCleared(
        budgetId: String,
        transactionIds: List<String>,
        cleared: Boolean,
        force: Boolean,
    ): WriteResult =
        edit(budgetId, if (cleared) UndoCopy.CLEAR else UndoCopy.UNCLEAR) {
            RegisterBook(it).setCleared(transactionIds, cleared, force)
        } ?: WriteResult.Rejected(ShellCopy.NO_ACCOUNTS)

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

    fun setReconcileCleared(
        budgetId: String,
        accountId: String,
        transactionIds: List<String>,
        cleared: Boolean,
    ): ReconcilePage? = open(budgetId) { ReconcileBook(it).setCleared(accountId, transactionIds, cleared) }

    fun finishReconcile(
        budgetId: String,
        accountId: String,
        adjust: Boolean = false,
    ): ReconcileFinishResult =
        edit(budgetId, UndoCopy.RECONCILE) { ReconcileBook(it).finish(accountId, adjust) }
            ?: ReconcileFinishResult.Rejected(ShellCopy.NO_ACCOUNTS)

    fun cancelReconcile(
        budgetId: String,
        accountId: String,
    ): Boolean = open(budgetId) { ReconcileBook(it).cancel(accountId) } ?: false

    fun readUpcomingSchedules(budgetId: String): List<UpcomingScheduleRow> =
        open(budgetId) { SchedulesBook(it, clock).listUpcoming() } ?: emptyList()

    fun readSchedules(budgetId: String): List<ScheduleSummary> = open(budgetId) { SchedulesBook(it, clock).listAll() } ?: emptyList()

    /** False until desktop Actual 26.10 has added schedule ordering to this budget. */
    fun canReorderSchedules(budgetId: String): Boolean = open(budgetId) { SchedulesBook(it, clock).canReorder() } ?: false

    /** Puts [scheduleId] just after [targetId] among schedules due the same day, or first when null; one undo step. */
    fun moveSchedule(
        budgetId: String,
        scheduleId: String,
        targetId: String?,
    ): ScheduleWriteResult =
        edit(budgetId, UndoCopy.MOVE_SCHEDULE) { SchedulesBook(it, clock).move(scheduleId, targetId) }
            ?: ScheduleWriteResult.Rejected(ShellCopy.NO_ACCOUNTS)

    fun readScheduleDetails(
        budgetId: String,
        scheduleId: String,
    ): ScheduleDetails? = open(budgetId) { SchedulesBook(it, clock).details(scheduleId) }

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
            ReportsReader(session, clock.today()).page(range, includeOffBudget, groupBy, reportCurrency(session))
        }

    /** Spending report → a row: the expenses under [path], one level further down or one by one. */
    fun readSpendingDrill(
        budgetId: String,
        range: ReportRange,
        path: List<SpendingFilter>,
        income: Boolean = false,
        throughToday: Boolean = false,
    ): SpendingDrill? =
        open(budgetId) { session ->
            ReportsReader(session, clock.today()).spendingDrill(range, path, reportCurrency(session), income, throughToday)
        }

    /** Cash flow → a month: its income by category and its expenses by group. */
    fun readCashFlowMonth(
        budgetId: String,
        month: YearMonth,
    ): CashFlowMonthPage? =
        open(budgetId) { session -> ReportsReader(session, clock.today()).cashFlowMonth(month, reportCurrency(session)) }

    /** The net worth graph over [span], ending today. */
    fun readNetWorthHistory(
        budgetId: String,
        span: NetWorthSpan,
    ): NetWorthHistory? = open(budgetId) { session -> ReportsReader(session, clock.today()).netWorthHistory(span, reportCurrency(session)) }

    private fun reportCurrency(session: SqlSession): CurrencySpec {
        val code =
            session
                .query("SELECT value FROM preferences WHERE id = ?", listOf("currency"))
                .firstOrNull()
                ?.str("value")
                .orEmpty()
        return Currencies.byCode(code) ?: Currencies.byCode("USD")!!
    }

    /** Lists bad template lines across category notes; writes nothing (spec 010 US5). */
    fun checkTemplates(budgetId: String): TemplateResult =
        when {
            !readFormatPrefs(budgetId).templatesOn -> TemplateResult.Rejected(TemplateCopy.OFF)
            else -> open(budgetId) { TemplateEngine(it, clock.today()).check() } ?: TemplateResult.Rejected(ShellCopy.NO_ACCOUNTS)
        }

    /** Apply or overwrite [month]'s budgets from templates as one undo step; both flags must be on. */
    fun applyTemplates(
        budgetId: String,
        month: YearMonth,
        overwrite: Boolean,
        confirmed: Boolean = false,
    ): TemplateResult {
        if (!readFormatPrefs(budgetId).templatesOn) return TemplateResult.Rejected(TemplateCopy.OFF)
        return edit(budgetId, UndoCopy.TEMPLATES) { TemplateEngine(it, clock.today()).run(month, overwrite, confirmed) }
            ?: TemplateResult.Rejected(ShellCopy.NO_ACCOUNTS)
    }

    /** End-of-month cleanup for [month] from the `#cleanup` lines in category notes, as one undo step. */
    fun cleanupMonth(
        budgetId: String,
        month: YearMonth,
    ): CleanupResult {
        if (!readFormatPrefs(budgetId).templatesOn) return CleanupResult.Rejected(TemplateCopy.OFF)
        return edit(budgetId, UndoCopy.CLEANUP) { CleanupEngine(it).run(month) } ?: CleanupResult.Rejected(ShellCopy.NO_ACCOUNTS)
    }

    /**
     * What applying (or, with [overwrite], overwriting) templates would budget for [month], with edit mode's unsaved
     * [budgeted] amounts in place first. Runs the templates and rolls every write back, so nothing is saved.
     * Returns the result and the budgeted amount of each category the templates set.
     */
    fun previewTemplates(
        budgetId: String,
        month: YearMonth,
        overwrite: Boolean,
        budgeted: Map<String, Long>,
    ): Pair<TemplateResult, Map<String, Long>> {
        if (!readFormatPrefs(budgetId).templatesOn) return TemplateResult.Rejected(TemplateCopy.OFF) to emptyMap()
        return serial {
            open(budgetId) { session ->
                val table =
                    if (session.query("SELECT value FROM preferences WHERE id = 'budgetType'").firstOrNull()?.str("value") == "tracking") {
                        "reflect_budgets"
                    } else {
                        "zero_budgets"
                    }

                fun amounts(): Map<String, Long> =
                    session
                        .query("SELECT category, amount FROM $table WHERE month = ?", listOf(month.toActualMonth()))
                        .associate { it.str("category").orEmpty() to it.long("amount") }
                var result: TemplateResult = TemplateResult.Applied(0)
                var after = emptyMap<String, Long>()
                var before = emptyMap<String, Long>()
                try {
                    session.transaction {
                        budgeted.forEach { (id, amount) ->
                            BudgetEdits.assign(session, SyncSchema.clockFor(session), month.toActualMonth(), id, amount)
                        }
                        before = amounts()
                        result = TemplateEngine(session, clock.today()).run(month, overwrite, confirmed = true)
                        after = amounts()
                        throw PreviewRollback()
                    }
                } catch (_: PreviewRollback) {
                    // Rolled back: the preview leaves the file as it was.
                }
                result to after.filter { (id, amount) -> before[id] != amount }
            }
        } ?: (TemplateResult.Rejected(ShellCopy.NO_ACCOUNTS) to emptyMap())
    }

    private class PreviewRollback : RuntimeException()

    /** What each template asks for in [month] on its own (the template editor's preview); writes nothing. */
    fun previewCategoryTemplates(
        budgetId: String,
        categoryId: String,
        month: YearMonth,
        templates: List<Template>,
    ): List<Long?> =
        if (templates.isEmpty()) {
            emptyList()
        } else {
            open(budgetId) { TemplateEngine(it, clock.today()).preview(categoryId, month, templates) } ?: templates.map { null }
        }

    /** Every visible category's templates in priority lanes for [month] (the templates screen); writes nothing. */
    fun templatePlan(
        budgetId: String,
        month: YearMonth,
    ): TemplatePlan? =
        if (!readFormatPrefs(budgetId).templatesOn) null else open(budgetId) { TemplateEngine(it, clock.today()).plan(month) }

    fun readCategoryNote(
        budgetId: String,
        categoryId: String,
    ): String = open(budgetId) { CategoryNotes.read(it, categoryId) }.orEmpty()

    /** The category's budget automations when desktop's automations own it (`source = ui`); null while its note does. */
    fun readCategoryAutomations(
        budgetId: String,
        categoryId: String,
    ): CategoryAutomations? = open(budgetId) { CategoryNotes.automations(it, categoryId) }

    fun saveCategoryAutomations(
        budgetId: String,
        categoryId: String,
        automations: CategoryAutomations,
    ): Boolean = edit(budgetId, UndoCopy.AUTOMATIONS) { CategoryNotes.saveAutomations(it, categoryId, automations) } != null

    /** Switch a notes category to budget automations; null when a template line does not read, so nothing moved. */
    fun migrateCategoryAutomations(
        budgetId: String,
        categoryId: String,
    ): CategoryAutomations? = edit(budgetId, UndoCopy.TO_AUTOMATIONS) { CategoryNotes.migrate(it, categoryId, clock.today()) }

    /** Write a category's automations back into its note; false when one cannot be written as a line. */
    fun unmigrateCategoryAutomations(
        budgetId: String,
        categoryId: String,
    ): Boolean = edit(budgetId, UndoCopy.TO_NOTES) { CategoryNotes.unmigrate(it, categoryId) } == true

    fun saveCategoryNote(
        budgetId: String,
        categoryId: String,
        note: String,
    ): Boolean = edit(budgetId, UndoCopy.CATEGORY_NOTE) { CategoryNotes.write(it, categoryId, note) } != null

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

    /** Unattended bank sync of every SimpleFIN account (see [BankSyncBook.syncAll]); null when the budget is gone. */
    fun syncBankAccounts(
        budgetId: String,
        serverAddress: String,
        token: String,
        transport: BankSyncTransport,
        today: java.time.LocalDate,
        nowMillis: Long,
        onAccount: (BankLinkedAccount) -> Unit = {},
        onFailure: (BankSyncFailure) -> Unit = {},
    ): BankSyncSummary? =
        serial {
            open(budgetId) { BankSyncBook(it, transport).syncAll(serverAddress, token, today, nowMillis, ids, onAccount, onFailure) }
        }

    /**
     * Clears one budget from the phone: its folder, the open-budget pointer when it is the open
     * one, and its ask-each-time entry. The server copy is not touched (budget picker).
     */
    fun delete(id: String): Boolean {
        if (id.isBlank() || id.contains('/') || id.contains('\\') || id == "." || id == "..") return false
        val dir = File(root, id)
        if (!dir.isDirectory) return false
        updateSettings { settings ->
            settings.copy(
                openBudgetId = settings.openBudgetId.takeUnless { it == id },
                askEachTime = settings.askEachTime - id,
            )
        }
        journal.switchTo(null)
        return dir.deleteRecursively()
    }

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
        return watched(id) {
            sessions.use(database) { session ->
                ActualSchema.ensure(session)
                SyncSchema.withClock(session, syncClockFor(session)) { block(session) }
            }
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
