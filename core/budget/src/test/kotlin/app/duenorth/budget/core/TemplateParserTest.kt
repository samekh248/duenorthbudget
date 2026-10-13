package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.YearMonth

/** Spec 010 T056: every form in `contracts/templates.md`, priorities, and bad lines. */
class TemplateParserTest {
    private fun parse(line: String): Template? = TemplateParser.parseLine(line)

    private fun n(text: String) = BigDecimal(text)

    @Test
    fun simpleAndUpTo() {
        assertEquals(Template.Simple(n("50"), null, 0), parse("#template 50"))
        assertEquals(Template.Simple(n("12.50"), null, 0), parse("#template \$12.50"))
        assertEquals(Template.Simple(null, n("200"), 0), parse("#template up to 200"))
        assertEquals(Template.Simple(n("50"), n("300"), 0), parse("#template 50 up to 300"))
        assertEquals(Template.Simple(n("1500"), null, 0), parse("#template 1,500"))
    }

    @Test
    fun byWithRepeats() {
        val december = YearMonth.of(2026, 12)
        assertEquals(Template.By(n("600"), december, null, annual = false, repeat = null, priority = 0), parse("#template 600 by 2026-12"))
        assertEquals(
            Template.By(n("600"), december, 12, annual = true, repeat = null, priority = 0),
            parse("#template 600 by 2026-12 repeat every year"),
        )
        assertEquals(
            Template.By(n("600"), december, 1, annual = false, repeat = 1, priority = 0),
            parse("#template 600 by 2026-12 repeat every month"),
        )
        assertEquals(
            Template.By(n("600"), december, 3, annual = false, repeat = 3, priority = 0),
            parse("#template 600 by 2026-12 repeat every 3 months"),
        )
        assertEquals(
            Template.By(n("600"), december, 24, annual = true, repeat = 2, priority = 0),
            parse("#template 600 by 2026-12 repeat every 2 years"),
        )
        assertNull(parse("#template 600 by 2026-12 repeat every 1 years"))
        assertNull(parse("#template 600 by December"))
    }

    @Test
    fun scheduleWithAndWithoutFull() {
        assertEquals(Template.Schedule("Rent", false, 0), parse("#template schedule Rent"))
        assertEquals(Template.Schedule("Car Loan", true, 0), parse("#template schedule full Car Loan"))
        assertEquals(Template.Schedule("Car Loan", true, 0), parse("#template schedule Car Loan full"))
        assertNull(parse("#template schedule"))
    }

    @Test
    fun percentageAverageRemainderGoal() {
        assertEquals(Template.Percentage(n("10"), false, "Salary", 0), parse("#template 10% of Salary"))
        assertEquals(Template.Percentage(n("5"), true, "all income", 0), parse("#template 5% of previous all income"))
        assertEquals(Template.Percentage(n("20"), false, "available funds", 0), parse("#template 20% of available funds"))
        assertEquals(Template.Average(3, 0), parse("#template average 3 months"))
        assertEquals(Template.Remainder(BigDecimal.ONE), parse("#template remainder"))
        assertEquals(Template.Remainder(n("2")), parse("#template remainder 2"))
        assertEquals(Template.Goal(n("500")), parse("#goal 500"))
    }

    @Test
    fun prioritiesComeFromTheSuffix() {
        assertEquals(2, parse("#template-2 75")?.priority)
        assertEquals(0, parse("#template 75")?.priority)
        assertNull(parse("#template remainder")?.priority)
        assertNull(parse("#goal 10")?.priority)
    }

    @Test
    fun badLinesComeBackAsTheirText() {
        val results = TemplateParser.parse("plain words\n#template fifty dollars\n#template 50\n#goal lots")
        assertEquals(3, results.size)
        assertEquals("#template fifty dollars", results[0].exceptionOrNull()?.message)
        assertTrue(results[1].isSuccess)
        assertEquals("#goal lots", results[2].exceptionOrNull()?.message)
        assertFalse(TemplateParser.isTemplateLine("templates are nice"))
        assertNull(parse("#template average months"))
        assertNull(parse("#template remainder a b"))
    }

    @Test
    fun definitionsMatchDesktopKeyOrder() {
        assertEquals(
            """{"type":"simple","monthly":50,"limit":null,"priority":0,"directive":"template"}""",
            parse("#template 50")!!.toJson().toString(),
        )
        assertEquals(
            """{"type":"remainder","priority":null,"directive":"template","weight":1,"limit":null}""",
            parse("#template remainder")!!.toJson().toString(),
        )
        assertEquals(
            """{"type":"goal","amount":500,"priority":null,"directive":"goal"}""",
            parse("#goal 500")!!.toJson().toString(),
        )
    }

    @Test
    fun errorsReadAsCategoryAndLine() {
        assertEquals("Fixed: #template fifty dollars", TemplateError("Fixed", "#template fifty dollars").toString())
    }
}
