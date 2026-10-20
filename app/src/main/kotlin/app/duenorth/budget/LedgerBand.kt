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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.duenorth.budget.design.components.Relief
import app.duenorth.budget.design.components.relief
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens

/**
 * The faint band under every other row of a ledger list (the register, accounts and the budget), which
 * separates rows without drawing lines between them.
 */
internal val ledgerBand: Color
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

/** Whether the row at [index] in a ledger list sits on a band: every second row, counting from the first. */
internal fun ledgerBanded(index: Int): Boolean = index % 2 == 1

/**
 * Under a card style, widens a list by the screen gutter on each side, so the cards' shadows fall into the
 * gutter instead of being cut at the list's edge. Pair it with [cardListPadding] to keep the cards in place.
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
 * The content padding that brings a [bleedGutter] list's rows back to the gutter. A card list also takes the
 * air above it as [top] padding instead of a margin, so the first card's light is not cut at the list's edge.
 */
internal fun cardListPadding(
    on: Boolean,
    top: Dp = 0.dp,
): PaddingValues = if (on) PaddingValues(start = MetroDimens.Gutter, end = MetroDimens.Gutter, top = top) else PaddingValues()

/** A raised card holding a group of ledger rows, under a card style. Its rows sit a gutter in from its edge. */
@Composable
internal fun LedgerCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .relief(Relief.Raised)
            .padding(horizontal = MetroDimens.Gutter)
            .padding(bottom = MetroDimens.Row),
        content = content,
    )
}
