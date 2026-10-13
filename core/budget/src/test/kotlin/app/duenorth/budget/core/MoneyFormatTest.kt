package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyFormatTest {
    @Test
    fun dollarsAndZeroAndYen() {
        val usd = Currencies.byCode("USD")!!
        val yen = Currencies.byCode("JPY")!!
        assertEquals("$0.00", MoneyFormat.format(0, usd))
        assertEquals("$12.34", MoneyFormat.format(1_234, usd))
        assertEquals("-$12.34", MoneyFormat.format(-1_234, usd))
        assertEquals("$1,800.00", MoneyFormat.format(180_000, usd))
        assertEquals("¥1,500", MoneyFormat.format(1_500, yen))
        assertEquals("-¥40", MoneyFormat.format(-40, yen))
    }

    @Test
    fun hideFractionAndNumberFormatChangeNextPaintedAmount() {
        val usd = Currencies.byCode("USD")!!
        assertEquals(
            "$1,800",
            MoneyFormat.format(180_000, usd, FormatOptions(hideFraction = true)),
        )
        assertEquals(
            "$1.800,00",
            MoneyFormat.format(180_000, usd, FormatOptions(numberFormat = FormatOptions.NUMBER_DOT_COMMA)),
        )
        assertEquals(
            "$1 800.00",
            MoneyFormat.format(180_000, usd, FormatOptions(numberFormat = FormatOptions.NUMBER_SPACE_DOT)),
        )
        assertEquals(
            "$1800.00",
            MoneyFormat.format(180_000, usd, FormatOptions(numberFormat = FormatOptions.NUMBER_COMMA_DOT_NO_GROUP)),
        )
        assertEquals(
            "$1.800",
            MoneyFormat.format(
                180_000,
                usd,
                FormatOptions(hideFraction = true, numberFormat = FormatOptions.NUMBER_DOT_COMMA),
            ),
        )
    }

    @Test
    fun parseScalesByCurrencyDecimals() {
        assertEquals(2_500L, MoneyFormat.parse("25", 2))
        assertEquals(2_500L, MoneyFormat.parse("25.00", 2))
        assertEquals(2_550L, MoneyFormat.parse("25.5", 2))
        assertEquals(25L, MoneyFormat.parse("25", 0))
        assertEquals(null, MoneyFormat.parse("", 2))
        assertEquals(null, MoneyFormat.parse("25.555", 2))
    }
}
