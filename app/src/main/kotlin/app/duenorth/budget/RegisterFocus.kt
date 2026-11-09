package app.duenorth.budget

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import app.duenorth.budget.design.MetroMotion
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import kotlinx.coroutines.delay

/**
 * Where a focused transaction sits in the register's list: [heading] is the item to bring to the top, so the row's
 * date (or the pair's or pending section's heading) shows above it, and [row] is the item that holds the row itself.
 */
internal data class FocusItems(
    val heading: Int,
    val row: Int,
)

/**
 * The list items for transaction [id], laid out as the register builds its list: each possible match pair, then the
 * pending heading and (when [showPending]) its rows, then each day. On [cards] a day, and the pending rows, are one
 * item each; otherwise a day is its heading followed by one item per row. Null when the list does not show [id].
 */
internal fun focusItems(
    id: String,
    pairs: List<Collection<String>>,
    pending: List<String>,
    showPending: Boolean,
    days: List<List<String>>,
    cards: Boolean,
): FocusItems? {
    pairs.forEachIndexed { index, ids -> if (id in ids) return FocusItems(index, index) }
    var at = pairs.size
    if (pending.isNotEmpty()) {
        val heading = at
        at += 1
        if (showPending) {
            val index = pending.indexOf(id)
            if (index >= 0) return FocusItems(heading, if (cards) at else at + index)
            at += if (cards) 1 else pending.size
        } else if (id in pending) {
            return FocusItems(heading, heading)
        }
    }
    days.forEach { day ->
        val index = day.indexOf(id)
        if (cards) {
            if (index >= 0) return FocusItems(at, at)
            at += 1
        } else {
            if (index >= 0) return FocusItems(at, at + 1 + index)
            at += 1 + day.size
        }
    }
    return null
}

/** The brief accent wash on a transaction the register was opened at, so the eye lands on it. */
@Stable
internal class FocusFlash {
    var id by mutableStateOf<String?>(null)
        private set
    internal val level = Animatable(0f)

    fun start(id: String) {
        this.id = id
    }

    internal fun done() {
        id = null
    }
}

@Composable
internal fun rememberFocusFlash(): FocusFlash {
    val flash = remember { FocusFlash() }
    val animate = Metro.animations
    val id = flash.id
    LaunchedEffect(flash, id) {
        if (id == null) return@LaunchedEffect
        if (animate) {
            flash.level.snapTo(0f)
            flash.level.animateTo(1f, tween(MetroMotion.QUICK_MS, easing = MetroMotion.EaseOut))
            delay(FLASH_HOLD_MS)
            flash.level.animateTo(0f, tween(FLASH_FADE_MS.toInt(), easing = MetroMotion.Standard))
        } else {
            flash.level.snapTo(1f)
            delay(FLASH_HOLD_MS + FLASH_FADE_MS)
            flash.level.snapTo(0f)
        }
        flash.done()
    }
    return flash
}

/** Washes row [id] in the accent while [flash] is on it, edge to edge as a ledger band runs. */
@Composable
internal fun Modifier.focusFlash(
    flash: FocusFlash,
    id: String,
): Modifier {
    if (flash.id != id) return this
    val tint = Metro.accent.text
    return drawBehind {
        val level = flash.level.value
        if (level == 0f) return@drawBehind
        val gutter = MetroDimens.Gutter.toPx()
        drawRect(tint.copy(alpha = FLASH_ALPHA * level), Offset(-gutter, 0f), Size(size.width + 2 * gutter, size.height))
    }
}

private const val FLASH_ALPHA = 0.24f
private const val FLASH_HOLD_MS = 900L
private const val FLASH_FADE_MS = 700L
