package app.duenorth.budget

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.OnboardingCopy
import app.duenorth.budget.core.RemoteFile
import app.duenorth.budget.core.ServerAddress
import app.duenorth.budget.core.SignInMethod
import app.duenorth.budget.core.SyncCopy
import app.duenorth.budget.design.components.LocalFieldSubmit
import app.duenorth.budget.design.components.MetroButton
import app.duenorth.budget.design.components.MetroButtonGrid
import app.duenorth.budget.design.components.MetroField
import app.duenorth.budget.design.components.MetroText
import app.duenorth.budget.design.components.metroPress
import app.duenorth.budget.design.theme.Metro
import app.duenorth.budget.design.theme.MetroDimens

object OnboardingScreenCopy {
    const val APP_TITLE = "DUE NORTH BUDGET"
    const val WELCOME = "welcome"
    const val WHERE = "where is your budget?"
    const val SERVER = "on my Actual server"
    const val SERVER_HINT = "sync with a server you already run"
    const val PHONE = "only on this phone"
    const val PHONE_ONLY = "phone only"
    const val PHONE_HINT = "start here, add a server later in settings"
    const val SERVER_TITLE = "server"
    const val WHERE_SERVER = "where does Actual run?"
    const val ADDRESS = "address"
    const val HTTP = "http"
    const val HTTPS = "https"
    const val TRY_AGAIN = "try again"
    const val SIGN_IN = "sign in"
    const val LABEL = "name on this phone"
    const val PASSWORD = "password"
    const val BROWSER = "use browser"
    const val PICK = "pick a budget"
    const val NEW_BUDGET = "new budget"
    const val ENCRYPTED = "encrypted · asks for its password"
    const val NEXT = "next"
    const val REFRESH = "refresh"

    fun checking(host: String) = "checking $host"

    fun method(methods: Set<SignInMethod>): String =
        when {
            SignInMethod.PASSWORD in methods && SignInMethod.BROWSER in methods -> "Actual server · password or browser sign-in"
            SignInMethod.BROWSER in methods -> "Actual server · browser sign-in"
            else -> "Actual server · password sign-in"
        }

    fun on(label: String) = "on $label"

    /** Settings → add server, when another server is active (spec 012 research R6). */
    fun replaces(current: String) = "signing in makes this your server; $current stays saved"
}

/** Small uppercase app title over an oversized lowercase page title (Metro 2). */
@Composable
private fun OnboardingPage(
    title: String,
    tag: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Metro.colors.background)
            .padding(horizontal = MetroDimens.Gutter)
            .testTag(tag),
    ) {
        MetroText(
            OnboardingScreenCopy.APP_TITLE,
            Metro.typography.pageTitle,
            Modifier.padding(top = MetroDimens.Gutter),
        )
        MetroText(title, Metro.typography.header, Modifier.padding(bottom = MetroDimens.Row))
        content()
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    hint: String,
    tag: String,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MetroDimens.TouchTarget)
            .metroPress(onClick)
            .padding(vertical = MetroDimens.Row)
            .testTag(tag),
    ) {
        MetroText(title, Metro.typography.subheader)
        MetroText(hint, Metro.typography.caption, color = Metro.colors.secondary)
    }
}

@Composable
private fun FieldLabel(text: String) {
    MetroText(
        text,
        Metro.typography.caption,
        Modifier.padding(top = MetroDimens.Row, bottom = 4.dp),
        color = Metro.colors.secondary,
    )
}

@Composable
private fun ErrorText(
    text: String,
    tag: String,
) {
    MetroText(
        text,
        Metro.typography.body,
        Modifier
            .padding(top = MetroDimens.Row)
            .testTag(tag),
        color = Metro.accent.text,
    )
}

/** Metro progress dots: five accent squares that pulse in turn; still under reduced motion. */
@Composable
fun ProgressDots(modifier: Modifier = Modifier) {
    val moving = Metro.animations
    val phase =
        if (moving) {
            val transition = rememberInfiniteTransition(label = "dots")
            transition.animateFloat(
                initialValue = 0f,
                targetValue = 5f,
                animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
                label = "phase",
            )
        } else {
            null
        }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(5) { index ->
            val lit =
                phase?.let {
                    val distance = kotlin.math.abs(it.value - index)
                    (1f - distance / 2f).coerceIn(0.25f, 1f)
                } ?: 1f
            Box(
                Modifier
                    .size(6.dp)
                    .alpha(lit)
                    .background(Metro.accent.fill),
            )
        }
    }
}

@Composable
fun WelcomeScreen(
    onServer: () -> Unit,
    onPhone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingPage(OnboardingScreenCopy.WELCOME, "onboarding-welcome", modifier) {
        MetroText(OnboardingScreenCopy.WHERE, Metro.typography.subheader)
        Spacer(Modifier.height(MetroDimens.Grid))
        ChoiceRow(OnboardingScreenCopy.SERVER, OnboardingScreenCopy.SERVER_HINT, "welcome-server", onServer)
        ChoiceRow(OnboardingScreenCopy.PHONE, OnboardingScreenCopy.PHONE_HINT, "welcome-phone", onPhone)
    }
}

@Composable
fun OnboardingAddressScreen(
    address: String,
    checking: Boolean,
    error: String?,
    onAddress: (String) -> Unit,
    onNext: () -> Unit,
    onPhone: () -> Unit,
    modifier: Modifier = Modifier,
    /** False when adding a server from settings: a budget is already on the phone. */
    offerPhone: Boolean = true,
    /** Opens with the http/https choices unfolded; for screenshots. */
    schemeOpenAtStart: Boolean = false,
) {
    val parsed = ServerAddress.parse(address)
    OnboardingPage(OnboardingScreenCopy.SERVER_TITLE, "onboarding-address", modifier) {
        MetroText(OnboardingScreenCopy.WHERE_SERVER, Metro.typography.subheader)
        FieldLabel(OnboardingScreenCopy.ADDRESS)
        val entry = ServerAddress.entry(address)
        // The prefix is the toggle: it shows the scheme in use, and tapping it unfolds both under the box.
        var schemeOpen by rememberSaveable { mutableStateOf(schemeOpenAtStart) }
        // Enter checks the address and moves on, as next in the bar does.
        CompositionLocalProvider(LocalFieldSubmit provides { if (!checking) onNext() }) {
            MetroField(
                entry.text,
                { onAddress(ServerAddress.join(entry.secure, it)) },
                "budget.example.com",
                Modifier.testTag("onboarding-address-field"),
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go,
                    ),
                prefix = entry.prefix,
                onPrefixClick = { schemeOpen = !schemeOpen },
            )
        }
        if (schemeOpen) {
            SchemeChoices(entry.secure) { secure ->
                schemeOpen = false
                if (secure != entry.secure) onAddress(ServerAddress.join(secure, entry.text))
            }
        }
        if (!entry.secure) {
            MetroText(
                OnboardingCopy.HTTP_WARNING,
                Metro.typography.body,
                Modifier
                    .padding(top = MetroDimens.Row)
                    .testTag("onboarding-http-warning"),
                color = Metro.accent.text,
            )
        } else if (parsed != null && parsed.candidates.size > 1) {
            // An address saved before the toggle, with no scheme of its own.
            MetroText(
                OnboardingCopy.TRIED_SECURE,
                Metro.typography.caption,
                Modifier.padding(top = 4.dp),
                color = Metro.colors.secondary,
            )
        }
        if (checking) {
            ProgressDots(Modifier.padding(top = MetroDimens.Grid))
            MetroText(
                OnboardingScreenCopy.checking(parsed?.host ?: address.trim()),
                Metro.typography.caption,
                Modifier
                    .padding(top = MetroDimens.Row)
                    .testTag("onboarding-checking"),
                color = Metro.colors.secondary,
            )
        }
        if (error != null && !checking) {
            ErrorText(error, "onboarding-address-error")
            if (error != OnboardingCopy.ENTER_ADDRESS) {
                ButtonRow(
                    ButtonSpec(OnboardingScreenCopy.TRY_AGAIN, "onboarding-retry", onNext),
                    ButtonSpec(OnboardingScreenCopy.PHONE_ONLY, "onboarding-phone", onPhone).takeIf { offerPhone },
                )
            }
        }
    }
}

/** The address prefix's choices, unfolded under the box as a Metro combo box unfolds; the one in use in the accent. */
@Composable
private fun SchemeChoices(
    secure: Boolean,
    onPick: (secure: Boolean) -> Unit,
) {
    Column(
        Modifier
            .padding(top = 4.dp)
            .fillMaxWidth()
            .border(MetroDimens.Stroke, Metro.accent.fill)
            .testTag("onboarding-scheme"),
    ) {
        listOf(false to OnboardingScreenCopy.HTTP, true to OnboardingScreenCopy.HTTPS).forEach { (choice, label) ->
            MetroText(
                label,
                Metro.typography.control,
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = MetroDimens.TouchTarget)
                    .metroPress { onPick(choice) }
                    .padding(horizontal = 10.dp, vertical = MetroDimens.Row)
                    .testTag("onboarding-scheme-$label"),
                color = if (choice == secure) Metro.accent.text else Metro.colors.foreground,
            )
        }
    }
}

@Composable
fun OnboardingSignInScreen(
    host: String,
    methods: Set<SignInMethod>,
    label: String,
    checking: Boolean,
    error: String?,
    onLabel: (String) -> Unit,
    onSignIn: (String) -> Unit,
    onBrowser: () -> Unit,
    modifier: Modifier = Modifier,
    /** The active server this sign-in replaces, when adding a server from settings. */
    replacing: String? = null,
    /** False for an http:// server: the password would cross the network unencrypted. */
    secure: Boolean = true,
) {
    // Never saved to disk, and cleared when the server refuses it (spec 011 FR-011).
    var password by rememberSaveable { mutableStateOf("") }
    var refused by remember { mutableStateOf<String?>(null) }
    if (error != null && error != refused) {
        refused = error
        password = ""
    }
    OnboardingPage(OnboardingScreenCopy.SIGN_IN, "onboarding-signin", modifier) {
        MetroText(host, Metro.typography.subheader)
        MetroText(OnboardingScreenCopy.method(methods), Metro.typography.caption, color = Metro.colors.secondary)
        if (!secure) {
            MetroText(
                OnboardingCopy.PASSWORD_IN_THE_CLEAR,
                Metro.typography.caption,
                Modifier
                    .padding(top = MetroDimens.Row)
                    .testTag("onboarding-not-encrypted"),
                color = Metro.colors.secondary,
            )
        }
        if (replacing != null) {
            MetroText(
                OnboardingScreenCopy.replaces(replacing),
                Metro.typography.caption,
                Modifier
                    .padding(top = MetroDimens.Row)
                    .testTag("onboarding-replaces-active"),
                color = Metro.colors.secondary,
            )
        }
        FieldLabel(OnboardingScreenCopy.LABEL)
        MetroField(label, onLabel, host, Modifier.testTag("onboarding-label"))
        if (SignInMethod.PASSWORD in methods) {
            FieldLabel(OnboardingScreenCopy.PASSWORD)
            MetroField(
                password,
                { password = it },
                OnboardingScreenCopy.PASSWORD,
                Modifier.testTag("onboarding-password"),
                conceal = true,
                keyboardOptions =
                    KeyboardOptions(
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
            )
            RememberFormSave { onSignIn(password) }
        }
        if (checking) ProgressDots(Modifier.padding(top = MetroDimens.Grid))
        if (error != null && !checking) ErrorText(error, "onboarding-signin-error")
        // Password sign-in lives on the app bar; the body only carries the browser choice.
        ButtonRow(
            ButtonSpec(OnboardingScreenCopy.SIGN_IN, "onboarding-signin-button") { onSignIn(password) }
                .takeIf { SignInMethod.PASSWORD in methods && !formSaveOnBar() },
            ButtonSpec(OnboardingScreenCopy.BROWSER, "onboarding-browser", onBrowser)
                .takeIf { SignInMethod.BROWSER in methods },
        )
    }
}

@Composable
fun OnboardingFilesScreen(
    label: String,
    files: List<RemoteFile>,
    error: String?,
    onOpen: (RemoteFile) -> Unit,
    modifier: Modifier = Modifier,
    replacing: List<String> = emptyList(),
) {
    OnboardingPage(OnboardingScreenCopy.PICK, "onboarding-files", modifier) {
        MetroText(OnboardingScreenCopy.on(label), Metro.typography.subheader, color = Metro.colors.secondary)
        if (replacing.isNotEmpty()) {
            MetroText(
                ReplaceBudgetCopy.replaces(replacing),
                Metro.typography.caption,
                Modifier.padding(top = MetroDimens.Row).testTag("onboarding-files-replaces"),
                color = Metro.colors.secondary,
            )
        }
        if (error != null) ErrorText(error, "onboarding-files-error")
        if (files.isEmpty()) {
            MetroText(
                SyncCopy.NO_SERVER_FILES,
                Metro.typography.body,
                Modifier.padding(top = MetroDimens.Grid),
            )
        }
        val repeated =
            files
                .groupingBy { it.name }
                .eachCount()
                .filterValues { it > 1 }
                .keys
        LazyColumn(Modifier.weight(1f).padding(top = MetroDimens.Row)) {
            items(files, key = { it.fileId }) { file ->
                val captions =
                    listOfNotNull(
                        OnboardingScreenCopy.ENCRYPTED.takeIf { file.encryptKeyId != null },
                        file.fileId.take(6).takeIf { file.name in repeated },
                    )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = MetroDimens.TouchTarget)
                        .metroPress { onOpen(file) }
                        .padding(vertical = MetroDimens.Row)
                        .testTag("onboarding-file-${file.fileId}"),
                ) {
                    MetroText(file.name.ifBlank { file.fileId.take(6) }, Metro.typography.subheader)
                    if (captions.isNotEmpty()) {
                        MetroText(captions.joinToString(" · "), Metro.typography.caption, color = Metro.colors.secondary)
                    }
                }
            }
        }
    }
}

private class ButtonSpec(
    val label: String,
    val tag: String,
    val onClick: () -> Unit,
)

/** Body buttons on the shared Windows Phone grid: at most two, side by side at equal width. */
@Composable
private fun ButtonRow(vararg buttons: ButtonSpec?) {
    val shown = buttons.filterNotNull().take(2)
    if (shown.isEmpty()) return
    MetroButtonGrid(Modifier.padding(top = MetroDimens.Gutter)) {
        shown.forEach { button -> MetroButton(button.label, Modifier.testTag(button.tag), onClick = button.onClick) }
    }
}
