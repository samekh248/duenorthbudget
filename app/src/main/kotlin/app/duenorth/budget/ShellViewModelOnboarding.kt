package app.duenorth.budget

import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.Dispatchers
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

/** The page saved progress resumes on. A sign-in step without a checked address goes back to the address. */
internal fun ShellViewModel.onboardingRoute(progress: OnboardingProgress?): ShellRoute =
    when (progress?.step ?: OnboardingStep.WELCOME) {
        OnboardingStep.WELCOME -> ShellRoute.Welcome
        OnboardingStep.ADDRESS -> ShellRoute.OnboardingAddress
        OnboardingStep.SIGN_IN ->
            if (progress?.readyAddress == null) ShellRoute.OnboardingAddress else ShellRoute.OnboardingSignIn
        OnboardingStep.FILES ->
            when {
                sync.signedIn() -> ShellRoute.OnboardingFiles
                progress?.readyAddress != null -> ShellRoute.OnboardingSignIn
                else -> ShellRoute.OnboardingAddress
            }
        OnboardingStep.CREATE -> ShellRoute.Create
    }

/** Reads `onboarding.json` before the first load. A phone that already has a budget drops it. */
internal suspend fun ShellViewModel.restoreOnboarding() {
    val stored =
        withContext(io) {
            val store = library.onboarding()
            val progress = store.read() ?: return@withContext null
            // An add-server path started in settings resumes even with budgets on the phone (spec 012).
            if (progress.origin == OnboardingOrigin.SETTINGS) return@withContext progress
            if (library.list().isNotEmpty()) {
                store.clear()
                null
            } else {
                progress
            }
        } ?: return
    if (stored.origin == OnboardingOrigin.SETTINGS) {
        mutate {
            it.copy(
                onboarding = OnboardingUi(stored),
                route = onboardingRoute(stored),
                settingsReturn = true,
                settingsPage = SettingsPage.SERVER,
            )
        }
        return
    }
    mutate { it.copy(onboarding = OnboardingUi(stored)) }
}

/** Settings → server → add: the onboarding server path, starting on an empty address (spec 012 FR-005). */
fun ShellViewModel.onboardingAddServer() {
    cancelOnboardingJob()
    val progress =
        OnboardingProgress(
            step = OnboardingStep.ADDRESS,
            path = OnboardingPath.SERVER,
            origin = OnboardingOrigin.SETTINGS,
        )
    mutate { it.copy(onboarding = OnboardingUi(progress)) }
    viewModelScope.launch(onboardingWrites) { library.onboarding().write(progress) }
    openFromSettings(ShellRoute.OnboardingAddress, SettingsPage.SERVER)
}

/** The settings-origin path is over without a pick: drop its progress and show the server item. */
private fun ShellViewModel.leaveSettingsOnboarding() {
    cancelOnboardingJob()
    mutate { it.copy(onboarding = null) }
    viewModelScope.launch(onboardingWrites) { library.onboarding().clear() }
    returnToSettings(SettingsPage.SERVER)
}

/** After the first load, the file list needs the server's budgets again. */
internal fun ShellViewModel.resumeOnboarding() {
    if (state.value.route == ShellRoute.OnboardingFiles) onboardingRefresh()
}

private fun ShellViewModel.updateOnboarding(transform: (OnboardingUi) -> OnboardingUi) {
    var saved: OnboardingProgress? = null
    mutate { current ->
        val ui = current.onboarding ?: return@mutate current
        val next = transform(ui)
        if (next.progress != ui.progress) saved = next.progress
        current.copy(onboarding = next)
    }
    val progress = saved ?: return
    viewModelScope.launch(onboardingWrites) { library.onboarding().write(progress) }
}

private fun ShellViewModel.progress(transform: (OnboardingProgress) -> OnboardingProgress) =
    updateOnboarding { it.copy(progress = transform(it.progress)) }

private fun ShellViewModel.cancelOnboardingJob() {
    onboardingJob?.cancel()
    onboardingJob = null
}

fun ShellViewModel.onboardingChooseServer() {
    val saved =
        state.value.activeInstance?.address ?: state.value.instances
            .firstOrNull()
            ?.address
    updateOnboarding {
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
    mutate { it.copy(route = ShellRoute.OnboardingAddress) }
}

fun ShellViewModel.onboardingChoosePhone() {
    cancelOnboardingJob()
    updateOnboarding {
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
    mutate { it.copy(route = ShellRoute.Create, createError = null) }
}

fun ShellViewModel.onboardingAddress(text: String) {
    val before =
        state.value.onboarding
            ?.progress
            ?.addressText
    if (text == before) return
    cancelOnboardingJob()
    updateOnboarding {
        it.copy(
            progress = it.progress.copy(addressText = text, readyAddress = null, methods = emptySet()),
            checking = false,
            addressError = null,
        )
    }
}

/** `next` on the address page: one server check, then the sign-in that server takes. */
fun ShellViewModel.onboardingCheck() {
    val ui = state.value.onboarding ?: return
    if (ui.checking) return
    val parsed = ServerAddress.parse(ui.progress.addressText)
    if (parsed == null) {
        updateOnboarding { it.copy(addressError = OnboardingCopy.ENTER_ADDRESS) }
        return
    }
    updateOnboarding { it.copy(checking = true, addressError = null) }
    onboardingJob =
        viewModelScope.launch {
            val check = withContext(io) { probe.check(parsed) }
            if (check is ServerCheck.Ready && !check.secure && parsed.candidates.size > 1) {
                // Only http:// answered a bare address. Say so, and let the person choose it, instead of
                // sending the password unencrypted without asking.
                updateOnboarding {
                    it.copy(
                        progress = it.progress.copy(addressText = check.address),
                        checking = false,
                        addressError = OnboardingCopy.HTTP_ONLY,
                    )
                }
            } else if (check is ServerCheck.Ready) {
                updateOnboarding {
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
                mutate { it.copy(route = ShellRoute.OnboardingSignIn) }
            } else {
                updateOnboarding { it.copy(checking = false, addressError = OnboardingCopy.message(check)) }
            }
        }
}

fun ShellViewModel.onboardingLabel(text: String) {
    progress { it.copy(instanceLabel = text) }
}

fun ShellViewModel.onboardingSignIn(password: String) {
    val ui = state.value.onboarding ?: return
    if (ui.checking) return
    val address = ui.progress.readyAddress ?: return
    if (SignInMethod.PASSWORD !in ui.progress.methods) return
    updateOnboarding { it.copy(checking = true, signInError = null) }
    onboardingJob =
        viewModelScope.launch {
            val result = withContext(io) { sync.connect(address, password, ui.progress.instanceLabel) }
            val settings = withContext(io) { library.settings() }
            // From first run or from settings, a signed-in server goes on to picking its budget.
            if (result.error == null) {
                updateOnboarding {
                    it.copy(progress = it.progress.copy(step = OnboardingStep.FILES), checking = false, signInError = null)
                }
            } else {
                updateOnboarding { it.copy(checking = false, signInError = result.error) }
            }
            mutate {
                it.copy(
                    route = if (result.error == null) ShellRoute.OnboardingFiles else it.route,
                    remoteFiles = if (result.error == null) result.files else it.remoteFiles,
                    serverError = null,
                    settings = settings,
                    servers = it.servers.copy(reachable = if (result.error == null) true else it.servers.reachable),
                    signedIn = sync.signedIn(),
                )
            }
        }
}

/** `use browser`: the address is saved first so the return survives a restart. */
fun ShellViewModel.onboardingBrowser() {
    val address =
        state.value.onboarding
            ?.progress
            ?.readyAddress ?: return
    updateOnboarding { it.copy(progress = it.progress.copy(openIdPendingAddress = address), signInError = null) }
    servers.beginOpenId(address)
}

/** Called once the browser sign-in returns to the app while onboarding. */
internal fun ShellViewModel.finishBrowserSignIn(
    saved: OnboardingProgress,
    error: String?,
) {
    if (state.value.onboarding == null) mutate { it.copy(onboarding = OnboardingUi(saved)) }
    if (error == null) {
        updateOnboarding {
            it.copy(
                progress = it.progress.copy(step = OnboardingStep.FILES, openIdPendingAddress = null),
                signInError = null,
            )
        }
        mutate { it.copy(route = ShellRoute.OnboardingFiles) }
    } else {
        updateOnboarding {
            it.copy(
                progress = it.progress.copy(step = OnboardingStep.SIGN_IN, openIdPendingAddress = null),
                signInError = error,
            )
        }
        mutate { it.copy(route = ShellRoute.OnboardingSignIn) }
    }
}

fun ShellViewModel.onboardingRefresh() {
    viewModelScope.launch {
        mutate { it.copy(remoteRefreshing = true) }
        val result = withContext(io) { sync.refreshRemote() }
        mutate {
            it.copy(remoteFiles = result.files, serverError = result.error, signedIn = sync.signedIn(), remoteRefreshing = false)
        }
    }
}

fun ShellViewModel.onboardingOpen(file: RemoteFile) {
    mutate { it.copy(serverError = null) }
    requestRemote(file)
}

fun ShellViewModel.onboardingNewBudget() {
    updateOnboarding {
        it.copy(
            progress =
                it.progress.copy(
                    step = OnboardingStep.CREATE,
                    path = OnboardingPath.SERVER,
                    createReturn = CreateReturn.FILES,
                ),
        )
    }
    mutate { it.copy(route = ShellRoute.Create, createError = null) }
}

/** Keeps the new-budget name and currency in progress while the person types. */
fun ShellViewModel.onboardingCreateDraft(
    name: String,
    currency: String,
) {
    if (state.value.onboarding == null) return
    progress { it.copy(createName = name, createCurrency = currency) }
}

/**
 * Back on an onboarding page: one step back with entries kept. Returns null when the
 * current page is not part of onboarding, false to leave the app from the welcome page.
 */
internal fun ShellViewModel.onboardingBack(): Boolean? {
    val current = state.value
    val ui = current.onboarding ?: return null
    val settingsOrigin = ui.progress.origin == OnboardingOrigin.SETTINGS
    if (current.shell != null && !settingsOrigin) return null

    fun step(
        to: OnboardingStep,
        route: ShellRoute,
    ): Boolean {
        cancelOnboardingJob()
        updateOnboarding {
            it.copy(progress = it.progress.copy(step = to), checking = false, addressError = null, signInError = null)
        }
        mutate { it.copy(route = route, createError = null, passwordError = null, pendingFile = null, serverError = null) }
        return true
    }
    return when (current.route) {
        ShellRoute.Welcome -> false
        ShellRoute.OnboardingAddress ->
            if (settingsOrigin) {
                leaveSettingsOnboarding()
                true
            } else {
                step(OnboardingStep.WELCOME, ShellRoute.Welcome)
            }
        ShellRoute.OnboardingSignIn -> step(OnboardingStep.ADDRESS, ShellRoute.OnboardingAddress)
        ShellRoute.OnboardingFiles ->
            if (settingsOrigin) {
                leaveSettingsOnboarding()
                true
            } else {
                step(OnboardingStep.SIGN_IN, ShellRoute.OnboardingSignIn)
            }
        ShellRoute.Create ->
            if (ui.progress.createReturn == CreateReturn.FILES && sync.signedIn()) {
                step(OnboardingStep.FILES, ShellRoute.OnboardingFiles)
            } else {
                step(OnboardingStep.WELCOME, ShellRoute.Welcome)
            }
        ShellRoute.BudgetPassword ->
            if (current.pendingFile != null && !current.locked) step(OnboardingStep.FILES, ShellRoute.OnboardingFiles) else null
        else -> null
    }
}

/** A budget is open: onboarding is over and its file is removed. */
internal fun ShellViewModel.finishOnboarding() {
    // The saved progress goes even when the screen's copy is already gone: a load that lands
    // mid-download can drop that copy, and the file would bring the steps back on the next launch.
    if (state.value.onboarding != null) {
        cancelOnboardingJob()
        mutate { it.copy(onboarding = null) }
    }
    viewModelScope.launch(onboardingWrites) { library.onboarding().clear() }
}
