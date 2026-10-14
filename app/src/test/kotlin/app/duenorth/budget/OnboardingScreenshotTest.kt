package app.duenorth.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.duenorth.budget.core.OnboardingCopy
import app.duenorth.budget.core.RemoteFile
import app.duenorth.budget.core.SignInMethod
import app.duenorth.budget.design.components.MetroAppBar
import app.duenorth.budget.design.components.ScreenAction
import app.duenorth.budget.design.theme.MetroTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class OnboardingScreenshotTest {
    @Test
    fun welcomeLight() = snap(ShellRoute.Welcome, "onboarding_welcome_light", dark = false) { welcome() }

    @Test
    fun welcomeDark() = snap(ShellRoute.Welcome, "onboarding_welcome_dark", dark = true) { welcome() }

    @Test
    fun addressLight() =
        snap(ShellRoute.OnboardingAddress, "onboarding_address_light", dark = false) { address(checking = true, error = null) }

    @Test
    fun addressDark() =
        snap(ShellRoute.OnboardingAddress, "onboarding_address_dark", dark = true) { address(checking = true, error = null) }

    @Test
    fun addressErrorLight() =
        snap(ShellRoute.OnboardingAddress, "onboarding_address_error_light", dark = false) {
            address(checking = false, error = OnboardingCopy.NOT_SET_UP)
        }

    @Test
    fun addressErrorDark() =
        snap(ShellRoute.OnboardingAddress, "onboarding_address_error_dark", dark = true) {
            address(checking = false, error = OnboardingCopy.NOT_SET_UP)
        }

    @Test
    fun signInLight() = snap(ShellRoute.OnboardingSignIn, "onboarding_signin_light", dark = false) { signIn(setOf(SignInMethod.PASSWORD)) }

    @Test
    fun signInDark() = snap(ShellRoute.OnboardingSignIn, "onboarding_signin_dark", dark = true) { signIn(setOf(SignInMethod.PASSWORD)) }

    @Test
    fun signInBrowserLight() =
        snap(ShellRoute.OnboardingSignIn, "onboarding_signin_browser_light", dark = false) {
            signIn(setOf(SignInMethod.BROWSER))
        }

    @Test
    fun signInBrowserDark() =
        snap(ShellRoute.OnboardingSignIn, "onboarding_signin_browser_dark", dark = true) {
            signIn(setOf(SignInMethod.BROWSER))
        }

    @Test
    fun filesLight() = snap(ShellRoute.OnboardingFiles, "onboarding_files_light", dark = false) { files() }

    @Test
    fun filesDark() = snap(ShellRoute.OnboardingFiles, "onboarding_files_dark", dark = true) { files() }

    @Composable
    private fun welcome() = WelcomeScreen(onServer = {}, onPhone = {})

    @Composable
    private fun address(
        checking: Boolean,
        error: String?,
    ) = OnboardingAddressScreen(
        address = "budget.example.com",
        checking = checking,
        error = error,
        onAddress = {},
        onNext = {},
        onPhone = {},
    )

    @Composable
    private fun signIn(methods: Set<SignInMethod>) =
        OnboardingSignInScreen(
            host = "budget.example.com",
            methods = methods,
            label = "home server",
            checking = false,
            error = null,
            onLabel = {},
            onSignIn = {},
            onBrowser = {},
        )

    @Composable
    private fun files() =
        OnboardingFilesScreen(
            label = "home server",
            files =
                listOf(
                    RemoteFile("a1b2c3d4", "g1", "Household", "key-1"),
                    RemoteFile("e5f6a7b8", "g2", "Travel 2026", null),
                    RemoteFile("c9d0e1f2", "g3", "Travel 2026", null),
                ),
            error = null,
            onOpen = {},
        )

    private fun snap(
        route: ShellRoute,
        name: String,
        dark: Boolean,
        content: @Composable () -> Unit,
    ) {
        captureRoboImage("src/test/snapshots/$name.png") {
            MetroTheme(darkTheme = dark, animationsEnabled = false) {
                // The page with the app bar it gets on the phone; the bar owns save.
                CompositionLocalProvider(LocalFormSaveHost provides FormSaveHost()) {
                    Column(Modifier.width(390.dp).height(780.dp)) {
                        Box(Modifier.weight(1f)) { content() }
                        MetroAppBar(
                            actions =
                                ScreenBar
                                    .specsFor(route, hasBudget = false, mode = null, passwordSignIn = !name.contains("browser"))
                                    .map { ScreenAction(it.id, it.glyph, it.label, it.primary) {} },
                            expanded = false,
                            onExpandedChange = {},
                            windowInsets = WindowInsets(0),
                        )
                    }
                }
            }
        }
    }
}
