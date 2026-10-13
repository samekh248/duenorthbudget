package app.duenorth.budget.design.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

/** The expanded app bar, if any, as the host sees it: where it is and how to close it. */
@Stable
internal class ExpandedAppBar(
    var collapse: () -> Unit,
) {
    var boundsInWindow: Rect = Rect.Zero
}

@Stable
internal class AppBarHostState {
    var expanded: ExpandedAppBar? = null
    var coordinates: LayoutCoordinates? = null
}

internal val LocalAppBarHost = staticCompositionLocalOf<AppBarHostState?> { null }

/**
 * Wraps a whole screen so an expanded [MetroAppBar] collapses when anything outside it is
 * touched, as on WP8.1 and in due north tasks. That touch only closes the bar: the press, scroll
 * or tap is swallowed and never reaches what lies underneath. Bars outside a host still close
 * from the ellipsis, from back, and from running one of their actions.
 */
@Composable
fun MetroAppBarHost(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val host = remember { AppBarHostState() }
    Box(
        modifier
            .onGloballyPositioned { host.coordinates = it }
            .pointerInput(host) {
                awaitEachGesture {
                    // Initial pass: the host sees the touch before anything inside it does.
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val bar = host.expanded ?: return@awaitEachGesture
                    val at =
                        host.coordinates?.takeIf { it.isAttached }?.localToWindow(down.position)
                            ?: return@awaitEachGesture
                    if (bar.boundsInWindow.contains(at)) return@awaitEachGesture
                    bar.collapse()
                    down.consume()
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            },
        propagateMinConstraints = true,
    ) {
        CompositionLocalProvider(LocalAppBarHost provides host, content = content)
    }
}
