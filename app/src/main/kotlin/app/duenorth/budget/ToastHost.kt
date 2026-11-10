package app.duenorth.budget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import app.duenorth.budget.design.theme.MetroStyle
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Draws [toast] in the look of the chosen style, each after its own platform's toast:
 *
 * - Metro: the Windows Phone 8.1 toast, a square accent strip across the top behind the status bar.
 * - Tile relief: that strip as a raised accent tile just under the status bar.
 * - Mica: a Windows 11 acrylic card washed with the accent (red on failure) and edged in it, above the app bar.
 * - Windows 10 Mobile: the system banner, chrome grey from the top with the app's tile beside the words.
 *
 * A failure is red in every style. The host sits at the app root, over every page, so a toast posted just before a
 * save leaves its page stays up on the page it lands on. A success times out; a failure stays until it is tapped or
 * flicked aside.
 */
@Composable
fun ToastHost(
    toast: Toast?,
    onDismiss: (Long) -> Unit,
    onAction: (Toast) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Holds the last toast while it animates out.
    var shown by remember { mutableStateOf(toast) }
    if (toast != null) shown = toast
    LaunchedEffect(toast?.id) {
        val current = toast ?: return@LaunchedEffect
        val millis = ToastTiming.shownFor(current) ?: return@LaunchedEffect
        delay(millis)
        onDismiss(current.id)
    }
    val style = Metro.style
    val fromBottom = style.layers
    val animate = Metro.animations
    AnimatedVisibility(
        visible = toast != null,
        modifier = modifier,
        enter =
            if (animate) {
                slideInVertically(tween(220)) { if (fromBottom) it / 2 else -it } + fadeIn(tween(160))
            } else {
                EnterTransition.None
            },
        exit =
            if (animate) {
                slideOutVertically(tween(180)) { if (fromBottom) it / 2 else -it } + fadeOut(tween(180))
            } else {
                ExitTransition.None
            },
    ) {
        val current = shown ?: return@AnimatedVisibility
        Swipeable(onGone = { onDismiss(current.id) }) { swipe ->
            val body =
                swipe
                    .testTag("toast")
                    .semantics(mergeDescendants = false) {
                        liveRegion = if (current.kind == ToastKind.Failure) LiveRegionMode.Assertive else LiveRegionMode.Polite
                        contentDescription = listOfNotNull(current.title, current.message).joinToString(". ")
                    }
            val dismiss = { onDismiss(current.id) }
            val act = { onAction(current) }
            when (style) {
                MetroStyle.Metro -> MetroToast(current, dismiss, act, body)
                MetroStyle.TileRelief -> TileToast(current, dismiss, act, body)
                MetroStyle.Mica -> MicaToast(current, dismiss, act, body)
                MetroStyle.Mobile10 -> Mobile10Toast(current, dismiss, act, body)
            }
        }
    }
}

/** A flick sideways past a third of the width dismisses, as a Windows Phone toast is flicked away. */
@Composable
private fun Swipeable(
    onGone: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    var dx by remember { mutableFloatStateOf(0f) }
    val threshold = with(LocalDensity.current) { 96.dp.toPx() }
    content(
        Modifier
            .graphicsLayer {
                translationX = dx
                alpha = 1f - (abs(dx) / (threshold * 2)).coerceIn(0f, 0.6f)
            }.pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (abs(dx) > threshold) onGone()
                        dx = 0f
                    },
                    onDragCancel = { dx = 0f },
                ) { change, amount ->
                    change.consume()
                    dx += amount
                }
            },
    )
}

private val ToastKind.failed get() = this == ToastKind.Failure

@Composable
private fun fill(kind: ToastKind): Color = if (kind.failed) Metro.colors.errorFill else Metro.accent.fill

@Composable
private fun onFill(kind: ToastKind): Color = if (kind.failed) Metro.colors.onErrorFill else Metro.accent.onFill

@Composable
private fun MetroToast(
    toast: Toast,
    onDismiss: () -> Unit,
    onAction: () -> Unit,
    modifier: Modifier,
) {
    val ink = onFill(toast.kind)
    Row(
        modifier
            .fillMaxWidth()
            .background(fill(toast.kind))
            .windowInsetsPadding(WindowInsets.statusBars)
            .metroPress(onDismiss)
            .padding(horizontal = MetroDimens.Gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter),
    ) {
        Box(Modifier.size(24.dp).border(1.5.dp, ink), contentAlignment = Alignment.Center) { StatusGlyph(toast.kind, ink) }
        Column(Modifier.weight(1f)) {
            MetroText(toast.title, Metro.typography.body.copy(fontWeight = FontWeight.SemiBold), color = ink)
            toast.message?.let { MetroText(it, Metro.typography.caption, color = ink) }
        }
        ActionWord(toast, ink, onAction)
    }
}

@Composable
private fun TileToast(
    toast: Toast,
    onDismiss: () -> Unit,
    onAction: () -> Unit,
    modifier: Modifier,
) {
    val ink = onFill(toast.kind)
    Row(
        modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = MetroDimens.Gutter, vertical = 8.dp)
            .fillMaxWidth()
            .shadow(8.dp, RectangleShape)
            .background(fill(toast.kind))
            .border(1.dp, Color.White.copy(alpha = 0.18f))
            .metroPress(onDismiss)
            .padding(horizontal = MetroDimens.Gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter),
    ) {
        Box(Modifier.size(28.dp).background(Color.Black.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            StatusGlyph(toast.kind, ink)
        }
        Column(Modifier.weight(1f)) {
            MetroText(toast.title, Metro.typography.body.copy(fontWeight = FontWeight.SemiBold), color = ink)
            toast.message?.let { MetroText(it, Metro.typography.caption, color = ink) }
        }
        if (toast.action != null) {
            Box(
                Modifier
                    .shadow(3.dp, RectangleShape)
                    .background(Color.White.copy(alpha = 0.22f))
                    .then(actionModifier(onAction))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) { MetroText(ToastCopy.UNDO, Metro.typography.caption.copy(fontWeight = FontWeight.SemiBold), color = ink) }
        }
    }
}

/**
 * Mica's toast colors, picked on 2026-11-09 from four mockups (an accent wash with a colored edge): the acrylic card
 * washed with the accent, or with Windows 11's error red on failure, and edged on the left in that same color.
 */
private object MicaToastColors {
    /** How much of the hue washes the card: a little more in dark, where a faint wash disappears. */
    private const val WASH_LIGHT = 0.09f
    private const val WASH_DARK = 0.16f

    /** The toast's own color: the accent for done, Windows 11's error red for failed. */
    @Composable
    fun hue(failed: Boolean): Color {
        val dark = Metro.colors.isDark
        return when {
            failed && dark -> Color(0xFFFF99A4)
            failed -> Color(0xFFC42B1C)
            dark -> Metro.accent.text
            else -> Metro.accent.fill
        }
    }

    @Composable
    fun card(failed: Boolean): Color {
        val base = Metro.surface.acrylic.copy(alpha = 1f)
        return lerp(base, hue(failed), if (Metro.colors.isDark) WASH_DARK else WASH_LIGHT)
    }
}

@Composable
private fun MicaToast(
    toast: Toast,
    onDismiss: () -> Unit,
    onAction: () -> Unit,
    modifier: Modifier,
) {
    val dark = Metro.colors.isDark
    val failed = toast.kind.failed
    val shape = RoundedCornerShape(8.dp)
    val hue = MicaToastColors.hue(failed)
    Row(
        modifier
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .fillMaxWidth()
            .shadow(12.dp, shape)
            .clip(shape)
            .background(MicaToastColors.card(failed))
            // The colored edge down the left, cut to the card's rounded corners.
            .drawBehind { drawRect(hue, size = Size(4.dp.toPx(), size.height)) }
            .border(1.dp, Metro.surface.stroke, shape)
            .padding(start = MetroDimens.Gutter + 4.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter),
    ) {
        Box(
            Modifier.padding(top = 2.dp).size(20.dp).background(hue, CircleShape),
            contentAlignment = Alignment.Center,
        ) { StatusGlyph(toast.kind, if (dark) Color.Black else Color.White, size = 12, round = true) }
        Column(Modifier.weight(1f)) {
            MetroText(toast.title, Metro.typography.body.copy(fontWeight = FontWeight.SemiBold))
            toast.message?.let { MetroText(it, Metro.typography.caption, color = Metro.colors.secondary) }
            if (toast.action != null) {
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .background(Metro.accent.fill, RoundedCornerShape(4.dp))
                        .then(actionModifier(onAction))
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                ) { MetroText(ToastCopy.UNDO, Metro.typography.caption, color = Metro.accent.onFill) }
            }
        }
        Box(
            Modifier
                .size(MetroDimens.TouchTarget)
                .metroPress(onDismiss)
                .semantics {
                    role = Role.Button
                    contentDescription = ToastCopy.DISMISS
                },
            contentAlignment = Alignment.TopCenter,
        ) { CloseGlyph(Metro.colors.secondary, Modifier.padding(top = 4.dp)) }
    }
}

@Composable
private fun Mobile10Toast(
    toast: Toast,
    onDismiss: () -> Unit,
    onAction: () -> Unit,
    modifier: Modifier,
) {
    val banner = if (Metro.colors.isDark) Color(0xFF2B2B2B) else Color(0xFFE6E6E6)
    // One line, as Metro's: tile, words, then a compact grey button at the end rather than a full-width one below.
    Row(
        modifier
            .fillMaxWidth()
            .shadow(8.dp, RectangleShape)
            .background(banner)
            .windowInsetsPadding(WindowInsets.statusBars)
            .metroPress(onDismiss)
            .padding(horizontal = MetroDimens.Gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter),
    ) {
        Box(Modifier.size(32.dp).background(fill(toast.kind)), contentAlignment = Alignment.Center) {
            StatusGlyph(toast.kind, onFill(toast.kind))
        }
        Column(Modifier.weight(1f)) {
            MetroText(toast.title, Metro.typography.body.copy(fontWeight = FontWeight.SemiBold))
            toast.message?.let { MetroText(it, Metro.typography.caption, color = Metro.colors.secondary) }
        }
        if (toast.action != null) {
            Box(
                Modifier
                    .background(Metro.colors.foreground.copy(alpha = 0.2f))
                    .then(actionModifier(onAction))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) { MetroText(ToastCopy.UNDO, Metro.typography.caption.copy(fontWeight = FontWeight.Normal)) }
        }
    }
}

/** Metro's action: a plain lowercase word, as on an app bar's menu. */
@Composable
private fun ActionWord(
    toast: Toast,
    ink: Color,
    onAction: () -> Unit,
) {
    if (toast.action == null) return
    Box(
        Modifier
            .then(actionModifier(onAction))
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) { MetroText(ToastCopy.UNDO, Metro.typography.body.copy(fontWeight = FontWeight.SemiBold), color = ink) }
}

@Composable
private fun actionModifier(onAction: () -> Unit): Modifier =
    Modifier
        .testTag("toast-action")
        .metroPress(onAction)
        .semantics { role = Role.Button }

/** A tick for done, an exclamation mark for failed (a cross inside the round badge of mica). */
@Composable
private fun StatusGlyph(
    kind: ToastKind,
    color: Color,
    size: Int = 14,
    round: Boolean = false,
) {
    Canvas(Modifier.size(size.dp)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = 2.dp.toPx()
        when {
            !kind.failed -> {
                drawLine(color, Offset(w * 0.12f, h * 0.55f), Offset(w * 0.4f, h * 0.82f), stroke, StrokeCap.Square)
                drawLine(color, Offset(w * 0.4f, h * 0.82f), Offset(w * 0.9f, h * 0.2f), stroke, StrokeCap.Square)
            }
            round -> {
                drawLine(color, Offset(w * 0.22f, h * 0.22f), Offset(w * 0.78f, h * 0.78f), stroke, StrokeCap.Square)
                drawLine(color, Offset(w * 0.78f, h * 0.22f), Offset(w * 0.22f, h * 0.78f), stroke, StrokeCap.Square)
            }
            else -> {
                drawLine(color, Offset(w / 2, h * 0.08f), Offset(w / 2, h * 0.62f), stroke, StrokeCap.Square)
                drawCircle(color, stroke * 0.7f, Offset(w / 2, h * 0.88f))
            }
        }
    }
}

@Composable
private fun CloseGlyph(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(12.dp)) {
        val s = size.width
        val stroke = Stroke(1.4.dp.toPx())
        drawLine(color, Offset(0f, 0f), Offset(s, s), stroke.width)
        drawLine(color, Offset(s, 0f), Offset(0f, s), stroke.width)
    }
}
