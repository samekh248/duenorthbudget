package app.duenorth.budget.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import app.duenorth.budget.design.theme.Metro

private const val TURNSTILE_DEGREES = 40f

/** The arriving page is fully opaque by this share of the turn. */
private const val TURNSTILE_FADE = 0.45f

private const val TURNSTILE_CAMERA = 16f

private class FirstPage(
    var pending: Boolean = true,
)

/**
 * Swings the page in about its left edge each time [page] changes, as Windows Phone's turnstile
 * does. Only the arriving page moves, so the page it replaces never has to stay composed. The page
 * shown first, and every page when animations are off, appears without motion.
 */
@Composable
fun Modifier.turnstileIn(page: Any): Modifier {
    val animations = Metro.animations
    val first = remember { FirstPage() }
    // A fresh turn per page, starting at 0 in the same composition so the page never flashes in first.
    val turn =
        remember(page) {
            Animatable(if (first.pending || !animations) 1f else 0f).also { first.pending = false }
        }
    LaunchedEffect(turn) {
        if (turn.value < 1f) turn.animateTo(1f, tween(MetroMotion.PAGE_MS, easing = MetroMotion.EaseOut))
    }
    return graphicsLayer {
        val t = turn.value
        if (t < 1f) {
            transformOrigin = TransformOrigin(0f, 0.5f)
            cameraDistance = TURNSTILE_CAMERA * density
            rotationY = TURNSTILE_DEGREES * (1f - t)
            alpha = (t / TURNSTILE_FADE).coerceAtMost(1f)
        }
    }
}
