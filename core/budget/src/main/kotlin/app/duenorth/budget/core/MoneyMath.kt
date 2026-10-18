package app.duenorth.budget.core

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Simple arithmetic in amount boxes: "120+45", "300/2", "3x19.99", "(40+60)*2". Numbers are read in the
 * person's number format. Given the box's previous amount, a leading operator works on it, so "+500" adds
 * 500 to what was there and "-50" takes 50 off. "x", "×", "*" multiply; "÷", "/" divide.
 */
object MoneyMath {
    private const val OPERATORS = "+-*/x×÷()"

    /** Results past this many whole units are rejected rather than written. */
    private val LIMIT = BigDecimal("1000000000000")

    /**
     * Whether [text] is a sum to work out rather than a plain amount. A lone leading sign is a plain amount,
     * unless there is a [base] for it to act on.
     */
    fun isMath(
        text: String,
        base: Long? = null,
    ): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        if (base != null && trimmed.first() in OPERATORS && trimmed.first() != '(') return true
        val body = trimmed.removePrefix("+").removePrefix("-")
        return body.any { it in OPERATORS || it == 'X' }
    }

    /**
     * [text] worked out in minor units, or null when it does not read as a sum. With a [base], a leading
     * operator applies to it. A plain amount with no operator also comes back, so callers can use this for
     * any box.
     */
    fun evaluate(
        text: String,
        numberFormat: String,
        decimals: Int,
        base: Long? = null,
    ): Long? {
        val trimmed = text.trim().replace('X', 'x')
        if (trimmed.isEmpty()) return null
        val expression =
            if (base != null && trimmed.first() in OPERATORS && trimmed.first() != '(') {
                "(${MoneyParse.formatMagnitude(base, decimals).let { if (base < 0) "-$it" else it }})" + trimmed
            } else {
                trimmed
            }
        // The base is written with "." for its decimal point whatever the number format.
        val baseChunk = if (expression != trimmed) MoneyParse.formatMagnitude(base!!, decimals) else null
        val tokens = tokenize(expression, numberFormat, decimals, baseChunk) ?: return null
        val value = Parser(tokens).parse() ?: return null
        val rounded = value.setScale(decimals, RoundingMode.HALF_UP)
        if (rounded.abs() >= LIMIT) return null
        return rounded.movePointRight(decimals).longValueExact()
    }

    /** [text] worked out and written as "-1234.56", the form [MoneyParse.canonical] returns; null if it does not work out. */
    fun canonical(
        text: String,
        numberFormat: String,
        decimals: Int,
        base: Long? = null,
    ): String? {
        val minor = evaluate(text, numberFormat, decimals, base) ?: return null
        return (if (minor < 0) "-" else "") + MoneyParse.formatMagnitude(minor, decimals)
    }

    /** The decimal point a keypad shows in [numberFormat]. */
    fun decimalPoint(numberFormat: String): String = MoneyFormat.separators(numberFormat).second

    private sealed interface Token {
        data class Number(
            val value: BigDecimal,
        ) : Token

        data class Op(
            val symbol: Char,
        ) : Token
    }

    private fun tokenize(
        text: String,
        numberFormat: String,
        decimals: Int,
        baseChunk: String?,
    ): List<Token>? {
        val tokens = mutableListOf<Token>()
        val chunk = StringBuilder()
        var first = true

        fun flush(): Boolean {
            val raw = chunk.toString().trim()
            chunk.clear()
            if (raw.isEmpty()) return true
            // The base goes in already canonical; everything typed is read in the person's format.
            val plain = if (first && raw == baseChunk) raw else MoneyParse.canonical(raw, numberFormat, decimals)
            first = false
            if (plain.isEmpty() || plain.any { it != '.' && !it.isDigit() } || plain.count { it == '.' } > 1 || plain == ".") {
                return false
            }
            tokens += Token.Number(BigDecimal(if (plain.startsWith(".")) "0$plain" else plain))
            return true
        }
        for (c in text) {
            if (c in OPERATORS) {
                if (!flush()) return null
                tokens +=
                    Token.Op(
                        when (c) {
                            'x', '×' -> '*'
                            '÷' -> '/'
                            else -> c
                        },
                    )
            } else {
                chunk.append(c)
            }
        }
        if (!flush()) return null
        return tokens
    }

    /** expression := term (('+'|'-') term)*; term := factor (('*'|'/') factor)*; factor := ('+'|'-') factor | number | '(' expression ')'. */
    private class Parser(
        private val tokens: List<Token>,
    ) {
        private var at = 0

        fun parse(): BigDecimal? {
            val value = expression() ?: return null
            return if (at == tokens.size) value else null
        }

        private fun peek(): Char? = (tokens.getOrNull(at) as? Token.Op)?.symbol

        private fun expression(): BigDecimal? {
            var value = term() ?: return null
            while (peek() == '+' || peek() == '-') {
                val op = peek()
                at++
                val right = term() ?: return null
                value = if (op == '+') value + right else value - right
            }
            return value
        }

        private fun term(): BigDecimal? {
            var value = factor() ?: return null
            while (peek() == '*' || peek() == '/') {
                val op = peek()
                at++
                val right = factor() ?: return null
                value =
                    if (op == '*') {
                        value * right
                    } else {
                        if (right.signum() == 0) return null
                        value.divide(right, MathContext.DECIMAL64)
                    }
            }
            return value
        }

        private fun factor(): BigDecimal? {
            when (val token = tokens.getOrNull(at) ?: return null) {
                is Token.Number -> {
                    at++
                    return token.value
                }
                is Token.Op ->
                    when (token.symbol) {
                        '-' -> {
                            at++
                            return factor()?.negate()
                        }
                        '+' -> {
                            at++
                            return factor()
                        }
                        '(' -> {
                            at++
                            val inner = expression() ?: return null
                            if (peek() != ')') return null
                            at++
                            return inner
                        }
                        else -> return null
                    }
            }
        }
    }
}
