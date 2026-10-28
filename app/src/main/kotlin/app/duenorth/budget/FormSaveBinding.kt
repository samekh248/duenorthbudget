package app.duenorth.budget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import app.duenorth.budget.design.components.ScreenAction

class FormSaveHost {
    private val handler = mutableStateOf<(() -> Unit)?>(null)
    private val barOverrides = mutableStateOf<List<Pair<Int, List<ScreenAction>>>>(emptyList())
    private var overrideCount = 0
    private val actions = mutableMapOf<String, () -> Unit>()

    /** Actions a form's own page (a picker over the form) puts on the bar in place of the route's. */
    val bar: List<ScreenAction>? get() = barOverrides.value.maxByOrNull { it.first }?.second

    /** A place in the stack of overrides: one taken later sits above, as a page drawn over another does. */
    fun overrideSlot(): Int = ++overrideCount

    /**
     * [slot]'s actions replace the bar until it passes null. Overrides stack, so a confirmation drawn over a page
     * that has its own override (a delete question over the categories page) hands the bar back when it closes.
     */
    fun overrideBar(
        slot: Int,
        actions: List<ScreenAction>?,
    ) {
        val others = barOverrides.value.filter { it.first != slot }
        barOverrides.value = if (actions == null) others else others + (slot to actions)
    }

    fun bind(save: () -> Unit) {
        handler.value = save
    }

    fun clear() {
        handler.value = null
    }

    fun save() {
        handler.value?.invoke()
    }

    /** A bar action the form itself carries out, such as adding a split line. */
    fun bindAction(
        id: String,
        run: (() -> Unit)?,
    ) {
        if (run == null) actions.remove(id) else actions[id] = run
    }

    fun run(id: String) {
        actions[id]?.invoke()
    }
}

val LocalFormSaveHost = staticCompositionLocalOf<FormSaveHost?> { null }

@Composable
fun RememberFormSave(save: () -> Unit) {
    val host = LocalFormSaveHost.current
    DisposableEffect(host, save) {
        host?.bind(save)
        onDispose { host?.clear() }
    }
}

/** While composed, the bar action [id] runs [run] on the form. */
@Composable
fun RememberFormAction(
    id: String,
    run: () -> Unit,
) {
    val host = LocalFormSaveHost.current
    DisposableEffect(host, id, run) {
        host?.bindAction(id, run)
        onDispose { host?.bindAction(id, null) }
    }
}

/**
 * While composed, [actions] replace the route's application bar: a picker page drawn over a
 * form (spec 013) shows its own done and cancel without leaving the form's route.
 */
@Composable
fun RememberBarOverride(actions: List<ScreenAction>) {
    val host = LocalFormSaveHost.current
    val slot = remember(host) { host?.overrideSlot() ?: 0 }
    DisposableEffect(host, actions) {
        host?.overrideBar(slot, actions)
        onDispose { host?.overrideBar(slot, null) }
    }
}

/** True when the application bar owns save; forms keep an in-body button otherwise. */
@Composable
fun formSaveOnBar(): Boolean = LocalFormSaveHost.current != null

@Composable
fun rememberFormSaveHost(): FormSaveHost = remember { FormSaveHost() }
