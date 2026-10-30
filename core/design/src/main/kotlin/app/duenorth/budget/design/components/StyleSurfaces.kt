package app.duenorth.budget.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import app.duenorth.budget.design.theme.Relief
import app.duenorth.budget.design.theme.mobile10Card

/**
 * The shape of a surface under the current style: rounded by [corner] under mica, square under every other style,
 * as Metro draws everything. Screens take their corners from here rather than typing them, so a style's corners
 * change in one place.
 */
@Composable
@ReadOnlyComposable
fun layerShape(corner: Dp = LayerCorner.Card): Shape = if (Metro.style.layers) RoundedCornerShape(corner) else RectangleShape

/** Clips to [layerShape] under mica, so a tint or a fill follows the rounded corners; nothing under other styles. */
@Composable
@ReadOnlyComposable
fun Modifier.layerClip(corner: Dp = LayerCorner.Card): Modifier = if (Metro.style.layers) clip(RoundedCornerShape(corner)) else this

/**
 * A card that groups or holds something (a template tile, a template chip, a note to fix) in the current style:
 * raised under tile relief, a layer card under mica, a faint wash under Windows 10 Mobile, a hairline box under
 * Metro. [small] is a control-sized card, with half the relief and mica's smaller corner. [selected] edges it in the
 * accent: Metro's hairline turns to the accent, Windows 10 Mobile adds its 2dp edge, and the relief styles add
 * Metro's stroke around the card's own shape.
 */
@Composable
fun Modifier.styleCard(
    selected: Boolean = false,
    small: Boolean = false,
): Modifier {
    val style = Metro.style
    val accent = Metro.accent.fill
    return when {
        style.relief -> {
            val raised = relief(Relief.Raised, small = small)
            if (selected) {
                raised.border(MetroDimens.Stroke, accent, layerShape(if (small) LayerCorner.Control else LayerCorner.Card))
            } else {
                raised
            }
        }
        // Windows 10 Mobile: a faint card, edged in the accent only once chosen.
        style.mobile10 -> background(Metro.colors.mobile10Card).then(if (selected) Modifier.border(2.dp, accent) else Modifier)
        else -> border(MetroDimens.Stroke, if (selected) accent else Metro.colors.secondary.copy(alpha = 0.5f))
    }
}
