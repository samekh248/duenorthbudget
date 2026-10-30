package app.duenorth.budget

import app.duenorth.budget.core.CreateResult
import app.duenorth.budget.core.FormatPrefs
import app.duenorth.budget.core.PendingExport
import app.duenorth.budget.core.PhoneSettings
import app.duenorth.budget.core.UnlockResult
import app.duenorth.budget.core.activeInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The settings screen's own state, inside [ShellUiState.settingsUi]. */
data class SettingsUi(
    /** Where each settings page was scrolled to, kept while a page opened from it shows. */
    val scroll: Map<SettingsPage, Int> = emptyMap(),
    val encryptionError: String? = null,
    /** The open budget's file on the server is encrypted. */
    val budgetEncrypted: Boolean = false,
    /** An export ready to hand to the system's save sheet; null once handed over. */
    val pendingExport: PendingExport? = null,
    /** The advanced repair being asked about; null when none is. */
    val confirmAdvanced: AdvancedConfirm? = null,
)

/**
 * Settings: its pages, formats, appearance, experimental features, export, encryption and advanced repairs. It opens
 * the payees page through [payees] and checks the server through [servers].
 */
class SettingsHolder internal constructor(
    private val host: ShellHost,
    private val payees: PayeesHolder,
    private val servers: ServersHolder,
) {
    /** Budget preference saves, in the order they were made, so two flipped together both land. */
    private val preferenceWrites = Mutex()

    private val state get() = host.state.value

    private fun update(transform: (SettingsUi) -> SettingsUi) = host.mutate { it.copy(settingsUi = transform(it.settingsUi)) }

    fun show() {
        // Edit mode's menu offers settings too; leaving for it drops the changes, as leaving the pivot item does.
        if (state.budgetEdit != null) host.cancelBudgetEdit()
        host.clearHistory()
        host.mutate {
            it.copy(
                route = ShellRoute.Settings,
                confirm = null,
                createError = null,
                settingsPage = SettingsPage.BUDGET,
                settingsUi = it.settingsUi.copy(encryptionError = null, scroll = emptyMap()),
                settingsReturn = false,
            )
        }
        refresh()
    }

    fun selectPage(page: SettingsPage) {
        host.mutate { it.copy(settingsPage = page) }
    }

    fun saveScroll(
        page: SettingsPage,
        offset: Int,
    ) {
        update { it.copy(scroll = it.scroll + (page to offset)) }
    }

    /** Opens a page from settings; back or finishing it comes back to the same item (spec 012 FR-015). */
    internal fun openFrom(
        route: ShellRoute,
        page: SettingsPage = state.settingsPage,
    ) {
        host.clearHistory()
        host.mutate {
            it.copy(
                route = route,
                settingsPage = page,
                settingsReturn = true,
                confirm = null,
                createError = null,
                settingsUi = it.settingsUi.copy(encryptionError = null),
                serverError = null,
            )
        }
    }

    internal fun returnTo(page: SettingsPage? = null) {
        host.clearHistory()
        host.mutate {
            it.copy(
                route = ShellRoute.Settings,
                settingsPage = page ?: it.settingsPage,
                settingsReturn = false,
                createError = null,
                settingsUi = it.settingsUi.copy(encryptionError = null),
                servers = it.servers.copy(switchTarget = null),
                budgetFiles = it.budgetFiles.copy(switchTarget = null, pendingFile = null),
            )
        }
        refresh()
    }

    fun showCreate() = openFrom(ShellRoute.Create, SettingsPage.BUDGET)

    fun showPayees() {
        payees.show()
        host.mutate { it.copy(settingsReturn = true, settingsPage = SettingsPage.BUDGET) }
    }

    fun showEncryption(mode: EncryptionMode) = openFrom(ShellRoute.EncryptionPassword(mode), SettingsPage.ADVANCED)

    fun setDateFormat(value: String) = setOpenBudgetPreference("dateFormat", value)

    fun setNumberFormat(value: String) = setOpenBudgetPreference("numberFormat", value)

    fun setFirstDay(index: Int) = setOpenBudgetPreference("firstDayOfWeekIdx", index.coerceIn(0, 6).toString())

    fun setHideFraction(hide: Boolean) = setOpenBudgetPreference("hideFraction", if (hide) "true" else "false")

    fun setExperimental(enabled: Boolean) = setOpenBudgetPreference("flags.experimental", if (enabled) "true" else "false")

    /** Settings → learn categories (spec 010 US3). */
    fun setLearning(enabled: Boolean) = setOpenBudgetPreference("learn-categories", if (enabled) "true" else "false")

    fun setGoalTemplates(enabled: Boolean) = setOpenBudgetPreference("flags.goalTemplatesEnabled", if (enabled) "true" else "false")

    fun setExperimentalFeature(
        feature: ExperimentalFeature,
        enabled: Boolean,
    ) {
        val preference = feature.preference
        if (preference == null) setPhoneFeature(feature, enabled) else setOpenBudgetPreference(preference, if (enabled) "true" else "false")
    }

    /** A feature with no Actual flag is this phone's setting; nothing goes into the budget file. */
    private fun setPhoneFeature(
        feature: ExperimentalFeature,
        enabled: Boolean,
    ) {
        when (feature) {
            ExperimentalFeature.FREE_AFTER_PLANS -> save(state.settings.copy(freeAfterPlans = enabled))
            ExperimentalFeature.TEMPLATES -> Unit
        }
    }

    /** `turn on` on the offer: the switch and every feature come on for the budget it asked about. */
    fun acceptExperimentalOffer() = answerExperimentalOffer(on = true)

    /** `keep off` on the offer: saved as off, so that budget is not asked again. Back asks next time. */
    fun declineExperimentalOffer() = answerExperimentalOffer(on = false)

    private fun answerExperimentalOffer(on: Boolean) {
        val offer = state.route as? ShellRoute.ExperimentalOffer ?: return
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
        host.mutate { it.copy(route = ShellRoute.Home) }
    }

    /**
     * After a budget is opened, switched to, or made: one that has never had experimental
     * features chosen asks before its home shows. With [always] (the budget picked or made while
     * setting up the phone) it asks even when the budget already has a choice, saying what it is. The budget is read from the phone, not the
     * screen, so the answer lands on the budget just opened.
     */
    internal suspend fun offerExperimental(always: Boolean = false) {
        val budgetId = withContext(host.io) { host.library.settings().openBudgetId } ?: return
        val prefs = withContext(host.io) { host.library.readFormatPrefs(budgetId) }
        if (prefs.experimentalAsked && !always) return
        val current = prefs.experimental.takeIf { prefs.experimentalAsked }
        host.mutate {
            val ask = it.route == ShellRoute.Home && it.shell != null && !it.locked
            if (ask) it.copy(route = ShellRoute.ExperimentalOffer(budgetId, current)) else it
        }
    }

    fun exportBudget() {
        val budgetId = state.settings.openBudgetId ?: return
        val name =
            state.budgets
                .find { it.id == budgetId }
                ?.name
                .orEmpty()
        host.scope.launch {
            val bytes = withContext(host.io) { host.library.exportZip(budgetId) } ?: return@launch
            val fileName = host.library.exportFileName(name)
            update { it.copy(pendingExport = PendingExport(fileName, bytes)) }
        }
    }

    fun clearPendingExport() {
        update { it.copy(pendingExport = null) }
    }

    fun requestAdvanced(kind: AdvancedConfirm) {
        update { it.copy(confirmAdvanced = kind) }
    }

    fun cancelAdvanced() {
        update { it.copy(confirmAdvanced = null) }
    }

    fun confirmRebuild() = confirmAdvancedAction(AdvancedConfirm.Rebuild) { id -> host.library.rebuildTotals(id) }

    fun confirmRepair() = confirmAdvancedAction(AdvancedConfirm.Repair) { id -> host.library.repairSplits(id) }

    fun confirmResetSync() = confirmAdvancedAction(AdvancedConfirm.ResetSync) { id -> host.sync.resetSync(id) }

    fun setEncryptionPassword(password: String) {
        val budgetId = state.settings.openBudgetId ?: return
        host.scope.launch {
            when (val result = withContext(host.io) { host.sync.setEncryptionPassword(budgetId, password) }) {
                is UnlockResult.Failed -> update { it.copy(encryptionError = result.reason) }
                UnlockResult.Unlocked -> encryptionSaved()
            }
        }
    }

    fun changeEncryptionPassword(
        current: String,
        next: String,
    ) {
        val budgetId = state.settings.openBudgetId ?: return
        host.scope.launch {
            when (
                val result =
                    withContext(host.io) {
                        host.sync.changeEncryptionPassword(budgetId, current, next)
                    }
            ) {
                is UnlockResult.Failed -> update { it.copy(encryptionError = result.reason) }
                UnlockResult.Unlocked -> encryptionSaved()
            }
        }
    }

    fun removeEncryptionPassword(password: String) {
        val budgetId = state.settings.openBudgetId ?: return
        host.scope.launch {
            when (val result = withContext(host.io) { host.sync.removeEncryptionPassword(budgetId, password) }) {
                is UnlockResult.Failed -> update { it.copy(encryptionError = result.reason) }
                UnlockResult.Unlocked -> encryptionSaved()
            }
        }
    }

    private fun encryptionSaved() {
        update { it.copy(encryptionError = null) }
        if (state.route is ShellRoute.EncryptionPassword) returnTo(SettingsPage.ADVANCED) else refresh()
    }

    fun create(
        name: String,
        currency: String,
    ) {
        host.scope.launch {
            when (val result = withContext(host.io) { host.createBudget(name, currency) }) {
                is CreateResult.Created -> {
                    host.mutate { it.copy(createError = null, route = ShellRoute.Home) }
                    host.reload()
                    offerExperimental()
                }
                is CreateResult.Rejected -> host.mutate { it.copy(createError = result.reason) }
            }
        }
    }

    fun setTheme(mode: String) {
        save(state.settings.copy(themeMode = mode))
    }

    fun setAccent(id: String) {
        save(state.settings.copy(accent = id))
    }

    fun setStyle(id: String) {
        save(state.settings.copy(style = id))
    }

    /** Settings → server → background sync; the app reschedules the job when this changes. */
    fun setBackgroundSyncHours(hours: Int) {
        if (hours == state.settings.backgroundSyncHours) return
        save(state.settings.copy(backgroundSyncHours = hours))
        host.onBackgroundSyncHours(hours)
    }

    internal fun save(settings: PhoneSettings) {
        host.mutate { it.copy(settings = settings) }
        host.scope.launch(host.io) { host.library.save(settings) }
    }

    internal fun refresh() {
        host.scope.launch {
            val settings = withContext(host.io) { host.library.settings() }
            val budgetId = settings.openBudgetId
            val prefs =
                if (budgetId == null) {
                    FormatPrefs()
                } else {
                    withContext(host.io) { host.library.readFormatPrefs(budgetId) }
                }
            val cloud =
                if (budgetId == null) {
                    null
                } else {
                    withContext(host.io) {
                        host.library.useDatabase(budgetId) { session ->
                            app.duenorth.budget.core.SyncSchema
                                .state(session, "cloudFileId")
                        }
                    }
                }
            val encrypted = budgetId != null && withContext(host.io) { host.sync.isEncrypted(budgetId) }
            host.mutate {
                it.copy(
                    settingsUi = it.settingsUi.copy(budgetEncrypted = encrypted),
                    settings = settings,
                    formatPrefs = prefs,
                    budgetMethod = prefs.budgetType,
                    cloudFileId = cloud?.takeIf { id -> id.isNotBlank() },
                    signedIn = host.sync.signedIn(),
                )
            }
            val address = settings.activeInstance()?.address
            if (!address.isNullOrBlank()) {
                servers.probeOpenId(address)
                servers.probeReachability()
            } else {
                host.mutate { it.copy(servers = it.servers.copy(openIdAvailable = false, reachable = null)) }
            }
        }
    }

    private fun setOpenBudgetPreference(
        id: String,
        value: String,
    ) {
        val budgetId = state.settings.openBudgetId ?: return
        setBudgetPreference(budgetId, id, value)
    }

    /** Saves [id] on [budgetId]; the screen updates straight away when that budget is the open one. */
    private fun setBudgetPreference(
        budgetId: String,
        id: String,
        value: String,
    ) {
        val showing = budgetId == state.settings.openBudgetId
        val optimistic =
            when (id) {
                "dateFormat" -> state.formatPrefs.copy(dateFormat = value)
                "numberFormat" -> state.formatPrefs.copy(numberFormat = value)
                "firstDayOfWeekIdx" ->
                    state.formatPrefs.copy(firstDayOfWeekIdx = value.toIntOrNull() ?: 0)
                "hideFraction" -> state.formatPrefs.copy(hideFraction = value == "true")
                "flags.experimental" -> state.formatPrefs.copy(experimental = value == "true", experimentalAsked = true)
                "learn-categories" -> state.formatPrefs.copy(learnCategories = value == "true")
                "flags.goalTemplatesEnabled" -> state.formatPrefs.copy(goalTemplates = value == "true")
                else -> state.formatPrefs
            }
        if (showing) {
            host.mutate {
                it.copy(
                    formatPrefs = optimistic,
                    budgetMethod = optimistic.budgetType,
                )
            }
        }
        host.scope.launch(host.io) {
            preferenceWrites.withLock { host.library.setPreference(budgetId, id, value) }
        }
    }

    private fun confirmAdvancedAction(
        kind: AdvancedConfirm,
        block: (String) -> Boolean,
    ) {
        if (state.settingsUi.confirmAdvanced != kind) return
        val budgetId = state.settings.openBudgetId ?: return
        host.scope.launch {
            withContext(host.io) { block(budgetId) }
            update { it.copy(confirmAdvanced = null) }
            host.reload()
            refresh()
        }
    }
}
