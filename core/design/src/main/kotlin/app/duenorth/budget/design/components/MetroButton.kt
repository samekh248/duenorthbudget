package app.duenorth.budget.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import app.duenorth.budget.design.theme.MetroWeights
import app.duenorth.budget.design.theme.Relief

/**
 * A body button, laid out the Windows Phone way: every button is one cell of a two-column
 * grid, so a lone button takes the left half of the row and pairs sit side by side at equal
 * width. Group neighbouring buttons in [MetroButtonGrid]; commands for the whole page belong
 * on the app bar instead.
 */
@Composable
fun MetroButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val cell = if (LocalButtonCell.current) Modifier.fillMaxWidth() else Modifier.buttonCell()
    MetroActionButton(label, modifier.then(cell), onClick)
}

/** Same button as [MetroButton]; kept for the screens that named the outlined style. */
@Composable
fun MetroOutlineButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    MetroButton(label, modifier, onClick)
}

/**
 * Buttons that belong together, two to a row at equal width, rows a gutter apart. An odd
 * last button keeps the left cell, so a page's buttons always line up on the same grid.
 */
@Composable
fun MetroButtonGrid(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val gutter = with(LocalDensity.current) { MetroDimens.Gutter.roundToPx() }
    Layout(
        content = { CompositionLocalProvider(LocalButtonCell provides true, content = content) },
        modifier = modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val cell = ((constraints.maxWidth - gutter) / 2).coerceAtLeast(0)
        val placeables = measurables.map { it.measure(Constraints.fixedWidth(cell)) }
        val rows = placeables.chunked(2)
        val heights = rows.map { row -> row.maxOf { it.height } }
        val height = heights.sum() + gutter * (rows.size - 1).coerceAtLeast(0)
        layout(constraints.maxWidth, height) {
            var y = 0
            rows.forEachIndexed { index, row ->
                row.forEachIndexed { column, placeable -> placeable.place(column * (cell + gutter), y) }
                y += heights[index] + gutter
            }
        }
    }
}

private val LocalButtonCell = staticCompositionLocalOf { false }

/** Half the row less half a gutter: the width of one [MetroButtonGrid] cell. A long label may grow it. */
private fun Modifier.buttonCell(): Modifier =
    layout { measurable, constraints ->
        if (!constraints.hasBoundedWidth) {
            val placeable = measurable.measure(constraints)
            return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
        val cell = ((constraints.maxWidth - MetroDimens.Gutter.roundToPx()) / 2).coerceIn(constraints.minWidth, constraints.maxWidth)
        val placeable = measurable.measure(constraints.copy(minWidth = cell))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

@Composable
private fun MetroActionButton(
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = Metro.colors
    val accent = Metro.accent
    val animations = Metro.animations
    val relief = Metro.style.relief
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    if (Metro.style.mobile10) {
        Mobile10Button(label, modifier, interaction, pressed, onClick)
        return
    }
    Box(
        modifier
            .heightIn(min = MetroDimens.ButtonMin)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ).graphicsLayer { rotationZ = if (pressed && animations) 2.5f else 0f }
            .then(
                if (relief) {
                    // Relief: the button stands off the page and presses in under the finger.
                    Modifier.relief(if (pressed) Relief.Inset else Relief.Raised, small = true)
                } else {
                    Modifier
                        .border(MetroDimens.Stroke, colors.foreground)
                        .background(if (pressed) accent.fill else Color.Transparent)
                },
            ).padding(horizontal = 9.5.dp, vertical = 3.5.dp),
        contentAlignment = Alignment.Center,
    ) {
        MetroText(
            label,
            Metro.typography.body.copy(fontWeight = MetroWeights.Semibold),
            color =
                when {
                    relief && pressed -> accent.text
                    pressed -> Color.White
                    else -> colors.foreground
                },
        )
    }
}

/** The Windows 10 Mobile button: a flat grey block with no outline, darker under the finger, its label in regular. */
@Composable
private fun Mobile10Button(
    label: String,
    modifier: Modifier,
    interaction: MutableInteractionSource,
    pressed: Boolean,
    onClick: () -> Unit,
) {
    val colors = Metro.colors
    Box(
        modifier
            .heightIn(min = Mobile10Dimens.Button)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .background(colors.foreground.copy(alpha = if (pressed) 0.4f else 0.2f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        MetroText(label, Metro.typography.body, color = colors.foreground)
    }
}

/** Sizes of the Windows 10 Mobile style that differ from Metro's. */
object Mobile10Dimens {
    /** A button: taller than the 32epx desktop button, so it stays an easy touch target. */
    val Button = 40.dp

    /** The closed command bar. */
    val CommandBar = 48.dp

    /** One command bar button. */
    val CommandSlot = 68.dp
}
