package app.duenorth.budget

import app.duenorth.budget.core.BudgetClock
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.CreateResult
import app.duenorth.budget.core.RemoteFile
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

    @Test
    fun pickingTheOpenBudgetFromTheServerListGoesBackToItWithoutADownload() {
        val host = host("budget-files-holder-open-remote")
        val home = (host.library.create("Home", "USD") as CreateResult.Created).id
        host.mutate {
            it.copy(
                route = ShellRoute.OnboardingFiles,
                settingsReturn = true,
                settings = host.library.settings(),
                budgets = host.library.list(),
            )
        }
        holder(host).requestRemote(RemoteFile(fileId = home, groupId = "g", name = "Home", encryptKeyId = null))
        assertEquals(ShellRoute.Home, host.state.value.route)
        assertEquals(false, host.state.value.syncing)
        assertEquals(null, host.state.value.budgetFiles.pendingFile)
    }

    @Test
    fun keepingThePhonesBudgetsAfterPickingAServerOneFromSettingsGoesBackToSettings() {
        val host = host("budget-files-holder-settings-remote")
        host.library.create("Home", "USD")
        host.mutate {
            it.copy(route = ShellRoute.Settings, settings = host.library.settings(), budgets = host.library.list())
        }
        val files = holder(host)
        val household = RemoteFile(fileId = "household", groupId = "g", name = "Household", encryptKeyId = null)
        files.requestRemote(household)
        // Home would be cleared, so the phone asks first.
        assertEquals(ShellRoute.ConfirmReplaceBudget, host.state.value.route)
        assertEquals(household, host.state.value.budgetFiles.pendingFile)
        files.cancelReplaceBudget()
        assertEquals(ShellRoute.Settings, host.state.value.route)
        assertEquals(null, host.state.value.budgetFiles.pendingFile)
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
