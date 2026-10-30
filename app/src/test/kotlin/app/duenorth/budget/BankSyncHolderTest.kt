package app.duenorth.budget

import app.duenorth.budget.core.BankProvider
import app.duenorth.budget.core.BankSyncFailure
import app.duenorth.budget.core.BudgetClock
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.CreateResult
import app.duenorth.budget.core.MockBudgets
import app.duenorth.budget.core.PhoneSettings
import app.duenorth.budget.core.UndoCopy
import app.duenorth.budget.core.createFromMock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun unlinkAsksFirstAndKeepLeavesTheLink() {
        val host = tour("bank-sync-unlink-keep")
        val id = host.state.value.settings.openBudgetId!!
        val bankSync = BankSyncHolder(host)
        bankSync.askUnlink(checkingFailure)
        assertEquals(checkingFailure, host.state.value.bankSync.unlinking)
        bankSync.keepLink()
        assertNull(host.state.value.bankSync.unlinking)
        assertEquals(BankProvider.SIMPLEFIN, host.sync.bankLinkOf(id, "checking")?.first)
    }

    @Test
    fun unlinkClearsTheLinkKeepsTheAccountAndLeavesTheOtherFailure() {
        val host = tour("bank-sync-unlink-yes")
        val id = host.state.value.settings.openBudgetId!!
        val bankSync = BankSyncHolder(host)
        bankSync.askUnlink(checkingFailure)
        bankSync.unlink()
        assertNull(host.sync.bankLinkOf(id, "checking"))
        assertTrue(
            host.library
                .readShell(id)!!
                .accounts
                .any { it.id == "checking" },
        )
        assertEquals(
            listOf(cardFailure),
            host.state.value.bankSync.panel
                ?.failures,
        )
        assertEquals(
            listOf("Card"),
            host.state.value.bankSync.panel
                ?.started,
        )
        assertNull(host.state.value.bankSync.unlinking)
        assertEquals(0, host.backs)
        assertEquals(UndoCopy.BANK_LINK, host.state.value.undoLabel)
    }

    @Test
    fun unlinkingTheLastFailureClosesThePage() {
        val host = tour("bank-sync-unlink-last", listOf(checkingFailure))
        val bankSync = BankSyncHolder(host)
        bankSync.askUnlink(checkingFailure)
        bankSync.unlink()
        assertNull(host.state.value.bankSync.panel)
        assertEquals(1, host.backs)
    }

    @Test
    fun anAccountWithoutAnIdIsNotOfferedUnlink() {
        val host = tour("bank-sync-unlink-none")
        BankSyncHolder(host).askUnlink(BankSyncFailure("Mystery", "fetch did not finish"))
        assertNull(host.state.value.bankSync.unlinking)
    }

    private val checkingFailure = BankSyncFailure("Checking", "fetch did not finish", "checking")
    private val cardFailure = BankSyncFailure("Card", "fetch did not finish", "card")

    /** The full tour sample, whose Checking is linked to SimpleFIN, on the failed sync's page. */
    private fun tour(
        name: String,
        failures: List<BankSyncFailure> = listOf(checkingFailure, cardFailure),
    ): FakeShellHost {
        val root =
            File(RuntimeEnvironment.getApplication().filesDir, name).apply {
                deleteRecursively()
                mkdirs()
            }
        val library = BudgetLibrary(root, AndroidSessionOpener(), BudgetClock.System)
        val id = (library.createFromMock(MockBudgets.FULL_TOUR) as CreateResult.Created).id
        val panel = BankSyncUi(running = false, started = failures.map { it.accountName }, failures = failures)
        return FakeShellHost(
            library,
            ShellUiState(
                settings = library.settings(),
                shell = library.readShell(id),
                route = ShellRoute.BankSyncReport,
                bankSync = BankSyncState(panel = panel),
            ),
        )
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
