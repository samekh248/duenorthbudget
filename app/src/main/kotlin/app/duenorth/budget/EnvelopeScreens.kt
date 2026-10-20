package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.CategoryManagePage
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.EnvelopeCopy
import app.duenorth.budget.core.FormatOptions
import app.duenorth.budget.core.ManageCategoryRow
import app.duenorth.budget.core.ManageGroupRow
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.MoneyMath
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.monthLabel
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroButtonGrid
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.PillCorner
import app.duenorth.budget.design.components.Relief
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.components.relief
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens

@Composable
fun CategoryBudgetScreen(
    shell: MonthShell,
    categoryId: String,
    error: String?,
    onSave: (String) -> Unit,
    onMove: () -> Unit,
    onToggleCarryover: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
    onSaveNote: (String) -> Unit = {},
) {
    val category = shell.groups.flatMap { it.categories }.first { it.id == categoryId }
    val prefs = LocalFormatPrefs.current
    // Signed, so a negative budget (money moved out) reads as one.
    val initial = prefs.editSignedAmount(category.budgetedMinor, shell.currency.decimals)
    var amount by remember(category, prefs) { mutableStateOf(initial) }
    // "+500" typed over the amount adds to it. This box starts filled in, so a leading minus is a negative
    // amount ("-150"), not "take 150 off".
    val base = if (amount.trimStart().startsWith("-")) null else category.budgetedMinor
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetroText(category.name, Metro.typography.header)
        MetroText(monthLabel(shell.month), Metro.typography.caption, color = Metro.colors.secondary)
        MetroText(
            "available ${MoneyFormat.format(category.availableMinor, shell.currency, formatOptions())}",
            Metro.typography.subheader,
        )
        MetroField(amount, { amount = it }, "budgeted", Modifier.testTag("category-budget-amount"))
        if (error != null) MetroText(error, Metro.typography.body, color = Metro.accent.text)
        RememberFormSave { onSave(prefs.readAmount(amount, shell.currency.decimals, base = base)) }
        MetroButtonGrid {
            if (!formSaveOnBar()) {
                MetroButton("save") { onSave(prefs.readAmount(amount, shell.currency.decimals, base = base)) }
            }
            MetroButton(EnvelopeCopy.MOVE, onClick = onMove)
            MetroButton(
                if (category.carryover) "stop ${EnvelopeCopy.ROLLOVER}" else EnvelopeCopy.ROLLOVER,
            ) {
                onToggleCarryover(!category.carryover)
            }
        }
        // Only while budget templates are on (spec 010 US5).
        if (note != null) CategoryNoteEditor(note, onSaveNote)
    }
}

/** The to-budget pick on the move screen, which moves money back out of the category. */
const val MOVE_TO_BUDGET = "to-budget"

/**
 * Moves money from one category: the amount (sums work, and all takes everything available), then where it
 * goes, picked from to budget and every other category with what each has available. Once an amount reads,
 * the source and the picked destination show what they will have after the move.
 */
@Composable
fun MoveCategoryScreen(
    shell: MonthShell,
    fromCategoryId: String,
    error: String?,
    onSave: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    initialTo: String = "",
    initialAmount: String = "",
) {
    val from = shell.groups.flatMap { it.categories }.first { it.id == fromCategoryId }
    val prefs = LocalFormatPrefs.current
    val options = formatOptions()
    val money = shell.currency
    var toId by remember { mutableStateOf(initialTo) }
    var amount by remember { mutableStateOf(initialAmount) }
    val moved = MoneyMath.evaluate(amount, prefs.numberFormat, money.decimals)?.takeIf { it > 0 } ?: 0L
    val envelope = shell.mode == BudgetMode.ENVELOPE
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter)
            .testTag("move-screen"),
    ) {
        MetroText("${EnvelopeCopy.MOVE} from ${from.name}", Metro.typography.subheader, maxLines = 2)
        MetroText(
            "available ${MoneyFormat.format(from.availableMinor - moved, money, options)}",
            Metro.typography.caption,
            Modifier.padding(top = 4.dp).testTag("move-from-available"),
            color = Metro.colors.secondary,
        )
        MetroField(amount, { amount = it }, "amount", Modifier.padding(top = MetroDimens.Gutter).testTag("move-amount"))
        // Quick picks: everything the category has, or what the picked category is overspent by.
        val target = shell.groups.flatMap { it.categories }.firstOrNull { it.id == toId }
        val cover =
            target
                ?.availableMinor
                ?.takeIf { it < 0 }
                ?.let { minOf(-it, from.availableMinor) }
                ?.takeIf { it > 0 }
        Row(horizontalArrangement = Arrangement.spacedBy(MetroDimens.Grid)) {
            if (from.availableMinor > 0) {
                QuickAmount("all ${MoneyFormat.format(from.availableMinor, money, options)}", "move-all") {
                    amount = prefs.editAmount(from.availableMinor, money.decimals)
                }
            }
            if (cover != null) {
                QuickAmount("cover ${MoneyFormat.format(cover, money, options)}", "move-cover") {
                    amount = prefs.editAmount(cover, money.decimals)
                }
            }
        }
        if (error != null) MetroText(error, Metro.typography.body, color = Metro.accent.text)
        MetroText("to", Metro.typography.caption, Modifier.padding(top = 4.dp), color = Metro.colors.secondary)
        LazyColumn(Modifier.weight(1f)) {
            if (envelope) {
                item(key = MOVE_TO_BUDGET) {
                    MoveTarget(
                        name = shell.headerLabel,
                        available = MoneyFormat.format(shell.headerMinor + if (toId == MOVE_TO_BUDGET) moved else 0L, money, options),
                        picked = toId == MOVE_TO_BUDGET,
                        banded = false,
                        tag = "move-to-$MOVE_TO_BUDGET",
                    ) { toId = MOVE_TO_BUDGET }
                }
            }
            var band = 0
            shell.groups.forEach { group ->
                val targets = group.categories.filter { it.id != fromCategoryId }
                if (targets.isEmpty()) return@forEach
                item(key = "group-${group.id}") {
                    MetroText(
                        group.name,
                        Metro.typography.groupHeader,
                        Modifier.padding(top = MetroDimens.Row, bottom = 4.dp),
                        color = Metro.colors.secondary,
                    )
                }
                targets.forEach { target ->
                    val banded = ledgerBanded(band++)
                    item(key = target.id) {
                        MoveTarget(
                            name = target.name,
                            available = MoneyFormat.format(target.availableMinor + if (toId == target.id) moved else 0L, money, options),
                            picked = toId == target.id,
                            banded = banded,
                            tag = "move-to-${target.id}",
                        ) { toId = target.id }
                    }
                }
            }
        }
        RememberFormSave { onSave(toId, prefs.readAmount(amount, money.decimals)) }
        if (!formSaveOnBar()) {
            MetroButton("move") { onSave(toId, prefs.readAmount(amount, money.decimals)) }
        }
    }
}

@Composable
private fun QuickAmount(
    label: String,
    tag: String,
    onPick: () -> Unit,
) {
    MetroText(
        label,
        Metro.typography.body,
        Modifier
            .heightIn(min = MetroDimens.TouchTarget)
            .metroPress(onClick = onPick)
            .padding(vertical = MetroDimens.Row)
            .testTag(tag),
        color = Metro.accent.text,
    )
}

@Composable
private fun MoveTarget(
    name: String,
    available: String,
    picked: Boolean,
    banded: Boolean,
    tag: String,
    onPick: () -> Unit,
) {
    val ink = if (picked) Metro.accent.onFill else Metro.colors.foreground
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .ledgerFill(
                if (picked) {
                    Metro.accent.fill
                } else if (banded) {
                    ledgerBand
                } else {
                    Color.Transparent
                },
            ).metroPress(onClick = onPick)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MetroText(name, Metro.typography.body, Modifier.weight(1f).padding(end = MetroDimens.Gutter), color = ink, maxLines = 2)
        MetroText(available, Metro.typography.body, color = if (picked) ink else Metro.accent.text, maxLines = 1, softWrap = false)
    }
}

@Composable
fun HoldMonthScreen(
    shell: MonthShell,
    error: String?,
    onSave: (String) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefs = LocalFormatPrefs.current
    var amount by remember { mutableStateOf("") }
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetroText(EnvelopeCopy.HOLD, Metro.typography.header)
        MetroText(
            "to budget ${MoneyFormat.format(shell.headerMinor, shell.currency, formatOptions())}",
            Metro.typography.subheader,
        )
        if (shell.bufferedMinor != 0L) {
            MetroText(
                "held ${MoneyFormat.format(shell.bufferedMinor, shell.currency, formatOptions())}",
                Metro.typography.caption,
                color = Metro.colors.secondary,
            )
        }
        MetroField(amount, { amount = it }, "amount for next month")
        if (error != null) MetroText(error, Metro.typography.body, color = Metro.accent.text)
        RememberFormSave { onSave(prefs.readAmount(amount, shell.currency.decimals)) }
        MetroButtonGrid {
            if (!formSaveOnBar()) {
                MetroButton(EnvelopeCopy.HOLD) { onSave(prefs.readAmount(amount, shell.currency.decimals)) }
            }
            if (shell.bufferedMinor != 0L) {
                MetroButton(EnvelopeCopy.RELEASE, onClick = onRelease)
            }
        }
    }
}

@Composable
fun ManageCategoriesScreen(
    manage: CategoryManagePage,
    error: String?,
    onAddGroup: (String) -> Unit,
    onAddCategory: (String, String) -> Unit,
    onRenameGroup: (String, String) -> Unit,
    onRenameCategory: (String, String) -> Unit,
    onHideCategory: (String, Boolean) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onMoveGroup: (String, Boolean) -> Unit,
    onMoveCategory: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var groupName by remember { mutableStateOf("") }
    var categoryName by remember { mutableStateOf("") }
    var groupId by remember {
        mutableStateOf(
            manage.groups
                .firstOrNull()
                ?.id
                .orEmpty(),
        )
    }
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(MetroDimens.Gutter),
    ) {
        MetroText(EnvelopeCopy.MANAGE, Metro.typography.header)
        MetroField(groupName, { groupName = it }, "new group")
        MetroButton("add group") { onAddGroup(groupName) }
        MetroField(categoryName, { categoryName = it }, "new category")
        LazyColumn(Modifier.weight(1f)) {
            manage.groups.forEach { group ->
                item(key = "manage-group-${group.id}") {
                    ManageGroupLine(
                        group = group,
                        selected = selectedGroupId == group.id,
                        onSelect = {
                            selectedGroupId = group.id
                            selectedCategoryId = null
                            renameText = group.name
                            groupId = group.id
                        },
                        onMoveEarlier = { onMoveGroup(group.id, true) },
                        onMoveLater = { onMoveGroup(group.id, false) },
                        onDelete = { onDeleteGroup(group.id) },
                    )
                }
                group.categories.forEach { category ->
                    item(key = "manage-cat-${category.id}") {
                        ManageCategoryLine(
                            category = category,
                            selected = selectedCategoryId == category.id,
                            onSelect = {
                                selectedCategoryId = category.id
                                selectedGroupId = null
                                renameText = category.name
                                groupId = group.id
                            },
                            onMoveEarlier = { onMoveCategory(category.id, true) },
                            onMoveLater = { onMoveCategory(category.id, false) },
                            onHide = { onHideCategory(category.id, !category.hidden) },
                            onDelete = { onDeleteCategory(category.id) },
                        )
                    }
                }
            }
        }
        MetroButton("add category") { onAddCategory(groupId, categoryName) }
        if (selectedGroupId != null || selectedCategoryId != null) {
            MetroField(renameText, { renameText = it }, "name", Modifier.testTag("manage-rename-field"))
            MetroButton(EnvelopeCopy.RENAME) {
                when {
                    selectedCategoryId != null -> onRenameCategory(selectedCategoryId!!, renameText)
                    selectedGroupId != null -> onRenameGroup(selectedGroupId!!, renameText)
                }
            }
        }
        if (error != null) MetroText(error, Metro.typography.body, color = Metro.accent.text)
    }
}

@Composable
private fun ManageGroupLine(
    group: ManageGroupRow,
    selected: Boolean,
    onSelect: () -> Unit,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Row actions show for the selected row only, as a button grid under its name.
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .metroPress(onClick = onSelect)
            .padding(vertical = MetroDimens.Row)
            .testTag("manage-group-${group.id}"),
    ) {
        MetroText(
            group.name,
            Metro.typography.subheader,
            color = if (selected) Metro.accent.text else Metro.colors.foreground,
        )
        if (selected) {
            MetroButtonGrid(Modifier.padding(top = MetroDimens.Row)) {
                MetroButton(EnvelopeCopy.MOVE_UP, onClick = onMoveEarlier)
                MetroButton(EnvelopeCopy.MOVE_DOWN, onClick = onMoveLater)
                MetroButton(EnvelopeCopy.DELETE, onClick = onDelete)
            }
        }
    }
}

@Composable
private fun ManageCategoryLine(
    category: ManageCategoryRow,
    selected: Boolean,
    onSelect: () -> Unit,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = if (category.hidden) "${category.name} (hidden)" else category.name
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .metroPress(onClick = onSelect)
            .padding(start = MetroDimens.Gutter, top = MetroDimens.Row, bottom = MetroDimens.Row)
            .testTag("manage-category-${category.id}"),
    ) {
        MetroText(
            label,
            Metro.typography.body,
            color = if (selected) Metro.accent.text else Metro.colors.foreground,
        )
        if (selected) {
            MetroButtonGrid(Modifier.padding(top = MetroDimens.Row)) {
                MetroButton(EnvelopeCopy.MOVE_UP, onClick = onMoveEarlier)
                MetroButton(EnvelopeCopy.MOVE_DOWN, onClick = onMoveLater)
                MetroButton(if (category.hidden) EnvelopeCopy.SHOW else EnvelopeCopy.HIDE, onClick = onHide)
                MetroButton(EnvelopeCopy.DELETE, onClick = onDelete)
            }
        }
    }
}

@Composable
fun CategoryLine(
    category: CategoryRow,
    shell: MonthShell,
    onOpen: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    formatOptions: FormatOptions = formatOptions(),
    banded: Boolean = false,
    /** Goals only show while templates are on, as Actual only judges them then. */
    showGoal: Boolean = LocalFormatPrefs.current.templatesOn,
) {
    val money = shell.currency
    val progress = if (showGoal) category.goalProgress else null
    Column(
        modifier
            .fillMaxWidth()
            .ledgerFill(if (banded) ledgerBand else Color.Transparent)
            .then(if (onOpen != null) Modifier.metroPress { onOpen(category.id) } else Modifier)
            .testTag("category-${category.id}"),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = MetroDimens.TouchTarget),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetroText(
                category.name,
                Metro.typography.body,
                Modifier.weight(if (Metro.style.wells) LEDGER_NAME_BESIDE_WELL else LEDGER_NAME).padding(end = MetroDimens.Gutter),
                color = if (category.hidden) Metro.colors.secondary else Metro.colors.foreground,
                maxLines = 2,
            )
            LedgerCell(
                MoneyFormat.format(category.budgetedMinor, money, formatOptions),
                Metro.typography.body,
                Metro.colors.foreground,
                "category-budgeted-${category.id}",
            )
            LedgerCell(
                MoneyFormat.format(category.spentMinor, money, formatOptions),
                Metro.typography.body,
                Metro.colors.foreground,
                "category-spent-${category.id}",
            )
            LedgerCell(
                MoneyFormat.format(category.availableMinor, money, formatOptions),
                Metro.typography.body,
                Metro.accent.text,
                "category-available-${category.id}",
                well = Metro.style.wells,
            )
        }
        if (progress != null) GoalBar(progress, Modifier.padding(bottom = GOAL_BAR_GAP).testTag("category-goal-${category.id}"))
    }
}

/** A thin bar across the row: the accent fills as much of the goal as is funded, the rest is a faint track. */
@Composable
private fun GoalBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    if (Metro.style.grooves) {
        GoalGroove(progress, modifier)
        return
    }
    Row(
        modifier
            .fillMaxWidth()
            .height(GOAL_BAR_HEIGHT)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f) },
    ) {
        if (progress > 0f) Box(Modifier.weight(progress).fillMaxHeight().background(Metro.accent.fill))
        if (progress < 1f) Box(Modifier.weight(1f - progress).fillMaxHeight().background(Metro.colors.secondary.copy(alpha = 0.25f)))
    }
}

/** Glow tiles' goal bar: a pressed-in groove, lit with the accent as far as the goal is funded. */
@Composable
private fun GoalGroove(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val fill = Metro.accent.fill
    Box(
        modifier
            .fillMaxWidth()
            .height(GOAL_GROOVE_HEIGHT)
            .relief(Relief.Inset, small = true, corner = PillCorner)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f) },
    ) {
        if (progress > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(fill.copy(alpha = 0.75f), fill)), RoundedCornerShape(PillCorner)),
            )
        }
    }
}

private val GOAL_GROOVE_HEIGHT = 8.dp
private val GOAL_BAR_HEIGHT = 3.dp
private val GOAL_BAR_GAP = 6.dp

@Composable
internal fun LedgerHeadings(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Spacer(Modifier.weight(if (Metro.style.wells) LEDGER_NAME_BESIDE_WELL else LEDGER_NAME))
        LedgerCell("budgeted", Metro.typography.caption, Metro.colors.secondary, "ledger-budgeted")
        LedgerCell("spent", Metro.typography.caption, Metro.colors.secondary, "ledger-spent")
        // Over soft clay's wells the heading takes the wider well column and sits over the figure inside it.
        val wells = Metro.style.wells
        MetroText(
            "available",
            Metro.typography.caption.copy(textAlign = TextAlign.End),
            Modifier
                .weight(if (wells) LEDGER_WELL else LEDGER_FIGURE)
                .padding(end = if (wells) 8.dp else 0.dp)
                .testTag("ledger-available"),
            color = Metro.colors.secondary,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
internal fun RowScope.LedgerCell(
    text: String,
    style: TextStyle,
    color: Color,
    tag: String,
    /** Soft clay sets the figure in a pressed-in well. */
    well: Boolean = false,
) {
    MetroText(
        text,
        style.copy(textAlign = TextAlign.End),
        Modifier
            .weight(if (well) LEDGER_WELL else LEDGER_FIGURE)
            .then(
                if (well) {
                    Modifier
                        .padding(start = 6.dp)
                        .relief(Relief.Inset, small = true, corner = PillCorner)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                } else {
                    Modifier
                },
            ).testTag(tag),
        color = color,
        maxLines = 1,
        softWrap = false,
    )
}

internal const val LEDGER_NAME = 1.8f
internal const val LEDGER_FIGURE = 1f

/** Soft clay's available column: wide enough for a figure and its well's padding. */
internal const val LEDGER_WELL = 1.35f

/** The name column gives up a little room to soft clay's wells, so every figure still fits. */
internal const val LEDGER_NAME_BESIDE_WELL = 1.5f
