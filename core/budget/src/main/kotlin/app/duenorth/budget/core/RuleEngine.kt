package app.duenorth.budget.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToLong

object ScheduleCopy {
    const val DUPLICATE = "a matching transaction is already in this account"
    const val NO_SCHEDULE = "no schedule"
    const val SCHEDULE_DONE = "schedule is finished"
}

private val ruleJson =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

data class RuleRecord(
    val id: String,
    val stage: String?,
    val conditionsOp: String,
    val conditions: JsonArray,
    val actions: JsonArray,
)

data class RuleTransactionView(
    var account: String,
    var payee: String?,
    var payeeName: String,
    var category: String?,
    var amount: Long,
    var notes: String?,
    var dateIso: String,
    var schedule: String? = null,
    var cleared: Boolean? = null,
)

class RulesBook(
    private val session: SqlSession,
    private val ids: () -> String = {
        java.util.UUID
            .randomUUID()
            .toString()
    },
) {
    fun isEnabled(ruleId: String): Boolean = !disabledIds().contains(ruleId)

    fun setEnabled(
        ruleId: String,
        enabled: Boolean,
    ) {
        val disabled = disabledIds().toMutableSet()
        if (enabled) {
            disabled.remove(ruleId)
        } else {
            disabled.add(ruleId)
        }
        persistDisabled(disabled)
    }

    fun loadRules(): List<RuleRecord> =
        session
            .query(
                """
                SELECT id, stage, conditions_op, conditions, actions
                FROM rules
                WHERE IFNULL(tombstone, 0) = 0
                  AND conditions IS NOT NULL
                  AND actions IS NOT NULL
                """.trimIndent(),
            ).mapNotNull { row ->
                val conditions = row.str("conditions") ?: return@mapNotNull null
                val actions = row.str("actions") ?: return@mapNotNull null
                RuleRecord(
                    id = row.str("id") ?: return@mapNotNull null,
                    stage = row.str("stage"),
                    conditionsOp = row.str("conditions_op") ?: "and",
                    conditions = ruleJson.parseToJsonElement(conditions).jsonArray,
                    actions = ruleJson.parseToJsonElement(actions).jsonArray,
                )
            }

    fun applyRules(
        view: RuleTransactionView,
        scheduleRuleId: String? = null,
    ): RuleTransactionView {
        val disabled = disabledIds()
        val scheduleRuleIds =
            session
                .query(
                    """
                    SELECT rule FROM schedules
                    WHERE IFNULL(tombstone, 0) = 0
                    """.trimIndent(),
                ).mapNotNull { it.str("rule") }
                .toSet()
        val rules =
            RuleCache.ranked(session) { rankRules(loadRules().filter { !isScheduleOnlyRule(it) }) }.filter { it.id !in disabled }
        var current = view
        for (rule in rules) {
            if (scheduleRuleId != null) {
                when {
                    rule.id == scheduleRuleId -> {
                        current = applyActions(rule, current, skipConditions = true)
                    }
                    rule.id in scheduleRuleIds -> continue
                    else -> {
                        if (conditionsMatch(rule, current)) {
                            current = applyActions(rule, current, skipConditions = false)
                        }
                    }
                }
            } else {
                if (conditionsMatch(rule, current)) {
                    current = applyActions(rule, current, skipConditions = false)
                }
            }
        }
        return current
    }

    fun insertRule(
        stage: String?,
        conditions: JsonArray,
        actions: JsonArray,
        id: String = ids(),
    ): String {
        SyncSchema.writeRow(
            session,
            "rules",
            id,
            "stage" to stage,
            "conditions_op" to "and",
            "conditions" to conditions.toString(),
            "actions" to actions.toString(),
            "tombstone" to 0,
        )
        return id
    }

    fun delete(ruleId: String) {
        SyncSchema.write(session, "rules", ruleId, "tombstone", 1)
    }

    fun insertPayeeCategoryRule(
        payeeId: String,
        categoryId: String,
        stage: String? = null,
    ): String {
        val conditions =
            ruleJson
                .parseToJsonElement(
                    """[{"op":"is","field":"description","value":"$payeeId","type":"id"}]""",
                ).jsonArray
        val actions =
            ruleJson
                .parseToJsonElement(
                    """[{"op":"set","field":"category","value":"$categoryId","type":"id"}]""",
                ).jsonArray
        return insertRule(stage, conditions, actions)
    }

    fun insertPayeeRenameRule(
        fromPayeeId: String,
        toPayeeId: String,
    ): String {
        val conditions =
            ruleJson
                .parseToJsonElement(
                    """[{"op":"is","field":"description","value":"$fromPayeeId","type":"id"}]""",
                ).jsonArray
        val actions =
            ruleJson
                .parseToJsonElement(
                    """[{"op":"set","field":"description","value":"$toPayeeId","type":"id"}]""",
                ).jsonArray
        return insertRule("pre", conditions, actions)
    }

    private fun isScheduleOnlyRule(rule: RuleRecord): Boolean =
        rule.actions.any { action ->
            action.jsonObject["op"]?.jsonPrimitive?.contentOrNull == "link-schedule"
        }

    private fun disabledIds(): Set<String> {
        val raw =
            session
                .query(
                    "SELECT value FROM preferences WHERE id = ?",
                    listOf("disabledRules"),
                ).firstOrNull()
                ?.str("value")
                .orEmpty()
        if (raw.isBlank()) return emptySet()
        return ruleJson
            .parseToJsonElement(raw)
            .jsonArray
            .mapNotNull { it.jsonPrimitive.contentOrNull }
            .toSet()
    }

    private fun persistDisabled(ids: Set<String>) {
        val payload = JsonArray(ids.sorted().map { JsonPrimitive(it) }).toString()
        session.exec(
            """
            INSERT INTO preferences (id, value) VALUES (?, ?)
            ON CONFLICT(id) DO UPDATE SET value = excluded.value
            """.trimIndent(),
            listOf("disabledRules", payload),
        )
    }

    private fun conditionsMatch(
        rule: RuleRecord,
        view: RuleTransactionView,
    ): Boolean {
        if (rule.conditions.isEmpty()) return false
        val method =
            when (rule.conditionsOp.lowercase()) {
                "or" -> rule.conditions.any { evalCondition(it.jsonObject, view) }
                else -> rule.conditions.all { evalCondition(it.jsonObject, view) }
            }
        return method
    }

    private fun applyActions(
        rule: RuleRecord,
        view: RuleTransactionView,
        skipConditions: Boolean,
    ): RuleTransactionView {
        if (!skipConditions && !conditionsMatch(rule, view)) return view
        var next = view
        for (element in rule.actions) {
            val action = element.jsonObject
            when (action["op"]?.jsonPrimitive?.contentOrNull) {
                "set" -> next = applySet(action, next)
                "prepend-notes", "append-notes" -> next = applyNotes(action, next)
                "link-schedule", "delete-transaction", "set-split-amount" -> Unit
            }
        }
        return next
    }

    private fun applySet(
        action: JsonObject,
        view: RuleTransactionView,
    ): RuleTransactionView {
        val field = action["field"]?.jsonPrimitive?.contentOrNull ?: return view
        val value = action["value"]
        return when (field) {
            "payee", "description" -> {
                val id = value?.jsonPrimitive?.contentOrNull ?: return view
                val name = payeeName(id)
                view.copy(payee = id, payeeName = name)
            }
            "category" -> {
                if (value == null || value is JsonPrimitive && value.contentOrNull == null) {
                    view.copy(category = null)
                } else {
                    view.copy(category = value.jsonPrimitive.contentOrNull)
                }
            }
            "notes" -> view.copy(notes = value?.jsonPrimitive?.contentOrNull)
            "amount" -> {
                val amount = jsonNumber(value) ?: return view
                view.copy(amount = amount)
            }
            "date" -> {
                val iso = (value as? JsonPrimitive)?.contentOrNull?.let { ScheduleDates.parseIso(it) } ?: return view
                view.copy(dateIso = iso.toString())
            }
            "cleared" -> view.copy(cleared = (value as? JsonPrimitive)?.let { it.booleanOrNull ?: (it.contentOrNull == "1") })
            else -> view
        }
    }

    private fun applyNotes(
        action: JsonObject,
        view: RuleTransactionView,
    ): RuleTransactionView {
        val op = action["op"]?.jsonPrimitive?.contentOrNull ?: return view
        val chunk = action["value"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val existing = view.notes.orEmpty()
        val merged =
            when (op) {
                "prepend-notes" -> chunk + existing
                "append-notes" -> existing + chunk
                else -> existing
            }
        return view.copy(notes = merged.ifBlank { null })
    }

    private fun evalCondition(
        cond: JsonObject,
        view: RuleTransactionView,
    ): Boolean {
        val field = cond["field"]?.jsonPrimitive?.contentOrNull ?: return false
        val op = cond["op"]?.jsonPrimitive?.contentOrNull ?: return false
        val value = cond["value"]
        return runCatching { evalField(field, op, value, cond, view) }.getOrDefault(false)
    }

    /** An op the engine cannot evaluate is false; it never throws (`contracts/schedules-rules.md`). */
    private fun evalField(
        field: String,
        op: String,
        value: JsonElement?,
        cond: JsonObject,
        view: RuleTransactionView,
    ): Boolean =
        when (field) {
            "payee", "description" -> evalId(view.payee, op, value)
            "category" -> evalId(view.category, op, value)
            "account", "acct" -> evalId(view.account, op, value)
            "amount" -> evalAmount(view.amount, op, value, cond["options"] as? JsonObject)
            "notes" -> evalString(view.notes.orEmpty(), op, value)
            "date" -> evalDate(view.dateIso, op, value)
            else -> false
        }

    private fun evalId(
        fieldValue: String?,
        op: String,
        expected: JsonElement?,
    ): Boolean {
        val left = fieldValue
        val right = (expected as? JsonPrimitive)?.contentOrNull
        return when (op) {
            "is" -> left == right
            "isNot" -> left != right
            "oneOf" -> expected is JsonArray && expected.any { it.jsonPrimitive.contentOrNull == left }
            "notOneOf" -> expected is JsonArray && expected.none { it.jsonPrimitive.contentOrNull == left }
            else -> false
        }
    }

    private fun evalString(
        fieldValue: String,
        op: String,
        expected: JsonElement?,
    ): Boolean {
        val hay = fieldValue.lowercase()
        if (op == "oneOf" || op == "notOneOf") {
            val options = (expected as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.lowercase() } ?: return false
            return if (op == "oneOf") hay in options else hay !in options
        }
        val needle = (expected as? JsonPrimitive)?.contentOrNull?.lowercase().orEmpty()
        return when (op) {
            "is" -> hay == needle
            "isNot" -> hay != needle
            "contains" -> needle.isNotEmpty() && hay.contains(needle)
            "doesNotContain" -> needle.isEmpty() || !hay.contains(needle)
            "matches" ->
                runCatching {
                    Regex(
                        (expected as? JsonPrimitive)?.contentOrNull.orEmpty(),
                    ).containsMatchIn(fieldValue)
                }.getOrDefault(false)
            else -> false
        }
    }

    private fun evalAmount(
        rawValue: Long,
        op: String,
        expected: JsonElement?,
        options: JsonObject? = null,
    ): Boolean {
        val inflow = options?.get("inflow")?.jsonPrimitive?.booleanOrNull == true
        val outflow = options?.get("outflow")?.jsonPrimitive?.booleanOrNull == true
        if (inflow && rawValue <= 0) return false
        if (outflow && rawValue >= 0) return false
        val fieldValue = if (inflow || outflow) abs(rawValue) else rawValue
        val target = jsonNumber(expected)
        return when (op) {
            "gt" -> target != null && fieldValue > target
            "gte" -> target != null && fieldValue >= target
            "lt" -> target != null && fieldValue < target
            "lte" -> target != null && fieldValue <= target
            "is" -> fieldValue == jsonNumber(expected)
            "isapprox" -> {
                val target = jsonNumber(expected) ?: return false
                val threshold = (abs(target) * 0.075).roundToLong().coerceAtLeast(1)
                abs(fieldValue - target) <= threshold
            }
            "isbetween" -> {
                val obj = expected as? JsonObject ?: return false
                val low = obj["num1"]?.jsonPrimitive?.longOrNull ?: return false
                val high = obj["num2"]?.jsonPrimitive?.longOrNull ?: return false
                val min = minOf(low, high)
                val max = maxOf(low, high)
                fieldValue in min..max
            }
            else -> false
        }
    }

    private fun evalDate(
        fieldIso: String,
        op: String,
        expected: JsonElement?,
    ): Boolean {
        val field = ScheduleDates.parseIso(fieldIso) ?: return false
        if (expected is JsonPrimitive) {
            val target = ScheduleDates.parseIso(expected.contentOrNull ?: return false) ?: return false
            return when (op) {
                "is" -> field == target
                "isapprox" -> abs(ChronoUnit.DAYS.between(target, field)) <= 2
                "gt" -> field > target
                "gte" -> field >= target
                "lt" -> field < target
                "lte" -> field <= target
                else -> false
            }
        }
        if (expected is JsonObject && (op == "is" || op == "isapprox")) {
            val window = if (op == "isapprox") 2L else 0L
            return ScheduleRecurrence
                .occurrences(expected, field.minusDays(window), limit = 1)
                .any { abs(ChronoUnit.DAYS.between(it, field)) <= window }
        }
        return false
    }

    private fun payeeName(id: String): String =
        session
            .query(
                "SELECT name FROM payees WHERE id = ? AND IFNULL(tombstone, 0) = 0",
                listOf(id),
            ).firstOrNull()
            ?.str("name")
            .orEmpty()
            .ifBlank { ShellCopy.NO_PAYEE }

    private fun jsonNumber(value: JsonElement?): Long? =
        when (value) {
            null -> null
            is JsonPrimitive -> value.longOrNull ?: value.doubleOrNull?.roundToLong()
            else -> null
        }
}

/**
 * Parsed and ranked rules per open file, so 100 rules do not slow a save (SC-004). Any local or
 * remote `rules` write bumps [generation]; the rule count guards rows written outside the funnel.
 */
object RuleCache {
    @Volatile
    var generation = 0L
        private set

    private data class Entry(
        val generation: Long,
        val count: Long,
        val rules: List<RuleRecord>,
    )

    private val entries = java.util.concurrent.ConcurrentHashMap<String, Entry>()

    fun invalidate() {
        synchronized(this) { generation++ }
    }

    fun ranked(
        session: SqlSession,
        load: () -> List<RuleRecord>,
    ): List<RuleRecord> {
        val key =
            runCatching { session.query("PRAGMA database_list").firstOrNull { it.str("name") == "main" }?.str("file") }
                .getOrNull()
                .orEmpty()
        val count = session.query("SELECT COUNT(*) AS n FROM rules").first().long("n")
        if (key.isEmpty()) return load()
        val gen = generation
        val hit = entries[key]
        if (hit != null && hit.generation == gen && hit.count == count) return hit.rules
        val rules = load()
        entries[key] = Entry(gen, count, rules)
        return rules
    }
}

fun rankRules(rules: List<RuleRecord>): List<RuleRecord> {
    val pre = rules.filter { it.stage == "pre" }.let { scoredSort(it) }
    val normal = rules.filter { it.stage != "pre" && it.stage != "post" }.let { scoredSort(it) }
    val post = rules.filter { it.stage == "post" }.let { scoredSort(it) }
    return pre + normal + post
}

private fun scoredSort(rules: List<RuleRecord>): List<RuleRecord> {
    val scores = rules.associateWith { scoreRule(it) }
    return rules.sortedWith(
        compareBy<RuleRecord> { scores[it] ?: 0 }
            .thenBy { it.id },
    )
}

private fun scoreRule(rule: RuleRecord): Int {
    val opScores =
        mapOf(
            "is" to 10,
            "isNot" to 10,
            "oneOf" to 9,
            "notOneOf" to 9,
            "isapprox" to 5,
            "isbetween" to 5,
            "gt" to 1,
            "gte" to 1,
            "lt" to 1,
            "lte" to 1,
            "contains" to 0,
            "doesNotContain" to 0,
        )
    var score = 0
    var strict = true
    for (element in rule.conditions) {
        val op = element.jsonObject["op"]?.jsonPrimitive?.contentOrNull ?: continue
        score += opScores[op] ?: 0
        if (op !in setOf("is", "isNot", "isapprox", "oneOf", "notOneOf")) {
            strict = false
        }
    }
    if (strict && rule.conditions.isNotEmpty()) score *= 2
    return score
}

object ScheduleDates {
    fun toIso(date: Int): String {
        val year = date / 10000
        val month = (date / 100) % 100
        val day = date % 100
        return "%04d-%02d-%02d".format(year, month, day)
    }

    fun fromIso(iso: String): Int? = RegisterEntry.parseDate(iso)

    fun parseIso(iso: String): LocalDate? =
        try {
            LocalDate.parse(iso)
        } catch (_: Exception) {
            null
        }

    /** `schedules_next_date` dates as Actual stores them: an integer `20261013`. */
    fun toStored(value: String?): String? {
        val text = value?.trim() ?: return null
        val compact = text.replace("-", "")
        return if (compact.length == 8 && compact.all { it.isDigit() }) compact else text
    }

    /** A stored next date, integer or the ISO text phone files used before spec 010. */
    fun storedToIso(value: String?): String? {
        val compact = toStored(value) ?: return null
        val number = compact.toIntOrNull() ?: return null
        return toIso(number)
    }
}
