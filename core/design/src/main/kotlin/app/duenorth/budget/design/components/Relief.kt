package app.duenorth.budget.design.components

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import app.duenorth.budget.design.theme.MetroSurface
import app.duenorth.budget.design.theme.Relief
import androidx.compose.ui.graphics.Path as ComposePath

/** A corner large enough to make any shape a circle or a pill. */
val PillCorner: Dp = 999.dp

/** Mica's corners: a layer card (a group, a day, the keypad's sheet) and a control on one (a field, a key). */
object LayerCorner {
    val Card = 8.dp
    val Control = 6.dp
    val Floating = 12.dp
}

/**
 * Draws a relief surface behind the content: a panel ([small] false) or a control ([small] true), in the style's
 * light and shade, filled with [fill] or [brush]. Corners are square, as in Metro, unless [corner] is given (the
 * app bar's round buttons). Under the Metro style it draws nothing, so callers add it unconditionally.
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
    val radius =
        when {
            // Mica rounds everything a little, and turns the app bar's round buttons into rounded squares too.
            surface.style.layers && (corner == null || corner == PillCorner) ->
                if (small) LayerCorner.Control else LayerCorner.Card
            else -> corner ?: 0.dp
        }
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
    if (surface.style.layers) {
        drawLayer(surface, relief, topLeft, size, radius, fill, brush)
        return
    }
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

/**
 * Mica's version of [drawRelief]: the [MetroSurface.layer] card (or [fill], when it is not the page, such as an
 * accent) edged by a hairline. Inset is a field: the same card with a line under it, as Windows 11 draws a text box.
 */
private fun DrawScope.drawLayer(
    surface: MetroSurface,
    relief: Relief,
    topLeft: Offset,
    size: Size,
    radius: Float,
    fill: Color,
    brush: Brush?,
) {
    val r = radius.coerceAtMost(minOf(size.width, size.height) / 2f)
    val corner = CornerRadius(r)
    val onPage = brush == null && fill == surface.page
    when {
        brush != null -> drawRoundRect(brush, topLeft, size, corner)
        onPage -> drawRoundRect(surface.layer, topLeft, size, corner)
        else -> drawRoundRect(fill, topLeft, size, corner)
    }
    if (!onPage) return
    val hair = 1.dp.toPx()
    drawRoundRect(
        surface.stroke,
        Offset(topLeft.x + hair / 2, topLeft.y + hair / 2),
        Size(size.width - hair, size.height - hair),
        CornerRadius((r - hair / 2).coerceAtLeast(0f)),
        style = Stroke(hair),
    )
    if (relief == Relief.Inset) {
        val shape =
            ComposePath().apply {
                addRoundRect(RoundRect(topLeft.x, topLeft.y, topLeft.x + size.width, topLeft.y + size.height, corner))
            }
        clipPath(shape) {
            drawRect(surface.underline, Offset(topLeft.x, topLeft.y + size.height - hair), Size(size.width, hair))
        }
    }
}

/**
 * Mica's mark of a field in use (a text box being typed in, an open combo box): a 2dp accent line along the
 * bottom, over the field's resting underline, as Windows 11 draws it.
 */
@Composable
fun Modifier.layerFocusLine(on: Boolean): Modifier {
    val accent = Metro.accent.fill
    return drawWithContent {
        drawContent()
        if (on) {
            val line = 2.dp.toPx()
            val inset = LayerCorner.Control.toPx() / 2
            drawRect(accent, Offset(inset, size.height - line), Size(size.width - 2 * inset, line))
        }
    }
}

/**
 * Mica's check box, as Windows 11 draws one: a square rounded by 4dp, a faint fill edged by a strong 1dp line
 * when clear, the accent with a tick when [checked]. No underline: that marks a field, not a check box.
 */
@Composable
fun LayerCheckBox(
    checked: Boolean,
    modifier: Modifier = Modifier,
) {
    val dark = Metro.colors.isDark
    val accent = Metro.accent.fill
    val tick = Metro.accent.onFill
    val wash = if (dark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.02f)
    val edge = if (dark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)
    Canvas(modifier) {
        val corner = CornerRadius(4.dp.toPx())
        if (checked) {
            drawRoundRect(accent, cornerRadius = corner)
            val w = size.width
            val h = size.height
            val path =
                ComposePath().apply {
                    moveTo(w * 0.26f, h * 0.52f)
                    lineTo(w * 0.43f, h * 0.68f)
                    lineTo(w * 0.75f, h * 0.34f)
                }
            drawPath(path, tick, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        } else {
            val hair = 1.dp.toPx()
            drawRoundRect(wash, cornerRadius = corner)
            drawRoundRect(
                edge,
                Offset(hair / 2, hair / 2),
                Size(size.width - hair, size.height - hair),
                CornerRadius(4.dp.toPx() - hair / 2),
                style = Stroke(hair),
            )
        }
    }
}

/**
 * One slice of a mica layer card split across list items (the budget editor's groups, which scroll one row at a
 * time): the card's [top] corners on the first slice, its [bottom] ones on the last, plain sides between. Under
 * any other style it draws nothing.
 */
@Composable
fun Modifier.layerSlice(
    top: Boolean,
    bottom: Boolean,
): Modifier {
    val surface = Metro.surface
    if (!surface.style.layers) return this
    return drawBehind {
        val r = LayerCorner.Card.toPx()
        val above = if (top) 0f else 2 * r
        val below = if (bottom) 0f else 2 * r
        clipRect {
            drawRoundRect(surface.layer, Offset(0f, -above), Size(size.width, size.height + above + below), CornerRadius(r))
            val hair = 1.dp.toPx()
            drawRoundRect(
                surface.stroke,
                Offset(hair / 2, hair / 2 - above),
                Size(size.width - hair, size.height + above + below - hair),
                CornerRadius(r - hair / 2),
                style = Stroke(hair),
            )
        }
    }
}

/**
 * A floating acrylic sheet (mica's app bar and keypad): the [MetroSurface.acrylic] tint with a soft drop shadow,
 * rounded by [LayerCorner.Floating].
 */
fun Modifier.acrylicSheet(surface: MetroSurface): Modifier =
    drawBehind {
        val r = LayerCorner.Floating.toPx()
        val blur = surface.blur.toPx()
        drawIntoCanvas { canvas ->
            val rect = RectF(0f, surface.depth.toPx(), size.width, size.height + surface.depth.toPx())
            canvas.nativeCanvas.drawRoundRect(rect, r, r, blurred(surface.shade, blur))
        }
        drawRoundRect(surface.acrylic, cornerRadius = CornerRadius(r))
        val hair = 1.dp.toPx()
        drawRoundRect(
            surface.stroke,
            Offset(hair / 2, hair / 2),
            Size(size.width - hair, size.height - hair),
            CornerRadius(r - hair / 2),
            style = Stroke(hair),
        )
    }

private fun blurred(
    color: Color,
    blur: Float,
): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
    }

/**
 * The edge of a Metro box (a date box, a check box, a picker cell): the 2.5 dp outline in [color], or under a
 * relief style the control's own relief instead, filled with [fill]. Windows 10 Mobile draws its 2 dp control edge,
 * grey where Metro's is ink.
 */
@Composable
fun Modifier.controlFrame(
    color: Color = Metro.colors.foreground,
    fill: Color = Metro.colors.background,
): Modifier =
    when {
        Metro.style.relief -> relief(Metro.style.control, small = true, fill = fill)
        Metro.style.mobile10 -> border(2.dp, if (color == Metro.colors.foreground) Metro.colors.outline else color)
        else -> border(MetroDimens.Stroke, color)
    }
