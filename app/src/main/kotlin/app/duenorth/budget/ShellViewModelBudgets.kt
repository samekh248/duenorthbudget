package app.duenorth.budget

import androidx.lifecycle.viewModelScope
import app.duenorth.budget.core.CreateResult
import app.duenorth.budget.core.OnboardingPath
import app.duenorth.budget.core.OpenResult
import app.duenorth.budget.core.RemoteFile
import app.duenorth.budget.core.SampleBudgets
import app.duenorth.budget.core.SyncCopy
import app.duenorth.budget.core.UnlockResult
import app.duenorth.budget.core.UploadResult
import app.duenorth.budget.core.activeInstance
import app.duenorth.budget.core.syncFraction
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Budgets: creating, opening, switching and replacing a budget, signing in and out, and syncing it.

fun ShellViewModel.showSampleBudgets() {
    if (!developerMode) return
    history.clear()
    mutate { it.copy(route = ShellRoute.SampleBudgets, createError = null, confirm = null) }
}

fun ShellViewModel.create(
    name: String,
    currency: String,
) {
    viewModelScope.launch {
        when (val result = withContext(io) { createBudget(name, currency) }) {
            is CreateResult.Rejected -> mutate { it.copy(createError = result.reason) }
            is CreateResult.Created -> {
                // A budget made on the server path is sent to that server in the background (spec 011 US5).
                val send =
                    state.value.onboarding
                        ?.progress
                        ?.path == OnboardingPath.SERVER && sync.signedIn()
                val settingUp = state.value.onboarding != null
                finishOnboarding()
                // Debug builds fill the file from a sample, so only release builds have steps left to do.
                if (samples == null) startGetStarted(result.id)
                val toSettings = state.value.settingsReturn
                mutate {
                    it.copy(
                        createError = null,
                        route = if (toSettings) ShellRoute.Settings else ShellRoute.Home,
                        settingsPage = if (toSettings) SettingsPage.BUDGET else it.settingsPage,
                        settingsReturn = false,
                    )
                }
                reload()
                offerExperimental(always = settingUp)
                if (toSettings) refreshSettingsSections()
                if (send) uploadNew()
            }
        }
    }
}

/** Debug builds seed every new budget with the full walkthrough data; release builds start empty. */
internal fun ShellViewModel.createBudget(
    name: String,
    currency: String,
): CreateResult {
    val samples = samples ?: return library.create(name, currency)
    return samples.create(library, samples.walkthrough, name.ifBlank { samples.walkthroughName })
}

fun ShellViewModel.createSample(datasetId: String) {
    val samples = samples ?: return
    viewModelScope.launch {
        when (val result = withContext(io) { samples.create(library, datasetId) }) {
            is CreateResult.Rejected -> mutate { it.copy(createError = result.reason) }
            is CreateResult.Created -> {
                val settingUp = state.value.onboarding != null
                finishOnboarding()
                if (datasetId == samples.brandNew) startGetStarted(result.id)
                mutate { it.copy(createError = null, route = ShellRoute.Home) }
                reload()
                offerExperimental(always = settingUp)
            }
        }
    }
}

fun ShellViewModel.requestSwitch(id: String) {
    val target = state.value.budgets.find { it.id == id } ?: return
    if (target.id == state.value.settings.openBudgetId) {
        mutate { it.copy(route = ShellRoute.Home) }
        return
    }
    mutate {
        it.copy(
            switchTarget = target,
            pendingFile = null,
            route = ShellRoute.ConfirmSwitch,
            switchReturnRoute = it.route,
        )
    }
}

fun ShellViewModel.cancelSwitch() {
    mutate {
        it.copy(
            route = it.switchReturnRoute,
            switchTarget = null,
            pendingFile = null,
        )
    }
}

fun ShellViewModel.confirmSwitch() {
    val pending = state.value.pendingFile
    if (pending != null) {
        beginOpen(pending)
        return
    }
    val target = state.value.switchTarget ?: return
    viewModelScope.launch {
        withContext(io) { library.switchTo(target.id) }
        mutate { it.copy(route = ShellRoute.Home, switchTarget = null) }
        reload()
        offerExperimental()
    }
}

fun ShellViewModel.connect(
    address: String,
    password: String,
) {
    servers.connect(address, password)
}

fun ShellViewModel.signOut(preferSettings: Boolean = false) {
    // Non-blocking: detach token on IO while UI stays responsive even if sync is running.
    viewModelScope.launch {
        withContext(io) { sync.signOut() }
        val settings = withContext(io) { library.settings() }
        mutate { current ->
            current.copy(
                remoteFiles = emptyList(),
                serverError = null,
                signedIn = false,
                settings = settings,
                servers = current.servers.copy(reachable = null),
                route =
                    when {
                        preferSettings || current.route == ShellRoute.Settings -> ShellRoute.Settings
                        else -> ShellRoute.Server
                    },
            )
        }
    }
}

fun ShellViewModel.requestRemote(file: RemoteFile) {
    // Keep and failures come back to the list the budget was picked from.
    mutate { it.copy(switchReturnRoute = it.route.takeIf(::isServerList) ?: it.switchReturnRoute) }
    if (file.encryptKeyId != null && !sync.isUnlocked(file.fileId)) {
        mutate {
            it.copy(route = ShellRoute.BudgetPassword, pendingFile = file, passwordError = null, askEachTime = true)
        }
        return
    }
    confirmOrOpen(file)
}

/** `use` on the replace confirmation: download the server budget, then clear the others. */
fun ShellViewModel.confirmReplaceBudget() {
    val current = state.value
    val file = current.pendingFile ?: return
    if (current.syncing) return
    // The download's progress shows over the list it was picked from.
    mutate { it.copy(route = it.switchReturnRoute.takeIf(::isServerList) ?: ShellRoute.Server) }
    beginOpen(file)
}

/** `keep` (or back) on the replace confirmation: the phone's budget stays, the list shows again. */
fun ShellViewModel.cancelReplaceBudget() {
    mutate {
        it.copy(
            route = it.switchReturnRoute.takeIf(::isServerList) ?: ShellRoute.Server,
            pendingFile = null,
            replaceBudgets = emptyList(),
            passwordError = null,
        )
    }
}

fun ShellViewModel.submitPassword(
    password: String,
    askEachTime: Boolean,
) {
    val file = state.value.pendingFile
    val openId = state.value.settings.openBudgetId
    viewModelScope.launch {
        if (file != null) {
            when (val result = withContext(io) { sync.unlock(file, password, askEachTime) }) {
                is UnlockResult.Failed -> mutate { it.copy(passwordError = result.reason) }
                UnlockResult.Unlocked -> confirmOrOpen(file)
            }
        } else if (openId != null) {
            val remote =
                RemoteFile(
                    fileId = openId,
                    groupId = "",
                    name =
                        state.value.budgets
                            .find { it.id == openId }
                            ?.name
                            .orEmpty(),
                    encryptKeyId = "local",
                )
            when (val result = withContext(io) { sync.unlock(remote, password, askEachTime) }) {
                is UnlockResult.Failed -> mutate { it.copy(passwordError = result.reason) }
                UnlockResult.Unlocked -> {
                    mutate { it.copy(locked = false, passwordError = null, route = ShellRoute.Home) }
                    reload()
                }
            }
        }
    }
}

fun ShellViewModel.setAskEachTime(value: Boolean) {
    mutate { it.copy(askEachTime = value) }
}

fun ShellViewModel.syncNow() {
    val id = state.value.settings.openBudgetId ?: return
    viewModelScope.launch {
        mutate { it.copy(syncing = true, syncProgress = 0.08f) }
        withContext(io) { runCatching { sync.sync(id) } }
        mutate { it.copy(syncing = false, syncProgress = null) }
        reload()
    }
}

/** The inbox `•••` → sync: the same sync, then one line under the inbox's totals says how it went. */
fun ShellViewModel.syncInbox() {
    val id = state.value.settings.openBudgetId ?: return
    if (state.value.syncing) return
    viewModelScope.launch {
        mutate { it.copy(syncing = true, syncProgress = 0.08f, inbox = it.inbox.copy(syncNotice = null)) }
        val result = withContext(io) { runCatching { sync.sync(id) }.getOrNull() }
        val notice = result?.status?.takeIf { it.isNotBlank() } ?: SyncCopy.LAST_FAILED
        mutate { it.copy(syncing = false, syncProgress = null, inbox = it.inbox.copy(syncNotice = notice)) }
        reload()
    }
}

fun ShellViewModel.uploadNew() {
    val id = state.value.settings.openBudgetId ?: return
    viewModelScope.launch {
        mutate { it.copy(syncing = true, syncProgress = 0.08f, serverError = null) }
        val result = withContext(io) { sync.uploadNew(id) }
        mutate {
            it.copy(
                syncing = false,
                syncProgress = null,
                serverError = (result as? UploadResult.Failed)?.reason,
                remoteFiles = sync.remoteFiles,
                signedIn = sync.signedIn(),
            )
        }
    }
}

fun ShellViewModel.requestReplace(file: RemoteFile) {
    mutate { it.copy(route = ShellRoute.ConfirmReplace, replaceFile = file) }
}

fun ShellViewModel.cancelReplace() {
    mutate { it.copy(route = ShellRoute.Server, replaceFile = null) }
}

fun ShellViewModel.confirmReplace() {
    val file = state.value.replaceFile ?: return
    val id = state.value.settings.openBudgetId ?: return
    viewModelScope.launch {
        val result = withContext(io) { sync.uploadOnto(id, file) }
        mutate {
            it.copy(
                route = ShellRoute.Server,
                replaceFile = null,
                serverError = (result as? UploadResult.Failed)?.reason,
                remoteFiles = sync.remoteFiles,
                signedIn = sync.signedIn(),
            )
        }
    }
}

/**
 * A server budget replaces whatever is on the phone. When other budgets are there, the
 * person sees which ones get cleared first; otherwise it opens straight away.
 */
private fun ShellViewModel.confirmOrOpen(file: RemoteFile) {
    viewModelScope.launch {
        val cleared = withContext(io) { library.list() }.filter { it.id != file.fileId }
        if (cleared.isEmpty()) {
            beginOpen(file)
            return@launch
        }
        mutate {
            it.copy(
                route = ShellRoute.ConfirmReplaceBudget,
                pendingFile = file,
                replaceBudgets = cleared,
                passwordError = null,
            )
        }
    }
}

private fun ShellViewModel.isServerList(route: ShellRoute): Boolean = route == ShellRoute.OnboardingFiles || route == ShellRoute.Server

private fun ShellViewModel.beginOpen(file: RemoteFile) {
    viewModelScope.launch {
        mutate { it.copy(syncing = true, syncProgress = 0f, serverError = null) }
        val result =
            withContext(io) {
                sync.open(file, replace = true) { received, total ->
                    mutate { it.copy(syncing = true, syncProgress = syncFraction(received, total)) }
                }
            }
        when (result) {
            OpenResult.Opened -> {
                val settingUp = state.value.onboarding != null
                finishOnboarding()
                mutate {
                    it.copy(
                        syncing = false,
                        syncProgress = null,
                        pendingFile = null,
                        replaceBudgets = emptyList(),
                        locked = false,
                        route = ShellRoute.Home,
                        settingsReturn = false,
                    )
                }
                reload()
                offerExperimental(always = settingUp)
            }
            OpenResult.NeedsPassword ->
                mutate {
                    it.copy(syncing = false, syncProgress = null, route = ShellRoute.BudgetPassword, pendingFile = file)
                }
            is OpenResult.Failed ->
                mutate {
                    it.copy(
                        syncing = false,
                        syncProgress = null,
                        serverError = result.reason,
                        replaceBudgets = emptyList(),
                        route =
                            when {
                                it.onboarding != null -> ShellRoute.OnboardingFiles
                                isServerList(it.switchReturnRoute) -> it.switchReturnRoute
                                else -> ShellRoute.Server
                            },
                    )
                }
        }
    }
}
