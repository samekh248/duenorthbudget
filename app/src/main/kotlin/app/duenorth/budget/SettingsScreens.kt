package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.components.MetroOutlineButton
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Accent
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens

enum class AdvancedConfirm {
    Rebuild,
    Repair,
    ResetSync,
}

@Composable
fun SettingsScreen(
    instances: List<SavedInstance>,
    activeInstance: SavedInstance?,
    instanceReachable: Boolean?,
    signedIn: Boolean,
    serverError: String?,
    budgets: List<BudgetSummary>,
    openBudgetId: String?,
    themeMode: String,
    accentId: String,
    formatPrefs: FormatPrefs,
    cloudFileId: String?,
    createError: String?,
    encryptionError: String?,
    openIdAvailable: Boolean,
    onConnect: (String, String) -> Unit,
    onBeginOpenId: (String) -> Unit,
    onMakeActive: (String) -> Unit,
    onRemoveInstance: (String) -> Unit,
    onSignOut: () -> Unit,
    onOpenBudget: (String) -> Unit,
    onCreateBudget: (String, String) -> Unit,
    onTheme: (String) -> Unit,
    onAccent: (String) -> Unit,
    launcherIcon: LauncherIcon = LauncherIcon.TILE,
    onLauncherIcon: (LauncherIcon) -> Unit = {},
    onPayees: () -> Unit,
    onDateFormat: (String) -> Unit,
    onNumberFormat: (String) -> Unit,
    onFirstDay: (Int) -> Unit,
    onHideFraction: (Boolean) -> Unit,
    onExperimental: (Boolean) -> Unit,
    onGoalTemplates: (Boolean) -> Unit = {},
    onLearning: (Boolean) -> Unit = {},
    onExport: () -> Unit,
    onSetEncryptionPassword: (String) -> Unit,
    onChangeEncryptionPassword: (String, String) -> Unit,
    onRemoveEncryptionPassword: (String) -> Unit,
    onRequestAdvanced: (AdvancedConfirm) -> Unit,
    modifier: Modifier = Modifier,
    defaultBudgetName: String = "",
) {
    val budgetOpen = !openBudgetId.isNullOrBlank()
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter)
            .verticalScroll(rememberScrollState())
            .testTag("settings-screen"),
        verticalArrangement = Arrangement.spacedBy(MetroDimens.Grid),
    ) {
        MetroText("settings", Metro.typography.header)
        InstanceSection(
            instances = instances,
            activeInstance = activeInstance,
            instanceReachable = instanceReachable,
            signedIn = signedIn,
            serverError = serverError,
            openIdAvailable = openIdAvailable,
            onConnect = onConnect,
            onBeginOpenId = onBeginOpenId,
            onMakeActive = onMakeActive,
            onRemoveInstance = onRemoveInstance,
            onSignOut = onSignOut,
        )
        BudgetsSection(
            budgets = budgets,
            openBudgetId = openBudgetId,
            createError = createError,
            defaultName = defaultBudgetName,
            onOpen = onOpenBudget,
            onCreate = onCreateBudget,
        )
        AppearanceSection(
            themeMode = themeMode,
            accentId = accentId,
            onTheme = onTheme,
            onAccent = onAccent,
        )
        LauncherIconSection(launcherIcon, onLauncherIcon)
        SettingsActionRow("payees", "settings-payees", onPayees)
        if (budgetOpen) {
            FormattingSection(
                prefs = formatPrefs,
                onDateFormat = onDateFormat,
                onNumberFormat = onNumberFormat,
                onFirstDay = onFirstDay,
                onHideFraction = onHideFraction,
            )
            MethodSection(formatPrefs.budgetType)
            LearningSection(formatPrefs.learnCategories, onLearning)
            ExperimentalSection(formatPrefs.experimental, onExperimental, formatPrefs.goalTemplates, onGoalTemplates)
            IdsSection(openBudgetId.orEmpty(), cloudFileId)
            SettingsActionRow("export", "settings-export", onExport)
            EncryptionSection(
                error = encryptionError,
                onSet = onSetEncryptionPassword,
                onChange = onChangeEncryptionPassword,
                onRemove = onRemoveEncryptionPassword,
            )
            AdvancedSection(onRequestAdvanced)
        }
    }
}

@Composable
fun ConfirmInstanceSwitchScreen(
    leaving: String,
    opening: String,
    onOpen: () -> Unit,
    onStay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter)
            .testTag("confirm-instance-switch"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MetroText("open $opening", Metro.typography.header)
        MetroText("leave $leaving", Metro.typography.subheader)
        MetroOutlineButton("open", onClick = onOpen)
        MetroOutlineButton("stay", onClick = onStay)
    }
}

@Composable
fun ConfirmAdvancedScreen(
    kind: AdvancedConfirm,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title =
        when (kind) {
            AdvancedConfirm.Rebuild -> "rebuild totals"
            AdvancedConfirm.Repair -> "repair splits"
            AdvancedConfirm.ResetSync -> "reset sync"
        }
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter)
            .testTag("confirm-advanced"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MetroText(title, Metro.typography.header)
        MetroText("this changes the budget on this phone", Metro.typography.subheader)
        MetroOutlineButton("confirm", Modifier.testTag("advanced-confirm"), onClick = onConfirm)
        MetroOutlineButton("stay", Modifier.testTag("advanced-cancel"), onClick = onCancel)
    }
}

@Composable
private fun InstanceSection(
    instances: List<SavedInstance>,
    activeInstance: SavedInstance?,
    instanceReachable: Boolean?,
    signedIn: Boolean,
    serverError: String?,
    openIdAvailable: Boolean,
    onConnect: (String, String) -> Unit,
    onBeginOpenId: (String) -> Unit,
    onMakeActive: (String) -> Unit,
    onRemoveInstance: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    var address by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    SettingsHeading("instance")
    val status =
        when {
            activeInstance == null -> "not connected"
            instanceReachable == false -> SyncCopy.NOT_REACHED
            else -> activeInstance.label ?: activeInstance.address
        }
    MetroText(
        status,
        Metro.typography.subheader,
        Modifier.testTag("instance-status"),
        color = if (activeInstance != null) Metro.accent.text else Metro.colors.foreground,
    )
    instances.forEach { instance ->
        val active = instance.id == activeInstance?.id
        val label = instance.label ?: instance.address
        Column(Modifier.padding(vertical = MetroDimens.Row)) {
            MetroText(
                if (active) "$label  active" else label,
                Metro.typography.subheader,
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = MetroDimens.TouchTarget)
                    .testTag("instance-${instance.id}"),
                color = if (active) Metro.accent.text else Metro.colors.foreground,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter)) {
                if (!active) {
                    MetroOutlineButton("make active", Modifier.testTag("instance-activate-${instance.id}")) {
                        onMakeActive(instance.id)
                    }
                }
                MetroOutlineButton("remove", Modifier.testTag("instance-remove-${instance.id}")) {
                    onRemoveInstance(instance.id)
                }
            }
        }
    }
    MetroField(address, { address = it }, "address", Modifier.testTag("instance-address"))
    MetroField(password, { password = it }, "password", Modifier.testTag("instance-password"), conceal = true)
    if (serverError != null) {
        MetroText(serverError, Metro.typography.body, color = Metro.accent.text)
    }
    MetroButton("sign in", Modifier.testTag("instance-connect")) { onConnect(address, password) }
    if (openIdAvailable) {
        MetroOutlineButton("sign in with browser", Modifier.testTag("instance-openid")) {
            onBeginOpenId(address)
        }
    }
    if (signedIn || activeInstance != null) {
        MetroOutlineButton("sign out", Modifier.testTag("instance-sign-out"), onClick = onSignOut)
    }
}

@Composable
private fun BudgetsSection(
    budgets: List<BudgetSummary>,
    openBudgetId: String?,
    createError: String?,
    defaultName: String,
    onOpen: (String) -> Unit,
    onCreate: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf(defaultName) }
    SettingsHeading("budgets")
    budgets.forEach { budget ->
        val open = budget.id == openBudgetId
        MetroText(
            if (open) "${budget.name}  open" else budget.name,
            Metro.typography.subheader,
            Modifier
                .fillMaxWidth()
                .heightIn(min = MetroDimens.TouchTarget)
                .metroPress { onOpen(budget.id) }
                .padding(vertical = MetroDimens.Row)
                .testTag("settings-budget-${budget.id}"),
            color = if (open) Metro.accent.text else Metro.colors.foreground,
        )
    }
    MetroField(name, { name = it }, "name", Modifier.testTag("settings-create-name"))
    if (createError != null) {
        MetroText(createError, Metro.typography.body, Modifier.testTag("settings-create-error"), color = Metro.accent.text)
    }
    MetroOutlineButton("create budget", Modifier.testTag("settings-create")) {
        onCreate(name, "USD")
        name = defaultName
    }
}

@Composable
private fun AppearanceSection(
    themeMode: String,
    accentId: String,
    onTheme: (String) -> Unit,
    onAccent: (String) -> Unit,
) {
    SettingsHeading("appearance")
    ThemeMode.entries.forEach { mode ->
        val selected = ThemeMode.fromStored(themeMode) == mode
        val label =
            when (mode) {
                ThemeMode.SYSTEM -> "follow phone"
                ThemeMode.LIGHT -> "light"
                ThemeMode.DARK -> "dark"
            }
        MetroText(
            label,
            Metro.typography.subheader,
            Modifier
                .fillMaxWidth()
                .heightIn(min = MetroDimens.TouchTarget)
                .metroPress { onTheme(mode.stored()) }
                .padding(vertical = MetroDimens.Row)
                .testTag("settings-theme-${mode.stored()}"),
            color = if (selected) Metro.accent.text else Metro.colors.foreground,
        )
    }
    MetroText(
        "accent",
        Metro.typography.caption,
        Modifier.padding(top = MetroDimens.Gutter, bottom = MetroDimens.Gutter),
        color = Metro.colors.secondary,
    )
    val accents = Accent.entries
    Column(verticalArrangement = Arrangement.spacedBy(MetroDimens.Gutter)) {
        accents.chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(MetroDimens.Gutter)) {
                row.forEach { accent ->
                    val fill = accent.fill(Metro.colors.isDark)
                    Box(
                        Modifier
                            .size(52.dp)
                            .background(fill)
                            .then(
                                if (accent.id == accentId) {
                                    Modifier.border(3.dp, Metro.colors.foreground)
                                } else {
                                    Modifier
                                },
                            ).metroPress { onAccent(accent.id) }
                            .testTag("settings-accent-${accent.id}"),
                    )
                }
            }
        }
    }
}

@Composable
private fun LauncherIconSection(
    current: LauncherIcon,
    onChoose: (LauncherIcon) -> Unit,
) {
    MetroText(
        "app icon",
        Metro.typography.caption,
        Modifier.padding(top = MetroDimens.Gutter),
        color = Metro.colors.secondary,
    )
    LauncherIcon.entries.forEach { icon ->
        ChoiceRow(icon.label, current == icon, "settings-icon-${icon.name.lowercase()}") { onChoose(icon) }
    }
}

@Composable
private fun FormattingSection(
    prefs: FormatPrefs,
    onDateFormat: (String) -> Unit,
    onNumberFormat: (String) -> Unit,
    onFirstDay: (Int) -> Unit,
    onHideFraction: (Boolean) -> Unit,
) {
    SettingsHeading("formatting", tag = "settings-formatting")
    MetroText("date format", Metro.typography.caption, color = Metro.colors.secondary)
    DateFormats.choices.forEach { choice ->
        ChoiceRow(choice, prefs.dateFormat == choice, "settings-date-$choice") { onDateFormat(choice) }
    }
    MetroText("number format", Metro.typography.caption, color = Metro.colors.secondary)
    listOf(
        FormatOptions.NUMBER_COMMA_DOT,
        FormatOptions.NUMBER_DOT_COMMA,
        FormatOptions.NUMBER_SPACE_DOT,
        FormatOptions.NUMBER_COMMA_DOT_NO_GROUP,
    ).forEach { choice ->
        ChoiceRow(choice, prefs.numberFormat == choice, "settings-number-$choice") { onNumberFormat(choice) }
    }
    MetroText("first day of week", Metro.typography.caption, color = Metro.colors.secondary)
    val days = listOf("sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday")
    days.forEachIndexed { index, label ->
        ChoiceRow(label, prefs.firstDayOfWeekIdx == index, "settings-first-day-$index") { onFirstDay(index) }
    }
    ChoiceRow(
        if (prefs.hideFraction) "hide decimals  on" else "hide decimals",
        prefs.hideFraction,
        "settings-hide-fraction",
    ) { onHideFraction(!prefs.hideFraction) }
}

@Composable
private fun MethodSection(budgetType: String) {
    SettingsHeading("method", tag = "settings-method")
    val label = if (budgetType == "tracking") "tracking" else "envelope"
    MetroText(label, Metro.typography.subheader, Modifier.testTag("settings-method-value"))
}

/** Actual's "learn categories": a payee's usual category becomes a rule. */
@Composable
private fun LearningSection(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    SettingsHeading("rules", tag = "settings-rules")
    ChoiceRow(
        if (enabled) "learn categories  on" else "learn categories",
        enabled,
        "settings-learning",
    ) { onToggle(!enabled) }
}

@Composable
private fun ExperimentalSection(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    templates: Boolean,
    onTemplates: (Boolean) -> Unit,
) {
    SettingsHeading("experimental", tag = "settings-experimental")
    ChoiceRow(
        if (enabled) "experimental features  on" else "experimental features",
        enabled,
        "settings-experimental-toggle",
    ) { onToggle(!enabled) }
    // Actual's goal templates feature flag; it only counts while experimental features are on.
    if (enabled) {
        ChoiceRow(
            if (templates) "budget templates  on" else "budget templates",
            templates,
            "settings-templates",
        ) { onTemplates(!templates) }
    }
}

@Composable
private fun IdsSection(
    budgetId: String,
    cloudFileId: String?,
) {
    SettingsHeading("ids", tag = "settings-ids")
    MetroText("budget $budgetId", Metro.typography.body, Modifier.testTag("settings-budget-id"))
    if (!cloudFileId.isNullOrBlank()) {
        MetroText("sync $cloudFileId", Metro.typography.body, Modifier.testTag("settings-cloud-id"))
    }
}

@Composable
private fun EncryptionSection(
    error: String?,
    onSet: (String) -> Unit,
    onChange: (String, String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    SettingsHeading("encryption", tag = "settings-encryption")
    MetroField(password, { password = it }, "new password", Modifier.testTag("encryption-password"), conceal = true)
    MetroOutlineButton("set password", Modifier.testTag("encryption-set")) {
        onSet(password)
        password = ""
    }
    MetroField(current, { current = it }, "current password", Modifier.testTag("encryption-current"), conceal = true)
    MetroField(next, { next = it }, "replacement", Modifier.testTag("encryption-next"), conceal = true)
    MetroOutlineButton("change password", Modifier.testTag("encryption-change")) {
        onChange(current, next)
        current = ""
        next = ""
    }
    MetroOutlineButton("remove password", Modifier.testTag("encryption-remove")) {
        onRemove(current.ifBlank { password })
        current = ""
        password = ""
    }
    if (error != null) {
        MetroText(error, Metro.typography.body, color = Metro.accent.text)
    }
}

@Composable
private fun AdvancedSection(onRequest: (AdvancedConfirm) -> Unit) {
    SettingsHeading("advanced", tag = "settings-advanced")
    MetroOutlineButton("rebuild totals", Modifier.testTag("settings-rebuild")) {
        onRequest(AdvancedConfirm.Rebuild)
    }
    MetroOutlineButton("repair splits", Modifier.testTag("settings-repair")) {
        onRequest(AdvancedConfirm.Repair)
    }
    MetroOutlineButton("reset sync", Modifier.testTag("settings-reset-sync")) {
        onRequest(AdvancedConfirm.ResetSync)
    }
}

@Composable
private fun SettingsHeading(
    text: String,
    tag: String? = null,
) {
    MetroText(
        text,
        Metro.typography.groupHeader,
        Modifier
            .padding(top = MetroDimens.Gutter)
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
