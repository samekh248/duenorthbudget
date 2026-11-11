package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyInputTest {
    private val dotComma = FormatOptions.NUMBER_DOT_COMMA
    private val spaceDot = FormatOptions.NUMBER_SPACE_DOT
    private val noGroup = FormatOptions.NUMBER_COMMA_DOT_NO_GROUP

    @Test
    fun takesPlainAmounts() {
        listOf("", "0", "12", "12.", "12.5", "12.50", ".5", "1,234.56", "-40", "+500").forEach {
            assertTrue(it, MoneyInput.accepts(it))
        }
    }

    @Test
    fun takesTheSumsMoneyMathWorksOut() {
        listOf("120+45", "300/2", "3*19.99", "3×19.99", "10÷4", "(40+60)*2", "-50").forEach {
            assertTrue(it, MoneyInput.accepts(it))
        }
    }

    @Test
    fun refusesLettersSymbolsAndWhitespace() {
        listOf("12a", "$12", "12€", "abc", "1 2", "12\t", "3x19.99", "1e5", "12%", "12_000", "1'000").forEach {
            assertFalse(it, MoneyInput.accepts(it))
        }
    }

    @Test
    fun refusesMoreDecimalPlacesThanTheCurrencyHas() {
        assertFalse(MoneyInput.accepts("12.345"))
        assertFalse(MoneyInput.accepts("1.2.3"))
        assertFalse(MoneyInput.accepts("12.5,0"))
        assertTrue(MoneyInput.accepts("12.345", decimals = 3))
        assertFalse(MoneyInput.accepts("12.5", decimals = 0))
        assertTrue(MoneyInput.accepts("1,250", decimals = 0))
    }

    @Test
    fun limitsEachNumberInASumOnItsOwn() {
        assertTrue(MoneyInput.accepts("1.25+2.50"))
        assertFalse(MoneyInput.accepts("1.25+2.505"))
    }

    @Test
    fun readsTheDotCommaFormat() {
        assertTrue(MoneyInput.accepts("1.234,56", dotComma))
        assertTrue(MoneyInput.accepts("12,5", dotComma))
        // A keyboard with no comma key types "." for the decimal point.
        assertTrue(MoneyInput.accepts("12.50", dotComma))
        assertFalse(MoneyInput.accepts("1,234", dotComma))
        assertFalse(MoneyInput.accepts("1,2,3", dotComma))
        assertFalse(MoneyInput.accepts("1,23.4", dotComma))
    }

    @Test
    fun formatsWithoutACommaGroupTakeNoComma() {
        assertTrue(MoneyInput.accepts("1234.56", spaceDot))
        assertFalse(MoneyInput.accepts("1,234.56", spaceDot))
        assertFalse(MoneyInput.accepts("1 234.56", spaceDot))
        assertFalse(MoneyInput.accepts("1,234.56", noGroup))
    }

    @Test
    fun filterDropsWhitespaceTypedOrPasted() {
        assertEquals("12", MoneyInput.filter("1", "1 2"))
        assertEquals("1234.56", MoneyInput.filter("", " 1 234.56\n"))
        assertEquals("1234.56", MoneyInput.filter("", "1 234.56", spaceDot))
        assertEquals("1234.56", MoneyInput.filter("", "1 234.56", spaceDot))
        assertEquals("", MoneyInput.filter("", " "))
    }

    @Test
    fun filterKeepsThePreviousTextWhenTheTypedTextIsNotAnAmount() {
        assertEquals("12", MoneyInput.filter("12", "12a"))
        assertEquals("12.50", MoneyInput.filter("12.50", "12.505"))
        assertEquals("12.5", MoneyInput.filter("12.5", "12.5."))
        assertEquals("", MoneyInput.filter("", "$12.00"))
        assertEquals("12.34", MoneyInput.filter("", "12.34"))
    }

    @Test
    fun filterAlwaysLetsCharactersBeTakenOut() {
        // A box holding three decimal places from before still lets them be deleted.
        assertEquals("12.34", MoneyInput.filter("12.345", "12.34"))
        assertEquals("12.34", MoneyInput.filter("12.3456", "12.345").let { MoneyInput.filter(it, "12.34") })
        assertEquals("", MoneyInput.filter("12", ""))
    }
}
