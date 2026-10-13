package app.duenorth.budget.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.util.UUID

object RuleEditorCopy {
    const val SAVE_RULE = "rule"
    const val DELETE_RULE = "delete rule"
    const val NEEDS_CONDITION = "add a condition"
    const val NEEDS_ACTION = "add an action"
    const val KEPT = "kept from the file"
    const val NOT_FOUND = "rule not found"
}

/** The editor's names for fields, the ops it offers for each, and how Actual stores them. */
object RuleFields {
    val conditionOps: Map<String, List<String>> =
        linkedMapOf(
            "payee" to listOf("is", "isNot", "oneOf", "notOneOf", "contains", "doesNotContain", "matches"),
            "account" to listOf("is", "isNot", "oneOf", "notOneOf"),
            "category" to listOf("is", "isNot", "oneOf", "notOneOf"),
            "amount" to listOf("is", "isapprox", "isbetween", "gt", "gte", "lt", "lte"),
            "date" to listOf("is", "isapprox", "gt", "gte", "lt", "lte"),
            "notes" to listOf("is", "isNot", "contains", "doesNotContain", "matches", "oneOf", "notOneOf"),
        )

    val actionOps: Map<String, List<String>> =
        linkedMapOf(
            "payee" to listOf("set"),
            "category" to listOf("set"),
            "notes" to listOf("set", "prepend-notes", "append-notes"),
            "amount" to listOf("set"),
            "date" to listOf("set"),
            "cleared" to listOf("set"),
        )

    /** R16 #9: payee is stored as `description`, account as `acct`. */
    fun stored(field: String): String =
        when (field) {
            "payee" -> "description"
            "account" -> "acct"
            else -> field
        }

    fun editor(stored: String): String =
        when (stored) {
            "description" -> "payee"
            "acct" -> "account"
            else -> stored
        }

    fun type(field: String): String =
        when (field) {
            "payee", "account", "category" -> "id"
            "amount" -> "number"
            "date" -> "date"
            "cleared" -> "boolean"
            else -> "string"
        }
}

/**
 * One condition or action. [raw] is exactly what the file holds; a part the editor does not
 * know is [kept] and written back unchanged. Editing a known part replaces [raw].
 */
data class RulePart(
    val field: String,
    val op: String,
    val value: JsonElement?,
    val raw: JsonObject,
    val kept: Boolean,
) {
    fun toJson(): JsonObject = raw

    companion object {
        fun condition(
            field: String,
            op: String,
            value: JsonElement?,
            options: JsonObject? = null,
        ): RulePart = build(field, op, value, options)

        fun action(
            field: String,
            op: String,
            value: JsonElement?,
        ): RulePart = build(field, op, value, null)

        private fun build(
            field: String,
            op: String,
            value: JsonElement?,
            options: JsonObject?,
        ): RulePart {
            val map = LinkedHashMap<String, JsonElement>()
            map["op"] = JsonPrimitive(op)
            map["field"] = JsonPrimitive(RuleFields.stored(field))
            map["value"] = value ?: JsonNull
            map["type"] = JsonPrimitive(RuleFields.type(field))
            if (options != null) map["options"] = options
            return RulePart(field, op, value, JsonObject(map), kept = false)
        }

        fun fromRaw(
            raw: JsonObject,
            action: Boolean,
        ): RulePart {
            val stored = raw["field"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val op = raw["op"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val field = RuleFields.editor(stored)
            val ops = if (action) RuleFields.actionOps else RuleFields.conditionOps
            val known = ops[field]?.contains(op) == true && stored != "imported_description"
            return RulePart(field, op, raw["value"], raw, kept = !known)
        }
    }
}

data class RuleDraft(
    val id: String?,
    /** `pre`, `post`, or null for the default stage. */
    val stage: String?,
    val conditionsOp: String,
    val conditions: List<RulePart>,
    val actions: List<RulePart>,
    val enabled: Boolean = true,
)

data class RuleSummary(
    val id: String,
    val stage: String?,
    val conditions: List<String>,
    val actions: List<String>,
    val hasKept: Boolean,
    val enabled: Boolean,
)

sealed interface RuleSaveResult {
    data class Saved(
        val ruleId: String,
    ) : RuleSaveResult

    data class Rejected(
        val reason: String,
    ) : RuleSaveResult
}

/** Rule list and editor (`contracts/schedules-rules.md`); schedule rules are edited with their schedule. */
class RuleEditorBook(
    private val session: SqlSession,
    private val ids: () -> String = { UUID.randomUUID().toString() },
) {
    private val rules = RulesBook(session, ids)

    fun list(): List<RuleSummary> {
        val names = names()
        return rules
            .loadRules()
            .filter { rule -> rule.actions.none { it.jsonObject["op"]?.jsonPrimitive?.contentOrNull == "link-schedule" } }
            .let { rankRules(it) }
            .map { rule ->
                val conditions = rule.conditions.map { RulePart.fromRaw(it.jsonObject, action = false) }
                val actions = rule.actions.map { RulePart.fromRaw(it.jsonObject, action = true) }
                RuleSummary(
                    id = rule.id,
                    stage = rule.stage,
                    conditions = conditions.map { describe(it, names) },
                    actions = actions.map { describe(it, names) },
                    hasKept = (conditions + actions).any { it.kept },
                    enabled = rules.isEnabled(rule.id),
                )
            }
    }

    fun draft(ruleId: String): RuleDraft? {
        val rule = rules.loadRules().firstOrNull { it.id == ruleId } ?: return null
        return RuleDraft(
            id = rule.id,
            stage = rule.stage,
            conditionsOp = rule.conditionsOp,
            conditions = rule.conditions.map { RulePart.fromRaw(it.jsonObject, action = false) },
            actions = rule.actions.map { RulePart.fromRaw(it.jsonObject, action = true) },
            enabled = rules.isEnabled(rule.id),
        )
    }

    fun save(draft: RuleDraft): RuleSaveResult {
        if (draft.conditions.isEmpty()) return RuleSaveResult.Rejected(RuleEditorCopy.NEEDS_CONDITION)
        if (draft.actions.isEmpty()) return RuleSaveResult.Rejected(RuleEditorCopy.NEEDS_ACTION)
        val id = draft.id ?: ids()
        val stage = draft.stage?.takeIf { it == "pre" || it == "post" }
        val conditions = JsonArray(draft.conditions.map { it.toJson() }).toString()
        val actions = JsonArray(draft.actions.map { it.toJson() }).toString()
        SyncSchema.writeRow(
            session,
            "rules",
            id,
            "stage" to stage,
            "conditions_op" to if (draft.conditionsOp == "or") "or" else "and",
            "conditions" to conditions,
            "actions" to actions,
            "tombstone" to 0,
        )
        if (rules.isEnabled(id) != draft.enabled) rules.setEnabled(id, draft.enabled)
        return RuleSaveResult.Saved(id)
    }

    fun delete(ruleId: String): RuleSaveResult {
        if (rules.loadRules().none { it.id == ruleId }) return RuleSaveResult.Rejected(RuleEditorCopy.NOT_FOUND)
        rules.delete(ruleId)
        return RuleSaveResult.Saved(ruleId)
    }

    /** "payee is Cafe", with ids shown by name. */
    fun describe(
        part: RulePart,
        names: Map<String, String> = names(),
    ): String {
        if (part.kept) return "${part.raw["field"]?.jsonPrimitive?.contentOrNull.orEmpty()} ${part.op} ${shown(part.value, names)}".trim()
        return "${part.field} ${part.op} ${shown(part.value, names)}".trim()
    }

    private fun shown(
        value: JsonElement?,
        names: Map<String, String>,
    ): String =
        when (value) {
            null, JsonNull -> ""
            is JsonPrimitive -> value.contentOrNull?.let { names[it] ?: it }.orEmpty()
            is JsonArray -> value.joinToString(", ") { shown(it, names) }
            is JsonObject ->
                if (value["num1"] != null) {
                    "${value["num1"]} and ${value["num2"]}"
                } else {
                    value["start"]?.jsonPrimitive?.contentOrNull.orEmpty()
                }
        }

    private fun names(): Map<String, String> {
        val out = HashMap<String, String>()
        for (table in listOf("payees", "accounts", "categories")) {
            session.query("SELECT id, name FROM $table").forEach { row ->
                val name = row.str("name").orEmpty()
                if (name.isNotEmpty()) out[row.str("id").orEmpty()] = name
            }
        }
        return out
    }
}

/**
 * Actual's `updateCategoryRules` (R10, R16 #7): when learning is on and at least 3 of the
 * payee's latest 5 transactions within 180 days share a category, keep a default-stage
 * "payee is X → set category" rule pointing at it.
 */
class CategoryLearning(
    private val session: SqlSession,
    private val ids: () -> String = { UUID.randomUUID().toString() },
) {
    fun enabled(): Boolean =
        session
            .query("SELECT value FROM preferences WHERE id = 'learn-categories'")
            .firstOrNull()
            ?.str("value") != "false"

    fun learnFrom(transactionId: String) {
        if (!enabled()) return
        val txn =
            session
                .query(
                    "SELECT description, category, date FROM transactions WHERE id = ? AND IFNULL(tombstone, 0) = 0",
                    listOf(transactionId),
                ).firstOrNull() ?: return
        val payeeId = txn.str("description") ?: return
        if (txn.str("category") == null) return
        val payee =
            session
                .query(
                    "SELECT learn_categories, transfer_acct FROM payees WHERE id = ? AND IFNULL(tombstone, 0) = 0",
                    listOf(payeeId),
                ).firstOrNull() ?: return
        if (payee.str("transfer_acct") != null) return
        if (payee.values["learn_categories"] != null && !payee.bool("learn_categories")) return
        val category = probableCategory(payeeId, txn.long("date").toInt()) ?: return
        val existing = simplePayeeRule(payeeId)
        val book = RulesBook(session, ids)
        if (existing == null) {
            book.insertPayeeCategoryRule(payeeId, category)
            return
        }
        val (ruleId, current) = existing
        if (current == category) return
        val actions = """[{"op":"set","field":"category","value":"$category","type":"id"}]"""
        SyncSchema.write(session, "rules", ruleId, "actions", actions)
    }

    private fun probableCategory(
        payeeId: String,
        date: Int,
    ): String? {
        val day = ScheduleDates.parseIso(ScheduleDates.toIso(date)) ?: return null
        val low = day.minusDays(LOOKAROUND_DAYS).let { it.year * 10_000 + it.monthValue * 100 + it.dayOfMonth }
        val high = day.plusDays(LOOKAROUND_DAYS).let { it.year * 10_000 + it.monthValue * 100 + it.dayOfMonth }
        val latest =
            session
                .query(
                    """
                    SELECT category FROM transactions
                    WHERE description = ? AND date >= ? AND date <= ?
                      AND IFNULL(tombstone, 0) = 0 AND IFNULL(isParent, 0) = 0
                    ORDER BY date DESC, sort_order DESC
                    LIMIT 5
                    """.trimIndent(),
                    listOf(payeeId, low, high),
                ).map { it.str("category") }
        val counts = latest.filterNotNull().groupingBy { it }.eachCount()
        return counts.entries.firstOrNull { it.value >= MIN_MATCHES }?.key
    }

    /** The default-stage rule whose only condition is `payee is X` and only action sets a category. */
    private fun simplePayeeRule(payeeId: String): Pair<String, String?>? {
        for (rule in RulesBook(session, ids).loadRules()) {
            if (rule.stage != null && rule.stage != "") continue
            if (rule.conditions.size != 1 || rule.actions.size != 1) continue
            val cond = rule.conditions[0].jsonObject
            val action = rule.actions[0].jsonObject
            val field = cond["field"]?.jsonPrimitive?.contentOrNull
            if ((field != "description" && field != "payee") || cond["op"]?.jsonPrimitive?.contentOrNull != "is") continue
            if (cond["value"]?.jsonPrimitive?.contentOrNull != payeeId) continue
            if (action["field"]?.jsonPrimitive?.contentOrNull != "category" || action["op"]?.jsonPrimitive?.contentOrNull != "set") continue
            return rule.id to action["value"]?.jsonPrimitive?.contentOrNull
        }
        return null
    }

    companion object {
        private const val LOOKAROUND_DAYS = 180L
        private const val MIN_MATCHES = 3
    }
}

/** Names for ids, so the editor reads "payee is Cafe" and turns typed names back into ids. */
data class RuleNames(
    val payees: Map<String, String> = emptyMap(),
    val accounts: Map<String, String> = emptyMap(),
    val categories: Map<String, String> = emptyMap(),
) {
    private fun table(field: String): Map<String, String>? =
        when (field) {
            "payee" -> payees
            "account" -> accounts
            "category" -> categories
            else -> null
        }

    fun name(
        field: String,
        id: String,
    ): String = table(field)?.get(id) ?: id

    fun idFor(
        field: String,
        name: String,
    ): String? =
        table(field)
            ?.entries
            ?.firstOrNull { it.value.equals(name.trim(), ignoreCase = true) }
            ?.key
}

/** Typed text to a rule part and back, for the fields and ops the editor offers. */
object RuleValues {
    private val textOps = setOf("contains", "doesNotContain", "matches")

    /** Null when the text does not name a known payee, account, or category, or is not a valid amount or date. */
    fun part(
        field: String,
        op: String,
        text: String,
        names: RuleNames,
        decimals: Int,
        action: Boolean,
    ): RulePart? {
        val value = value(field, op, text, names, decimals) ?: return null
        return if (action) RulePart.action(field, op, value) else RulePart.condition(field, op, value)
    }

    private fun value(
        field: String,
        op: String,
        text: String,
        names: RuleNames,
        decimals: Int,
    ): JsonElement? {
        val trimmed = text.trim()
        return when (RuleFields.type(field)) {
            "id" ->
                when {
                    op in textOps -> trimmed.takeIf { it.isNotEmpty() }?.let { JsonPrimitive(it) }
                    op == "oneOf" || op == "notOneOf" -> {
                        val ids = trimmed.split(',').filter { it.isNotBlank() }.map { names.idFor(field, it) ?: return null }
                        if (ids.isEmpty()) null else JsonArray(ids.map { JsonPrimitive(it) })
                    }
                    else -> names.idFor(field, trimmed)?.let { JsonPrimitive(it) }
                }
            "number" ->
                if (op == "isbetween") {
                    val parts = trimmed.split(" and ", ",").map { it.trim() }.filter { it.isNotEmpty() }
                    if (parts.size != 2) return null
                    val low = MoneyFormat.parse(parts[0], decimals) ?: return null
                    val high = MoneyFormat.parse(parts[1], decimals) ?: return null
                    JsonObject(linkedMapOf("num1" to JsonPrimitive(low), "num2" to JsonPrimitive(high)))
                } else {
                    MoneyFormat.parse(trimmed, decimals)?.let { JsonPrimitive(it) }
                }
            "date" -> trimmed.takeIf { DATE.matches(it) }?.let { JsonPrimitive(it) }
            "boolean" ->
                when (trimmed.lowercase()) {
                    "yes", "true", "cleared" -> JsonPrimitive(true)
                    "no", "false", "uncleared" -> JsonPrimitive(false)
                    else -> null
                }
            else -> JsonPrimitive(trimmed)
        }
    }

    /** "payee is Cafe"; a kept part shows its stored field. */
    fun describe(
        part: RulePart,
        names: RuleNames,
        decimals: Int,
    ): String {
        val field = if (part.kept) part.raw["field"]?.let { (it as? JsonPrimitive)?.contentOrNull }.orEmpty() else part.field
        return listOf(field, part.op, shown(part.field, part.value, names, decimals)).filter { it.isNotEmpty() }.joinToString(" ")
    }

    private fun shown(
        field: String,
        value: JsonElement?,
        names: RuleNames,
        decimals: Int,
    ): String =
        when (value) {
            null, JsonNull -> ""
            is JsonArray -> value.joinToString(", ") { shown(field, it, names, decimals) }
            is JsonObject ->
                value["num1"]?.let { low ->
                    "${shown(field, low, names, decimals)} and ${shown(field, value["num2"], names, decimals)}"
                } ?: value["start"]?.let { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
            is JsonPrimitive -> {
                val content = value.contentOrNull.orEmpty()
                when {
                    field == "amount" && !value.isString -> content.toLongOrNull()?.let { amountText(it, decimals) } ?: content
                    RuleFields.type(field) == "id" -> names.name(field, content)
                    else -> content
                }
            }
        }

    fun amountText(
        minor: Long,
        decimals: Int,
    ): String = BigDecimal.valueOf(minor).movePointLeft(decimals).toPlainString()

    private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")
}
