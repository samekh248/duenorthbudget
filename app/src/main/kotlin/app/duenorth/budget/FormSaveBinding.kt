package app.duenorth.budget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import app.duenorth.budget.design.components.ScreenAction

class FormSaveHost {
    private val handler = mutableStateOf<(() -> Unit)?>(null)
    private val barOverride = mutableStateOf<List<ScreenAction>?>(null)
    private val actions = mutableMapOf<String, () -> Unit>()

    /** Actions a form's own page (a picker over the form) puts on the bar in place of the route's. */
    val bar: List<ScreenAction>? get() = barOverride.value

    fun overrideBar(actions: List<ScreenAction>?) {
        barOverride.value = actions
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
    DisposableEffect(host, actions) {
        host?.overrideBar(actions)
        onDispose { host?.overrideBar(null) }
    }
}

/** True when the application bar owns save; forms keep an in-body button otherwise. */
@Composable
fun formSaveOnBar(): Boolean = LocalFormSaveHost.current != null

@Composable
fun rememberFormSaveHost(): FormSaveHost = remember { FormSaveHost() }
