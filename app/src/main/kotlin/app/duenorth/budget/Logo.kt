package app.duenorth.budget

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import app.duenorth.budget.design.theme.Metro
import kotlinx.coroutines.launch

/**
 * The "growth north" logo, drawing itself: the arrowhead rises up its stem with fading echoes
 * (the Due North Tasks motion), then the two short bars grow in behind it. Plays once when it
 * appears and again on tap; rests when animations are off. Strokes on a Canvas, nothing to load.
 */
@Composable
internal fun BudgetLogo(
    height: Dp,
    modifier: Modifier = Modifier,
    color: Color = Metro.accent.fill,
) {
    val animate = Metro.animations
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    val scope = rememberCoroutineScope()
    val play: () -> Unit = {
        scope.launch {
            if (!animate) {
                progress.snapTo(1f)
            } else {
                progress.snapTo(0f)
                progress.animateTo(1f, tween(LogoMotion.DURATION_MS, easing = LinearEasing))
            }
        }
    }
    LaunchedEffect(animate) { play() }
    Canvas(
        modifier
            .height(height)
            .width(height * LogoMotion.ASPECT)
            .clickable(interactionSource = null, indication = null, onClick = play)
            .semantics {
                contentDescription = "due north budget logo"
                role = Role.Image
                onClick("replay") {
                    play()
                    true
                }
            }.testTag("budget-logo"),
    ) { drawLogo(progress.value, color) }
}

/** The logo animation's timeline, in the launcher icon's 108-unit space. */
internal object LogoMotion {
    const val DURATION_MS = 700

    /** The crop around the mark: x 33..75, y 27..80. */
    const val LEFT = 33f
    const val TOP = 27f
    const val WIDTH = 42f
    const val HEIGHT = 53f
    const val ASPECT = WIDTH / HEIGHT
    const val STROKE = 5.5f

    /** The arrow's column, its tip, and the shared floor of all three bars. */
    const val STEM_X = 59f
    const val TIP_Y = 33f
    const val FLOOR = 79f

    /** The two short bars: x and resting top. */
    val bars = listOf(37f to 67f, 48f to 57f)

    /** How far below its resting place the arrowhead starts. */
    const val RISE = 30f
    const val RISE_MS = 600f

    /** Each trail frame: its offset below the tip, when it flashes, and how long it fades. */
    val trails = listOf(24f to 50f, 18f to 100f, 12f to 160f, 6f to 220f)
    const val TRAIL_FADE_MS = 300f
    const val TRAIL_ALPHA = 0.5f

    /** The bars grow once the head has passed them, the shorter one first. */
    const val BAR_START_MS = 320f
    const val BAR_STAGGER_MS = 80f
    const val BAR_MS = 220f
}

private fun easeOut(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return 1f - (1f - t) * (1f - t) * (1f - t)
}

/** Draws "growth north" at [progress] (0 = start of the animation, 1 = the launcher icon). */
internal fun DrawScope.drawLogo(
    progress: Float,
    color: Color,
) {
    val m = LogoMotion
    val scale = size.height / m.HEIGHT

    fun p(
        x: Float,
        y: Float,
    ) = Offset((x - m.LEFT) * scale, (y - m.TOP) * scale)
    val width = m.STROKE * scale
    val square = Stroke(width, cap = StrokeCap.Square, join = StrokeJoin.Miter)

    fun chevron(dy: Float) =
        Path().apply {
            p(m.STEM_X - 12f, m.TIP_Y + 12f + dy).let { moveTo(it.x, it.y) }
            p(m.STEM_X, m.TIP_Y + dy).let { lineTo(it.x, it.y) }
            p(m.STEM_X + 12f, m.TIP_Y + 12f + dy).let { lineTo(it.x, it.y) }
        }

    fun bar(
        x: Float,
        top: Float,
    ) = drawLine(color, p(x, m.FLOOR), p(x, top), width, StrokeCap.Butt)

    val ms = progress.coerceAtMost(1f) * m.DURATION_MS
    if (progress < 1f) {
        m.trails.forEach { (dy, at) ->
            val t = (ms - at) / m.TRAIL_FADE_MS
            if (t in 0f..1f) drawPath(chevron(dy), color, alpha = m.TRAIL_ALPHA * (1f - t), style = square)
        }
    }
    m.bars.forEachIndexed { i, (x, top) ->
        val grow = easeOut((ms - m.BAR_START_MS - i * m.BAR_STAGGER_MS) / m.BAR_MS)
        if (grow > 0f) bar(x, m.FLOOR - (m.FLOOR - top) * grow)
    }
    val rise = m.RISE * (1f - easeOut(ms / m.RISE_MS))
    bar(m.STEM_X, m.TIP_Y + rise)
    drawPath(chevron(rise), color, style = square)
}
