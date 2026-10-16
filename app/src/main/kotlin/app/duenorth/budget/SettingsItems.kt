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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.BudgetSummary
import app.duenorth.budget.core.DateFormats
import app.duenorth.budget.core.FormatOptions
import app.duenorth.budget.core.FormatPrefs
import app.duenorth.budget.core.SavedInstance
import app.duenorth.budget.core.SyncCopy
import app.duenorth.budget.core.ThemeMode
import app.duenorth.budget.design.components.AppGlyph
import app.duenorth.budget.design.components.AppGlyphIcon
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroButtonGrid
import app.duenorth.budget.design.components.MetroComboBox
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Accent
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens

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
) {
    val open = budgets.find { it.id == openBudgetId }
    if (open == null) {
        SettingsHeading("budgets", first = true)
        MetroText(SettingsCopy.NO_BUDGET, Metro.typography.subheader, Modifier.testTag("settings-open-budget"))
    } else {
        SettingsHeading("open budget", tag = "settings-method", first = true)
        MetroText(open.name, Metro.typography.subheader, Modifier.testTag("settings-open-budget"), color = Metro.accent.text)
        MetroText(
            SettingsCopy.method(budgetType),
            Metro.typography.body,
            Modifier.testTag("settings-method-value"),
            color = Metro.colors.secondary,
        )
    }
    val others = budgets.filter { it.id != openBudgetId }
    if (others.isNotEmpty()) {
        if (open != null) SettingsHeading("other budgets")
        others.forEach { budget ->
            SettingsActionRow(budget.name, "settings-budget-${budget.id}") { onOpen(budget.id) }
        }
    }
    if (open != null) {
        SettingsHeading("payees")
        SettingsActionRow("rename and merge payees", "settings-payees", onPayees)
        // Actual's "learn categories": a payee's usual category becomes a rule.
        SettingsHeading("rules", tag = "settings-rules")
        ChoiceRow(
            if (learning) "learn categories  on" else "learn categories",
            learning,
            "settings-learning",
        ) { onLearning(!learning) }
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
) {
    SettingsHeading("this phone syncs with", first = true)
    MetroText(
        activeInstance?.let { it.label ?: it.address } ?: SettingsCopy.NOT_CONNECTED,
        Metro.typography.subheader,
        Modifier.testTag("instance-status"),
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
        MetroButtonGrid(Modifier.padding(top = MetroDimens.Row)) {
            MetroButton("remove", Modifier.testTag("instance-remove-${activeInstance.id}")) { onRemove(activeInstance.id) }
        }
    }
    val others = instances.filter { it.id != activeInstance?.id }
    if (others.isNotEmpty()) {
        SettingsHeading("saved servers")
        others.forEach { instance ->
            Column(Modifier.padding(vertical = MetroDimens.Row)) {
                MetroText(
                    instance.label ?: instance.address,
                    Metro.typography.subheader,
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = MetroDimens.TouchTarget)
                        .testTag("instance-${instance.id}"),
                )
                MetroButtonGrid {
                    MetroButton("make active", Modifier.testTag("instance-activate-${instance.id}")) { onMakeActive(instance.id) }
                    MetroButton("remove", Modifier.testTag("instance-remove-${instance.id}")) { onRemove(instance.id) }
                }
            }
        }
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
    experimental: Boolean,
    goalTemplates: Boolean,
    budgetId: String,
    cloudFileId: String?,
    encrypted: Boolean,
    onExperimental: (Boolean) -> Unit,
    onGoalTemplates: (Boolean) -> Unit,
    onExport: () -> Unit,
    onEncryption: (EncryptionMode) -> Unit,
    onRequest: (AdvancedConfirm) -> Unit,
) {
    SettingsHeading("experimental", tag = "settings-experimental", first = true)
    ChoiceRow(
        if (experimental) "experimental features  on" else "experimental features",
        experimental,
        "settings-experimental-toggle",
    ) { onExperimental(!experimental) }
    // Actual's goal templates feature flag; it only counts while experimental features are on.
    if (experimental) {
        ChoiceRow(
            if (goalTemplates) "budget templates  on" else "budget templates",
            goalTemplates,
            "settings-templates",
        ) { onGoalTemplates(!goalTemplates) }
    }
    SettingsHeading("export")
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
    MetroButtonGrid(Modifier.padding(top = MetroDimens.Row)) {
        if (encrypted) {
            MetroButton(SettingsCopy.CHANGE_PASSWORD, Modifier.testTag("encryption-change")) { onEncryption(EncryptionMode.CHANGE) }
            MetroButton(SettingsCopy.REMOVE_PASSWORD, Modifier.testTag("encryption-remove")) { onEncryption(EncryptionMode.REMOVE) }
        } else {
            MetroButton(SettingsCopy.SET_PASSWORD, Modifier.testTag("encryption-set")) { onEncryption(EncryptionMode.SET) }
        }
    }
    SettingsHeading("repair", tag = "settings-advanced")
    MetroButtonGrid(Modifier.padding(top = MetroDimens.Row)) {
        MetroButton("rebuild totals", Modifier.testTag("settings-rebuild")) { onRequest(AdvancedConfirm.Rebuild) }
        MetroButton("repair splits", Modifier.testTag("settings-repair")) { onRequest(AdvancedConfirm.Repair) }
        MetroButton("reset sync", Modifier.testTag("settings-reset-sync")) { onRequest(AdvancedConfirm.ResetSync) }
    }
}

@Composable
private fun SettingsHeading(
    text: String,
    tag: String? = null,
    first: Boolean = false,
) {
    MetroText(
        text,
        Metro.typography.groupHeader,
        Modifier
            .padding(top = if (first) 0.dp else MetroDimens.Gutter)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        color = Metro.colors.secondary,
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
