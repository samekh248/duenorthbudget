package app.duenorth.budget

import android.os.Looper
import app.duenorth.budget.core.BudgetClock
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.CreateResult
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.HttpActualTransport
import app.duenorth.budget.core.MemorySecretStore
import app.duenorth.budget.core.MockBudgets
import app.duenorth.budget.core.SyncCoordinator
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import java.io.File

/** Shared setup for the spec 010 view model tests: a household sample budget opened in a fresh model. */
internal object ParityTestSupport {
    val USD = Currencies.byCode("USD")!!

    fun household(name: String): Pair<ShellViewModel, BudgetLibrary> {
        val context = RuntimeEnvironment.getApplication()
        val root =
            File(context.filesDir, "parity-$name").apply {
                deleteRecursively()
                mkdirs()
            }
        val library = BudgetLibrary(root, AndroidSessionOpener(), BudgetClock.System)
        library.createFromMock(MockBudgets.HOUSEHOLD) as CreateResult.Created
        val sync = SyncCoordinator(library, HttpActualTransport(), MemorySecretStore(), BudgetClock.System)
        val model = ShellViewModel(library, sync)
        await { !model.state.value.loading && model.state.value.shell != null }
        return model to library
    }

    fun drain() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    fun await(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!predicate()) {
            if (System.nanoTime() > deadline) error("timed out")
            drain()
            Thread.sleep(15)
        }
    }
}
