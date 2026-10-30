package app.duenorth.budget

import app.duenorth.budget.core.BudgetClock
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.CreateResult
import app.duenorth.budget.core.GetStartedStep
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** The get started holder on its own, over a stand-in shell. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GetStartedHolderTest {
    @Test
    fun hidingTheListLeavesItBehindForGood() {
        val (host, id) = host("get-started-holder-hide")
        val getStarted = GetStartedHolder(host, EnvelopesHolder(host))
        runBlocking { getStarted.start(id) }
        assertTrue(id in host.library.settings().getStarted)
        getStarted.hide()
        assertFalse(id in host.state.value.settings.getStarted)
        assertFalse(id in host.library.settings().getStarted)
    }

    @Test
    fun anEmptyBudgetsCategoriesLineOpensTheStarterCategories() {
        val (host, _) = host("get-started-holder-categories")
        GetStartedHolder(host, EnvelopesHolder(host)).open(GetStartedStep.CATEGORIES)
        assertEquals(listOf<ShellRoute>(ShellRoute.StarterCategories), host.pushed)
    }

    private fun host(name: String): Pair<FakeShellHost, String> {
        val root =
            File(RuntimeEnvironment.getApplication().filesDir, name).apply {
                deleteRecursively()
                mkdirs()
            }
        val library = BudgetLibrary(root, AndroidSessionOpener(), BudgetClock.System)
        val id = (library.create("Home", "USD") as CreateResult.Created).id
        return FakeShellHost(library, ShellUiState(settings = library.settings(), shell = library.readShell(id))) to id
    }
}
