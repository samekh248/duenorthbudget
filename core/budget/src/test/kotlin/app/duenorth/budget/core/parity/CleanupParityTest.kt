package app.duenorth.budget.core.parity

import app.duenorth.budget.core.CleanupEngine
import app.duenorth.budget.core.CleanupParser
import app.duenorth.budget.core.CleanupResult
import app.duenorth.budget.core.CleanupRow
import app.duenorth.budget.core.SqlSession
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** End-of-month cleanup against desktop Actual 26.8.1 (`parity/cleanup/`). */
class CleanupParityTest {
    private val october = YearMonth.of(2026, 10)

    @Test
    fun cleanupLinesReadAsDesktopReadsThem() {
        val fixture = ParityFixture.load("cleanup", "grammar")
        val problems = mutableListOf<String>()
        for (entry in fixture.value("parsed").jsonArray) {
            val obj = entry.jsonObject
            val line = obj.getValue("line").jsonPrimitive.content
            // Desktop records the stored group's name; a line names it in any case, with spaces around it.
            val want = (obj["def"] as? JsonArray)?.map { row(it.jsonObject) }?.map(::key)
            val got = CleanupParser.parseNote(line).takeIf { it.isNotEmpty() }?.map(::key)
            if (want != got) problems += "$line: desktop $want, phone $got"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun notesAreStoredWithTheirGroupsLikeDesktop() {
        val fixture = ParityFixture.load("cleanup", "store")
        on(fixture) { session ->
            session.transaction { CleanupEngine(session).store() }
            assertDefs(fixture, session)
            fixture.assertTable(session, "cleanup_groups", key = listOf("name"), ignore = setOf("id"))
        }
    }

    @Test
    fun cleanupMovesMoneyLikeDesktop() = cleanup("cleanup")

    @Test
    fun cleanupShortOfMoneyMovesItLikeDesktop() = cleanup("cleanup-short")

    private fun cleanup(name: String) {
        val fixture = ParityFixture.load("cleanup", name)
        on(fixture) { session ->
            var result: CleanupResult? = null
            session.transaction {
                if (name == "cleanup-short") {
                    app.duenorth.budget.core.SyncSchema.writeRow(
                        session,
                        "zero_budgets",
                        "202610-${fixture.id("categories", "Big")}",
                        "month" to 202610,
                        "category" to fixture.id("categories", "Big"),
                        "amount" to 480000,
                    )
                }
                result = CleanupEngine(session).run(october)
            }
            val want = fixture.value("result").jsonObject
            assertEquals(want.toString(), desktopWords(want), words(result!!))
            fixture.assertTable(session, "zero_budgets", where = { it["month"] == 202610L })
            assertDefs(fixture, session)
            assertEquals(
                fixture.long("2026-10", "toBudget"),
                app.duenorth.budget.core.ShellReader
                    .loadDetailed(session, october)
                    .figures.headerMinor,
            )
        }
    }

    /** Desktop's notice as message and details, to compare with the phone's result. */
    private fun desktopWords(result: JsonObject): Pair<String, String?> =
        result.getValue("message").jsonPrimitive.content to (result["pre"] as? JsonPrimitive)?.contentOrNull

    private fun words(result: CleanupResult): Pair<String, String?> =
        when (result) {
            is CleanupResult.Applied -> "cleanup-applied" to null
            CleanupResult.UpToDate -> "cleanup-up-to-date" to null
            is CleanupResult.NoFunds -> "cleanup-no-funds" to result.warnings.joinToString("\n\n")
            is CleanupResult.Rejected -> "rejected" to result.reason
        }

    /** Each category's `cleanup_def` with group ids read as names, since the phone makes its own ids for new groups. */
    private fun assertDefs(
        fixture: ParityFixture,
        session: SqlSession,
    ) {
        val wantGroups = fixture.expectedRows("cleanup_groups").associate { it["id"] to it["name"] }
        val gotGroups = session.query("SELECT id, name FROM cleanup_groups").associate { it.str("id") to it.str("name") }
        val want = fixture.expectedRows("categories").associate { it["name"] to named(it["cleanup_def"] as String?, wantGroups) }
        val got =
            session.query("SELECT name, cleanup_def FROM categories").associate {
                it.str("name") to
                    named(it.str("cleanup_def"), gotGroups)
            }
        for ((category, def) in want) assertEquals("$category cleanup_def", def, got[category])
    }

    private fun named(
        raw: String?,
        groups: Map<out Any?, Any?>,
    ): String? {
        val array = raw?.let { Json.parseToJsonElement(it) as JsonArray } ?: return null
        return JsonArray(
            array.map { element ->
                val obj = element.jsonObject
                JsonObject(
                    obj.mapValues { (key, value) ->
                        if (key == "groupId" && value is JsonPrimitive &&
                            value !is JsonNull
                        ) {
                            JsonPrimitive(groups[value.content]?.toString())
                        } else {
                            value
                        }
                    },
                )
            },
        ).toString()
    }

    private fun key(row: CleanupRow): CleanupRow =
        when (row) {
            is CleanupRow.Source -> row.copy(group = row.group?.trim()?.lowercase())
            is CleanupRow.Sink -> row.copy(group = row.group?.trim()?.lowercase())
            is CleanupRow.Overspend -> row.copy(group = row.group.trim().lowercase())
        }

    private fun row(obj: JsonObject): CleanupRow {
        val group = (obj["groupId"] as? JsonPrimitive)?.contentOrNull
        return when (obj.getValue("role").jsonPrimitive.content) {
            "source" -> CleanupRow.Source(group)
            "sink" ->
                CleanupRow.Sink(
                    group,
                    obj
                        .getValue("weight")
                        .jsonPrimitive.content
                        .toLong(),
                )
            else -> CleanupRow.Overspend(group.orEmpty())
        }
    }

    private fun on(
        fixture: ParityFixture,
        block: (SqlSession) -> Unit,
    ) {
        fixture.open().use { opened -> opened.db(block) }
    }
}
