package app.duenorth.budget.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing

/** The app's shared durations and easing, so every motion moves at one pace. */
object MetroMotion {
    /** Metro's exponential ease out: quick off the mark, with a long soft landing. For things arriving. */
    val EaseOut: Easing = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1f)

    /** For a slot that grows or shrinks in place. */
    val Standard: Easing = FastOutSlowInEasing

    /** Fades and colour changes. */
    const val QUICK_MS = 160

    /** A row or panel resizing, and the app bar opening. */
    const val SHORT_MS = 240

    /** A page turning in, and the next inbox row rising into the panel. */
    const val PAGE_MS = 360
}

/** Header order and travel for the pivot. Items loop, so the row wraps after the last one. */
object PivotMotion {
    /** Item indexes in header-row order, starting with [lead]. */
    fun order(
        lead: Int,
        count: Int,
    ): List<Int> = List(count) { Math.floorMod(lead + it, count) }

    /** Shortest signed number of pages from [from] to [to] around the loop. */
    fun delta(
        from: Int,
        to: Int,
        count: Int,
    ): Int {
        var delta = to - from
        if (delta > count / 2) delta -= count
        if (delta < -count / 2) delta += count
        return delta
    }
}

object MotionPolicy {
    fun enabled(animatorScale: Float): Boolean = animatorScale > 0f
}

object PressFeedback {
    fun rotation(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        animations: Boolean,
    ): Pair<Float, Float> {
        if (!animations || width <= 0f || height <= 0f) return 0f to 0f
        val rotationY = ((x / width) - 0.5f) * -16f
        val rotationX = ((y / height) - 0.5f) * 16f
        return rotationX to rotationY
    }
}
