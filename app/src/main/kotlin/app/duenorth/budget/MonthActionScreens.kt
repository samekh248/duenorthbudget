package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.ActualMonthMath
import app.duenorth.budget.core.AutoHoldChoice
import app.duenorth.budget.core.CurrencySpec
import app.duenorth.budget.core.IncomeHoldRow
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.MonthFillCopy
import app.duenorth.budget.core.MonthFillKind
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.monthLabel
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.components.MetroOutlineButton
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import java.time.YearMonth

/** Words for the month actions of spec 010 (`contracts/month-actions.md`). */
object MonthActionCopy {
    const val INCOME = "income"
    const val CARRIED = "from last month"
    const val OVERSPENT = "overspent last month"
    const val BUDGETED = "budgeted"
    const val HELD = "held for next month"
    const val TOTAL = "to budget"
    const val REPLACE = "replace"
    const val ZERO = "set to zero"
    const val KEEP = "keep"
    const val TRANSFER = "move to to-budget"
    const val NO_INCOME = "no income categories"
    const val HOLD_ON = "on"
    const val HOLD_OFF_MONTH = "off this month"
    const val HOLD_OFF = "off"
    const val HOLDING = "holds this month's income for next month"
    const val NOT_HOLDING = "income counts this month"
    const val UNDO = "undo"
    const val UNDONE = "undid"
    const val UNDO_CONFLICT = "changed since; not undone, see conflicts"
    const val AUTO_HOLD_NOTE = "a manual hold for a month still wins for that month"

    fun fillTitle(kind: MonthFillKind): String =
        when (kind) {
            MonthFillKind.COPY_LAST -> MonthFillCopy.COPY_LAST
            MonthFillKind.AVERAGE -> MonthFillCopy.AVERAGE
            MonthFillKind.ZERO -> MonthFillCopy.ZERO
        }
}

/** A fill that is waiting for the person to confirm replacing what is budgeted. */
data class FillConfirm(
    val kind: MonthFillKind,
    val reason: String,
)

/** The five parts of to-budget for the open month; envelope only. Tag `breakdown-screen`. */
@Composable
fun BreakdownScreen(
    month: YearMonth,
    breakdown: ActualMonthMath.MonthBreakdown,
    currency: CurrencySpec,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter)
            .testTag("breakdown-screen"),
    ) {
        MetroText(MonthFillCopy.BREAKDOWN, Metro.typography.header)
        MetroText(monthLabel(month), Metro.typography.caption, color = Metro.colors.secondary)
        Spacer(Modifier.height(MetroDimens.Grid))
        BreakdownLine(MonthActionCopy.INCOME, breakdown.incomeMinor, currency, "breakdown-income")
        BreakdownLine(MonthActionCopy.CARRIED, breakdown.carriedMinor, currency, "breakdown-carried")
        BreakdownLine(MonthActionCopy.OVERSPENT, breakdown.overspentMinor, currency, "breakdown-overspent")
        BreakdownLine(MonthActionCopy.BUDGETED, breakdown.budgetedMinor, currency, "breakdown-budgeted")
        BreakdownLine(MonthActionCopy.HELD, breakdown.heldMinor, currency, "breakdown-held")
        Spacer(
            Modifier
                .padding(vertical = MetroDimens.Row)
                .fillMaxWidth()
                .height(1.dp)
                .background(if (Metro.colors.isDark) Metro.colors.outline else Metro.colors.chrome),
        )
        BreakdownLine(
            MonthActionCopy.TOTAL,
            breakdown.totalMinor,
            currency,
            "breakdown-total",
            emphasis = true,
        )
    }
}

@Composable
private fun BreakdownLine(
    label: String,
    amountMinor: Long,
    currency: CurrencySpec,
    tag: String,
    emphasis: Boolean = false,
) {
    val style = if (emphasis) Metro.typography.subheader else Metro.typography.body
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MetroText(label, style, Modifier.weight(1f), color = if (emphasis) Metro.colors.foreground else Metro.colors.secondary)
        MetroText(
            MoneyFormat.format(amountMinor, currency),
            style.copy(textAlign = TextAlign.End),
            Modifier.testTag("$tag-amount"),
            color = if (emphasis) Metro.accent.text else Metro.colors.foreground,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Asks before a fill replaces amounts already budgeted; nothing is written until [onConfirm]. */
@Composable
fun FillConfirmScreen(
    confirm: FillConfirm,
    month: YearMonth,
    onConfirm: () -> Unit,
    onKeep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter)
            .testTag("fill-confirm"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MetroText(MonthActionCopy.fillTitle(confirm.kind), Metro.typography.header)
        MetroText(monthLabel(month), Metro.typography.caption, color = Metro.colors.secondary)
        MetroText(confirm.reason, Metro.typography.subheader)
        MetroButton(
            if (confirm.kind == MonthFillKind.ZERO) MonthActionCopy.ZERO else MonthActionCopy.REPLACE,
            Modifier.testTag("fill-confirm-yes"),
            onClick = onConfirm,
        )
        MetroButton(MonthActionCopy.KEEP, Modifier.testTag("fill-confirm-keep"), onClick = onKeep)
    }
}

/** Moves part of a category's budget back to to-budget (envelope only). */
@Composable
fun ToBudgetScreen(
    shell: MonthShell,
    categoryId: String,
    error: String?,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val category = shell.groups.flatMap { it.categories }.firstOrNull { it.id == categoryId }
    var amount by remember { mutableStateOf("") }
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter)
            .testTag("to-budget-screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetroText(MonthActionCopy.TRANSFER, Metro.typography.header)
        MetroText(
            listOfNotNull(category?.name, monthLabel(shell.month)).joinToString(" · "),
            Metro.typography.caption,
            color = Metro.colors.secondary,
        )
        if (category != null) {
            MetroText(
                "available ${MoneyFormat.format(category.availableMinor, shell.currency)}",
                Metro.typography.subheader,
                Modifier.testTag("to-budget-available"),
            )
        }
        MetroText(
            "${MonthActionCopy.TOTAL} ${MoneyFormat.format(shell.headerMinor, shell.currency)}",
            Metro.typography.body,
            Modifier.testTag("to-budget-header"),
            color = Metro.colors.secondary,
        )
        MetroField(amount, { amount = it }, "amount", Modifier.testTag("to-budget-amount"))
        if (error != null) MetroText(error, Metro.typography.body, Modifier.testTag("to-budget-error"), color = Metro.accent.text)
        RememberFormSave { onSave(amount) }
        if (!formSaveOnBar()) {
            MetroButton(MonthActionCopy.TRANSFER, Modifier.testTag("to-budget-save")) { onSave(amount) }
        }
    }
}

/** Income auto-hold, with Actual's three choices for each income category. */
@Composable
fun AutoHoldScreen(
    month: YearMonth,
    rows: List<IncomeHoldRow>,
    error: String?,
    onChoose: (String, AutoHoldChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter)
            .testTag("auto-hold-screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetroText(MonthFillCopy.AUTO_HOLD, Metro.typography.header)
        MetroText(monthLabel(month), Metro.typography.caption, color = Metro.colors.secondary)
        if (error != null) MetroText(error, Metro.typography.body, color = Metro.accent.text)
        if (rows.isEmpty()) {
            MetroText(MonthActionCopy.NO_INCOME, Metro.typography.caption, color = Metro.colors.secondary)
        }
        LazyColumn(Modifier.weight(1f)) {
            items(rows, key = { it.id }) { row ->
                AutoHoldLine(row, onChoose)
            }
        }
        MetroText(MonthActionCopy.AUTO_HOLD_NOTE, Metro.typography.caption, color = Metro.colors.secondary)
    }
}

@Composable
private fun AutoHoldLine(
    row: IncomeHoldRow,
    onChoose: (String, AutoHoldChoice) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = MetroDimens.Row)
            .testTag("auto-hold-${row.id}"),
        verticalArrangement = Arrangement.spacedBy(MetroDimens.Row),
    ) {
        MetroText(row.name, Metro.typography.subheader)
        MetroText(
            if (row.on) MonthActionCopy.HOLDING else MonthActionCopy.NOT_HOLDING,
            Metro.typography.caption,
            Modifier.testTag("auto-hold-${row.id}-state"),
            color = if (row.on) Metro.accent.text else Metro.colors.secondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter)) {
            HoldChoice(row, AutoHoldChoice.ON, MonthActionCopy.HOLD_ON, "on", onChoose)
            HoldChoice(row, AutoHoldChoice.OFF_THIS_MONTH, MonthActionCopy.HOLD_OFF_MONTH, "off-month", onChoose)
            HoldChoice(row, AutoHoldChoice.OFF, MonthActionCopy.HOLD_OFF, "off", onChoose)
        }
    }
}

@Composable
private fun HoldChoice(
    row: IncomeHoldRow,
    choice: AutoHoldChoice,
    label: String,
    suffix: String,
    onChoose: (String, AutoHoldChoice) -> Unit,
) {
    MetroOutlineButton(label, Modifier.testTag("auto-hold-${row.id}-$suffix")) { onChoose(row.id, choice) }
}

/** One line under the Home totals after a month action, such as "nothing to copy". */
@Composable
fun MonthNotice(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Metro.colors.secondary,
) {
    MetroText(text, Metro.typography.caption, modifier.testTag("month-notice"), color = color)
}
