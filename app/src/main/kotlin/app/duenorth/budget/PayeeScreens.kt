package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.PayeeLookalike
import app.duenorth.budget.core.PayeeRow
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroButtonGrid
import app.duenorth.budget.design.components.MetroContextMenu
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.components.MetroMenuItem
import app.duenorth.budget.design.components.MetroSegmentedToggle
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.ScreenAction
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import app.duenorth.budget.design.theme.MetroWeights

/** What the payees page can do beyond renaming one payee: its look-alike suggestions and batch mode. */
class PayeeActions(
    /** Makes the payees one payee under the name, remembering a rule for the merged names when asked. */
    val onMergeAll: (List<String>, String, Boolean) -> Unit = { _, _, _ -> },
    /** Deletes payees no transaction uses. */
    val onDelete: (List<String>) -> Unit = {},
    /** "Not the same": the suggestion goes, here and in the assistant. */
    val onNotTheSame: (PayeeLookalike) -> Unit = {},
)

/** The names going into one payee on the merge page, the one kept first, and the name it starts with. */
internal data class PayeeMergeDraft(
    val names: List<PayeeRow>,
    val name: String,
)

/**
 * The payees page, laid out as the inbox is: the best look-alike suggestion stands apart in a panel with merge and
 * not the same, the other suggestions wait under it, then every payee under letter headings, and the unused ones
 * last. Batch mode gives every payee a check box, and the bar merges or deletes the checked ones.
 */
@Composable
fun PayeesScreen(
    payees: List<PayeeRow>,
    error: String?,
    mergeTarget: String?,
    onRename: (String, String, Boolean, Boolean) -> Unit,
    onConfirmMerge: (String, String) -> Unit,
    onCancelMerge: () -> Unit,
    modifier: Modifier = Modifier,
    lookalikes: List<PayeeLookalike> = emptyList(),
    actions: PayeeActions = PayeeActions(),
    /** Opens in batch mode with these payees checked; for previews and snapshots. */
    initialSelection: Set<String>? = null,
    /** Opens on the merge page for these payees, the first kept; for previews and snapshots. */
    initialMerge: List<String>? = null,
) {
    var editing by remember { mutableStateOf<PayeeRow?>(null) }
    var name by remember(editing?.id) { mutableStateOf(editing?.name.orEmpty()) }
    var rememberRule by remember(editing?.id) { mutableStateOf(false) }
    var selection by remember { mutableStateOf(initialSelection) }
    var merging by remember {
        mutableStateOf(
            initialMerge?.let { ids ->
                val names = ids.mapNotNull { id -> payees.firstOrNull { it.id == id } }
                PayeeMergeDraft(names, names.firstOrNull()?.name.orEmpty())
            },
        )
    }
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background),
    ) {
        val draft = merging
        if (draft != null) {
            PayeeMergePage(
                draft = draft,
                onMerge = { kept, rule ->
                    actions.onMergeAll(draft.names.map { it.id }, kept, rule)
                    merging = null
                    selection = null
                },
                onCancel = { merging = null },
            )
        } else if (mergeTarget != null && editing != null) {
            ConfirmPage(
                title = PayeeScreenCopy.MERGE_TITLE,
                body = PayeeScreenCopy.MERGE_BODY,
                yes = PayeeScreenCopy.MERGE,
                onYes = {
                    onConfirmMerge(editing!!.id, name)
                    editing = null
                },
                onKeep = onCancelMerge,
                tag = "payee-merge",
            )
        } else if (editing != null) {
            Column(Modifier.padding(MetroDimens.Gutter)) {
                MetroText(PayeeScreenCopy.RENAME_TITLE, Metro.typography.header)
                MetroField(name, { name = it }, "name", Modifier.padding(top = MetroDimens.Grid))
                RowChoice(PayeeScreenCopy.REMEMBER, rememberRule, "payee-remember-rule") { rememberRule = !rememberRule }
                if (error != null) {
                    WarningText(error, Modifier.padding(top = MetroDimens.Gutter))
                }
                PageBar(
                    listOf(
                        ScreenAction("payee-save", AppGlyph.Save, "save") { onRename(editing!!.id, name, false, rememberRule) },
                        ScreenAction("payee-cancel", AppGlyph.Cancel, "cancel") { editing = null },
                    ),
                )
            }
        } else {
            PayeeList(
                payees = payees,
                lookalikes = lookalikes,
                error = error,
                selection = selection,
                onSelection = { selection = it },
                onEdit = { payee ->
                    editing = payee
                    name = payee.name
                },
                onMergeDraft = { merging = it },
                actions = actions,
            )
        }
    }
}

/** The payees page's list: totals, the featured suggestion, the other suggestions, then the payees in groups. */
@Composable
private fun PayeeList(
    payees: List<PayeeRow>,
    lookalikes: List<PayeeLookalike>,
    error: String?,
    selection: Set<String>?,
    onSelection: (Set<String>?) -> Unit,
    onEdit: (PayeeRow) -> Unit,
    onMergeDraft: (PayeeMergeDraft) -> Unit,
    actions: PayeeActions,
) {
    val own = payees.filter { !it.transfer }
    val (used, unused) = own.partition { it.transactionCount > 0 }
    var menu by remember { mutableStateOf<Pair<List<MetroMenuItem>, Rect>?>(null) }
    val mergeOf = { group: PayeeLookalike -> PayeeMergeDraft(group.names, group.keep.name) }
    val payeeMenu = { payee: PayeeRow, bounds: Rect ->
        menu =
            listOfNotNull(
                MetroMenuItem(RowMenuCopy.SELECT) { onSelection(setOf(payee.id)) },
                MetroMenuItem(PayeeScreenCopy.RENAME) { onEdit(payee) },
                lookalikes.firstOrNull { group -> group.names.any { it.id == payee.id } }?.let { group ->
                    MetroMenuItem(PayeeScreenCopy.MERGE_LOOKALIKES) { onMergeDraft(mergeOf(group)) }
                },
                if (payee.transactionCount ==
                    0
                ) {
                    MetroMenuItem(PayeeScreenCopy.DELETE, accent = true) { actions.onDelete(listOf(payee.id)) }
                } else {
                    null
                },
            ) to bounds
    }
    val groupMenu = { group: PayeeLookalike, bounds: Rect ->
        menu =
            listOf(
                MetroMenuItem(PayeeScreenCopy.MERGE) { actions.onMergeAll(group.names.map { it.id }, group.keep.name, true) },
                MetroMenuItem(PayeeScreenCopy.MERGE_AS) { onMergeDraft(mergeOf(group)) },
                MetroMenuItem(PayeeScreenCopy.NOT_THE_SAME) { actions.onNotTheSame(group) },
            ) to bounds
    }
    Column(Modifier.fillMaxSize().padding(horizontal = MetroDimens.Gutter)) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp)) {
            BudgetTotal(PayeeScreenCopy.PAYEES, "${own.size}", Metro.accent.text, "payees-count", modifier = Modifier.weight(1f))
            if (selection != null) {
                BudgetTotal(InboxCopy.SELECTED, "${selection.size}", Metro.colors.foreground, "payees-selected", alignEnd = true)
            } else {
                BudgetTotal(PayeeScreenCopy.LOOKALIKES, "${lookalikes.size}", Metro.colors.foreground, "payees-lookalikes", alignEnd = true)
            }
        }
        if (error != null) WarningText(error, Modifier.padding(bottom = 4.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            if (selection == null) {
                lookalikes.firstOrNull()?.let { lead ->
                    item(key = "lookalike-panel-${lead.id}") {
                        LookalikePanel(
                            group = lead,
                            onMerge = { actions.onMergeAll(lead.names.map { it.id }, lead.keep.name, true) },
                            onNotTheSame = { actions.onNotTheSame(lead) },
                            onRename = { onMergeDraft(mergeOf(lead)) },
                            onMenu = { groupMenu(lead, it) },
                            modifier = Modifier.padding(top = FEATURED_AIR_ABOVE),
                        )
                    }
                }
                val more = lookalikes.drop(1)
                if (more.isNotEmpty()) {
                    item(key = "group-more") { PayeeGroupHeading(PayeeScreenCopy.MORE_LOOKALIKES, more.size) }
                    itemsIndexed(more, key = { _, it -> "lookalike-${it.id}" }) { i, group ->
                        LookalikeLine(group, ledgerBanded(i), onTap = { onMergeDraft(mergeOf(group)) }, onMenu = { groupMenu(group, it) })
                    }
                }
            }
            val toggle = { id: String ->
                val now = selection.orEmpty()
                onSelection(if (id in now) now - id else now + id)
            }
            payeeGroup(PayeeScreenCopy.IN_USE, used, selection, toggle, onEdit, payeeMenu, letters = true)
            payeeGroup(PayeeScreenCopy.NOT_USED, unused, selection, toggle, onEdit, payeeMenu, letters = false)
        }
    }
    menu?.let { (items, anchor) -> MetroContextMenu(anchor, items, { menu = null }, tag = "payee-menu") }
    PageBar(payeeBar(selection, own, onSelection, onMergeDraft, actions))
}

/** The page's bar: batch mode, or what can be done to the checked payees, cancel last and delete set apart. */
private fun payeeBar(
    selection: Set<String>?,
    payees: List<PayeeRow>,
    onSelection: (Set<String>?) -> Unit,
    onMergeDraft: (PayeeMergeDraft) -> Unit,
    actions: PayeeActions,
): List<ScreenAction> {
    if (selection == null) {
        return if (payees.isEmpty()) {
            emptyList()
        } else {
            listOf(ScreenAction("select-payees", AppGlyph.Select, InboxCopy.SELECT, caption = InboxCopy.BATCH) { onSelection(emptySet()) })
        }
    }
    val picked = payees.filter { it.id in selection }
    return buildList {
        if (picked.size >= 2) {
            add(
                ScreenAction("merge-payees", AppGlyph.Merge, PayeeScreenCopy.MERGE) {
                    val ordered = picked.sortedWith(compareByDescending<PayeeRow> { it.transactionCount }.thenBy { it.name.lowercase() })
                    onMergeDraft(PayeeMergeDraft(ordered, ordered.first().name))
                },
            )
        }
        add(ScreenAction("end-payees-selecting", AppGlyph.Cancel, InboxCopy.CANCEL) { onSelection(null) })
        if (picked.isNotEmpty() && picked.all { it.transactionCount == 0 }) {
            add(
                ScreenAction("delete-payees", AppGlyph.Delete, PayeeScreenCopy.DELETE, apart = true) {
                    actions.onDelete(picked.map { it.id })
                    onSelection(null)
                },
            )
        }
    }
}

/** A group of payees under its heading; in use, the payees also sit under their first letter. */
private fun LazyListScope.payeeGroup(
    title: String,
    rows: List<PayeeRow>,
    selection: Set<String>?,
    onToggle: (String) -> Unit,
    onEdit: (PayeeRow) -> Unit,
    onMenu: (PayeeRow, Rect) -> Unit,
    letters: Boolean,
) {
    if (rows.isEmpty()) return
    item(key = "group-$title") { PayeeGroupHeading(title, rows.size) }
    val runs = if (letters) rows.groupBy { payeeLetter(it.name) }.toList() else listOf("" to rows)
    var start = 0
    runs.forEach { (letter, run) ->
        if (letter.isNotEmpty()) {
            stickyHeader(key = "letter-$title-$letter") { PayeeLetterHeading(letter) }
        }
        val offset = start
        itemsIndexed(run, key = { _, it -> "payee-${it.id}" }) { i, payee ->
            PayeeLine(
                payee = payee,
                banded = ledgerBanded(offset + i),
                checked = selection?.let { payee.id in it },
                onTap = { if (selection != null) onToggle(payee.id) else onEdit(payee) },
                onMenu = if (selection == null) { bounds -> onMenu(payee, bounds) } else null,
            )
        }
        start += run.size
    }
}

/** The letter a payee files under: its first letter, or # for a name that starts with anything else. */
internal fun payeeLetter(name: String): String {
    val first = name.trim().firstOrNull() ?: return "#"
    return if (first.isLetter()) first.lowercase() else "#"
}

/**
 * The lead suggestion: the names that look like one payee, the one used most first, the accent rule between them
 * with why, the name they would merge as, and merge or not the same.
 */
@Composable
private fun LookalikePanel(
    group: PayeeLookalike,
    onMerge: () -> Unit,
    onNotTheSame: () -> Unit,
    onRename: () -> Unit,
    onMenu: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Column(
        modifier
            .fillMaxWidth()
            .ledgerFill(Metro.colors.chrome)
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .metroPress(onClick = onRename, onLongClick = { onMenu(bounds) }, bleed = MetroDimens.Gutter)
            .padding(vertical = FEATURED_PAD)
            .testTag("payee-lookalike-panel-${group.id}"),
    ) {
        MetroText(PayeeScreenCopy.POSSIBLE_DUPLICATE, Metro.typography.groupHeader, color = Metro.accent.text)
        group.names.forEachIndexed { i, payee ->
            if (i == 1) LookalikeLink()
            Column(Modifier.fillMaxWidth().padding(top = if (i == 0) 10.dp else 6.dp)) {
                MetroText(
                    if (i == 0) PayeeScreenCopy.USED_MOST else PayeeScreenCopy.ALSO_AS,
                    Metro.typography.caption,
                    color = Metro.accent.text,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    MetroText(
                        payee.name,
                        Metro.typography.body.copy(fontWeight = MetroWeights.Semibold),
                        Modifier.weight(1f).padding(end = 12.dp),
                        maxLines = 1,
                    )
                    MetroText(
                        PayeeScreenCopy.transactions(payee.transactionCount),
                        Metro.typography.caption,
                        color = Metro.colors.secondary,
                        maxLines = 1,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            MetroText("${PayeeScreenCopy.MERGES_AS} ", Metro.typography.caption, color = Metro.colors.secondary)
            MetroText(
                group.keep.name,
                Metro.typography.body.copy(fontWeight = MetroWeights.Semibold),
                Modifier.weight(1f),
                maxLines = 1,
            )
            MetroText(
                PayeeScreenCopy.RENAME,
                Metro.typography.body,
                Modifier
                    .metroPress(
                        onRename,
                    ).padding(start = 12.dp, top = 6.dp, bottom = 6.dp)
                    .testTag("payee-lookalike-rename-${group.id}"),
                color = Metro.accent.text,
            )
        }
        MetroButtonGrid(Modifier.padding(top = 8.dp)) {
            AccentButton(PayeeScreenCopy.MERGE, Modifier.testTag("payee-lookalike-merge-${group.id}"), onMerge)
            MetroButton(PayeeScreenCopy.NOT_THE_SAME, Modifier.testTag("payee-lookalike-dismiss-${group.id}"), onClick = onNotTheSame)
        }
    }
}

/** The accent rule between the kept name and the names that look like it, as the inbox joins a possible match. */
@Composable
private fun LookalikeLink() {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(22.dp).background(Metro.accent.fill))
        MetroText(
            PayeeScreenCopy.WHY_ALIKE,
            Metro.typography.caption,
            Modifier.padding(start = 10.dp),
            color = Metro.colors.secondary,
            maxLines = 1,
        )
    }
}

/** A waiting suggestion: the names it would merge, and what they become. A tap opens the merge page. */
@Composable
private fun LookalikeLine(
    group: PayeeLookalike,
    banded: Boolean,
    onTap: () -> Unit,
    onMenu: (Rect) -> Unit,
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .ledgerFill(if (banded) ledgerBand else Color.Transparent)
            .metroPress(onClick = onTap, onLongClick = { onMenu(bounds) })
            .padding(vertical = PAYEE_ROW_AIR)
            .testTag("payee-lookalike-${group.id}"),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f).padding(end = MetroDimens.Gutter)) {
            MetroText(group.others.joinToString(" · ") { it.name }, Metro.typography.body, maxLines = 2)
            MetroText(
                "${PayeeScreenCopy.LOOKS_LIKE} ${group.keep.name}",
                Metro.typography.caption,
                color = Metro.colors.secondary,
                maxLines = 1,
            )
        }
        MetroText(PayeeScreenCopy.MERGE, Metro.typography.body, color = Metro.accent.text, maxLines = 1)
    }
}

/** One payee: its name, and how many transactions use it. A check box leads it in batch mode. */
@Composable
private fun PayeeLine(
    payee: PayeeRow,
    banded: Boolean,
    checked: Boolean?,
    onTap: () -> Unit,
    onMenu: ((Rect) -> Unit)?,
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .ledgerFill(if (banded) ledgerBand else Color.Transparent)
            .metroPress(onClick = onTap, onLongClick = onMenu?.let { open -> { open(bounds) } })
            .padding(vertical = PAYEE_ROW_AIR)
            .testTag("payee-row-${payee.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (checked != null) {
            CheckSquare(checked, Modifier.testTag(if (checked) "payee-checked-${payee.id}" else "payee-unchecked-${payee.id}"))
        }
        Column(Modifier.weight(1f).padding(start = if (checked != null) MetroDimens.Gutter else 0.dp)) {
            MetroText(payee.name, Metro.typography.body, maxLines = 2)
            MetroText(
                PayeeScreenCopy.transactions(payee.transactionCount),
                Metro.typography.caption,
                color = Metro.colors.secondary,
                maxLines = 1,
            )
        }
    }
}

/** A group's heading: its name, with how many are in it, as the inbox's batch mode heads its groups. */
@Composable
private fun PayeeGroupHeading(
    title: String,
    count: Int,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = GROUP_AIR_ABOVE, bottom = 2.dp)
            .testTag("payee-group-$title"),
        verticalAlignment = Alignment.Bottom,
    ) {
        MetroText(title, Metro.typography.subheader, Modifier.weight(1f), color = Metro.accent.text, maxLines = 1)
        MetroText("$count", Metro.typography.subheader, color = Metro.colors.secondary, maxLines = 1)
    }
}

/** A letter over its payees, sticking to the top of the list until the next letter pushes it off. */
@Composable
private fun PayeeLetterHeading(letter: String) {
    MetroText(
        letter,
        Metro.typography.caption,
        Modifier
            .fillMaxWidth()
            .background(Metro.colors.background)
            .padding(top = 12.dp, bottom = 4.dp)
            .testTag("payee-letter-$letter"),
        color = Metro.accent.text,
    )
}

/**
 * Merge payees: the names going into one, a name for it (the most used, or any of them with a tap, or a new one),
 * and whether to remember each old name as a rule so the bank's spelling lands here next time.
 */
@Composable
private fun PayeeMergePage(
    draft: PayeeMergeDraft,
    onMerge: (String, Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember(draft) { mutableStateOf(draft.name) }
    var rememberRule by remember(draft) { mutableStateOf(true) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(MetroDimens.Gutter)
            .testTag("payee-merge-page"),
    ) {
        MetroText(PayeeScreenCopy.MERGE_PAGE_TITLE, Metro.typography.header)
        MetroText(
            PayeeScreenCopy.mergeBody(draft.names.size, draft.names.sumOf { it.transactionCount }),
            Metro.typography.body,
            Modifier.padding(top = 4.dp),
            color = Metro.colors.secondary,
        )
        MetroText(
            PayeeScreenCopy.KEEP_WHICH,
            Metro.typography.caption,
            Modifier.padding(top = MetroDimens.Gutter),
            color = Metro.accent.text,
        )
        draft.names.forEach { payee ->
            val chosen = payee.name.trim().equals(name.trim(), ignoreCase = true)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = MetroDimens.TouchTarget)
                    .metroPress { name = payee.name }
                    .padding(vertical = 8.dp)
                    .testTag("payee-merge-name-${payee.id}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CheckSquare(chosen)
                Column(Modifier.weight(1f).padding(start = MetroDimens.Gutter)) {
                    MetroText(payee.name, Metro.typography.body, maxLines = 2)
                    MetroText(
                        PayeeScreenCopy.transactions(payee.transactionCount),
                        Metro.typography.caption,
                        color = Metro.colors.secondary,
                    )
                }
            }
        }
        MetroText(PayeeScreenCopy.NEW_NAME, Metro.typography.caption, Modifier.padding(top = MetroDimens.Gutter), color = Metro.accent.text)
        MetroField(name, { name = it }, PayeeScreenCopy.NAME, Modifier.padding(top = 4.dp).testTag("payee-merge-field"))
        RowChoice(PayeeScreenCopy.REMEMBER, rememberRule, "payee-merge-remember-rule") { rememberRule = !rememberRule }
        PageBar(
            listOf(
                ScreenAction("payee-merge-save", AppGlyph.Merge, PayeeScreenCopy.MERGE) { onMerge(name, rememberRule) },
                ScreenAction("payee-merge-cancel", AppGlyph.Cancel, InboxCopy.CANCEL, run = onCancel),
            ),
        )
    }
}

/** Remember as rule as the off/on pair every setting in the app uses. */
@Composable
private fun RowChoice(
    label: String,
    selected: Boolean,
    tag: String,
    onSelect: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(top = MetroDimens.Gutter), verticalAlignment = Alignment.CenterVertically) {
        MetroText(label, Metro.typography.body, Modifier.weight(1f).padding(end = 12.dp))
        MetroSegmentedToggle(
            first = ExperimentalCopy.OFF,
            second = ExperimentalCopy.ON,
            firstSelected = !selected,
            onSelect = { first -> if (first == selected) onSelect() },
            tag = tag,
        )
    }
}

private val PAYEE_ROW_AIR = 15.dp
private val FEATURED_AIR_ABOVE = 20.dp
private val GROUP_AIR_ABOVE = 24.dp

/** Words for the payees page. */
object PayeeScreenCopy {
    const val MERGE_TITLE = "merge payees?"
    const val MERGE_BODY = "this name already exists; merging moves these transactions to the existing payee"
    const val MERGE = "merge"
    const val PAYEES = "payees"
    const val LOOKALIKES = "look-alikes"
    const val POSSIBLE_DUPLICATE = "possible duplicate"
    const val USED_MOST = "used most"
    const val ALSO_AS = "also as"
    const val WHY_ALIKE = "names that read alike once case, numbers and endings go"
    const val MERGES_AS = "merges as"
    const val RENAME = "rename"
    const val RENAME_TITLE = "rename payee"
    const val NOT_THE_SAME = "not the same"
    const val MORE_LOOKALIKES = "more look-alikes"
    const val LOOKS_LIKE = "looks like"
    const val IN_USE = "in use"
    const val NOT_USED = "not used"
    const val DELETE = "delete"
    const val MERGE_AS = "merge as…"
    const val MERGE_LOOKALIKES = "merge look-alikes"
    const val MERGE_PAGE_TITLE = "merge payees"
    const val KEEP_WHICH = "keep which name?"
    const val NEW_NAME = "or type a new name"
    const val NAME = "name"
    const val REMEMBER = "remember as rule"

    fun transactions(count: Int): String =
        when (count) {
            0 -> "no transactions"
            1 -> "1 transaction"
            else -> "$count transactions"
        }

    fun mergeBody(
        names: Int,
        transactions: Int,
    ): String = "these $names names become one payee, with all ${transactions(transactions)}"
}
