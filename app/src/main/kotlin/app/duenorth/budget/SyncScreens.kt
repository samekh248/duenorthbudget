package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.BudgetConflict
import app.duenorth.budget.core.CategoryChoice
import app.duenorth.budget.core.RemoteFile
import app.duenorth.budget.core.SyncCopy
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroButtonGrid
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens

@Composable
fun SyncProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
) {
    val fraction = (progress ?: 0.08f).coerceIn(0.08f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .semantics { contentDescription = SyncCopy.SYNCING }
            .testTag("sync-progress"),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(4.dp)
                .background(Metro.accent.fill),
        )
    }
}

@Composable
fun ServerScreen(
    address: String,
    signedIn: Boolean,
    files: List<RemoteFile>,
    status: String,
    error: String?,
    conflictCount: Int = 0,
    onConflicts: () -> Unit = {},
    onConnect: (String, String) -> Unit,
    onOpen: (RemoteFile) -> Unit,
    onReplace: (RemoteFile) -> Unit,
    onUpload: () -> Unit,
    onSync: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var server by remember(address) { mutableStateOf(address) }
    var password by remember { mutableStateOf("") }
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetroText("server", Metro.typography.header)
        if (status.isNotEmpty()) {
            MetroText(status, Metro.typography.body, Modifier.testTag("sync-status"))
        }
        if (!signedIn) {
            MetroField(server, { server = it }, "address", Modifier.testTag("server-address"))
            MetroField(password, { password = it }, "sign in", Modifier.testTag("server-password"), conceal = true)
            if (error != null) MetroText(error, Metro.typography.body, color = Metro.accent.text)
            MetroButton("connect", Modifier.testTag("server-connect")) { onConnect(server, password) }
        } else {
            if (error != null) MetroText(error, Metro.typography.body, color = Metro.accent.text)
            if (files.isEmpty()) {
                MetroText(SyncCopy.NO_SERVER_FILES, Metro.typography.body)
            }
            LazyColumn(Modifier.weight(1f)) {
                items(files, key = { it.fileId }) { file ->
                    Column(Modifier.fillMaxWidth().padding(vertical = MetroDimens.Row)) {
                        MetroText(
                            file.name,
                            Metro.typography.subheader,
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = MetroDimens.TouchTarget)
                                .metroPress { onOpen(file) }
                                .testTag("remote-${file.fileId}"),
                        )
                        MetroText(
                            "replace ${file.name}",
                            Metro.typography.caption,
                            Modifier.metroPress { onReplace(file) }.padding(bottom = MetroDimens.Gutter),
                            color = Metro.colors.secondary,
                        )
                    }
                }
            }
            MetroButtonGrid {
                MetroButton("sync now", onClick = onSync)
                MetroButton("send budget", onClick = onUpload)
                if (conflictCount > 0) MetroButton("conflicts", onClick = onConflicts)
                MetroButton("sign out", onClick = onSignOut)
            }
        }
    }
}

@Composable
fun BudgetPasswordScreen(
    error: String?,
    askEachTime: Boolean,
    onAskEachTime: (Boolean) -> Unit,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var password by remember { mutableStateOf("") }
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetroText("budget password", Metro.typography.header)
        MetroField(password, { password = it }, "password", Modifier.testTag("budget-password"), conceal = true)
        if (error != null) {
            MetroText(error, Metro.typography.body, Modifier.testTag("password-error"), color = Metro.accent.text)
        }
        val askLabel = if (askEachTime) "ask each time" else "ask once"
        MetroText(
            askLabel,
            Metro.typography.subheader,
            Modifier
                .heightIn(min = MetroDimens.TouchTarget)
                .metroPress { onAskEachTime(!askEachTime) }
                .testTag("ask-each-time"),
            color = if (askEachTime) Metro.accent.text else Metro.colors.foreground,
        )
        val unlock = {
            onSubmit(password)
            password = ""
        }
        RememberFormSave(unlock)
        if (!formSaveOnBar()) {
            MetroButton("open", Modifier.testTag("budget-unlock"), onClick = unlock)
        } else {
            Box(Modifier.testTag("budget-unlock")) {}
        }
    }
}

@Composable
fun ConflictScreen(
    conflicts: List<BudgetConflict>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter),
    ) {
        MetroText("conflicts", Metro.typography.header)
        LazyColumn(Modifier.weight(1f)) {
            items(conflicts, key = { it.id }) { conflict ->
                Column(Modifier.padding(vertical = 12.dp)) {
                    MetroText(conflict.label, Metro.typography.subheader)
                    MetroText(
                        "phone ${conflict.localText}" + if (conflict.phoneKept) " · kept" else "",
                        Metro.typography.body,
                        Modifier.testTag("conflict-local-${conflict.id}"),
                    )
                    MetroText(
                        "server ${conflict.remoteText}" + if (conflict.phoneKept) "" else " · kept",
                        Metro.typography.body,
                        Modifier.testTag("conflict-remote-${conflict.id}"),
                    )
                }
            }
        }
    }
}

@Composable
fun AssignScreen(
    categories: List<CategoryChoice>,
    error: String?,
    onAssign: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefs = LocalFormatPrefs.current
    var selected by remember(categories) { mutableStateOf(categories.firstOrNull()?.id.orEmpty()) }
    var amount by remember { mutableStateOf("") }
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetroText("assign", Metro.typography.header)
        MetroField(amount, { amount = it }, "amount", Modifier.testTag("assign-amount"))
        if (error != null) MetroText(error, Metro.typography.body, color = Metro.accent.text)
        LazyColumn(Modifier.weight(1f)) {
            items(categories, key = { it.id }) { category ->
                MetroText(
                    category.name,
                    Metro.typography.subheader,
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = MetroDimens.TouchTarget)
                        .metroPress { selected = category.id }
                        .padding(vertical = MetroDimens.Row),
                    color = if (category.id == selected) Metro.accent.text else Metro.colors.foreground,
                )
            }
        }
        RememberFormSave { onAssign(selected, prefs.readAmount(amount)) }
        if (!formSaveOnBar()) {
            MetroButton("assign", Modifier.testTag("assign-save")) { onAssign(selected, prefs.readAmount(amount)) }
        } else {
            Box(Modifier.testTag("assign-save")) {}
        }
    }
}

@Composable
fun ConfirmReplaceScreen(
    serverName: String,
    phoneName: String,
    onReplace: () -> Unit,
    onKeep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MetroText("replace $serverName", Metro.typography.header)
        MetroText("with $phoneName", Metro.typography.subheader)
        MetroButtonGrid {
            MetroButton("replace", onClick = onReplace)
            MetroButton("keep", onClick = onKeep)
        }
    }
}

/** Words on the budget picker's replace confirmation. */
object ReplaceBudgetCopy {
    const val USE = "use"
    const val KEEP = "keep"
    const val LOST = "changes that never synced to a server are lost"

    fun use(name: String) = "use $name"

    fun clears(names: List<String>) = "clears ${names(names)} from this phone"

    /** The caption on the picker while the phone holds a budget. */
    fun replaces(names: List<String>) = "picking one replaces ${names(names)} on this phone"

    private fun names(names: List<String>): String =
        when (names.size) {
            0 -> ""
            1 -> names.single()
            else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
        }
}

/** A server budget was picked while the phone holds others: names each budget that gets cleared. */
@Composable
fun ConfirmReplaceBudgetScreen(
    opening: String,
    clearing: List<String>,
    onUse: () -> Unit,
    onKeep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter)
            .testTag("confirm-replace-budget"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MetroText(ReplaceBudgetCopy.use(opening), Metro.typography.header)
        MetroText(ReplaceBudgetCopy.clears(clearing), Metro.typography.subheader, Modifier.testTag("replace-budget-clears"))
        MetroText(ReplaceBudgetCopy.LOST, Metro.typography.body, color = Metro.colors.secondary)
        MetroButtonGrid {
            MetroButton(ReplaceBudgetCopy.USE, Modifier.testTag("replace-budget-use"), onClick = onUse)
            MetroButton(ReplaceBudgetCopy.KEEP, Modifier.testTag("replace-budget-keep"), onClick = onKeep)
        }
    }
}
