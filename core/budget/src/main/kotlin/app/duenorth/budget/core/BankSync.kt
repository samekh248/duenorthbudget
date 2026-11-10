package app.duenorth.budget.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
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
    /** GoCardless's requisition, kept in the account's `banks` row as `bank_id`; null for the others. */
    val requisitionId: String? = null,
)

interface BankSyncTransport {
    fun fetchTransactions(
        serverAddress: String,
        token: String,
        bankAccountId: String,
        startDateIso: String,
    ): List<ParsedImportRow>

    /** The rows and the bank's own balance for the account, when the bridge sends one. */
    fun fetchAccount(
        serverAddress: String,
        token: String,
        bankAccountId: String,
        startDateIso: String,
    ): BankFetch = BankFetch(fetchTransactions(serverAddress, token, bankAccountId, startDateIso), null)

    /**
     * Every account in [accounts], by [BankLinkedAccount.accountId]. A failure that is one account's own comes back in
     * its [BankFetch.error]; one that stops them all (the server refused the token, or could not be reached) throws.
     */
    fun fetchAll(
        serverAddress: String,
        token: String,
        accounts: List<BankLinkedAccount>,
        startDateIso: String,
    ): Map<String, BankFetch> =
        accounts.associate { account ->
            account.accountId to
                try {
                    fetchAccount(serverAddress, token, account.bankAccountId, startDateIso)
                } catch (error: BankSyncException) {
                    BankFetch(emptyList(), null, error)
                }
        }
}

/**
 * What one account's fetch brought back: its transactions, and the balance the bank reports, in minor units. [error]
 * is set when the bank also said something is wrong with the account; the rows it sent anyway are still kept.
 */
data class BankFetch(
    val rows: List<ParsedImportRow>,
    val balanceMinor: Long?,
    val error: BankSyncException? = null,
)

class HttpBankSyncTransport : BankSyncTransport {
    private val json = Json { ignoreUnknownKeys = true }

    override fun fetchTransactions(
        serverAddress: String,
        token: String,
        bankAccountId: String,
        startDateIso: String,
    ): List<ParsedImportRow> = fetchAccount(serverAddress, token, bankAccountId, startDateIso).rows

    override fun fetchAccount(
        serverAddress: String,
        token: String,
        bankAccountId: String,
        startDateIso: String,
    ): BankFetch {
        val root =
            post(
                serverAddress,
                token,
                "simplefin/transactions",
                JsonObject(mapOf("accountId" to JsonPrimitive(bankAccountId), "startDate" to JsonPrimitive(startDateIso))),
            )
        return BankFetch(parseRows(root), parseBalance(root))
    }

    /**
     * SimpleFIN accounts in one request, as Actual's own sync of every account does: SimpleFIN Bridge rations how
     * often it is asked, and a request per account spent that many times over. GoCardless and Pluggy.ai, which take
     * one account at a time, each in its own.
     */
    override fun fetchAll(
        serverAddress: String,
        token: String,
        accounts: List<BankLinkedAccount>,
        startDateIso: String,
    ): Map<String, BankFetch> {
        val fetched = mutableMapOf<String, BankFetch>()
        val simpleFin = accounts.filter { it.syncSource == BankProvider.SIMPLEFIN.syncSource }
        if (simpleFin.isNotEmpty()) fetched += simpleFinBatch(serverAddress, token, simpleFin, startDateIso)
        for (account in accounts - simpleFin.toSet()) {
            fetched[account.accountId] =
                try {
                    fetchOther(serverAddress, token, account, startDateIso)
                } catch (error: BankSyncException) {
                    BankFetch(emptyList(), null, error)
                }
        }
        return fetched
    }

    private fun simpleFinBatch(
        serverAddress: String,
        token: String,
        accounts: List<BankLinkedAccount>,
        startDateIso: String,
    ): Map<String, BankFetch> {
        val body =
            JsonObject(
                mapOf(
                    "accountId" to JsonArray(accounts.map { JsonPrimitive(it.bankAccountId) }),
                    "startDate" to JsonArray(accounts.map { JsonPrimitive(startDateIso) }),
                ),
            )
        val root = post(serverAddress, token, "simplefin/transactions", body)
        val errors = root["errors"] as? JsonObject
        return accounts.associate { account ->
            val data = root[account.bankAccountId] as? JsonObject
            val error =
                ((errors?.get(account.bankAccountId) as? JsonArray)?.firstOrNull() as? JsonObject)
                    ?.string("error_code")
                    ?.let { SimpleFinError.of(it) }
            account.accountId to
                if (data == null) {
                    BankFetch(emptyList(), null, error ?: SimpleFinError.of("ACCOUNT_MISSING"))
                } else {
                    BankFetch(parseRows(data), parseBalance(data), error)
                }
        }
    }

    private fun fetchOther(
        serverAddress: String,
        token: String,
        account: BankLinkedAccount,
        startDateIso: String,
    ): BankFetch {
        val provider = BankProvider.bySource(account.syncSource) ?: throw BankSyncException(ImportCopy.FETCH_FAILED)
        val body =
            buildMap {
                put("accountId", JsonPrimitive(account.bankAccountId))
                put("startDate", JsonPrimitive(startDateIso))
                if (provider == BankProvider.GOCARDLESS) {
                    put("requisitionId", JsonPrimitive(account.requisitionId.orEmpty()))
                    put("includeBalance", JsonPrimitive(true))
                }
            }
        val root = post(serverAddress, token, "${provider.path}/transactions", JsonObject(body))
        // Pluggy.ai's `balances` carry minor units already, so its `startingBalance` (the current balance) is read.
        val balance =
            if (provider == BankProvider.PLUGGY) {
                root["startingBalance"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.toBigDecimalOrNull()
                    ?.toLong()
            } else {
                parseBalance(root)
            }
        return BankFetch(parseRows(root), balance)
    }

    /** One call to the budget server's bank bridge: the reply's `data`, or the failure it names. */
    private fun post(
        serverAddress: String,
        token: String,
        path: String,
        payload: JsonObject,
    ): JsonObject {
        val base = serverAddress.trimEnd('/')
        val body = payload.toString().toByteArray(Charsets.UTF_8)
        val connection = java.net.URL("$base/$path").openConnection() as java.net.HttpURLConnection
        try {
            // A bridge that never answers would hold up this bank sync, and every sync behind it, for good.
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
            if (code == 401 || code == 403) throw BankSyncException(SyncCopy.SIGN_IN_AGAIN, BankSyncFix.SIGN_IN)
            if (code != 200) throw BankSyncException(ImportCopy.FETCH_FAILED)
            val reply = json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
            // Actual answers `{"status":"ok","data":{...}}`, and a bank error comes back inside `data`.
            val root = reply["data"] as? JsonObject ?: reply
            root.string("error_code")?.let { throw SimpleFinError.of(it) }
            return root
        } catch (error: BankSyncException) {
            throw error
        } catch (_: Exception) {
            throw BankSyncException(ImportCopy.FETCH_FAILED)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseRows(root: JsonObject): List<ParsedImportRow> {
        val transactions =
            (root["transactions"] as? JsonObject)?.get("all") as? JsonArray
                ?: root["transactions"] as? JsonArray
                ?: JsonArray(emptyList())
        return transactions.mapNotNull { element -> (element as? JsonObject)?.let { mapTransaction(it, DECIMALS) } }
    }

    /**
     * The bank's current balance: Actual's server sends `balances`, the `expected` one being the account's balance
     * as the bank reports it.
     */
    private fun parseBalance(root: JsonObject): Long? {
        val balances = (root["balances"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        val pick = balances.firstOrNull { it.string("balanceType") == "expected" } ?: balances.firstOrNull() ?: return null
        val amount = (pick["balanceAmount"] as? JsonObject)?.string("amount") ?: return null
        return ImportFileParserAmount.parse(amount, DECIMALS)
    }

    private fun mapTransaction(
        row: JsonObject,
        decimals: Int,
    ): ParsedImportRow? {
        val dateIso =
            row.string("date")
                ?: row.string("posted")
                ?: return null
        val date = RegisterEntry.parseDate(dateIso) ?: return null
        val amount =
            row.longAmount("amount")
                ?: (row["transactionAmount"] as? JsonObject)
                    ?.string("amount")
                    ?.let { ImportFileParserAmount.parse(it, decimals) }
                ?: return null
        // Actual's server sends `payeeName`, `notes` and `transactionId`, the names its web app imports from. Many banks
        // send SimpleFIN no payee, only a description (Actual's `notes`): the row still comes in, named by that.
        val payee =
            row.string("payeeName")
                ?: row.string("payee_name")
                ?: row.string("imported_payee")
                ?: row.string("payee")
                ?: row.string("notes")
                ?: row.string("description")
                ?: ""
        val pending = isPending(row)
        val financialId =
            row.string("transactionId")
                ?: row.string("imported_id")
                ?: row.string("id")
                // GoCardless banks that send no `transactionId`: Actual names a posted row by the bank's internal id.
                ?: row.string("internalTransactionId")?.takeIf { !pending }?.let { "$INTERNAL_ID_PREFIX$it" }
        return ParsedImportRow(
            date = date,
            payee = payee,
            amountMinor = amount,
            financialId = financialId,
            importedPayee = row.string("imported_payee") ?: payee.ifBlank { null },
            note = row.string("notes"),
            pending = pending,
            // `booked` is written either way, so a row that only said `pending` still reads as pending later.
            raw = JsonObject(row + ("booked" to JsonPrimitive(!pending))).toString(),
        )
    }

    /** Actual's server marks a row the bank has not posted with `booked: false`; SimpleFIN itself says `pending`. */
    private fun isPending(row: JsonObject): Boolean {
        val booked = (row["booked"] as? JsonPrimitive)?.booleanOrNull
        if (booked != null) return !booked
        return (row["pending"] as? JsonPrimitive)?.booleanOrNull == true
    }

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.longAmount(name: String): Long? {
        val primitive = this[name] as? JsonPrimitive ?: return null
        primitive.contentOrNull?.toLongOrNull()?.let { return it }
        primitive.contentOrNull?.let { ImportFileParserAmount.parse(it, DECIMALS) }?.let { return it }
        return null
    }

    companion object {
        /**
         * Marks a [ParsedImportRow.financialId] made from GoCardless's `internalTransactionId`; the account's own id
         * goes in its place as it is stored, as Actual writes `<account>-<internal id>`.
         */
        const val INTERNAL_ID_PREFIX = "internal-id:"

        private const val DECIMALS = 2
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
    /** What the person can do about it, when anything. */
    val fix: BankSyncFix? = null,
) : Exception(message)

/**
 * Where a broken bank connection is mended: none of these can be done from the phone alone. Every failure but
 * [SIGN_IN] also offers SimpleFIN Bridge, where most broken connections are mended whatever the error said.
 */
enum class BankSyncFix {
    /** The bank's sign-in at SimpleFIN Bridge lapsed (a changed password, a new MFA prompt, expired consent). */
    SIMPLEFIN,

    /** SimpleFIN refused the server's access token, so it needs a new setup token. */
    TOKEN,

    /** SimpleFIN no longer has the linked account, so it needs linking again. */
    RELINK,

    /** The budget server, not SimpleFIN, turned the phone away: signing in again mends it, not SimpleFIN. */
    SIGN_IN,
}

/** The `error_code`s Actual's server sends back from `/simplefin/transactions`, in the app's words. */
object SimpleFinError {
    /** Where SimpleFIN Bridge lists its connections; its sign-in page leads there. */
    const val BRIDGE_URL = "https://bridge.simplefin.org/auth/login"
    const val NEEDS_ATTENTION = "the bank needs you to sign in again at SimpleFIN"
    const val TOKEN_REFUSED = "SimpleFIN refused the server's token"
    const val ACCOUNT_MISSING = "SimpleFIN no longer has this account"
    const val SERVER_DOWN = "SimpleFIN didn't answer"

    fun of(code: String): BankSyncException =
        when (code) {
            "ACCOUNT_NEEDS_ATTENTION" -> BankSyncException(NEEDS_ATTENTION, BankSyncFix.SIMPLEFIN)
            "INVALID_ACCESS_TOKEN" -> BankSyncException(TOKEN_REFUSED, BankSyncFix.TOKEN)
            "ACCOUNT_MISSING" -> BankSyncException(ACCOUNT_MISSING, BankSyncFix.RELINK)
            "SERVER_DOWN" -> BankSyncException(SERVER_DOWN)
            else -> BankSyncException(ImportCopy.FETCH_FAILED)
        }
}

class BankSyncBook(
    private val session: SqlSession,
    private val transport: BankSyncTransport,
) {
    fun linkedAccount(accountId: String): BankLinkedAccount? {
        val row =
            session
                .query(
                    """
                    SELECT a.id, a.name, a.account_id, a.account_sync_source, b.bank_id
                    FROM accounts a LEFT JOIN banks b ON b.id = a.bank
                    WHERE a.id = ? AND IFNULL(a.tombstone, 0) = 0
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
            requisitionId = row.str("bank_id")?.takeIf { source == BankProvider.GOCARDLESS.syncSource },
        )
    }

    fun previewFetch(
        accountId: String,
        serverAddress: String?,
        token: String?,
        today: LocalDate,
    ): Pair<ImportPreviewBundle?, String?> {
        val linked = linkedAccount(accountId) ?: return null to ImportCopy.NOTHING_TO_FETCH
        if (BankProvider.bySource(linked.syncSource) == null) return null to ImportCopy.FETCH_FAILED
        if (serverAddress.isNullOrBlank() || token.isNullOrBlank()) return null to ImportCopy.FETCH_FAILED
        val fetch =
            try {
                transport.fetchAll(serverAddress, token, listOf(linked), startIso(today))[linked.accountId]
            } catch (error: BankSyncException) {
                return null to error.message
            } ?: return null to ImportCopy.FETCH_FAILED
        if (fetch.rows.isEmpty() && fetch.error != null) return null to fetch.error.message
        val rows = ownIds(linked, fetch.rows)
        val import =
            ImportBook(session) {
                java.util.UUID
                    .randomUUID()
                    .toString()
            }
        val currency =
            BudgetCurrency.read(session)
        val preview = import.previewCandidates(accountId, currency, rows)
        return ImportPreviewBundle(preview, rows) to null
    }

    /**
     * Bank sync with nobody watching: every open bank-linked account fetches the last 90 days and adds what it does
     * not already hold, as Actual's bank sync does, with no preview. Duplicates are skipped by the same checks as a
     * fetch; a failed account is counted and the rest still run.
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
    ): BankSyncSummary =
        syncAll(
            transport = transport,
            serverAddress = serverAddress,
            token = token,
            today = today,
            linked = ::linkedAccounts,
            store = { account, fetch -> store(account, fetch, nowMillis, ids) },
            onAccount = onAccount,
            onFailure = onFailure,
        )

    /** Every open account linked to a bank through a provider the server offers, in the budget's order. */
    fun linkedAccounts(): List<BankLinkedAccount> =
        session
            .query(
                """
                SELECT id FROM accounts
                WHERE account_sync_source IN (${BankProvider.entries.joinToString { "?" }}) AND IFNULL(account_id, '') != ''
                    AND IFNULL(tombstone, 0) = 0 AND IFNULL(closed, 0) = 0
                ORDER BY sort_order, id
                """.trimIndent(),
                BankProvider.entries.map { it.syncSource },
            ).mapNotNull { row -> row.str("id")?.let { linkedAccount(it) } }

    /** Adds what one account's [fetch] brought that the account does not already hold; the number added. */
    fun store(
        account: BankLinkedAccount,
        fetch: BankFetch,
        nowMillis: Long,
        ids: () -> String,
    ): Int {
        val rows = ownIds(account, fetch.rows)
        val added = ImportBook(session, ids).confirm(account.accountId, rows, review = false).addedIds.size
        // Kept fresh on every sync, as Actual's own sync does, so the assistant can compare it with the register.
        fetch.balanceMinor?.let { SyncSchema.write(session, "accounts", account.accountId, "balance_current", it) }
        SyncSchema.write(session, "accounts", account.accountId, "last_sync", nowMillis.toString())
        return added
    }

    companion object {
        /**
         * The bank sync loop, with the budget reached only through [linked] and [store]. The fetches run between
         * them, so a caller that opens the budget (and takes its edit turn) inside each one leaves the budget free
         * while the banks answer, and a save made meanwhile goes through at once. Every SimpleFIN account is fetched
         * in the one request the first of them starts.
         */
        fun syncAll(
            transport: BankSyncTransport,
            serverAddress: String,
            token: String,
            today: LocalDate,
            linked: () -> List<BankLinkedAccount>,
            store: (BankLinkedAccount, BankFetch) -> Int,
            onAccount: (BankLinkedAccount) -> Unit = {},
            onFailure: (BankSyncFailure) -> Unit = {},
        ): BankSyncSummary {
            val accounts = linked()
            val startIso = startIso(today)
            val simpleFin = accounts.filter { it.syncSource == BankProvider.SIMPLEFIN.syncSource }
            var batch: Result<Map<String, BankFetch>>? = null
            var added = 0
            val failures = mutableListOf<BankSyncFailure>()

            fun fail(
                account: BankLinkedAccount,
                error: Exception,
            ) {
                val message = (error as? BankSyncException)?.message ?: STORE_FAILED
                val failure = BankSyncFailure(account.name, message, account.accountId, (error as? BankSyncException)?.fix)
                failures += failure
                onFailure(failure)
            }
            accounts.forEach { account ->
                onAccount(account)
                val fetched =
                    if (account in simpleFin) {
                        val all =
                            batch ?: runCatching { transport.fetchAll(serverAddress, token, simpleFin, startIso) }.also { batch = it }
                        all.map { it[account.accountId] }
                    } else {
                        runCatching { transport.fetchAll(serverAddress, token, listOf(account), startIso)[account.accountId] }
                    }
                val fetch =
                    fetched.getOrElse { error ->
                        fail(account, error as? BankSyncException ?: BankSyncException(ImportCopy.FETCH_FAILED))
                        return@forEach
                    } ?: BankFetch(emptyList(), null, BankSyncException(ImportCopy.FETCH_FAILED))
                fetch.error?.let { fail(account, it) }
                if (fetch.rows.isEmpty() && fetch.error != null) return@forEach
                // One account the budget could not take stops only that account, not the ones after it.
                try {
                    added += store(account, fetch)
                } catch (error: Exception) {
                    if (fetch.error == null) fail(account, error)
                }
            }
            return BankSyncSummary(accounts = accounts.size, added = added, failures = failures)
        }

        /** What an account reports when the budget could not take what its bank sent. */
        const val STORE_FAILED = "couldn't save what the bank sent"

        /** 89 days ago until today is 90 days, as far back as Actual asks, and as far as most banks go. */
        private fun startIso(today: LocalDate): String {
            val start = today.minusDays(89)
            return String.format("%04d-%02d-%02d", start.year, start.monthValue, start.dayOfMonth)
        }

        /** Gives a GoCardless row named by the bank's internal id the account's own id, as Actual names it. */
        private fun ownIds(
            account: BankLinkedAccount,
            rows: List<ParsedImportRow>,
        ): List<ParsedImportRow> =
            rows.map { row ->
                val id = row.financialId ?: return@map row
                if (!id.startsWith(HttpBankSyncTransport.INTERNAL_ID_PREFIX)) return@map row
                row.copy(financialId = "${account.accountId}-${id.removePrefix(HttpBankSyncTransport.INTERNAL_ID_PREFIX)}")
            }
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

/** One account the bank sync could not fetch, the short reason, and what would mend it. */
@kotlinx.serialization.Serializable
data class BankSyncFailure(
    val accountName: String,
    val message: String,
    val accountId: String = "",
    val fix: BankSyncFix? = null,
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
    const val TOKEN_CAPTION = "token"
    const val NEW_TOKEN = "new setup token"
    const val COUNTRY = "country code, like GB"
    const val FIND_BANKS = "find banks"
    const val BANKS = "banks"
    const val SIGN_IN = "sign in at your bank"
    const val SIGNED_IN = "I've signed in"
    const val SIGNED_IN_CAPTION = "done"
    const val PICK_ACCOUNT = "pick the bank account"
    const val NO_ACCOUNTS = "no accounts yet"
    const val LINKED = "linked to"
    const val REMOVE = "remove link"
    const val REMOVE_CAPTION = "remove"
    const val REMOVE_BODY = "this account stops syncing from the bank; its transactions stay"
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

    /**
     * Sends the SimpleFIN setup token to the server and keeps no copy. The access key claimed from an
     * earlier token is cleared first, as Actual's reset does, so the server claims this one.
     */
    fun saveSimpleFinToken(value: String) {
        call("secret", JsonObject(mapOf("name" to JsonPrimitive("simplefin_accessKey"), "value" to JsonNull)))
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
