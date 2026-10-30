package app.duenorth.budget

import app.duenorth.budget.core.BudgetClock
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.PhoneSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** The bank sync holder on its own, over a stand-in shell. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BankSyncHolderTest {
    @Test
    fun theReportOpensOnceAndDismissLeavesIt() {
        val host = host("bank-sync-report", ShellUiState(bankSync = BankSyncState(panel = BankSyncUi(running = false))))
        val bankSync = BankSyncHolder(host)
        bankSync.openReport()
        host.mutate { it.copy(route = ShellRoute.BankSyncReport) }
        bankSync.openReport()
        assertEquals(listOf<ShellRoute>(ShellRoute.BankSyncReport), host.pushed)
        bankSync.dismiss()
        assertEquals(null, host.state.value.bankSync.panel)
        assertEquals(1, host.backs)
    }

    @Test
    fun aRunningSyncKeepsItsPanel() {
        val running = BankSyncState(syncing = true, panel = BankSyncUi())
        val host = host("bank-sync-running", ShellUiState(bankSync = running))
        BankSyncHolder(host).dismiss()
        assertEquals(running, host.state.value.bankSync)
    }

    @Test
    fun aLockedBudgetDoesNotSync() {
        val host = host("bank-sync-locked", ShellUiState(settings = PhoneSettings(openBudgetId = "b"), locked = true))
        BankSyncHolder(host).syncAll()
        assertEquals(BankSyncState(), host.state.value.bankSync)
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
