package app.duenorth.budget

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.RuleDraft
import app.duenorth.budget.core.RuleEditorCopy
import app.duenorth.budget.core.RuleFields
import app.duenorth.budget.core.RuleNames
import app.duenorth.budget.core.RulePart
import app.duenorth.budget.core.RuleSummary
import app.duenorth.budget.core.RuleValues
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.AppGlyphIcon
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroComboBox
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.components.MetroSegmentedToggle
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.ScreenAction
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
    const val ALL = "all"
    const val ANY = "any"
    const val STAGE_PRE = "first"
    const val STAGE_DEFAULT = "normal"
    const val STAGE_POST = "last"
    const val ENABLED = "on"
    const val DISABLED = "off"
    const val ENABLED_LABEL = "use this rule"
    const val ENABLED_HINT = "off keeps it but skips it"
    const val RUNS = "runs"
    const val RUNS_HINT = "its turn among your rules"
    const val IF_HINT = "a transaction matches"
    const val THEN_HINT = "change it like this"
    const val ADD_CONDITION = "add condition"
    const val ADD_ACTION = "add action"
    const val NEW_CONDITION = "new condition"
    const val NEW_ACTION = "new action"
    const val ADD_PART = "add"
    const val CANCEL = "cancel"
    const val FIELD = "field"
    const val TEST = "test"
    const val HOW = "change"
    const val READS_AS = "reads as"
    const val REMOVE = "remove"
    const val VALUE = "value"
    const val NOT_FOUND = "no match for that name"
    const val BAD_VALUE = "check the value"

    /** Actual's op names in words: `isNot` is "is not", `gte` is "is at least". */
    fun opLabel(op: String): String =
        when (op) {
            "isNot" -> "is not"
            "oneOf" -> "is one of"
            "notOneOf" -> "is not one of"
            "doesNotContain" -> "doesn't contain"
            "isapprox" -> "is about"
            "isbetween" -> "is between"
            "gt" -> "is more than"
            "gte" -> "is at least"
            "lt" -> "is less than"
            "lte" -> "is at most"
            "set" -> "set to"
            "prepend-notes" -> "add before"
            "append-notes" -> "add after"
            else -> op
        }

    /** A card's caption, "payee · is not"; a kept part shows what the file holds. */
    fun caption(part: RulePart): String {
        if (part.kept) {
            return "${part.storedField} · ${part.op}"
        }
        return "${part.field} · ${opLabel(part.op)}"
    }

    /** "payee is Cafe", "set category to Food", "add after notes (big)". */
    fun sentence(
        part: RulePart,
        value: String,
    ): String =
        when (part.op) {
            "set" -> "set ${part.field} to $value"
            "prepend-notes", "append-notes" -> "${opLabel(part.op)} notes: $value"
            else -> "${part.field} ${opLabel(part.op)} $value"
        }

    /** The value box's hint, saying what shape of value the field and test take. */
    fun valueHint(
        field: String,
        op: String,
    ): String =
        when {
            op == "oneOf" || op == "notOneOf" -> "names, split by commas"
            op == "isbetween" -> "low and high, like 10 and 50"
            RuleFields.type(field) == "boolean" -> "yes or no"
            RuleFields.type(field) == "number" -> "amount, minus for spending"
            RuleFields.type(field) == "date" -> "date"
            op == "matches" -> "pattern"
            else -> VALUE
        }
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
 * One rule, as a labelled form: whether it is on and when it runs, then its conditions and
 * actions as cards. Adding a part opens [PartPage] over the form. Parts the phone cannot edit
 * are shown as "kept from the file" and saved back as they were. Tag `rule-editor`.
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
    // Null while the form shows; true or false while the page adding an action or a condition is open.
    var adding by remember(draft.id) { mutableStateOf<Boolean?>(null) }
    val current = {
        draft.copy(
            stage = stage,
            conditionsOp = conditionsOp,
            conditions = conditions.toList(),
            actions = actions.toList(),
            enabled = enabled,
        )
    }
    RememberFormSave { onSave(current()) }
    val action = adding
    if (action != null) {
        PartPage(
            ops = if (action) RuleFields.actionOps else RuleFields.conditionOps,
            names = names,
            decimals = decimals,
            action = action,
            tag = if (action) "rule-add-action" else "rule-add-condition",
            onAdd = {
                if (action) actions.add(it) else conditions.add(it)
                adding = null
            },
            onCancel = { adding = null },
            modifier = modifier,
        )
        return
    }
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(MetroDimens.Gutter)
            .testTag("rule-editor"),
    ) {
        MetroText(if (draft.id == null) RuleScreenCopy.ADD else RuleScreenCopy.EDIT, Metro.typography.header)
        SettingLine(RuleScreenCopy.ENABLED_LABEL, RuleScreenCopy.ENABLED_HINT) {
            MetroSegmentedToggle(RuleScreenCopy.ENABLED, RuleScreenCopy.DISABLED, enabled, { enabled = it }, "rule-enabled")
        }
        val stages = listOf("pre", null, "post")
        SettingLine(RuleScreenCopy.RUNS, RuleScreenCopy.RUNS_HINT) {
            MetroSegmentedToggle(stages.map(::stageLabel), stages.indexOf(stage).coerceAtLeast(0), { stage = stages[it] }, "rule-stage")
        }
        PartsHeading(RuleScreenCopy.IF, RuleScreenCopy.IF_HINT) {
            MetroSegmentedToggle(
                RuleScreenCopy.ALL,
                RuleScreenCopy.ANY,
                conditionsOp != "or",
                { conditionsOp = if (it) "and" else "or" },
                "rule-op",
            )
        }
        conditions.forEachIndexed { index, part ->
            PartCard(part, names, decimals, "rule-condition-$index") { conditions.removeAt(index) }
        }
        AddPartButton(RuleScreenCopy.ADD_CONDITION, "rule-add-condition") { adding = false }
        PartsHeading(RuleScreenCopy.THEN, RuleScreenCopy.THEN_HINT)
        actions.forEachIndexed { index, part ->
            PartCard(part, names, decimals, "rule-action-$index") { actions.removeAt(index) }
        }
        AddPartButton(RuleScreenCopy.ADD_ACTION, "rule-add-action") { adding = true }
        if (error != null) {
            MetroText(
                error,
                Metro.typography.body,
                Modifier.padding(top = MetroDimens.Row).testTag("rule-error"),
                color = Metro.accent.text,
            )
        }
        if (!formSaveOnBar()) {
            MetroButton("save", Modifier.padding(top = MetroDimens.Grid).testTag("rule-save")) { onSave(current()) }
        }
    }
}

/** A labelled setting: its name and a line on what it does, with its control on the right. */
@Composable
private fun SettingLine(
    label: String,
    hint: String,
    control: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = MetroDimens.Row),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = MetroDimens.Gutter)) {
            MetroText(label, Metro.typography.body)
            MetroText(hint, Metro.typography.caption, color = Metro.colors.secondary)
        }
        control()
    }
}

/** "IF" or "THEN" over its cards, with what it means beside it and an optional control on the right. */
@Composable
private fun PartsHeading(
    label: String,
    hint: String,
    control: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = MetroDimens.Grid, bottom = MetroDimens.FieldLabelGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MetroText(label.uppercase(), Metro.typography.control, Modifier.padding(end = 8.dp))
        MetroText(hint, Metro.typography.caption, Modifier.weight(1f), color = Metro.colors.secondary)
        control?.invoke()
    }
}

/**
 * One condition or action: the field and test as a caption over the value, with a remove
 * glyph. A kept part has no remove; Actual on a computer can change it.
 */
@Composable
private fun PartCard(
    part: RulePart,
    names: RuleNames,
    decimals: Int,
    tag: String,
    onRemove: () -> Unit,
) {
    val prefs = LocalFormatPrefs.current
    val edge = if (part.kept) Metro.colors.secondary else Metro.accent.fill
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = MetroDimens.FieldLabelGap)
            .background(edge.copy(alpha = if (part.kept) 0.06f else 0.08f))
            .height(IntrinsicSize.Min)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(edge))
        Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp)) {
            MetroText(
                RuleScreenCopy.caption(part).uppercase(),
                Metro.typography.caption,
                color = Metro.colors.secondary,
                maxLines = 1,
                softWrap = false,
            )
            MetroText(RuleValues.valueText(part, names, decimals, prefs), Metro.typography.subheader, maxLines = 3)
            if (part.kept) {
                MetroText(RuleEditorCopy.KEPT, Metro.typography.caption, Modifier.testTag("$tag-kept"), color = Metro.accent.text)
            }
        }
        if (!part.kept) {
            Box(
                Modifier
                    .size(MetroDimens.TouchTarget)
                    .metroPress(onRemove)
                    .semantics { contentDescription = RuleScreenCopy.REMOVE }
                    .testTag("$tag-remove"),
                contentAlignment = Alignment.Center,
            ) { AppGlyphIcon(AppGlyph.Cancel) }
        }
    }
}

/** The outlined "+ add condition" line under a list of parts. */
@Composable
private fun AddPartButton(
    label: String,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .border(MetroDimens.Stroke, Metro.colors.secondary)
            .metroPress(onClick)
            .padding(horizontal = 10.dp)
            .testTag("$tag-open"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppGlyphIcon(AppGlyph.Add, Metro.accent.text)
        MetroText(label, Metro.typography.control, Modifier.padding(start = 8.dp), color = Metro.accent.text)
    }
}

/**
 * The page for one new condition or action, drawn over the form: field and test as combo
 * boxes, then the value, with how the part will read once the value makes sense. The bar's
 * done adds it; cancel or back returns to the form.
 */
@Composable
internal fun PartPage(
    ops: Map<String, List<String>>,
    names: RuleNames,
    decimals: Int,
    action: Boolean,
    tag: String,
    onAdd: (RulePart) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    initialText: String = "",
) {
    var field by remember { mutableStateOf(ops.keys.first()) }
    var op by remember { mutableStateOf(ops.getValue(ops.keys.first()).first()) }
    val prefs = LocalFormatPrefs.current
    var text by remember { mutableStateOf(initialText) }
    var problem by remember { mutableStateOf<String?>(null) }
    val built = {
        val typed =
            when (RuleFields.type(field)) {
                "number" -> prefs.readAmount(text, decimals)
                "date" -> prefs.readDate(text)
                else -> text
            }
        RuleValues.part(field, op, typed, names, decimals, action)
    }
    val add = {
        val part = built()
        if (part == null) {
            problem = if (RuleFields.type(field) == "id") RuleScreenCopy.NOT_FOUND else RuleScreenCopy.BAD_VALUE
        } else {
            onAdd(part)
        }
    }
    BackHandler(onBack = onCancel)
    val done by rememberUpdatedState(add)
    val cancel by rememberUpdatedState(onCancel)
    val bar =
        remember {
            listOf(
                ScreenAction("rule-part-done", AppGlyph.Save, RuleScreenCopy.ADD_PART) { done() },
                ScreenAction("rule-part-cancel", AppGlyph.Cancel, RuleScreenCopy.CANCEL) { cancel() },
            )
        }
    RememberBarOverride(bar)
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(MetroDimens.Gutter)
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(MetroDimens.Row),
    ) {
        MetroText(if (action) RuleScreenCopy.NEW_ACTION else RuleScreenCopy.NEW_CONDITION, Metro.typography.header)
        PageField(RuleScreenCopy.FIELD) {
            MetroComboBox(ops.keys.toList(), field, { it }, { name ->
                field = name
                op = ops.getValue(name).first()
                problem = null
            }, "$tag-field")
        }
        val fieldOps = ops.getValue(field)
        // An action with one way to change its field, such as "set to", needs no test to pick.
        if (fieldOps.size > 1) {
            PageField(if (action) RuleScreenCopy.HOW else RuleScreenCopy.TEST) {
                MetroComboBox(fieldOps, op, RuleScreenCopy::opLabel, { op = it }, "$tag-op")
            }
        }
        PageField(RuleScreenCopy.VALUE) {
            val hint = RuleScreenCopy.valueHint(field, op)
            if (field == "payee" && op in PAYEE_NAME_OPS) {
                // A rule names saved payees only, so the list offers no new one.
                val payees = remember(names) { names.payees.values.sortedBy { it.lowercase() } }
                PayeeField(text, { text = it }, payees, hint, "$tag-value", offerNew = false)
            } else {
                MetroField(text, { text = it }, hint, Modifier.testTag("$tag-value"))
            }
        }
        if (problem != null) MetroText(problem!!, Metro.typography.body, Modifier.testTag("$tag-problem"), color = Metro.accent.text)
        val part = if (text.isBlank()) null else built()
        if (part != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = MetroDimens.Row)
                    .background(Metro.accent.fill.copy(alpha = 0.08f))
                    .height(IntrinsicSize.Min)
                    .testTag("$tag-preview"),
            ) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(Metro.accent.fill))
                MetroText(
                    "${RuleScreenCopy.READS_AS} ${RuleScreenCopy.sentence(part, RuleValues.valueText(part, names, decimals, prefs))}",
                    Metro.typography.body,
                    Modifier.padding(10.dp),
                )
            }
        }
        if (!formSaveOnBar()) {
            MetroButton(RuleScreenCopy.ADD_PART, Modifier.testTag("$tag-button")) { add() }
        }
    }
}

@Composable
private fun PageField(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        MetroText(label, Metro.typography.caption, Modifier.padding(bottom = MetroDimens.FieldLabelGap), color = Metro.colors.secondary)
        content()
    }
}

/** Payee ops whose value is one saved payee; "contains", "matches" and the lists take free text. */
private val PAYEE_NAME_OPS = setOf("is", "isNot", "set")
