package app.duenorth.budget

import app.duenorth.budget.core.BankProvider
import app.duenorth.budget.core.BudgetClock
import app.duenorth.budget.core.BudgetLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** The bank link holder on its own, over a stand-in shell. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BankLinkHolderTest {
    @Test
    fun theRegistersBankLinkOpensForItsAccount() {
        val host = host("bank-link-holder-open")
        host.mutate { it.copy(route = ShellRoute.Register("visa")) }
        val bankLink = BankLinkHolder(host)
        bankLink.open()
        assertEquals(listOf<ShellRoute>(ShellRoute.BankLink("visa")), host.pushed)
        assertEquals(
            "visa",
            host.state.value.bankLink
                ?.accountId,
        )
        bankLink.replaceSimpleFinToken()
        assertEquals(
            BankProvider.SIMPLEFIN,
            host.state.value.bankLink
                ?.provider,
        )
        assertTrue(
            host.state.value.bankLink!!
                .askingToken,
        )
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
