package app.duenorth.budget

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import app.duenorth.budget.design.components.MetroText

/**
 * While a newly picked month's figures load, the month on screen still holds the last month's. Under
 * [FiguresLoading] every figure draws as a soft block of the same size instead, so the page keeps its shape (names,
 * headings, rows) and only the numbers wait. The blocks breathe together on one shared pulse.
 */
internal val LocalFigurePulse = compositionLocalOf<State<Float>?> { null }

/** Off holds the placeholders at full strength without the pulse, for screenshots that need the frame to settle. */
internal val LocalFigurePulseAnimates = staticCompositionLocalOf { true }

@Composable
internal fun FiguresLoading(
    loading: Boolean,
    content: @Composable () -> Unit,
) {
    if (!loading) {
        CompositionLocalProvider(LocalFigurePulse provides null, content = content)
        return
    }
    if (!LocalFigurePulseAnimates.current) {
        val still = remember { mutableFloatStateOf(1f) }
        CompositionLocalProvider(LocalFigurePulse provides still, content = content)
        return
    }
    val pulse =
        rememberInfiniteTransition(label = "figure-pulse").animateFloat(
            initialValue = PULSE_LOW,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(PULSE_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "figure-pulse-alpha",
        )
    CompositionLocalProvider(LocalFigurePulse provides pulse, content = content)
}

/** True while figures are drawn as placeholders. */
@Composable
internal fun figuresLoading(): Boolean = LocalFigurePulse.current != null

/**
 * A money figure: the text, or while [FiguresLoading] a block as tall as the text's line and about as wide as
 * [text], tinted from [color] so it sits right on the page, on a band, or on an accent tile, light or dark.
 */
@Composable
internal fun FigureText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
) {
    val pulse = LocalFigurePulse.current
    if (pulse == null) {
        MetroText(text, style, modifier, color = color, maxLines = maxLines, softWrap = softWrap)
        return
    }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val (lineHeight, textWidth) =
        remember(text, style, density) {
            val size = measurer.measure(text, style, maxLines = 1).size
            with(density) { size.height.toDp() to size.width.toDp() }
        }
    val align =
        when (style.textAlign) {
            TextAlign.End, TextAlign.Right -> Alignment.CenterEnd
            TextAlign.Center -> Alignment.Center
            else -> Alignment.CenterStart
        }
    Box(modifier.height(lineHeight), contentAlignment = align) {
        Box(
            Modifier
                .width(textWidth * BLOCK_WIDTH)
                .height(lineHeight * BLOCK_HEIGHT)
                .graphicsLayer { alpha = pulse.value }
                .background(color.copy(alpha = BLOCK_ALPHA)),
        )
    }
}

private const val PULSE_LOW = 0.45f
private const val PULSE_MS = 700
private const val BLOCK_WIDTH = 0.85f
private const val BLOCK_HEIGHT = 0.5f
private const val BLOCK_ALPHA = 0.28f
