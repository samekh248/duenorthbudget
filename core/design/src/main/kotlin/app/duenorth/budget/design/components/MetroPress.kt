package app.duenorth.budget.design.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Dp
import app.duenorth.budget.design.theme.Metro

@Composable
fun Modifier.metroPress(onClick: () -> Unit): Modifier = metroPress(onClick, onLongClick = null)

/**
 * [metroPress] that also answers a long press, as list items do to start select mode. The pressed item sinks
 * in a little under a soft band, staying level, as Windows 10 Mobile's lists do. A [bleed] holds the band still
 * at the item's full size and runs it that far past each side, for an item whose surface runs past the gutter
 * (the inbox's featured panel), so the band covers the whole surface while only the content sinks.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.metroPress(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    bleed: Dp? = null,
): Modifier {
    val animations = Metro.animations
    val pressedColor = if (Metro.colors.isDark) Color(0x29FFFFFF) else Color(0x1F000000)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val sink by animateFloatAsState(
        if (pressed && animations) PRESSED_SINK else 1f,
        if (animations) tween(PRESS_MS) else snap(),
        label = "press",
    )
    return this
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            role = Role.Button,
            onLongClick = onLongClick,
            onClick = onClick,
        ).then(
            if (bleed == null) {
                Modifier
            } else {
                Modifier.drawBehind {
                    if (!pressed) return@drawBehind
                    val side = bleed.toPx()
                    drawRect(pressedColor, Offset(-side, 0f), Size(size.width + 2 * side, size.height))
                }
            },
        ).graphicsLayer {
            scaleX = sink
            scaleY = sink
        }.background(if (pressed && bleed == null) pressedColor else Color.Transparent)
}

/** How far a pressed item sinks: 3% smaller, so a full-width row pulls in from both screen edges. */
private const val PRESSED_SINK = 0.97f
private const val PRESS_MS = 100
