package app.duenorth.budget

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroButtonGrid
import app.duenorth.budget.design.components.ScreenAction

/**
 * A page's own commands, for a page whose bar depends on what it is showing (a step, a draft): on the application
 * bar in the app, in place of the route's, and as body buttons tagged with each action's id when the page stands
 * on its own. Commands for the whole page belong on the bar, never in the body.
 */
@Composable
fun PageBar(
    actions: List<ScreenAction>,
    modifier: Modifier = Modifier,
) {
    if (formSaveOnBar()) {
        // The bar keeps its buttons while only the work behind them changes, so it does not flicker on each keystroke.
        val latest = rememberUpdatedState(actions.associate { it.id to it.run })
        val shape = actions.map { listOf(it.id, it.label, it.glyph, it.primary) }
        val bar =
            remember(shape) {
                actions.map { action -> action.copy(run = { latest.value[action.id]?.invoke() }) }
            }
        RememberBarOverride(bar)
    } else if (actions.isNotEmpty()) {
        MetroButtonGrid(modifier.padding(top = 12.dp)) {
            actions.forEach { action -> MetroButton(action.label, Modifier.testTag(action.id), onClick = action.run) }
        }
    }
}
