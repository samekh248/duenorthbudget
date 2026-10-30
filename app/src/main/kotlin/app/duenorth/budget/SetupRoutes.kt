package app.duenorth.budget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.OnboardingOrigin
import app.duenorth.budget.core.ServerAddress
import java.util.Locale

// The pages of settings, the server, onboarding and budget files, each wired to the view model. DueNorthApp picks one per route.

@Composable
internal fun SettingsRoute(
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    val context = LocalContext.current
    var launcherIcon by remember { mutableStateOf(LauncherIcons.current(context)) }
    SettingsScreen(
        page = state.settingsPage,
        instances = state.instances,
        activeInstance = state.activeInstance,
        instanceReachable = state.servers.reachable,
        signedIn = state.signedIn,
        budgets = state.budgets,
        openBudgetId = state.settings.openBudgetId,
        themeMode = state.settings.themeMode,
        accentId = state.settings.accent,
        styleId = state.settings.style,
        formatPrefs = state.formatPrefs,
        cloudFileId = state.cloudFileId,
        encrypted = state.settingsUi.budgetEncrypted,
        onPage = model.settingsUi::selectPage,
        onMakeActive = model.servers::requestSwitch,
        onRemoveInstance = model.servers::requestRemove,
        onOpenBudget = model::requestSwitch,
        onTheme = model.settingsUi::setTheme,
        onAccent = model.settingsUi::setAccent,
        onStyle = model.settingsUi::setStyle,
        launcherIcon = launcherIcon,
        onLauncherIcon = {
            LauncherIcons.set(context, it)
            launcherIcon = it
        },
        onPayees = model.settingsUi::showPayees,
        onDateFormat = model.settingsUi::setDateFormat,
        onNumberFormat = model.settingsUi::setNumberFormat,
        onFirstDay = model.settingsUi::setFirstDay,
        onHideFraction = model.settingsUi::setHideFraction,
        onExperimental = model.settingsUi::setExperimental,
        onExperimentalFeature = model.settingsUi::setExperimentalFeature,
        onLearning = model.settingsUi::setLearning,
        onExport = model.settingsUi::exportBudget,
        onEncryption = model.settingsUi::showEncryption,
        onRequestAdvanced = model.settingsUi::requestAdvanced,
        phoneSettings = state.settings,
        onBackgroundSyncHours = model.settingsUi::setBackgroundSyncHours,
        scroll = state.settingsUi.scroll,
        onScroll = model.settingsUi::saveScroll,
        modifier = modifier,
    )
}

@Composable
internal fun ExperimentalOfferRoute(
    route: ShellRoute.ExperimentalOffer,
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    ExperimentalOfferScreen(
        budgetName = state.budgets.find { it.id == route.budgetId }?.name ?: state.openBudgetName(),
        onTurnOn = model.settingsUi::acceptExperimentalOffer,
        onKeepOff = model.settingsUi::declineExperimentalOffer,
        current = route.current,
        modifier = modifier,
    )
}

@Composable
internal fun EncryptionPasswordRoute(
    route: ShellRoute.EncryptionPassword,
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    EncryptionPasswordScreen(
        mode = route.mode,
        error = state.settingsUi.encryptionError,
        onSet = model.settingsUi::setEncryptionPassword,
        onChange = model.settingsUi::changeEncryptionPassword,
        onRemove = model.settingsUi::removeEncryptionPassword,
        modifier = modifier,
    )
}

@Composable
internal fun ConfirmRemoveInstanceRoute(
    route: ShellRoute.ConfirmRemoveInstance,
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    val instance = state.instances.find { it.id == route.instanceId }
    ConfirmRemoveInstanceScreen(
        name = instance?.label ?: instance?.address.orEmpty(),
        onRemove = model.servers::confirmRemove,
        onKeep = { model.back() },
        modifier = modifier,
    )
}

@Composable
internal fun ConfirmInstanceSwitchRoute(
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    ConfirmInstanceSwitchScreen(
        leaving =
            state.activeInstance?.label
                ?: state.activeInstance?.address.orEmpty(),
        opening =
            state.servers.switchTarget?.label
                ?: state.servers.switchTarget
                    ?.address
                    .orEmpty(),
        onOpen = { model.servers.confirmSwitch() },
        onStay = model.servers::cancelSwitch,
        modifier = modifier,
    )
}

@Composable
internal fun SampleBudgetsRoute(
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    SampleBudgetsScreen(
        samples = state.sampleBudgets,
        error = state.createError,
        onChoose = model::createSample,
        modifier = modifier,
    )
}

@Composable
internal fun CreateRoute(
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    val draft = state.onboarding?.progress
    CreateBudgetScreen(
        error = state.createError,
        onCreate = model::create,
        modifier = modifier,
        defaultName = draft?.createName?.ifBlank { null } ?: state.defaultBudgetName,
        defaultCurrency = draft?.createCurrency ?: Currencies.forLocale(Locale.getDefault()),
        onDraft = if (draft == null) null else model::onboardingCreateDraft,
    )
}

@Composable
internal fun WelcomeRoute(
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    WelcomeScreen(
        onServer = model::onboardingChooseServer,
        onPhone = model::onboardingChoosePhone,
        modifier = modifier,
    )
}

@Composable
internal fun OnboardingAddressRoute(
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    val onboarding = state.onboarding
    OnboardingAddressScreen(
        address = onboarding?.progress?.addressText.orEmpty(),
        checking = onboarding?.checking == true,
        error = onboarding?.addressError,
        onAddress = model::onboardingAddress,
        onNext = model::onboardingCheck,
        onPhone = model::onboardingChoosePhone,
        modifier = modifier,
        offerPhone = onboarding?.progress?.origin != OnboardingOrigin.SETTINGS,
    )
}

@Composable
internal fun OnboardingSignInRoute(
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    val onboarding = state.onboarding
    val ready = onboarding?.progress?.readyAddress.orEmpty()
    val host = ServerAddress.parse(ready)?.host ?: ready
    OnboardingSignInScreen(
        host = host,
        methods = onboarding?.progress?.methods.orEmpty(),
        label = onboarding?.progress?.instanceLabel.orEmpty(),
        checking = onboarding?.checking == true || state.syncing,
        error = onboarding?.signInError,
        onLabel = model::onboardingLabel,
        onSignIn = model::onboardingSignIn,
        onBrowser = model::onboardingBrowser,
        modifier = modifier,
        secure = ready.isEmpty() || ServerAddress.isSecure(ready),
        replacing =
            state.activeInstance
                ?.takeIf { onboarding?.progress?.origin == OnboardingOrigin.SETTINGS }
                ?.let { it.label ?: it.address },
    )
}

@Composable
internal fun OnboardingFilesRoute(
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    OnboardingFilesScreen(
        label =
            state.activeInstance?.label
                ?: state.onboarding
                    ?.progress
                    ?.instanceLabel
                    .orEmpty(),
        files = state.remoteFiles,
        error = state.serverError,
        onOpen = model::onboardingOpen,
        modifier = modifier,
        replacing = state.budgets.map { it.name },
    )
}

@Composable
internal fun ConfirmReplaceBudgetRoute(
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    ConfirmReplaceBudgetScreen(
        opening =
            state.pendingFile
                ?.name
                .orEmpty(),
        clearing = state.replaceBudgets.map { it.name },
        onUse = model::confirmReplaceBudget,
        onKeep = model::cancelReplaceBudget,
        modifier = modifier,
    )
}

@Composable
internal fun ConfirmSwitchRoute(
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    ConfirmSwitchScreen(
        opening = state.switchTarget?.name.orEmpty(),
        leaving = state.openBudgetName(),
        onOpen = model::confirmSwitch,
        onStay = model::cancelSwitch,
        modifier = modifier,
    )
}

@Composable
internal fun PayeesRoute(
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    PayeesScreen(
        payees = state.payees.rows,
        error = state.writeError,
        mergeTarget = state.payees.mergeTarget,
        onRename = model.payees::rename,
        onConfirmMerge = model.payees::confirmMerge,
        onCancelMerge = model.payees::cancelMerge,
        modifier = modifier,
    )
}

@Composable
internal fun ServerRoute(
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    ServerScreen(
        address = state.settings.serverAddress.orEmpty(),
        signedIn = state.signedIn,
        files = state.remoteFiles,
        status = state.syncStatus,
        error = state.serverError,
        conflictCount = state.conflicts.size,
        onConflicts = model.servers::showConflicts,
        onConnect = model::connect,
        onOpen = model::requestRemote,
        onReplace = model::requestReplace,
        onUpload = model::uploadNew,
        onSync = model::syncNow,
        onSignOut = model::signOut,
        modifier = modifier,
    )
}

@Composable
internal fun BudgetPasswordRoute(
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    BudgetPasswordScreen(
        error = state.passwordError,
        askEachTime = state.askEachTime,
        onAskEachTime = model::setAskEachTime,
        onSubmit = { password -> model.submitPassword(password, state.askEachTime) },
        modifier = modifier,
    )
}

@Composable
internal fun ConflictsRoute(
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    ConflictScreen(
        conflicts = state.conflicts,
        modifier = modifier,
    )
}

@Composable
internal fun ConfirmReplaceRoute(
    state: ShellUiState,
    model: ShellViewModel,
    modifier: Modifier,
) {
    ConfirmReplaceScreen(
        serverName = state.replaceFile?.name.orEmpty(),
        phoneName = state.openBudgetName(),
        onReplace = model::confirmReplace,
        onKeep = model::cancelReplace,
        modifier = modifier,
    )
}
