package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.CategoryRow
import app.duenorth.budget.core.EnvelopeCopy
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.MoneyParse
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.monthLabel
import app.duenorth.budget.design.components.MetroButton
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
        MetroButton("save") { onSave(amount) }
        MetroButton(EnvelopeCopy.MOVE, onClick = onMove)
        MetroButton(
            if (category.carryover) "stop ${EnvelopeCopy.ROLLOVER}" else EnvelopeCopy.ROLLOVER,
        ) {
            onToggleCarryover(!category.carryover)
        }
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
        MetroField(amount, { amount = it }, "amount", Modifier.padding(vertical = 8.dp))
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
                        .padding(vertical = 8.dp),
                    color = if (toId == target.id) Metro.accent.text else Metro.colors.foreground,
                )
            }
        }
        MetroButton("move") { onSave(toId, amount) }
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
                Metro.typography.body,
            )
        }
        MetroField(amount, { amount = it }, "amount for next month")
        if (error != null) MetroText(error, Metro.typography.body, color = Metro.accent.text)
        MetroButton(EnvelopeCopy.HOLD) { onSave(amount) }
        if (shell.bufferedMinor != 0L) {
            MetroButton(EnvelopeCopy.RELEASE, onClick = onRelease)
        }
    }
}

@Composable
fun ManageCategoriesScreen(
    shell: MonthShell,
    error: String?,
    onAddGroup: (String) -> Unit,
    onAddCategory: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var groupName by remember { mutableStateOf("") }
    var categoryName by remember { mutableStateOf("") }
    var groupId by remember { mutableStateOf(shell.groups.firstOrNull()?.id.orEmpty()) }
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetroText(EnvelopeCopy.MANAGE, Metro.typography.header)
        MetroField(groupName, { groupName = it }, "new group")
        MetroButton("add group") { onAddGroup(groupName) }
        MetroField(categoryName, { categoryName = it }, "new category")
        LazyColumn(Modifier.weight(1f)) {
            items(shell.groups, key = { it.id }) { group ->
                MetroText(
                    group.name,
                    Metro.typography.subheader,
                    Modifier
                        .fillMaxWidth()
                        .metroPress { groupId = group.id }
                        .padding(vertical = 8.dp),
                    color = if (groupId == group.id) Metro.accent.text else Metro.colors.foreground,
                )
            }
        }
        MetroButton("add category") { onAddCategory(groupId, categoryName) }
        if (error != null) MetroText(error, Metro.typography.body, color = Metro.accent.text)
    }
}

@Composable
fun CategoryLine(
    category: CategoryRow,
    shell: MonthShell,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .metroPress { onOpen(category.id) }
            .padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
            .testTag("category-${category.id}"),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MetroText(category.name, Metro.typography.body, Modifier.weight(1f), maxLines = 2)
        MetroText(
            MoneyFormat.format(category.availableMinor, shell.currency),
            Metro.typography.body,
        )
    }
}
