package app.duenorth.budget

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.CurrencySpec
import app.duenorth.budget.core.MoneyFormat
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.theme.Metro

/**
 * What a category has available this month, shown beside its name in every category picker so the choice is made
 * knowing what is left in it. Grey ink, red when the category is overspent; nothing for income or an unknown balance.
 */
@Composable
internal fun CategoryBalance(
    availableMinor: Long?,
    currency: CurrencySpec?,
    /** Beside the name by default; a picker that sets the balance under the name passes [Modifier]. */
    modifier: Modifier = Modifier.padding(start = 12.dp),
    style: TextStyle = Metro.typography.body,
) {
    if (availableMinor == null || currency == null) return
    MetroText(
        MoneyFormat.format(availableMinor, currency, formatOptions()),
        style,
        modifier,
        color = if (availableMinor < 0) Metro.colors.error else Metro.colors.secondary,
        maxLines = 1,
        softWrap = false,
    )
}
