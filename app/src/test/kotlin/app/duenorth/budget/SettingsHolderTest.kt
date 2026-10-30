package app.duenorth.budget

import app.duenorth.budget.core.BudgetClock
import app.duenorth.budget.core.BudgetLibrary
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** The settings holder on its own, over a stand-in shell. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsHolderTest {
    @Test
    fun aRepairRunsOnlyOnceAskedAbout() {
        val host = host("settings-holder-advanced")
        val settings = holder(host)
        settings.confirmRebuild()
        assertEquals("nothing was asked, so nothing runs", 0, host.reloads)
        settings.requestAdvanced(AdvancedConfirm.Rebuild)
        assertEquals(AdvancedConfirm.Rebuild, host.state.value.settingsUi.confirmAdvanced)
        settings.cancelAdvanced()
        assertEquals(null, host.state.value.settingsUi.confirmAdvanced)
    }

    @Test
    fun backgroundSyncReschedulesOnlyWhenTheIntervalChanges() {
        val host = host("settings-holder-sync")
        val settings = holder(host)
        val now = host.state.value.settings.backgroundSyncHours
        settings.setBackgroundSyncHours(now)
        settings.setBackgroundSyncHours(now + 1)
        assertEquals(listOf(now + 1), host.backgroundSyncHours)
        assertEquals(now + 1, host.state.value.settings.backgroundSyncHours)
    }

    @Test
    fun openingAPageFromSettingsComesBackToIt() {
        val host = host("settings-holder-return")
        val settings = holder(host)
        settings.openFrom(ShellRoute.Create, SettingsPage.ADVANCED)
        assertEquals(ShellRoute.Create, host.state.value.route)
        assertEquals(true, host.state.value.settingsReturn)
        settings.returnTo()
        assertEquals(ShellRoute.Settings, host.state.value.route)
        assertEquals(SettingsPage.ADVANCED, host.state.value.settingsPage)
        assertEquals(false, host.state.value.settingsReturn)
    }

    @Test
    fun serverBudgetsOpenThePickerFromTheBudgetItemOnlyWhenSignedIn() {
        val host = host("settings-holder-server-budgets")
        val settings = holder(host)
        settings.showServerBudgets()
        assertEquals("signed out, there is no list to pick from", emptyList<ShellRoute>(), host.pushed)
        host.mutate { it.copy(signedIn = true, route = ShellRoute.Settings, settingsPage = SettingsPage.BUDGET) }
        settings.showServerBudgets()
        assertEquals(ShellRoute.OnboardingFiles, host.state.value.route)
        assertEquals(true, host.state.value.settingsReturn)
        settings.returnTo()
        assertEquals(ShellRoute.Settings, host.state.value.route)
        assertEquals(SettingsPage.BUDGET, host.state.value.settingsPage)
    }

    private fun holder(host: FakeShellHost) = SettingsHolder(host, PayeesHolder(host), ServersHolder(host))

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
