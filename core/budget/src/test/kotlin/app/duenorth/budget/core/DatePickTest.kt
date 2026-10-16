package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Test

class DatePickTest {
    @Test
    fun columnsFollowEachDateFormat() {
        val month = DateColumn.MONTH
        val day = DateColumn.DAY
        val year = DateColumn.YEAR
        assertEquals(listOf(month, day, year), DatePick.columns("MM/dd/yyyy"))
        assertEquals(listOf(day, month, year), DatePick.columns("dd/MM/yyyy"))
        assertEquals(listOf(year, month, day), DatePick.columns("yyyy-MM-dd"))
        assertEquals(listOf(month, day, year), DatePick.columns("MM.dd.yyyy"))
        assertEquals(listOf(day, month, year), DatePick.columns("dd.MM.yyyy"))
    }

    @Test
    fun daysInHonoursLeapYears() {
        assertEquals(29, DatePick.daysIn(2024, 2))
        assertEquals(28, DatePick.daysIn(2026, 2))
        assertEquals(28, DatePick.daysIn(1900, 2))
        assertEquals(29, DatePick.daysIn(2000, 2))
        assertEquals(30, DatePick.daysIn(2026, 9))
        assertEquals(31, DatePick.daysIn(2026, 12))
    }

    @Test
    fun changingMonthOrYearClampsTheDay() {
        assertEquals(20260228, DatePick.withMonth(20260131, 2))
        assertEquals(20240229, DatePick.withMonth(20240131, 2))
        assertEquals(20250228, DatePick.withYear(20240229, 2025))
        assertEquals(20261031, DatePick.withDay(20261001, 31))
        assertEquals(20260930, DatePick.withDay(20260901, 31))
    }

    @Test
    fun yearRangeIncludesTheDate() {
        assertEquals(1900..2100, DatePick.yearRange(20261001))
        assertEquals(1850..2100, DatePick.yearRange(18500101))
        assertEquals(1900..2150, DatePick.yearRange(21500101))
    }

    @Test
    fun weekdayLeadsTheChosenFormat() {
        assertEquals("Thu 10/01/2026", DateFormats.withWeekday(20261001, "MM/dd/yyyy"))
        assertEquals("Thu 01.10.2026", DateFormats.withWeekday(20261001, "dd.MM.yyyy"))
        assertEquals("Wed 00-00-0000", DateFormats.widestSample("MM-dd-yyyy"))
    }

    @Test
    fun typedDatesAreReadInTheFormatsOrder() {
        assertEquals(20261007, DateFormats.parse("10/07/2026", "MM/dd/yyyy"))
        assertEquals(20261007, DateFormats.parse("07.10.2026", "dd.MM.yyyy"))
        assertEquals(20261007, DateFormats.parse("7/10/2026", "dd/MM/yyyy"))
        assertEquals(20261007, DateFormats.parse("2026-10-07", "dd.MM.yyyy"))
        assertEquals(null, DateFormats.parse("31.02.2026", "dd.MM.yyyy"))
        assertEquals("2026-10-07", DateFormats.toIso("07.10.2026", "dd.MM.yyyy"))
        assertEquals("soon", DateFormats.toIso("soon", "dd.MM.yyyy"))
    }
}
