package app.duenorth.budget

import app.duenorth.budget.core.BudgetSummary
import app.duenorth.budget.core.CreateResult
import app.duenorth.budget.core.OnboardingPath
import app.duenorth.budget.core.OpenResult
import app.duenorth.budget.core.RemoteFile
import app.duenorth.budget.core.SampleBudgets
import app.duenorth.budget.core.SyncCopy
import app.duenorth.budget.core.UnlockResult
import app.duenorth.budget.core.UploadResult
import app.duenorth.budget.core.syncFraction
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The budget files' own state, inside [ShellUiState.budgetFiles]. */
data class BudgetFilesUi(
    /** The phone's budget a switch would open, while the page asks. */
    val switchTarget: BudgetSummary? = null,
    /** Where keeping the current budget, or a failed open, goes back to. */
    val switchReturnRoute: ShellRoute = ShellRoute.Home,
    /** The server budget being opened, unlocked or confirmed. */
    val pendingFile: RemoteFile? = null,
    /** The server budget the phone's budget would be uploaded onto, while the page asks. */
    val replaceFile: RemoteFile? = null,
    /** The phone's budgets that picking [pendingFile] clears. */
    val replaceBudgets: List<BudgetSummary> = emptyList(),
    val passwordError: String? = null,
    /** The budget password is asked for every time it opens, rather than remembered. */
    val askEachTime: Boolean = true,
)

/**
 * Budget files: creating, opening, switching and replacing a budget, unlocking it, signing out and syncing it. A
 * budget made or opened can ask about experimental features through [settings]. [samples] are this build's sample
 * budgets, offered in [developerMode].
 */
class BudgetFilesHolder internal constructor(
    private val host: ShellHost,
    private val samples: SampleBudgets?,
    private val developerMode: Boolean,
    private val settings: SettingsHolder,
) {
    private val state get() = host.state.value

    fun showSamples() {
        if (!developerMode) return
        host.clearHistory()
        host.mutate { it.copy(route = ShellRoute.SampleBudgets, createError = null, confirm = null) }
    }

    fun create(
        name: String,
        currency: String,
    ) {
        host.scope.launch {
            when (val result = withContext(host.io) { createBudget(name, currency) }) {
                is CreateResult.Rejected -> host.mutate { it.copy(createError = result.reason) }
                is CreateResult.Created -> {
                    // A budget made on the server path is sent to that server in the background (spec 011 US5).
                    val send =
                        state.onboarding
                            ?.progress
                            ?.path == OnboardingPath.SERVER && host.sync.signedIn()
                    val settingUp = state.onboarding != null
                    host.finishOnboarding()
                    // Debug builds fill the file from a sample, so only release builds have steps left to do.
                    if (samples == null) host.startGetStarted(result.id)
                    val toSettings = state.settingsReturn
                    host.mutate {
                        it.copy(
                            createError = null,
                            route = if (toSettings) ShellRoute.Settings else ShellRoute.Home,
                            settingsPage = if (toSettings) SettingsPage.BUDGET else it.settingsPage,
                            settingsReturn = false,
                        )
                    }
                    host.reload()
                    settings.offerExperimental(always = settingUp)
                    if (toSettings) settings.refresh()
                    if (send) uploadNew()
                }
            }
        }
    }

    /** Debug builds seed every new budget with the full walkthrough data; release builds start empty. */
    internal fun createBudget(
        name: String,
        currency: String,
    ): CreateResult {
        val samples = samples ?: return host.library.create(name, currency)
        return samples.create(host.library, samples.walkthrough, name.ifBlank { samples.walkthroughName })
    }

    fun createSample(datasetId: String) {
        val samples = samples ?: return
        host.scope.launch {
            when (val result = withContext(host.io) { samples.create(host.library, datasetId) }) {
                is CreateResult.Rejected -> host.mutate { it.copy(createError = result.reason) }
                is CreateResult.Created -> {
                    val settingUp = state.onboarding != null
                    host.finishOnboarding()
                    if (datasetId == samples.brandNew) host.startGetStarted(result.id)
                    host.mutate { it.copy(createError = null, route = ShellRoute.Home) }
                    host.reload()
                    settings.offerExperimental(always = settingUp)
                }
            }
        }
    }

    fun requestSwitch(id: String) {
        val target = state.budgets.find { it.id == id } ?: return
        if (target.id == state.settings.openBudgetId) {
            host.mutate { it.copy(route = ShellRoute.Home) }
            return
        }
        host.mutate {
            it.copy(
                route = ShellRoute.ConfirmSwitch,
                budgetFiles = it.budgetFiles.copy(switchTarget = target, pendingFile = null, switchReturnRoute = it.route),
            )
        }
    }

    fun cancelSwitch() {
        host.mutate {
            it.copy(
                route = it.budgetFiles.switchReturnRoute,
                budgetFiles = it.budgetFiles.copy(switchTarget = null, pendingFile = null),
            )
        }
    }

    fun confirmSwitch() {
        val pending = state.budgetFiles.pendingFile
        if (pending != null) {
            beginOpen(pending)
            return
        }
        val target = state.budgetFiles.switchTarget ?: return
        host.scope.launch {
            withContext(host.io) { host.library.switchTo(target.id) }
            host.mutate { it.copy(route = ShellRoute.Home, budgetFiles = it.budgetFiles.copy(switchTarget = null)) }
            host.reload()
            settings.offerExperimental()
        }
    }

    fun signOut(preferSettings: Boolean = false) {
        // Non-blocking: detach token on IO while UI stays responsive even if sync is running.
        host.scope.launch {
            withContext(host.io) { host.sync.signOut() }
            val settings = withContext(host.io) { host.library.settings() }
            host.mutate { current ->
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

    fun requestRemote(file: RemoteFile) {
        // The budget already open is not downloaded over itself; picking it just goes back to it.
        if (file.fileId == state.settings.openBudgetId) {
            host.mutate { it.copy(route = ShellRoute.Home, settingsReturn = false, serverError = null) }
            return
        }
        // Keep and failures come back to the list the budget was picked from.
        host.mutate {
            it.copy(
                budgetFiles =
                    it.budgetFiles.copy(
                        switchReturnRoute =
                            it.route.takeIf(::isPickedFrom) ?: it.budgetFiles.switchReturnRoute,
                    ),
            )
        }
        if (file.encryptKeyId != null && !host.sync.isUnlocked(file.fileId)) {
            host.mutate {
                it.copy(
                    route = ShellRoute.BudgetPassword,
                    budgetFiles = it.budgetFiles.copy(pendingFile = file, passwordError = null, askEachTime = true),
                )
            }
            return
        }
        confirmOrOpen(file)
    }

    /** `use` on the replace confirmation: download the server budget, then clear the others. */
    fun confirmReplaceBudget() {
        val current = state
        val file = current.budgetFiles.pendingFile ?: return
        if (current.syncing) return
        // The download's progress shows over the list it was picked from.
        host.mutate { it.copy(route = it.budgetFiles.switchReturnRoute.takeIf(::isPickedFrom) ?: ShellRoute.Server) }
        beginOpen(file)
    }

    /** `keep` (or back) on the replace confirmation: the phone's budget stays, the list shows again. */
    fun cancelReplaceBudget() {
        host.mutate {
            it.copy(
                route = it.budgetFiles.switchReturnRoute.takeIf(::isPickedFrom) ?: ShellRoute.Server,
                budgetFiles = it.budgetFiles.copy(pendingFile = null, replaceBudgets = emptyList(), passwordError = null),
            )
        }
    }

    fun submitPassword(
        password: String,
        askEachTime: Boolean,
    ) {
        val file = state.budgetFiles.pendingFile
        val openId = state.settings.openBudgetId
        host.scope.launch {
            if (file != null) {
                when (val result = withContext(host.io) { host.sync.unlock(file, password, askEachTime) }) {
                    is UnlockResult.Failed -> host.mutate { it.copy(budgetFiles = it.budgetFiles.copy(passwordError = result.reason)) }
                    UnlockResult.Unlocked -> confirmOrOpen(file)
                }
            } else if (openId != null) {
                val remote =
                    RemoteFile(
                        fileId = openId,
                        groupId = "",
                        name =
                            state.budgets
                                .find { it.id == openId }
                                ?.name
                                .orEmpty(),
                        encryptKeyId = "local",
                    )
                when (val result = withContext(host.io) { host.sync.unlock(remote, password, askEachTime) }) {
                    is UnlockResult.Failed -> host.mutate { it.copy(budgetFiles = it.budgetFiles.copy(passwordError = result.reason)) }
                    UnlockResult.Unlocked -> {
                        host.mutate {
                            it.copy(
                                locked = false,
                                route = ShellRoute.Home,
                                budgetFiles = it.budgetFiles.copy(passwordError = null),
                            )
                        }
                        host.reload()
                    }
                }
            }
        }
    }

    fun setAskEachTime(value: Boolean) {
        host.mutate { it.copy(budgetFiles = it.budgetFiles.copy(askEachTime = value)) }
    }

    fun syncNow() {
        val id = state.settings.openBudgetId ?: return
        host.scope.launch {
            host.mutate { it.copy(syncing = true, syncProgress = 0.08f) }
            withContext(host.io) { runCatching { host.sync.sync(id) } }
            host.mutate { it.copy(syncing = false, syncProgress = null) }
            host.reload()
        }
    }

    /** The inbox `•••` → sync: the same sync, then one line under the inbox's totals says how it went. */
    fun syncInbox() {
        val id = state.settings.openBudgetId ?: return
        if (state.syncing) return
        host.scope.launch {
            host.mutate { it.copy(syncing = true, syncProgress = 0.08f, inbox = it.inbox.copy(syncNotice = null)) }
            val result = withContext(host.io) { runCatching { host.sync.sync(id) }.getOrNull() }
            val notice = result?.status?.takeIf { it.isNotBlank() } ?: SyncCopy.LAST_FAILED
            host.mutate { it.copy(syncing = false, syncProgress = null, inbox = it.inbox.copy(syncNotice = notice)) }
            host.reload()
        }
    }

    fun uploadNew() {
        val id = state.settings.openBudgetId ?: return
        host.scope.launch {
            host.mutate { it.copy(syncing = true, syncProgress = 0.08f, serverError = null) }
            val result = withContext(host.io) { host.sync.uploadNew(id) }
            host.mutate {
                it.copy(
                    syncing = false,
                    syncProgress = null,
                    serverError = (result as? UploadResult.Failed)?.reason,
                    remoteFiles = host.sync.remoteFiles,
                    signedIn = host.sync.signedIn(),
                )
            }
        }
    }

    fun requestReplace(file: RemoteFile) {
        host.mutate { it.copy(route = ShellRoute.ConfirmReplace, budgetFiles = it.budgetFiles.copy(replaceFile = file)) }
    }

    fun cancelReplace() {
        host.mutate { it.copy(route = ShellRoute.Server, budgetFiles = it.budgetFiles.copy(replaceFile = null)) }
    }

    fun confirmReplace() {
        val file = state.budgetFiles.replaceFile ?: return
        val id = state.settings.openBudgetId ?: return
        host.scope.launch {
            val result = withContext(host.io) { host.sync.uploadOnto(id, file) }
            host.mutate {
                it.copy(
                    route = ShellRoute.Server,
                    serverError = (result as? UploadResult.Failed)?.reason,
                    remoteFiles = host.sync.remoteFiles,
                    signedIn = host.sync.signedIn(),
                    budgetFiles = it.budgetFiles.copy(replaceFile = null),
                )
            }
        }
    }

    /**
     * A server budget replaces whatever is on the phone. When other budgets are there, the
     * person sees which ones get cleared first; otherwise it opens straight away.
     */
    private fun confirmOrOpen(file: RemoteFile) {
        host.scope.launch {
            val cleared = withContext(host.io) { host.library.list() }.filter { it.id != file.fileId }
            if (cleared.isEmpty()) {
                beginOpen(file)
                return@launch
            }
            host.mutate {
                it.copy(
                    route = ShellRoute.ConfirmReplaceBudget,
                    budgetFiles = it.budgetFiles.copy(pendingFile = file, replaceBudgets = cleared, passwordError = null),
                )
            }
        }
    }

    private fun isServerList(route: ShellRoute): Boolean = route == ShellRoute.OnboardingFiles || route == ShellRoute.Server

    /** Where a server budget can be picked from: the server lists, or settings → budget's open-budget box. */
    private fun isPickedFrom(route: ShellRoute): Boolean = isServerList(route) || route == ShellRoute.Settings

    private fun beginOpen(file: RemoteFile) {
        host.scope.launch {
            host.mutate { it.copy(syncing = true, syncProgress = 0f, serverError = null) }
            val result =
                withContext(host.io) {
                    host.sync.open(file, replace = true) { received, total ->
                        host.mutate { it.copy(syncing = true, syncProgress = syncFraction(received, total)) }
                    }
                }
            when (result) {
                OpenResult.Opened -> {
                    val settingUp = state.onboarding != null
                    host.finishOnboarding()
                    host.mutate {
                        it.copy(
                            syncing = false,
                            syncProgress = null,
                            locked = false,
                            route = ShellRoute.Home,
                            settingsReturn = false,
                            budgetFiles = it.budgetFiles.copy(pendingFile = null, replaceBudgets = emptyList()),
                        )
                    }
                    host.reload()
                    settings.offerExperimental(always = settingUp)
                }
                OpenResult.NeedsPassword ->
                    host.mutate {
                        it.copy(
                            syncing = false,
                            syncProgress = null,
                            route = ShellRoute.BudgetPassword,
                            budgetFiles = it.budgetFiles.copy(pendingFile = file),
                        )
                    }
                is OpenResult.Failed ->
                    host.mutate {
                        it.copy(
                            syncing = false,
                            syncProgress = null,
                            serverError = result.reason,
                            route =
                                when {
                                    it.onboarding != null -> ShellRoute.OnboardingFiles
                                    isPickedFrom(it.budgetFiles.switchReturnRoute) -> it.budgetFiles.switchReturnRoute
                                    else -> ShellRoute.Server
                                },
                            budgetFiles = it.budgetFiles.copy(replaceBudgets = emptyList()),
                        )
                    }
            }
        }
    }
}
