package app.duenorth.budget

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import app.duenorth.budget.design.MetroMotion
import app.duenorth.budget.design.theme.Metro
import kotlin.math.roundToInt

/**
 * Which rows a list has shown. The rows there when the list first draws are the list as it opened, so they never
 * animate. A row that turns up later is new: it opens a slot that eases the rows under it down, and rises into it.
 *
 * A row counts as new only on the frame it arrives. One that scrolls into view later, or comes back after a scroll,
 * just appears, so scrolling never plays the entrance.
 */
@Stable
internal class Arrivals {
    private var seen: HashSet<String>? = null
    private val fresh = HashSet<String>()

    /** Bumped each time new rows arrive, so the list can clear them a frame later. */
    internal var batch = 0
        private set

    /** Notes the list's whole set of [ids]. True when any of them is new. */
    fun note(ids: Iterable<String>): Boolean {
        val known = seen ?: return false.also { seen = ids.toHashSet() }
        var any = false
        ids.forEach { id ->
            if (known.add(id)) {
                fresh += id
                any = true
            }
        }
        if (any) batch++
        return any
    }

    fun isNew(id: String): Boolean = id in fresh

    /** Whether every one of [ids] is new: a day heading arrives with its rows only when the whole day is new. */
    fun allNew(ids: Iterable<String>): Boolean = fresh.isNotEmpty() && ids.all { it in fresh }

    internal fun settle() = fresh.clear()
}

/** The [Arrivals] for a list showing [ids], started again whenever [key] changes (a different account, say). */
@Composable
internal fun rememberArrivals(
    ids: List<String>,
    key: Any? = Unit,
): Arrivals {
    val arrivals = remember(key) { Arrivals() }
    arrivals.note(ids)
    LaunchedEffect(arrivals, arrivals.batch) {
        // The rows that arrived are composed on the frame they arrive; after that, nothing is new.
        withFrameNanos { }
        withFrameNanos { }
        arrivals.settle()
    }
    return arrivals
}

/**
 * Keeps this list at its very top when rows arrive while it sits there, so rows landing above the first one are
 * seen arriving instead of slipping in out of sight. Call it in the list's composable, before the list.
 */
@Composable
internal fun LazyListState.keepTopFor(arrivals: Arrivals) {
    val handled = remember(arrivals) { intArrayOf(arrivals.batch) }
    if (arrivals.batch == handled[0]) return
    handled[0] = arrivals.batch
    // Read without subscribing, so scrolling never recomposes the list's owner.
    val atTop = Snapshot.withoutReadObservation { firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0 }
    if (atTop) requestScrollToItem(0)
}

/**
 * Plays a row's entrance when [arrivals] says row [id] has just arrived: its slot opens from nothing, easing the
 * rows below it down, while the row fades in and rises into the slot with Metro's ease out. With animations off,
 * or for a row already there, it is a no-op.
 */
@Composable
internal fun Modifier.arriving(
    arrivals: Arrivals?,
    id: String,
    new: (Arrivals) -> Boolean = { it.isNew(id) },
): Modifier {
    if (arrivals == null || !Metro.animations) return this
    // Decided once, when the row is first composed; the run itself is read only in layout and drawing.
    val entering = remember(id) { new(arrivals) }
    if (!entering) return this
    val progress = remember(id) { Animatable(0f) }
    LaunchedEffect(progress) {
        progress.animateTo(1f, tween(ArrivalMotion.DURATION_MS, easing = LinearEasing))
    }
    val t = { progress.value }
    return this
        .drawWithContent {
            // Clip only below, so a band or press that runs past the gutter still shows at the sides.
            clipRect(-size.width, 0f, size.width * 2, size.height) { this@drawWithContent.drawContent() }
        }.layout { measurable, constraints ->
            val p = measurable.measure(constraints)
            layout(p.width, (p.height * ArrivalMotion.open(t())).roundToInt()) { p.place(0, 0) }
        }.graphicsLayer {
            val rise = ArrivalMotion.rise(t())
            alpha = rise
            translationY = size.height * ArrivalMotion.TRAVEL * (1f - rise)
        }
}

/** The entrance's timing, as fractions of one linear run from 0 to 1. */
internal object ArrivalMotion {
    /** As long as a page turn, so a row arrives at the pace the next inbox row rises into the panel. */
    const val DURATION_MS = MetroMotion.PAGE_MS

    /** The share of its height the row rises through on its way in. */
    const val TRAVEL = 0.5f

    /** The slot opens over the first two thirds, at the pace a row resizes. */
    private const val OPEN_UNTIL = MetroMotion.SHORT_MS.toFloat() / MetroMotion.PAGE_MS

    /** The row starts in once the slot has begun to open, so it never shows in a gap too thin to read. */
    private const val RISE_FROM = 0.15f

    fun open(t: Float): Float = MetroMotion.Standard.transform((t / OPEN_UNTIL).coerceIn(0f, 1f))

    fun rise(t: Float): Float = MetroMotion.EaseOut.transform(((t - RISE_FROM) / (1f - RISE_FROM)).coerceIn(0f, 1f))
}
