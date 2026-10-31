package app.duenorth.budget

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.duenorth.budget.design.theme.Metro

/** Holds every [WorkingDots] below it at one moment of its cycle, for screenshots. */
internal val LocalWorkingDotsAt = staticCompositionLocalOf<Float?> { null }

/** Copy for the working dots. */
object WorkingCopy {
    const val WORKING = "working"
}

/**
 * The Windows Phone indeterminate progress bar: five accent dots that run in from the left, bunch up
 * and slow through the middle, then speed off to the right, over and over. Windows Phone 8.1 and 10
 * use it for work of unknown length that does not stop the person using the page, along the top of
 * the page or under the thing that is busy. Under reduced motion the five dots sit still in the middle.
 */
@Composable
fun WorkingDots(
    modifier: Modifier = Modifier,
    description: String = WorkingCopy.WORKING,
    /** Holds the dots at this many milliseconds into their cycle; for screenshots, where animations do not run. */
    at: Float? = LocalWorkingDotsAt.current,
) {
    val color = Metro.accent.fill
    val clock =
        if (Metro.animations && at == null) {
            rememberInfiniteTransition(label = "working").animateFloat(
                initialValue = 0f,
                targetValue = CYCLE_MS,
                animationSpec = infiniteRepeatable(tween(CYCLE_MS.toInt(), easing = LinearEasing), RepeatMode.Restart),
                label = "clock",
            )
        } else {
            null
        }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            }.testTag("working-dots"),
    ) {
        val dot = 4.dp.toPx()
        val gap = 8.dp.toPx()
        val y = (size.height - dot) / 2f
        val now = at ?: clock?.value
        repeat(DOTS) { index ->
            val x =
                if (now == null) {
                    val row = DOTS * dot + (DOTS - 1) * gap
                    (size.width - row) / 2f + index * (dot + gap)
                } else {
                    // The first dot leads: later dots start a little after it and trail it across.
                    val along = (now - (DOTS - 1 - index) * STAGGER_MS) / TRAVEL_MS
                    if (along !in 0f..1f) return@repeat
                    travel(along) * (size.width + dot) - dot
                }
            drawRect(color, Offset(x, y), Size(dot, dot))
        }
    }
}

/** Where a dot is, from 0 (off the left edge) to 1 (off the right), [along] its run: fast, slow through the middle, fast. */
internal fun travel(along: Float): Float {
    val centred = along - 0.5f
    val eased = 4f * centred * centred * centred + 0.5f
    return SLOW_SHARE * eased + (1f - SLOW_SHARE) * along
}

private const val DOTS = 5
private const val CYCLE_MS = 3200f
private const val TRAVEL_MS = 2400f
private const val STAGGER_MS = 160f

/** How much of a dot's run follows the slow-in-the-middle curve rather than a steady pace. */
private const val SLOW_SHARE = 0.7f
