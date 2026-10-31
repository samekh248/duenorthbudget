package app.duenorth.budget

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex

/**
 * Drag to reorder, shared by the accounts list, the categories page and upcoming schedules. Rows are named by id; a drop hands
 * `onMove` the held row and the row it now sits before (null when it lands last). It holds the row a drag has,
 * how far it has travelled, and each row's height, which turns distance into places.
 */
internal class RowDrag {
    var dragging by mutableStateOf<String?>(null)
    var offset by mutableFloatStateOf(0f)
    private val heights = mutableStateMapOf<String, Int>()

    /** The group the held row was picked up in, as it was then: a repaint mid-drag must not cancel the gesture. */
    private var held: List<String> = emptyList()

    /** Each row's group as last drawn, read when a drag starts; the gesture itself is keyed by the row alone. */
    private val groups = HashMap<String, List<String>>()

    fun drop() {
        dragging = null
        offset = 0f
        held = emptyList()
    }

    /**
     * Where the held row would land among [rows] if let go now: past every row whose middle its own middle
     * has crossed. Rows differ in height (a long name wraps), so each is measured rather than assumed.
     */
    fun target(rows: List<String>): Int? {
        val from = rows.indexOf(dragging).takeIf { it >= 0 } ?: return null
        val tops = rows.runningFold(0f) { top, row -> top + height(row) }
        val middle = tops[from] + offset + height(rows[from]) / 2f
        return rows.indices.count { it != from && tops[it] + height(rows[it]) / 2f < middle }
    }

    private fun height(id: String): Float = (heights[id] ?: heights.values.firstOrNull() ?: 1).toFloat()

    /** The held row follows the finger; the rows it passes slide out of its way by its height. */
    fun follow(
        id: String,
        rows: List<String>,
    ): Modifier {
        val from = rows.indexOf(dragging)
        val index = rows.indexOf(id)
        val to = target(rows)
        val lifted = dragging == id
        val gap = if (from < 0) 0f else height(rows[from])
        val shift =
            when {
                from < 0 || to == null || lifted -> 0f
                index in (from + 1)..to -> -gap
                index in to until from -> gap
                else -> 0f
            }
        return Modifier
            .onSizeChanged { heights[id] = it.height }
            .zIndex(if (lifted) 1f else 0f)
            .graphicsLayer { translationY = if (lifted) offset else shift }
    }

    /**
     * The grip's own gesture: a drag straight away, no hold first, so the list keeps its scroll elsewhere.
     * With [hold] the whole row is the grip and a drag starts after a long press, so a swipe still scrolls.
     */
    fun handle(
        id: String,
        rows: List<String>,
        onMove: (String, String?) -> Unit,
        hold: Boolean = false,
    ): Modifier {
        groups[id] = rows
        val start: (Offset) -> Unit = {
            held = groups[id].orEmpty()
            dragging = id
            offset = 0f
        }
        val end: () -> Unit = {
            val group = held
            val from = group.indexOf(id)
            val to = target(group) ?: from
            drop()
            if (from >= 0 && to != from) onMove(id, if (to < from) group[to] else group.getOrNull(to + 1))
        }
        val move: (PointerInputChange, Offset) -> Unit = { change, amount ->
            change.consume()
            offset += amount.y
        }
        return Modifier.pointerInput(id, hold) {
            if (hold) {
                detectDragGesturesAfterLongPress(onDragStart = start, onDragEnd = end, onDragCancel = ::drop, onDrag = move)
            } else {
                detectDragGestures(onDragStart = start, onDragEnd = end, onDragCancel = ::drop, onDrag = move)
            }
        }
    }
}
