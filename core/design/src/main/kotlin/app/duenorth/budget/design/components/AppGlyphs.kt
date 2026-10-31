package app.duenorth.budget.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.duenorth.budget.design.theme.Metro

enum class AppGlyph {
    Budgets,
    Appearance,
    Sync,
    Payees,
    Hold,
    Categories,
    Add,
    Settings,
    Transfer,
    Reconcile,
    Import,
    Save,
    Cancel,
    ChevronLeft,
    ChevronRight,
    ChevronDown,
    Calendar,
    Breakdown,
    Copy,
    Average,
    Zero,
    Undo,
    Select,
    Search,
    Delete,
    Edit,
    Merge,
}

object AppGlyphInk {
    fun strokeCount(glyph: AppGlyph): Int =
        when (glyph) {
            AppGlyph.Budgets -> 3
            AppGlyph.Appearance -> 3
            AppGlyph.Sync -> 4
            AppGlyph.Payees -> 2
            AppGlyph.Hold -> 2
            AppGlyph.Categories -> 3
            AppGlyph.Add -> 2
            AppGlyph.Settings -> 2
            AppGlyph.Transfer -> 4
            AppGlyph.Reconcile -> 2
            AppGlyph.Import -> 3
            AppGlyph.Save -> 2
            AppGlyph.Cancel -> 2
            AppGlyph.ChevronLeft -> 2
            AppGlyph.ChevronRight -> 2
            AppGlyph.ChevronDown -> 2
            AppGlyph.Calendar -> 3
            AppGlyph.Breakdown -> 4
            AppGlyph.Copy -> 2
            AppGlyph.Average -> 3
            AppGlyph.Zero -> 1
            AppGlyph.Undo -> 3
            AppGlyph.Select -> 4
            AppGlyph.Search -> 2
            AppGlyph.Delete -> 4
            AppGlyph.Edit -> 2
            AppGlyph.Merge -> 4
        }

    fun DrawScope.draw(
        glyph: AppGlyph,
        color: Color,
    ) {
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Square)
        val w = size.width
        val h = size.height
        when (glyph) {
            AppGlyph.Budgets -> {
                drawRect(
                    color,
                    topLeft = Offset(3.dp.toPx(), 6.dp.toPx()),
                    size =
                        androidx.compose.ui.geometry
                            .Size(16.dp.toPx(), 13.dp.toPx()),
                    style = stroke,
                )
                drawLine(color, Offset(3.dp.toPx(), 6.dp.toPx()), Offset(8.dp.toPx(), 3.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(8.dp.toPx(), 3.dp.toPx()), Offset(14.dp.toPx(), 6.dp.toPx()), strokeWidth = 1.6.dp.toPx())
            }
            AppGlyph.Appearance -> {
                drawCircle(color, radius = 5.dp.toPx(), style = stroke)
                drawLine(color, Offset(w / 2, 1.dp.toPx()), Offset(w / 2, 4.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(w / 2, h - 1.dp.toPx()), Offset(w / 2, h - 4.dp.toPx()), strokeWidth = 1.6.dp.toPx())
            }
            // Two arrows chasing each other round a circle, the WP8.1 sync mark (distinct from transfer's straight arrows).
            AppGlyph.Sync -> {
                // As in due north tasks: two arcs chasing each other clockwise, each ending in a solid
                // head that sits on the arc's end and points along it (WP8.1 sync), so the glyph reads
                // the same mid-spin. Drawn on a 24 x 24 grid scaled to the canvas.
                val u = size.minDimension / 24f
                val r = 7.5f
                for (end in listOf(SYNC_TOP_END, SYNC_TOP_END + 180f)) {
                    drawArc(
                        color,
                        end - SYNC_SWEEP,
                        SYNC_SWEEP,
                        false,
                        topLeft = Offset((12f - r) * u, (12f - r) * u),
                        size =
                            androidx.compose.ui.geometry
                                .Size(2 * r * u, 2 * r * u),
                        style = stroke,
                    )
                    val a = Math.toRadians(end.toDouble())
                    // Outward from the centre, then clockwise along the arc.
                    val nx = kotlin.math.cos(a).toFloat()
                    val ny = kotlin.math.sin(a).toFloat()
                    val tx = -ny
                    val ty = nx
                    val ex = 12f + r * nx
                    val ey = 12f + r * ny
                    drawPath(
                        Path().apply {
                            moveTo((ex + tx * SYNC_HEAD_LENGTH) * u, (ey + ty * SYNC_HEAD_LENGTH) * u)
                            lineTo((ex + nx * SYNC_HEAD_HALF) * u, (ey + ny * SYNC_HEAD_HALF) * u)
                            lineTo((ex - nx * SYNC_HEAD_HALF) * u, (ey - ny * SYNC_HEAD_HALF) * u)
                            close()
                        },
                        color,
                    )
                }
            }
            AppGlyph.Payees -> {
                drawCircle(color, radius = 3.5.dp.toPx(), center = Offset(w / 2, 7.dp.toPx()), style = stroke)
                drawLine(color, Offset(5.dp.toPx(), 18.dp.toPx()), Offset(17.dp.toPx(), 18.dp.toPx()), strokeWidth = 1.6.dp.toPx())
            }
            AppGlyph.Hold -> {
                drawLine(color, Offset(7.dp.toPx(), 5.dp.toPx()), Offset(7.dp.toPx(), 17.dp.toPx()), strokeWidth = 2.2.dp.toPx())
                drawLine(color, Offset(15.dp.toPx(), 5.dp.toPx()), Offset(15.dp.toPx(), 17.dp.toPx()), strokeWidth = 2.2.dp.toPx())
            }
            AppGlyph.Categories -> {
                drawRect(
                    color,
                    topLeft = Offset(4.dp.toPx(), 5.dp.toPx()),
                    size =
                        androidx.compose.ui.geometry
                            .Size(14.dp.toPx(), 12.dp.toPx()),
                    style = stroke,
                )
                drawLine(color, Offset(4.dp.toPx(), 9.dp.toPx()), Offset(18.dp.toPx(), 9.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(4.dp.toPx(), 13.dp.toPx()), Offset(18.dp.toPx(), 13.dp.toPx()), strokeWidth = 1.6.dp.toPx())
            }
            AppGlyph.Add -> {
                drawLine(color, Offset(w / 2, 4.dp.toPx()), Offset(w / 2, 18.dp.toPx()), strokeWidth = 1.8.dp.toPx())
                drawLine(color, Offset(4.dp.toPx(), h / 2), Offset(18.dp.toPx(), h / 2), strokeWidth = 1.8.dp.toPx())
            }
            AppGlyph.Settings -> {
                drawCircle(color, radius = 3.dp.toPx(), style = stroke)
                drawCircle(color, radius = 8.dp.toPx(), style = stroke)
            }
            AppGlyph.Transfer -> {
                drawLine(color, Offset(4.dp.toPx(), 8.dp.toPx()), Offset(16.dp.toPx(), 8.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(13.dp.toPx(), 5.dp.toPx()), Offset(17.dp.toPx(), 8.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(18.dp.toPx(), 14.dp.toPx()), Offset(6.dp.toPx(), 14.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(9.dp.toPx(), 17.dp.toPx()), Offset(5.dp.toPx(), 14.dp.toPx()), strokeWidth = 1.6.dp.toPx())
            }
            AppGlyph.Reconcile -> {
                drawLine(color, Offset(5.dp.toPx(), 11.dp.toPx()), Offset(9.dp.toPx(), 16.dp.toPx()), strokeWidth = 1.8.dp.toPx())
                drawLine(color, Offset(9.dp.toPx(), 16.dp.toPx()), Offset(17.dp.toPx(), 6.dp.toPx()), strokeWidth = 1.8.dp.toPx())
            }
            AppGlyph.Import -> {
                drawLine(color, Offset(w / 2, 4.dp.toPx()), Offset(w / 2, 14.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(7.dp.toPx(), 11.dp.toPx()), Offset(w / 2, 15.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(15.dp.toPx(), 11.dp.toPx()), Offset(w / 2, 15.dp.toPx()), strokeWidth = 1.6.dp.toPx())
            }
            AppGlyph.Save -> {
                drawLine(color, Offset(5.dp.toPx(), 11.dp.toPx()), Offset(9.dp.toPx(), 16.dp.toPx()), strokeWidth = 1.8.dp.toPx())
                drawLine(color, Offset(9.dp.toPx(), 16.dp.toPx()), Offset(17.dp.toPx(), 6.dp.toPx()), strokeWidth = 1.8.dp.toPx())
            }
            AppGlyph.Cancel -> {
                drawLine(color, Offset(6.dp.toPx(), 6.dp.toPx()), Offset(16.dp.toPx(), 16.dp.toPx()), strokeWidth = 1.8.dp.toPx())
                drawLine(color, Offset(16.dp.toPx(), 6.dp.toPx()), Offset(6.dp.toPx(), 16.dp.toPx()), strokeWidth = 1.8.dp.toPx())
            }
            AppGlyph.ChevronLeft -> {
                drawLine(color, Offset(13.dp.toPx(), 5.dp.toPx()), Offset(7.dp.toPx(), 11.dp.toPx()), strokeWidth = 1.8.dp.toPx())
                drawLine(color, Offset(7.dp.toPx(), 11.dp.toPx()), Offset(13.dp.toPx(), 17.dp.toPx()), strokeWidth = 1.8.dp.toPx())
            }
            AppGlyph.ChevronRight -> {
                drawLine(color, Offset(9.dp.toPx(), 5.dp.toPx()), Offset(15.dp.toPx(), 11.dp.toPx()), strokeWidth = 1.8.dp.toPx())
                drawLine(color, Offset(15.dp.toPx(), 11.dp.toPx()), Offset(9.dp.toPx(), 17.dp.toPx()), strokeWidth = 1.8.dp.toPx())
            }
            AppGlyph.ChevronDown -> {
                drawLine(color, Offset(5.dp.toPx(), 8.dp.toPx()), Offset(11.dp.toPx(), 14.dp.toPx()), strokeWidth = 1.8.dp.toPx())
                drawLine(color, Offset(11.dp.toPx(), 14.dp.toPx()), Offset(17.dp.toPx(), 8.dp.toPx()), strokeWidth = 1.8.dp.toPx())
            }
            AppGlyph.Calendar -> {
                drawRect(
                    color,
                    topLeft = Offset(4.dp.toPx(), 6.dp.toPx()),
                    size =
                        androidx.compose.ui.geometry
                            .Size(14.dp.toPx(), 12.dp.toPx()),
                    style = stroke,
                )
                drawLine(color, Offset(4.dp.toPx(), 10.dp.toPx()), Offset(18.dp.toPx(), 10.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(8.dp.toPx(), 4.dp.toPx()), Offset(8.dp.toPx(), 8.dp.toPx()), strokeWidth = 1.6.dp.toPx())
            }
            AppGlyph.Select -> {
                // A list with a box before each line: the WP "select" glyph.
                listOf(5f, 13f).forEach { top ->
                    drawRect(
                        color,
                        topLeft = Offset(4.dp.toPx(), top.dp.toPx()),
                        size =
                            androidx.compose.ui.geometry
                                .Size(5.dp.toPx(), 5.dp.toPx()),
                        style = stroke,
                    )
                    drawLine(
                        color,
                        Offset(12.dp.toPx(), (top + 2.5f).dp.toPx()),
                        Offset(18.dp.toPx(), (top + 2.5f).dp.toPx()),
                        strokeWidth = 1.6.dp.toPx(),
                    )
                }
            }
            AppGlyph.Search -> {
                // A lens with its handle running down to the right: the WP "search" glyph.
                drawCircle(color, radius = 5.5.dp.toPx(), center = Offset(9.5.dp.toPx(), 9.5.dp.toPx()), style = stroke)
                drawLine(color, Offset(13.5.dp.toPx(), 13.5.dp.toPx()), Offset(18.dp.toPx(), 18.dp.toPx()), strokeWidth = 1.6.dp.toPx())
            }
            AppGlyph.Delete -> {
                // A bin under its lid, with its handle and two ribs: the WP "delete" glyph.
                drawLine(color, Offset(4.dp.toPx(), 6.dp.toPx()), Offset(18.dp.toPx(), 6.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(9.dp.toPx(), 3.5.dp.toPx()), Offset(13.dp.toPx(), 3.5.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawRect(
                    color,
                    topLeft = Offset(6.dp.toPx(), 6.dp.toPx()),
                    size =
                        androidx.compose.ui.geometry
                            .Size(10.dp.toPx(), 12.dp.toPx()),
                    style = stroke,
                )
                listOf(9.5f, 12.5f).forEach { x ->
                    drawLine(color, Offset(x.dp.toPx(), 9.dp.toPx()), Offset(x.dp.toPx(), 15.dp.toPx()), strokeWidth = 1.2.dp.toPx())
                }
            }
            AppGlyph.Edit -> {
                // A pencil leaning down to the left, its point at the bottom and a band below its end: the WP "edit" glyph.
                drawPath(
                    Path().apply {
                        moveTo(4.dp.toPx(), 18.dp.toPx())
                        lineTo(5.6.dp.toPx(), 13.6.dp.toPx())
                        lineTo(14.6.dp.toPx(), 4.6.dp.toPx())
                        lineTo(17.4.dp.toPx(), 7.4.dp.toPx())
                        lineTo(8.4.dp.toPx(), 16.4.dp.toPx())
                        close()
                    },
                    color,
                    style = Stroke(width = 1.6.dp.toPx(), join = StrokeJoin.Miter),
                )
                drawLine(color, Offset(12.6.dp.toPx(), 6.6.dp.toPx()), Offset(15.4.dp.toPx(), 9.4.dp.toPx()), strokeWidth = 1.6.dp.toPx())
            }
            AppGlyph.Merge -> {
                // Two lines running together into one arrow: the WP "merge" glyph.
                val sw = 1.6.dp.toPx()
                drawLine(color, Offset(4.dp.toPx(), 5.dp.toPx()), Offset(11.dp.toPx(), 11.dp.toPx()), strokeWidth = sw)
                drawLine(color, Offset(4.dp.toPx(), 17.dp.toPx()), Offset(11.dp.toPx(), 11.dp.toPx()), strokeWidth = sw)
                drawLine(color, Offset(11.dp.toPx(), 11.dp.toPx()), Offset(18.dp.toPx(), 11.dp.toPx()), strokeWidth = sw)
                drawPath(
                    Path().apply {
                        moveTo(14.5.dp.toPx(), 7.5.dp.toPx())
                        lineTo(18.dp.toPx(), 11.dp.toPx())
                        lineTo(14.5.dp.toPx(), 14.5.dp.toPx())
                    },
                    color,
                    style = Stroke(width = sw, join = StrokeJoin.Miter),
                )
            }
            AppGlyph.Breakdown -> {
                drawLine(color, Offset(4.dp.toPx(), 5.dp.toPx()), Offset(18.dp.toPx(), 5.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(4.dp.toPx(), 9.dp.toPx()), Offset(14.dp.toPx(), 9.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(4.dp.toPx(), 13.dp.toPx()), Offset(16.dp.toPx(), 13.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(4.dp.toPx(), 18.dp.toPx()), Offset(18.dp.toPx(), 18.dp.toPx()), strokeWidth = 2.2.dp.toPx())
            }
            AppGlyph.Copy -> {
                drawRect(
                    color,
                    topLeft = Offset(4.dp.toPx(), 7.dp.toPx()),
                    size =
                        androidx.compose.ui.geometry
                            .Size(11.dp.toPx(), 11.dp.toPx()),
                    style = stroke,
                )
                drawRect(
                    color,
                    topLeft = Offset(8.dp.toPx(), 3.dp.toPx()),
                    size =
                        androidx.compose.ui.geometry
                            .Size(11.dp.toPx(), 11.dp.toPx()),
                    style = stroke,
                )
            }
            AppGlyph.Average -> {
                drawLine(color, Offset(4.dp.toPx(), 15.dp.toPx()), Offset(4.dp.toPx(), 18.dp.toPx()), strokeWidth = 2.2.dp.toPx())
                drawLine(color, Offset(11.dp.toPx(), 9.dp.toPx()), Offset(11.dp.toPx(), 18.dp.toPx()), strokeWidth = 2.2.dp.toPx())
                drawLine(color, Offset(3.dp.toPx(), 12.dp.toPx()), Offset(19.dp.toPx(), 12.dp.toPx()), strokeWidth = 1.2.dp.toPx())
            }
            AppGlyph.Zero -> {
                drawOval(
                    color,
                    topLeft = Offset(6.dp.toPx(), 4.dp.toPx()),
                    size =
                        androidx.compose.ui.geometry
                            .Size(10.dp.toPx(), 14.dp.toPx()),
                    style = stroke,
                )
            }
            AppGlyph.Undo -> {
                drawLine(color, Offset(5.dp.toPx(), 9.dp.toPx()), Offset(15.dp.toPx(), 9.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(5.dp.toPx(), 9.dp.toPx()), Offset(9.dp.toPx(), 5.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawArc(
                    color,
                    startAngle = -90f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(11.dp.toPx(), 9.dp.toPx()),
                    size =
                        androidx.compose.ui.geometry
                            .Size(8.dp.toPx(), 8.dp.toPx()),
                    style = stroke,
                )
            }
        }
    }
}

@Composable
fun AppGlyphIcon(
    glyph: AppGlyph,
    color: Color = Metro.colors.foreground,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
) {
    val mobile10 = Metro.style.mobile10
    Canvas(modifier.size(size)) {
        if (mobile10 && Mdl2GlyphInk.covers(glyph)) {
            with(Mdl2GlyphInk) { draw(glyph, color) }
        } else {
            with(AppGlyphInk) { draw(glyph, color) }
        }
    }
}

/**
 * The glyphs of the Windows 10 Mobile style, after Segoe MDL2 Assets (which may not be bundled): hairline strokes on
 * a 20-unit grid, square ends and mitred corners, drawn 20dp inside the glyph's box. Each is an SVG path.
 */
object Mdl2GlyphInk {
    private const val GRID = 20f

    private val paths: Map<AppGlyph, String> =
        mapOf(
            // OpenFolder: the budget files.
            AppGlyph.Budgets to "M1.5 16.5V3.5h5.5l2 2h8v3M1.5 16.5l3-8h14l-3 8z",
            // Brightness: a sun and its eight rays.
            AppGlyph.Appearance to
                "M10 6a4 4 0 1 0 0 8a4 4 0 1 0 0-8zM10 1v2.5M10 16.5V19M1 10h2.5M16.5 10H19M3.6 3.6l1.8 1.8M14.6 14.6l1.8 1.8" +
                "M3.6 16.4l1.8-1.8M14.6 5.4l1.8-1.8",
            // Sync: two arcs chasing each other, each with a corner for its arrowhead.
            AppGlyph.Sync to "M16.4 8.5A6.6 6.6 0 0 0 4.3 6.5M3.6 11.5a6.6 6.6 0 0 0 12.1 2M4 2.5v4.2h4.2M16 17.5v-4.2h-4.2",
            // Contact: a head over the shoulders.
            AppGlyph.Payees to "M10 2a4.2 4.2 0 1 0 0 8.4a4.2 4.2 0 1 0 0-8.4zM2.5 18.5a7.5 7.5 0 0 1 15 0",
            // Pause.
            AppGlyph.Hold to "M5 3.5h3v13H5zM12 3.5h3v13h-3z",
            // ViewAll: four tiles.
            AppGlyph.Categories to "M2.5 2.5h6v6h-6zM11.5 2.5h6v6h-6zM2.5 11.5h6v6h-6zM11.5 11.5h6v6h-6z",
            AppGlyph.Add to "M10 2v16M2 10h16",
            // Switch: one arrow out, one back.
            AppGlyph.Transfer to "M2 7h15M13.5 3.5L17 7l-3.5 3.5M18 13H3M6.5 9.5L3 13l3.5 3.5",
            // CheckMark.
            AppGlyph.Reconcile to "M2.5 10.5l5 5L17.5 5",
            // Download: an arrow onto a line.
            AppGlyph.Import to "M10 2v11.5M5.5 9l4.5 4.5L14.5 9M2.5 17.5h15",
            // Accept.
            AppGlyph.Save to "M2.5 10.5l5 5L17.5 5",
            AppGlyph.Cancel to "M3.5 3.5l13 13M16.5 3.5l-13 13",
            AppGlyph.ChevronLeft to "M13 3.5L6.5 10l6.5 6.5",
            AppGlyph.ChevronRight to "M7 3.5l6.5 6.5L7 16.5",
            AppGlyph.ChevronDown to "M3.5 7l6.5 6.5L16.5 7",
            // Calendar: a page under its binding rings.
            AppGlyph.Calendar to "M2 4h16v14H2zM2 8h16M6 2v4M14 2v4",
            // List.
            AppGlyph.Breakdown to "M2 4.5h16M2 8.5h11M2 12.5h13M2 16.5h16",
            AppGlyph.Copy to "M6.5 6.5h11v11h-11zM3.5 13.5h-1v-11h11v1",
            // A bar chart under its mean.
            AppGlyph.Average to "M3.5 18v-4M10 18V7M16.5 18v-6M2 10.5h16",
            AppGlyph.Zero to "M10 3a5 7 0 1 1 0 14a5 7 0 1 1 0-14z",
            // Undo: back along a line that turns home.
            AppGlyph.Undo to "M6.5 3.5L3 7l3.5 3.5M3 7h9a4.5 4.5 0 0 1 0 9H6",
            // MultiSelect: ticked boxes before their lines.
            AppGlyph.Select to "M2 3h6v6H2zM2 11h6v6H2zM3.6 6.1l1.2 1.2L6.6 5M11 6h7M11 14h7",
            // Search: the lens up and right, its handle down and left.
            AppGlyph.Search to "M12 2a6 6 0 1 0 0 12a6 6 0 1 0 0-12zM7.7 12.3L2 18",
            // Delete: a bin with its lid and handle and two ribs.
            AppGlyph.Delete to "M2 4.5h16M7.5 4.5V2h5v2.5M4 4.5l1 13.5h10l1-13.5M8.5 7.5v7.5M11.5 7.5v7.5",
            // Edit: a pencil down to the left, its tip and the band behind it.
            AppGlyph.Edit to "M2.5 17.5l1-4.5L14 2.5L17.5 6L7 16.5zM12 4.5L15.5 8M3.5 13L7 16.5",
            // Merge: two lines running together into one arrow.
            AppGlyph.Merge to "M2 3.5l7 6.5M2 16.5l7-6.5M9 10h9M14.5 6.5L18 10l-3.5 3.5",
        )

    /** Whether this style has its own ink for [glyph]; one it lacks falls back to Metro's rather than failing. */
    fun covers(glyph: AppGlyph): Boolean = glyph == AppGlyph.Settings || glyph in paths

    private val parsed = mutableMapOf<AppGlyph, Path>()

    private fun pathOf(glyph: AppGlyph): Path =
        parsed.getOrPut(glyph) {
            if (glyph == AppGlyph.Settings) gear() else PathParser().parsePathString(paths.getValue(glyph)).toPath()
        }

    /** Settings: an eight-toothed gear round its hub. */
    private fun gear(): Path =
        Path().apply {
            val teeth = 8
            val outer = 8.5f
            val inner = 6.6f
            for (i in 0 until teeth * 4) {
                val angle = Math.toRadians(i * 360.0 / (teeth * 4) - 90.0 / teeth)
                val r = if ((i / 2) % 2 == 0) outer else inner
                val x = 10f + r * kotlin.math.cos(angle).toFloat()
                val y = 10f + r * kotlin.math.sin(angle).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
            addOval(
                androidx.compose.ui.geometry
                    .Rect(7f, 7f, 13f, 13f),
            )
        }

    fun DrawScope.draw(
        glyph: AppGlyph,
        color: Color,
    ) {
        val box = size.minDimension
        val glyphSize = minOf(box, 20.dp.toPx())
        val scale = glyphSize / GRID
        translate((size.width - glyphSize) / 2, (size.height - glyphSize) / 2) {
            scale(scale, pivot = Offset.Zero) {
                drawPath(
                    pathOf(glyph),
                    color,
                    style = Stroke(width = 1.dp.toPx() / scale, cap = StrokeCap.Square, join = StrokeJoin.Miter),
                )
            }
        }
    }
}
