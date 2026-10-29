package app.duenorth.budget.design.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duenorth.budget.design.MetroMotion
import app.duenorth.budget.design.PivotMotion
import app.duenorth.budget.design.metroTilt
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import app.duenorth.budget.design.theme.MetroWeights
import app.duenorth.budget.design.theme.Relief
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
        text = if (Metro.style.mobile10) sentenceCase(text) else text,
        modifier = modifier,
        style = style.copy(color = color),
        maxLines = maxLines,
        softWrap = softWrap,
        overflow = if (softWrap) TextOverflow.Clip else TextOverflow.Ellipsis,
    )
}

/**
 * Windows 10 Mobile writes labels in sentence case where Metro writes them in lowercase: the first letter is raised
 * when the text starts in lowercase, so names, figures and all-caps titles are left as they are.
 */
fun sentenceCase(text: String): String =
    if (text.firstOrNull()?.isLowerCase() ==
        true
    ) {
        text.replaceFirstChar { it.titlecaseChar() }
    } else {
        text
    }

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

/**
 * What Enter does in a one-line [MetroField]: the app provides its form's save, so pressing Enter (or the
 * keyboard's done key) submits the page the way the bar's save does. Either way the box lets go of focus and
 * the keyboard closes, so the rest of the page shows; null does only that.
 */
val LocalFieldSubmit = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * A text box. One line unless [lines] is given: Enter in a one-line box submits the form through
 * [LocalFieldSubmit] and closes the keyboard (a box asking for [ImeAction.Next] moves on instead), and only
 * a multi-line box takes Enter as a new line.
 */
@Composable
fun MetroField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    conceal: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    fill: Boolean = true,
    lines: IntRange? = null,
    /** Fixed text before what is typed, such as `https://`: shown in the box, never editable. */
    prefix: String? = null,
    /** Makes [prefix] an accent button, for a page that lets the person pick it. */
    onPrefixClick: (() -> Unit)? = null,
) {
    val submit = LocalFieldSubmit.current
    MetroFieldFrame(value.isEmpty(), hint, modifier, fill, prefix, onPrefixClick) { style, cursor ->
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = style,
            cursorBrush = cursor,
            visualTransformation = if (conceal) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = fieldKeyboard(keyboardOptions, lines),
            keyboardActions = fieldActions(lines, submit),
            singleLine = lines == null,
            minLines = lines?.first ?: 1,
            maxLines = lines?.last ?: Int.MAX_VALUE,
            modifier = Modifier.fillMaxWidth().then(if (lines != null) Modifier.padding(vertical = 10.dp) else Modifier),
        )
    }
}

/**
 * The same one-line box over a [TextFieldValue], for a box that sets its own selection, such as one that
 * opens with its text selected so typing replaces it. Enter submits through [LocalFieldSubmit].
 */
@Composable
fun MetroField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    fill: Boolean = true,
) {
    val submit = LocalFieldSubmit.current
    MetroFieldFrame(value.text.isEmpty(), hint, modifier, fill) { style, cursor ->
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = style,
            cursorBrush = cursor,
            keyboardOptions = fieldKeyboard(keyboardOptions, null),
            keyboardActions = fieldActions(null, submit),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MetroFieldFrame(
    empty: Boolean,
    hint: String,
    modifier: Modifier,
    fill: Boolean,
    prefix: String? = null,
    onPrefixClick: (() -> Unit)? = null,
    field: @Composable (TextStyle, SolidColor) -> Unit,
) {
    val colors = Metro.colors
    // A relief style cuts the box into the page and writes in the page's own ink.
    val relief = Metro.style.relief
    // Windows 10 Mobile outlines the box on the page in a 2dp border that turns to the accent while typing.
    val mobile10 = Metro.style.mobile10
    val background = if (colors.isDark) Color.White else Color(0xFFBFBFBF)
    val hintColor =
        when {
            relief || mobile10 -> colors.secondary
            colors.isDark -> Color(0xFF909090)
            else -> Color(0xFF8F8F8F)
        }
    // Mica marks the field being typed in with an accent line under it, as Windows 11 does.
    var focused by remember { mutableStateOf(false) }
    val focusLine = Metro.accent.fill
    Box(
        modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .then(
                when {
                    relief -> Modifier.relief(Relief.Inset, small = true)
                    mobile10 -> Modifier.onFocusChanged { focused = it.hasFocus }.border(2.dp, if (focused) focusLine else colors.outline)
                    else -> Modifier.background(background)
                },
            ).then(
                if (Metro.style.layers) {
                    Modifier.onFocusChanged { focused = it.hasFocus }.layerFocusLine(focused)
                } else {
                    Modifier
                },
            ).heightIn(min = MetroDimens.TouchTarget)
            .padding(horizontal = if (relief) 14.dp else 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val ink = Metro.typography.control.copy(color = if (relief || mobile10) colors.foreground else Color.Black)
        if (prefix == null) {
            if (empty) {
                MetroText(hint, Metro.typography.control, color = hintColor)
            }
            field(ink, SolidColor(Metro.accent.fill))
        } else {
            // The prefix is part of the value but cannot be typed over: a darker grey than the hint,
            // so an empty box still reads as prefix then hint.
            val prefixColor = if (relief || mobile10) colors.secondary else Color(0xFF5A5A5A)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onPrefixClick == null) {
                    MetroText(
                        prefix,
                        Metro.typography.control,
                        Modifier.testTag("field-prefix"),
                        color = prefixColor,
                        maxLines = 1,
                        softWrap = false,
                    )
                } else {
                    // A pickable prefix is a small accent button, apart from the typed text.
                    val accent = Metro.accent
                    Row(
                        Modifier
                            .padding(end = 8.dp)
                            .heightIn(min = MetroDimens.TouchTarget - 12.dp)
                            .background(accent.fill)
                            .metroPress(onPrefixClick)
                            .padding(horizontal = 8.dp)
                            .testTag("field-prefix"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MetroText(prefix, Metro.typography.control, color = accent.onFill, maxLines = 1, softWrap = false)
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (empty) {
                        MetroText(hint, Metro.typography.control, color = hintColor, maxLines = 1)
                    }
                    field(ink, SolidColor(Metro.accent.fill))
                }
            }
        }
    }
}

private fun fieldKeyboard(
    options: KeyboardOptions,
    lines: IntRange?,
): KeyboardOptions =
    if (lines == null && options.imeAction in setOf(ImeAction.Default, ImeAction.Unspecified)) {
        options.copy(imeAction = ImeAction.Done)
    } else {
        options
    }

@Composable
private fun fieldActions(
    lines: IntRange?,
    submit: (() -> Unit)?,
): KeyboardActions {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    if (lines != null) return KeyboardActions.Default
    // Letting go of the box, not only hiding the keyboard, so nothing is left waiting for typing.
    val enter = {
        submit?.invoke()
        focus.clearFocus()
        keyboard?.hide()
    }
    return KeyboardActions(
        onDone = { enter() },
        onGo = { enter() },
        onSend = { enter() },
        onSearch = { enter() },
    )
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
    val relief = Metro.style.relief
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    if (Metro.style.mobile10) {
        Mobile10Button(label, modifier, interaction, pressed, onClick)
        return
    }
    Box(
        modifier
            .heightIn(min = MetroDimens.ButtonMin)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ).graphicsLayer { rotationZ = if (pressed && animations) 2.5f else 0f }
            .then(
                if (relief) {
                    // Relief: the button stands off the page and presses in under the finger.
                    Modifier.relief(if (pressed) Relief.Inset else Relief.Raised, small = true)
                } else {
                    Modifier
                        .border(MetroDimens.Stroke, colors.foreground)
                        .background(if (pressed) accent.fill else Color.Transparent)
                },
            ).padding(horizontal = 9.5.dp, vertical = 3.5.dp),
        contentAlignment = Alignment.Center,
    ) {
        MetroText(
            label,
            Metro.typography.body.copy(fontWeight = MetroWeights.Semibold),
            color =
                when {
                    relief && pressed -> accent.text
                    pressed -> Color.White
                    else -> colors.foreground
                },
        )
    }
}

/** The Windows 10 Mobile button: a flat grey block with no outline, darker under the finger, its label in regular. */
@Composable
private fun Mobile10Button(
    label: String,
    modifier: Modifier,
    interaction: MutableInteractionSource,
    pressed: Boolean,
    onClick: () -> Unit,
) {
    val colors = Metro.colors
    Box(
        modifier
            .heightIn(min = Mobile10Dimens.Button)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .background(colors.foreground.copy(alpha = if (pressed) 0.4f else 0.2f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        MetroText(label, Metro.typography.body, color = colors.foreground)
    }
}

/** Sizes of the Windows 10 Mobile style that differ from Metro's. */
object Mobile10Dimens {
    /** A button: taller than the 32epx desktop button, so it stays an easy touch target. */
    val Button = 40.dp

    /** The closed command bar. */
    val CommandBar = 48.dp

    /** One command bar button. */
    val CommandSlot = 68.dp
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
    Select,
    Search,
    Delete,
    Edit,
}

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
private const val SYNC_TOP_END = 335f
private const val SYNC_SWEEP = 130f
private const val SYNC_HEAD_LENGTH = 3.6f
private const val SYNC_HEAD_HALF = 3f
private const val SPIN_MS = 1200

fun List<AppBarButton>.asScreenActions(): List<ScreenAction> =
    map { ScreenAction(id = it.id, glyph = it.glyph, label = it.label, primary = true, run = it.onClick) }

object AppGlyphInk {
    fun strokeCount(glyph: AppGlyph): Int =
        when (glyph) {
            AppGlyph.Budgets -> 3
            AppGlyph.Appearance -> 3
            AppGlyph.Sync -> 4
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
            AppGlyph.Select -> 4
            AppGlyph.Search -> 2
            AppGlyph.Delete -> 4
            AppGlyph.Edit -> 2
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
                // As in due north tasks: two arcs chasing each other clockwise, each ending in a solid
                // head that sits on the arc's end and points along it (WP8.1 sync), so the glyph reads
                // the same mid-spin. Drawn on a 24 x 24 grid scaled to the canvas.
                val u = size.minDimension / 24f
                val r = 7.5f
                for (end in listOf(SYNC_TOP_END, SYNC_TOP_END + 180f)) {
                    drawArc(
                        color,
                        end - SYNC_SWEEP,
                        SYNC_SWEEP,
                        false,
                        topLeft = Offset((12f - r) * u, (12f - r) * u),
                        size =
                            androidx.compose.ui.geometry
                                .Size(2 * r * u, 2 * r * u),
                        style = stroke,
                    )
                    val a = Math.toRadians(end.toDouble())
                    // Outward from the centre, then clockwise along the arc.
                    val nx = kotlin.math.cos(a).toFloat()
                    val ny = kotlin.math.sin(a).toFloat()
                    val tx = -ny
                    val ty = nx
                    val ex = 12f + r * nx
                    val ey = 12f + r * ny
                    drawPath(
                        Path().apply {
                            moveTo((ex + tx * SYNC_HEAD_LENGTH) * u, (ey + ty * SYNC_HEAD_LENGTH) * u)
                            lineTo((ex + nx * SYNC_HEAD_HALF) * u, (ey + ny * SYNC_HEAD_HALF) * u)
                            lineTo((ex - nx * SYNC_HEAD_HALF) * u, (ey - ny * SYNC_HEAD_HALF) * u)
                            close()
                        },
                        color,
                    )
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
            AppGlyph.Select -> {
                // A list with a box before each line: the WP "select" glyph.
                listOf(5f, 13f).forEach { top ->
                    drawRect(
                        color,
                        topLeft = Offset(4.dp.toPx(), top.dp.toPx()),
                        size =
                            androidx.compose.ui.geometry
                                .Size(5.dp.toPx(), 5.dp.toPx()),
                        style = stroke,
                    )
                    drawLine(
                        color,
                        Offset(12.dp.toPx(), (top + 2.5f).dp.toPx()),
                        Offset(18.dp.toPx(), (top + 2.5f).dp.toPx()),
                        strokeWidth = 1.6.dp.toPx(),
                    )
                }
            }
            AppGlyph.Search -> {
                // A lens with its handle running down to the right: the WP "search" glyph.
                drawCircle(color, radius = 5.5.dp.toPx(), center = Offset(9.5.dp.toPx(), 9.5.dp.toPx()), style = stroke)
                drawLine(color, Offset(13.5.dp.toPx(), 13.5.dp.toPx()), Offset(18.dp.toPx(), 18.dp.toPx()), strokeWidth = 1.6.dp.toPx())
            }
            AppGlyph.Delete -> {
                // A bin under its lid, with its handle and two ribs: the WP "delete" glyph.
                drawLine(color, Offset(4.dp.toPx(), 6.dp.toPx()), Offset(18.dp.toPx(), 6.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(color, Offset(9.dp.toPx(), 3.5.dp.toPx()), Offset(13.dp.toPx(), 3.5.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawRect(
                    color,
                    topLeft = Offset(6.dp.toPx(), 6.dp.toPx()),
                    size =
                        androidx.compose.ui.geometry
                            .Size(10.dp.toPx(), 12.dp.toPx()),
                    style = stroke,
                )
                listOf(9.5f, 12.5f).forEach { x ->
                    drawLine(color, Offset(x.dp.toPx(), 9.dp.toPx()), Offset(x.dp.toPx(), 15.dp.toPx()), strokeWidth = 1.2.dp.toPx())
                }
            }
            AppGlyph.Edit -> {
                // A pencil leaning down to the left, its point at the bottom and a band below its end: the WP "edit" glyph.
                drawPath(
                    Path().apply {
                        moveTo(4.dp.toPx(), 18.dp.toPx())
                        lineTo(5.6.dp.toPx(), 13.6.dp.toPx())
                        lineTo(14.6.dp.toPx(), 4.6.dp.toPx())
                        lineTo(17.4.dp.toPx(), 7.4.dp.toPx())
                        lineTo(8.4.dp.toPx(), 16.4.dp.toPx())
                        close()
                    },
                    color,
                    style = Stroke(width = 1.6.dp.toPx(), join = StrokeJoin.Miter),
                )
                drawLine(color, Offset(12.6.dp.toPx(), 6.6.dp.toPx()), Offset(15.4.dp.toPx(), 9.4.dp.toPx()), strokeWidth = 1.6.dp.toPx())
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
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
) {
    val mobile10 = Metro.style.mobile10
    Canvas(modifier.size(size)) {
        if (mobile10 && Mdl2GlyphInk.covers(glyph)) {
            with(Mdl2GlyphInk) { draw(glyph, color) }
        } else {
            with(AppGlyphInk) { draw(glyph, color) }
        }
    }
}

/**
 * The glyphs of the Windows 10 Mobile style, after Segoe MDL2 Assets (which may not be bundled): hairline strokes on
 * a 20-unit grid, square ends and mitred corners, drawn 20dp inside the glyph's box. Each is an SVG path.
 */
object Mdl2GlyphInk {
    private const val GRID = 20f

    private val paths: Map<AppGlyph, String> =
        mapOf(
            // OpenFolder: the budget files.
            AppGlyph.Budgets to "M1.5 16.5V3.5h5.5l2 2h8v3M1.5 16.5l3-8h14l-3 8z",
            // Brightness: a sun and its eight rays.
            AppGlyph.Appearance to
                "M10 6a4 4 0 1 0 0 8a4 4 0 1 0 0-8zM10 1v2.5M10 16.5V19M1 10h2.5M16.5 10H19M3.6 3.6l1.8 1.8M14.6 14.6l1.8 1.8" +
                "M3.6 16.4l1.8-1.8M14.6 5.4l1.8-1.8",
            // Sync: two arcs chasing each other, each with a corner for its arrowhead.
            AppGlyph.Sync to "M16.4 8.5A6.6 6.6 0 0 0 4.3 6.5M3.6 11.5a6.6 6.6 0 0 0 12.1 2M4 2.5v4.2h4.2M16 17.5v-4.2h-4.2",
            // Contact: a head over the shoulders.
            AppGlyph.Payees to "M10 2a4.2 4.2 0 1 0 0 8.4a4.2 4.2 0 1 0 0-8.4zM2.5 18.5a7.5 7.5 0 0 1 15 0",
            // Pause.
            AppGlyph.Hold to "M5 3.5h3v13H5zM12 3.5h3v13h-3z",
            // ViewAll: four tiles.
            AppGlyph.Categories to "M2.5 2.5h6v6h-6zM11.5 2.5h6v6h-6zM2.5 11.5h6v6h-6zM11.5 11.5h6v6h-6z",
            AppGlyph.Add to "M10 2v16M2 10h16",
            // Switch: one arrow out, one back.
            AppGlyph.Transfer to "M2 7h15M13.5 3.5L17 7l-3.5 3.5M18 13H3M6.5 9.5L3 13l3.5 3.5",
            // CheckMark.
            AppGlyph.Reconcile to "M2.5 10.5l5 5L17.5 5",
            // Download: an arrow onto a line.
            AppGlyph.Import to "M10 2v11.5M5.5 9l4.5 4.5L14.5 9M2.5 17.5h15",
            // Accept.
            AppGlyph.Save to "M2.5 10.5l5 5L17.5 5",
            AppGlyph.Cancel to "M3.5 3.5l13 13M16.5 3.5l-13 13",
            AppGlyph.ChevronLeft to "M13 3.5L6.5 10l6.5 6.5",
            AppGlyph.ChevronRight to "M7 3.5l6.5 6.5L7 16.5",
            AppGlyph.ChevronDown to "M3.5 7l6.5 6.5L16.5 7",
            // Calendar: a page under its binding rings.
            AppGlyph.Calendar to "M2 4h16v14H2zM2 8h16M6 2v4M14 2v4",
            // List.
            AppGlyph.Breakdown to "M2 4.5h16M2 8.5h11M2 12.5h13M2 16.5h16",
            AppGlyph.Copy to "M6.5 6.5h11v11h-11zM3.5 13.5h-1v-11h11v1",
            // A bar chart under its mean.
            AppGlyph.Average to "M3.5 18v-4M10 18V7M16.5 18v-6M2 10.5h16",
            AppGlyph.Zero to "M10 3a5 7 0 1 1 0 14a5 7 0 1 1 0-14z",
            // Undo: back along a line that turns home.
            AppGlyph.Undo to "M6.5 3.5L3 7l3.5 3.5M3 7h9a4.5 4.5 0 0 1 0 9H6",
            // MultiSelect: ticked boxes before their lines.
            AppGlyph.Select to "M2 3h6v6H2zM2 11h6v6H2zM3.6 6.1l1.2 1.2L6.6 5M11 6h7M11 14h7",
            // Search: the lens up and right, its handle down and left.
            AppGlyph.Search to "M12 2a6 6 0 1 0 0 12a6 6 0 1 0 0-12zM7.7 12.3L2 18",
            // Delete: a bin with its lid and handle and two ribs.
            AppGlyph.Delete to "M2 4.5h16M7.5 4.5V2h5v2.5M4 4.5l1 13.5h10l1-13.5M8.5 7.5v7.5M11.5 7.5v7.5",
            // Edit: a pencil down to the left, its tip and the band behind it.
            AppGlyph.Edit to "M2.5 17.5l1-4.5L14 2.5L17.5 6L7 16.5zM12 4.5L15.5 8M3.5 13L7 16.5",
        )

    /** Whether this style has its own ink for [glyph]; one it lacks falls back to Metro's rather than failing. */
    fun covers(glyph: AppGlyph): Boolean = glyph == AppGlyph.Settings || glyph in paths

    private val parsed = mutableMapOf<AppGlyph, Path>()

    private fun pathOf(glyph: AppGlyph): Path =
        parsed.getOrPut(glyph) {
            if (glyph == AppGlyph.Settings) gear() else PathParser().parsePathString(paths.getValue(glyph)).toPath()
        }

    /** Settings: an eight-toothed gear round its hub. */
    private fun gear(): Path =
        Path().apply {
            val teeth = 8
            val outer = 8.5f
            val inner = 6.6f
            for (i in 0 until teeth * 4) {
                val angle = Math.toRadians(i * 360.0 / (teeth * 4) - 90.0 / teeth)
                val r = if ((i / 2) % 2 == 0) outer else inner
                val x = 10f + r * kotlin.math.cos(angle).toFloat()
                val y = 10f + r * kotlin.math.sin(angle).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
            addOval(
                androidx.compose.ui.geometry
                    .Rect(7f, 7f, 13f, 13f),
            )
        }

    fun DrawScope.draw(
        glyph: AppGlyph,
        color: Color,
    ) {
        val box = size.minDimension
        val glyphSize = minOf(box, 20.dp.toPx())
        val scale = glyphSize / GRID
        translate((size.width - glyphSize) / 2, (size.height - glyphSize) / 2) {
            scale(scale, pivot = Offset.Zero) {
                drawPath(
                    pathOf(glyph),
                    color,
                    style = Stroke(width = 1.dp.toPx() / scale, cap = StrokeCap.Square, join = StrokeJoin.Miter),
                )
            }
        }
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
    /** Holds the pivot on its item: no swiping, and the headers do not switch. */
    locked: Boolean = false,
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
        if (locked) return
        scope.launch {
            val target = pager.currentPage + PivotMotion.delta(itemOf(pager.currentPage), index, count)
            if (animations) {
                pager.animateScrollToPage(target, animationSpec = tween(MetroMotion.PAGE_MS, easing = MetroMotion.EaseOut))
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
        // Mica swaps the pivot's big headers for a Windows 11 selector bar: smaller headers, the chosen one underlined.
        val layers = Metro.style.layers
        val headerStyle = if (layers) type.sectionHeader.copy(fontSize = 30.sp) else type.sectionHeader
        val selectorLine = Metro.accent.fill
        val gapPx = with(LocalDensity.current) { (if (layers) 20.dp else MetroDimens.Grid).roundToPx() }
        val gutterPx = with(LocalDensity.current) { MetroDimens.Gutter.roundToPx() }
        Layout(
            content = {
                PivotMotion.order(itemOf(base), count).forEach { index ->
                    // Keyed, so each header keeps its own colour as the row rotates.
                    key(index) {
                        val isSelected = index == selected
                        // Brightens and dims rather than switching at the swipe's midpoint.
                        val color by animateColorAsState(
                            if (isSelected) colors.foreground else colors.secondary,
                            if (animations) tween(MetroMotion.QUICK_MS) else snap(),
                            label = "pivot-header",
                        )
                        MetroText(
                            items[index].header,
                            headerStyle,
                            Modifier
                                .heightIn(min = MetroDimens.TouchTarget)
                                .then(if (layers && isSelected) Modifier.drawBehind { drawSelectorLine(selectorLine) } else Modifier)
                                .semantics {
                                    role = Role.Tab
                                    this.selected = isSelected
                                }.metroPress { goTo(index) }
                                .testTag("pivot-header-$index"),
                            color = color,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
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
            userScrollEnabled = count > 1 && !locked,
        ) { page ->
            Box(Modifier.fillMaxSize()) {
                items[itemOf(page)].content()
            }
        }
    }
}

/** The short accent line under mica's chosen pivot header. */
private fun DrawScope.drawSelectorLine(color: Color) {
    val line = 3.dp.toPx()
    drawRoundRect(
        color,
        Offset(0f, size.height - line),
        Size(28.dp.toPx(), line),
        CornerRadius(line / 2),
    )
}
