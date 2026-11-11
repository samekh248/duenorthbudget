package app.duenorth.budget.core

/**
 * What an amount box lets through as the person types or pastes. An amount box takes only an amount in the
 * person's number format, with the sums [MoneyMath] works out ("120+45", "(40+60)*2"):
 *
 * - digits, the format's decimal point and group separator, and the operators `+ - * / × ÷ ( )`;
 * - never whitespace: spaces, tabs, line breaks and no-break spaces are dropped, typed or pasted;
 * - no more than the currency's decimal places in any one number, and one decimal point per number;
 * - nothing else: a letter, a currency symbol or any other character leaves the box as it was.
 */
object MoneyInput {
    /** The operators [MoneyMath] reads, as symbols; the letter "x" is left out so a box takes no letters. */
    private const val OPERATORS = "+-*/×÷()"

    /**
     * The box's next text when [typed] replaces [previous]: [typed] without whitespace if that is an amount
     * the box takes, or else [previous] unchanged. Taking characters out is always let through, so a box that
     * somehow holds something it would not take can still be cleared.
     */
    fun filter(
        previous: String,
        typed: String,
        numberFormat: String = FormatOptions.NUMBER_COMMA_DOT,
        decimals: Int = 2,
    ): String {
        val text = typed.filterNot { it.isWhitespace() }
        if (accepts(text, numberFormat, decimals)) return text
        val deleting = typed.length < previous.length
        return if (deleting && text.all { allowedChar(it, numberFormat) }) text else previous
    }

    /** Whether [text], as typed so far, is an amount (or a sum of amounts) a box takes. Empty counts. */
    fun accepts(
        text: String,
        numberFormat: String = FormatOptions.NUMBER_COMMA_DOT,
        decimals: Int = 2,
    ): Boolean {
        if (!text.all { allowedChar(it, numberFormat) }) return false
        return numbers(text).all { number(it, numberFormat, decimals) }
    }

    private fun allowedChar(
        char: Char,
        numberFormat: String,
    ): Boolean = char in '0'..'9' || char in OPERATORS || char in separators(numberFormat)

    /** The separators a number may hold in [numberFormat]; whitespace never, so a space group separator is left out. */
    private fun separators(numberFormat: String): String =
        when (numberFormat) {
            // A keyboard without a comma key types "." for the decimal point, which MoneyParse reads.
            FormatOptions.NUMBER_DOT_COMMA -> ".,"
            FormatOptions.NUMBER_SPACE_DOT, FormatOptions.NUMBER_COMMA_DOT_NO_GROUP -> "."
            else -> ",."
        }

    private fun numbers(text: String): List<String> = text.split(*OPERATORS.toCharArray()).filter { it.isNotEmpty() }

    /** One number between operators, as typed so far: one decimal point at most, and no more than [decimals] after it. */
    private fun number(
        text: String,
        numberFormat: String,
        decimals: Int,
    ): Boolean {
        // In dot-comma a "." may be a group separator ("1.234"), so only the comma is the decimal point here.
        val point = if (numberFormat == FormatOptions.NUMBER_DOT_COMMA) ',' else '.'
        val points = text.count { it == point }
        if (points == 0) return true
        if (points > 1 || decimals == 0) return false
        val fraction = text.substringAfter(point)
        // Group separators belong to the whole part, never after the decimal point.
        return fraction.all { it in '0'..'9' } && fraction.length <= decimals
    }
}
