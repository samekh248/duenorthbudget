package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountFormatTest {
    @Test
    fun editTextUsesEachNumberFormat() {
        assertEquals("1,234.56", MoneyParse.editText(-123456, 2, FormatOptions(numberFormat = FormatOptions.NUMBER_COMMA_DOT)))
        assertEquals("1.234,56", MoneyParse.editText(123456, 2, FormatOptions(numberFormat = FormatOptions.NUMBER_DOT_COMMA)))
        assertEquals("1 234.56", MoneyParse.editText(123456, 2, FormatOptions(numberFormat = FormatOptions.NUMBER_SPACE_DOT)))
        assertEquals(
            "1234.56",
            MoneyParse.editText(123456, 2, FormatOptions(numberFormat = FormatOptions.NUMBER_COMMA_DOT_NO_GROUP)),
        )
        assertEquals("1,234", MoneyParse.editText(1234, 0, FormatOptions()))
    }

    @Test
    fun hideDecimalsNeverDropsCents() {
        assertEquals("1,234", MoneyParse.editText(123400, 2, FormatOptions(hideFraction = true)))
        assertEquals("1,234.05", MoneyParse.editText(123405, 2, FormatOptions(hideFraction = true)))
    }

    @Test
    fun parseReadsEachNumberFormat() {
        assertEquals(123456L, MoneyParse.parseMagnitude("1,234.56", 2, FormatOptions.NUMBER_COMMA_DOT))
        assertEquals(123456L, MoneyParse.parseMagnitude("1.234,56", 2, FormatOptions.NUMBER_DOT_COMMA))
        assertEquals(123456L, MoneyParse.parseMagnitude("1 234.56", 2, FormatOptions.NUMBER_SPACE_DOT))
        assertEquals(123456L, MoneyParse.parseMagnitude("1 234.56", 2, FormatOptions.NUMBER_SPACE_DOT))
        assertEquals(123456L, MoneyParse.parseMagnitude("1234.56", 2, FormatOptions.NUMBER_COMMA_DOT_NO_GROUP))
        assertEquals(123400L, MoneyParse.parseMagnitude("1.234", 2, FormatOptions.NUMBER_DOT_COMMA))
        assertNull(MoneyParse.parseMagnitude("1,2,3", 2, FormatOptions.NUMBER_DOT_COMMA))
    }

    @Test
    fun dotCommaTakesAKeyboardDotAsTheDecimalPoint() {
        assertEquals(1250L, MoneyParse.parseMagnitude("12.50", 2, FormatOptions.NUMBER_DOT_COMMA))
        assertEquals(1250L, MoneyParse.parseMagnitude("12.5", 2, FormatOptions.NUMBER_DOT_COMMA))
        assertEquals(1250L, MoneyParse.parseMagnitude("12,5", 2, FormatOptions.NUMBER_DOT_COMMA))
    }

    @Test
    fun editTextRoundTripsThroughParse() {
        listOf(
            FormatOptions.NUMBER_COMMA_DOT,
            FormatOptions.NUMBER_DOT_COMMA,
            FormatOptions.NUMBER_SPACE_DOT,
            FormatOptions.NUMBER_COMMA_DOT_NO_GROUP,
        ).forEach { format ->
            listOf(0L, 5L, 123456L, 100000000L, 987654321L).forEach { minor ->
                val text = MoneyParse.editText(minor, 2, FormatOptions(numberFormat = format))
                assertEquals("$format $text", minor, MoneyParse.parseMagnitude(text, 2, format))
            }
        }
    }

    @Test
    fun transactionReadsTheAmountInTheChosenFormat() {
        val result =
            RegisterEntry.transaction(
                id = "t",
                accountId = "a",
                date = 20261001,
                payee = "Shop",
                amountText = "1.234,56",
                decimals = 2,
                expense = true,
                categoryId = null,
                note = "",
                numberFormat = FormatOptions.NUMBER_DOT_COMMA,
            )
        assertEquals(-123456L, (result as EntryResult.Ok).value.amountMinor)
    }

    @Test
    fun canonicalRewritesTypedAmountsForTheBudgetCode() {
        assertEquals("-1234.5", MoneyParse.canonical("-1.234,5", FormatOptions.NUMBER_DOT_COMMA))
        assertEquals("1234.56", MoneyParse.canonical("1 234.56", FormatOptions.NUMBER_SPACE_DOT))
        assertEquals("1234.56", MoneyParse.canonical("1,234.56", FormatOptions.NUMBER_COMMA_DOT))
        assertEquals("12.50", MoneyParse.canonical("12.50", FormatOptions.NUMBER_DOT_COMMA))
        assertEquals("abc", MoneyParse.canonical("abc", FormatOptions.NUMBER_DOT_COMMA))
    }
}
