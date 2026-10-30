package app.duenorth.budget

import app.duenorth.budget.core.BudgetClock
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.TemplateKind
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** The templates holder on its own, over a stand-in shell. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TemplatesHolderTest {
    @Test
    fun keepingDropsTheOverwriteQuestion() {
        val host = host("templates-holder-keep")
        host.mutate { it.copy(templates = it.templates.copy(confirm = "overwrite?")) }
        TemplatesHolder(host).keep()
        assertEquals(null, host.state.value.templates.confirm)
    }

    @Test
    fun aKindPickedTakesTheGridsPlace() {
        val host = host("templates-holder-kind")
        host.mutate { it.copy(route = ShellRoute.TemplateKinds("c-gas", null)) }
        TemplatesHolder(host).pickKind(TemplateKind.entries.first())
        assertEquals(listOf<ShellRoute>(ShellRoute.TemplateEditor("c-gas", null, TemplateKind.entries.first())), host.replaced)
    }

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
