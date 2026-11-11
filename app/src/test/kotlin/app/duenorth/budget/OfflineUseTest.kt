package app.duenorth.budget

import app.duenorth.budget.core.ActualTransport
import app.duenorth.budget.core.BankLinkCopy
import app.duenorth.budget.core.BankSyncTransport
import app.duenorth.budget.core.BudgetClock
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.MemorySecretStore
import app.duenorth.budget.core.ProbeHttp
import app.duenorth.budget.core.ScheduleDraft
import app.duenorth.budget.core.ScheduleRepeat
import app.duenorth.budget.core.SchedulesBook
import app.duenorth.budget.core.ServerProbe
import app.duenorth.budget.core.SyncCoordinator
import app.duenorth.budget.core.SyncSchema
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.lang.reflect.Proxy
import java.time.LocalDate
import java.util.Collections

/**
 * A phone that never connects to an Actual server: the budget made on the phone works from first
 * run through relaunch, nothing touches the network, and no sync failure is recorded against it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OfflineUseTest {
    /** Every network call any layer makes, by name; a phone with no server makes none. */
    private val calls = Collections.synchronizedList(mutableListOf<String>())

    private fun <T> tripwire(type: Class<T>): T =
        type.cast(
            Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
                calls += "${type.simpleName}.${method.name}"
                throw java.io.IOException("no network on this phone")
            },
        )

    private val clock = BudgetClock { LocalDate.of(2026, 11, 11) }

    private fun root(name: String): File =
        File(RuntimeEnvironment.getApplication().filesDir, "offline-$name").apply {
            deleteRecursively()
            mkdirs()
        }

    private fun library(root: File) = BudgetLibrary(root, AndroidSessionOpener(), clock)

    private fun model(root: File): ShellViewModel {
        val library = library(root)
        val sync =
            SyncCoordinator(
                library,
                tripwire(ActualTransport::class.java),
                MemorySecretStore(),
                clock,
                bankTransport = tripwire(BankSyncTransport::class.java),
            )
        val model =
            ShellViewModel(
                library,
                sync,
                bankSyncTransport = tripwire(BankSyncTransport::class.java),
                probe =
                    ServerProbe(
                        ProbeHttp { url ->
                            calls += "probe $url"
                            throw java.io.IOException("no network")
                        },
                    ),
                io = Dispatchers.Main,
            )
        awaitMain { !model.state.value.loading }
        return model
    }

    private fun lastFailure(
        library: BudgetLibrary,
        budgetId: String,
    ): String? = library.useDatabase(budgetId) { SyncSchema.state(it, "lastFailure") }

    @Test
    fun aPhoneOnlyBudgetRunsAndRelaunchesWithoutAServer() {
        val root = root("phone")
        val first = model(root)
        first.onboarding.choosePhone()
        first.budgetFiles.create("Home", "USD")
        awaitMain { first.state.value.shell != null }
        val budgetId = first.state.value.settings.openBudgetId!!
        first.accounts.submitAdd("Checking", offBudget = false, balanceText = "1000")
        awaitMain {
            first.state.value.shell
                ?.accounts
                ?.any { it.name == "Checking" } == true
        }

        // A schedule that posts on its own, due today: it posts at launch with no server.
        val checking =
            first.state.value.shell!!
                .accounts
                .single { it.name == "Checking" }
                .id
        library(root).useDatabase(budgetId) { session ->
            SchedulesBook(session, clock).save(
                ScheduleDraft(
                    name = "Rent",
                    accountId = checking,
                    payeeName = "Landlord",
                    amountMinor = -50000L,
                    repeat = ScheduleRepeat.MONTHLY,
                    startIso = "2026-11-11",
                    postsTransaction = true,
                ),
            )
        }

        // The app closed and opened again, as after a force stop.
        val second = model(root)
        awaitMain { second.state.value.shell != null }
        assertEquals(ShellRoute.Home, second.state.value.route)
        assertNull(second.state.value.onboarding)
        awaitMain { !second.state.value.bankSync.syncing }
        val posted =
            library(root).useDatabase(budgetId) { session ->
                session.query("SELECT id FROM transactions WHERE schedule IS NOT NULL AND IFNULL(tombstone, 0) = 0").size
            }
        assertEquals(1, posted)

        // Nothing on home offers a sync, and no bank sync failure waits there.
        assertTrue(
            !second.state.value
                .pullSync()
                .enabled,
        )
        assertNull(second.state.value.bankSync.panel)

        // Export works from the phone alone.
        second.settingsUi.exportBudget()
        awaitMain { second.state.value.settingsUi.pendingExport != null }

        assertEquals(emptyList<String>(), calls.toList())
        // No failed sync is recorded on a budget that has no server to sync with.
        assertNull(lastFailure(library(root), budgetId))
        assertEquals("", second.state.value.syncStatus)
    }

    @Test
    fun bankLinkSaysASignInIsNeededWithoutCallingAnything() {
        val root = root("bank")
        val model = model(root)
        model.onboarding.choosePhone()
        model.budgetFiles.create("Home", "USD")
        awaitMain { model.state.value.shell != null }
        model.accounts.submitAdd("Checking", offBudget = false, balanceText = "0")
        awaitMain {
            model.state.value.shell
                ?.accounts
                ?.isNotEmpty() == true
        }
        val checking =
            model.state.value.shell!!
                .accounts
                .first()
                .id
        model.register.openAccount(checking)
        model.bankLink.open()
        awaitMain {
            model.state.value.bankLink
                ?.loading == false
        }
        assertEquals(
            BankLinkCopy.SIGN_IN_FIRST,
            model.state.value.bankLink
                ?.error,
        )
        assertNotNull(model.state.value.bankLink)
        assertEquals(emptyList<String>(), calls.toList())
    }
}
