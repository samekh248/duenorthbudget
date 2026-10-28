package app.duenorth.budget.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate

data class BankLinkedAccount(
    val accountId: String,
    val name: String,
    val syncSource: String,
    val bankAccountId: String,
)

interface BankSyncTransport {
    fun fetchTransactions(
        serverAddress: String,
        token: String,
        bankAccountId: String,
        startDateIso: String,
    ): List<ParsedImportRow>
}

class HttpBankSyncTransport : BankSyncTransport {
    private val json = Json { ignoreUnknownKeys = true }

    override fun fetchTransactions(
        serverAddress: String,
        token: String,
        bankAccountId: String,
        startDateIso: String,
    ): List<ParsedImportRow> {
        val base = serverAddress.trimEnd('/')
        val body =
            """
            {"accountId":"$bankAccountId","startDate":"$startDateIso"}
            """.trimIndent().toByteArray(Charsets.UTF_8)
        val connection =
            java.net.URL("$base/simplefin/transactions").openConnection() as java.net.HttpURLConnection
        try {
            // A bridge that never answers would hold the budget's lock, and every sync behind it, for good.
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-ACTUAL-TOKEN", token)
            connection.doOutput = true
            connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            val bytes = stream?.readBytes() ?: ByteArray(0)
            if (code != 200) throw BankSyncException(ImportCopy.FETCH_FAILED)
            val reply = json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
            // Actual answers `{"status":"ok","data":{...}}`, and a bank error comes back inside `data`.
            val root = reply["data"] as? JsonObject ?: reply
            if (root.containsKey("error_code")) throw BankSyncException(ImportCopy.FETCH_FAILED)
            return parseSimpleFinResponse(root, 2)
        } catch (error: BankSyncException) {
            throw error
        } catch (_: Exception) {
            throw BankSyncException(ImportCopy.FETCH_FAILED)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseSimpleFinResponse(
        root: JsonObject,
        decimals: Int,
    ): List<ParsedImportRow> {
        val transactions =
            root["transactions"]
                ?.jsonObject
                ?.get("all")
                ?.jsonArray
                ?: root["transactions"]?.jsonArray
                ?: JsonArray(emptyList())
        return transactions.mapNotNull { element -> mapTransaction(element, decimals) }
    }

    private fun mapTransaction(
        element: JsonElement,
        decimals: Int,
    ): ParsedImportRow? {
        val row = element.jsonObject
        val dateIso =
            row.string("date")
                ?: row.string("posted")
                ?: return null
        val date = RegisterEntry.parseDate(dateIso) ?: return null
        val amount =
            row.longAmount("amount")
                ?: row
                    .jsonObject["transactionAmount"]
                    ?.jsonObject
                    ?.string("amount")
                    ?.let { ImportFileParserAmount.parse(it, decimals) }
                ?: return null
        // Actual's server sends `payeeName`, `notes` and `transactionId`, the names its web app imports from.
        val payee =
            row.string("payeeName")
                ?: row.string("payee_name")
                ?: row.string("imported_payee")
                ?: row.string("payee")
                ?: return null
        val financialId = row.string("transactionId") ?: row.string("imported_id") ?: row.string("id")
        return ParsedImportRow(
            date = date,
            payee = payee,
            amountMinor = amount,
            financialId = financialId,
            importedPayee = row.string("imported_payee") ?: payee,
            note = row.string("notes"),
        )
    }

    private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.longAmount(name: String): Long? {
        val primitive = this[name]?.jsonPrimitive ?: return null
        primitive.contentOrNull?.toLongOrNull()?.let { return it }
        primitive.doubleOrNull?.let { return (it * 100).toLong() }
        return null
    }
}

private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 90_000

private object ImportFileParserAmount {
    fun parse(
        raw: String,
        decimals: Int,
    ): Long? {
        var cleaned = raw.trim().replace(",", "")
        val negative = cleaned.startsWith('-')
        if (negative) cleaned = cleaned.drop(1)
        val magnitude = MoneyParse.parseMagnitude(cleaned, decimals) ?: return null
        return if (negative) -magnitude else magnitude
    }
}

class BankSyncException(
    message: String,
) : Exception(message)

class BankSyncBook(
    private val session: SqlSession,
    private val transport: BankSyncTransport,
) {
    fun linkedAccount(accountId: String): BankLinkedAccount? {
        val row =
            session
                .query(
                    """
                    SELECT id, name, account_id, account_sync_source
                    FROM accounts
                    WHERE id = ? AND IFNULL(tombstone, 0) = 0
                    """.trimIndent(),
                    listOf(accountId),
                ).firstOrNull() ?: return null
        val source = row.str("account_sync_source")?.trim().orEmpty()
        val bankId = row.str("account_id")?.trim().orEmpty()
        if (source.isEmpty() || bankId.isEmpty()) return null
        return BankLinkedAccount(
            accountId = row.str("id").orEmpty(),
            name = row.str("name").orEmpty(),
            syncSource = source,
            bankAccountId = bankId,
        )
    }

    fun previewFetch(
        accountId: String,
        serverAddress: String?,
        token: String?,
        today: LocalDate,
    ): Pair<ImportPreviewBundle?, String?> {
        val linked = linkedAccount(accountId) ?: return null to ImportCopy.NOTHING_TO_FETCH
        if (linked.syncSource != "simpleFin") return null to ImportCopy.FETCH_FAILED
        if (serverAddress.isNullOrBlank() || token.isNullOrBlank()) return null to ImportCopy.FETCH_FAILED
        val start = today.minusDays(89)
        val startIso = String.format("%04d-%02d-%02d", start.year, start.monthValue, start.dayOfMonth)
        val rows =
            try {
                transport.fetchTransactions(serverAddress, token, linked.bankAccountId, startIso)
            } catch (error: BankSyncException) {
                return null to error.message
            }
        val import =
            ImportBook(session) {
                java.util.UUID
                    .randomUUID()
                    .toString()
            }
        val currency =
            session
                .query("SELECT value FROM preferences WHERE id = ?", listOf("currency"))
                .firstOrNull()
                ?.str("value")
                .let { Currencies.byCode(it.orEmpty()) ?: Currencies.byCode("USD")!! }
        val preview = import.previewCandidates(accountId, currency, rows)
        return ImportPreviewBundle(preview, rows) to null
    }

    /**
     * Bank sync with nobody watching: every open account linked through SimpleFIN fetches the
     * last 90 days and adds what it does not already hold, as Actual's bank sync does, with no
     * preview. Duplicates are skipped by the same checks as a fetch; a failed account is counted
     * and the rest still run.
     */
    fun syncAll(
        serverAddress: String,
        token: String,
        today: LocalDate,
        nowMillis: Long,
        ids: () -> String,
        /** Each linked account as its fetch starts, so the screen can name it. */
        onAccount: (BankLinkedAccount) -> Unit = {},
        /** Each account that failed, as it fails. */
        onFailure: (BankSyncFailure) -> Unit = {},
    ): BankSyncSummary {
        val linked =
            session
                .query(
                    """
                    SELECT id FROM accounts
                    WHERE account_sync_source = ? AND IFNULL(account_id, '') != ''
                        AND IFNULL(tombstone, 0) = 0 AND IFNULL(closed, 0) = 0
                    ORDER BY sort_order, id
                    """.trimIndent(),
                    listOf("simpleFin"),
                ).mapNotNull { row -> row.str("id")?.let { linkedAccount(it) } }
        val start = today.minusDays(89)
        val startIso = String.format("%04d-%02d-%02d", start.year, start.monthValue, start.dayOfMonth)
        var added = 0
        val failures = mutableListOf<BankSyncFailure>()
        linked.forEach { account ->
            onAccount(account)
            val rows =
                try {
                    transport.fetchTransactions(serverAddress, token, account.bankAccountId, startIso)
                } catch (error: BankSyncException) {
                    val failure = BankSyncFailure(account.name, error.message ?: ImportCopy.FETCH_FAILED)
                    failures += failure
                    onFailure(failure)
                    return@forEach
                }
            added += ImportBook(session, ids).confirm(account.accountId, rows, review = false).addedIds.size
            SyncSchema.write(session, "accounts", account.accountId, "last_sync", nowMillis.toString())
        }
        return BankSyncSummary(accounts = linked.size, added = added, failures = failures)
    }
}

/** How one unattended bank sync went: linked accounts tried, transactions added, accounts that failed. */
data class BankSyncSummary(
    val accounts: Int,
    val added: Int,
    val failures: List<BankSyncFailure> = emptyList(),
) {
    val failed: Int get() = failures.size
}

/** One account the bank sync could not fetch, and the short reason. */
data class BankSyncFailure(
    val accountName: String,
    val message: String,
)

class FakeBankSyncTransport(
    private val rows: List<ParsedImportRow>,
) : BankSyncTransport {
    override fun fetchTransactions(
        serverAddress: String,
        token: String,
        bankAccountId: String,
        startDateIso: String,
    ): List<ParsedImportRow> = rows
}

/** Words for spec 010's bank link (`contracts/bank-link.md`). */
object BankLinkCopy {
    const val TITLE = "bank link"
    const val PROVIDERS = "providers"
    const val NONE = "no bank providers are set up on this server"
    const val TOKEN = "SimpleFIN setup token"
    const val SEND_TOKEN = "save token"
    const val COUNTRY = "country code, like GB"
    const val FIND_BANKS = "find banks"
    const val SIGN_IN = "sign in at your bank"
    const val SIGNED_IN = "I've signed in"
    const val PICK_ACCOUNT = "pick the bank account"
    const val NO_ACCOUNTS = "no accounts yet"
    const val LINKED = "linked to"
    const val REMOVE = "remove link"
    const val REMOVE_BODY = "this account stops syncing from the bank; its transactions stay"
    const val LINK = "link bank account"
    const val UNREACHABLE = "couldn't reach the bank provider"
    const val SIGN_IN_FIRST = "sign in to a server first"
    const val WAITING = "finish signing in at your bank first"
}

class BankLinkException(
    message: String,
) : Exception(message)

/** The providers Actual 26.8.1's server can link through, with the `account_sync_source` each writes. */
enum class BankProvider(
    val label: String,
    val syncSource: String,
    val path: String,
) {
    GOCARDLESS("GoCardless", "goCardless", "gocardless"),
    SIMPLEFIN("SimpleFIN", "simpleFin", "simplefin"),
    PLUGGY("Pluggy.ai", "pluggyai", "pluggyai"),
    ;

    companion object {
        fun bySource(source: String?): BankProvider? = entries.firstOrNull { it.syncSource == source }
    }
}

/** An account at the bank, as the provider lists it. */
data class ExternalAccount(
    val accountId: String,
    val name: String,
    val institution: String,
    /** `banks.bank_id`: the requisition for GoCardless, the org domain or id otherwise. */
    val bankKey: String,
    val balanceMinor: Long?,
)

data class GoCardlessBank(
    val id: String,
    val name: String,
)

data class GoCardlessLink(
    val link: String,
    val requisitionId: String,
)

/** Provider calls through the active server. Secrets go only to `POST /secret`. */
class BankLinkClient(
    private val transport: ActualTransport,
    private val address: String,
    private val token: String,
) {
    /** The providers the server reports as configured. */
    fun configured(): List<BankProvider> =
        BankProvider.entries.filter { provider ->
            val data = call("${provider.path}/status", JsonObject(emptyMap()))
            (data as? JsonObject)?.get("configured")?.jsonPrimitive?.contentOrNull == "true"
        }

    /** Sends the SimpleFIN setup token to the server and keeps no copy. */
    fun saveSimpleFinToken(value: String) {
        call("secret", JsonObject(mapOf("name" to JsonPrimitive("simplefin_token"), "value" to JsonPrimitive(value.trim()))))
    }

    fun accounts(provider: BankProvider): List<ExternalAccount> {
        require(provider != BankProvider.GOCARDLESS) { "GoCardless accounts come from a requisition" }
        val data = call("${provider.path}/accounts", JsonObject(emptyMap()))
        return list(data, "accounts").mapNotNull { account ->
            val org = account["org"] as? JsonObject
            val id = account.text("id") ?: account.text("account_id") ?: return@mapNotNull null
            val institution = org?.text("name") ?: account.text("institution") ?: provider.label
            ExternalAccount(
                accountId = id,
                name = account.text("name") ?: id,
                institution = institution,
                bankKey = org?.text("domain") ?: org?.text("id") ?: institution,
                balanceMinor = minor(account["balance"]),
            )
        }
    }

    fun goCardlessBanks(country: String): List<GoCardlessBank> {
        val data = call("gocardless/get-banks", JsonObject(mapOf("country" to JsonPrimitive(country.trim().uppercase()))))
        return (data as? JsonArray)
            .orEmpty()
            .mapNotNull { it as? JsonObject }
            .mapNotNull { bank -> GoCardlessBank(bank.text("id") ?: return@mapNotNull null, bank.text("name").orEmpty()) }
    }

    fun goCardlessStart(institutionId: String): GoCardlessLink {
        val data =
            call(
                "gocardless/create-web-token",
                JsonObject(mapOf("institutionId" to JsonPrimitive(institutionId), "accessValidForDays" to JsonPrimitive(90))),
            ) as? JsonObject ?: throw BankLinkException(BankLinkCopy.UNREACHABLE)
        return GoCardlessLink(
            link = data.text("link") ?: throw BankLinkException(BankLinkCopy.UNREACHABLE),
            requisitionId = data.text("requisitionId") ?: throw BankLinkException(BankLinkCopy.UNREACHABLE),
        )
    }

    /** The requisition's accounts; empty until the person finishes at the bank. */
    fun goCardlessAccounts(requisitionId: String): List<ExternalAccount> {
        val data = call("gocardless/get-accounts", JsonObject(mapOf("requisitionId" to JsonPrimitive(requisitionId))))
        return list(data, "accounts").mapNotNull { account ->
            val id = account.text("account_id") ?: return@mapNotNull null
            ExternalAccount(
                accountId = id,
                name = account.text("name") ?: account.text("official_name") ?: id,
                institution = (account["institution"] as? JsonObject)?.text("name") ?: BankProvider.GOCARDLESS.label,
                bankKey = requisitionId,
                balanceMinor = minor(account["balance"]),
            )
        }
    }

    private fun call(
        path: String,
        body: JsonObject,
    ): JsonElement =
        try {
            transport.provider(address, token, path, body)
        } catch (error: SyncNetworkException) {
            throw BankLinkException(BankLinkCopy.UNREACHABLE)
        }

    private fun list(
        data: JsonElement,
        key: String,
    ): List<JsonObject> =
        ((data as? JsonObject)?.get(key) as? JsonArray)
            .orEmpty()
            .mapNotNull { it as? JsonObject }

    private fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun minor(value: JsonElement?): Long? =
        (value as? JsonPrimitive)
            ?.contentOrNull
            ?.toBigDecimalOrNull()
            ?.movePointRight(2)
            ?.setScale(0, java.math.RoundingMode.HALF_UP)
            ?.toLong()
}

sealed interface BankLinkResult {
    data object Saved : BankLinkResult

    data class Rejected(
        val reason: String,
    ) : BankLinkResult
}

/**
 * Links a phone account to a bank account as Actual's `linkSimpleFinAccount` and friends do
 * (`contracts/bank-link.md`): a `banks` row found or made by its `bank_id`, then the account's
 * link columns. Remove clears them as `unlinkAccount` does.
 */
class BankLinkBook(
    private val session: SqlSession,
    private val ids: () -> String,
) {
    fun link(
        accountId: String,
        provider: BankProvider,
        external: ExternalAccount,
    ): BankLinkResult {
        if (!accountAlive(accountId)) return BankLinkResult.Rejected(AccountsCopy.NOT_FOUND)
        val bank =
            session
                .query("SELECT id FROM banks WHERE bank_id = ? AND IFNULL(tombstone, 0) = 0", listOf(external.bankKey))
                .firstOrNull()
                ?.str("id")
                ?: ids().also { id ->
                    SyncSchema.writeRow(
                        session,
                        "banks",
                        id,
                        "bank_id" to external.bankKey,
                        "name" to external.institution,
                        "tombstone" to 0,
                    )
                }
        SyncSchema.write(session, "accounts", accountId, "account_id", external.accountId)
        SyncSchema.write(session, "accounts", accountId, "bank", bank)
        SyncSchema.write(session, "accounts", accountId, "account_sync_source", provider.syncSource)
        SyncSchema.write(session, "accounts", accountId, "balance_current", external.balanceMinor)
        SyncSchema.write(session, "accounts", accountId, "balance_available", null)
        SyncSchema.write(session, "accounts", accountId, "balance_limit", null)
        SyncSchema.write(session, "accounts", accountId, "last_sync", null)
        return BankLinkResult.Saved
    }

    fun remove(accountId: String): BankLinkResult {
        if (!accountAlive(accountId)) return BankLinkResult.Rejected(AccountsCopy.NOT_FOUND)
        for (column in listOf("account_id", "bank", "account_sync_source", "balance_current", "balance_available", "balance_limit")) {
            SyncSchema.write(session, "accounts", accountId, column, null)
        }
        return BankLinkResult.Saved
    }

    /** Provider and bank name for a linked account, else null. */
    fun linked(accountId: String): Pair<BankProvider, String>? {
        val row =
            session
                .query(
                    """
                    SELECT a.account_sync_source, a.account_id, b.name AS bank_name
                    FROM accounts a LEFT JOIN banks b ON b.id = a.bank
                    WHERE a.id = ?
                    """.trimIndent(),
                    listOf(accountId),
                ).firstOrNull() ?: return null
        if (row.str("account_id").isNullOrBlank()) return null
        val provider = BankProvider.bySource(row.str("account_sync_source")) ?: return null
        return provider to (row.str("bank_name") ?: provider.label)
    }

    private fun accountAlive(accountId: String): Boolean =
        session.query("SELECT 1 FROM accounts WHERE id = ? AND IFNULL(tombstone, 0) = 0", listOf(accountId)).isNotEmpty()
}
