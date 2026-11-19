package app.duenorth.budget.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import app.duenorth.budget.design.MetroMotion
import app.duenorth.budget.design.theme.Metro

/** One bar button as the bar shows it: [visible] eases it in when its screen arrives and out when it leaves. */
internal class AppBarSlot(
    var action: ScreenAction,
    val visible: MutableTransitionState<Boolean>,
) {
    /** Still on its way out: drawn, but no longer the screen's, so a tap does nothing. */
    val leaving: Boolean get() = !visible.targetState

    val gone: Boolean get() = !visible.targetState && !visible.currentState && visible.isIdle
}

/**
 * The bar's buttons across screens. A button both screens share stays, sliding to its new place; a new screen's
 * own buttons grow in; the old screen's shrink out where they stood, kept in the row until they have gone.
 */
internal class AppBarSlots {
    private var slots: List<AppBarSlot> = emptyList()
    private var started = false

    /** The slots to draw for [actions], in the bar's order, with any still leaving left where they were. */
    fun update(
        actions: List<ScreenAction>,
        animate: Boolean,
    ): List<AppBarSlot> {
        if (!animate) {
            // Animations off: the bar trades its buttons at once, as it always did.
            val kept = slots.filter { it.visible.targetState }.associateBy { it.action.id }
            slots =
                actions.map { action -> kept[action.id]?.also { it.action = action } ?: AppBarSlot(action, MutableTransitionState(true)) }
            started = true
            return slots
        }
        val ids = actions.map { it.id }.toSet()
        val known = slots.filterNot { it.gone }.associateBy { it.action.id }
        val next =
            actions
                .map { action ->
                    known[action.id]?.also {
                        it.action = action
                        if (!it.visible.targetState) it.visible.targetState = true
                    } ?: AppBarSlot(action, MutableTransitionState(!started).apply { targetState = true })
                }.toMutableList()
        slots.forEachIndexed { index, slot ->
            if (slot.action.id !in ids && !slot.gone) {
                if (slot.visible.targetState) slot.visible.targetState = false
                next.add(minOf(index, next.size), slot)
            }
        }
        slots = next
        started = true
        // Read here so the bar recomposes, and drops a slot, once its exit has run.
        return slots.filterNot { it.gone }
    }
}

@Composable
internal fun rememberAppBarSlots(actions: List<ScreenAction>): List<AppBarSlot> {
    val slots = remember { AppBarSlots() }
    return slots.update(actions, Metro.animations)
}

/** Each of [slots] in its own key, growing in and shrinking out with a short fade and scale. */
@Composable
internal fun RowScope.AppBarSlotRow(
    slots: List<AppBarSlot>,
    content: @Composable (AppBarSlot) -> Unit,
) {
    val animate = Metro.animations
    slots.forEach { slot ->
        key(slot.action.id) {
            AnimatedVisibility(
                visibleState = slot.visible,
                enter = if (animate) AppBarEnter else EnterTransition.None,
                exit = if (animate) AppBarExit else ExitTransition.None,
                label = "app bar ${slot.action.id}",
            ) {
                content(slot)
            }
        }
    }
}

/** How small a button starts as it arrives, and ends as it leaves. */
private const val SLOT_SCALE = 0.6f

// The incoming button waits for the outgoing one to fade most of the way, so the two never read as one blur.
private val AppBarEnter: EnterTransition =
    expandHorizontally(tween(MetroMotion.SHORT_MS, easing = MetroMotion.Standard), Alignment.CenterHorizontally, clip = false) +
        fadeIn(tween(MetroMotion.QUICK_MS, delayMillis = MetroMotion.QUICK_MS / 2)) +
        scaleIn(tween(MetroMotion.SHORT_MS, delayMillis = MetroMotion.QUICK_MS / 2, easing = MetroMotion.EaseOut), SLOT_SCALE)

private val AppBarExit: ExitTransition =
    shrinkHorizontally(tween(MetroMotion.SHORT_MS, easing = MetroMotion.Standard), Alignment.CenterHorizontally, clip = false) +
        fadeOut(tween(MetroMotion.QUICK_MS)) +
        scaleOut(tween(MetroMotion.QUICK_MS), SLOT_SCALE)
