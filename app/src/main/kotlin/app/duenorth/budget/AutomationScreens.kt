package app.duenorth.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.CategoryAutomations
import app.duenorth.budget.core.CurrencySpec
import app.duenorth.budget.core.FormatOptions
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.core.TemplateJson
import app.duenorth.budget.core.TemplateKind
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.styleCard
import app.duenorth.budget.design.theme.Metro

// Desktop's budget automations (Actual's experimental flags.goalTemplatesUIEnabled) keep a category's templates in
// categories.goal_def instead of its note. The category page shows them as tiles; for now they are changed on desktop.

object AutomationCopy {
    const val SECTION = "budget automations"
    const val OWNER = "set up in Actual's budget automations. change them there for now."
    const val EMPTY = "no automations yet."
    const val NOT_RUN = "the phone can't run one of these yet, so filling from templates leaves this category alone."
    const val UNSUPPORTED = "change this one in Actual on a computer"

    /** What desktop calls an automation the phone cannot run yet, from its stored `type`. */
    fun typeLabel(type: String?): String =
        when (type) {
            "periodic" -> "fixed amount, repeating"
            "spend" -> "spend by a date"
            "copy" -> "copy a past month"
            "refill" -> "refill to a limit"
            "limit" -> "limit"
            "simple" -> "fixed amount with a held limit"
            "schedule" -> "schedule with an adjustment"
            "average" -> "average with an adjustment"
            "remainder" -> "what's left, with a limit"
            "by" -> "save by a date from another month"
            null -> "an automation this phone can't read"
            else -> type
        }
}

/**
 * The category page's budget automations, read-only: one tile per automation in stored order with what it asks for
 * this month, and the kinds the phone cannot run yet named so nothing looks lost.
 */
@Composable
fun AutomationTiles(
    automations: CategoryAutomations,
    amounts: List<Long?>,
    currency: CurrencySpec,
    modifier: Modifier = Modifier,
) {
    val options = formatOptions()
    val asks = amounts.filterNotNull().sum()
    Column(modifier.fillMaxWidth().testTag("automation-tiles"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
            MetroText(AutomationCopy.SECTION, Metro.typography.subheader, Modifier.weight(1f))
            if (automations.runnable && automations.entries.isNotEmpty()) {
                MetroText("${TemplateEditorCopy.ASKS} ", Metro.typography.caption, color = Metro.colors.secondary)
                MetroText(
                    MoneyFormat.format(asks, currency, options),
                    Metro.typography.body,
                    Modifier.testTag("automation-asks"),
                    color = Metro.accent.text,
                )
            }
        }
        MetroText(AutomationCopy.OWNER, Metro.typography.caption, Modifier.testTag("automation-owner"), color = Metro.colors.secondary)
        automations.entries.forEachIndexed { index, entry ->
            AutomationTile(entry, amounts.getOrNull(index), currency, options, Modifier.testTag("automation-tile-$index"))
        }
        if (automations.entries.isEmpty()) {
            MetroText(AutomationCopy.EMPTY, Metro.typography.caption, color = Metro.colors.secondary)
        }
        if (!automations.runnable) {
            MetroText(AutomationCopy.NOT_RUN, Metro.typography.caption, Modifier.testTag("automation-not-run"), color = Metro.accent.text)
        }
    }
}

@Composable
private fun AutomationTile(
    entry: TemplateJson.Stored,
    amount: Long?,
    currency: CurrencySpec,
    options: FormatOptions,
    modifier: Modifier,
) {
    val template = entry.template
    Row(
        modifier
            .fillMaxWidth()
            .styleCard(selected = template == null)
            .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TemplateGlyph(template?.let { TemplateKind.of(it) }, Metro.accent.text, Modifier.padding(end = 10.dp))
        Column(Modifier.weight(1f)) {
            MetroText(
                if (template == null) AutomationCopy.typeLabel(entry.type) else TemplateWords.title(template, currency, options),
                Metro.typography.body,
                maxLines = 1,
                softWrap = false,
            )
            MetroText(
                if (template == null) AutomationCopy.UNSUPPORTED else TemplateWords.caption(template, currency, options),
                Metro.typography.caption,
                color = if (template == null) Metro.accent.text else Metro.colors.secondary,
                maxLines = 2,
            )
        }
        if (template != null) {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                MetroText(amount?.let { MoneyFormat.format(it, currency, options) } ?: "—", Metro.typography.body)
                PriorityBadge(template, Modifier.padding(top = 4.dp))
            }
        }
    }
}
