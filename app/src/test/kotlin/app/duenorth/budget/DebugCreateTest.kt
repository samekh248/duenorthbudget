package app.duenorth.budget

import android.os.Looper
import app.duenorth.budget.core.BudgetClock
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.GetStartedStep
import app.duenorth.budget.core.HttpActualTransport
import app.duenorth.budget.core.MemorySecretStore
import app.duenorth.budget.core.MockBudgets
import app.duenorth.budget.core.StarterCategories
import app.duenorth.budget.core.SyncCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DebugCreateTest {
    @Test
    fun debugCreateSeedsTheWalkthroughUnderTheDefaultName() {
        val (library, model) = model("debug-create", developerMode = true)
        assertEquals(MockBudgets.WALKTHROUGH_NAME, model.state.value.defaultBudgetName)
        model.create(MockBudgets.WALKTHROUGH_NAME, "USD")
        await { model.state.value.shell != null }
        val shell = model.state.value.shell!!
        assertEquals(MockBudgets.WALKTHROUGH_NAME, shell.budgetName)
        assertTrue(shell.inbox.isNotEmpty())
        assertTrue(library.bankLinked(shell.budgetId, "checking"))
    }

    @Test
    fun releaseCreateStaysEmptyWithNoDefaultName() {
        val (_, model) = model("release-create", developerMode = false)
        assertEquals("", model.state.value.defaultBudgetName)
        model.create("Home", "USD")
        await { model.state.value.shell != null }
        val shell = model.state.value.shell!!
        assertEquals("Home", shell.budgetName)
        assertTrue(shell.inbox.isEmpty())
    }

    @Test
    fun releaseCreateOpensOnTheGetStartedList() {
        val (library, model) = model("release-get-started", developerMode = false)
        model.create("Home", "USD")
        await { model.state.value.shell != null }
        assertEquals(ScreenBar.HOME_BUDGET, model.state.value.homeSection)
        assertEquals(
            4,
            model.state.value
                .getStarted()!!
                .size,
        )

        // Set up categories opens the starter set while the budget has none; add brings them in.
        model.openGetStarted(GetStartedStep.CATEGORIES)
        assertEquals(ShellRoute.StarterCategories, model.state.value.route)
        model.addStarterCategories(StarterCategories.all.take(2))
        await {
            model.state.value.route == ShellRoute.Home && model.state.value.shell!!
                .groups.size == 2
        }
        assertEquals(
            listOf(false, true, false, false),
            model.state.value
                .getStarted()!!
                .map { it.done },
        )

        model.hideGetStarted()
        assertNull(model.state.value.getStarted())
        await { library.settings().getStarted.isEmpty() }
    }

    @Test
    fun debugBrandNewSampleHasTheGetStartedList() {
        val (_, model) = model("debug-brand-new", developerMode = true)
        model.createSample(MockBudgets.BRAND_NEW)
        await { model.state.value.shell != null }
        assertTrue(
            model.state.value.shell!!
                .groups
                .isEmpty(),
        )
        assertEquals(
            4,
            model.state.value
                .getStarted()!!
                .size,
        )
    }

    private fun model(
        name: String,
        developerMode: Boolean,
    ): Pair<BudgetLibrary, ShellViewModel> {
        val context = RuntimeEnvironment.getApplication()
        val root =
            File(context.filesDir, name).apply {
                deleteRecursively()
                mkdirs()
            }
        val clock = BudgetClock { LocalDate.of(2026, 10, 7) }
        val library = BudgetLibrary(root, AndroidSessionOpener(), clock)
        val sync = SyncCoordinator(library, HttpActualTransport(), MemorySecretStore(), clock)
        val model = ShellViewModel(library, sync, developerMode = developerMode)
        await { !model.state.value.loading }
        return library to model
    }

    private fun await(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!predicate()) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            check(System.nanoTime() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }
}
