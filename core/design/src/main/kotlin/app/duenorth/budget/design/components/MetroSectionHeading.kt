package app.duenorth.budget.design.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import app.duenorth.budget.design.theme.MetroWeights

/** How large a [MetroSectionHeading] is drawn: over a form's fields, or over a page's groups of rows. */
enum class SectionSize { FORM, PAGE }

/**
 * The one gray, lowercase label over a group of fields or rows. [SectionSize.FORM] is a caption, for forms;
 * [SectionSize.PAGE] is a group header, for pages such as settings. [first] drops the space above, for a heading
 * at the top of its page.
 */
@Composable
fun MetroSectionHeading(
    text: String,
    modifier: Modifier = Modifier,
    size: SectionSize = SectionSize.FORM,
    first: Boolean = false,
) {
    val top =
        when {
            first -> 0.dp
            size == SectionSize.FORM -> MetroDimens.Row
            else -> MetroDimens.Gutter
        }
    if (Metro.style.tonal) {
        // Tonal: a small-caps title, as the widget heads its card.
        MetroText(
            text.uppercase(),
            Metro.typography.caption.copy(fontWeight = MetroWeights.Semibold, letterSpacing = 0.08.em),
            modifier.padding(top = top + if (size == SectionSize.PAGE) 6.dp else 0.dp, bottom = 6.dp),
            color = Metro.colors.secondary,
        )
        return
    }
    MetroText(
        text,
        if (size == SectionSize.FORM) Metro.typography.caption else Metro.typography.groupHeader,
        modifier.padding(top = top, bottom = if (size == SectionSize.FORM) 4.dp else 0.dp),
        color = Metro.colors.secondary,
    )
}
