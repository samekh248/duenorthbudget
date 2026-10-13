package app.duenorth.budget.design

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
