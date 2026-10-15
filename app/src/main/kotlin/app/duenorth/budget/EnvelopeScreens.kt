package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.CategoryManagePage
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.EnvelopeCopy
import app.duenorth.budget.core.FormatOptions
import app.duenorth.budget.core.ManageCategoryRow
import app.duenorth.budget.core.ManageGroupRow
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.MoneyParse
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.monthLabel
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroButtonGrid
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.metroPress
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
    var amount by remember(category) { mutableStateOf(MoneyParse.formatMagnitude(category.budgetedMinor, shell.currency.decimals)) }
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
            "available ${MoneyFormat.format(category.availableMinor, shell.currency)}",
            Metro.typography.subheader,
        )
        MetroField(amount, { amount = it }, "budgeted", Modifier.testTag("category-budget-amount"))
        if (error != null) MetroText(error, Metro.typography.body, color = Metro.accent.text)
        RememberFormSave { onSave(amount) }
        MetroButtonGrid {
            if (!formSaveOnBar()) {
                MetroButton("save") { onSave(amount) }
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

@Composable
fun MoveCategoryScreen(
    shell: MonthShell,
    fromCategoryId: String,
    error: String?,
    onSave: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val from = shell.groups.flatMap { it.categories }.first { it.id == fromCategoryId }
    var toId by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    val targets = shell.groups.flatMap { it.categories }.filter { it.id != fromCategoryId }
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter),
    ) {
        MetroText("${EnvelopeCopy.MOVE} from ${from.name}", Metro.typography.header)
        MetroField(amount, { amount = it }, "amount", Modifier.padding(vertical = MetroDimens.Gutter))
        if (error != null) MetroText(error, Metro.typography.body, color = Metro.accent.text)
        LazyColumn(Modifier.weight(1f)) {
            items(targets, key = { it.id }) { target ->
                MetroText(
                    target.name,
                    Metro.typography.subheader,
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = MetroDimens.TouchTarget)
                        .metroPress { toId = target.id }
                        .padding(vertical = MetroDimens.Row),
                    color = if (toId == target.id) Metro.accent.text else Metro.colors.foreground,
                )
            }
        }
        RememberFormSave { onSave(toId, amount) }
        if (!formSaveOnBar()) {
            MetroButton("move") { onSave(toId, amount) }
        }
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
            "to budget ${MoneyFormat.format(shell.headerMinor, shell.currency)}",
            Metro.typography.subheader,
        )
        if (shell.bufferedMinor != 0L) {
            MetroText(
                "held ${MoneyFormat.format(shell.bufferedMinor, shell.currency)}",
                Metro.typography.caption,
                color = Metro.colors.secondary,
            )
        }
        MetroField(amount, { amount = it }, "amount for next month")
        if (error != null) MetroText(error, Metro.typography.body, color = Metro.accent.text)
        RememberFormSave { onSave(amount) }
        MetroButtonGrid {
            if (!formSaveOnBar()) {
                MetroButton(EnvelopeCopy.HOLD) { onSave(amount) }
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
    formatOptions: FormatOptions = FormatOptions(),
) {
    val money = shell.currency
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .then(if (onOpen != null) Modifier.metroPress { onOpen(category.id) } else Modifier)
            .testTag("category-${category.id}"),
    ) {
        Row(
            Modifier.fillMaxWidth().align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetroText(
                category.name,
                Metro.typography.body,
                Modifier.weight(LEDGER_NAME).padding(end = MetroDimens.Gutter),
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
            )
        }
        Row(Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
            Spacer(Modifier.weight(LEDGER_NAME))
            Box(
                Modifier
                    .weight(LEDGER_FIGURE * 3)
                    .height(1.dp)
                    .background(if (Metro.colors.isDark) Metro.colors.outline else Metro.colors.chrome)
                    .testTag("category-rule-${category.id}"),
            )
        }
    }
}

@Composable
internal fun LedgerHeadings(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Spacer(Modifier.weight(LEDGER_NAME))
        LedgerCell("budgeted", Metro.typography.caption, Metro.colors.secondary, "ledger-budgeted")
        LedgerCell("spent", Metro.typography.caption, Metro.colors.secondary, "ledger-spent")
        LedgerCell("available", Metro.typography.caption, Metro.colors.secondary, "ledger-available")
    }
}

@Composable
internal fun RowScope.LedgerCell(
    text: String,
    style: TextStyle,
    color: Color,
    tag: String,
) {
    MetroText(
        text,
        style.copy(textAlign = TextAlign.End),
        Modifier.weight(LEDGER_FIGURE).testTag(tag),
        color = color,
        maxLines = 1,
        softWrap = false,
    )
}

internal const val LEDGER_NAME = 1.8f
internal const val LEDGER_FIGURE = 1f
