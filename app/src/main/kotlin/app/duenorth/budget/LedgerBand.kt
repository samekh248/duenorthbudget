package app.duenorth.budget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
