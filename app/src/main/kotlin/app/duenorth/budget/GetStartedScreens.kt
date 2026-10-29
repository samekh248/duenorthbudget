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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.FormatOptions
import app.duenorth.budget.core.GetStartedCopy
import app.duenorth.budget.core.GetStartedLine
import app.duenorth.budget.core.GetStartedStep
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.StarterCategories
import app.duenorth.budget.core.StarterCategoriesCopy
import app.duenorth.budget.core.StarterGroup
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens

/**
 * A new budget's get started list over its month: one line per job, each opening the page that does it,
 * ticked once the budget shows it done. Tag `get-started`, and `get-started-<step>` on each line.
 */
@Composable
fun GetStartedList(
    lines: List<GetStartedLine>,
    shell: MonthShell,
    onStep: (GetStartedStep) -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
    formatOptions: FormatOptions = formatOptions(),
) {
    Column(modifier.fillMaxWidth().testTag("get-started")) {
        MetroText(GetStartedCopy.TITLE, Metro.typography.subheader)
        MetroText(GetStartedCopy.progress(lines), Metro.typography.caption, color = Metro.colors.secondary)
        lines.forEach { line ->
            val note =
                when {
                    line.step != GetStartedStep.ASSIGN || line.done || shell.headerMinor == 0L -> null
                    shell.headerMinor > 0 ->
                        GetStartedCopy.waiting(MoneyFormat.format(shell.headerMinor, shell.currency, formatOptions))
                    else -> GetStartedCopy.over(MoneyFormat.format(-shell.headerMinor, shell.currency, formatOptions))
                }
            GetStartedRow(line, note) { onStep(line.step) }
        }
        MetroText(
            GetStartedCopy.HIDE,
            Metro.typography.caption,
            Modifier
                .heightIn(min = MetroDimens.TouchTarget)
                .metroPress(onHide)
                .padding(vertical = MetroDimens.Row)
                .testTag("get-started-hide"),
            color = Metro.colors.secondary,
        )
    }
}

@Composable
private fun GetStartedRow(
    line: GetStartedLine,
    note: String?,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .metroPress(onClick)
            .padding(vertical = 6.dp)
            .testTag("get-started-${line.step.name.lowercase()}"),
        horizontalArrangement = Arrangement.spacedBy(MetroDimens.Row),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckSquare(line.done)
        Column(Modifier.weight(1f)) {
            val style = Metro.typography.body
            MetroText(
                GetStartedCopy.label(line.step),
                if (line.done) style.copy(textDecoration = TextDecoration.LineThrough) else style,
                color = if (line.done) Metro.colors.secondary else Metro.colors.foreground,
            )
            if (note != null) {
                // Warnings use the accent; red is kept for figures in the red.
                MetroText(note, Metro.typography.caption, color = Metro.accent.text)
            }
        }
    }
}

/**
 * Get started → set up categories, while the budget has none: starter groups to trim, each listing its
 * categories, added together from the bar. Tag `starter-categories`, and `starter-<n>` on each group.
 */
@Composable
fun StarterCategoriesScreen(
    error: String?,
    onAdd: (List<StarterGroup>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val picked = remember { mutableStateListOf(*StarterCategories.all.map { it.picked }.toTypedArray()) }
    val add = { onAdd(StarterCategories.all.filterIndexed { index, _ -> picked[index] }) }
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter)
            .testTag("starter-categories"),
        verticalArrangement = Arrangement.spacedBy(MetroDimens.Row),
    ) {
        MetroText(StarterCategoriesCopy.TITLE, Metro.typography.header)
        MetroText(StarterCategoriesCopy.LEAD, Metro.typography.caption, color = Metro.colors.secondary)
        if (error != null) MetroText(error, Metro.typography.body, color = Metro.accent.text)
        LazyColumn(Modifier.weight(1f).padding(top = MetroDimens.Row)) {
            itemsIndexed(StarterCategories.all, key = { _, group -> group.name }) { index, group ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = MetroDimens.TouchTarget)
                        .metroPress { picked[index] = !picked[index] }
                        .padding(vertical = MetroDimens.Row)
                        .testTag("starter-$index"),
                    horizontalArrangement = Arrangement.spacedBy(MetroDimens.Row),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CheckSquare(picked[index])
                    Column(Modifier.weight(1f)) {
                        MetroText(group.name, Metro.typography.subheader)
                        MetroText(group.categories.joinToString(", "), Metro.typography.caption, color = Metro.colors.secondary)
                    }
                }
            }
        }
        RememberFormSave(add)
        if (!formSaveOnBar()) MetroButton(StarterCategoriesCopy.ADD, Modifier.testTag("starter-add"), onClick = add)
    }
}
