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
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-ACTUAL-TOKEN", token)
            connection.doOutput = true
            connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            val bytes = stream?.readBytes() ?: ByteArray(0)
            if (code != 200) throw BankSyncException(ImportCopy.FETCH_FAILED)
            val root = json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
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
        val payee =
            row.string("payee_name")
                ?: row.string("imported_payee")
                ?: row.string("payee")
                ?: return null
        val financialId = row.string("imported_id") ?: row.string("id")
        return ParsedImportRow(
            date = date,
            payee = payee,
            amountMinor = amount,
            financialId = financialId,
            importedPayee = row.string("imported_payee") ?: payee,
        )
    }

    private fun JsonObject.string(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.longAmount(name: String): Long? {
        val primitive = this[name]?.jsonPrimitive ?: return null
        primitive.contentOrNull?.toLongOrNull()?.let { return it }
        primitive.doubleOrNull?.let { return (it * 100).toLong() }
        return null
    }
}

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
        val import = ImportBook(session) { java.util.UUID.randomUUID().toString() }
        val currency =
            session
                .query("SELECT value FROM preferences WHERE id = ?", listOf("currency"))
                .firstOrNull()
                ?.str("value")
                .let { Currencies.byCode(it.orEmpty()) ?: Currencies.byCode("USD")!! }
        val preview = import.previewCandidates(accountId, currency, rows)
        return ImportPreviewBundle(preview, rows) to null
    }
}

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
