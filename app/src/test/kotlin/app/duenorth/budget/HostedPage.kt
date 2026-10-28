package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.duenorth.budget.design.components.MetroAppBar

/** A page as the app hosts it, for a snapshot: its own commands on the application bar under it. */
@Composable
internal fun HostedPage(content: @Composable () -> Unit) {
    val host = rememberFormSaveHost()
    Column(Modifier.width(390.dp).height(780.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            CompositionLocalProvider(LocalFormSaveHost provides host) { content() }
        }
        MetroAppBar(host.bar.orEmpty(), true, {}, windowInsets = WindowInsets(bottom = 0.dp))
    }
}
