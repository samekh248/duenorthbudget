package app.duenorth.budget.design.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.duenorth.budget.design.PivotMotion
import app.duenorth.budget.design.metroTilt
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import app.duenorth.budget.design.theme.MetroWeights
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun MetroText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Metro.colors.foreground,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color),
        maxLines = maxLines,
        softWrap = softWrap,
        overflow = if (softWrap) TextOverflow.Clip else TextOverflow.Ellipsis,
    )
}

@Composable
fun Modifier.metroPress(onClick: () -> Unit): Modifier = metroPress(onClick, onLongClick = null)

/** [metroPress] that also answers a long press, as list items do to start select mode. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.metroPress(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
): Modifier {
    val animations = Metro.animations
    val pressedColor = if (Metro.colors.isDark) Color(0x73FFFFFF) else Color(0x40000000)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    return this
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            role = Role.Button,
            onLongClick = onLongClick,
            onClick = onClick,
        ).graphicsLayer {
            rotationZ = if (pressed && animations) 2.5f else 0f
        }.background(if (pressed) pressedColor else Color.Transparent)
}

@Composable
fun MetroField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    conceal: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val colors = Metro.colors
    val field = if (colors.isDark) Color.White else Color(0xFFBFBFBF)
    val ink = Color.Black
    val hintColor = if (colors.isDark) Color(0xFF909090) else Color(0xFF8F8F8F)
    Box(
        modifier
            .fillMaxWidth()
            .background(field)
            .heightIn(min = MetroDimens.TouchTarget)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            MetroText(hint, Metro.typography.control, color = hintColor)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = Metro.typography.control.copy(color = ink),
            cursorBrush = SolidColor(Metro.accent.fill),
            visualTransformation = if (conceal) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = keyboardOptions,
            singleLine = keyboardOptions != KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * A body button, laid out the Windows Phone way: every button is one cell of a two-column
 * grid, so a lone button takes the left half of the row and pairs sit side by side at equal
 * width. Group neighbouring buttons in [MetroButtonGrid]; commands for the whole page belong
 * on the app bar instead.
 */
@Composable
fun MetroButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val cell = if (LocalButtonCell.current) Modifier.fillMaxWidth() else Modifier.buttonCell()
    MetroActionButton(label, modifier.then(cell), onClick)
}

/** Same button as [MetroButton]; kept for the screens that named the outlined style. */
@Composable
fun MetroOutlineButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    MetroButton(label, modifier, onClick)
}

/**
 * Buttons that belong together, two to a row at equal width, rows a gutter apart. An odd
 * last button keeps the left cell, so a page's buttons always line up on the same grid.
 */
@Composable
fun MetroButtonGrid(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val gutter = with(LocalDensity.current) { MetroDimens.Gutter.roundToPx() }
    Layout(
        content = { CompositionLocalProvider(LocalButtonCell provides true, content = content) },
        modifier = modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val cell = ((constraints.maxWidth - gutter) / 2).coerceAtLeast(0)
        val placeables = measurables.map { it.measure(Constraints.fixedWidth(cell)) }
        val rows = placeables.chunked(2)
        val heights = rows.map { row -> row.maxOf { it.height } }
        val height = heights.sum() + gutter * (rows.size - 1).coerceAtLeast(0)
        layout(constraints.maxWidth, height) {
            var y = 0
            rows.forEachIndexed { index, row ->
                row.forEachIndexed { column, placeable -> placeable.place(column * (cell + gutter), y) }
                y += heights[index] + gutter
            }
        }
    }
}

private val LocalButtonCell = staticCompositionLocalOf { false }

/** Half the row less half a gutter: the width of one [MetroButtonGrid] cell. A long label may grow it. */
private fun Modifier.buttonCell(): Modifier =
    layout { measurable, constraints ->
        if (!constraints.hasBoundedWidth) {
            val placeable = measurable.measure(constraints)
            return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
        val cell = ((constraints.maxWidth - MetroDimens.Gutter.roundToPx()) / 2).coerceIn(constraints.minWidth, constraints.maxWidth)
        val placeable = measurable.measure(constraints.copy(minWidth = cell))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

@Composable
private fun MetroActionButton(
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = Metro.colors
    val accent = Metro.accent
    val animations = Metro.animations
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .heightIn(min = MetroDimens.ButtonMin)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ).graphicsLayer { rotationZ = if (pressed && animations) 2.5f else 0f }
            .border(MetroDimens.Stroke, colors.foreground)
            .background(if (pressed) accent.fill else Color.Transparent)
            .padding(horizontal = 9.5.dp, vertical = 3.5.dp),
        contentAlignment = Alignment.Center,
    ) {
        MetroText(
            label,
            Metro.typography.body.copy(fontWeight = MetroWeights.Semibold),
            color = if (pressed) Color.White else colors.foreground,
        )
    }
}

enum class AppGlyph {
    Budgets,
    Appearance,
    Sync,
    Payees,
    Hold,
    Categories,
    Add,
    Settings,
    Transfer,
    Reconcile,
    Import,
    Save,
    Cancel,
    ChevronLeft,
    ChevronRight,
    ChevronDown,
    Calendar,
    Breakdown,
    Copy,
    Average,
    Zero,
    Undo,
}

@Immutable
data class ScreenAction(
    val id: String,
    val glyph: AppGlyph,
    val label: String,
    val primary: Boolean = true,
    val run: () -> Unit,
)

@Immutable
data class AppBarButton(
    val glyph: AppGlyph,
    val label: String,
    val onClick: () -> Unit,
    val id: String = label,
)

private const val EXPAND_MS = 200

/** Metro's ease out, used for the bar opening and closing. */
private val AppBarEasing = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1f)

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
            .animateContentSize(tween(EXPAND_MS, easing = AppBarEasing)),
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = MetroDimens.AppBar)) {
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(MetroDimens.AppBarGap),
            ) {
                primary.forEach { action ->
                    AppBarIcon(action, expanded) {
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
    onClick: () -> Unit,
) {
    val colors = Metro.colors
    Column(
        Modifier
            .width(MetroDimens.AppBarSlot)
            .metroTilt()
            .testTag("app-bar-${action.id}")
            .clickable(
                interactionSource = null,
                indication = null,
                onClickLabel = action.label,
                role = Role.Button,
                onClick = onClick,
            ).semantics { contentDescription = action.label },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(MetroDimens.AppBarIcon)
                .border(MetroDimens.AppBarStroke, colors.foreground, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AppGlyphIcon(action.glyph)
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

fun List<AppBarButton>.asScreenActions(): List<ScreenAction> =
    map { ScreenAction(id = it.id, glyph = it.glyph, label = it.label, primary = true, run = it.onClick) }

object AppGlyphInk {
    fun strokeCount(glyph: AppGlyph): Int =
        when (glyph) {
            AppGlyph.Budgets -> 3
            AppGlyph.Appearance -> 3
            AppGlyph.Sync -> 6
            AppGlyph.Payees -> 2
            AppGlyph.Hold -> 2
            AppGlyph.Categories -> 3
            AppGlyph.Add -> 2
            AppGlyph.Settings -> 2
            AppGlyph.Transfer -> 4
            AppGlyph.Reconcile -> 2
            AppGlyph.Import -> 3
            AppGlyph.Save -> 2
            AppGlyph.Cancel -> 2
            AppGlyph.ChevronLeft -> 2
            AppGlyph.ChevronRight -> 2
            AppGlyph.ChevronDown -> 2
            AppGlyph.Calendar -> 3
            AppGlyph.Breakdown -> 4
            AppGlyph.Copy -> 2
            AppGlyph.Average -> 3
            AppGlyph.Zero -> 1
            AppGlyph.Undo -> 3
        }

    fun DrawScope.draw(
        glyph: AppGlyph,
        color: Color,
    ) {
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Square)
        val w = size.width
        val h = size.height
        when (glyph) {
            AppGlyph.Budgets -> {
                drawRect(
                    color,
                    topLeft = Offset(3.dp.toPx(), 6.dp.toPx()),
                    size =
                        androidx.compose.ui.geometry
                            .Size(16.dp.toPx(), 13.dp.toPx()),
                    style = stroke,
                )
                drawLine(color, Offset(3.dp.toPx(), 6.dp.toPx()), Offset(8.dp.toPx(), 3.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(8.dp.toPx(), 3.dp.toPx()), Offset(14.dp.toPx(), 6.dp.toPx()), strokeWidth = 1.6.dp.toPx())
            }
            AppGlyph.Appearance -> {
                drawCircle(color, radius = 5.dp.toPx(), style = stroke)
                drawLine(color, Offset(w / 2, 1.dp.toPx()), Offset(w / 2, 4.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(w / 2, h - 1.dp.toPx()), Offset(w / 2, h - 4.dp.toPx()), strokeWidth = 1.6.dp.toPx())
            }
            // Two arrows chasing each other round a circle, the WP8.1 sync mark (distinct from transfer's straight arrows).
            AppGlyph.Sync -> {
                val radius = 6.5.dp.toPx()
                val center = Offset(w / 2, h / 2)
                val head = 3.dp.toPx()
                listOf(200f, 20f).forEach { start ->
                    drawArc(
                        color,
                        startAngle = start,
                        sweepAngle = 130f,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size =
                            androidx.compose.ui.geometry
                                .Size(radius * 2, radius * 2),
                        style = stroke,
                    )
                    val end = Math.toRadians((start + 130f).toDouble())
                    val tip = Offset(center.x + radius * kotlin.math.cos(end).toFloat(), center.y + radius * kotlin.math.sin(end).toFloat())
                    val heading = kotlin.math.atan2(kotlin.math.cos(end), -kotlin.math.sin(end))
                    listOf(150.0, -150.0).forEach { spread ->
                        val back = heading + Math.toRadians(spread)
                        drawLine(
                            color,
                            tip,
                            Offset(tip.x + head * kotlin.math.cos(back).toFloat(), tip.y + head * kotlin.math.sin(back).toFloat()),
                            strokeWidth = 1.6.dp.toPx(),
                        )
                    }
                }
            }
            AppGlyph.Payees -> {
                drawCircle(color, radius = 3.5.dp.toPx(), center = Offset(w / 2, 7.dp.toPx()), style = stroke)
                drawLine(color, Offset(5.dp.toPx(), 18.dp.toPx()), Offset(17.dp.toPx(), 18.dp.toPx()), strokeWidth = 1.6.dp.toPx())
            }
            AppGlyph.Hold -> {
                drawLine(color, Offset(7.dp.toPx(), 5.dp.toPx()), Offset(7.dp.toPx(), 17.dp.toPx()), strokeWidth = 2.2.dp.toPx())
                drawLine(color, Offset(15.dp.toPx(), 5.dp.toPx()), Offset(15.dp.toPx(), 17.dp.toPx()), strokeWidth = 2.2.dp.toPx())
            }
            AppGlyph.Categories -> {
                drawRect(
                    color,
                    topLeft = Offset(4.dp.toPx(), 5.dp.toPx()),
                    size =
                        androidx.compose.ui.geometry
                            .Size(14.dp.toPx(), 12.dp.toPx()),
                    style = stroke,
                )
                drawLine(color, Offset(4.dp.toPx(), 9.dp.toPx()), Offset(18.dp.toPx(), 9.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(4.dp.toPx(), 13.dp.toPx()), Offset(18.dp.toPx(), 13.dp.toPx()), strokeWidth = 1.6.dp.toPx())
            }
            AppGlyph.Add -> {
                drawLine(color, Offset(w / 2, 4.dp.toPx()), Offset(w / 2, 18.dp.toPx()), strokeWidth = 1.8.dp.toPx())
                drawLine(color, Offset(4.dp.toPx(), h / 2), Offset(18.dp.toPx(), h / 2), strokeWidth = 1.8.dp.toPx())
            }
            AppGlyph.Settings -> {
                drawCircle(color, radius = 3.dp.toPx(), style = stroke)
                drawCircle(color, radius = 8.dp.toPx(), style = stroke)
            }
            AppGlyph.Transfer -> {
                drawLine(color, Offset(4.dp.toPx(), 8.dp.toPx()), Offset(16.dp.toPx(), 8.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(13.dp.toPx(), 5.dp.toPx()), Offset(17.dp.toPx(), 8.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(18.dp.toPx(), 14.dp.toPx()), Offset(6.dp.toPx(), 14.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(9.dp.toPx(), 17.dp.toPx()), Offset(5.dp.toPx(), 14.dp.toPx()), strokeWidth = 1.6.dp.toPx())
            }
            AppGlyph.Reconcile -> {
                drawLine(color, Offset(5.dp.toPx(), 11.dp.toPx()), Offset(9.dp.toPx(), 16.dp.toPx()), strokeWidth = 1.8.dp.toPx())
                drawLine(color, Offset(9.dp.toPx(), 16.dp.toPx()), Offset(17.dp.toPx(), 6.dp.toPx()), strokeWidth = 1.8.dp.toPx())
            }
            AppGlyph.Import -> {
                drawLine(color, Offset(w / 2, 4.dp.toPx()), Offset(w / 2, 14.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(7.dp.toPx(), 11.dp.toPx()), Offset(w / 2, 15.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(15.dp.toPx(), 11.dp.toPx()), Offset(w / 2, 15.dp.toPx()), strokeWidth = 1.6.dp.toPx())
            }
            AppGlyph.Save -> {
                drawLine(color, Offset(5.dp.toPx(), 11.dp.toPx()), Offset(9.dp.toPx(), 16.dp.toPx()), strokeWidth = 1.8.dp.toPx())
                drawLine(color, Offset(9.dp.toPx(), 16.dp.toPx()), Offset(17.dp.toPx(), 6.dp.toPx()), strokeWidth = 1.8.dp.toPx())
            }
            AppGlyph.Cancel -> {
                drawLine(color, Offset(6.dp.toPx(), 6.dp.toPx()), Offset(16.dp.toPx(), 16.dp.toPx()), strokeWidth = 1.8.dp.toPx())
                drawLine(color, Offset(16.dp.toPx(), 6.dp.toPx()), Offset(6.dp.toPx(), 16.dp.toPx()), strokeWidth = 1.8.dp.toPx())
            }
            AppGlyph.ChevronLeft -> {
                drawLine(color, Offset(13.dp.toPx(), 5.dp.toPx()), Offset(7.dp.toPx(), 11.dp.toPx()), strokeWidth = 1.8.dp.toPx())
                drawLine(color, Offset(7.dp.toPx(), 11.dp.toPx()), Offset(13.dp.toPx(), 17.dp.toPx()), strokeWidth = 1.8.dp.toPx())
            }
            AppGlyph.ChevronRight -> {
                drawLine(color, Offset(9.dp.toPx(), 5.dp.toPx()), Offset(15.dp.toPx(), 11.dp.toPx()), strokeWidth = 1.8.dp.toPx())
                drawLine(color, Offset(15.dp.toPx(), 11.dp.toPx()), Offset(9.dp.toPx(), 17.dp.toPx()), strokeWidth = 1.8.dp.toPx())
            }
            AppGlyph.ChevronDown -> {
                drawLine(color, Offset(5.dp.toPx(), 8.dp.toPx()), Offset(11.dp.toPx(), 14.dp.toPx()), strokeWidth = 1.8.dp.toPx())
                drawLine(color, Offset(11.dp.toPx(), 14.dp.toPx()), Offset(17.dp.toPx(), 8.dp.toPx()), strokeWidth = 1.8.dp.toPx())
            }
            AppGlyph.Calendar -> {
                drawRect(
                    color,
                    topLeft = Offset(4.dp.toPx(), 6.dp.toPx()),
                    size =
                        androidx.compose.ui.geometry
                            .Size(14.dp.toPx(), 12.dp.toPx()),
                    style = stroke,
                )
                drawLine(color, Offset(4.dp.toPx(), 10.dp.toPx()), Offset(18.dp.toPx(), 10.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(8.dp.toPx(), 4.dp.toPx()), Offset(8.dp.toPx(), 8.dp.toPx()), strokeWidth = 1.6.dp.toPx())
            }
            AppGlyph.Breakdown -> {
                drawLine(color, Offset(4.dp.toPx(), 5.dp.toPx()), Offset(18.dp.toPx(), 5.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(4.dp.toPx(), 9.dp.toPx()), Offset(14.dp.toPx(), 9.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(4.dp.toPx(), 13.dp.toPx()), Offset(16.dp.toPx(), 13.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(4.dp.toPx(), 18.dp.toPx()), Offset(18.dp.toPx(), 18.dp.toPx()), strokeWidth = 2.2.dp.toPx())
            }
            AppGlyph.Copy -> {
                drawRect(
                    color,
                    topLeft = Offset(4.dp.toPx(), 7.dp.toPx()),
                    size =
                        androidx.compose.ui.geometry
                            .Size(11.dp.toPx(), 11.dp.toPx()),
                    style = stroke,
                )
                drawRect(
                    color,
                    topLeft = Offset(8.dp.toPx(), 3.dp.toPx()),
                    size =
                        androidx.compose.ui.geometry
                            .Size(11.dp.toPx(), 11.dp.toPx()),
                    style = stroke,
                )
            }
            AppGlyph.Average -> {
                drawLine(color, Offset(4.dp.toPx(), 15.dp.toPx()), Offset(4.dp.toPx(), 18.dp.toPx()), strokeWidth = 2.2.dp.toPx())
                drawLine(color, Offset(11.dp.toPx(), 9.dp.toPx()), Offset(11.dp.toPx(), 18.dp.toPx()), strokeWidth = 2.2.dp.toPx())
                drawLine(color, Offset(3.dp.toPx(), 12.dp.toPx()), Offset(19.dp.toPx(), 12.dp.toPx()), strokeWidth = 1.2.dp.toPx())
            }
            AppGlyph.Zero -> {
                drawOval(
                    color,
                    topLeft = Offset(6.dp.toPx(), 4.dp.toPx()),
                    size =
                        androidx.compose.ui.geometry
                            .Size(10.dp.toPx(), 14.dp.toPx()),
                    style = stroke,
                )
            }
            AppGlyph.Undo -> {
                drawLine(color, Offset(5.dp.toPx(), 9.dp.toPx()), Offset(15.dp.toPx(), 9.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(5.dp.toPx(), 9.dp.toPx()), Offset(9.dp.toPx(), 5.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawArc(
                    color,
                    startAngle = -90f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(11.dp.toPx(), 9.dp.toPx()),
                    size =
                        androidx.compose.ui.geometry
                            .Size(8.dp.toPx(), 8.dp.toPx()),
                    style = stroke,
                )
            }
        }
    }
}

@Composable
fun AppGlyphIcon(
    glyph: AppGlyph,
    color: Color = Metro.colors.foreground,
) {
    Canvas(Modifier.size(22.dp)) {
        with(AppGlyphInk) { draw(glyph, color) }
    }
}

@Immutable
class PivotItem(
    val header: String,
    val content: @Composable () -> Unit,
)

/**
 * Metro pivot: a small title, a row of headers, and one item at a time across the full width.
 * The selected header leads the row; the rest follow in loop order, dimmed, and slide in as
 * the person swipes. Tapping a header opens that item.
 */
@Composable
fun MetroPivot(
    title: String,
    items: List<PivotItem>,
    modifier: Modifier = Modifier,
    initialItem: Int = 0,
    onGesture: (Boolean) -> Unit = {},
    /** Called with the item index once a swipe or header tap settles. */
    onSelected: (Int) -> Unit = {},
) {
    val count = items.size
    val loops = if (count > 1) 200 else 1
    val origin = remember(count) { (loops / 2) * count }
    val pager = rememberPagerState(initialPage = origin + initialItem) { loops * count }
    val scope = rememberCoroutineScope()
    val type = Metro.typography
    val colors = Metro.colors
    val animations = Metro.animations

    LaunchedEffect(pager) {
        snapshotFlow { pager.isScrollInProgress }.collect { onGesture(it) }
    }

    fun itemOf(page: Int): Int = Math.floorMod(page - origin, count)

    val selectedCallback by rememberUpdatedState(onSelected)
    LaunchedEffect(pager, count) {
        snapshotFlow { pager.settledPage }.drop(1).collect { selectedCallback(itemOf(it)) }
    }

    fun goTo(index: Int) {
        scope.launch {
            val target = pager.currentPage + PivotMotion.delta(itemOf(pager.currentPage), index, count)
            if (animations) {
                pager.animateScrollToPage(target)
            } else {
                pager.scrollToPage(target)
            }
        }
    }

    val base by remember {
        derivedStateOf { floor(pager.currentPage + pager.currentPageOffsetFraction).toInt() }
    }
    val selected by remember { derivedStateOf { itemOf(pager.currentPage) } }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("pivot"),
    ) {
        MetroText(
            title,
            type.pageTitle,
            Modifier
                .padding(start = MetroDimens.Gutter, top = MetroDimens.Row, end = MetroDimens.Gutter)
                .testTag("pivot-title"),
            maxLines = 1,
            softWrap = false,
        )
        val gapPx = with(LocalDensity.current) { MetroDimens.Grid.roundToPx() }
        val gutterPx = with(LocalDensity.current) { MetroDimens.Gutter.roundToPx() }
        Layout(
            content = {
                PivotMotion.order(itemOf(base), count).forEach { index ->
                    val isSelected = index == selected
                    MetroText(
                        items[index].header,
                        type.sectionHeader,
                        Modifier
                            .heightIn(min = MetroDimens.TouchTarget)
                            .semantics {
                                role = Role.Tab
                                this.selected = isSelected
                            }.metroPress { goTo(index) }
                            .testTag("pivot-header-$index"),
                        color = if (isSelected) colors.foreground else colors.secondary,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .padding(bottom = MetroDimens.Row)
                    .testTag("pivot-headers"),
        ) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity, minHeight = 0)
            val placeables = measurables.map { it.measure(loose) }
            val height = placeables.maxOfOrNull { it.height } ?: 0
            layout(constraints.maxWidth, height) {
                // Read in layout so a swipe only moves the row; it never recomposes it.
                val position = pager.currentPage + pager.currentPageOffsetFraction
                val fraction = position - floor(position)
                val lead = placeables.firstOrNull()?.width ?: 0
                var x = gutterPx - (fraction * (lead + gapPx)).roundToInt()
                placeables.forEach {
                    it.placeRelative(x, 0)
                    x += it.width + gapPx
                }
            }
        }
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f),
            beyondViewportPageCount = 1,
            userScrollEnabled = count > 1,
        ) { page ->
            Box(Modifier.fillMaxSize()) {
                items[itemOf(page)].content()
            }
        }
    }
}
