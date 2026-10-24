package app.duenorth.budget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.relief
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import app.duenorth.budget.design.theme.Relief

object InboxEmptyCopy {
    const val TITLE = "all caught up"
    const val BODY = "new and imported transactions wait here until you give them a category."
    const val ADD = "add transaction"
}

/**
 * The inbox with nothing in it: an empty tray with a paper plane flying out of it, chosen by the owner on
 * 2026-10-23 from three mockups. Everything is drawn from the theme, so the tray takes the style (an outlined box
 * under Metro, sunk into a raised panel under tile relief, a field on a layer card under mica) and the plane and
 * sparkles take the accent.
 */
@Composable
fun InboxEmpty(
    modifier: Modifier = Modifier,
    onAdd: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxSize().padding(MetroDimens.Gutter).testTag("inbox-empty"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PaperPlane()
        Tray()
        Spacer(Modifier.height(30.dp))
        MetroText(InboxEmptyCopy.TITLE, Metro.typography.subheader.copy(textAlign = TextAlign.Center))
        MetroText(
            InboxEmptyCopy.BODY,
            Metro.typography.body.copy(textAlign = TextAlign.Center),
            color = Metro.colors.secondary,
            modifier = Modifier.padding(top = 8.dp).widthIn(max = 290.dp),
        )
        if (onAdd != null) {
            Spacer(Modifier.height(24.dp))
            MetroButton(InboxEmptyCopy.ADD, onClick = onAdd)
        }
    }
}

/** The plane and its dotted trail, drawn on a 260 by 110 dp grid. */
@Composable
private fun PaperPlane() {
    val accent = Metro.accent
    val trail = Metro.colors.secondary
    Canvas(Modifier.size(260.dp, 110.dp)) {
        scale(density, pivot = Offset.Zero) {
            val loop =
                Path().apply {
                    moveTo(70f, 100f)
                    cubicTo(40f, 70f, 90f, 40f, 130f, 62f)
                    cubicTo(170f, 84f, 190f, 70f, 205f, 30f)
                }
            drawPath(
                loop,
                trail,
                style = Stroke(2f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.01f, 9f))),
            )
            drawCircle(accent.text, 2.5f, Offset(40f, 30f))
            drawCircle(accent.text, 2f, Offset(238f, 70f))
            drawPath(
                Path().apply {
                    moveTo(160f, 14f)
                    lineTo(162f, 19f)
                    lineTo(167f, 21f)
                    lineTo(162f, 23f)
                    lineTo(160f, 28f)
                    lineTo(158f, 23f)
                    lineTo(153f, 21f)
                    lineTo(158f, 19f)
                    close()
                },
                accent.text,
            )
            translate(212f, 22f) {
                rotate(-28f, pivot = Offset.Zero) {
                    drawPath(triangle(-22f, 0f, 22f, -14f, 4f, 14f), accent.fill)
                    // The near wing, a shade off the fill so the fold reads on any accent.
                    drawPath(triangle(-22f, 0f, 22f, -14f, -2f, 5f), accent.text.copy(alpha = 0.7f))
                    drawPath(triangle(-2f, 5f, 4f, 14f, 2f, 4f), Color.Black.copy(alpha = 0.25f))
                }
            }
        }
    }
}

private fun triangle(
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float,
    x3: Float,
    y3: Float,
): Path =
    Path().apply {
        moveTo(x1, y1)
        lineTo(x2, y2)
        lineTo(x3, y3)
        close()
    }

/** The empty tray: a raised panel with a well pressed into it, or under Metro an outline around an outline. */
@Composable
private fun Tray() {
    val colors = Metro.colors
    val relief = Metro.style.relief
    val shelf = colors.secondary.copy(alpha = 0.45f)
    Box(
        Modifier
            .padding(top = 4.dp)
            .size(220.dp, 86.dp)
            .then(if (relief) Modifier.relief(Relief.Raised) else Modifier.border(MetroDimens.Stroke, colors.foreground))
            .padding(12.dp),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (relief) Modifier.relief(Relief.Inset) else Modifier.border(MetroDimens.Stroke, colors.secondary))
                .drawBehind {
                    val y = size.height - 13.dp.toPx()
                    val half = 55.dp.toPx()
                    drawLine(
                        shelf,
                        Offset(size.width / 2 - half, y),
                        Offset(size.width / 2 + half, y),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                },
        )
    }
}
