package app.duenorth.budget.core

import java.nio.charset.Charset

data class ParsedImportRow(
    val date: Int,
    val payee: String,
    val amountMinor: Long,
    val financialId: String? = null,
    val importedPayee: String? = null,
    val note: String? = null,
)

sealed interface ParseFileResult {
    data class Ok(
        val rows: List<ParsedImportRow>,
    ) : ParseFileResult

    data object Unreadable : ParseFileResult
}

object ImportFileParser {
    fun parse(
        bytes: ByteArray,
        fileName: String,
        decimals: Int,
    ): ParseFileResult {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".qif") -> parseQif(String(bytes, Charset.forName("ISO-8859-1")), decimals)
            lower.endsWith(".ofx") || lower.endsWith(".qfx") -> parseOfx(String(bytes, Charsets.UTF_8), decimals)
            lower.endsWith(".csv") -> parseCsv(String(bytes, Charsets.UTF_8), decimals)
            else -> {
                val text = String(bytes, Charsets.UTF_8)
                when {
                    text.contains("<STMTTRN>", ignoreCase = true) -> parseOfx(text, decimals)
                    text.trimStart().startsWith("!Type:", ignoreCase = true) -> parseQif(text, decimals)
                    text.contains(',') -> parseCsv(text, decimals)
                    else -> ParseFileResult.Unreadable
                }
            }
        }
    }

    private fun parseQif(
        text: String,
        decimals: Int,
    ): ParseFileResult {
        val rows = mutableListOf<ParsedImportRow>()
        var date: Int? = null
        var amount: Long? = null
        var payee: String? = null
        var note: String? = null
        fun flush() {
            val d = date
            val a = amount
            val p = payee?.trim().orEmpty()
            if (d != null && a != null && p.isNotEmpty()) {
                rows += ParsedImportRow(d, p, a, note = note?.trim()?.ifEmpty { null })
            }
            date = null
            amount = null
            payee = null
            note = null
        }
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line == "^") {
                flush()
                return@forEach
            }
            if (line.length < 2) return@forEach
            when (line[0]) {
                'D' -> date = parseQifDate(line.substring(1).trim())
                'T' -> amount = parseQifAmount(line.substring(1).trim(), decimals)
                'P' -> payee = line.substring(1).trim()
                'M' -> note = line.substring(1).trim()
            }
        }
        flush()
        return if (rows.isEmpty() && text.isNotBlank()) ParseFileResult.Unreadable else ParseFileResult.Ok(rows)
    }

    private fun parseQifDate(raw: String): Int? {
        val parts = raw.split('/', '-', '.').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size != 3) return RegisterEntry.parseDate(raw)
        val (a, b, c) = parts
        val nums = listOf(a, b, c).mapNotNull { it.toIntOrNull() }
        if (nums.size != 3) return null
        val (first, second, yearRaw) = nums
        val year = if (yearRaw < 100) 2000 + yearRaw else yearRaw
        val (month, day) =
            if (first > 12) {
                second to first
            } else {
                first to second
            }
        return RegisterEntry.parseDate(String.format("%04d-%02d-%02d", year, month, day))
    }

    private fun parseQifAmount(
        raw: String,
        decimals: Int,
    ): Long? {
        var cleaned = raw.trim()
        if (cleaned.startsWith('(') && cleaned.endsWith(')')) {
            cleaned = "-" + cleaned.substring(1, cleaned.length - 1)
        }
        cleaned = cleaned.replace(",", "")
        val negative = cleaned.startsWith('-')
        if (negative) cleaned = cleaned.drop(1)
        val magnitude = MoneyParse.parseMagnitude(cleaned, decimals) ?: return null
        return if (negative) -magnitude else magnitude
    }

    private fun parseOfx(
        text: String,
        decimals: Int,
    ): ParseFileResult {
        val rows = mutableListOf<ParsedImportRow>()
        val blocks = text.split("<STMTTRN>", "<stmttrn>").drop(1)
        for (block in blocks) {
            val chunk = block.substringBefore("</STMTTRN>").substringBefore("</stmttrn>")
            val dateRaw = tagValue(chunk, "DTPOSTED") ?: tagValue(chunk, "DTUSER") ?: continue
            val date = parseOfxDate(dateRaw) ?: continue
            val amountRaw = tagValue(chunk, "TRNAMT") ?: continue
            val amount = parseSignedAmount(amountRaw, decimals) ?: continue
            val payee =
                tagValue(chunk, "NAME")
                    ?: tagValue(chunk, "PAYEE")
                    ?: tagValue(chunk, "MEMO")
                    ?: continue
            val fitId = tagValue(chunk, "FITID")
            rows +=
                ParsedImportRow(
                    date = date,
                    payee = payee.trim(),
                    amountMinor = amount,
                    financialId = fitId?.trim()?.ifEmpty { null },
                    importedPayee = payee.trim(),
                )
        }
        return if (rows.isEmpty()) ParseFileResult.Unreadable else ParseFileResult.Ok(rows)
    }

    private fun tagValue(
        block: String,
        tag: String,
    ): String? {
        val pattern = Regex("""<$tag>([^<\r\n]+)""", RegexOption.IGNORE_CASE)
        return pattern.find(block)?.groupValues?.get(1)?.trim()
    }

    private fun parseOfxDate(raw: String): Int? {
        val digits = raw.filter { it.isDigit() }
        if (digits.length < 8) return null
        val y = digits.substring(0, 4).toIntOrNull() ?: return null
        val m = digits.substring(4, 6).toIntOrNull() ?: return null
        val d = digits.substring(6, 8).toIntOrNull() ?: return null
        return RegisterEntry.parseDate(String.format("%04d-%02d-%02d", y, m, d))
    }

    private fun parseCsv(
        text: String,
        decimals: Int,
    ): ParseFileResult {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.isEmpty()) return ParseFileResult.Unreadable
        val header = lines.first().split(',').map { it.trim().lowercase() }
        val dateIdx = header.indexOfFirst { it == "date" }
        val payeeIdx = header.indexOfFirst { it == "payee" }
        val amountIdx = header.indexOfFirst { it == "amount" }
        if (dateIdx < 0 || payeeIdx < 0 || amountIdx < 0) return ParseFileResult.Unreadable
        val rows = mutableListOf<ParsedImportRow>()
        for (line in lines.drop(1)) {
            val cols = splitCsvLine(line)
            if (cols.size <= maxOf(dateIdx, payeeIdx, amountIdx)) continue
            val date = RegisterEntry.parseDate(cols[dateIdx].trim()) ?: continue
            val payee = cols[payeeIdx].trim()
            if (payee.isEmpty()) continue
            val amount = parseSignedAmount(cols[amountIdx].trim(), decimals) ?: continue
            rows += ParsedImportRow(date, payee, amount)
        }
        return if (rows.isEmpty()) ParseFileResult.Unreadable else ParseFileResult.Ok(rows)
    }

    private fun splitCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    out += current.toString()
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        out += current.toString()
        return out
    }

    private fun parseSignedAmount(
        raw: String,
        decimals: Int,
    ): Long? {
        var cleaned = raw.trim()
        if (cleaned.startsWith('(') && cleaned.endsWith(')')) {
            cleaned = "-" + cleaned.substring(1, cleaned.length - 1)
        }
        val negative = cleaned.startsWith('-')
        if (negative) cleaned = cleaned.drop(1).trim()
        val magnitude = MoneyParse.parseMagnitude(cleaned.replace(",", ""), decimals) ?: return null
        return if (negative) -magnitude else magnitude
    }
}
