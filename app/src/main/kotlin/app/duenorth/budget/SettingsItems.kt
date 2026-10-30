package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.BackgroundSync
import app.duenorth.budget.core.BudgetSummary
import app.duenorth.budget.core.DateFormats
import app.duenorth.budget.core.FormatOptions
import app.duenorth.budget.core.FormatPrefs
import app.duenorth.budget.core.SavedInstance
import app.duenorth.budget.core.SyncCopy
import app.duenorth.budget.core.ThemeMode
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.AppGlyphIcon
import app.duenorth.budget.design.components.MetroComboBox
import app.duenorth.budget.design.components.MetroContextMenu
import app.duenorth.budget.design.components.MetroMenuItem
import app.duenorth.budget.design.components.MetroSectionHeading
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.SectionSize
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Accent
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import app.duenorth.budget.design.theme.MetroStyle

/** One pivot item's scrolling column; its scroll offset is handed back when it leaves (research R3). */
@Composable
internal fun SettingsItem(
    page: SettingsPage,
    initialScroll: Int,
    onScroll: (SettingsPage, Int) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState(initialScroll)
    val report by rememberUpdatedState(onScroll)
    DisposableEffect(page) {
        onDispose { report(page, scroll.value) }
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(start = MetroDimens.Gutter, end = MetroDimens.Gutter, bottom = MetroDimens.Gutter)
            .testTag("settings-item-${page.name.lowercase()}"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
internal fun BudgetItem(
    budgets: List<BudgetSummary>,
    openBudgetId: String?,
    budgetType: String,
    learning: Boolean,
    onOpen: (String) -> Unit,
    onPayees: () -> Unit,
    onLearning: (Boolean) -> Unit,
    serverName: String? = null,
    signedIn: Boolean = false,
    synced: Boolean = false,
    onServerBudgets: () -> Unit = {},
) {
    val open = budgets.find { it.id == openBudgetId }
    SettingsHeading("open budget", tag = "settings-method", first = true)
    if (open == null) {
        MetroText(SettingsCopy.NO_BUDGET, Metro.typography.subheader, Modifier.testTag("settings-open-budget"))
    } else {
        MetroText(open.name, Metro.typography.subheader, Modifier.testTag("settings-open-budget"), color = Metro.accent.text)
        MetroText(
            SettingsCopy.method(budgetType) + " · " + SettingsCopy.lives(serverName.takeIf { synced }),
            Metro.typography.body,
            Modifier.testTag("settings-method-value"),
            color = Metro.colors.secondary,
        )
    }
    // Every way to another budget sits under one heading: the phone's own, then the server's picker.
    val others = budgets.filter { it.id != openBudgetId }
    val server = serverName?.takeIf { signedIn }
    if (others.isNotEmpty() || serverName != null) {
        SettingsHeading(if (open == null) SettingsCopy.OPEN_A_BUDGET else SettingsCopy.OPEN_ANOTHER, tag = "settings-switch")
    }
    others.forEach { budget ->
        SettingsLinkRow(budget.name, SettingsCopy.ON_PHONE, "settings-budget-${budget.id}") { onOpen(budget.id) }
    }
    if (server != null) {
        SettingsLinkRow(SettingsCopy.serverBudgets(server), SettingsCopy.SERVER_BUDGETS_HINT, "settings-server-budgets", onServerBudgets)
    } else if (serverName != null) {
        MetroText(
            SettingsCopy.signInFor(serverName),
            Metro.typography.body,
            Modifier.padding(top = 4.dp).testTag("settings-server-budgets-signed-out"),
            color = Metro.colors.secondary,
        )
    }
    if (open != null) {
        SettingsHeading("payees")
        SettingsLinkRow(SettingsCopy.RENAME_PAYEES, SettingsCopy.RENAME_PAYEES_HINT, "settings-payees", onPayees)
        // Actual's "learn categories": a payee's usual category becomes a rule.
        SettingsHeading("rules", tag = "settings-rules")
        Column(Modifier.padding(top = MetroDimens.Row).testTag("settings-learning")) {
            SwitchRow(SettingsCopy.LEARN, learning, "settings-learning-switch", onLearning)
            MetroText(
                SettingsCopy.LEARN_HINT,
                Metro.typography.caption,
                Modifier.padding(top = 4.dp),
                color = Metro.colors.secondary,
            )
        }
    }
}

@Composable
internal fun ServerItem(
    instances: List<SavedInstance>,
    activeInstance: SavedInstance?,
    instanceReachable: Boolean?,
    signedIn: Boolean,
    onMakeActive: (String) -> Unit,
    onRemove: (String) -> Unit,
    backgroundSyncHours: Int = BackgroundSync.DEFAULT_HOURS,
    onBackgroundSyncHours: (Int) -> Unit = {},
) {
    // A server's commands open from its row, as the categories page's do.
    var menu by remember { mutableStateOf<Pair<Rect, List<MetroMenuItem>>?>(null) }
    var activeBounds by remember { mutableStateOf(Rect.Zero) }
    SettingsHeading("this phone syncs with", first = true)
    MetroText(
        activeInstance?.let { it.label ?: it.address } ?: SettingsCopy.NOT_CONNECTED,
        Metro.typography.subheader,
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .onGloballyPositioned { activeBounds = it.boundsInWindow() }
            .then(
                if (activeInstance != null) {
                    Modifier.metroPress {
                        menu = activeBounds to listOf(removeItem(activeInstance.id, onRemove))
                    }
                } else {
                    Modifier
                },
            ).testTag("instance-status"),
        color = if (activeInstance != null) Metro.accent.text else Metro.colors.foreground,
    )
    if (activeInstance == null) {
        MetroText(SettingsCopy.NO_SERVER_HINT, Metro.typography.body, color = Metro.colors.secondary)
    } else {
        if (activeInstance.label != null) {
            MetroText(
                activeInstance.address,
                Metro.typography.body,
                Modifier.testTag("instance-address-line"),
                color = Metro.colors.secondary,
            )
        }
        val state =
            when {
                instanceReachable == false -> SyncCopy.NOT_REACHED
                signedIn -> SettingsCopy.SIGNED_IN
                else -> SettingsCopy.SIGNED_OUT
            }
        MetroText(state, Metro.typography.body, Modifier.testTag("instance-state"))
    }
    val others = instances.filter { it.id != activeInstance?.id }
    if (others.isNotEmpty()) {
        SettingsHeading("saved servers")
        others.forEach { instance ->
            var bounds by remember { mutableStateOf(Rect.Zero) }
            MetroText(
                instance.label ?: instance.address,
                Metro.typography.subheader,
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = MetroDimens.TouchTarget)
                    .onGloballyPositioned { bounds = it.boundsInWindow() }
                    .metroPress {
                        menu =
                            bounds to
                            listOf(
                                MetroMenuItem("make active", "instance-activate-${instance.id}") { onMakeActive(instance.id) },
                                removeItem(instance.id, onRemove),
                            )
                    }.padding(vertical = MetroDimens.Row)
                    .testTag("instance-${instance.id}"),
            )
        }
    }
    menu?.let { (anchor, items) -> MetroContextMenu(anchor, items, onDismiss = { menu = null }, tag = "instance-menu") }
    // Only a server can be synced with, so the interval shows once one is active.
    if (activeInstance != null) {
        SettingsHeading("background sync", tag = "settings-background-sync")
        ListPicker(
            BackgroundSync.CHOICES,
            backgroundSyncHours.takeIf { it in BackgroundSync.CHOICES } ?: BackgroundSync.DEFAULT_HOURS,
            "settings-background-sync-picker",
            BackgroundSync::label,
            onBackgroundSyncHours,
        )
        MetroText(SettingsCopy.BACKGROUND_SYNC_HINT, Metro.typography.body, color = Metro.colors.secondary)
    }
}

@Composable
internal fun FormatItem(
    prefs: FormatPrefs,
    onDateFormat: (String) -> Unit,
    onNumberFormat: (String) -> Unit,
    onFirstDay: (Int) -> Unit,
    onHideFraction: (Boolean) -> Unit,
) {
    SettingsHeading("date format", tag = "settings-formatting", first = true)
    ListPicker(DateFormats.choices, prefs.dateFormat, "settings-date", { it }, onDateFormat)
    SettingsHeading("number format")
    ListPicker(
        listOf(
            FormatOptions.NUMBER_COMMA_DOT,
            FormatOptions.NUMBER_DOT_COMMA,
            FormatOptions.NUMBER_SPACE_DOT,
            FormatOptions.NUMBER_COMMA_DOT_NO_GROUP,
        ),
        prefs.numberFormat,
        "settings-number",
        ::numberSample,
        onNumberFormat,
    )
    SettingsHeading("first day of week")
    val days = listOf("sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday")
    ListPicker(days.indices.toList(), prefs.firstDayOfWeekIdx, "settings-first-day", { days[it] }, onFirstDay)
    SettingsHeading("decimals")
    ChoiceRow(
        if (prefs.hideFraction) "hide decimals  on" else "hide decimals",
        prefs.hideFraction,
        "settings-hide-fraction",
    ) { onHideFraction(!prefs.hideFraction) }
}

@Composable
internal fun AppearanceItem(
    themeMode: String,
    accentId: String,
    launcherIcon: LauncherIcon,
    onTheme: (String) -> Unit,
    onAccent: (String) -> Unit,
    onLauncherIcon: (LauncherIcon) -> Unit,
    styleId: String = MetroStyle.Default.id,
    onStyle: (String) -> Unit = {},
) {
    SettingsHeading("theme", first = true)
    ThemeMode.entries.forEach { mode ->
        val label =
            when (mode) {
                ThemeMode.SYSTEM -> "follow phone"
                ThemeMode.LIGHT -> "light"
                ThemeMode.DARK -> "dark"
            }
        ChoiceRow(label, ThemeMode.fromStored(themeMode) == mode, "settings-theme-${mode.stored()}") { onTheme(mode.stored()) }
    }
    SettingsHeading("style")
    MetroStyle.entries.forEach { style ->
        ChoiceRow(style.label, MetroStyle.fromId(styleId) == style, "settings-style-${style.id}") { onStyle(style.id) }
    }
    SettingsHeading("accent")
    Column(
        Modifier.padding(top = MetroDimens.Row),
        verticalArrangement = Arrangement.spacedBy(MetroDimens.Gutter),
    ) {
        Accent.entries.chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter)) {
                row.forEach { accent ->
                    Box(
                        Modifier
                            .size(52.dp)
                            .background(accent.fill(Metro.colors.isDark))
                            .then(if (accent.id == accentId) Modifier.border(3.dp, Metro.colors.foreground) else Modifier)
                            .metroPress { onAccent(accent.id) }
                            .testTag("settings-accent-${accent.id}"),
                    )
                }
            }
        }
    }
    SettingsHeading("app icon")
    LauncherIcon.entries.forEach { icon ->
        ChoiceRow(icon.label, launcherIcon == icon, "settings-icon-${icon.name.lowercase()}") { onLauncherIcon(icon) }
    }
}

@Composable
internal fun AdvancedItem(
    budgetId: String,
    cloudFileId: String?,
    encrypted: Boolean,
    onExport: () -> Unit,
    onEncryption: (EncryptionMode) -> Unit,
    onRequest: (AdvancedConfirm) -> Unit,
) {
    SettingsHeading("export", first = true)
    SettingsActionRow(SettingsCopy.EXPORT_HINT, "settings-export", onExport)
    SettingsHeading("ids", tag = "settings-ids")
    MetroText("budget $budgetId", Metro.typography.body, Modifier.testTag("settings-budget-id"))
    if (!cloudFileId.isNullOrBlank()) {
        MetroText("sync $cloudFileId", Metro.typography.body, Modifier.testTag("settings-cloud-id"))
    }
    SettingsHeading("encryption", tag = "settings-encryption")
    MetroText(
        if (encrypted) SettingsCopy.ENCRYPTED else SettingsCopy.NOT_ENCRYPTED,
        Metro.typography.body,
        Modifier.testTag("settings-encryption-state"),
    )
    if (encrypted) {
        SettingsActionRow(SettingsCopy.CHANGE_PASSWORD, "encryption-change") { onEncryption(EncryptionMode.CHANGE) }
        SettingsActionRow(SettingsCopy.REMOVE_PASSWORD, "encryption-remove") { onEncryption(EncryptionMode.REMOVE) }
    } else {
        SettingsActionRow(SettingsCopy.SET_PASSWORD, "encryption-set") { onEncryption(EncryptionMode.SET) }
    }
    SettingsHeading("repair", tag = "settings-advanced")
    SettingsActionRow("rebuild totals", "settings-rebuild") { onRequest(AdvancedConfirm.Rebuild) }
    SettingsActionRow("repair splits", "settings-repair") { onRequest(AdvancedConfirm.Repair) }
    SettingsActionRow("reset sync", "settings-reset-sync") { onRequest(AdvancedConfirm.ResetSync) }
}

@Composable
private fun SettingsHeading(
    text: String,
    tag: String? = null,
    first: Boolean = false,
) {
    MetroSectionHeading(
        text,
        if (tag != null) Modifier.testTag(tag) else Modifier,
        size = SectionSize.PAGE,
        first = first,
    )
}

@Composable
private fun SettingsActionRow(
    label: String,
    tag: String,
    onClick: () -> Unit,
) {
    MetroText(
        label,
        Metro.typography.subheader,
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .metroPress(onClick)
            .padding(vertical = MetroDimens.Row)
            .testTag(tag),
    )
}

/** A row that opens something, with one line under it saying what. */
@Composable
private fun SettingsLinkRow(
    label: String,
    caption: String,
    tag: String,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .metroPress(onClick)
            .padding(vertical = MetroDimens.Row)
            .testTag(tag),
    ) {
        MetroText(label, Metro.typography.subheader)
        MetroText(caption, Metro.typography.caption, color = Metro.colors.secondary)
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    MetroText(
        label,
        Metro.typography.subheader,
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .metroPress(onClick)
            .padding(vertical = MetroDimens.Row)
            .testTag(tag),
        color = if (selected) Metro.accent.text else Metro.colors.foreground,
    )
}

/** A number format shown as a sample amount, as Actual's own picker does. */
private fun numberSample(format: String): String =
    when (format) {
        FormatOptions.NUMBER_DOT_COMMA -> "1.000,33"
        FormatOptions.NUMBER_SPACE_DOT -> "1\u00A0000.33"
        FormatOptions.NUMBER_COMMA_DOT_NO_GROUP -> "1000.33"
        else -> "1,000.33"
    }

/** Settings' combo boxes: every list here is short enough to unfold in place. */
@Composable
private fun <T> ListPicker(
    choices: List<T>,
    selected: T,
    tag: String,
    label: (T) -> String,
    onPick: (T) -> Unit,
) {
    MetroComboBox(choices, selected, label, onPick, tag, Modifier.padding(top = 4.dp))
}

private fun removeItem(
    id: String,
    onRemove: (String) -> Unit,
) = MetroMenuItem("remove", "instance-remove-$id", accent = true) { onRemove(id) }
