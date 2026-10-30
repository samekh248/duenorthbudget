package app.duenorth.budget

import app.duenorth.budget.core.BudgetClock
import app.duenorth.budget.core.BudgetLibrary
import app.duenorth.budget.core.Currencies
import app.duenorth.budget.core.ImportPreview
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** The import holder on its own, over a stand-in shell. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ImportHolderTest {
    @Test
    fun cancelLeavesThePreviewForTheAccountsRegister() {
        val preview = ImportPreview("checking", Currencies.byCode("USD")!!, emptyList(), adding = 0, skipping = 0)
        val host =
            host(
                "import-holder-cancel",
                ShellUiState(route = ShellRoute.ImportPreviewRoute("checking"), import = ImportState(preview = preview, error = "x")),
            )
        ImportHolder(host).cancel()
        assertEquals(ShellRoute.Register("checking"), host.state.value.route)
        assertEquals(ImportState(), host.state.value.import)
    }

    @Test
    fun confirmWithoutAPreviewDoesNothing() {
        val host = host("import-holder-none", ShellUiState())
        ImportHolder(host).confirm()
        assertEquals(emptyList<ShellRoute>(), host.pushed)
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
