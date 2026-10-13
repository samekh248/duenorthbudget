package app.duenorth.budget.core.parity

import app.duenorth.budget.core.BudgetClock
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.JdbcSessionOpener
import app.duenorth.budget.core.SqlSession
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.fail
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.util.zip.ZipInputStream

/**
 * One desktop-Actual golden fixture from `src/test/resources/parity/<area>/`
 * (spec 010 research R1). [open] installs the scenario's base budget on the
 * phone side; a test then runs the same action through a book and calls
 * [assertTable] to diff the rows against what Actual 26.8.1 wrote.
 */
class ParityFixture private constructor(
    val area: String,
    val scenario: String,
    private val expected: JsonObject,
) {
    val today: LocalDate = LocalDate.parse(expected.getValue("today").jsonPrimitive.content)
    val actualVersion: String = expected.getValue("actualVersion").jsonPrimitive.content
    val action: JsonElement = expected["action"] ?: JsonNull
    val values: JsonObject = expected["values"]?.jsonObject ?: JsonObject(emptyMap())
    private val baseName: String = expected.getValue("base").jsonPrimitive.content

    /** Id of a named entity in the fixture: kind is accounts, categories, groups, payees, or schedules. */
    fun id(
        kind: String,
        name: String,
    ): String =
        expected
            .getValue("ids")
            .jsonObject[kind]
            ?.jsonObject
            ?.get(name)
            ?.jsonPrimitive
            ?.contentOrNull
            ?: error("$area/$scenario has no $kind named '$name'")

    fun expectedRows(table: String): List<Map<String, Any?>> =
        expected
            .getValue("tables")
            .jsonObject[table]
            ?.jsonArray
            ?.map { row -> row.jsonObject.mapValues { (_, value) -> scalar(value) } }
            ?: error("$area/$scenario did not record table '$table'")

    /** Value at a path under `values`, e.g. `value("2026-10", "toBudget")`. */
    fun value(vararg path: String): JsonElement =
        path.fold(values as JsonElement) { node, key ->
            (node as? JsonObject)?.get(key) ?: error("$area/$scenario has no value at ${path.joinToString("/")}")
        }

    fun long(vararg path: String): Long =
        value(*path).jsonPrimitive.longOrNull ?: error("$area/$scenario value ${path.joinToString("/")} is not a number")

    /** Installs the base budget in a fresh library rooted in a temp directory, with the clock at [today]. */
    fun open(): Opened {
        val root = Files.createTempDirectory("parity-$area-$scenario").toFile()
        val library = BudgetLibrary(root, JdbcSessionOpener(), BudgetClock { today })
        val budgetId = "parity-$area-$scenario"
        library.installDownloaded(budgetId, "$area $scenario", sqliteFromZip(baseName))
        return Opened(library, budgetId, root)
    }

    /**
     * Diffs [table] on the phone against the fixture. Rows are matched by [key]
     * columns (use a natural key such as `name` for rows the phone creates with
     * its own ids). Only columns the fixture recorded are compared, minus [ignore].
     * [where] filters both sides, e.g. to compare one month.
     */
    fun assertTable(
        session: SqlSession,
        table: String,
        key: List<String> = listOf("id"),
        ignore: Set<String> = emptySet(),
        where: (Map<String, Any?>) -> Boolean = { true },
    ) {
        val want = expectedRows(table).filter(where)
        val columns = (want.flatMap { it.keys }.toSet() - ignore).sorted()
        val got =
            session
                .query("SELECT * FROM $table")
                .map { row -> row.values.mapValues { (_, value) -> normalize(value) } }
                .filter(where)
        val wantByKey = index(want, key, "fixture")
        val gotByKey = index(got, key, "phone")
        val problems = mutableListOf<String>()
        for ((k, row) in wantByKey) {
            val other = gotByKey[k]
            if (other == null) {
                problems += "missing row $k"
                continue
            }
            for (column in columns) {
                val a = row[column]
                val b = other[column]
                if (!sameValue(a, b)) problems += "row $k column $column: expected <$a> but was <$b>"
            }
        }
        for (k in gotByKey.keys - wantByKey.keys) problems += "unexpected row $k"
        if (problems.isNotEmpty()) {
            fail(
                "$area/$scenario table $table differs from desktop Actual $actualVersion:\n  " +
                    problems.joinToString("\n  "),
            )
        }
    }

    private fun index(
        rows: List<Map<String, Any?>>,
        key: List<String>,
        side: String,
    ): Map<List<Any?>, Map<String, Any?>> {
        val out = LinkedHashMap<List<Any?>, Map<String, Any?>>()
        for (row in rows) {
            val k = key.map { row[it] }
            if (out.put(k, row) != null) error("$area/$scenario: key $key is not unique on the $side side ($k)")
        }
        return out
    }

    private fun sqliteFromZip(name: String): ByteArray {
        val bytes = resource("$area/$name")
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "db.sqlite") return zip.readBytes()
            }
        }
        error("$area/$name has no db.sqlite")
    }

    class Opened(
        val library: BudgetLibrary,
        val budgetId: String,
        val root: File,
    ) : AutoCloseable {
        fun <T> db(block: (SqlSession) -> T): T = library.useDatabase(budgetId, block) ?: error("budget $budgetId has no database")

        override fun close() {
            root.deleteRecursively()
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun load(
            area: String,
            scenario: String,
        ): ParityFixture {
            val expected = json.parseToJsonElement(resource("$area/$scenario.json").toString(Charsets.UTF_8)).jsonObject
            return ParityFixture(area, scenario, expected)
        }

        /** Every scenario name in [area], sorted. */
        fun scenarios(area: String): List<String> =
            File(resourceDir(), area)
                .listFiles { file -> file.name.endsWith(".json") }
                .orEmpty()
                .map { it.name.removeSuffix(".json") }
                .sorted()

        fun areas(): List<String> =
            resourceDir()
                .listFiles { file -> file.isDirectory }
                .orEmpty()
                .map { it.name }
                .sorted()

        private fun resource(path: String): ByteArray =
            ParityFixture::class.java.getResourceAsStream("/parity/$path")?.use { it.readBytes() }
                ?: error("missing fixture resource parity/$path")

        private fun resourceDir(): File =
            File(
                ParityFixture::class.java.getResource("/parity")?.toURI()
                    ?: error("missing fixture directory parity/"),
            )

        private fun scalar(value: JsonElement): Any? =
            when (value) {
                JsonNull -> null
                is JsonPrimitive ->
                    when {
                        value.isString -> value.content
                        value.booleanOrNull != null -> if (value.booleanOrNull == true) 1L else 0L
                        value.longOrNull != null -> value.longOrNull
                        else -> value.content.toDoubleOrNull()
                    }
                is JsonObject, is JsonArray -> value.toString()
            }

        private fun normalize(value: Any?): Any? =
            when (value) {
                null -> null
                is Boolean -> if (value) 1L else 0L
                is Int -> value.toLong()
                is Short -> value.toLong()
                is Long -> value
                is Float -> value.toDouble()
                is Double -> if (value % 1.0 == 0.0) value.toLong() else value
                is ByteArray -> value.toString(Charsets.UTF_8)
                else -> value.toString()
            }

        private fun sameValue(
            a: Any?,
            b: Any?,
        ): Boolean {
            if (a == b) return true
            if (a is Number && b is Number) return a.toDouble() == b.toDouble()
            return false
        }
    }
}
