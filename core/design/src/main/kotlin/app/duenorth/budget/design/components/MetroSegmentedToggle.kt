package app.duenorth.budget.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    Row(modifier.height(IntrinsicSize.Min).testTag(tag)) {
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
    Box(
        Modifier
            .fillMaxHeight()
            .heightIn(min = MetroDimens.TouchTarget)
            .background(if (selected) accent.fill else Color.Transparent)
            .border(MetroDimens.Stroke, if (selected) accent.fill else ink)
            .metroPress(onClick)
            .semantics {
                this.selected = selected
                role = Role.Tab
            }.padding(horizontal = 12.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        MetroText(label, Metro.typography.control, color = if (selected) accent.onFill else ink, maxLines = 1, softWrap = false)
    }
}
