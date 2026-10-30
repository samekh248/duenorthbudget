package app.duenorth.budget

import app.duenorth.budget.core.BudgetClock
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.CreateResult
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** The budget files holder on its own, over a stand-in shell. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BudgetFilesHolderTest {
    @Test
    fun keepingTheCurrentBudgetGoesBackWhereTheSwitchStarted() {
        val host = host("budget-files-holder-switch")
        // Making a budget opens it, so Trip is open and Home is the one to switch to.
        val home = (host.library.create("Home", "USD") as CreateResult.Created).id
        host.library.create("Trip", "USD")
        host.mutate {
            it.copy(route = ShellRoute.Settings, settings = host.library.settings(), budgets = host.library.list())
        }
        val files = holder(host)
        files.requestSwitch(home)
        assertEquals(ShellRoute.ConfirmSwitch, host.state.value.route)
        assertEquals(
            home,
            host.state.value.budgetFiles.switchTarget
                ?.id,
        )
        files.cancelSwitch()
        assertEquals(ShellRoute.Settings, host.state.value.route)
        assertEquals(null, host.state.value.budgetFiles.switchTarget)
    }

    @Test
    fun askingEachTimeIsKeptInTheHoldersState() {
        val host = host("budget-files-holder-ask")
        val files = holder(host)
        files.setAskEachTime(false)
        assertEquals(false, host.state.value.budgetFiles.askEachTime)
    }

    private fun holder(host: FakeShellHost) =
        BudgetFilesHolder(host, null, developerMode = false, SettingsHolder(host, PayeesHolder(host), ServersHolder(host)))

    private fun host(name: String): FakeShellHost {
        val root =
            File(RuntimeEnvironment.getApplication().filesDir, name).apply {
                deleteRecursively()
                mkdirs()
            }
        val library = BudgetLibrary(root, AndroidSessionOpener(), BudgetClock.System)
        return FakeShellHost(library, ShellUiState(settings = library.settings()))
    }
}
