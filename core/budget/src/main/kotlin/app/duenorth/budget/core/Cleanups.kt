package app.duenorth.budget.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.time.YearMonth
import java.util.UUID
import kotlin.math.abs

/** One `#cleanup` line: the part a category plays when the month is cleaned up. */
sealed interface CleanupRow {
    /** Gives what it has left; [group] null for the whole budget. */
    data class Source(
        val group: String?,
    ) : CleanupRow

    /** Takes a [weight]ed share of what is gathered. */
    data class Sink(
        val group: String?,
        val weight: Long,
    ) : CleanupRow

    /** Has its overspending covered from its [group] first. */
    data class Overspend(
        val group: String,
    ) : CleanupRow
}

/** Port of Actual 26.8.1's `cleanup-template.pegjs`: `#cleanup source`, `#cleanup sink 2`, `#cleanup Fun source`. */
object CleanupParser {
    /** The note's cleanup lines, in order; a line desktop cannot read is left out. */
    fun parseNote(note: String): List<CleanupRow> =
        note.lines().mapNotNull { line ->
            val trimmed = line.trim()
            if (!trimmed.lowercase().startsWith("#cleanup ")) null else parseLine(trimmed)
        }

    /** One trimmed line; null when desktop's grammar refuses it, or it names an overspend group of nothing. */
    fun parseLine(line: String): CleanupRow? {
        if (!line.startsWith("#cleanup")) return null
        var at = "#cleanup".length
        val spaces = spaces(line, at)
        if (spaces == 0) return null
        at += spaces
        val row = inner(line, at) ?: return null
        if (row.second != line.length) return null
        return row.first.takeUnless { it is CleanupRow.Overspend && it.group.isEmpty() }
    }

    /** PEG ordered choice: the first alternative that matches wins, even if the line then has more. */
    private fun inner(
        text: String,
        start: Int,
    ): Pair<CleanupRow, Int>? {
        if (text.startsWith("source", start)) return CleanupRow.Source(null) to start + 6
        if (text.startsWith("sink", start)) {
            var at = start + 4
            at += spaces(text, at)
            val (weight, end) = weight(text, at)
            return CleanupRow.Sink(null, weight) to end
        }
        val sourceGroup = groupUntil(text, start, " source")
        run {
            var at = sourceGroup
            at += spaces(text, at)
            if (text.startsWith("source", at)) return CleanupRow.Source(text.substring(start, sourceGroup).ifEmpty { null }) to at + 6
        }
        val sinkGroup = groupUntil(text, start, " sink")
        run {
            var at = sinkGroup
            at += spaces(text, at)
            if (text.startsWith("sink", at)) {
                at += 4
                at += spaces(text, at)
                val (weight, end) = weight(text, at)
                return CleanupRow.Sink(text.substring(start, sinkGroup).ifEmpty { null }, weight) to end
            }
        }
        return CleanupRow.Overspend(text.substring(start, sourceGroup)) to sourceGroup
    }

    /** Where a group name ends: before [stop], or at the end of the line. */
    private fun groupUntil(
        text: String,
        start: Int,
        stop: String,
    ): Int {
        var at = start
        while (at < text.length && !text.startsWith(stop, at)) at++
        return at
    }

    private fun spaces(
        text: String,
        start: Int,
    ): Int {
        var at = start
        while (at < text.length && text[at] == ' ') at++
        return at - start
    }

    /** `+weight || 1`: digits, with none (or zero) meaning one. */
    private fun weight(
        text: String,
        start: Int,
    ): Pair<Long, Int> {
        var at = start
        while (at < text.length && text[at].isDigit()) at++
        val value = text.substring(start, at).toLongOrNull() ?: 0L
        return (if (value == 0L) 1L else value) to at
    }
}

/** What an end-of-month cleanup did, as desktop words its notice. */
sealed interface CleanupResult {
    /** Money moved from [sources] categories to [sinks]. */
    data class Applied(
        val sources: Int,
        val sinks: Int,
    ) : CleanupResult

    data object UpToDate : CleanupResult

    /** It ran, but some money could not move: [warnings] say why. */
    data class NoFunds(
        val warnings: List<String>,
    ) : CleanupResult

    data class Rejected(
        val reason: String,
    ) : CleanupResult
}

/**
 * Port of Actual 26.8.1's end-of-month cleanup (`budget/cleanup-goal-template`): `#cleanup` notes are stored as each
 * category's `cleanup_def`, then each named group's sources pay its overspent categories and sinks, sources across
 * the budget give what they have left, to budget covers the rest of the overspending, and what remains goes to the
 * sinks by weight. Only envelope budgets clean up.
 */
class CleanupEngine(
    private val session: SqlSession,
    private val ids: () -> String = { UUID.randomUUID().toString() },
) {
    private class Def(
        val role: String,
        val groupId: String?,
        val weight: Long,
    )

    private class Member(
        val category: String,
        val groupId: String,
        val weight: Long = 0,
    )

    private var loaded: ShellReader.LoadedDetailed? = null

    /** `budget/store-note-cleanups`: every notes category's cleanup lines as `cleanup_def`, groups made as named. */
    fun store() {
        val candidates =
            // Categories the budget automations own keep their cleanup in the automations editor.
            session
                .query(
                    """
                    SELECT c.id AS id, n.note AS note, c.template_settings AS settings FROM categories c
                    LEFT JOIN notes n ON n.id = c.id WHERE IFNULL(c.tombstone, 0) = 0 ORDER BY c.rowid
                    """.trimIndent(),
                ).filterNot { TemplateJson.managed(it.str("settings")) }
        val parsed = LinkedHashMap<String, List<CleanupRow>>()
        val names = LinkedHashSet<String>()
        for (row in candidates) {
            val note = row.str("note")?.takeIf { it.isNotEmpty() } ?: continue
            val rows = CleanupParser.parseNote(note)
            if (rows.isEmpty()) continue
            parsed[row.str("id").orEmpty()] = rows
            for (r in rows) {
                when (r) {
                    is CleanupRow.Source -> r.group
                    is CleanupRow.Sink -> r.group
                    is CleanupRow.Overspend -> r.group
                }?.let(names::add)
            }
        }
        val nameToId = names.associate { it.lowercase() to resolveGroup(it) }
        for (row in candidates) {
            val id = row.str("id").orEmpty()
            val rows = parsed[id]
            val def =
                rows?.let { list ->
                    JsonArray(
                        list.map { r ->
                            when (r) {
                                is CleanupRow.Source -> obj("role" to "source", "groupId" to r.group?.let { nameToId[it.lowercase()] })
                                is CleanupRow.Sink ->
                                    obj("role" to "sink", "groupId" to r.group?.let { nameToId[it.lowercase()] }, "weight" to r.weight)
                                is CleanupRow.Overspend -> obj("role" to "overspend", "groupId" to nameToId[r.group.lowercase()])
                            }
                        },
                    ).toString()
                }
            SyncSchema.write(session, "categories", id, "cleanup_def", def)
        }
        tombstoneOrphans()
    }

    /** `budget/cleanup-goal-template` for [month]. */
    fun run(month: YearMonth): CleanupResult {
        if (tracking()) return CleanupResult.Rejected(CleanupCopy.TRACKING)
        store()
        return process(month)
    }

    private fun process(month: YearMonth): CleanupResult {
        var sources = 0
        var sinks = 0
        var totalWeight = 0L
        val warnings = mutableListOf<String>()
        val categories =
            session
                .query(
                    "SELECT id, name, IFNULL(is_income, 0) AS is_income, cleanup_def FROM categories WHERE IFNULL(tombstone, 0) = 0 ORDER BY rowid",
                )
        val defs = categories.associate { it.str("id").orEmpty() to defs(it.str("cleanup_def")) }
        val groupSource = mutableListOf<Member>()
        val groupSink = mutableListOf<Member>()
        val groupOverspend = mutableListOf<Member>()
        for (category in categories) {
            val id = category.str("id").orEmpty()
            for (def in defs[id] ?: continue) {
                when {
                    def.role == "source" && def.groupId != null -> groupSource += Member(id, def.groupId)
                    def.role == "sink" && def.groupId != null -> groupSink += Member(id, def.groupId, def.weight)
                    def.role == "overspend" -> groupOverspend += Member(id, def.groupId.orEmpty())
                }
            }
        }
        val groupNames =
            session
                .query("SELECT id, name FROM cleanup_groups WHERE IFNULL(tombstone, 0) = 0")
                .associate { it.str("id").orEmpty() to it.str("name").orEmpty() }
        warnings += groups(month, groupSource, groupSink, groupOverspend, groupNames)

        val sinkCategories = mutableListOf<Pair<String, Long>>()
        for (category in categories) {
            val id = category.str("id").orEmpty()
            val def = defs[id] ?: continue
            if (def.any { it.role == "source" && it.groupId == null }) {
                val balance = leftover(month, id)
                val budgeted = budget(month, id)
                if (balance >= 0) {
                    setBudget(month, id, budgeted - balance)
                    setGoal(month, id, budgeted - balance)
                    sources++
                } else {
                    warnings += "${category.str("name").orEmpty()} does not have available funds."
                }
            }
            val sink = def.firstOrNull { it.role == "sink" && it.groupId == null }
            if (sink != null) {
                sinkCategories += id to sink.weight
                sinks++
                totalWeight += sink.weight
            }
        }
        // To budget covers overspending, except where it rolls over.
        for (category in categories) {
            val id = category.str("id").orEmpty()
            val available = toBudget(month)
            val balance = leftover(month, id)
            val budgeted = budget(month, id)
            if (balance >= 0 || category.bool("is_income") || carryover(month, id)) continue
            if (abs(balance) <= available) {
                setBudget(month, id, budgeted + abs(balance))
            } else {
                setBudget(month, id, budgeted + available)
            }
        }
        val available = toBudget(month)
        if (available < 0) warnings += "Global: No funds are available to reallocate."
        sinkCategories.forEachIndexed { n, (id, weight) ->
            val budgeted = budget(month, id)
            var amount = budgeted + jsRound(weight.toDouble() / totalWeight * available)
            if (n == sinkCategories.lastIndex) {
                val now = toBudget(month)
                if (amount > now) amount = budgeted + now
            }
            setBudget(month, id, amount)
        }
        return when {
            sources == 0 -> if (warnings.isNotEmpty()) CleanupResult.NoFunds(warnings) else CleanupResult.UpToDate
            warnings.isNotEmpty() -> CleanupResult.NoFunds(warnings)
            available == 0L -> CleanupResult.UpToDate
            else -> CleanupResult.Applied(sources, sinks)
        }
    }

    /** `applyGroupCleanups`: each named group's sources pay its overspent categories, then its sinks by weight. */
    private fun groups(
        month: YearMonth,
        sources: List<Member>,
        sinks: List<Member>,
        overspends: List<Member>,
        names: Map<String, String>,
    ): List<String> {
        val warnings = mutableListOf<String>()
        var remaining = sources
        while (remaining.isNotEmpty()) {
            val groupId = remaining.first().groupId
            val groupSources = remaining.filter { it.groupId == groupId }
            val groupSinks = sinks.filter { it.groupId == groupId }
            val groupOverspends = overspends.filter { it.groupId == groupId }
            if (groupSinks.isNotEmpty() || groupOverspends.isNotEmpty()) {
                var gathered = 0L
                for (source in groupSources) {
                    val balance = leftover(month, source.category)
                    setBudget(month, source.category, budget(month, source.category) - balance)
                    gathered += balance
                }
                val totalWeight = groupSinks.sumOf { it.weight }
                for (over in groupOverspends) {
                    if (gathered <= 0) break
                    val balance = leftover(month, over.category)
                    val budgeted = budget(month, over.category)
                    if (balance >= 0 || carryover(month, over.category)) continue
                    if (abs(balance) <= gathered) {
                        setBudget(month, over.category, budgeted + abs(balance))
                        gathered -= abs(balance)
                    } else {
                        setBudget(month, over.category, budgeted + gathered)
                        gathered = 0
                    }
                }
                for (sink in groupSinks) {
                    if (gathered <= 0) break
                    val amount = budget(month, sink.category) + jsRound(sink.weight.toDouble() / totalWeight * gathered)
                    setBudget(month, sink.category, amount)
                }
            } else {
                warnings += "Cleanup group \"${names[groupId] ?: groupId}\" has no matching sink categories."
            }
            remaining = remaining.filter { it.groupId != groupId }
        }
        return warnings
    }

    /** `resolveCleanupGroup`: the group of that name, brought back if it was removed, or a new one. */
    private fun resolveGroup(name: String): String {
        val trimmed = name.trim()
        val existing =
            session
                .query("SELECT id, tombstone FROM cleanup_groups WHERE lower(name) = ? LIMIT 1", listOf(trimmed.lowercase()))
                .firstOrNull()
        if (existing != null) {
            val id = existing.str("id").orEmpty()
            if (existing.bool("tombstone")) SyncSchema.write(session, "cleanup_groups", id, "tombstone", 0)
            return id
        }
        val id = ids()
        SyncSchema.writeRow(session, "cleanup_groups", id, "name" to trimmed, "tombstone" to 0)
        return id
    }

    private fun tombstoneOrphans() {
        val used =
            session
                .query("SELECT cleanup_def FROM categories WHERE IFNULL(tombstone, 0) = 0 AND cleanup_def IS NOT NULL")
                .flatMap { row -> defs(row.str("cleanup_def")).orEmpty().mapNotNull { it.groupId } }
                .toSet()
        for (row in session.query("SELECT id FROM cleanup_groups WHERE IFNULL(tombstone, 0) = 0")) {
            val id = row.str("id").orEmpty()
            if (id !in used) SyncSchema.write(session, "cleanup_groups", id, "tombstone", 1)
        }
    }

    /** `parseCleanupDef`: the stored rows, or null for none. */
    private fun defs(raw: String?): List<Def>? {
        val array = raw?.let { runCatching { Json.parseToJsonElement(it) as? JsonArray }.getOrNull() } ?: return null
        if (array.isEmpty()) return null
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            Def(
                role = (obj["role"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                groupId = (obj["groupId"] as? JsonPrimitive)?.contentOrNull,
                weight = (obj["weight"] as? JsonPrimitive)?.longOrNull ?: 0L,
            )
        }
    }

    private fun obj(vararg pairs: Pair<String, Any?>): JsonObject =
        JsonObject(
            linkedMapOf(
                *pairs
                    .map { (k, v) ->
                        k to
                            when (v) {
                                null -> JsonNull
                                is Number -> JsonPrimitive(v)
                                else -> JsonPrimitive(v.toString())
                            }
                    }.toTypedArray(),
            ),
        )

    private fun tracking(): Boolean =
        session.query("SELECT value FROM preferences WHERE id = 'budgetType'").firstOrNull()?.str("value") == "tracking"

    // The month's sheet, read again after each write as desktop's cells recompute.
    private fun sheet(month: YearMonth): ShellReader.LoadedDetailed =
        loaded ?: ShellReader.loadDetailed(session, month).also { loaded = it }

    private fun toBudget(month: YearMonth): Long = sheet(month).figures.headerMinor

    private fun leftover(
        month: YearMonth,
        id: String,
    ): Long = sheet(month).categories[id]?.availableMinor ?: 0L

    private fun budget(
        month: YearMonth,
        id: String,
    ): Long = sheet(month).categories[id]?.budgetedMinor ?: 0L

    private fun carryover(
        month: YearMonth,
        id: String,
    ): Boolean =
        session
            .query("SELECT carryover FROM zero_budgets WHERE month = ? AND category = ?", listOf(month.toActualMonth(), id))
            .firstOrNull()
            ?.bool("carryover") ?: false

    private fun row(
        month: YearMonth,
        id: String,
    ): String? =
        session
            .query("SELECT id FROM zero_budgets WHERE month = ? AND category = ?", listOf(month.toActualMonth(), id))
            .firstOrNull()
            ?.str("id")

    private fun setBudget(
        month: YearMonth,
        id: String,
        amount: Long,
    ) {
        loaded = null
        val existing = row(month, id)
        if (existing != null) {
            SyncSchema.write(session, "zero_budgets", existing, "amount", amount)
        } else {
            SyncSchema.writeRow(
                session,
                "zero_budgets",
                BudgetEdits.assignmentId(month.toActualMonth(), id),
                "month" to month.toActualMonth(),
                "category" to id,
                "amount" to amount,
            )
        }
    }

    private fun setGoal(
        month: YearMonth,
        id: String,
        goal: Long,
    ) {
        loaded = null
        val existing = row(month, id) ?: BudgetEdits.assignmentId(month.toActualMonth(), id)
        SyncSchema.writeRow(
            session,
            "zero_budgets",
            existing,
            "month" to month.toActualMonth(),
            "category" to id,
            "goal" to goal,
            "long_goal" to 0,
        )
    }
}

object CleanupCopy {
    const val ACTION = "end-of-month cleanup"
    const val TRACKING = "cleanup works on envelope budgets only"
    const val UP_TO_DATE = "nothing to clean up"
    const val NOTES = "cleaned up, with notes"

    fun applied(result: CleanupResult.Applied): String =
        "cleaned up: ${result.sources} ${if (result.sources == 1) "category" else "categories"} gave, " +
            "${result.sinks} ${if (result.sinks == 1) "category" else "categories"} took"
}
