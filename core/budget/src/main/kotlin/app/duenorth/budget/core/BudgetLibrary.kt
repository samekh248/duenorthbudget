package app.duenorth.budget.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.YearMonth
import java.util.UUID

class BudgetLibrary(
    val root: File,
    private val sessions: SessionOpener,
    private val clock: BudgetClock,
    private val ids: () -> String = { UUID.randomUUID().toString() },
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }

    fun settings(): PhoneSettings {
        val file = phoneFile()
        if (!file.exists()) return PhoneSettings()
        return json.decodeFromString(PhoneSettings.serializer(), file.readText())
    }

    fun save(settings: PhoneSettings) {
        root.mkdirs()
        writeAtomically(phoneFile(), json.encodeToString(settings))
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

    fun readShell(id: String): MonthShell? {
        val dir = File(root, id)
        val metadata = readMetadata(dir) ?: return null
        val database = File(dir, "db.sqlite")
        if (!database.exists()) return null
        return sessions.use(database) { session ->
            ActualSchema.ensure(session)
            ShellReader.read(session, metadata, clock.today())
        }
    }

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
    ): WriteResult = open(budgetId) { RegisterBook(it).save(draft) } ?: WriteResult.Rejected(ShellCopy.NO_ACCOUNTS)

    fun setTransactionCategory(
        budgetId: String,
        transactionId: String,
        categoryId: String?,
        force: Boolean,
    ): WriteResult =
        open(budgetId) { RegisterBook(it).setCategory(transactionId, categoryId, force) }
            ?: WriteResult.Rejected(ShellCopy.NO_ACCOUNTS)

    fun deleteTransaction(
        budgetId: String,
        transactionId: String,
        force: Boolean,
    ): WriteResult =
        open(budgetId) { RegisterBook(it).delete(transactionId, force) }
            ?: WriteResult.Rejected(ShellCopy.NO_ACCOUNTS)

    fun splitTransaction(
        budgetId: String,
        transactionId: String,
        parts: List<SplitPart>,
        force: Boolean,
    ): WriteResult =
        open(budgetId) { RegisterBook(it).split(transactionId, parts, force) }
            ?: WriteResult.Rejected(ShellCopy.NO_ACCOUNTS)

    fun unsplitTransaction(
        budgetId: String,
        transactionId: String,
        force: Boolean,
    ): WriteResult =
        open(budgetId) { RegisterBook(it).unsplit(transactionId, force) }
            ?: WriteResult.Rejected(ShellCopy.NO_ACCOUNTS)

    fun transfer(
        budgetId: String,
        draft: TransferDraft,
    ): WriteResult = open(budgetId) { RegisterBook(it).transfer(draft) } ?: WriteResult.Rejected(ShellCopy.NO_ACCOUNTS)

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
        open(budgetId) { ReconcileBook(it).finish(accountId) }
            ?: ReconcileFinishResult.Rejected(ShellCopy.NO_ACCOUNTS)

    fun cancelReconcile(
        budgetId: String,
        accountId: String,
    ): Boolean = open(budgetId) { ReconcileBook(it).cancel(accountId) } ?: false

    fun switchTo(id: String): Boolean {
        val target = summary(File(root, id)) ?: return false
        val settings = settings()
        if (settings.openBudgetId == target.id) return true
        save(settings.copy(openBudgetId = target.id))
        return true
    }

    private fun <T> open(
        id: String,
        block: (SqlSession) -> T,
    ): T? {
        val database = File(root, id).resolve("db.sqlite")
        if (!database.isFile) return null
        return sessions.use(database) { session ->
            ActualSchema.ensure(session)
            block(session)
        }
    }

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
