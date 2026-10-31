package app.duenorth.budget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/** The template editor writes lines Actual reads, and leaves the rest of the note alone. */
class TemplateEditingTest {
    private fun n(text: String) = BigDecimal(text)

    private val december = YearMonth.of(2026, 12)

    @Test
    fun everyKindReadsBackAsItself() {
        val templates =
            listOf(
                Template.Simple(n("400"), null, 0),
                Template.Simple(n("400"), TemplateLimit(n("600")), 1),
                Template.Simple(null, TemplateLimit(n("600")), 2),
                Template.Simple(n("12.5"), null, 0),
                Template.By(n("1200"), december, annual = null, repeat = null, priority = 0),
                Template.By(n("1200"), december, annual = true, repeat = null, priority = 2),
                Template.By(n("1200"), december, annual = true, repeat = 2, priority = 0),
                Template.By(n("300"), december, annual = false, repeat = null, priority = 0),
                Template.By(n("300"), december, annual = false, repeat = 6, priority = 3),
                Template.Schedule("Rent", full = false, priority = 0),
                Template.Schedule("Car Insurance", full = true, priority = 1),
                Template.Percentage(n("10"), previous = false, category = "all income", priority = 0),
                Template.Percentage(n("7.5"), previous = true, category = "Paycheck", priority = 2),
                Template.Percentage(n("20"), previous = false, category = "available funds", priority = 0),
                Template.Average(3, 0),
                Template.Average(12, 4),
                Template.Remainder(n("1")),
                Template.Remainder(n("2")),
                Template.Goal(n("500")),
                // The newer forms: cycles, spending, copies, adjustments and every kind of cap.
                Template.Periodic(n("50"), 2, RepeatUnit.WEEK, LocalDate.of(2026, 10, 2), null, 1),
                Template.Periodic(n("50"), 1, RepeatUnit.DAY, LocalDate.of(2026, 10, 1), TemplateLimit(n("400")), 0),
                Template.Spend(n("900"), december, YearMonth.of(2026, 6), annual = null, repeat = null, priority = 1),
                Template.Spend(n("900"), december, YearMonth.of(2026, 6), annual = true, repeat = 2, priority = 0),
                Template.Copy(1, 2),
                Template.Copy(12, 0),
                Template.Schedule("Rent", full = false, priority = 0, adjustment = TemplateAdjustment(n("10"), percent = true)),
                Template.Schedule("Rent", full = true, priority = 1, adjustment = TemplateAdjustment(n("-25"), percent = false)),
                Template.Average(6, 0, TemplateAdjustment(n("-12.5"), percent = true)),
                Template.Simple(null, TemplateLimit(n("600"), hold = true), 0),
                Template.Simple(n("100"), TemplateLimit(n("15"), period = LimitPeriod.DAILY), 0),
                Template.Simple(null, TemplateLimit(n("80"), period = LimitPeriod.WEEKLY, start = LocalDate.of(2026, 10, 5)), 3),
                Template.Remainder(n("2"), TemplateLimit(n("300"))),
            )
        for (template in templates) {
            val line = TemplateLines.format(template)
            assertEquals(line, template, TemplateParser.parseLine(line))
        }
    }

    @Test
    fun everyTemplateHasAForm() {
        val templates =
            listOf(
                Template.Simple(n("400"), TemplateLimit(n("600"), hold = true, period = LimitPeriod.DAILY), 0) to TemplateKind.MONTHLY,
                Template.Simple(null, TemplateLimit(n("600")), 0) to TemplateKind.CAP,
                Template.Periodic(n("400"), 1, RepeatUnit.MONTH, LocalDate.of(2026, 10, 1), null, 0) to TemplateKind.MONTHLY,
                Template.Periodic(n("50"), 2, RepeatUnit.WEEK, LocalDate.of(2026, 10, 1), null, 0) to TemplateKind.REPEAT,
                Template.Spend(n("900"), december, YearMonth.of(2026, 6), null, null, 0) to TemplateKind.SPEND,
                Template.Copy(1, 0) to TemplateKind.COPY,
                Template.Limit(TemplateLimit(n("600"))) to TemplateKind.CAP,
                Template.Refill(0) to TemplateKind.REFILL,
                Template.Average(3, 0, TemplateAdjustment(n("10"), percent = true)) to TemplateKind.AVERAGE,
                Template.Remainder(n("1"), TemplateLimit(n("100"))) to TemplateKind.REMAINDER,
            )
        for ((template, kind) in templates) assertEquals("$template", kind, TemplateKind.of(template))
        // A refill alone has no note line, so only budget automations offer one.
        assertEquals(TemplateKind.entries - TemplateKind.REFILL, TemplateKind.offered(automations = false))
        assertEquals(TemplateKind.entries, TemplateKind.offered(automations = true))
    }

    @Test
    fun automationsStartAsDesktopsEditorWritesThem() {
        val october = YearMonth.of(2026, 10)
        assertEquals(
            Template.Periodic(BigDecimal.ZERO, 1, RepeatUnit.MONTH, october.atDay(1), null, 2),
            TemplateKind.MONTHLY.blank(2, october, "", automations = true),
        )
        assertEquals(Template.Limit(TemplateLimit(BigDecimal.ZERO)), TemplateKind.CAP.blank(2, october, "", automations = true))
        assertEquals(Template.Simple(null, TemplateLimit(BigDecimal.ZERO), 2), TemplateKind.CAP.blank(2, october, ""))
    }

    @Test
    fun linesUseActualWords() {
        assertEquals("#template 400 up to 600", TemplateLines.format(Template.Simple(n("400"), TemplateLimit(n("600")), 0)))
        assertEquals(
            "#template-2 1200 by 2026-12 repeat every year",
            TemplateLines.format(Template.By(n("1200"), december, true, null, 2)),
        )
        assertEquals("#template 10% of previous all income", TemplateLines.format(Template.Percentage(n("10"), true, "all income", 0)))
        assertEquals("#template remainder", TemplateLines.format(Template.Remainder(n("1"))))
        assertEquals("#goal 500", TemplateLines.format(Template.Goal(n("500.00"))))
    }

    @Test
    fun theRestOfTheNoteKeepsItsPlace() {
        val note = "groceries and takeout\n#template 400\n#template-1 10% of all income\nask about the coupon"
        val split = TemplateNote.parse(note)
        assertEquals("groceries and takeout\nask about the coupon", split.text)
        assertEquals(2, split.entries.size)
        assertEquals(
            "groceries and takeout\n#template 500\n#template-1 10% of all income\nask about the coupon",
            split.replace(0, Template.Simple(n("500"), null, 0)),
        )
        assertEquals("groceries and takeout\n#template-1 10% of all income\nask about the coupon", split.remove(0))
    }

    @Test
    fun addingToANoteWithoutTemplatesGoesAtTheEnd() {
        assertEquals("#template 50", TemplateNote.parse("").add(Template.Simple(n("50"), null, 0)))
        assertEquals("a note\n#template 50", TemplateNote.parse("a note\n").add(Template.Simple(n("50"), null, 0)))
    }

    @Test
    fun aLineThatDoesNotParseIsKept() {
        val split = TemplateNote.parse("#template fifty dollars\n#template 50")
        assertNull(split.entries[0].template)
        assertEquals("#template fifty dollars\n#template 60", split.replace(1, Template.Simple(n("60"), null, 0)))
    }

    @Test
    fun movingHandsOutTheSamePrioritiesInTheNewOrder() {
        val split = TemplateNote.parse("#template-1 400\n#goal 500\n#template-3 10% of all income")
        assertEquals("#template-1 10% of all income\n#template-3 400\n#goal 500", split.move(2, 0))
        // A line whose priority stays put keeps its own wording.
        assertEquals(
            "#goal 500\n#template \$400\n#template-1 50",
            TemplateNote.parse("#template \$400\n#goal 500\n#template-1 50").move(1, 0),
        )
    }

    @Test
    fun editingTheFreeTextKeepsTheTemplates() {
        val split = TemplateNote.parse("old words\n#template 400")
        assertEquals("new words\n#template 400", split.withText("new words"))
        assertEquals("#template 400", split.withText(""))
    }
}
