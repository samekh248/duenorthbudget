package app.duenorth.budget.design.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens

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
    MetroText(
        text,
        if (size == SectionSize.FORM) Metro.typography.caption else Metro.typography.groupHeader,
        modifier.padding(top = top, bottom = if (size == SectionSize.FORM) 4.dp else 0.dp),
        color = Metro.colors.secondary,
    )
}
