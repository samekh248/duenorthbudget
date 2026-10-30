package app.duenorth.budget

import app.duenorth.budget.core.BudgetClock
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.CreateResult
import app.duenorth.budget.core.MockBudgets
import app.duenorth.budget.core.WriteResult
import app.duenorth.budget.core.createFromMock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** The inbox holder on its own, over a stand-in shell: no view model, no screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class InboxHolderTest {
    @Test
    fun selectModeChecksAndUnchecksRows() {
        val host = host("inbox-holder-select")
        val inbox = InboxHolder(host)
        val (a, b) =
            host.state.value.shell!!
                .inbox
                .take(2)
                .map { it.id }
        inbox.toggleSelected(a)
        assertEquals("toggling outside select mode does nothing", null, host.state.value.inbox.selection)
        inbox.startSelecting()
        inbox.toggleSelected(a)
        inbox.toggleSelected(b)
        inbox.toggleSelected(a)
        assertEquals(setOf(b), host.state.value.inbox.selection)
        inbox.endSelecting()
        assertEquals(null, host.state.value.inbox.selection)
    }

    @Test
    fun acceptFilesTheRowUnderItsSuggestionAndPublishes() {
        val host = host("inbox-holder-accept")
        val inbox = InboxHolder(host)
        val row =
            host.state.value.shell!!
                .inbox
                .first { it.suggestionId != null }
        inbox.accept(row.id)
        assertEquals(listOf(row.id), host.published.map { it.second })
        assertTrue(host.published.single().first is WriteResult.Saved)
        // The write has landed, so the confirmation ending lets the row go.
        inbox.filedShown(row.id)
        assertTrue(
            host.state.value.inbox.filed
                .isEmpty(),
        )
    }

    /** A shell over the household sample whose writes run at once and whose publishes are only recorded. */
    private class FakeHost(
        override val library: BudgetLibrary,
        start: ShellUiState,
    ) : ShellHost {
        override val state = MutableStateFlow(start)
        override val scope = CoroutineScope(Dispatchers.Unconfined)
        override val io = Dispatchers.Unconfined
        override var writing = false
        val pushed = mutableListOf<ShellRoute>()

        /** Each publish's result, with the transaction the holder filed (the first filed row's id). */
        val published = mutableListOf<Pair<WriteResult, String>>()

        override fun mutate(transform: (ShellUiState) -> ShellUiState) = state.update(transform)

        override fun push(route: ShellRoute) {
            pushed += route
        }

        override fun launchWrite(block: suspend (String) -> Unit) {
            val id = state.value.settings.openBudgetId ?: return
            writing = true
            try {
                runBlocking { block(id) }
            } finally {
                writing = false
            }
        }

        override suspend fun publish(
            result: WriteResult,
            accountId: String,
            stayOnRegister: Boolean,
        ) {
            published += result to
                state.value.inbox.filed
                    .first()
                    .row.id
        }

        override fun loadPayees() = Unit

        override fun loadAccount(accountId: String) = Unit
    }

    private fun host(name: String): FakeHost {
        val root =
            File(RuntimeEnvironment.getApplication().filesDir, name).apply {
                deleteRecursively()
                mkdirs()
            }
        val library = BudgetLibrary(root, AndroidSessionOpener(), BudgetClock.System)
        val id = (library.createFromMock(MockBudgets.HOUSEHOLD) as CreateResult.Created).id
        return FakeHost(library, ShellUiState(settings = library.settings(), shell = library.readShell(id)))
    }
}
