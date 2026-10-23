package app.duenorth.budget.design.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.dp
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import app.duenorth.budget.design.theme.Relief

/**
 * The Windows Phone 8.1 combo box: the current value in a bordered box with a chevron. With
 * [fullScreenThreshold] or fewer choices, tapping it unfolds every choice in place, the chosen
 * one in the accent; tapping a choice picks it and folds the box back up. With more, tapping it
 * calls [onOpenFull] so the page can show the full-screen list, as WP8.1 does past five items.
 */
@Composable
fun <T> MetroComboBox(
    choices: List<T>,
    selected: T,
    label: (T) -> String,
    onPick: (T) -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    fullScreenThreshold: Int = Int.MAX_VALUE,
    onOpenFull: () -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .then(
                if (Metro.style.relief) {
                    // Open, the box is pressed in; otherwise it stands up like a button (under mica, a field).
                    Modifier.relief(if (open) Relief.Inset else Metro.style.control, small = true)
                } else {
                    Modifier.border(MetroDimens.Stroke, if (open) Metro.accent.fill else Metro.colors.foreground)
                },
            ).testTag(tag),
    ) {
        if (!open) {
            MetroComboValue(
                text = if (selected in choices) label(selected) else "",
                tag = "$tag-value",
                onClick = { if (choices.size > fullScreenThreshold) onOpenFull() else open = true },
            )
        } else {
            choices.forEach { choice ->
                MetroText(
                    label(choice),
                    Metro.typography.control,
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = MetroDimens.TouchTarget)
                        .metroPress {
                            open = false
                            if (choice != selected) onPick(choice)
                        }.padding(horizontal = 10.dp, vertical = MetroDimens.Row)
                        .testTag("$tag-$choice"),
                    color = if (choice == selected) Metro.accent.text else Metro.colors.foreground,
                )
            }
        }
    }
}

/**
 * The closed face of a combo box: the value and a chevron in one touch-target row. Also the
 * date box, which opens a picker page rather than a list.
 */
@Composable
fun MetroComboValue(
    text: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .heightIn(min = MetroDimens.TouchTarget)
            .metroPress(onClick)
            .padding(horizontal = 10.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MetroText(
            text,
            Metro.typography.control,
            Modifier.weight(1f),
            maxLines = 1,
            softWrap = false,
        )
        AppGlyphIcon(AppGlyph.ChevronDown)
    }
}
