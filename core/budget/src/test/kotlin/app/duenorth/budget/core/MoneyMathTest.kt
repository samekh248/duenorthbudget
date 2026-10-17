package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyMathTest {
    private val commaDot = FormatOptions.NUMBER_COMMA_DOT

    private fun eval(
        text: String,
        base: Long? = null,
        format: String = commaDot,
        decimals: Int = 2,
    ) = MoneyMath.evaluate(text, format, decimals, base)

    @Test
    fun worksOutSums() {
        assertEquals(16_500L, eval("120+45"))
        assertEquals(15_000L, eval("300/2"))
        assertEquals(5_997L, eval("3x19.99"))
        assertEquals(5_997L, eval("3×19.99"))
        assertEquals(5_997L, eval("3*19.99"))
        assertEquals(20_000L, eval("(40+60)*2"))
        assertEquals(14_000L, eval("100+20*2"))
        assertEquals(-2_000L, eval("-50+30"))
        assertEquals(123_456L, eval("1,000+234.56"))
    }

    @Test
    fun roundsToTheCurrency() {
        assertEquals(3_333L, eval("100/3"))
        assertEquals(6_667L, eval("200÷3"))
        assertEquals(33L, eval("100/3", decimals = 0))
    }

    @Test
    fun leadingOperatorWorksOnThePreviousAmount() {
        assertEquals(70_000L, eval("+500", base = 20_000))
        assertEquals(15_000L, eval("-50", base = 20_000))
        assertEquals(40_000L, eval("*2", base = 20_000))
        assertEquals(10_000L, eval("/2", base = 20_000))
        assertEquals(20_000L, eval("x2", base = 10_000))
        // A plain amount replaces it.
        assertEquals(50_000L, eval("500", base = 20_000))
        assertEquals(30_000L, eval("+500", base = -20_000))
    }

    @Test
    fun readsThePersonsNumberFormat() {
        val dotComma = FormatOptions.NUMBER_DOT_COMMA
        assertEquals(123_450L, eval("1.000,50+234", format = dotComma))
        assertEquals(1_250L, eval("2,5*5", format = dotComma))
        assertEquals(1_250L, eval("2.5*5", format = dotComma))
        assertEquals(250_050L, eval("+0,50", base = 250_000, format = dotComma))
        assertEquals(200_000L, eval("1 000+1 000", format = FormatOptions.NUMBER_SPACE_DOT))
    }

    @Test
    fun rejectsWhatDoesNotWorkOut() {
        assertNull(eval("12++"))
        assertNull(eval("5/0"))
        assertNull(eval("(4+5"))
        assertNull(eval("abc+1"))
        assertNull(eval("1.2.3+1"))
    }

    @Test
    fun plainAmountsAndSignsAreNotSums() {
        assertFalse(MoneyMath.isMath("120"))
        assertFalse(MoneyMath.isMath("-120"))
        assertFalse(MoneyMath.isMath("1,234.56"))
        assertTrue(MoneyMath.isMath("-120", base = 1))
        assertTrue(MoneyMath.isMath("120-20"))
    }

    @Test
    fun canonicalReadsSumsAndLeavesPlainAmountsAsBefore() {
        assertEquals("165.00", MoneyParse.canonical("120+45", commaDot))
        assertEquals("700.00", MoneyParse.canonical("+500", commaDot, 2, base = 20_000))
        assertEquals("1234.56", MoneyParse.canonical("1,234.56", commaDot))
        assertEquals("-12", MoneyParse.canonical("-12", commaDot))
        assertEquals("12++", MoneyParse.canonical("12++", commaDot))
    }

    @Test
    fun transactionAmountTakesASum() {
        val result =
            RegisterEntry.transaction(
                id = "t",
                accountId = "a",
                dateText = "2026-10-17",
                payee = "Grocer",
                amountText = "12.50+7.50",
                decimals = 2,
                expense = true,
                categoryId = null,
                note = "",
            )
        assertEquals(-2_000L, (result as EntryResult.Ok).value.amountMinor)
    }
}
