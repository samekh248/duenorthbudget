package app.duenorth.budget

import androidx.lifecycle.viewModelScope
import app.duenorth.budget.core.CreateResult
import app.duenorth.budget.core.FormatPrefs
import app.duenorth.budget.core.PendingExport
import app.duenorth.budget.core.PhoneSettings
import app.duenorth.budget.core.UnlockResult
import app.duenorth.budget.core.activeInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// Settings: its pages, formats, appearance, experimental features, export, encryption and advanced repairs.

fun ShellViewModel.showSettings() {
    // Edit mode's menu offers settings too; leaving for it drops the changes, as leaving the pivot item does.
    if (state.value.budgetEdit != null) cancelBudgetEdit()
    history.clear()
    mutate {
        it.copy(
            route = ShellRoute.Settings,
            confirm = null,
            createError = null,
            encryptionError = null,
            settingsPage = SettingsPage.BUDGET,
            settingsScroll = emptyMap(),
            settingsReturn = false,
        )
    }
    refreshSettingsSections()
}

fun ShellViewModel.selectSettingsPage(page: SettingsPage) {
    mutate { it.copy(settingsPage = page) }
}

fun ShellViewModel.saveSettingsScroll(
    page: SettingsPage,
    offset: Int,
) {
    mutate { it.copy(settingsScroll = it.settingsScroll + (page to offset)) }
}

/** Opens a page from settings; back or finishing it comes back to the same item (spec 012 FR-015). */
internal fun ShellViewModel.openFromSettings(
    route: ShellRoute,
    page: SettingsPage = state.value.settingsPage,
) {
    history.clear()
    mutate {
        it.copy(
            route = route,
            settingsPage = page,
            settingsReturn = true,
            confirm = null,
            createError = null,
            encryptionError = null,
            serverError = null,
        )
    }
}

internal fun ShellViewModel.returnToSettings(page: SettingsPage? = null) {
    history.clear()
    mutate {
        it.copy(
            route = ShellRoute.Settings,
            settingsPage = page ?: it.settingsPage,
            settingsReturn = false,
            createError = null,
            encryptionError = null,
            switchTarget = null,
            switchInstanceTarget = null,
            pendingFile = null,
        )
    }
    refreshSettingsSections()
}

fun ShellViewModel.showSettingsCreate() = openFromSettings(ShellRoute.Create, SettingsPage.BUDGET)

fun ShellViewModel.showSettingsPayees() {
    showPayees()
    mutate { it.copy(settingsReturn = true, settingsPage = SettingsPage.BUDGET) }
}

fun ShellViewModel.showEncryption(mode: EncryptionMode) = openFromSettings(ShellRoute.EncryptionPassword(mode), SettingsPage.ADVANCED)

fun ShellViewModel.setDateFormat(value: String) = setOpenBudgetPreference("dateFormat", value)

fun ShellViewModel.setNumberFormat(value: String) = setOpenBudgetPreference("numberFormat", value)

fun ShellViewModel.setFirstDay(index: Int) = setOpenBudgetPreference("firstDayOfWeekIdx", index.coerceIn(0, 6).toString())

fun ShellViewModel.setHideFraction(hide: Boolean) = setOpenBudgetPreference("hideFraction", if (hide) "true" else "false")

fun ShellViewModel.setExperimental(enabled: Boolean) = setOpenBudgetPreference("flags.experimental", if (enabled) "true" else "false")

/** Settings → learn categories (spec 010 US3). */
fun ShellViewModel.setLearning(enabled: Boolean) = setOpenBudgetPreference("learn-categories", if (enabled) "true" else "false")

fun ShellViewModel.setGoalTemplates(enabled: Boolean) =
    setOpenBudgetPreference("flags.goalTemplatesEnabled", if (enabled) "true" else "false")

fun ShellViewModel.setExperimentalFeature(
    feature: ExperimentalFeature,
    enabled: Boolean,
) {
    val preference = feature.preference
    if (preference == null) setPhoneFeature(feature, enabled) else setOpenBudgetPreference(preference, if (enabled) "true" else "false")
}

/** A feature with no Actual flag is this phone's setting; nothing goes into the budget file. */
private fun ShellViewModel.setPhoneFeature(
    feature: ExperimentalFeature,
    enabled: Boolean,
) {
    when (feature) {
        ExperimentalFeature.FREE_AFTER_PLANS -> saveSettings(state.value.settings.copy(freeAfterPlans = enabled))
        ExperimentalFeature.TEMPLATES -> Unit
    }
}

/** `turn on` on the offer: the switch and every feature come on for the budget it asked about. */
fun ShellViewModel.acceptExperimentalOffer() = answerExperimentalOffer(on = true)

/** `keep off` on the offer: saved as off, so that budget is not asked again. Back asks next time. */
fun ShellViewModel.declineExperimentalOffer() = answerExperimentalOffer(on = false)

private fun ShellViewModel.answerExperimentalOffer(on: Boolean) {
    val offer = state.value.route as? ShellRoute.ExperimentalOffer ?: return
    // Keeping what a budget already has leaves its features as they are.
    if (offer.current != on) {
        val value = if (on) "true" else "false"
        setBudgetPreference(offer.budgetId, "flags.experimental", value)
        if (on) {
            ExperimentalFeature.entries.forEach { feature ->
                val preference = feature.preference
                if (preference == null) setPhoneFeature(feature, true) else setBudgetPreference(offer.budgetId, preference, value)
            }
        }
    }
    mutate { it.copy(route = ShellRoute.Home) }
}

/**
 * After a budget is opened, switched to, or made: one that has never had experimental
 * features chosen asks before its home shows. With [always] (the budget picked or made while
 * setting up the phone) it asks even when the budget already has a choice, saying what it is. The budget is read from the phone, not the
 * screen, so the answer lands on the budget just opened.
 */
internal suspend fun ShellViewModel.offerExperimental(always: Boolean = false) {
    val budgetId = withContext(io) { library.settings().openBudgetId } ?: return
    val prefs = withContext(io) { library.readFormatPrefs(budgetId) }
    if (prefs.experimentalAsked && !always) return
    val current = prefs.experimental.takeIf { prefs.experimentalAsked }
    mutate {
        val ask = it.route == ShellRoute.Home && it.shell != null && !it.locked
        if (ask) it.copy(route = ShellRoute.ExperimentalOffer(budgetId, current)) else it
    }
}

fun ShellViewModel.exportBudget() {
    val budgetId = state.value.settings.openBudgetId ?: return
    val name =
        state.value.budgets
            .find { it.id == budgetId }
            ?.name
            .orEmpty()
    viewModelScope.launch {
        val bytes = withContext(io) { library.exportZip(budgetId) } ?: return@launch
        val fileName = library.exportFileName(name)
        mutate { it.copy(pendingExport = PendingExport(fileName, bytes)) }
    }
}

fun ShellViewModel.clearPendingExport() {
    mutate { it.copy(pendingExport = null) }
}

fun ShellViewModel.requestAdvanced(kind: AdvancedConfirm) {
    mutate { it.copy(confirmAdvanced = kind) }
}

fun ShellViewModel.cancelAdvanced() {
    mutate { it.copy(confirmAdvanced = null) }
}

fun ShellViewModel.confirmRebuild() = confirmAdvancedAction(AdvancedConfirm.Rebuild) { id -> library.rebuildTotals(id) }

fun ShellViewModel.confirmRepair() = confirmAdvancedAction(AdvancedConfirm.Repair) { id -> library.repairSplits(id) }

fun ShellViewModel.confirmResetSync() = confirmAdvancedAction(AdvancedConfirm.ResetSync) { id -> sync.resetSync(id) }

fun ShellViewModel.setEncryptionPassword(password: String) {
    val budgetId = state.value.settings.openBudgetId ?: return
    viewModelScope.launch {
        when (val result = withContext(io) { sync.setEncryptionPassword(budgetId, password) }) {
            is UnlockResult.Failed -> mutate { it.copy(encryptionError = result.reason) }
            UnlockResult.Unlocked -> encryptionSaved()
        }
    }
}

fun ShellViewModel.changeEncryptionPassword(
    current: String,
    next: String,
) {
    val budgetId = state.value.settings.openBudgetId ?: return
    viewModelScope.launch {
        when (
            val result =
                withContext(io) {
                    sync.changeEncryptionPassword(budgetId, current, next)
                }
        ) {
            is UnlockResult.Failed -> mutate { it.copy(encryptionError = result.reason) }
            UnlockResult.Unlocked -> encryptionSaved()
        }
    }
}

fun ShellViewModel.removeEncryptionPassword(password: String) {
    val budgetId = state.value.settings.openBudgetId ?: return
    viewModelScope.launch {
        when (val result = withContext(io) { sync.removeEncryptionPassword(budgetId, password) }) {
            is UnlockResult.Failed -> mutate { it.copy(encryptionError = result.reason) }
            UnlockResult.Unlocked -> encryptionSaved()
        }
    }
}

private fun ShellViewModel.encryptionSaved() {
    mutate { it.copy(encryptionError = null) }
    if (state.value.route is ShellRoute.EncryptionPassword) returnToSettings(SettingsPage.ADVANCED) else refreshSettingsSections()
}

fun ShellViewModel.createFromSettings(
    name: String,
    currency: String,
) {
    viewModelScope.launch {
        when (val result = withContext(io) { createBudget(name, currency) }) {
            is CreateResult.Created -> {
                mutate { it.copy(createError = null, route = ShellRoute.Home) }
                reload()
                offerExperimental()
            }
            is CreateResult.Rejected -> mutate { it.copy(createError = result.reason) }
        }
    }
}

fun ShellViewModel.setTheme(mode: String) {
    saveSettings(state.value.settings.copy(themeMode = mode))
}

fun ShellViewModel.setAccent(id: String) {
    saveSettings(state.value.settings.copy(accent = id))
}

fun ShellViewModel.setStyle(id: String) {
    saveSettings(state.value.settings.copy(style = id))
}

/** Settings → server → background sync; the app reschedules the job when this changes. */
fun ShellViewModel.setBackgroundSyncHours(hours: Int) {
    if (hours == state.value.settings.backgroundSyncHours) return
    saveSettings(state.value.settings.copy(backgroundSyncHours = hours))
    onBackgroundSyncHours(hours)
}

internal fun ShellViewModel.saveSettings(settings: PhoneSettings) {
    mutate { it.copy(settings = settings) }
    viewModelScope.launch(io) { library.save(settings) }
}

internal fun ShellViewModel.refreshSettingsSections() {
    viewModelScope.launch {
        val settings = withContext(io) { library.settings() }
        val budgetId = settings.openBudgetId
        val prefs =
            if (budgetId == null) {
                FormatPrefs()
            } else {
                withContext(io) { library.readFormatPrefs(budgetId) }
            }
        val cloud =
            if (budgetId == null) {
                null
            } else {
                withContext(io) {
                    library.useDatabase(budgetId) { session ->
                        app.duenorth.budget.core.SyncSchema
                            .state(session, "cloudFileId")
                    }
                }
            }
        val encrypted = budgetId != null && withContext(io) { sync.isEncrypted(budgetId) }
        mutate {
            it.copy(
                budgetEncrypted = encrypted,
                settings = settings,
                instances = settings.instances,
                activeInstance = settings.activeInstance(),
                formatPrefs = prefs,
                budgetMethod = prefs.budgetType,
                cloudFileId = cloud?.takeIf { id -> id.isNotBlank() },
                signedIn = sync.signedIn(),
            )
        }
        val address = settings.activeInstance()?.address
        if (!address.isNullOrBlank()) {
            probeOpenId(address)
            probeReachability()
        } else {
            mutate { it.copy(openIdAvailable = false, instanceReachable = null) }
        }
    }
}

private fun ShellViewModel.setOpenBudgetPreference(
    id: String,
    value: String,
) {
    val budgetId = state.value.settings.openBudgetId ?: return
    setBudgetPreference(budgetId, id, value)
}

/** Saves [id] on [budgetId]; the screen updates straight away when that budget is the open one. */
private fun ShellViewModel.setBudgetPreference(
    budgetId: String,
    id: String,
    value: String,
) {
    val showing = budgetId == state.value.settings.openBudgetId
    val optimistic =
        when (id) {
            "dateFormat" -> state.value.formatPrefs.copy(dateFormat = value)
            "numberFormat" -> state.value.formatPrefs.copy(numberFormat = value)
            "firstDayOfWeekIdx" ->
                state.value.formatPrefs.copy(firstDayOfWeekIdx = value.toIntOrNull() ?: 0)
            "hideFraction" -> state.value.formatPrefs.copy(hideFraction = value == "true")
            "flags.experimental" -> state.value.formatPrefs.copy(experimental = value == "true", experimentalAsked = true)
            "learn-categories" -> state.value.formatPrefs.copy(learnCategories = value == "true")
            "flags.goalTemplatesEnabled" -> state.value.formatPrefs.copy(goalTemplates = value == "true")
            else -> state.value.formatPrefs
        }
    if (showing) {
        mutate {
            it.copy(
                formatPrefs = optimistic,
                budgetMethod = optimistic.budgetType,
            )
        }
    }
    viewModelScope.launch(io) {
        preferenceWrites.withLock { library.setPreference(budgetId, id, value) }
    }
}

private fun ShellViewModel.confirmAdvancedAction(
    kind: AdvancedConfirm,
    block: (String) -> Boolean,
) {
    if (state.value.confirmAdvanced != kind) return
    val budgetId = state.value.settings.openBudgetId ?: return
    viewModelScope.launch {
        withContext(io) { block(budgetId) }
        mutate { it.copy(confirmAdvanced = null) }
        reload()
        refreshSettingsSections()
    }
}
