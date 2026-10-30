package app.duenorth.budget.core

import app.duenorth.budget.core.parity.ParityFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.YearMonth

/** Categories desktop's budget automations own (`template_settings.source = ui`) run from `goal_def`, never the note. */
class TemplateAutomationsTest {
    private val fixture = ParityFixture.load("templates", "overwrite")
    private val october = YearMonth.of(2026, 10)
    private val ui = """{"source":"ui"}"""

    @Test
    fun aRunBudgetsFromGoalDefAndLeavesItAlone() {
        fixture.open().use { opened ->
            opened.db { session ->
                val fixed = fixture.id("categories", "Fixed")
                assertTrue(TemplateParser.isTemplateLine(CategoryNotes.read(session, fixed).lines().first { it.startsWith("#") }))
                val goalDef = """[{"type":"simple","monthly":123,"priority":0,"directive":"template"}]"""
                session.transaction {
                    SyncSchema.write(session, "categories", fixed, "goal_def", goalDef)
                    SyncSchema.write(session, "categories", fixed, "template_settings", ui)
                }
                val result = TemplateEngine(session, fixture.today).run(october, overwrite = true, confirmed = true)
                assertTrue("$result", result is TemplateResult.Applied)
                val row = session.query("SELECT goal_def, template_settings FROM categories WHERE id = ?", listOf(fixed)).first()
                assertEquals(goalDef, row.str("goal_def"))
                assertEquals(ui, row.str("template_settings"))
                val budget =
                    session
                        .query("SELECT amount, goal FROM zero_budgets WHERE month = ? AND category = ?", listOf(202610, fixed))
                        .first()
                assertEquals(12300L, budget.long("amount"))
                assertEquals(12300L, budget.long("goal"))
            }
        }
    }

    @Test
    fun aCategoryWithAnAutomationThePhoneCannotRunIsLeftExactlyAsItWas() {
        fixture.open().use { opened ->
            opened.db { session ->
                val fixed = fixture.id("categories", "Fixed")
                session.transaction {
                    SyncSchema.write(
                        session,
                        "categories",
                        fixed,
                        "goal_def",
                        """[{"type":"refill","priority":0,"directive":"template"}]""",
                    )
                    SyncSchema.write(session, "categories", fixed, "template_settings", ui)
                }
                val before = session.query("SELECT * FROM zero_budgets WHERE category = ?", listOf(fixed)).map { it.values }
                val engine = TemplateEngine(session, fixture.today)
                assertTrue(engine.run(october, overwrite = true, confirmed = true) is TemplateResult.Applied)
                assertEquals(before, session.query("SELECT * FROM zero_budgets WHERE category = ?", listOf(fixed)).map { it.values })
                assertFalse(engine.goals(october).containsKey(fixed))
                assertTrue(
                    engine
                        .plan(october)
                        .lanes
                        .flatMap { it.chips }
                        .none { it.categoryId == fixed },
                )
            }
        }
    }

    @Test
    fun notesCategoriesStillRunFromTheirNote() {
        fixture.open().use { opened ->
            opened.db { session ->
                TemplateEngine(session, fixture.today).run(october, overwrite = true, confirmed = true)
                fixture.assertTable(session, "categories")
            }
        }
    }

    @Test
    fun goalDefReadsBackEachForm() {
        val read =
            TemplateJson.parse(
                """
                [{"type":"simple","monthly":50,"limit":{"amount":200,"hold":false,"period":"monthly"},"priority":1,"directive":"template"},
                 {"type":"by","amount":1200,"month":"2027-06","annual":true,"priority":2,"directive":"template"},
                 {"type":"by","amount":300,"month":"2027-01","annual":false,"repeat":3,"priority":2,"directive":"template"},
                 {"type":"schedule","name":"Old name","scheduleId":"s1","full":true,"priority":3,"directive":"template"},
                 {"type":"percentage","percent":10,"previous":true,"category":"all income","priority":4,"directive":"template"},
                 {"type":"average","numMonths":3,"priority":5,"directive":"template"},
                 {"type":"remainder","weight":2,"priority":null,"directive":"template"},
                 {"type":"goal","amount":5000,"directive":"goal"}]
                """.trimIndent(),
            ) { id -> if (id == "s1") "Rent" else null }
        assertEquals(
            listOf(
                Template.Simple(BigDecimal("50"), BigDecimal("200"), 1),
                Template.By(BigDecimal("1200"), YearMonth.of(2027, 6), 12, annual = true, repeat = null, priority = 2),
                Template.By(BigDecimal("300"), YearMonth.of(2027, 1), 3, annual = false, repeat = 3, priority = 2),
                Template.Schedule("Rent", full = true, priority = 3),
                Template.Percentage(BigDecimal("10"), previous = true, category = "all income", priority = 4),
                Template.Average(3, 5),
                Template.Remainder(BigDecimal("2")),
                Template.Goal(BigDecimal("5000")),
            ),
            read,
        )
    }

    @Test
    fun newerFormsComeBackUnsupported() {
        val read =
            TemplateJson.parse(
                """
                [{"type":"periodic","amount":10,"period":{"period":"week","amount":1},"starting":"2026-10-01","priority":0,"directive":"template"},
                 {"type":"simple","limit":{"amount":200,"hold":true,"period":"monthly"},"priority":0,"directive":"template"},
                 {"type":"average","numMonths":3,"adjustment":10,"adjustmentType":"percent","priority":0,"directive":"template"},
                 {"type":"limit","amount":500,"hold":false,"period":"monthly","priority":null,"directive":"template"}]
                """.trimIndent(),
            )
        assertEquals(4, read.size)
        read.forEach { assertNull(it) }
        assertEquals(listOf(null), TemplateJson.parse("not json"))
        assertTrue(TemplateJson.parse(null).isEmpty())
        assertTrue(TemplateJson.managed(ui))
        assertFalse(TemplateJson.managed("""{"source":"notes"}"""))
        assertFalse(TemplateJson.managed(null))
    }

    @Test
    fun theCategoryPageReadsAutomationsOnlyForCategoriesTheyOwn() {
        fixture.open().use { opened ->
            val fixed = fixture.id("categories", "Fixed")
            assertNull(opened.library.readCategoryAutomations(opened.budgetId, fixed))
            opened.db { session ->
                session.transaction {
                    SyncSchema.write(
                        session,
                        "categories",
                        fixed,
                        "goal_def",
                        """[{"type":"simple","monthly":123,"priority":0,"directive":"template"},{"type":"copy","lookBack":3,"priority":1,"directive":"template"}]""",
                    )
                    SyncSchema.write(session, "categories", fixed, "template_settings", ui)
                }
            }
            val automations = opened.library.readCategoryAutomations(opened.budgetId, fixed)!!
            assertEquals(listOf("simple", "copy"), automations.entries.map { it.type })
            assertEquals(listOf(Template.Simple(BigDecimal("123"), null, 0)), automations.templates)
            assertFalse(automations.runnable)
        }
    }

    @Test
    fun theirChipsAreMarkedSoTheLanesLeaveThemAlone() {
        fixture.open().use { opened ->
            opened.db { session ->
                val fixed = fixture.id("categories", "Fixed")
                session.transaction {
                    SyncSchema.write(
                        session,
                        "categories",
                        fixed,
                        "goal_def",
                        """[{"type":"simple","monthly":123,"priority":0,"directive":"template"}]""",
                    )
                    SyncSchema.write(session, "categories", fixed, "template_settings", ui)
                }
                val chips = TemplateEngine(session, fixture.today).plan(october).lanes.flatMap { it.chips }
                assertTrue(chips.single { it.categoryId == fixed }.managed)
                assertTrue(chips.filter { it.categoryId != fixed }.none { it.managed })
            }
        }
    }

    @Test
    fun switchingToAutomationsKeepsTheNoteAndRunsTheSame() {
        fixture.open().use { opened ->
            val fixed = fixture.id("categories", "Fixed")
            val note = opened.library.readCategoryNote(opened.budgetId, fixed)
            val before = TemplateNote.parse(note).entries.mapNotNull { it.template }
            val automations = opened.library.migrateCategoryAutomations(opened.budgetId, fixed)!!
            assertEquals(before, automations.templates)
            assertEquals(note, opened.library.readCategoryNote(opened.budgetId, fixed))
            assertEquals(automations.templates, opened.library.readCategoryAutomations(opened.budgetId, fixed)!!.templates)
            opened.db { session ->
                TemplateEngine(session, fixture.today).run(october, overwrite = true, confirmed = true)
                fixture.assertTable(session, "zero_budgets", where = { it["month"] == 202610L })
            }
        }
    }

    @Test
    fun aNoteWithALineThatDoesNotReadStaysOnNotes() {
        fixture.open().use { opened ->
            val fixed = fixture.id("categories", "Fixed")
            opened.library.saveCategoryNote(opened.budgetId, fixed, "rent\n#template fifty dollars")
            assertNull(opened.library.migrateCategoryAutomations(opened.budgetId, fixed))
            assertNull(opened.library.readCategoryAutomations(opened.budgetId, fixed))
        }
    }

    @Test
    fun editsKeepAnAutomationThePhoneCannotRunAsDesktopWroteIt() {
        fixture.open().use { opened ->
            val fixed = fixture.id("categories", "Fixed")
            val refill = """{"type":"refill","priority":2,"directive":"template","description":"top up"}"""
            opened.db { session ->
                session.transaction {
                    SyncSchema.write(
                        session,
                        "categories",
                        fixed,
                        "goal_def",
                        """[{"type":"simple","monthly":123,"priority":0,"directive":"template"},$refill]""",
                    )
                    SyncSchema.write(session, "categories", fixed, "template_settings", ui)
                }
            }
            val automations = opened.library.readCategoryAutomations(opened.budgetId, fixed)!!
            val edited = automations.replace(0, Template.Simple(BigDecimal("150"), null, 0))
            assertTrue(opened.library.saveCategoryAutomations(opened.budgetId, fixed, edited))
            val goalDef =
                opened.db { session ->
                    session.query("SELECT goal_def FROM categories WHERE id = ?", listOf(fixed)).first().str("goal_def")
                }!!
            assertTrue(goalDef, goalDef.endsWith("$refill]"))
            assertTrue(goalDef, goalDef.contains("\"monthly\":150"))
            assertFalse(opened.library.unmigrateCategoryAutomations(opened.budgetId, fixed))
        }
    }

    @Test
    fun backToNotesWritesTheAutomationsIntoTheNote() {
        fixture.open().use { opened ->
            val fixed = fixture.id("categories", "Fixed")
            opened.library.saveCategoryNote(opened.budgetId, fixed, "rent\n#template 50")
            val automations = opened.library.migrateCategoryAutomations(opened.budgetId, fixed)!!
            opened.library.saveCategoryAutomations(opened.budgetId, fixed, automations.add(Template.Goal(BigDecimal("900"))))
            assertTrue(opened.library.unmigrateCategoryAutomations(opened.budgetId, fixed))
            assertEquals("rent\n#template 50\n#goal 900", opened.library.readCategoryNote(opened.budgetId, fixed))
            assertNull(opened.library.readCategoryAutomations(opened.budgetId, fixed))
        }
    }

    @Test
    fun movingAnAutomationHandsItTheEarlierPriority() {
        val automations =
            CategoryAutomations(
                listOf(
                    TemplateJson.Stored.of(Template.Simple(BigDecimal("1"), null, 1)),
                    TemplateJson.Stored.of(Template.Simple(BigDecimal("2"), null, 3)),
                ),
            )
        assertEquals(
            listOf(Template.Simple(BigDecimal("2"), null, 1), Template.Simple(BigDecimal("1"), null, 3)),
            automations.move(1, 0).templates,
        )
    }
}
