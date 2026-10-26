package app.duenorth.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.BudgetSummary
import app.duenorth.budget.core.FormatPrefs
import app.duenorth.budget.core.PhoneSettings
import app.duenorth.budget.core.SavedInstance
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroButtonGrid
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.components.MetroPivot
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.PivotItem
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens
import app.duenorth.budget.design.theme.MetroStyle

enum class AdvancedConfirm {
    Rebuild,
    Repair,
    ResetSync,
}

/** The settings pivot items, in header order (spec 012 FR-002). */
enum class SettingsPage(
    val header: String,
    val needsBudget: Boolean,
) {
    BUDGET("budget", false),
    SERVER("server", false),
    FORMAT("format", true),
    APPEARANCE("appearance", false),
    ADVANCED("advanced", true),
    EXPERIMENTAL("experimental", true),
    ;

    companion object {
        fun shown(budgetOpen: Boolean): List<SettingsPage> = entries.filter { budgetOpen || !it.needsBudget }

        /** [page] when it is in the header row, otherwise the budget item (the budget closed). */
        fun visible(
            budgetOpen: Boolean,
            page: SettingsPage,
        ): SettingsPage = if (page in shown(budgetOpen)) page else BUDGET
    }
}

enum class EncryptionMode {
    SET,
    CHANGE,
    REMOVE,
}

object SettingsCopy {
    const val TITLE = "SETTINGS"
    const val NEW = "new"
    const val PAYEES = "payees"
    const val ADD_SERVER = "add"
    const val SERVER_BUDGETS = "server budgets"
    const val SIGN_OUT = "sign out"
    const val EXPORT = "export"
    const val NOT_CONNECTED = "not connected"
    const val SIGNED_IN = "signed in"
    const val SIGNED_OUT = "signed out"
    const val NO_SERVER_HINT = "add the Actual server you run to sync this phone's budgets"
    const val NO_BUDGET = "no budget open"
    const val EXPORT_HINT = "save a copy of this budget as a zip file"
    const val ENCRYPTED = "encrypted"
    const val NOT_ENCRYPTED = "not encrypted"
    const val SET_PASSWORD = "set password"
    const val CHANGE_PASSWORD = "change password"
    const val REMOVE_PASSWORD = "remove password"
    const val NEW_PASSWORD = "new password"
    const val CURRENT_PASSWORD = "current password"
    const val REPLACEMENT = "replacement"
    const val BUDGETS_STAY = "budgets on this phone stay"

    fun method(budgetType: String) = if (budgetType == "tracking") "tracking budget" else "envelope budget"

    fun encryptionTitle(mode: EncryptionMode) =
        when (mode) {
            EncryptionMode.SET -> SET_PASSWORD
            EncryptionMode.CHANGE -> CHANGE_PASSWORD
            EncryptionMode.REMOVE -> REMOVE_PASSWORD
        }
}

/**
 * Settings as a pivot (spec 012): budget, server, format, appearance, advanced. Format and
 * advanced only show while a budget is open. Forms open their own pages; no item holds a field.
 */
@Composable
fun SettingsScreen(
    page: SettingsPage,
    instances: List<SavedInstance>,
    activeInstance: SavedInstance?,
    instanceReachable: Boolean?,
    signedIn: Boolean,
    budgets: List<BudgetSummary>,
    openBudgetId: String?,
    themeMode: String,
    accentId: String,
    formatPrefs: FormatPrefs,
    cloudFileId: String?,
    encrypted: Boolean,
    onPage: (SettingsPage) -> Unit,
    onMakeActive: (String) -> Unit,
    onRemoveInstance: (String) -> Unit,
    onOpenBudget: (String) -> Unit,
    onTheme: (String) -> Unit,
    onAccent: (String) -> Unit,
    styleId: String = MetroStyle.Default.id,
    onStyle: (String) -> Unit = {},
    onPayees: () -> Unit,
    onDateFormat: (String) -> Unit,
    onNumberFormat: (String) -> Unit,
    onFirstDay: (Int) -> Unit,
    onHideFraction: (Boolean) -> Unit,
    onExperimental: (Boolean) -> Unit,
    onExport: () -> Unit,
    onEncryption: (EncryptionMode) -> Unit,
    onRequestAdvanced: (AdvancedConfirm) -> Unit,
    modifier: Modifier = Modifier,
    launcherIcon: LauncherIcon = LauncherIcon.TILE,
    onLauncherIcon: (LauncherIcon) -> Unit = {},
    onLearning: (Boolean) -> Unit = {},
    onExperimentalFeature: (ExperimentalFeature, Boolean) -> Unit = { _, _ -> },
    phoneSettings: PhoneSettings = PhoneSettings(),
    scroll: Map<SettingsPage, Int> = emptyMap(),
    onScroll: (SettingsPage, Int) -> Unit = { _, _ -> },
) {
    val budgetOpen = !openBudgetId.isNullOrBlank()
    val pages = SettingsPage.shown(budgetOpen)
    val selected = SettingsPage.visible(budgetOpen, page)
    val items =
        pages.map { item ->
            PivotItem(item.header) {
                SettingsItem(item, scroll[item] ?: 0, onScroll) {
                    when (item) {
                        SettingsPage.BUDGET ->
                            BudgetItem(
                                budgets = budgets,
                                openBudgetId = openBudgetId,
                                budgetType = formatPrefs.budgetType,
                                learning = formatPrefs.learnCategories,
                                onOpen = onOpenBudget,
                                onPayees = onPayees,
                                onLearning = onLearning,
                            )
                        SettingsPage.SERVER ->
                            ServerItem(
                                instances = instances,
                                activeInstance = activeInstance,
                                instanceReachable = instanceReachable,
                                signedIn = signedIn,
                                onMakeActive = onMakeActive,
                                onRemove = onRemoveInstance,
                            )
                        SettingsPage.FORMAT ->
                            FormatItem(
                                prefs = formatPrefs,
                                onDateFormat = onDateFormat,
                                onNumberFormat = onNumberFormat,
                                onFirstDay = onFirstDay,
                                onHideFraction = onHideFraction,
                            )
                        SettingsPage.APPEARANCE ->
                            AppearanceItem(
                                themeMode = themeMode,
                                accentId = accentId,
                                launcherIcon = launcherIcon,
                                onTheme = onTheme,
                                onAccent = onAccent,
                                styleId = styleId,
                                onStyle = onStyle,
                                onLauncherIcon = onLauncherIcon,
                            )
                        SettingsPage.ADVANCED ->
                            AdvancedItem(
                                budgetId = openBudgetId.orEmpty(),
                                cloudFileId = cloudFileId,
                                encrypted = encrypted,
                                onExport = onExport,
                                onEncryption = onEncryption,
                                onRequest = onRequestAdvanced,
                            )
                        SettingsPage.EXPERIMENTAL ->
                            ExperimentalItem(
                                prefs = formatPrefs,
                                onExperimental = onExperimental,
                                onFeature = onExperimentalFeature,
                                phone = phoneSettings,
                            )
                    }
                }
            }
        }
    // Keyed on the header row so a budget opening or closing rebuilds the pager at the right item.
    androidx.compose.runtime.key(pages) {
        MetroPivot(
            title = SettingsCopy.TITLE,
            items = items,
            modifier = modifier.testTag("settings-screen"),
            initialItem = pages.indexOf(selected).coerceAtLeast(0),
            onSelected = { index -> pages.getOrNull(index)?.let(onPage) },
        )
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
        MetroButtonGrid {
            MetroButton("open", onClick = onOpen)
            MetroButton("stay", onClick = onStay)
        }
    }
}

/** Settings → server → remove: names the server and says the phone's budgets stay (spec 012 FR-007). */
@Composable
fun ConfirmRemoveInstanceScreen(
    name: String,
    onRemove: () -> Unit,
    onKeep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter)
            .testTag("confirm-remove-instance"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MetroText("remove $name", Metro.typography.header)
        MetroText(SettingsCopy.BUDGETS_STAY, Metro.typography.subheader)
        MetroButtonGrid {
            MetroButton("remove", Modifier.testTag("remove-instance-confirm"), onClick = onRemove)
            MetroButton("keep", Modifier.testTag("remove-instance-keep"), onClick = onKeep)
        }
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
        MetroButtonGrid {
            MetroButton("confirm", Modifier.testTag("advanced-confirm"), onClick = onConfirm)
            MetroButton("stay", Modifier.testTag("advanced-cancel"), onClick = onCancel)
        }
    }
}

/** One page per encryption password action; save sits on the app bar (spec 012 FR-013). */
@Composable
fun EncryptionPasswordScreen(
    mode: EncryptionMode,
    error: String?,
    onSet: (String) -> Unit,
    onChange: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Passwords live only while the page is open.
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val save: () -> Unit = {
        when (mode) {
            EncryptionMode.SET -> onSet(first)
            EncryptionMode.CHANGE -> onChange(first, second)
            EncryptionMode.REMOVE -> onRemove(first)
        }
    }
    RememberFormSave(save)
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(MetroDimens.Gutter)
            .testTag("encryption-screen"),
        verticalArrangement = Arrangement.spacedBy(MetroDimens.Grid),
    ) {
        MetroText(SettingsCopy.encryptionTitle(mode), Metro.typography.header)
        when (mode) {
            EncryptionMode.SET ->
                MetroField(first, { first = it }, SettingsCopy.NEW_PASSWORD, Modifier.testTag("encryption-password"), conceal = true)
            EncryptionMode.CHANGE -> {
                MetroField(first, { first = it }, SettingsCopy.CURRENT_PASSWORD, Modifier.testTag("encryption-current"), conceal = true)
                MetroField(second, { second = it }, SettingsCopy.REPLACEMENT, Modifier.testTag("encryption-next"), conceal = true)
            }
            EncryptionMode.REMOVE ->
                MetroField(first, { first = it }, SettingsCopy.CURRENT_PASSWORD, Modifier.testTag("encryption-current"), conceal = true)
        }
        if (error != null) {
            MetroText(error, Metro.typography.body, Modifier.testTag("encryption-error"), color = Metro.accent.text)
        }
        if (!formSaveOnBar()) {
            MetroButtonGrid {
                MetroButton(SettingsCopy.encryptionTitle(mode), Modifier.testTag("encryption-save"), onClick = save)
            }
        }
    }
}
