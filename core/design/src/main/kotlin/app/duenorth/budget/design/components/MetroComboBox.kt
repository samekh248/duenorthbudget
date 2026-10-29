package app.duenorth.budget.design.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
                    if (Metro.style.mobile10) {
                        // Windows 10 Mobile: the combo box's 2dp grey edge, the accent while open.
                        Modifier.border(2.dp, if (open) Metro.accent.fill else Metro.colors.outline)
                    } else {
                        Modifier.border(MetroDimens.Stroke, if (open) Metro.accent.fill else Metro.colors.foreground)
                    }
                },
            ).then(
                if (Metro.style.layers) {
                    // Mica: presses stay inside the rounded field, and the open box is underlined in the accent.
                    Modifier.layerFocusLine(open).clip(RoundedCornerShape(LayerCorner.Control))
                } else {
                    Modifier
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
            val layers = Metro.style.layers
            val accent = Metro.accent.fill
            val wash = Metro.colors.foreground.copy(alpha = if (Metro.colors.isDark) 0.08f else 0.05f)
            choices.forEach { choice ->
                val chosen = choice == selected
                MetroText(
                    label(choice),
                    Metro.typography.control,
                    Modifier
                        .fillMaxWidth()
                        .then(if (layers && chosen) Modifier.layerChosen(wash, accent) else Modifier)
                        .heightIn(min = MetroDimens.TouchTarget)
                        .metroPress {
                            open = false
                            if (choice != selected) onPick(choice)
                        }.padding(horizontal = comboInset(), vertical = MetroDimens.Row)
                        .testTag("$tag-$choice"),
                    // Mica marks the chosen item with a wash and an accent pill, as a Windows 11 list does, not with colored text.
                    color = if (chosen && !layers) Metro.accent.text else Metro.colors.foreground,
                )
            }
        }
    }
}

/** A Windows 11 list's chosen item: a faint rounded [wash] inset from the box, with a short [accent] pill at its start. */
private fun Modifier.layerChosen(
    wash: Color,
    accent: Color,
): Modifier =
    drawBehind {
        val inset = 4.dp.toPx()
        drawRoundRect(wash, Offset(inset, inset / 2), Size(size.width - 2 * inset, size.height - inset), CornerRadius(4.dp.toPx()))
        val pill = Size(3.dp.toPx(), 16.dp.toPx())
        drawRoundRect(accent, Offset(inset, (size.height - pill.height) / 2), pill, CornerRadius(pill.width / 2))
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
            .padding(horizontal = comboInset())
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

/** How far a combo box's text sits from its edge: under mica 14dp, so it lines up with the text boxes. */
@Composable
private fun comboInset() = if (Metro.style.layers) 14.dp else 10.dp
