package app.duenorth.budget.design

/**
 * The panorama title moves 15% of its own width from the first section to the last,
 * which is slower than the sections (Due North Tasks research R3, TITLE_TRAVEL).
 * A position past the last section eases the title back toward the start so the loop
 * does not jump.
 */
object PanoramaMotion {
    const val TITLE_TRAVEL = 0.15f

    fun titleShiftPx(
        sectionPosition: Float,
        sectionCount: Int,
        titleWidthPx: Float,
    ): Float {
        if (sectionCount <= 1 || titleWidthPx <= 0f) return 0f
        val span = (sectionCount - 1).toFloat()
        val cycle = ((sectionPosition % sectionCount) + sectionCount) % sectionCount
        val progress =
            if (cycle <= span) {
                cycle / span
            } else {
                val wrap = (cycle - span) / (sectionCount - span)
                1f - wrap
            }
        val shift = progress * titleWidthPx * TITLE_TRAVEL
        return if (shift == 0f) 0f else -shift
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
