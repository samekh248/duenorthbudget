package app.duenorth.budget.design.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.duenorth.budget.design.MetroMotion
import app.duenorth.budget.design.metroTilt
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import app.duenorth.budget.design.theme.Relief

@Immutable
data class ScreenAction(
    val id: String,
    val glyph: AppGlyph,
    val label: String,
    val primary: Boolean = true,
    /** A sync button whose sync is running: its glyph turns until it ends. */
    val spinning: Boolean = false,
    /** A destructive button, set apart from the rest of the bar by a rule and extra air. */
    val apart: Boolean = false,
    val run: () -> Unit,
)

@Immutable
data class AppBarButton(
    val glyph: AppGlyph,
    val label: String,
    val onClick: () -> Unit,
    val id: String = label,
)

/**
 * The WP8.1 Application Bar, as in due north tasks: round outlined buttons centered along the
 * bottom and an ellipsis near the top right that expands the bar to show their labels and the
 * menu. Running any action closes an expanded bar, and inside a [MetroAppBarHost] so does
 * touching anything outside it.
 */
@Composable
fun MetroAppBar(
    actions: List<ScreenAction>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** The bar's chrome runs down behind these, and its buttons stay above them. */
    windowInsets: WindowInsets = WindowInsets.navigationBars,
) {
    // A page with no buttons and no menu shows no bar at all, only keeping its content clear of
    // the system navigation bar.
    if (actions.isEmpty()) {
        Spacer(modifier.fillMaxWidth().windowInsetsBottomHeight(windowInsets).testTag("app-bar-none"))
        return
    }
    val colors = Metro.colors
    val primary = actions.filter { it.primary }
    val secondary = actions.filter { !it.primary }
    val collapse by rememberUpdatedState { onExpandedChange(false) }
    val host = LocalAppBarHost.current
    val registration = remember { ExpandedAppBar(collapse = { collapse() }) }
    if (host != null) {
        DisposableEffect(host, expanded) {
            if (expanded) host.expanded = registration
            onDispose { if (host.expanded === registration) host.expanded = null }
        }
    }
    val style = Metro.style
    if (style.mobile10) {
        Mobile10CommandBar(primary, secondary, expanded, onExpandedChange, modifier, windowInsets, host, registration)
        return
    }
    val chrome =
        if (style.layers) {
            // Mica: an acrylic sheet floating a gutter in from the screen's edges, over the page's content.
            Modifier
                .background(colors.background)
                .windowInsetsPadding(windowInsets)
                .padding(start = MetroDimens.Gutter, end = MetroDimens.Gutter, bottom = MetroDimens.Gutter)
                .acrylicSheet(Metro.surface)
        } else {
            Modifier.background(colors.chrome).windowInsetsPadding(windowInsets)
        }
    Column(
        modifier
            .fillMaxWidth()
            .then(
                if (host != null) {
                    Modifier.onGloballyPositioned { registration.boundsInWindow = it.boundsInWindow() }
                } else {
                    Modifier
                },
            ).then(chrome)
            .animateContentSize(tween(MetroMotion.SHORT_MS, easing = MetroMotion.EaseOut)),
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = MetroDimens.AppBar)) {
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(MetroDimens.AppBarGap),
            ) {
                primary.forEach { action ->
                    AppBarIcon(action, expanded, rule = action.apart && action != primary.first()) {
                        onExpandedChange(false)
                        action.run()
                    }
                }
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(width = 56.dp, height = MetroDimens.TouchTarget)
                    .testTag("app-bar-ellipsis")
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        role = Role.Button,
                        onClick = { onExpandedChange(!expanded) },
                    ).semantics { contentDescription = if (expanded) "less" else "more" }
                    // A full 48dp target, with the dots where WP8.1 draws them near the top edge.
                    .padding(top = 6.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                EllipsisDots(colors.foreground)
            }
        }
        if (expanded && secondary.isNotEmpty()) {
            Column(Modifier.padding(start = MetroDimens.Gutter * 2, bottom = MetroDimens.Gutter)) {
                secondary.forEach { action ->
                    MetroText(
                        action.label,
                        Metro.typography.subheader,
                        Modifier
                            .fillMaxWidth()
                            .metroTilt()
                            .testTag("app-bar-menu-${action.id}")
                            .clickable(interactionSource = null, indication = null, role = Role.Button) {
                                onExpandedChange(false)
                                action.run()
                            }.heightIn(min = MetroDimens.TouchTarget)
                            .padding(vertical = 10.dp),
                    )
                }
            }
        }
    }
}

/**
 * The Windows 10 Mobile command bar: bare glyphs right-aligned on a flat 48dp bar, the ellipsis last. Opened, it
 * shows each glyph's label under it and the secondary commands below as a plain list.
 */
@Composable
private fun Mobile10CommandBar(
    primary: List<ScreenAction>,
    secondary: List<ScreenAction>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier,
    windowInsets: WindowInsets,
    host: AppBarHostState?,
    registration: ExpandedAppBar,
) {
    val colors = Metro.colors
    Column(
        modifier
            .fillMaxWidth()
            .then(
                if (host != null) {
                    Modifier.onGloballyPositioned { registration.boundsInWindow = it.boundsInWindow() }
                } else {
                    Modifier
                },
            ).background(colors.chrome)
            .windowInsetsPadding(windowInsets)
            .animateContentSize(tween(MetroMotion.SHORT_MS, easing = MetroMotion.EaseOut)),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = Mobile10Dimens.CommandBar), horizontalArrangement = Arrangement.End) {
            primary.forEach { action ->
                Column(
                    Modifier
                        .width(Mobile10Dimens.CommandSlot)
                        .then(
                            if (action.apart && action != primary.first()) {
                                val rule = colors.secondary
                                Modifier.testTag("app-bar-rule-${action.id}").drawBehind {
                                    val inset = 12.dp.toPx()
                                    drawLine(
                                        rule,
                                        Offset(0f, inset),
                                        Offset(0f, Mobile10Dimens.CommandBar.toPx() - inset),
                                        strokeWidth = 1.dp.toPx(),
                                    )
                                }
                            } else {
                                Modifier
                            },
                        ).testTag("app-bar-${action.id}")
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            onClickLabel = action.label,
                            role = Role.Button,
                        ) {
                            onExpandedChange(false)
                            action.run()
                        }.semantics {
                            contentDescription = action.label
                            if (action.spinning) stateDescription = "syncing"
                        }.padding(top = 14.dp, bottom = if (expanded) 8.dp else 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppGlyphIcon(action.glyph, colors.foreground, Modifier.syncSpin(action.spinning))
                    if (expanded) {
                        MetroText(
                            action.label,
                            Metro.typography.caption,
                            Modifier.wrapContentWidth(unbounded = true).padding(top = 4.dp),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
            Box(
                Modifier
                    .size(width = MetroDimens.TouchTarget, height = Mobile10Dimens.CommandBar)
                    .testTag("app-bar-ellipsis")
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        role = Role.Button,
                        onClick = { onExpandedChange(!expanded) },
                    ).semantics { contentDescription = if (expanded) "less" else "more" }
                    .padding(top = 4.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                EllipsisDots(colors.foreground)
            }
        }
        if (expanded && secondary.isNotEmpty()) {
            Column(Modifier.padding(bottom = 8.dp)) {
                secondary.forEach { action ->
                    MetroText(
                        action.label,
                        Metro.typography.body,
                        Modifier
                            .fillMaxWidth()
                            .testTag("app-bar-menu-${action.id}")
                            .clickable(interactionSource = null, indication = null, role = Role.Button) {
                                onExpandedChange(false)
                                action.run()
                            }.heightIn(min = MetroDimens.TouchTarget)
                            .padding(horizontal = MetroDimens.Gutter, vertical = 13.dp),
                    )
                }
            }
        }
    }
}

/** The three small dots of the WP8.1 ellipsis. */
@Composable
private fun EllipsisDots(color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val u = size.minDimension / 24f
        for (x in listOf(6f, 12f, 18f)) drawCircle(color, radius = 1.4f * u, center = Offset(x * u, 12f * u))
    }
}

@Composable
private fun AppBarIcon(
    action: ScreenAction,
    showLabel: Boolean,
    /** Draws a thin upright rule in the gap before the button, setting it apart; the bar keeps its width. */
    rule: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = Metro.colors
    val ruleColor = colors.secondary
    Column(
        Modifier
            .width(MetroDimens.AppBarSlot)
            .then(
                if (rule) {
                    Modifier.testTag("app-bar-rule-${action.id}").drawBehind {
                        val x = -MetroDimens.AppBarGap.toPx() / 2
                        drawLine(ruleColor, Offset(x, 0f), Offset(x, MetroDimens.AppBarIcon.toPx()), strokeWidth = 1.dp.toPx())
                    }
                } else {
                    Modifier
                },
            ).metroTilt()
            .testTag("app-bar-${action.id}")
            .clickable(
                interactionSource = null,
                indication = null,
                onClickLabel = action.label,
                role = Role.Button,
                onClick = onClick,
            ).semantics {
                contentDescription = action.label
                if (action.spinning) stateDescription = "syncing"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val style = Metro.style
        // Tile relief fills add with the accent, so the day's main action leads the bar.
        val filled = style.filledAdd && action.glyph == AppGlyph.Add
        // The button's own face is the ground it stands on: the bar's chrome.
        val ground = colors.chrome
        val accent = Metro.accent
        Box(
            Modifier
                .size(MetroDimens.AppBarIcon)
                .then(
                    when {
                        filled ->
                            Modifier.relief(Relief.Raised, small = true, corner = PillCorner, fill = accent.fill)
                        // Mica's other buttons are bare glyphs on the acrylic.
                        style.layers -> Modifier
                        style.relief -> Modifier.relief(Relief.Raised, small = true, corner = PillCorner, fill = ground)
                        else -> Modifier.border(MetroDimens.AppBarStroke, colors.foreground, CircleShape)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            AppGlyphIcon(action.glyph, if (filled) accent.onFill else colors.foreground, Modifier.syncSpin(action.spinning))
        }
        if (showLabel) {
            MetroText(
                action.label,
                Metro.typography.caption,
                // Longer labels ("categories") may run a little past the slot, never wrap.
                Modifier.wrapContentWidth(unbounded = true).padding(top = 4.dp, bottom = 6.dp),
                color = colors.foreground,
                maxLines = 1,
                softWrap = false,
            )
        } else {
            Box(Modifier.height(4.dp))
        }
    }
}

/**
 * As in due north tasks: a steady turn while [spinning], drawn in the layer only so nothing
 * recomposes per frame. When it stops, the glyph eases on to upright rather than snapping back.
 * Still with animations off.
 */
@Composable
fun Modifier.syncSpin(spinning: Boolean): Modifier {
    if (!Metro.animations) return this
    // The angle the spin had reached, read when it stops; written from the draw phase.
    val reached = remember { floatArrayOf(0f) }
    val settle = remember { Animatable(0f) }
    var settling by remember { mutableStateOf(false) }
    LaunchedEffect(spinning) {
        if (spinning || reached[0] == 0f) return@LaunchedEffect
        settling = true
        settle.snapTo(reached[0])
        val left = (FULL_TURN - reached[0]) / FULL_TURN
        settle.animateTo(FULL_TURN, tween((SPIN_MS * left).toInt(), easing = MetroMotion.EaseOut))
        reached[0] = 0f
        settling = false
    }
    return when {
        spinning -> {
            val angle =
                rememberInfiniteTransition(label = "sync spin").animateFloat(
                    initialValue = 0f,
                    targetValue = FULL_TURN,
                    animationSpec = infiniteRepeatable(tween(SPIN_MS, easing = LinearEasing), RepeatMode.Restart),
                    label = "angle",
                )
            graphicsLayer {
                rotationZ = angle.value
                reached[0] = angle.value
            }
        }
        settling -> graphicsLayer { rotationZ = settle.value }
        else -> this
    }
}

private const val FULL_TURN = 360f
internal const val SYNC_TOP_END = 335f
internal const val SYNC_SWEEP = 130f
internal const val SYNC_HEAD_LENGTH = 3.6f
internal const val SYNC_HEAD_HALF = 3f
private const val SPIN_MS = 1200

fun List<AppBarButton>.asScreenActions(): List<ScreenAction> =
    map { ScreenAction(id = it.id, glyph = it.glyph, label = it.label, primary = true, run = it.onClick) }
