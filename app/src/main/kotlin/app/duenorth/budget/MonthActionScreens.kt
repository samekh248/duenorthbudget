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
import app.duenorth.budget.core.CurrencySpec
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.MonthFillCopy
import app.duenorth.budget.core.MonthFillKind
import app.duenorth.budget.core.MonthShell
import app.duenorth.budget.core.monthLabel
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroButtonGrid
import app.duenorth.budget.design.components.MetroField
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
    const val UNDO = "undo"
    const val UNDONE = "undid"
    const val UNDO_CONFLICT = "changed since; not undone, see conflicts"

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
        MetroButtonGrid {
            MetroButton(
                if (confirm.kind == MonthFillKind.ZERO) MonthActionCopy.ZERO else MonthActionCopy.REPLACE,
                Modifier.testTag("fill-confirm-yes"),
                onClick = onConfirm,
            )
            MetroButton(MonthActionCopy.KEEP, Modifier.testTag("fill-confirm-keep"), onClick = onKeep)
        }
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

/** One line under the Home totals after a month action, such as "nothing to copy". */
@Composable
fun MonthNotice(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Metro.colors.secondary,
) {
    MetroText(text, Metro.typography.caption, modifier.testTag("month-notice"), color = color)
}
