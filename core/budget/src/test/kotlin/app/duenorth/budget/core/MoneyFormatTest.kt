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
}
