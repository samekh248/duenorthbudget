package app.duenorth.budget.design.components

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroSurface
import androidx.compose.ui.graphics.Path as ComposePath

/** Raised stands off the page; inset is pressed into it. */
enum class Relief { Raised, Inset }

/** A corner large enough to make any shape a circle or a pill. */
val PillCorner: Dp = 999.dp

/**
 * Draws a soft-relief surface behind the content: a card ([small] false) or a control ([small] true), in the
 * style's light and shade, filled with [fill] or [brush]. Under the Metro style it draws nothing, so callers
 * add it unconditionally.
 */
@Composable
fun Modifier.relief(
    relief: Relief,
    small: Boolean = false,
    corner: Dp? = null,
    fill: Color = Metro.colors.background,
    brush: Brush? = null,
): Modifier {
    val surface = Metro.surface
    if (!surface.style.relief) return this
    val radius = corner ?: if (small) surface.controlCorner else surface.corner
    return drawBehind { drawRelief(surface, relief, Offset.Zero, size, radius.toPx(), fill, brush, small) }
}

/** The drawing behind [relief], for a shape inside a larger draw (the register's cleared mark). */
fun DrawScope.drawRelief(
    surface: MetroSurface,
    relief: Relief,
    topLeft: Offset,
    size: Size,
    radius: Float,
    fill: Color,
    brush: Brush? = null,
    small: Boolean = false,
) {
    if (size.width <= 0f || size.height <= 0f) return
    val scale = if (small) 0.5f else 1f
    val depth = surface.depth.toPx() * scale
    val blur = (surface.blur.toPx() * scale).coerceAtLeast(1f)
    val r = radius.coerceAtMost(minOf(size.width, size.height) / 2f)
    val corner = CornerRadius(r)

    fun fillShape() {
        if (brush != null) {
            drawRoundRect(brush, topLeft, size, corner)
        } else {
            drawRoundRect(fill, topLeft, size, corner)
        }
    }
    when (relief) {
        Relief.Raised -> {
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                for ((color, shift) in listOf(surface.shade to depth, surface.highlight to -depth)) {
                    val rect =
                        RectF(
                            topLeft.x + shift,
                            topLeft.y + shift,
                            topLeft.x + size.width + shift,
                            topLeft.y + size.height + shift,
                        )
                    native.drawRoundRect(rect, r, r, blurred(color, blur))
                }
            }
            fillShape()
        }
        Relief.Inset -> {
            fillShape()
            val shape =
                ComposePath().apply {
                    addRoundRect(
                        RoundRect(topLeft.x, topLeft.y, topLeft.x + size.width, topLeft.y + size.height, corner),
                    )
                }
            clipPath(shape) {
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    val pad = blur * 3 + depth
                    for ((color, shift) in listOf(surface.shade to depth, surface.highlight to -depth)) {
                        // A frame around the shape, moved by the light, so only its soft inner edge shows.
                        val frame =
                            android.graphics.Path().apply {
                                fillType = android.graphics.Path.FillType.EVEN_ODD
                                addRect(
                                    topLeft.x - pad,
                                    topLeft.y - pad,
                                    topLeft.x + size.width + pad,
                                    topLeft.y + size.height + pad,
                                    android.graphics.Path.Direction.CW,
                                )
                                addRoundRect(
                                    RectF(
                                        topLeft.x + shift,
                                        topLeft.y + shift,
                                        topLeft.x + size.width + shift,
                                        topLeft.y + size.height + shift,
                                    ),
                                    r,
                                    r,
                                    android.graphics.Path.Direction.CW,
                                )
                            }
                        native.drawPath(frame, blurred(color, blur * 0.6f))
                    }
                }
            }
        }
    }
}

private fun blurred(
    color: Color,
    blur: Float,
): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
    }
