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

/** Asks before a fill replaces amounts already budgeted; nothing is written until [onConfirm]. */
@Composable
fun FillConfirmScreen(
    confirm: FillConfirm,
    month: YearMonth,
    onConfirm: () -> Unit,
    onKeep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ConfirmPage(
        title = MonthActionCopy.fillTitle(confirm.kind),
        caption = monthLabel(month),
        body = confirm.reason,
        yes = if (confirm.kind == MonthFillKind.ZERO) MonthActionCopy.ZERO else MonthActionCopy.REPLACE,
        onYes = onConfirm,
        onKeep = onKeep,
        tag = "fill-confirm",
        modifier = modifier,
    )
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
    val prefs = LocalFormatPrefs.current
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
                "available ${MoneyFormat.format(category.availableMinor, shell.currency, formatOptions())}",
                Metro.typography.subheader,
                Modifier.testTag("to-budget-available"),
            )
        }
        MetroText(
            "${MonthActionCopy.TOTAL} ${MoneyFormat.format(shell.headerMinor, shell.currency, formatOptions())}",
            Metro.typography.body,
            Modifier.testTag("to-budget-header"),
            color = Metro.colors.secondary,
        )
        MetroField(amount, { amount = it }, "amount", Modifier.testTag("to-budget-amount"))
        if (error != null) MetroText(error, Metro.typography.body, Modifier.testTag("to-budget-error"), color = Metro.accent.text)
        RememberFormSave { onSave(prefs.readAmount(amount, shell.currency.decimals)) }
        if (!formSaveOnBar()) {
            MetroButton(
                MonthActionCopy.TRANSFER,
                Modifier.testTag("to-budget-save"),
            ) { onSave(prefs.readAmount(amount, shell.currency.decimals)) }
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
