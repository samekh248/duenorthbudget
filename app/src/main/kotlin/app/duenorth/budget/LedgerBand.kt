package app.duenorth.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.duenorth.budget.design.components.layerClip
import app.duenorth.budget.design.components.relief
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import app.duenorth.budget.design.theme.Relief

/**
 * The faint band under every other row of a ledger list (the register, accounts and the budget), which
 * separates rows without drawing lines between them. None under Windows 10 Mobile, except on the accounts list.
 */
internal val ledgerBand: Color
    @Composable
    @ReadOnlyComposable
    get() =
        // Windows 10 Mobile lists run unbanded, their rows set apart by air alone.
        if (Metro.style.mobile10) Color.Transparent else ledgerBandShade

/** The [ledgerBand] shade under every style, Windows 10 Mobile included, for lists banded under all of them. */
internal val ledgerBandShade: Color
    @Composable
    @ReadOnlyComposable
    get() = if (Metro.colors.isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.035f)

/** Paints [fill] behind the row, running past the screen gutter to both edges of the screen. */
internal fun Modifier.ledgerFill(fill: Color): Modifier =
    drawBehind {
        if (fill.alpha == 0f) return@drawBehind
        val gutter = MetroDimens.Gutter.toPx()
        drawRect(fill, Offset(-gutter, 0f), Size(size.width + 2 * gutter, size.height))
    }

/**
 * Flags a row that needs attention, such as an overspent category: [tint] on a softly rounded panel that reaches
 * [LEDGER_FLAG_MARGIN] past the row's text on each side. A goal bar inside keeps the same margin below it, so the
 * bar lines up with the text and with every other row's bar, framed evenly by the tint.
 */
internal fun Modifier.ledgerFlag(tint: Color): Modifier =
    drawBehind {
        val margin = LEDGER_FLAG_MARGIN.toPx()
        val inset = LEDGER_FLAG_INSET.toPx()
        drawRoundRect(
            tint,
            Offset(-margin, inset),
            Size(size.width + 2 * margin, size.height - 2 * inset),
            CornerRadius(LEDGER_FLAG_CORNER.toPx()),
        )
    }

/** How far a flagged row's panel reaches past its text, and the air left under its goal bar. */
internal val LEDGER_FLAG_MARGIN = 6.dp

/** The gap above and below a flagged row's panel, so it reads as a panel against a banded neighbour. */
internal val LEDGER_FLAG_INSET = 2.dp

private val LEDGER_FLAG_CORNER = 4.dp

/** Whether the row at [index] in a ledger list sits on a band: every second row, counting from the first. */
internal fun ledgerBanded(index: Int): Boolean = index % 2 == 1

/**
 * Under a block style, widens a list by the screen gutter on each side, so the blocks' light and shade fall into the
 * gutter instead of being cut at the list's edge. Pair it with [cardListPadding] to keep the blocks in place.
 */
internal fun Modifier.bleedGutter(on: Boolean): Modifier =
    if (!on) {
        this
    } else {
        layout { measurable, constraints ->
            val gutter = MetroDimens.Gutter.roundToPx()
            val placeable =
                measurable.measure(
                    constraints.copy(minWidth = constraints.minWidth + 2 * gutter, maxWidth = constraints.maxWidth + 2 * gutter),
                )
            layout(constraints.maxWidth, placeable.height) { placeable.place(-gutter, 0) }
        }
    }

/**
 * The content padding that brings a [bleedGutter] list's rows back to the gutter. A block list also takes the
 * air above it as [top] padding instead of a margin, so the first block's light is not cut at the list's edge.
 */
internal fun cardListPadding(
    on: Boolean,
    top: Dp = 0.dp,
): PaddingValues = if (on) PaddingValues(start = MetroDimens.Gutter, end = MetroDimens.Gutter, top = top) else PaddingValues()

/** Whether the style draws each group or day as one panel ([LedgerGroup]) rather than as loose rows. */
internal val ledgerBlocks: Boolean
    @Composable
    @ReadOnlyComposable
    get() = Metro.style.panels

/**
 * A group of ledger rows as one panel holding [heading] and the rows: raised and square under tile relief, a layer
 * card under mica. The rows sit a gutter in.
 */
@Composable
internal fun LedgerGroup(
    modifier: Modifier = Modifier,
    heading: @Composable () -> Unit,
    /** False when the last row takes the panel's bottom air itself, so a tint on it runs to the panel's edge. */
    bottomAir: Boolean = true,
    rows: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .relief(Relief.Raised)
            // Mica's card is rounded: a row's tint follows its corners rather than poking out square.
            .layerClip()
            .padding(horizontal = MetroDimens.Gutter)
            .padding(bottom = if (bottomAir) MetroDimens.Row else 0.dp),
    ) {
        heading()
        rows()
    }
}
