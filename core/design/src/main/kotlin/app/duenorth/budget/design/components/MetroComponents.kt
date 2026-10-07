package app.duenorth.budget.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.duenorth.budget.design.PanoramaMotion
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import kotlinx.coroutines.launch

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
fun Modifier.metroPress(onClick: () -> Unit): Modifier {
    val animations = Metro.animations
    val pressedColor = Metro.colors.chrome
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    return this
        .clickable(
            interactionSource = interaction,
            indication = null,
            role = Role.Button,
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
) {
    val colors = Metro.colors
    val type = Metro.typography
    Box(
        modifier
            .fillMaxWidth()
            .border(1.dp, colors.outline)
            .heightIn(min = MetroDimens.TouchTarget)
            .padding(horizontal = MetroDimens.Gutter, vertical = 12.dp),
    ) {
        if (value.isEmpty()) {
            MetroText(hint, type.body, color = colors.secondary)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = type.body.copy(color = colors.foreground),
            cursorBrush = SolidColor(Metro.accent.fill),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun MetroButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = Metro.accent
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .background(accent.fill)
            .metroPress(onClick),
        contentAlignment = Alignment.Center,
    ) {
        MetroText(label, Metro.typography.body, color = accent.onFill)
    }
}

enum class AppGlyph { Budgets, Appearance, More }

@Immutable
data class AppBarButton(
    val glyph: AppGlyph,
    val label: String,
    val onClick: () -> Unit,
)

@Composable
fun MetroAppBar(
    buttons: List<AppBarButton>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Metro.colors
    Column(modifier.fillMaxWidth().background(colors.chrome)) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = MetroDimens.AppBar)
                .padding(vertical = 8.dp),
        ) {
            Row(
                Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                buttons.forEach { button ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(MetroDimens.TouchTarget)
                                .border(2.dp, colors.foreground, CircleShape)
                                .clip(CircleShape)
                                .metroPress(button.onClick),
                            contentAlignment = Alignment.Center,
                        ) {
                            AppGlyphIcon(button.glyph)
                        }
                        if (expanded) {
                            MetroText(button.label, Metro.typography.caption)
                        }
                    }
                }
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(width = 56.dp, height = MetroDimens.TouchTarget)
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        role = Role.Button,
                        onClick = { onExpandedChange(!expanded) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                MetroText("•••", Metro.typography.subheader)
            }
        }
    }
}

@Composable
private fun AppGlyphIcon(glyph: AppGlyph) {
    val color = Metro.colors.foreground
    Canvas(Modifier.size(22.dp)) {
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Square)
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
                drawLine(color, Offset(size.width / 2, 1.dp.toPx()), Offset(size.width / 2, 4.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                drawLine(
                    color,
                    Offset(size.width / 2, size.height - 1.dp.toPx()),
                    Offset(size.width / 2, size.height - 4.dp.toPx()),
                    strokeWidth = 1.6.dp.toPx(),
                )
            }
            AppGlyph.More -> Unit
        }
    }
}

/** Draws this child higher by [distance] and gives that space back to the parent. */
private fun Modifier.pullUp(distance: Dp): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val dy = distance.roundToPx()
        val height = (placeable.height - dy).coerceAtLeast(0)
        layout(placeable.width, height) {
            placeable.placeRelative(0, -dy)
        }
    }

@Immutable
class PanoramaSection(
    val header: String,
    val content: @Composable () -> Unit,
)

@Composable
fun MetroPanorama(
    title: String,
    sections: List<PanoramaSection>,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    initialSection: Int = 0,
    onGesture: (Boolean) -> Unit = {},
) {
    val count = sections.size
    val loops = 200
    val origin = remember(count) { (loops / 2) * count }
    val pager = rememberPagerState(initialPage = origin + initialSection) { loops * count }
    val scope = rememberCoroutineScope()
    val type = Metro.typography
    val colors = Metro.colors
    val animations = Metro.animations
    var titleWidth by remember { mutableIntStateOf(0) }

    LaunchedEffect(pager) {
        snapshotFlow { pager.isScrollInProgress }.collect { onGesture(it) }
    }

    fun sectionOf(page: Int): Int = Math.floorMod(page - origin, count)

    fun goTo(index: Int) {
        scope.launch {
            val current = sectionOf(pager.currentPage)
            var delta = index - current
            if (delta > count / 2) delta -= count
            if (delta < -count / 2) delta += count
            val target = pager.currentPage + delta
            if (animations) {
                pager.animateScrollToPage(target)
            } else {
                pager.scrollToPage(target)
            }
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("panorama"),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = MetroDimens.Gutter, top = 8.dp, end = MetroDimens.Gutter)
                .onGloballyPositioned { titleWidth = it.size.width }
                .graphicsLayer {
                    val shift =
                        PanoramaMotion.titleShiftPx(
                            sectionPosition = (pager.currentPage - origin) + pager.currentPageOffsetFraction,
                            sectionCount = count,
                            titleWidthPx = titleWidth.toFloat(),
                        )
                    // Keep the whole title on screen. The raw shift can be larger than the gutter.
                    translationX = shift.coerceIn(-MetroDimens.Gutter.toPx(), 0f)
                },
        ) {
            MetroText(
                title,
                type.panoramaTitle,
                Modifier.testTag("panorama-title"),
                maxLines = 1,
                softWrap = false,
            )
            if (subtitle.isNotEmpty()) {
                MetroText(
                    subtitle,
                    type.subheader,
                    Modifier
                        // The 118sp line box hangs below the letters. Sit the subtitle on that baseline.
                        .pullUp(40.dp)
                        .padding(start = 16.dp)
                        .testTag("panorama-subtitle"),
                    color = colors.secondary,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(end = MetroDimens.Peek),
            beyondViewportPageCount = 1,
        ) { page ->
            val index = sectionOf(page)
            val section = sections[index]
            Column(Modifier.fillMaxSize()) {
                MetroText(
                    section.header,
                    type.sectionHeader,
                    Modifier
                        .padding(start = MetroDimens.Gutter)
                        .semantics { heading() }
                        .metroPress { goTo(index) }
                        .heightIn(min = MetroDimens.TouchTarget),
                    maxLines = 1,
                    softWrap = false,
                )
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    section.content()
                }
            }
        }
    }
}
