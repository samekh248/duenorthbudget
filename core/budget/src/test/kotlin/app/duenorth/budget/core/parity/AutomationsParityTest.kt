package app.duenorth.budget.core.parity

import app.duenorth.budget.core.CategoryNotes
import app.duenorth.budget.core.SqlSession
import app.duenorth.budget.core.TemplateEngine
import app.duenorth.budget.core.TemplateJson
import app.duenorth.budget.core.TemplateLines
import app.duenorth.budget.core.TemplateParser
import app.duenorth.budget.core.TemplateResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** Budget automations and the newer template forms against desktop Actual 26.8.1 (`parity/automations/`). */
class AutomationsParityTest {
    private val october = YearMonth.of(2026, 10)

    @Test
    fun noteLinesReadAsDesktopReadsThem() {
        val fixture = ParityFixture.load("automations", "grammar")
        val problems = mutableListOf<String>()
        for (entry in fixture.value("parsed").jsonArray) {
            val obj = entry.jsonObject
            val line = obj.getValue("line").jsonPrimitive.content
            val goalDef = (obj["goal_def"] as? JsonNull)?.let { null } ?: obj["goal_def"]?.jsonPrimitive?.contentOrNull
            val parsed = TemplateParser.parseLine(line)
            when {
                goalDef == null ->
                    if (TemplateParser.isTemplateLine(line)) problems += "$line: desktop skips it, the phone reads it as a template line"
                goalDef.contains("\"directive\":\"error\"") ->
                    if (parsed != null) problems += "$line: desktop refuses it, the phone read $parsed"
                else -> {
                    val got = parsed?.let { JsonArray(listOf(it.toJson())).toString() }
                    if (got != goalDef) problems += "$line:\n    desktop $goalDef\n    phone   $got"
                }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun automationsGoBackToNotesInDesktopWords() {
        val fixture = ParityFixture.load("automations", "grammar")
        val lists = fixture.value("rendered").jsonObject
        val render = (fixture.action as JsonObject).getValue("render").jsonObject
        for ((name, list) in render) {
            val templates = TemplateJson.parse(list.toString())
            assertTrue(name, templates.none { it == null })
            assertEquals(
                name,
                lists.getValue(name).jsonPrimitive.content,
                TemplateLines.unparse(templates.filterNotNull()).joinToString("\n"),
            )
        }
    }

    @Test
    fun overwriteBudgetsEveryKindLikeDesktop() {
        val fixture = ParityFixture.load("automations", "overwrite")
        on(fixture) { session ->
            val late = fixture.id("categories", "Late Priority")
            session.transaction {
                app.duenorth.budget.core.SyncSchema.write(
                    session,
                    "categories",
                    late,
                    "goal_def",
                    """[{"type":"simple","monthly":29000,"priority":5,"directive":"template"}]""",
                )
            }
            val result = TemplateEngine(session, fixture.today).run(october, overwrite = true, confirmed = true)
            assertEquals(TemplateResult.Applied(fixture.long("result", "count").toInt()), result)
            fixture.assertTable(session, "zero_budgets", where = { it["month"] == 202610L })
            fixture.assertTable(session, "categories")
            assertEquals(fixture.long("2026-10", "toBudget"), toBudget(session))
        }
    }

    @Test
    fun hiddenFractionsRoundToWholeAmountsLikeDesktop() {
        val fixture = ParityFixture.load("automations", "overwrite-whole")
        fixture.open().use { opened ->
            opened.library.setPreference(opened.budgetId, "hideFraction", "true")
            opened.db { session -> whole(fixture, session) }
        }
    }

    private fun whole(
        fixture: ParityFixture,
        session: SqlSession,
    ) {
        run {
            val result = TemplateEngine(session, fixture.today).run(october, overwrite = true, confirmed = true)
            assertTrue("$result", result is TemplateResult.Applied)
            fixture.assertTable(session, "zero_budgets", where = { it["month"] == 202610L })
        }
    }

    @Test
    fun eachAutomationsProjectionMatchesDesktopsDryRun() {
        val fixture = ParityFixture.load("automations", "dry-run")
        on(fixture) { session ->
            val engine = TemplateEngine(session, fixture.today)
            // The note categories' goal_def is written by a run first, as the fixture did.
            val problems = mutableListOf<String>()
            for ((name, run) in fixture.value("dryRun").jsonObject) {
                val id = fixture.id("categories", name)
                val templates =
                    CategoryNotes.automations(session, id)?.templates
                        ?: TemplateParser.parse(CategoryNotes.read(session, id)).map { it.getOrThrow() }
                val want =
                    run.jsonObject
                        .getValue("perTemplate")
                        .jsonArray
                        .map { it.jsonPrimitive.longOrNull }
                val got = engine.preview(id, october, templates)
                got.forEachIndexed { index, amount ->
                    if (amount != null && amount != want[index]) problems += "$name #$index: desktop ${want[index]}, phone $amount"
                }
            }
            assertTrue(problems.joinToString("\n"), problems.isEmpty())
        }
    }

    @Test
    fun aRefillAloneHasNoNoteLine() {
        assertFalse(
            TemplateLines.writable(
                listOf(
                    app.duenorth.budget.core.Template
                        .Refill(0),
                ),
            ),
        )
        assertNull(TemplateParser.parseLine("#template refill"))
    }

    private fun toBudget(session: SqlSession): Long =
        app.duenorth.budget.core.ShellReader
            .loadDetailed(session, october)
            .figures.headerMinor

    private fun on(
        fixture: ParityFixture,
        block: (SqlSession) -> Unit,
    ) {
        fixture.open().use { opened -> opened.db(block) }
    }
}
