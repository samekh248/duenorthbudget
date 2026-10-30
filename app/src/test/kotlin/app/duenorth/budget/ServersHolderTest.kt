package app.duenorth.budget

import app.duenorth.budget.core.BudgetClock
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.PhoneSettings
import app.duenorth.budget.core.SavedInstance
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** The servers holder on its own, over a stand-in shell. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ServersHolderTest {
    private val home = SavedInstance("home", "https://home.example")
    private val office = SavedInstance("office", "https://office.example")
    private val settings = PhoneSettings(instances = listOf(home, office), activeInstanceId = home.id)

    @Test
    fun switchingServersAsksFirstAndStayKeepsTheActiveOne() {
        val host = host("servers-holder-switch", ShellUiState(settings = settings))
        val servers = ServersHolder(host)
        servers.requestSwitch(office.id)
        assertEquals(ShellRoute.ConfirmInstanceSwitch, host.state.value.route)
        assertEquals(office, host.state.value.servers.switchTarget)
        servers.cancelSwitch()
        assertEquals(ShellRoute.Settings, host.state.value.route)
        assertEquals(null, host.state.value.servers.switchTarget)
        assertEquals(home, host.state.value.activeInstance)
    }

    @Test
    fun aLinkThatIsNotTheSignInReturnIsIgnored() {
        val start = ShellUiState(settings = settings)
        val host = host("servers-holder-link", start)
        ServersHolder(host).completeOpenId("https://elsewhere.example/?token=abc")
        assertEquals(start, host.state.value)
    }

    private fun host(
        name: String,
        start: ShellUiState,
    ): FakeShellHost {
        val root =
            File(RuntimeEnvironment.getApplication().filesDir, name).apply {
                deleteRecursively()
                mkdirs()
            }
        return FakeShellHost(BudgetLibrary(root, AndroidSessionOpener(), BudgetClock.System), start)
    }
}
