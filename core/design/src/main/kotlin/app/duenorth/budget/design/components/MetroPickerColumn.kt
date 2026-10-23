package app.duenorth.budget.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** One looping picker column's rows, and the one centred row it shows as chosen. */
object PickerColumn {
    /** Rows shown at once; the middle one is the choice. */
    const val VISIBLE = 5
    private const val CENTRE = VISIBLE / 2
    private const val SPAN = Int.MAX_VALUE / 2

    /** The first visible list index that puts [position] in the centre row. */
    fun firstIndexFor(
        position: Int,
        count: Int,
    ): Int = (SPAN / count) * count + position - CENTRE

    /** The position (0 until [count]) shown in the centre row when [firstIndex] is the first visible row. */
    fun centredPosition(
        firstIndex: Int,
        count: Int,
    ): Int = Math.floorMod(firstIndex + CENTRE, count)

    const val LIST_SIZE = Int.MAX_VALUE
}

/**
 * A Windows Phone 8.1 picker column (the DatePickerFlyout's looping strip): [count] rows that
 * loop and snap, the centred row filled with the accent. [label] gives a row's main text and a
 * smaller line under it. [onPick] fires once a fling settles on a new row.
 */
@Composable
fun MetroPickerColumn(
    count: Int,
    position: Int,
    label: (Int) -> Pair<String, String>,
    onPick: (Int) -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    rowHeight: Dp = 72.dp,
) {
    val state = rememberLazyListState(PickerColumn.firstIndexFor(position, count))
    val pick by rememberUpdatedState(onPick)
    val currentCount by rememberUpdatedState(count)
    val scope = rememberCoroutineScope()
    // The day column's length follows the month: re-centre on the (already clamped) choice.
    LaunchedEffect(count, position) {
        if (!state.isScrollInProgress && centred(state, count) != position) {
            state.scrollToItem(PickerColumn.firstIndexFor(position, count))
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }
            .distinctUntilChanged()
            .filter { !it }
            .collect { pick(centred(state, currentCount)) }
    }
    LazyColumn(
        modifier
            .height(rowHeight * PickerColumn.VISIBLE)
            .testTag(tag),
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        items(PickerColumn.LIST_SIZE) { index ->
            val at = Math.floorMod(index, count)
            val chosen = at == position
            val (main, sub) = label(at)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .padding(vertical = 3.dp)
                    .controlFrame(
                        if (chosen) Metro.accent.fill else Metro.colors.outline,
                        fill = if (chosen) Metro.accent.fill else Metro.colors.background,
                    ).background(if (chosen) Metro.accent.fill else Color.Transparent)
                    .metroPress {
                        scope.launch { state.animateScrollToItem(index - PickerColumn.VISIBLE / 2) }
                    }.semantics { selected = chosen }
                    .testTag("$tag-$at"),
                contentAlignment = Alignment.BottomStart,
            ) {
                val ink = if (chosen) Metro.accent.onFill else Metro.colors.secondary
                Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    MetroText(main, Metro.typography.subheader, color = if (chosen) ink else Metro.colors.foreground, maxLines = 1)
                    if (sub.isNotEmpty()) MetroText(sub, Metro.typography.caption, color = ink, maxLines = 1)
                }
            }
        }
    }
}

private fun centred(
    state: LazyListState,
    count: Int,
): Int = PickerColumn.centredPosition(state.firstVisibleItemIndex, count)
