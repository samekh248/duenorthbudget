package app.duenorth.budget

import app.duenorth.budget.core.CreateReturn
import app.duenorth.budget.core.OnboardingCopy
import app.duenorth.budget.core.OnboardingOrigin
import app.duenorth.budget.core.OnboardingPath
import app.duenorth.budget.core.OnboardingProgress
import app.duenorth.budget.core.OnboardingStep
import app.duenorth.budget.core.RemoteFile
import app.duenorth.budget.core.ServerAddress
import app.duenorth.budget.core.ServerCheck
import app.duenorth.budget.core.SignInMethod
import app.duenorth.budget.core.activeInstance
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** First-run state: saved [progress] plus what only lives while the page is open (spec 011). */
data class OnboardingUi(
    val progress: OnboardingProgress,
    val checking: Boolean = false,
    val addressError: String? = null,
    val signInError: String? = null,
)

internal fun ShellRoute.isOnboarding(): Boolean =
    this == ShellRoute.Welcome ||
        this == ShellRoute.OnboardingAddress ||
        this == ShellRoute.OnboardingSignIn ||
        this == ShellRoute.OnboardingFiles

/**
 * First run (spec 011), and settings' add server path through the same pages (spec 012): welcome, the server's
 * address and sign-in, its budgets, or a budget made on the phone. Progress is saved as it goes, so a restart
 * resumes on the same page.
 */
class OnboardingHolder internal constructor(
    private val host: ShellHost,
    private val settings: SettingsHolder,
    private val servers: ServersHolder,
    private val budgetFiles: BudgetFilesHolder,
) {
    /** The running server check or sign-in, cancelled by back (spec 011 R11). */
    private var job: Job? = null

    /** `onboarding.json` writes, one at a time and in order. */
    private val writes = host.orderedIo

    private val state get() = host.state.value

    /** The page saved progress resumes on. A sign-in step without a checked address goes back to the address. */
    internal fun route(progress: OnboardingProgress?): ShellRoute =
        when (progress?.step ?: OnboardingStep.WELCOME) {
            OnboardingStep.WELCOME -> ShellRoute.Welcome
            OnboardingStep.ADDRESS -> ShellRoute.OnboardingAddress
            OnboardingStep.SIGN_IN ->
                if (progress?.readyAddress == null) ShellRoute.OnboardingAddress else ShellRoute.OnboardingSignIn
            OnboardingStep.FILES ->
                when {
                    host.sync.signedIn() -> ShellRoute.OnboardingFiles
                    progress?.readyAddress != null -> ShellRoute.OnboardingSignIn
                    else -> ShellRoute.OnboardingAddress
                }
            OnboardingStep.CREATE -> ShellRoute.Create
        }

    /** Reads `onboarding.json` before the first load. A phone that already has a budget drops it. */
    internal suspend fun restore() {
        val stored =
            withContext(host.io) {
                val store = host.library.onboarding()
                val progress = store.read() ?: return@withContext null
                // An add-server path started in settings resumes even with budgets on the phone (spec 012).
                if (progress.origin == OnboardingOrigin.SETTINGS) return@withContext progress
                if (host.library.list().isNotEmpty()) {
                    store.clear()
                    null
                } else {
                    progress
                }
            } ?: return
        if (stored.origin == OnboardingOrigin.SETTINGS) {
            host.mutate {
                it.copy(
                    onboarding = OnboardingUi(stored),
                    route = route(stored),
                    settingsReturn = true,
                    settingsPage = SettingsPage.SERVER,
                )
            }
            return
        }
        host.mutate { it.copy(onboarding = OnboardingUi(stored)) }
    }

    /** Settings → server → add: the onboarding server path, starting on an empty address (spec 012 FR-005). */
    fun addServer() {
        cancelJob()
        val progress =
            OnboardingProgress(
                step = OnboardingStep.ADDRESS,
                path = OnboardingPath.SERVER,
                origin = OnboardingOrigin.SETTINGS,
            )
        host.mutate { it.copy(onboarding = OnboardingUi(progress)) }
        host.scope.launch(writes) { host.library.onboarding().write(progress) }
        settings.openFrom(ShellRoute.OnboardingAddress, SettingsPage.SERVER)
    }

    /** The settings-origin path is over without a pick: drop its progress and show the server item. */
    private fun leaveSettings() {
        cancelJob()
        host.mutate { it.copy(onboarding = null) }
        host.scope.launch(writes) { host.library.onboarding().clear() }
        settings.returnTo(SettingsPage.SERVER)
    }

    /** After the first load, the file list needs the server's budgets again. */
    internal fun resume() {
        if (state.route == ShellRoute.OnboardingFiles) refreshFiles()
    }

    private fun update(transform: (OnboardingUi) -> OnboardingUi) {
        var saved: OnboardingProgress? = null
        host.mutate { current ->
            val ui = current.onboarding ?: return@mutate current
            val next = transform(ui)
            if (next.progress != ui.progress) saved = next.progress
            current.copy(onboarding = next)
        }
        val progress = saved ?: return
        host.scope.launch(writes) { host.library.onboarding().write(progress) }
    }

    private fun progress(transform: (OnboardingProgress) -> OnboardingProgress) = update { it.copy(progress = transform(it.progress)) }

    private fun cancelJob() {
        job?.cancel()
        job = null
    }

    fun chooseServer() {
        val saved =
            state.activeInstance?.address ?: state.instances
                .firstOrNull()
                ?.address
        update {
            it.copy(
                progress =
                    it.progress.copy(
                        step = OnboardingStep.ADDRESS,
                        path = OnboardingPath.SERVER,
                        addressText = it.progress.addressText.ifBlank { saved.orEmpty() },
                    ),
                addressError = null,
            )
        }
        host.mutate { it.copy(route = ShellRoute.OnboardingAddress) }
    }

    fun choosePhone() {
        cancelJob()
        update {
            it.copy(
                progress =
                    it.progress.copy(
                        step = OnboardingStep.CREATE,
                        path = OnboardingPath.PHONE,
                        createReturn = CreateReturn.WELCOME,
                    ),
                checking = false,
            )
        }
        host.mutate { it.copy(route = ShellRoute.Create, createError = null) }
    }

    fun setAddress(text: String) {
        val before =
            state.onboarding
                ?.progress
                ?.addressText
        if (text == before) return
        cancelJob()
        update {
            it.copy(
                progress = it.progress.copy(addressText = text, readyAddress = null, methods = emptySet()),
                checking = false,
                addressError = null,
            )
        }
    }

    /** `next` on the address page: one server check, then the sign-in that server takes. */
    fun check() {
        val ui = state.onboarding ?: return
        if (ui.checking) return
        val parsed = ServerAddress.parse(ui.progress.addressText)
        if (parsed == null) {
            update { it.copy(addressError = OnboardingCopy.ENTER_ADDRESS) }
            return
        }
        update { it.copy(checking = true, addressError = null) }
        job =
            host.scope.launch {
                val check = withContext(host.io) { host.probe.check(parsed) }
                if (check is ServerCheck.Ready && !check.secure && parsed.candidates.size > 1) {
                    // Only http:// answered a bare address. Say so, and let the person choose it, instead of
                    // sending the password unencrypted without asking.
                    update {
                        it.copy(
                            progress = it.progress.copy(addressText = check.address),
                            checking = false,
                            addressError = OnboardingCopy.HTTP_ONLY,
                        )
                    }
                } else if (check is ServerCheck.Ready) {
                    update {
                        it.copy(
                            progress =
                                it.progress.copy(
                                    step = OnboardingStep.SIGN_IN,
                                    readyAddress = check.address,
                                    methods = check.methods,
                                    instanceLabel = it.progress.instanceLabel ?: parsed.defaultLabel,
                                ),
                            checking = false,
                            signInError = null,
                        )
                    }
                    host.mutate { it.copy(route = ShellRoute.OnboardingSignIn) }
                } else {
                    update { it.copy(checking = false, addressError = OnboardingCopy.message(check)) }
                }
            }
    }

    fun setLabel(text: String) {
        progress { it.copy(instanceLabel = text) }
    }

    fun signIn(password: String) {
        val ui = state.onboarding ?: return
        if (ui.checking) return
        val address = ui.progress.readyAddress ?: return
        if (SignInMethod.PASSWORD !in ui.progress.methods) return
        update { it.copy(checking = true, signInError = null) }
        job =
            host.scope.launch {
                val result = withContext(host.io) { host.sync.connect(address, password, ui.progress.instanceLabel) }
                val settings = withContext(host.io) { host.library.settings() }
                // From first run or from settings, a signed-in server goes on to picking its budget.
                if (result.error == null) {
                    update {
                        it.copy(progress = it.progress.copy(step = OnboardingStep.FILES), checking = false, signInError = null)
                    }
                } else {
                    update { it.copy(checking = false, signInError = result.error) }
                }
                host.mutate {
                    it.copy(
                        route = if (result.error == null) ShellRoute.OnboardingFiles else it.route,
                        remoteFiles = if (result.error == null) result.files else it.remoteFiles,
                        serverError = null,
                        settings = settings,
                        servers = it.servers.copy(reachable = if (result.error == null) true else it.servers.reachable),
                        signedIn = host.sync.signedIn(),
                    )
                }
            }
    }

    /** `use browser`: the address is saved first so the return survives a restart. */
    fun browser() {
        val address =
            state.onboarding
                ?.progress
                ?.readyAddress ?: return
        update { it.copy(progress = it.progress.copy(openIdPendingAddress = address), signInError = null) }
        servers.beginOpenId(address)
    }

    /** Called once the browser sign-in returns to the app while onboarding. */
    internal fun finishBrowserSignIn(
        saved: OnboardingProgress,
        error: String?,
    ) {
        if (state.onboarding == null) host.mutate { it.copy(onboarding = OnboardingUi(saved)) }
        if (error == null) {
            update {
                it.copy(
                    progress = it.progress.copy(step = OnboardingStep.FILES, openIdPendingAddress = null),
                    signInError = null,
                )
            }
            host.mutate { it.copy(route = ShellRoute.OnboardingFiles) }
        } else {
            update {
                it.copy(
                    progress = it.progress.copy(step = OnboardingStep.SIGN_IN, openIdPendingAddress = null),
                    signInError = error,
                )
            }
            host.mutate { it.copy(route = ShellRoute.OnboardingSignIn) }
        }
    }

    fun refreshFiles() {
        host.scope.launch {
            host.mutate { it.copy(remoteRefreshing = true) }
            val result = withContext(host.io) { host.sync.refreshRemote() }
            host.mutate {
                it.copy(remoteFiles = result.files, serverError = result.error, signedIn = host.sync.signedIn(), remoteRefreshing = false)
            }
        }
    }

    fun open(file: RemoteFile) {
        host.mutate { it.copy(serverError = null) }
        budgetFiles.requestRemote(file)
    }

    fun newBudget() {
        update {
            it.copy(
                progress =
                    it.progress.copy(
                        step = OnboardingStep.CREATE,
                        path = OnboardingPath.SERVER,
                        createReturn = CreateReturn.FILES,
                    ),
            )
        }
        host.mutate { it.copy(route = ShellRoute.Create, createError = null) }
    }

    /** Keeps the new-budget name and currency in progress while the person types. */
    fun createDraft(
        name: String,
        currency: String,
    ) {
        if (state.onboarding == null) return
        progress { it.copy(createName = name, createCurrency = currency) }
    }

    /**
     * Back on an onboarding page: one step back with entries kept. Returns null when the
     * current page is not part of onboarding, false to leave the app from the welcome page.
     */
    internal fun stepBack(): Boolean? {
        val current = state
        val ui = current.onboarding ?: return null
        val settingsOrigin = ui.progress.origin == OnboardingOrigin.SETTINGS
        if (current.shell != null && !settingsOrigin) return null

        fun step(
            to: OnboardingStep,
            route: ShellRoute,
        ): Boolean {
            cancelJob()
            update {
                it.copy(progress = it.progress.copy(step = to), checking = false, addressError = null, signInError = null)
            }
            host.mutate {
                it.copy(
                    route = route,
                    createError = null,
                    serverError = null,
                    budgetFiles = it.budgetFiles.copy(passwordError = null, pendingFile = null),
                )
            }
            return true
        }
        return when (current.route) {
            ShellRoute.Welcome -> false
            ShellRoute.OnboardingAddress ->
                if (settingsOrigin) {
                    leaveSettings()
                    true
                } else {
                    step(OnboardingStep.WELCOME, ShellRoute.Welcome)
                }
            ShellRoute.OnboardingSignIn -> step(OnboardingStep.ADDRESS, ShellRoute.OnboardingAddress)
            ShellRoute.OnboardingFiles ->
                if (settingsOrigin) {
                    leaveSettings()
                    true
                } else {
                    step(OnboardingStep.SIGN_IN, ShellRoute.OnboardingSignIn)
                }
            ShellRoute.Create ->
                if (ui.progress.createReturn == CreateReturn.FILES && host.sync.signedIn()) {
                    step(OnboardingStep.FILES, ShellRoute.OnboardingFiles)
                } else {
                    step(OnboardingStep.WELCOME, ShellRoute.Welcome)
                }
            ShellRoute.BudgetPassword ->
                if (current.budgetFiles.pendingFile != null &&
                    !current.locked
                ) {
                    step(OnboardingStep.FILES, ShellRoute.OnboardingFiles)
                } else {
                    null
                }
            else -> null
        }
    }

    /** A budget is open: onboarding is over and its file is removed. */
    internal fun finish() {
        // The saved progress goes even when the screen's copy is already gone: a load that lands
        // mid-download can drop that copy, and the file would bring the steps back on the next launch.
        if (state.onboarding != null) {
            cancelJob()
            host.mutate { it.copy(onboarding = null) }
        }
        host.scope.launch(writes) { host.library.onboarding().clear() }
    }
}
