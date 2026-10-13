package app.duenorth.budget.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import app.duenorth.budget.design.theme.Metro
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val MAX_TILT_DEGREES = 10f
private const val PRESSED_SCALE = 0.97f
private const val TILT_MS = 100

/** Items wider than this tilt proportionally less, so a full-width row does not swing wildly. */
private const val FULL_TILT_WIDTH_DP = 120f

/**
 * Metro tilt, as in due north tasks: the pressed item leans toward the finger and sinks slightly,
 * then springs back on release. It only observes touches, so it combines with clickable. Uses
 * graphicsLayer only, so pressing never relayouts.
 */
fun Modifier.metroTilt(): Modifier =
    this.composed {
        if (!Metro.animations) return@composed Modifier
        val scope = rememberCoroutineScope()
        val tilt = remember { TiltState() }
        Modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    // Initial pass, so a touch an expanded app bar's host swallowed never tilts anything.
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (down.isConsumed) return@awaitEachGesture
                    tilt.press(scope, down.position, size, density)
                    waitForUpOrCancellation()
                    tilt.release(scope)
                }
            }.graphicsLayer {
                rotationX = tilt.rotationX.value
                rotationY = tilt.rotationY.value
                scaleX = tilt.scale.value
                scaleY = tilt.scale.value
                cameraDistance = 16 * density
            }
    }

private class TiltState {
    val rotationX = Animatable(0f)
    val rotationY = Animatable(0f)
    val scale = Animatable(1f)

    fun press(
        scope: CoroutineScope,
        at: Offset,
        size: IntSize,
        density: Float,
    ) {
        if (size.width == 0 || size.height == 0) return
        // -1..1 from the center to each edge.
        val nx = (at.x / size.width - 0.5f) * 2f
        val ny = (at.y / size.height - 0.5f) * 2f
        val yLimit = MAX_TILT_DEGREES * min(1f, FULL_TILT_WIDTH_DP / (size.width / density))
        val xLimit = MAX_TILT_DEGREES * min(1f, FULL_TILT_WIDTH_DP / (size.height / density))
        // Pressing near the middle sinks the item more than it leans.
        val edge = max(abs(nx), abs(ny))
        animate(scope, rotationY, nx * yLimit)
        animate(scope, rotationX, -ny * xLimit)
        animate(scope, scale, 1f - (1f - PRESSED_SCALE) * (1f - edge * 0.5f))
    }

    fun release(scope: CoroutineScope) {
        animate(scope, rotationX, 0f)
        animate(scope, rotationY, 0f)
        animate(scope, scale, 1f)
    }

    private fun animate(
        scope: CoroutineScope,
        value: Animatable<Float, AnimationVector1D>,
        target: Float,
    ) {
        scope.launch { value.animateTo(target, tween(TILT_MS)) }
    }
}
