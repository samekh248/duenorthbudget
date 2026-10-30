package app.duenorth.budget

import app.duenorth.budget.core.BudgetClock
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.OnboardingCopy
import app.duenorth.budget.core.OnboardingPath
import app.duenorth.budget.core.OnboardingProgress
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** The onboarding holder on its own, over a stand-in shell. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OnboardingHolderTest {
    @Test
    fun theServerPathAsksForAnAddressThatReads() {
        val host = host("onboarding-holder-address")
        val onboarding = holder(host)
        onboarding.chooseServer()
        assertEquals(ShellRoute.OnboardingAddress, host.state.value.route)
        assertEquals(
            OnboardingPath.SERVER,
            host.state.value.onboarding!!
                .progress.path,
        )
        onboarding.check()
        assertEquals(
            OnboardingCopy.ENTER_ADDRESS,
            host.state.value.onboarding!!
                .addressError,
        )
        onboarding.setAddress("budget.example.com")
        assertEquals(
            null,
            host.state.value.onboarding!!
                .addressError,
        )
    }

    @Test
    fun backFromTheAddressReturnsToWelcome() {
        val host = host("onboarding-holder-back")
        val onboarding = holder(host)
        onboarding.chooseServer()
        assertEquals(true, onboarding.stepBack())
        assertEquals(ShellRoute.Welcome, host.state.value.route)
        assertEquals(false, onboarding.stepBack())
    }

    private fun holder(host: FakeShellHost): OnboardingHolder {
        val payees = PayeesHolder(host)
        val servers = ServersHolder(host)
        val settings = SettingsHolder(host, payees, servers)
        return OnboardingHolder(host, settings, servers, BudgetFilesHolder(host, null, developerMode = false, settings))
    }

    private fun host(name: String): FakeShellHost {
        val root =
            File(RuntimeEnvironment.getApplication().filesDir, name).apply {
                deleteRecursively()
                mkdirs()
            }
        val library = BudgetLibrary(root, AndroidSessionOpener(), BudgetClock.System)
        return FakeShellHost(
            library,
            ShellUiState(settings = library.settings(), route = ShellRoute.Welcome, onboarding = OnboardingUi(OnboardingProgress())),
        )
    }
}
