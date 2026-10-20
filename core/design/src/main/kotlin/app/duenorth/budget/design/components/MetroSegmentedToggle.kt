package app.duenorth.budget.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import app.duenorth.budget.design.theme.MetroStyle
import app.duenorth.budget.design.theme.Relief

/**
 * Two Metro toggle buttons joined edge to edge, for a choice between two named options on one
 * line (spec 013's expense and income). The chosen segment is filled with the accent; the other
 * is outlined. Windows Phone 8.1 has no segmented control; the owner chose this over radio
 * buttons and a toggle switch.
 */
@Composable
fun MetroSegmentedToggle(
    first: String,
    second: String,
    firstSelected: Boolean,
    onSelect: (first: Boolean) -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
) {
    // Carved: the two options share a track cut into the page; mica, a track on a layer card. Other relief styles
    // stand them up side by side.
    val style = Metro.style
    val track =
        when {
            style.carvedControls -> Modifier.relief(Relief.Inset, small = true).padding(4.dp)
            style.layers -> Modifier.relief(Relief.Raised).padding(3.dp)
            else -> Modifier
        }
    Row(
        modifier.height(IntrinsicSize.Min).then(track).testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(if (style.relief && !style.trackedToggle) 10.dp else 0.dp),
    ) {
        Segment(first, firstSelected, "$tag-$first") { onSelect(true) }
        Segment(second, !firstSelected, "$tag-$second") { onSelect(false) }
    }
}

@Composable
private fun Segment(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    val accent = Metro.accent
    val ink = Metro.colors.foreground
    val style = Metro.style
    val relief = style.relief
    Box(
        Modifier
            .fillMaxHeight()
            .heightIn(min = if (style.trackedToggle) MetroDimens.TouchTarget - 8.dp else MetroDimens.TouchTarget)
            .then(
                when {
                    // The chosen option is the accent: pressed in under pressed metro, standing out otherwise.
                    relief && selected ->
                        Modifier.relief(
                            if (style == MetroStyle.PressedMetro) Relief.Inset else Relief.Raised,
                            small = true,
                            fill = accent.fill,
                        )
                    style.trackedToggle -> Modifier
                    relief -> Modifier.relief(Relief.Raised, small = true)
                    else ->
                        Modifier
                            .background(if (selected) accent.fill else Color.Transparent)
                            .border(MetroDimens.Stroke, if (selected) accent.fill else ink)
                },
            ).metroPress(onClick)
            .semantics {
                this.selected = selected
                role = Role.Tab
            }.padding(horizontal = 12.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        MetroText(
            label,
            Metro.typography.control,
            color =
                when {
                    !selected -> ink
                    else -> accent.onFill
                },
            maxLines = 1,
            softWrap = false,
        )
    }
}
