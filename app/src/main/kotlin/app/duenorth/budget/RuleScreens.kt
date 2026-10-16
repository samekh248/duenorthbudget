package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.RuleDraft
import app.duenorth.budget.core.RuleEditorCopy
import app.duenorth.budget.core.RuleFields
import app.duenorth.budget.core.RuleNames
import app.duenorth.budget.core.RulePart
import app.duenorth.budget.core.RuleSummary
import app.duenorth.budget.core.RuleValues
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens

/** Words for spec 010's rule screens (`contracts/schedules-rules.md`). */
object RuleScreenCopy {
    const val TITLE = "rules"
    const val ADD = "new rule"
    const val EDIT = "rule"
    const val DELETE = "delete rule"
    const val EMPTY = "no rules yet"
    const val IF = "if"
    const val THEN = "then"
    const val ALL = "all match"
    const val ANY = "any matches"
    const val STAGE_PRE = "first"
    const val STAGE_DEFAULT = "normal"
    const val STAGE_POST = "last"
    const val ENABLED = "on"
    const val DISABLED = "off"
    const val ADD_CONDITION = "add condition"
    const val ADD_ACTION = "add action"
    const val REMOVE = "remove"
    const val VALUE = "value"
    const val NOT_FOUND = "no match for that name"
    const val BAD_VALUE = "check the value"
}

/** The rule list, ranked as they run: first, normal, then last. Tag `rules-screen`. */
@Composable
fun RulesScreen(
    rules: List<RuleSummary>,
    onRule: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .testTag("rules-screen"),
    ) {
        MetroText(RuleScreenCopy.TITLE, Metro.typography.header, Modifier.padding(MetroDimens.Gutter))
        if (rules.isEmpty()) {
            MetroText(RuleScreenCopy.EMPTY, Metro.typography.body, Modifier.padding(horizontal = MetroDimens.Gutter))
            return
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(rules, key = { it.id }) { rule -> RuleLine(rule, onRule) }
        }
    }
}

@Composable
private fun RuleLine(
    rule: RuleSummary,
    onRule: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .metroPress { onRule(rule.id) }
            .padding(horizontal = MetroDimens.Gutter, vertical = MetroDimens.Row)
            .testTag("rule-line-${rule.id}"),
    ) {
        MetroText(
            "${RuleScreenCopy.IF} ${rule.conditions.joinToString(", ")}",
            Metro.typography.subheader,
            maxLines = 3,
        )
        MetroText(
            "${RuleScreenCopy.THEN} ${rule.actions.joinToString(", ")}",
            Metro.typography.body,
            color = Metro.colors.secondary,
            maxLines = 3,
        )
        val notes =
            listOfNotNull(
                stageLabel(rule.stage).takeIf { rule.stage != null },
                RuleScreenCopy.DISABLED.takeIf { !rule.enabled },
                RuleEditorCopy.KEPT.takeIf { rule.hasKept },
            )
        if (notes.isNotEmpty()) {
            MetroText(
                notes.joinToString(" · "),
                Metro.typography.caption,
                Modifier.testTag("rule-notes-${rule.id}"),
                color = Metro.accent.text,
            )
        }
    }
}

private fun stageLabel(stage: String?): String =
    when (stage) {
        "pre" -> RuleScreenCopy.STAGE_PRE
        "post" -> RuleScreenCopy.STAGE_POST
        else -> RuleScreenCopy.STAGE_DEFAULT
    }

/**
 * One rule. Parts the phone cannot edit are shown as "kept from the file" and saved back as
 * they were. Tag `rule-editor`.
 */
@Composable
fun RuleEditorScreen(
    draft: RuleDraft,
    names: RuleNames,
    decimals: Int,
    error: String?,
    onSave: (RuleDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    var stage by remember(draft.id) { mutableStateOf(draft.stage) }
    var conditionsOp by remember(draft.id) { mutableStateOf(draft.conditionsOp) }
    var enabled by remember(draft.id) { mutableStateOf(draft.enabled) }
    val conditions = remember(draft.id) { mutableStateListOf<RulePart>().apply { addAll(draft.conditions) } }
    val actions = remember(draft.id) { mutableStateListOf<RulePart>().apply { addAll(draft.actions) } }
    val current = {
        draft.copy(
            stage = stage,
            conditionsOp = conditionsOp,
            conditions = conditions.toList(),
            actions = actions.toList(),
            enabled = enabled,
        )
    }
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(MetroDimens.Gutter)
            .testTag("rule-editor"),
        verticalArrangement = Arrangement.spacedBy(MetroDimens.Row),
    ) {
        MetroText(if (draft.id == null) RuleScreenCopy.ADD else RuleScreenCopy.EDIT, Metro.typography.header)
        ChoiceRow {
            listOf("pre", null, "post").forEach { value ->
                Choice(stageLabel(value), stage == value, "rule-stage-${value ?: "default"}") { stage = value }
            }
        }
        ChoiceRow {
            Choice(RuleScreenCopy.ALL, conditionsOp != "or", "rule-op-and") { conditionsOp = "and" }
            Choice(RuleScreenCopy.ANY, conditionsOp == "or", "rule-op-or") { conditionsOp = "or" }
            Choice(if (enabled) RuleScreenCopy.ENABLED else RuleScreenCopy.DISABLED, enabled, "rule-enabled") { enabled = !enabled }
        }
        RuleHeading(RuleScreenCopy.IF)
        conditions.forEachIndexed { index, part ->
            PartLine(part, names, decimals, "rule-condition-$index") { conditions.removeAt(index) }
        }
        PartAdder(RuleFields.conditionOps, names, decimals, action = false, tag = "rule-add-condition") { conditions.add(it) }
        RuleHeading(RuleScreenCopy.THEN)
        actions.forEachIndexed { index, part ->
            PartLine(part, names, decimals, "rule-action-$index") { actions.removeAt(index) }
        }
        PartAdder(RuleFields.actionOps, names, decimals, action = true, tag = "rule-add-action") { actions.add(it) }
        if (error != null) MetroText(error, Metro.typography.body, Modifier.testTag("rule-error"), color = Metro.accent.text)
        RememberFormSave { onSave(current()) }
        if (!formSaveOnBar()) {
            MetroButton("save", Modifier.testTag("rule-save")) { onSave(current()) }
        }
    }
}

@Composable
private fun RuleHeading(text: String) {
    MetroText(text, Metro.typography.caption, Modifier.padding(top = MetroDimens.Gutter), color = Metro.colors.secondary)
}

@Composable
private fun ChoiceRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun PartLine(
    part: RulePart,
    names: RuleNames,
    decimals: Int,
    tag: String,
    onRemove: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            MetroText(RuleValues.describe(part, names, decimals, LocalFormatPrefs.current), Metro.typography.subheader, maxLines = 3)
            if (part.kept) {
                MetroText(RuleEditorCopy.KEPT, Metro.typography.caption, Modifier.testTag("$tag-kept"), color = Metro.accent.text)
            }
        }
        // A kept part cannot be rebuilt here, so it stays; Actual on a computer can change it.
        if (!part.kept) {
            MetroText(
                RuleScreenCopy.REMOVE,
                Metro.typography.body,
                Modifier.metroPress(onRemove).padding(MetroDimens.Row).testTag("$tag-remove"),
                color = Metro.accent.text,
            )
        }
    }
}

@Composable
private fun PartAdder(
    ops: Map<String, List<String>>,
    names: RuleNames,
    decimals: Int,
    action: Boolean,
    tag: String,
    onAdd: (RulePart) -> Unit,
) {
    var field by remember { mutableStateOf(ops.keys.first()) }
    var op by remember { mutableStateOf(ops.getValue(ops.keys.first()).first()) }
    val prefs = LocalFormatPrefs.current
    var text by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().testTag(tag), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ChoiceRow {
            ops.keys.forEach { name ->
                Choice(name, field == name, "$tag-field-$name") {
                    field = name
                    op = ops.getValue(name).first()
                    problem = null
                }
            }
        }
        ChoiceRow {
            ops.getValue(field).forEach { name -> Choice(name, op == name, "$tag-op-$name") { op = name } }
        }
        MetroField(text, { text = it }, RuleScreenCopy.VALUE, Modifier.testTag("$tag-value"))
        if (problem != null) MetroText(problem!!, Metro.typography.caption, color = Metro.accent.text)
        MetroButton(if (action) RuleScreenCopy.ADD_ACTION else RuleScreenCopy.ADD_CONDITION, Modifier.testTag("$tag-button")) {
            val typed =
                when (RuleFields.type(field)) {
                    "number" -> prefs.readAmount(text, decimals)
                    "date" -> prefs.readDate(text)
                    else -> text
                }
            val part = RuleValues.part(field, op, typed, names, decimals, action)
            if (part == null) {
                problem = if (RuleFields.type(field) == "id") RuleScreenCopy.NOT_FOUND else RuleScreenCopy.BAD_VALUE
            } else {
                onAdd(part)
                text = ""
                problem = null
            }
        }
    }
}
