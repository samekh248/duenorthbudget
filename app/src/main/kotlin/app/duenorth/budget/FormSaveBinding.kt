package app.duenorth.budget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

class FormSaveHost {
    private val handler = mutableStateOf<(() -> Unit)?>(null)

    fun bind(save: () -> Unit) {
        handler.value = save
    }

    fun clear() {
        handler.value = null
    }

    fun save() {
        handler.value?.invoke()
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

/** True when the application bar owns save; forms keep an in-body button otherwise. */
@Composable
fun formSaveOnBar(): Boolean = LocalFormSaveHost.current != null

@Composable
fun rememberFormSaveHost(): FormSaveHost = remember { FormSaveHost() }
