package app.duenorth.budget.core

object MoneyParse {
    /**
     * A typed amount read with the Settings [numberFormat]: its group separator is ignored and its decimal
     * separator ends the whole part. The default reads "1,234.56", as before format settings reached the forms.
     */
    fun parseMagnitude(
        text: String,
        decimals: Int,
        numberFormat: String = FormatOptions.NUMBER_COMMA_DOT,
    ): Long? {
        val body =
            normalize(text.trim(), decimals, numberFormat)
                .removePrefix("+")
                .removePrefix("-")
        if (body.isEmpty()) return null
        if (body.any { it != '.' && !it.isDigit() }) return null
        if (body.count { it == '.' } > 1) return null
        val parts = body.split('.')
        if (decimals == 0) {
            if (parts.size > 1) return null
            return parts[0].toLongOrNull()
        }
        val whole = parts[0].ifEmpty { "0" }.toLongOrNull() ?: return null
        if (parts.size == 1) return whole * scale(decimals)
        if (parts[1].length > decimals) return null
        val fraction = parts[1].padEnd(decimals, '0').ifEmpty { "0" }.toLongOrNull() ?: return null
        return whole * scale(decimals) + fraction
    }

    fun formatMagnitude(
        minor: Long,
        decimals: Int,
    ): String {
        val absolute = if (minor == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(minor)
        if (decimals == 0) return absolute.toString()
        val scale = scale(decimals)
        val whole = absolute / scale
        val fraction = (absolute % scale).toString().padStart(decimals, '0')
        return "$whole.$fraction"
    }

    /**
     * An amount as it starts out in a box: the format's separators, no symbol, no sign. With hide decimals on,
     * the fraction is left off only when it is zero, so saving never drops cents the person cannot see.
     */
    fun editText(
        minor: Long,
        decimals: Int,
        options: FormatOptions = FormatOptions(),
    ): String {
        val absolute = if (minor == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(minor)
        val (groupSep, decimalSep) = MoneyFormat.separators(options.numberFormat)
        val scale = scale(decimals)
        val whole = MoneyFormat.group(absolute / scale, groupSep)
        val fraction = absolute % scale
        if (decimals == 0 || (options.hideFraction && fraction == 0L)) return whole
        return whole + decimalSep + fraction.toString().padStart(decimals, '0')
    }

    /**
     * A typed amount in [numberFormat] rewritten with no group separators and "." for the decimal point
     * ("-1.234,5" becomes "-1234.5"), the form the budget and sync code reads. The caller still checks it.
     * [decimals] only decides whether a lone "." in the dot-comma format is a decimal point.
     */
    fun canonical(
        text: String,
        numberFormat: String,
        decimals: Int = 2,
        base: Long? = null,
    ): String =
        // A sum ("120+45", or "+500" on top of [base]) comes back worked out; one that does not work out is
        // left for the checks to reject.
        if (MoneyMath.isMath(text, base)) {
            MoneyMath.canonical(text, numberFormat, decimals, base) ?: text.trim()
        } else {
            normalize(text.trim(), decimals, numberFormat)
        }

    /** [text] rewritten as plain digits with "." for the decimal point, or left for the checks to reject. */
    private fun normalize(
        text: String,
        decimals: Int,
        numberFormat: String,
    ): String =
        when (numberFormat) {
            FormatOptions.NUMBER_DOT_COMMA ->
                if (dotIsDecimal(text, decimals)) text else text.replace(".", "").replace(",", ".")
            FormatOptions.NUMBER_SPACE_DOT -> text.filterNot { it == ' ' || it == '\u00A0' || it == '\u202F' }
            else -> text.replace(",", "")
        }

    /**
     * In "1.234,56" a lone "." followed by fewer than three digits cannot be a group separator, so it is the
     * decimal point a keyboard without a comma key typed. "12.50" reads as twelve and a half, not 1250.
     */
    private fun dotIsDecimal(
        text: String,
        decimals: Int,
    ): Boolean {
        if (',' in text || text.count { it == '.' } != 1) return false
        val after = text.substringAfter('.').length
        return after in 1..decimals && after < 3
    }

    private fun scale(decimals: Int): Long {
        var value = 1L
        repeat(decimals) { value *= 10 }
        return value
    }
}
