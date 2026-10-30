package app.duenorth.budget

import androidx.lifecycle.viewModelScope
import app.duenorth.budget.core.ServerAddress
import app.duenorth.budget.core.ServerCheck
import app.duenorth.budget.core.SyncCoordinator
import app.duenorth.budget.core.SyncCopy
import app.duenorth.budget.core.activeInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Servers: the instances list, OpenID sign-in, switching and removing an instance, and server checks.

fun ShellViewModel.showServer() {
    viewModelScope.launch {
        val result =
            if (sync.signedIn()) {
                withContext(io) { sync.refreshRemote() }
            } else {
                null
            }
        mutate { current ->
            val status = current.settings.openBudgetId?.let { sync.status(it) } ?: current.syncStatus
            current.copy(
                route = ShellRoute.Server,
                remoteFiles = result?.files ?: sync.remoteFiles,
                serverError = result?.error,
                syncStatus = status,
                signedIn = sync.signedIn(),
            )
        }
    }
}

fun ShellViewModel.showConflicts() {
    val id = state.value.settings.openBudgetId
    viewModelScope.launch {
        val conflicts = if (id == null) emptyList() else withContext(io) { sync.conflicts(id) }
        if (id != null) withContext(io) { sync.markConflictsSeen(id) }
        mutate { it.copy(route = ShellRoute.Conflicts, conflicts = conflicts) }
    }
}

/** Settings → server → server budgets: the server page with a budget open, the pick-a-budget page without (research R5). */
fun ShellViewModel.showServerBudgets() {
    if (state.value.shell == null) {
        openFromSettings(ShellRoute.OnboardingFiles, SettingsPage.SERVER)
        onboardingRefresh()
    } else {
        showServer()
        mutate { it.copy(settingsReturn = true, settingsPage = SettingsPage.SERVER) }
    }
}

fun ShellViewModel.requestRemoveInstance(instanceId: String) =
    openFromSettings(ShellRoute.ConfirmRemoveInstance(instanceId), SettingsPage.SERVER)

fun ShellViewModel.confirmRemoveInstance() {
    val id = (state.value.route as? ShellRoute.ConfirmRemoveInstance)?.instanceId ?: return
    removeInstance(id)
    returnToSettings(SettingsPage.SERVER)
}

fun ShellViewModel.connectInstance(
    address: String,
    password: String,
) {
    viewModelScope.launch {
        mutate { it.copy(serverError = null, syncing = true, syncProgress = 0f) }
        val result = withContext(io) { sync.connect(address, password) }
        val settings = withContext(io) { library.settings() }
        mutate {
            it.copy(
                syncing = false,
                syncProgress = null,
                serverError = result.error,
                remoteFiles = if (result.error == null) result.files else it.remoteFiles,
                settings = settings,
                instances = settings.instances,
                activeInstance = settings.activeInstance(),
                instanceReachable = if (result.error == null) true else it.instanceReachable,
                signedIn = sync.signedIn(),
                openIdAvailable = false,
            )
        }
        if (result.error == null) {
            probeOpenId(address.ifBlank { settings.activeInstance()?.address.orEmpty() })
        }
    }
}

fun ShellViewModel.beginOpenId(address: String) {
    viewModelScope.launch {
        mutate { it.copy(serverError = null) }
        val redirect =
            withContext(io) {
                sync.beginOpenId(address, ShellViewModel.OPEN_ID_RETURN)
            }
        if (redirect.isNullOrBlank()) {
            mutate { it.copy(serverError = SyncCopy.ADDRESS_FAILED, openIdRedirectUrl = null) }
        } else {
            mutate {
                it.copy(
                    openIdRedirectUrl = redirect,
                    openIdPendingAddress = SyncCoordinator.normalizeAddress(address) ?: address.trim().trimEnd('/'),
                )
            }
        }
    }
}

fun ShellViewModel.clearOpenIdRedirect() {
    mutate { it.copy(openIdRedirectUrl = null) }
}

fun ShellViewModel.completeOpenId(callbackUrl: String?) {
    if (callbackUrl.isNullOrBlank()) return
    // Any app can send a link; only the sign-in return address is a sign-in.
    if (!ShellViewModel.isOpenIdReturn(callbackUrl)) return
    val token =
        extractOAuthToken(callbackUrl) ?: run {
            mutate { it.copy(serverError = SyncCopy.SIGN_IN_FAILED) }
            return
        }
    viewModelScope.launch {
        // After a restart the pending address lives only in onboarding.json (spec 011 R5).
        val saved =
            state.value.onboarding?.progress
                ?: withContext(io) { library.onboarding().read() }
        val onboarding = saved?.openIdPendingAddress != null
        val address = state.value.openIdPendingAddress ?: saved?.openIdPendingAddress ?: return@launch
        val label = if (onboarding) saved?.instanceLabel else null
        mutate { it.copy(syncing = true, syncProgress = 0f, serverError = null) }
        val result = withContext(io) { sync.completeOpenId(address, token, label) }
        val settings = withContext(io) { library.settings() }
        mutate {
            it.copy(
                syncing = false,
                syncProgress = null,
                serverError = if (onboarding) null else result.error,
                remoteFiles = if (result.error == null) result.files else it.remoteFiles,
                settings = settings,
                instances = settings.instances,
                activeInstance = settings.activeInstance(),
                instanceReachable = if (result.error == null) true else false,
                signedIn = sync.signedIn(),
                openIdPendingAddress = null,
                openIdRedirectUrl = null,
                route = if (onboarding) it.route else ShellRoute.Settings,
            )
        }
        if (onboarding) finishBrowserSignIn(saved!!, result.error)
    }
}

fun ShellViewModel.requestInstanceSwitch(instanceId: String) {
    mutate { it.copy(settingsPage = SettingsPage.SERVER) }
    val settings = state.value.settings
    val target = settings.instances.firstOrNull { it.id == instanceId } ?: return
    val current = settings.activeInstance()
    if (current == null || current.id == target.id) {
        confirmInstanceSwitch(instanceId)
        return
    }
    mutate {
        it.copy(
            route = ShellRoute.ConfirmInstanceSwitch,
            switchInstanceTarget = target,
        )
    }
}

fun ShellViewModel.confirmInstanceSwitch(instanceId: String? = null) {
    val id = instanceId ?: state.value.switchInstanceTarget?.id ?: return
    viewModelScope.launch {
        withContext(io) { sync.makeActive(id) }
        val settings = withContext(io) { library.settings() }
        mutate {
            it.copy(
                settings = settings,
                instances = settings.instances,
                activeInstance = settings.activeInstance(),
                instanceReachable = null,
                remoteFiles = emptyList(),
                switchInstanceTarget = null,
                route = ShellRoute.Settings,
                settingsReturn = false,
                signedIn = sync.signedIn(),
            )
        }
        probeReachability()
        // A signed-in server goes straight to picking which of its budgets to use.
        if (sync.signedIn()) {
            openFromSettings(ShellRoute.OnboardingFiles, SettingsPage.SERVER)
            onboardingRefresh()
        }
    }
}

fun ShellViewModel.cancelInstanceSwitch() {
    mutate {
        it.copy(route = ShellRoute.Settings, switchInstanceTarget = null, settingsReturn = false)
    }
}

fun ShellViewModel.removeInstance(instanceId: String) {
    viewModelScope.launch {
        withContext(io) { sync.removeInstance(instanceId) }
        val settings = withContext(io) { library.settings() }
        mutate {
            it.copy(
                settings = settings,
                instances = settings.instances,
                activeInstance = settings.activeInstance(),
                signedIn = sync.signedIn(),
                remoteFiles = if (settings.activeInstanceId == null) emptyList() else it.remoteFiles,
            )
        }
    }
}

fun ShellViewModel.signOutInstance() {
    signOut(preferSettings = true)
}

internal fun ShellViewModel.probeOpenId(address: String) {
    if (address.isBlank()) return
    viewModelScope.launch {
        val methods = withContext(io) { sync.loginMethods(address) }
        mutate { it.copy(openIdAvailable = methods.any { method -> method.equals("openid", ignoreCase = true) }) }
    }
}

internal fun ShellViewModel.probeReachability() {
    val address = state.value.activeInstance?.address ?: return
    viewModelScope.launch {
        val reachable =
            withContext(io) {
                val parsed = ServerAddress.parse(address) ?: return@withContext false
                runCatching { probe.check(parsed) is ServerCheck.Ready }.getOrElse { false }
            }
        mutate { it.copy(instanceReachable = reachable) }
    }
}

private fun ShellViewModel.extractOAuthToken(callbackUrl: String): String? {
    val uri = android.net.Uri.parse(callbackUrl)
    val fromQuery = uri.getQueryParameter("token") ?: uri.getQueryParameter("access_token")
    if (!fromQuery.isNullOrBlank()) return fromQuery
    val fragment = uri.fragment.orEmpty()
    if (fragment.isBlank()) return null
    return fragment
        .split('&')
        .mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) null else part.substring(0, idx) to part.substring(idx + 1)
        }.firstOrNull { (key, _) -> key == "token" || key == "access_token" }
        ?.second
        ?.takeIf { it.isNotBlank() }
}
