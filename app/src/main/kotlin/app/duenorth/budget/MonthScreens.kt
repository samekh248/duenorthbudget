package app.duenorth.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.CategoryMonth
import app.duenorth.budget.core.CurrencySpec
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.ShellCopy
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens

@Composable
fun GroupScreen(
    groupName: String,
    categories: List<CategoryMonth>,
    currency: CurrencySpec,
    notice: String?,
    onListGesture: (Boolean) -> Unit,
    onCategory: (String) -> Unit,
    onAssign: (String, String) -> Unit,
    onRename: (String) -> Unit,
    onAdd: (String) -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDelete: () -> Unit,
    onShow: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = categories.filter { !it.hidden }
    val hidden = categories.filter { it.hidden }
    var groupTitle by remember(groupName) { mutableStateOf(groupName) }
    var categoryName by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collect { onListGesture(it) }
    }
    Column(modifier.fillMaxSize().padding(horizontal = MetroDimens.Gutter)) {
        MetroField(groupTitle, { groupTitle = it }, ShellCopy.RENAME, Modifier.testTag("group-name-field"))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TextAction(ShellCopy.RENAME, Modifier.testTag("rename-group")) { onRename(groupTitle) }
            TextAction(ShellCopy.UP, Modifier.testTag("group-up")) { onUp() }
            TextAction(ShellCopy.DOWN, Modifier.testTag("group-down")) { onDown() }
        }
        Notice(notice)
        LazyColumn(Modifier.weight(1f), state = list) {
            items(visible, key = { it.id }) { category ->
                CategoryLine(category, currency, onCategory, onAssign)
            }
            if (hidden.isNotEmpty()) {
                item { MetroText(ShellCopy.HIDDEN, Metro.typography.caption, color = Metro.colors.secondary) }
                items(hidden, key = { "hidden-${it.id}" }) { category ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = MetroDimens.TouchTarget),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MetroText(category.name, Metro.typography.body, Modifier.weight(1f))
                        TextAction(ShellCopy.SHOW, Modifier.testTag("show-${category.id}")) { onShow(category.id) }
                    }
                }
            }
        }
        MetroField(categoryName, { categoryName = it }, ShellCopy.NEW_CATEGORY, Modifier.testTag("new-category-field"))
        MetroButton(ShellCopy.NEW_CATEGORY, Modifier.testTag("new-category"), onClick = { onAdd(categoryName) })
        TextAction(ShellCopy.DELETE, Modifier.padding(top = 8.dp).testTag("delete-group")) { onDelete() }
    }
}

@Composable
private fun CategoryLine(
    category: CategoryMonth,
    currency: CurrencySpec,
    onCategory: (String) -> Unit,
    onAssign: (String, String) -> Unit,
) {
    var amount by remember(category.id, category.budgetedMinor) { mutableStateOf(MoneyFormat.plain(category.budgetedMinor, currency)) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("category-${category.id}")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            MetroText(
                category.name,
                Metro.typography.subheader,
                Modifier
                    .weight(1f)
                    .testTag("category-name-${category.id}")
                    .metroPress { onCategory(category.id) },
                maxLines = 2,
            )
            MetroText(
                MoneyFormat.format(category.availableMinor, currency),
                Metro.typography.subheader,
                Modifier.testTag("category-available-${category.id}"),
            )
        }
        MetroText(
            ShellCopy.SPENT + " " + MoneyFormat.format(category.spentMinor, currency),
            Metro.typography.caption,
            color = Metro.colors.secondary,
        )
        MetroField(amount, { amount = it }, ShellCopy.BUDGETED, Modifier.testTag("budgeted-${category.id}"))
        TextAction(ShellCopy.ASSIGN, Modifier.testTag("assign-${category.id}")) { onAssign(category.id, amount) }
    }
}

@Composable
fun CategoryScreen(
    category: CategoryMonth,
    others: List<CategoryMonth>,
    currency: CurrencySpec,
    notice: String?,
    onAssign: (String) -> Unit,
    onMove: (String, String) -> Unit,
    onCarryover: (Boolean) -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var budgeted by remember(category.id, category.budgetedMinor) { mutableStateOf(MoneyFormat.plain(category.budgetedMinor, currency)) }
    var moveAmount by remember(category.id) { mutableStateOf("") }
    val moveLabel = if (category.availableMinor < 0) ShellCopy.COVER else ShellCopy.MOVE
    Column(modifier.fillMaxSize().padding(horizontal = MetroDimens.Gutter)) {
        MetroText(category.name, Metro.typography.header, maxLines = 2)
        MetroText(
            ShellCopy.AVAILABLE + " " + MoneyFormat.format(category.availableMinor, currency),
            Metro.typography.body,
            Modifier.testTag("category-available"),
        )
        MetroText(
            ShellCopy.SPENT + " " + MoneyFormat.format(category.spentMinor, currency),
            Metro.typography.caption,
            color = Metro.colors.secondary,
        )
        MetroField(budgeted, { budgeted = it }, ShellCopy.BUDGETED, Modifier.testTag("budgeted-field"))
        MetroButton(ShellCopy.ASSIGN, Modifier.testTag("assign"), onClick = { onAssign(budgeted) })
        Notice(notice)
        MetroField(moveAmount, { moveAmount = it }, moveLabel, Modifier.padding(top = 16.dp).testTag("move-field"))
        LazyColumn(Modifier.weight(1f)) {
            items(others, key = { it.id }) { other ->
                TextAction(
                    "$moveLabel ${other.name}",
                    Modifier.testTag("move-${other.id}"),
                ) { onMove(other.id, moveAmount) }
            }
        }
        TextAction(
            if (category.carryover) ShellCopy.STOP_ROLLOVER else ShellCopy.ROLLOVER,
            Modifier.testTag("rollover"),
        ) { onCarryover(!category.carryover) }
        TextAction(ShellCopy.HIDE, Modifier.testTag("hide")) { onHide() }
        TextAction(ShellCopy.DELETE, Modifier.testTag("delete-category")) { onDelete() }
    }
}

@Composable
fun HoldScreen(
    held: Long,
    currency: CurrencySpec,
    notice: String?,
    onHold: (String) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var amount by remember(held) { mutableStateOf(MoneyFormat.plain(held, currency)) }
    Column(
        modifier.fillMaxSize().padding(horizontal = MetroDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetroText(ShellCopy.HELD, Metro.typography.header)
        MetroField(amount, { amount = it }, ShellCopy.HOLD, Modifier.testTag("hold-field"))
        Notice(notice)
        MetroButton(ShellCopy.HOLD, Modifier.testTag("save-hold"), onClick = { onHold(amount) })
        MetroButton(ShellCopy.RELEASE, Modifier.testTag("release-hold"), onClick = onRelease)
    }
}

@Composable
fun NameScreen(
    title: String,
    notice: String?,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    Column(
        modifier.fillMaxSize().padding(horizontal = MetroDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetroText(title, Metro.typography.header)
        MetroField(name, { name = it }, ShellCopy.ENTER_NAME, Modifier.testTag("name-field"))
        Notice(notice)
        MetroButton(title, Modifier.testTag("save-name"), onClick = { onSave(name) })
    }
}

@Composable
private fun Notice(notice: String?) {
    if (notice != null) {
        MetroText(notice, Metro.typography.body, Modifier.padding(top = 8.dp).testTag("notice"), color = Metro.colors.secondary)
    }
}

@Composable
private fun TextAction(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    MetroText(
        label,
        Metro.typography.body,
        modifier.heightIn(min = MetroDimens.TouchTarget).metroPress(onClick).padding(vertical = 8.dp),
        color = Metro.accent.text,
    )
}
