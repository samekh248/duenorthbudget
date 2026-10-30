package app.duenorth.budget

import app.duenorth.budget.core.SavedInstance
import app.duenorth.budget.core.ServerAddress
import app.duenorth.budget.core.ServerCheck
import app.duenorth.budget.core.SyncCoordinator
import app.duenorth.budget.core.SyncCopy
import app.duenorth.budget.core.activeInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The servers' own state, inside [ShellUiState.servers]. */
data class ServersState(
    /** Whether the active server answered its last check; null until checked. */
    val reachable: Boolean? = null,
    /** The browser page an OpenID sign-in opens; cleared once it opens. */
    val openIdRedirectUrl: String? = null,
    /** The active server offers OpenID sign-in. */
    val openIdAvailable: Boolean = false,
    /** The address an OpenID sign-in is for, while the browser has it. */
    val openIdPendingAddress: String? = null,
    /** The server a switch would make active, while the page asks. */
    val switchTarget: SavedInstance? = null,
)

/** Servers: the instances list, OpenID sign-in, switching and removing an instance, and server checks. */
class ServersHolder internal constructor(
    private val host: ShellHost,
) {
    private val state get() = host.state.value

    fun show() {
        host.scope.launch {
            val result =
                if (host.sync.signedIn()) {
                    withContext(host.io) { host.sync.refreshRemote() }
                } else {
                    null
                }
            host.mutate { current ->
                val status = current.settings.openBudgetId?.let { host.sync.status(it) } ?: current.syncStatus
                current.copy(
                    route = ShellRoute.Server,
                    remoteFiles = result?.files ?: host.sync.remoteFiles,
                    serverError = result?.error,
                    syncStatus = status,
                    signedIn = host.sync.signedIn(),
                )
            }
        }
    }

    fun showConflicts() {
        val id = state.settings.openBudgetId
        host.scope.launch {
            val conflicts = if (id == null) emptyList() else withContext(host.io) { host.sync.conflicts(id) }
            if (id != null) withContext(host.io) { host.sync.markConflictsSeen(id) }
            host.mutate { it.copy(route = ShellRoute.Conflicts, conflicts = conflicts) }
        }
    }

    /** Settings → server → server budgets: the server page with a budget open, the pick-a-budget page without (research R5). */
    fun showBudgets() {
        if (state.shell == null) {
            host.openFromSettings(ShellRoute.OnboardingFiles, SettingsPage.SERVER)
            host.refreshOnboardingFiles()
        } else {
            show()
            host.mutate { it.copy(settingsReturn = true, settingsPage = SettingsPage.SERVER) }
        }
    }

    fun requestRemove(instanceId: String) = host.openFromSettings(ShellRoute.ConfirmRemoveInstance(instanceId), SettingsPage.SERVER)

    fun confirmRemove() {
        val id = (state.route as? ShellRoute.ConfirmRemoveInstance)?.instanceId ?: return
        remove(id)
        host.returnToSettings(SettingsPage.SERVER)
    }

    fun connect(
        address: String,
        password: String,
    ) {
        host.scope.launch {
            host.mutate { it.copy(serverError = null, syncing = true, syncProgress = 0f) }
            val result = withContext(host.io) { host.sync.connect(address, password) }
            val settings = withContext(host.io) { host.library.settings() }
            host.mutate {
                it.copy(
                    syncing = false,
                    syncProgress = null,
                    serverError = result.error,
                    remoteFiles = if (result.error == null) result.files else it.remoteFiles,
                    settings = settings,
                    signedIn = host.sync.signedIn(),
                    servers =
                        it.servers.copy(
                            reachable = if (result.error == null) true else it.servers.reachable,
                            openIdAvailable = false,
                        ),
                )
            }
            if (result.error == null) {
                probeOpenId(address.ifBlank { settings.activeInstance()?.address.orEmpty() })
            }
        }
    }

    fun beginOpenId(address: String) {
        host.scope.launch {
            host.mutate { it.copy(serverError = null) }
            val redirect =
                withContext(host.io) {
                    host.sync.beginOpenId(address, ShellViewModel.OPEN_ID_RETURN)
                }
            if (redirect.isNullOrBlank()) {
                host.mutate { it.copy(serverError = SyncCopy.ADDRESS_FAILED, servers = it.servers.copy(openIdRedirectUrl = null)) }
            } else {
                host.mutate {
                    it.copy(
                        servers =
                            it.servers.copy(
                                openIdRedirectUrl = redirect,
                                openIdPendingAddress = SyncCoordinator.normalizeAddress(address) ?: address.trim().trimEnd('/'),
                            ),
                    )
                }
            }
        }
    }

    fun clearOpenIdRedirect() {
        host.mutate { it.copy(servers = it.servers.copy(openIdRedirectUrl = null)) }
    }

    fun completeOpenId(callbackUrl: String?) {
        if (callbackUrl.isNullOrBlank()) return
        // Any app can send a link; only the sign-in return address is a sign-in.
        if (!ShellViewModel.isOpenIdReturn(callbackUrl)) return
        val token =
            extractOAuthToken(callbackUrl) ?: run {
                host.mutate { it.copy(serverError = SyncCopy.SIGN_IN_FAILED) }
                return
            }
        host.scope.launch {
            // After a restart the pending address lives only in onboarding.json (spec 011 R5).
            val saved =
                state.onboarding?.progress
                    ?: withContext(host.io) { host.library.onboarding().read() }
            val onboarding = saved?.openIdPendingAddress != null
            val address = state.servers.openIdPendingAddress ?: saved?.openIdPendingAddress ?: return@launch
            val label = if (onboarding) saved?.instanceLabel else null
            host.mutate { it.copy(syncing = true, syncProgress = 0f, serverError = null) }
            val result = withContext(host.io) { host.sync.completeOpenId(address, token, label) }
            val settings = withContext(host.io) { host.library.settings() }
            host.mutate {
                it.copy(
                    syncing = false,
                    syncProgress = null,
                    serverError = if (onboarding) null else result.error,
                    remoteFiles = if (result.error == null) result.files else it.remoteFiles,
                    settings = settings,
                    signedIn = host.sync.signedIn(),
                    servers =
                        it.servers.copy(
                            reachable = if (result.error == null) true else false,
                            openIdPendingAddress = null,
                            openIdRedirectUrl = null,
                        ),
                    route = if (onboarding) it.route else ShellRoute.Settings,
                )
            }
            if (onboarding) host.finishBrowserSignIn(saved!!, result.error)
        }
    }

    fun requestSwitch(instanceId: String) {
        host.mutate { it.copy(settingsPage = SettingsPage.SERVER) }
        val settings = state.settings
        val target = settings.instances.firstOrNull { it.id == instanceId } ?: return
        val current = settings.activeInstance()
        if (current == null || current.id == target.id) {
            confirmSwitch(instanceId)
            return
        }
        host.mutate {
            it.copy(
                route = ShellRoute.ConfirmInstanceSwitch,
                servers = it.servers.copy(switchTarget = target),
            )
        }
    }

    fun confirmSwitch(instanceId: String? = null) {
        val id = instanceId ?: state.servers.switchTarget?.id ?: return
        host.scope.launch {
            withContext(host.io) { host.sync.makeActive(id) }
            val settings = withContext(host.io) { host.library.settings() }
            host.mutate {
                it.copy(
                    settings = settings,
                    remoteFiles = emptyList(),
                    servers = it.servers.copy(reachable = null, switchTarget = null),
                    route = ShellRoute.Settings,
                    settingsReturn = false,
                    signedIn = host.sync.signedIn(),
                )
            }
            probeReachability()
            // A signed-in server goes straight to picking which of its budgets to use.
            if (host.sync.signedIn()) {
                host.openFromSettings(ShellRoute.OnboardingFiles, SettingsPage.SERVER)
                host.refreshOnboardingFiles()
            }
        }
    }

    fun cancelSwitch() {
        host.mutate {
            it.copy(route = ShellRoute.Settings, servers = it.servers.copy(switchTarget = null), settingsReturn = false)
        }
    }

    fun remove(instanceId: String) {
        host.scope.launch {
            withContext(host.io) { host.sync.removeInstance(instanceId) }
            val settings = withContext(host.io) { host.library.settings() }
            host.mutate {
                it.copy(
                    settings = settings,
                    signedIn = host.sync.signedIn(),
                    remoteFiles = if (settings.activeInstanceId == null) emptyList() else it.remoteFiles,
                )
            }
        }
    }

    fun signOut() {
        host.signOut(preferSettings = true)
    }

    internal fun probeOpenId(address: String) {
        if (address.isBlank()) return
        host.scope.launch {
            val methods = withContext(host.io) { host.sync.loginMethods(address) }
            host.mutate {
                it.copy(
                    servers =
                        it.servers.copy(
                            openIdAvailable =
                                methods.any { method ->
                                    method.equals("openid", ignoreCase = true)
                                },
                        ),
                )
            }
        }
    }

    internal fun probeReachability() {
        val address = state.activeInstance?.address ?: return
        host.scope.launch {
            val reachable =
                withContext(host.io) {
                    val parsed = ServerAddress.parse(address) ?: return@withContext false
                    runCatching { host.probe.check(parsed) is ServerCheck.Ready }.getOrElse { false }
                }
            host.mutate { it.copy(servers = it.servers.copy(reachable = reachable)) }
        }
    }

    private fun extractOAuthToken(callbackUrl: String): String? {
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
}
