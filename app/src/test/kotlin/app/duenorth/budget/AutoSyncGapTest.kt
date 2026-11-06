package app.duenorth.budget

import androidx.lifecycle.viewModelScope
import app.duenorth.budget.ParityTestSupport.drain
import app.duenorth.budget.ParityTestSupport.household
import app.duenorth.budget.core.SyncCoordinator
import app.duenorth.budget.core.SyncSchema
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The sync the app starts on its own, at launch and on coming back, waits until the last good one is 30 minutes old. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AutoSyncGapTest {
    @Test
    fun autoSyncRunsOnlyOnceTheLastSuccessfulSyncIsHalfAnHourOld() {
        val (model, library) = household("auto-sync-gap")
        val id = model.state.value.settings.openBudgetId!!

        fun lastSynced(millisAgo: Long?) {
            val value = millisAgo?.let { (System.currentTimeMillis() - it).toString() }.orEmpty()
            library.useDatabase(id) { SyncSchema.putState(it, "lastSuccessEpoch", value) }
        }

        fun ran(): Boolean {
            var ran: Boolean? = null
            model.viewModelScope.launch { ran = model.autoSync(id) }
            drain()
            return ran!!
        }

        lastSynced(null)
        assertTrue(ran())
        lastSynced(5 * 60_000L)
        assertFalse(ran())
        lastSynced(SyncCoordinator.AUTO_SYNC_SKIP_MS - 60_000L)
        assertFalse(ran())
        lastSynced(SyncCoordinator.AUTO_SYNC_SKIP_MS + 1_000L)
        assertTrue(ran())
    }

    @Test
    fun comingBackChecksAgainOncePastTheMinuteGuard() {
        val (model, library) = household("auto-sync-return")
        val id = model.state.value.settings.openBudgetId!!
        library.useDatabase(id) { SyncSchema.putState(it, "lastSuccessEpoch", System.currentTimeMillis().toString()) }
        // Back within a minute of launch: not even checked.
        val launched = model.lastScreenSync
        model.onScreenReturned()
        drain()
        assertEquals(launched, model.lastScreenSync)
        // Over a minute later it checks; a fresh sync still leaves it alone, and the screen keeps its budget.
        model.lastScreenSync = 0L
        model.onScreenReturned()
        drain()
        assertTrue(model.lastScreenSync > 0L)
        assertFalse(model.state.value.bankSync.syncing)
        assertEquals(id, model.state.value.settings.openBudgetId)
    }
}
