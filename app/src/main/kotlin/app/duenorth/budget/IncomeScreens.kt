package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.AutoHoldChoice
import app.duenorth.budget.core.BudgetMode
import app.duenorth.budget.core.FormatOptions
import app.duenorth.budget.core.IncomeCategoryRow
import app.duenorth.budget.core.IncomeGroupRow
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.MoneyParse
import app.duenorth.budget.core.MonthFillCopy
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.monthLabel
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.components.MetroOutlineButton
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens

/** Words for income groups (spec 010). */
object IncomeCopy {
    const val RECEIVED = "received"
    const val BUDGETED = "budgeted"
    const val HOLD_ON = "on"
    const val HOLD_OFF_MONTH = "off this month"
    const val HOLD_OFF = "off"
    const val HOLDING = "holds this month's income for next month"
    const val NOT_HOLDING = "income counts this month"
    const val HOLD_NOTE = "a manual hold for a month still wins for that month"
}

fun MonthShell.incomeCategory(id: String): IncomeCategoryRow? = incomeGroups.flatMap { it.categories }.firstOrNull { it.id == id }

/** One income group on Home: its name and what came in this month. */
@Composable
fun IncomeGroupLine(
    group: IncomeGroupRow,
    shell: MonthShell,
    modifier: Modifier = Modifier,
    formatOptions: FormatOptions = FormatOptions(),
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .padding(top = MetroDimens.Row)
            .testTag("income-group-${group.id}"),
        verticalAlignment = Alignment.Top,
    ) {
        MetroText(
            group.name,
            Metro.typography.groupHeader,
            Modifier
                .weight(1f)
                .padding(end = 12.dp),
            maxLines = 2,
        )
        Column(horizontalAlignment = Alignment.End) {
            MetroText(
                MoneyFormat.format(group.receivedMinor, shell.currency, formatOptions),
                Metro.typography.subheader.copy(textAlign = TextAlign.End),
                Modifier.testTag("income-group-received-${group.id}"),
                color = Metro.accent.text,
                maxLines = 1,
                softWrap = false,
            )
            MetroText(IncomeCopy.RECEIVED, Metro.typography.caption, color = Metro.colors.secondary)
        }
    }
}

/**
 * One income category under its group on Home, on the same columns as the ledger:
 * budgeted on tracking files, and received under available.
 */
@Composable
fun IncomeCategoryLine(
    category: IncomeCategoryRow,
    shell: MonthShell,
    onOpen: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    formatOptions: FormatOptions = FormatOptions(),
) {
    val money = shell.currency
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .then(if (onOpen != null) Modifier.metroPress { onOpen(category.id) } else Modifier)
            .testTag("income-category-${category.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MetroText(
            category.name,
            Metro.typography.body,
            Modifier.weight(LEDGER_NAME).padding(end = MetroDimens.Gutter),
            maxLines = 2,
        )
        if (shell.mode == BudgetMode.TRACKING) {
            LedgerCell(
                MoneyFormat.format(category.budgetedMinor, money, formatOptions),
                Metro.typography.body,
                Metro.colors.foreground,
                "income-budgeted-${category.id}",
            )
        } else {
            Spacer(Modifier.weight(LEDGER_FIGURE))
        }
        Spacer(Modifier.weight(LEDGER_FIGURE))
        LedgerCell(
            MoneyFormat.format(category.receivedMinor, money, formatOptions),
            Metro.typography.body,
            Metro.accent.text,
            "income-received-${category.id}",
        )
    }
}

/**
 * One income category for the month. Envelope files offer Actual's auto-hold choices;
 * tracking files budget income like any category.
 */
@Composable
fun IncomeCategoryScreen(
    shell: MonthShell,
    category: IncomeCategoryRow,
    error: String?,
    onSave: (String) -> Unit,
    onAutoHold: (AutoHoldChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    var amount by remember(category) { mutableStateOf(MoneyParse.formatMagnitude(category.budgetedMinor, shell.currency.decimals)) }
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter)
            .testTag("income-category-screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetroText(category.name, Metro.typography.header)
        MetroText(monthLabel(shell.month), Metro.typography.caption, color = Metro.colors.secondary)
        MetroText(
            "${IncomeCopy.RECEIVED} ${MoneyFormat.format(category.receivedMinor, shell.currency)}",
            Metro.typography.subheader,
            Modifier.testTag("income-received"),
        )
        if (shell.mode == BudgetMode.TRACKING) {
            MetroField(amount, { amount = it }, IncomeCopy.BUDGETED, Modifier.testTag("income-budget-amount"))
            if (error != null) MetroText(error, Metro.typography.body, color = Metro.accent.text)
            RememberFormSave { onSave(amount) }
            if (!formSaveOnBar()) {
                MetroButton("save") { onSave(amount) }
            }
        } else {
            MetroText(MonthFillCopy.AUTO_HOLD, Metro.typography.subheader)
            MetroText(
                if (category.autoHold) IncomeCopy.HOLDING else IncomeCopy.NOT_HOLDING,
                Metro.typography.body,
                Modifier.testTag("auto-hold-state"),
                color = if (category.autoHold) Metro.accent.text else Metro.colors.secondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter)) {
                MetroOutlineButton(IncomeCopy.HOLD_ON, Modifier.testTag("auto-hold-on")) { onAutoHold(AutoHoldChoice.ON) }
                MetroOutlineButton(IncomeCopy.HOLD_OFF_MONTH, Modifier.testTag("auto-hold-off-month")) {
                    onAutoHold(AutoHoldChoice.OFF_THIS_MONTH)
                }
                MetroOutlineButton(IncomeCopy.HOLD_OFF, Modifier.testTag("auto-hold-off")) { onAutoHold(AutoHoldChoice.OFF) }
            }
            if (error != null) MetroText(error, Metro.typography.body, color = Metro.accent.text)
            MetroText(IncomeCopy.HOLD_NOTE, Metro.typography.caption, color = Metro.colors.secondary)
        }
    }
}
