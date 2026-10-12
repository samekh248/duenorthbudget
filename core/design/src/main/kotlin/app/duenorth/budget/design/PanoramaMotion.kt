package app.duenorth.budget.design

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Title lag for the panorama. Adjacent pages differ by [stepPx] (the width of "due").
 * Even pages rest at 0; odd pages rest at -[stepPx]. Fractional pages lerp.
 */
object PanoramaMotion {
    fun titleShiftPx(
        pagePosition: Float,
        sectionCount: Int,
        stepPx: Float,
        motionEnabled: Boolean,
    ): Float {
        if (!motionEnabled || sectionCount <= 1 || stepPx <= 0f) return 0f
        val floorPage = floor(pagePosition.toDouble()).toInt()
        val ceilPage = ceil(pagePosition.toDouble()).toInt()
        val fraction = (pagePosition - floorPage).coerceIn(0f, 1f)
        val start = restShift(floorPage, stepPx)
        val end = restShift(ceilPage, stepPx)
        val shift = start + (end - start) * fraction
        return shift.coerceIn(-abs(stepPx), 0f)
    }

    private fun restShift(
        page: Int,
        stepPx: Float,
    ): Float = if (page % 2 == 0) 0f else -stepPx
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
