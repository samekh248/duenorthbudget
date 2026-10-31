package app.duenorth.budget.design.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duenorth.budget.design.MetroMotion
import app.duenorth.budget.design.PivotMotion
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.roundToInt

@Immutable
class PivotItem(
    val header: String,
    /** Things waiting in the item, shown small in the accent beside its header; none when 0. */
    val count: Int = 0,
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
        val accentText = Metro.accent.text
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
                        Row(
                            Modifier
                                .heightIn(min = MetroDimens.TouchTarget)
                                .then(if (layers && isSelected) Modifier.drawBehind { drawSelectorLine(selectorLine) } else Modifier)
                                .semantics(mergeDescendants = true) {
                                    role = Role.Tab
                                    this.selected = isSelected
                                }.metroPress { goTo(index) }
                                .testTag("pivot-header-$index"),
                        ) {
                            MetroText(
                                items[index].header,
                                headerStyle,
                                color = color,
                                maxLines = 1,
                                softWrap = false,
                            )
                            val count = items[index].count
                            if (count > 0) {
                                MetroText(
                                    "$count",
                                    type.caption.copy(fontWeight = FontWeight.SemiBold),
                                    Modifier.padding(start = 4.dp, top = 6.dp).testTag("pivot-count-$index"),
                                    color = accentText,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
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
